package org.rsmod.content.quest.manager

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbHelper
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.table.QuestRow
import org.rsmod.game.entity.Player

/**
 * Tells the client which quests the active [QuestRequirementPolicy] treats as complete, so
 * client-side quest locks (spellbooks, teleports, prayers, ...) agree with the server. The
 * overridden `[proc,quest_is_complete]` checks this list before the real quest varps, leaving
 * actual quest progress and the quest journal untouched.
 */
internal object QuestPolicyClientSync {
    private const val ALL_COMPLETE = "*"

    private val questIdsByKey: Map<String, Int> by lazy {
        DbHelper.table("dbtable.quest").associate { row ->
            val key = RSCM.getReverseMapping(RSCMType.DBROW, row.id).removePrefix("dbrow.")
            key to QuestRow(row).id
        }
    }

    fun sync(player: Player) {
        val script = "clientscript.quest_policy_complete_set".asRSCM(RSCMType.CLIENTSCRIPT)
        player.runClientScript(script, completedIds(player))
    }

    fun completedIds(player: Player): String {
        if (QuestRequirements.activePolicy().mode == QuestRequirementMode.AssumeCompleted) {
            return ALL_COMPLETE
        }
        val ids =
            questIdsByKey
                .filterKeys { QuestRequirements.hasCompleted(player, it) }
                .values
                .sorted()
        return if (ids.isEmpty()) "" else ids.joinToString(",", ",", ",")
    }
}
