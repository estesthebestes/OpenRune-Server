package org.rsmod.content.drops

import jakarta.inject.Inject
import org.rsmod.api.droptable.DropRateModifiers
import org.rsmod.api.hotreload.config.ServerConfigReloadListener
import org.rsmod.api.hotreload.config.ServerConfigReloadTarget
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DropRateModifiersScript
@Inject
constructor(
    private val config: ServerConfig,
    private val configTarget: ServerConfigReloadTarget,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val server = config.gameplay.dropRates.multiplier
        require(server > 0.0) { "gameplay.drop-rates.multiplier must be positive, was $server" }
        DropRateModifiers.serverMultiplier = server
        DropRateModifiers.playerMultiplier = { player ->
            val percent = player.vars[PLAYER_MULTIPLIER_VARP]
            if (percent == 0) 1.0 else percent / 100.0
        }
        DropRateModifiers.install()
        configTarget.addListener(DropMultiplierListener)
    }

    private object DropMultiplierListener : ServerConfigReloadListener {
        override val name: String = "drop multiplier"

        override fun validate(config: ServerConfig) {
            val multiplier = config.gameplay.dropRates.multiplier
            require(multiplier > 0.0) { "drop-rates.multiplier must be above 0 (was $multiplier)" }
        }

        override fun apply(previous: ServerConfig, current: ServerConfig): String? {
            val before = DropRateModifiers.serverMultiplier
            val after = current.gameplay.dropRates.multiplier
            if (before == after) {
                return null
            }
            DropRateModifiers.serverMultiplier = after
            return "drop multiplier x$before -> x$after"
        }
    }

    companion object {
        const val PLAYER_MULTIPLIER_VARP = "varp.drop_rate_multiplier"
    }
}
