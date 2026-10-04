package org.rsmod.content.areas.zeah.doors

import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

internal object KourendEntryRules {
    const val WOODCUTTING_GUILD_LEVEL = 60
    const val FARMING_GUILD_LEVEL = 45
    const val TITHE_FARM_LEVEL = 34

    const val ASCENT_OF_ARCEUUS = "quest_ascentofarceuus"
    const val FORSAKEN_TOWER = "quest_forsakentower"

    fun canEnterWoodcuttingGuild(player: Player): Boolean =
        player.woodcuttingLvl >= WOODCUTTING_GUILD_LEVEL

    fun canEnterFarmingGuild(player: Player): Boolean = player.farmingLvl >= FARMING_GUILD_LEVEL

    fun canEnterTitheFarm(player: Player): Boolean = player.farmingLvl >= TITHE_FARM_LEVEL

    fun canEnterTowerOfMagic(player: Player): Boolean = startedOrDone(player, ASCENT_OF_ARCEUUS)

    fun canEnterForsakenTower(player: Player): Boolean = startedOrDone(player, FORSAKEN_TOWER)

    private fun startedOrDone(player: Player, quest: String): Boolean =
        QuestRequirements.isOnQuest(player, quest) || QuestRequirements.hasCompleted(player, quest)
}
