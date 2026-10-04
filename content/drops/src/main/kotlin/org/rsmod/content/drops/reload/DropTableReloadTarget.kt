package org.rsmod.content.drops.reload

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.DropTableReload
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadException
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSources
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.server.config.ServerConfig

@Singleton
class DropTableReloadTarget
@Inject
constructor(private val registry: DropTableRegistry, private val config: ServerConfig) :
    Reloadable {
    override val id: String = "drops"
    override val description: String = "TOML drop tables"
    override val order: Int = 30

    private val sourceDir: Path?
        get() = ReloadSources.resolve(config.hotReload.paths[id], DEV_DIR)

    override fun watchPaths(): List<Path> = listOfNotNull(sourceDir)

    override fun prepare(context: ReloadContext): ReloadPlan {
        val dir = sourceDir
        val sources = if (dir != null) readDirectory(dir) else registry.classpathTomlSources()
        val reload =
            try {
                registry.prepareReload(sources)
            } catch (e: IllegalStateException) {
                throw ReloadException(e.message ?: "Drop tables failed to load", e)
            }
        val origin = if (dir != null) "source files" else "bundled resources"
        return ReloadPlan {
            registry.install(reload)
            ReloadSummary("${reload.tableCount} tables from $origin (${describe(reload)})")
        }
    }

    private fun describe(reload: DropTableReload): String {
        val parts =
            listOf("changed" to reload.changed, "added" to reload.added, "removed" to reload.removed)
                .filter { it.second.isNotEmpty() }
                .map { (label, files) -> "$label ${preview(files)}" }
        return if (parts.isEmpty()) "no changes" else parts.joinToString("; ")
    }

    private fun preview(files: List<String>): String {
        val shown = files.take(PREVIEW).joinToString(", ")
        return if (files.size > PREVIEW) "$shown +${files.size - PREVIEW} more" else shown
    }

    private fun readDirectory(dir: Path): Map<String, String> =
        Files.walk(dir).use { stream ->
            stream
                .filter { it.isRegularFile() && it.extension == "toml" }
                .toList()
                .associate { file ->
                    val relative = dir.relativize(file).invariantSeparatorsPathString
                    "$RESOURCE_ROOT/$relative" to file.readText()
                }
        }

    private companion object {
        private const val RESOURCE_ROOT = "drops/tables"
        private const val DEV_DIR = "content/drops/src/main/resources/drops/tables"
        private const val PREVIEW = 4
    }
}
