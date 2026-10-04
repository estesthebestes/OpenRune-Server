package org.rsmod.api.hotreload

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.hotreload.config.ServerConfigReloadTarget
import org.rsmod.api.hotreload.hotswap.HotSwapTarget
import org.rsmod.api.hotreload.watch.HotReloadWatcher
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class HotReloadScript
@Inject
constructor(
    private val registry: HotReloadRegistry,
    private val service: HotReloadService,
    private val watcher: HotReloadWatcher,
    private val configTarget: ServerConfigReloadTarget,
    private val hotSwap: HotSwapTarget,
    private val config: ServerConfig,
) : PluginScript() {
    override fun ScriptContext.startup() {
        registry.register(configTarget)
        registry.register(hotSwap)
        onGameStartup { start() }
        onEvent<GameLifecycle.StartCycle> { service.drainApplies() }
        onEvent<GameLifecycle.Shutdown> { stop() }
    }

    private fun start() {
        val settings = config.hotReload
        if (settings.code) {
            service.request(hotSwap.id, ReloadRequester.Server, setOf(HotSwapTarget.BASELINE))
        }
        if (settings.watch) {
            watcher.start(settings.debounceMs)
        }
    }

    private fun stop() {
        watcher.stop()
        service.shutdown()
    }
}
