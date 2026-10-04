package org.rsmod.api.hotreload.hotswap

import com.github.michaelbull.logging.InlineLogger
import com.google.inject.Injector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.hotreload.GradleTasks
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadException
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSources
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.hotreload.noChanges
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.plugin.scripts.LoadedPluginScripts
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `::hotswap`: pushes freshly compiled content/api classes into the running JVM. Changed classes
 * are redefined in place, brand new classes (new lambdas, new scripts) are defined next to their
 * package, and every plugin script whose classes changed is restarted so new or edited handler
 * registrations take effect. Method-body edits work on any JDK; adding methods or fields needs
 * JetBrains Runtime (`gradlew run -Photswap`).
 */
@Singleton
public class HotSwapTarget
@Inject
constructor(
    private val config: ServerConfig,
    private val injector: Injector,
    private val context: ScriptContext,
) : Reloadable {
    override val id: String = "code"
    override val description: String = "Compiled content/api classes (::hotswap)"
    override val order: Int = 90
    override val watchDebounceMs: Long = 1_000
    override val includeInAll: Boolean = false

    private val logger = InlineLogger()
    private val index = CompiledClassIndex(ReloadSources.workingDir)

    @Volatile private var baseline: Map<String, ClassFileStamp>? = null

    override fun watchPaths(): List<Path> =
        if (config.hotReload.code) index.classDirs() else emptyList()

    override fun prepare(context: ReloadContext): ReloadPlan {
        if (!config.hotReload.code) {
            throw ReloadException("Hot swap is off: set hot-reload.code: true in game.yml and restart.")
        }
        if (BASELINE in context.options) {
            val snapshot = index.snapshot(previous = null)
            baseline = snapshot
            return noChanges("indexed ${snapshot.size} compiled classes")
        }
        context.options.filter { it.startsWith(BUILD_PREFIX) }.forEach { option ->
            GradleTasks.run(ReloadSources.workingDir, listOf("${option.removePrefix(BUILD_PREFIX)}:classes"))
        }
        val base = baseline ?: throw ReloadException("Still indexing compiled classes; try again.")
        val current = index.snapshot(previous = base)
        val changed = current.filter { (name, stamp) -> base[name]?.let { it.crc != stamp.crc } == true }
        val added = current.filterKeys { it !in base }
        if (changed.isEmpty() && added.isEmpty()) {
            return noChanges("no compiled class changes")
        }
        val instrumentation = HotSwapAgent.instrumentation()
        val changedBytes = changed.mapValues { Files.readAllBytes(it.value.path) }
        val addedBytes = added.mapValues { Files.readAllBytes(it.value.path) }
        return ReloadPlan { apply(instrumentation, changedBytes, addedBytes, current, base) }
    }

    private fun apply(
        instrumentation: Instrumentation,
        changed: Map<String, ByteArray>,
        added: Map<String, ByteArray>,
        current: Map<String, ClassFileStamp>,
        previous: Map<String, ClassFileStamp>,
    ): ReloadSummary {
        val loader = javaClass.classLoader
        val definitions = changed.map { (name, bytes) -> ClassDefinition(Class.forName(name, false, loader), bytes) }
        try {
            if (definitions.isNotEmpty()) {
                instrumentation.redefineClasses(*definitions.toTypedArray())
            }
        } catch (e: UnsupportedOperationException) {
            throw ReloadException(structuralMessage(e))
        } catch (e: LinkageError) {
            throw ReloadException("Class redefinition was rejected: ${e.message}", e)
        }
        val defined = defineNew(added, previous.keys)
        val undefined = added.keys - defined.mapTo(hashSetOf()) { it.name }
        // Incremental compiles briefly delete class files; keep their entries so a class that
        // reappears unchanged is not mistaken for a new one. Classes that could not be defined
        // stay out of the baseline so they keep showing up as pending.
        baseline = previous + current - undefined

        val warnings = mutableListOf<String>()
        if (undefined.isNotEmpty()) {
            val packages = undefined.map { it.substringBeforeLast('.') }.distinct()
            warnings +=
                "${undefined.size} new classes need a restart (new packages can't be added " +
                    "live): ${packages.joinToString()}"
        }
        val touched = (changed.keys + added.keys).mapTo(hashSetOf()) { it.substringBefore('$') }
        var restarted = 0
        for (script in LoadedPluginScripts.all()) {
            if (script.javaClass.name !in touched) continue
            val failure = restart(script)
            if (failure == null) restarted++ else warnings += failure
        }
        var started = 0
        for (clazz in defined) {
            if (!PluginScript::class.java.isAssignableFrom(clazz) || Modifier.isAbstract(clazz.modifiers)) {
                continue
            }
            if (LoadedPluginScripts.byClassName(clazz.name) != null) continue
            try {
                val script = injector.getInstance(clazz) as PluginScript
                context.withOwner(script) { with(script) { context.startup() } }
                LoadedPluginScripts.add(script)
                started++
            } catch (e: Exception) {
                logger.error(e) { "New script ${clazz.name} failed to start." }
                warnings += "${clazz.simpleName} failed to start: ${e.message}"
            }
        }
        val message =
            "${changed.size} classes redefined, ${defined.size} new; " +
                "$restarted scripts restarted, $started new scripts started"
        return ReloadSummary(message, warnings)
    }

    /** Restarts one script's registrations without any class change. */
    public fun restartScript(name: String): String {
        val script =
            LoadedPluginScripts.all().firstOrNull {
                it.javaClass.simpleName.equals(name, ignoreCase = true) ||
                    it.javaClass.name.equals(name, ignoreCase = true)
            } ?: return "No loaded script named '$name'."
        return restart(script) ?: "Restarted ${script.javaClass.simpleName}."
    }

    private fun restart(script: PluginScript): String? =
        try {
            with(script) { context.shutdown() }
            context.removeByOwner(script)
            context.withOwner(script) { with(script) { context.startup() } }
            null
        } catch (e: Exception) {
            logger.error(e) { "Script ${script.javaClass.name} failed to restart." }
            "${script.javaClass.simpleName} failed to restart (restart the server): ${e.message}"
        }

    private fun defineNew(added: Map<String, ByteArray>, known: Set<String>): List<Class<*>> {
        val pending = added.toMutableMap()
        val defined = mutableListOf<Class<*>>()
        var progress = true
        while (pending.isNotEmpty() && progress) {
            progress = false
            val iterator = pending.entries.iterator()
            while (iterator.hasNext()) {
                val (name, bytes) = iterator.next()
                val host = hostFor(name, known) ?: continue
                try {
                    defined += MethodHandles.privateLookupIn(host, MethodHandles.lookup()).defineClass(bytes)
                    iterator.remove()
                    progress = true
                } catch (_: NoClassDefFoundError) {
                } catch (e: LinkageError) {
                    logger.warn { "Could not define $name: ${e.message}" }
                    iterator.remove()
                }
            }
        }
        if (pending.isNotEmpty()) {
            logger.warn { "Classes not defined (restart needed): ${pending.keys}" }
        }
        return defined
    }

    private fun hostFor(name: String, known: Set<String>): Class<*>? {
        val packageName = name.substringBeforeLast('.', "")
        val outer = name.substringBefore('$')
        val candidates =
            sequenceOf(outer) +
                known.asSequence().filter { it.substringBeforeLast('.', "") == packageName }
        for (candidate in candidates) {
            if (candidate == name) continue
            val clazz = runCatching { Class.forName(candidate, false, javaClass.classLoader) }.getOrNull()
            if (clazz != null && clazz.packageName == packageName) return clazz
        }
        return null
    }

    private fun structuralMessage(e: UnsupportedOperationException): String =
        if (HotSwapAgent.supportsStructuralChanges) {
            "This change cannot be hot swapped (${e.message}); restart the server."
        } else {
            "This change adds or removes methods/fields (${e.message}). Run the server on " +
                "JetBrains Runtime (gradlew run -Photswap) to swap it, or restart."
        }

    public companion object {
        public const val BASELINE: String = "baseline"
        public const val BUILD_PREFIX: String = "build="
    }
}
