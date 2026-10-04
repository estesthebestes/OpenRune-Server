package org.rsmod.content.quest.manager

import jakarta.inject.Inject
import org.rsmod.api.hotreload.config.ServerConfigReloadListener
import org.rsmod.api.hotreload.config.ServerConfigReloadTarget
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class QuestPolicyReloadScript
@Inject
constructor(
    private val configTarget: ServerConfigReloadTarget,
    private val playerList: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        configTarget.addListener(QuestPolicyListener())
    }

    private inner class QuestPolicyListener : ServerConfigReloadListener {
        override val name: String = "quest requirements"

        override fun validate(config: ServerConfig) {
            QuestRequirementPolicy.from(config)
        }

        override fun apply(previous: ServerConfig, current: ServerConfig): String? {
            val before = QuestRequirements.activePolicy()
            val after = QuestRequirementPolicy.from(current)
            if (before == after) {
                return null
            }
            QuestRequirements.install(after)
            var synced = 0
            for (player in playerList) {
                QuestPolicyClientSync.sync(player)
                synced++
            }
            return "quest mode ${before.mode} -> ${after.mode} (resynced $synced players)"
        }
    }
}
