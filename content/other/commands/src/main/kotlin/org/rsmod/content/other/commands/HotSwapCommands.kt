package org.rsmod.content.other.commands

import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import org.rsmod.api.hotreload.HotReloadService
import org.rsmod.api.hotreload.ReloadRequester
import org.rsmod.api.hotreload.ReloadSources
import org.rsmod.api.hotreload.RequestOutcome
import org.rsmod.api.hotreload.hotswap.HotSwapTarget
import org.rsmod.api.player.output.mes
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HotSwapCommands
@Inject
constructor(private val reloads: HotReloadService, private val hotSwap: HotSwapTarget) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("hotswap", "Swap in recompiled code: ::hotswap [module] [build]", ::hotSwap)
        onCommand("rescript", "Re-run a plugin script's startup: ::rescript <ScriptName>", ::rescript)
    }

    private fun hotSwap(cheat: Cheat) =
        with(cheat) {
            val build = "build" in args
            val moduleName = args.firstOrNull { it != "build" }
            val options = mutableSetOf<String>()
            if (moduleName != null) {
                val gradlePath =
                    resolveModule(moduleName)
                        ?: return@with player.mes("No content or api module named '$moduleName'.")
                if (build) {
                    options += HotSwapTarget.BUILD_PREFIX + gradlePath
                }
            } else if (build) {
                return@with player.mes("Name the module to build, e.g. ::hotswap generic-npcs build")
            }
            val requester = ReloadRequester.Admin(player.uid, player.displayName)
            val message =
                when (reloads.request(hotSwap.id, requester, options)) {
                    RequestOutcome.Started ->
                        if (build) {
                            "Compiling $moduleName and swapping it in..."
                        } else {
                            "Swapping in compiled code..."
                        }
                    RequestOutcome.Coalesced -> "A hot swap is already running; it will run again after."
                    RequestOutcome.ShuttingDown -> "The server is shutting down."
                    RequestOutcome.UnknownTarget -> "Hot swap is not available."
                }
            player.mes(message)
        }

    private fun rescript(cheat: Cheat) =
        with(cheat) {
            val name = args.firstOrNull() ?: return@with player.mes("Use ::rescript <ScriptName>")
            player.mes(hotSwap.restartScript(name))
        }

    /** Maps a module directory name like `generic-npcs` to its Gradle path. */
    private fun resolveModule(name: String): String? {
        val root = ReloadSources.workingDir
        for (base in listOf("content", "api")) {
            val dir = root.resolve(base)
            if (!dir.isDirectory()) continue
            val match =
                Files.walk(dir).use { stream ->
                    stream
                        .filter { it.isDirectory() && it.name.equals(name, ignoreCase = true) }
                        .filter { it.resolve("build.gradle.kts").exists() }
                        .filter { "build" !in root.relativize(it).map(Path::toString) }
                        .findFirst()
                        .orElse(null)
                } ?: continue
            return ":" + root.relativize(match).invariantSeparatorsPathString.replace('/', ':')
        }
        return null
    }
}
