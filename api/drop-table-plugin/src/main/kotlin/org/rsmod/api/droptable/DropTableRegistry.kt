package org.rsmod.api.droptable

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import dtx.rs.RSDropTable
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.time.measureTimedValue
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.droptable.toml.DropTableTomlParser
import org.rsmod.api.droptable.toml.DropTableTomlResolver
import org.rsmod.api.droptable.toml.DropTableTomlTextFixer
import org.rsmod.api.droptable.toml.TomlDropTableDef
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scan.PluginClasspathScan

internal typealias DropTable = RSDropTable<Player, DropRollItem>

@Singleton
public class DropTableRegistry
@Inject
constructor(private val tomlResolver: DropTableTomlResolver) {
    private val logger = InlineLogger()

    private val annotatedTables: List<DropTable>

    @Volatile private var index: DropTableIndex

    @Volatile private var tomlHashes: Map<String, Int>

    init {
        val scan = PluginClasspathScan.scan
        val (sources, readDuration) = measureTimedValue { tomlSources(scan) }
        val (toml, tomlDuration) = measureTimedValue { parseToml(sources).getOrThrow() }
        val (annotated, annotatedDuration) = measureTimedValue { loadAnnotatedTables(scan) }
        annotatedTables = annotated
        index = DropTableIndex.build(toml.map { it.second }, annotatedTables)
        tomlHashes = sources.mapValues { it.value.hashCode() }
        logger.info {
            "DropTableRegistry: ${sources.size} toml table(s) loaded in " +
                "${readDuration + tomlDuration}; ${annotated.size} annotated table(s) loaded in " +
                "$annotatedDuration"
        }
    }

    public val tomlTableCount: Int
        get() = tomlHashes.size

    public fun forNpc(npc: Npc): RSDropTable<Player, DropRollItem>? =
        forNpc(npc, areaChecker = null)

    public fun forNpc(npc: Npc, areaChecker: AreaChecker?): RSDropTable<Player, DropRollItem>? {
        val candidates = index.byNpc[npc.type.internalName] ?: return null
        if (candidates.size == 1) {
            return candidates.first()
        }

        if (areaChecker != null) {
            val areaMatched =
                candidates.filter { table ->
                    table.areas.isNotEmpty() &&
                        table.areas.any { areaChecker.inArea(it, npc.coords) }
                }
            when (areaMatched.size) {
                1 -> return areaMatched.first()
                0 -> return candidates.firstOrNull { it.areas.isEmpty() } ?: candidates.first()
                else ->
                    error(
                        "Multiple drop tables match npc '${npc.type.internalName}' " +
                            "at ${npc.coords}: ${areaMatched.map { it.tableIdentifier }}",
                    )
            }
        }

        return candidates.firstOrNull { it.areas.isEmpty() } ?: candidates.first()
    }

    public fun forLoc(loc: String): RSDropTable<Player, DropRollItem>? = index.byLoc[loc]

    /** Reads every bundled TOML resource under `drops/tables`, keyed by its resource path. */
    public fun classpathTomlSources(): Map<String, String> =
        ClassGraph().acceptPaths(TOML_ROOT).scan().use(::tomlSources)

    /**
     * Parses [sources] (resource path → TOML text) and builds a complete replacement index,
     * keeping every `@RegisterDropTable` Kotlin table from boot. Safe to call off the game thread;
     * nothing changes until [install]. Throws [IllegalStateException] listing every parse error or
     * table conflict.
     */
    public fun prepareReload(sources: Map<String, String>): DropTableReload {
        val parsed = parseToml(sources).getOrThrow()
        val index = DropTableIndex.build(parsed.map { it.second }, annotatedTables)
        val hashes = sources.mapValues { it.value.hashCode() }
        val previous = tomlHashes
        val changed = hashes.filter { (path, hash) -> previous[path]?.let { it != hash } == true }
        val added = hashes.keys - previous.keys
        val removed = previous.keys - hashes.keys
        return DropTableReload(
            index = index,
            hashes = hashes,
            changed = changed.keys.map(::fileName).sorted(),
            added = added.map(::fileName).sorted(),
            removed = removed.map(::fileName).sorted(),
        )
    }

    /** Swaps in a prepared index. Must only be called from the game thread. */
    public fun install(reload: DropTableReload) {
        index = reload.index
        tomlHashes = reload.hashes
    }

    private fun tomlSources(scan: ScanResult): Map<String, String> =
        scan.allResources
            .filter { it.path.endsWith(".toml") }
            .associate { it.path to it.contentAsString }

    private fun parseToml(sources: Map<String, String>): ParseResult {
        val mapper =
            ObjectMapper(TomlFactory())
                .registerKotlinModule()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        val results =
            sources.entries
                .parallelStream()
                .map { (path, text) ->
                    try {
                        val raw = DropTableTomlTextFixer.hoistTableLevelKeys(text)
                        val def = mapper.readValue<TomlDropTableDef>(raw)
                        Result.success(path to DropTableTomlParser.parse(def, tomlResolver, sourcePath = path))
                    } catch (e: Exception) {
                        Result.failure(IllegalStateException("${fileName(path)}: ${e.message}", e))
                    }
                }
                .toList()
        return ParseResult(
            tables = results.mapNotNull { it.getOrNull() },
            errors = results.mapNotNull { it.exceptionOrNull()?.message?.lineSequence()?.first() },
        )
    }

    /**
     * Only loads classes that ClassGraph's bytecode-level field-annotation index already flagged
     * as having a [RegisterDropTable] field, instead of loading (and static-initializing) every
     * scanned class just to inspect its declared fields. Class loading (and any static-init work
     * it triggers) runs in parallel.
     */
    private fun loadAnnotatedTables(scan: ScanResult): List<DropTable> {
        val candidates = scan.getClassesWithFieldAnnotation(RegisterDropTable::class.java.name)
        val loaded = candidates.parallelStream().map { classInfo -> classInfo.loadClass() }.toList()
        return loaded.flatMap(::annotatedFields)
    }

    private fun annotatedFields(clazz: Class<*>): List<DropTable> =
        clazz.declaredFields.mapNotNull { field ->
            if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                return@mapNotNull null
            }
            if (field.getAnnotation(RegisterDropTable::class.java) == null) {
                return@mapNotNull null
            }
            if (!RSDropTable::class.java.isAssignableFrom(field.type)) {
                return@mapNotNull null
            }
            try {
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                field.get(null) as DropTable
            } catch (exception: Exception) {
                throw IllegalStateException(
                    "Failed to register drop table from ${clazz.name}.${field.name}",
                    exception,
                )
            }
        }

    private class ParseResult(val tables: List<Pair<String, DropTable>>, val errors: List<String>) {
        fun getOrThrow(): List<Pair<String, DropTable>> {
            check(errors.isEmpty()) {
                "${errors.size} drop table file(s) failed to parse:\n" + errors.joinToString("\n")
            }
            return tables
        }
    }

    private companion object {
        private const val TOML_ROOT = "drops/tables"

        private fun fileName(path: String): String = path.substringAfterLast('/')
    }
}

