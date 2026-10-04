package org.rsmod.api.realm.config.updater

import jakarta.inject.Inject
import org.rsmod.api.hotreload.HotReloadRegistry
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.config.reload.RealmReloadTarget
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class RealmConfigUpdaterScript
@Inject
constructor(
    private val updater: RealmConfigUpdater,
    private val realm: Realm,
    private val registry: HotReloadRegistry,
    private val reloadTarget: RealmReloadTarget,
) : PluginScript() {
    override fun ScriptContext.startup() {
        registry.register(reloadTarget)
        onGameStartup { attachGameThread() }
        onPlayerLogin { player.globalXpRate = realm.config.globalXpRate }
    }

    private fun attachGameThread() {
        val thread = Thread.currentThread()
        updater.attachWriteThread(thread)
    }
}
