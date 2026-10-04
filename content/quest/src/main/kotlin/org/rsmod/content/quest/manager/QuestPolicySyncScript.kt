package org.rsmod.content.quest.manager

import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sends each player the quest-complete list on login; see [QuestPolicyClientSync]. */
class QuestPolicySyncScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { QuestPolicyClientSync.sync(player) }
    }

    internal fun completedIds(player: Player): String = QuestPolicyClientSync.completedIds(player)
}