public class DropTableReload
internal constructor(
    internal val index: DropTableIndex,
    internal val hashes: Map<String, Int>,
    public val changed: List<String>,
    public val added: List<String>,
    public val removed: List<String>,
) {
    public val tableCount: Int
        get() = hashes.size
}

internal class DropTableIndex(
    val byNpc: Map<String, List<DropTable>>,
    val byLoc: Map<String, DropTable>,
) {
    companion object {
        fun build(toml: List<DropTable>, annotated: List<DropTable>): DropTableIndex {
            val builder = Builder()
            toml.forEach { builder.register(it, Source.Toml) }
            annotated.forEach { builder.register(it, Source.Annotation) }
            return DropTableIndex(builder.byNpc, builder.byLoc)
        }
    }

    private enum class Source {
        Toml,
        Annotation,
    }

    private class Builder {
        val byNpc = hashMapOf<String, MutableList<DropTable>>()
        val byLoc = hashMapOf<String, DropTable>()
        private val tomlTablesByNpc = hashMapOf<String, MutableSet<String>>()

        fun register(table: DropTable, source: Source) {
            check(table.npcs.isNotEmpty() || table.locs.isNotEmpty()) {
                "Drop table '${table.tableIdentifier}' must define at least one npc or loc."
            }

            table.npcs.forEach { npc ->
                val existing = byNpc.getOrPut(npc) { mutableListOf() }
                validateRegistration(table, npc, existing, source)
                existing += table
                if (source == Source.Toml) {
                    tomlTablesByNpc.getOrPut(npc) { mutableSetOf() } += table.tableIdentifier
                }
            }

            table.locs.forEach { loc ->
                check(!byLoc.containsKey(loc)) {
                    "Duplicate drop table for loc '$loc': " +
                        "'${byLoc[loc]?.tableIdentifier}' and '${table.tableIdentifier}'."
                }
                byLoc[loc] = table
            }
        }

        private fun validateRegistration(
            table: DropTable,
            npc: String,
            existing: List<DropTable>,
            source: Source,
        ) {
            if (existing.isEmpty()) {
                return
            }
            val overlapping =
                existing.filter { registered ->
                    registered.areas.isEmpty() && table.areas.isEmpty() ||
                        registered.areas.any { it in table.areas }
                }
            check(overlapping.isEmpty()) { buildConflictMessage(npc, overlapping, table, source) }
        }

        private fun buildConflictMessage(
            npc: String,
            overlapping: List<DropTable>,
            table: DropTable,
            source: Source,
        ): String {
            val conflicting = overlapping + table
            val tableNames = conflicting.joinToString { "'${it.tableIdentifier}'" }
            val tomlLoaded = tomlTablesByNpc[npc]?.isNotEmpty() == true
            val suffix =
                when {
                    source == Source.Annotation && tomlLoaded ->
                        " Remove the @RegisterDropTable Kotlin definition or the TOML file " +
                            "under drops/tables/."
                    source == Source.Toml ->
                        " Duplicate TOML drop tables cannot target the same npc."
                    else -> " Remove duplicate @RegisterDropTable definitions."
                }
            return "Ambiguous drop tables for npc '$npc': $tableNames.$suffix"
        }
    }
}
