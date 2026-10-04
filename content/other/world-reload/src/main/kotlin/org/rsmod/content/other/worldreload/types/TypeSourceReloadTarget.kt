package org.rsmod.content.other.worldreload.types

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSources
import org.rsmod.api.hotreload.Reloadable

/**
 * Watch-only companion to [TypeReloadTarget]: when the editable type data changes (the server
 * TOMLs under `.data/raw-cache/server` or any module's `pack/configs`), it rebuilds the cache and
 * applies the changed types, the same as `::reload types build`.
 */
@Singleton
class TypeSourceReloadTarget @Inject constructor(private val types: TypeReloadTarget) : Reloadable {
    override val id: String = "typesrc"
    override val description: String = "Edited type data: rebuild the cache, then reload types"
    override val order: Int = 61
    override val watchDebounceMs: Long = 2_000
    override val includeInAll: Boolean = false

    override fun watchPaths(): List<Path> = TypeSources.dirs(ReloadSources.workingDir)

    override fun prepare(context: ReloadContext): ReloadPlan =
        types.prepare(context.withOptions(setOf(BUILD)))

    private companion object {
        private const val BUILD = "build"
    }
}

internal object TypeSources {
    private val PluginRoots = listOf("content")
    private val Skipped = setOf("build", ".gradle", "node_modules", "src")

    fun dirs(root: Path): List<Path> {
        val dirs = mutableListOf<Path>()
        root.resolve(".data").resolve("raw-cache").resolve("server").takeIf { it.isDirectory() }
            ?.let(dirs::add)
        for (pluginRoot in PluginRoots) {
            val base = root.resolve(pluginRoot)
            if (base.isDirectory()) {
                collectPackConfigs(base.toRealPath(), dirs)
            }
        }
        return dirs
    }

    private fun collectPackConfigs(dir: Path, out: MutableList<Path>) {
        val configs = dir.resolve("src").resolve("main").resolve("resources").resolve("pack")
            .resolve("configs")
        if (configs.isDirectory()) {
            out.add(configs)
        }
        Files.list(dir).use { children ->
            children
                .filter { it.isDirectory() && it.name !in Skipped }
                .forEach { collectPackConfigs(it, out) }
        }
    }
}
