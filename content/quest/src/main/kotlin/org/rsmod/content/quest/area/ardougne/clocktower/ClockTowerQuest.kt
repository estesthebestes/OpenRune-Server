package org.rsmod.content.quest.area.ardougne.clocktower

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class ClockTowerQuest :
    QuestScript(
        "quest_clocktower",
        "varp.cogquest",
        rewards { extra("500 Coins") },
        ItemRewardDisplay(Cog.WHITE.obj, zoom = 200),
        questVarbit = "varbit.clocktower_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Clock Tower end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $STAGE_COMPLETE."
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Brother Kojo</col> at the <col=800000>Clock Tower</col> south " +
            "of <col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            description(
                "<red>Brother Kojo</red>'s clock tower has broken down. He wants me to find the " +
                    "four missing <red>cogs</red> in the dungeon beneath the tower and fit one " +
                    "to a spindle on each of its levels."
            )
            line("The cogs are too heavy to carry more than one at a time.")
            for (cog in Cog.entries) {
                if (isPlaced(p, cog)) strike(cog.journalDone) else line(cog.journalHint)
            }
            if (allPlaced(p)) {
                line("All four cogs are in place. I should tell <red>Brother Kojo</red>.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I found the four cogs in the dungeon beneath Brother Kojo's clock tower: the " +
                    "red one among the ogres, the blue one in a cell, the black one ringed by " +
                    "fire and the white one beyond a cage of rats."
            )
            line(
                "With every cog on its spindle the clock works again, and Brother Kojo paid me " +
                    "$REWARD_COINS coins."
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isActive(player: Player): Boolean = stage(player) == STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isPlaced(player: Player, cog: Cog): Boolean = player.vars[cog.varbit] == 1

    fun place(player: Player, cog: Cog) {
        VarPlayerIntMapSetter.set(player, cog.varbit, 1)
    }

    fun placedCount(player: Player): Int = Cog.entries.count { isPlaced(player, it) }

    fun allPlaced(player: Player): Boolean = placedCount(player) == Cog.entries.size

    fun ratsPoisoned(player: Player): Boolean = player.vars[RATS_POISONED_VARBIT] == 1

    fun poisonRats(player: Player) {
        VarPlayerIntMapSetter.set(player, RATS_POISONED_VARBIT, 1)
    }

    fun clearProgress(player: Player) {
        for (cog in Cog.entries) {
            VarPlayerIntMapSetter.set(player, cog.varbit, 0)
        }
        VarPlayerIntMapSetter.set(player, RATS_POISONED_VARBIT, 0)
    }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 8

        const val REWARD_COINS = 500

        const val KOJO = "npc.brother_kojo"
        const val COINS = "obj.coins"

        const val RATS_POISONED_VARBIT = "varbit.clocktower_rats_poisoned"
    }
}

enum class Cog(
    val label: String,
    val obj: String,
    val varbit: String,
    val brokenSpindle: String,
    val fittedSpindle: String,
    val journalHint: String,
    val journalDone: String,
) {
    RED(
        "red",
        "obj.redcog",
        "varbit.clocktower_red_cog",
        "loc.brokeclockpole_red",
        "loc.clockpole_red",
        "The <red>red cog</red> lies in the cellar among some <red>ogres</red>, beyond the " +
            "south-east door.",
        "The red cog is fitted to its spindle on the ground floor.",
    ),
    BLACK(
        "black",
        "obj.blackcog",
        "varbit.clocktower_black_cog",
        "loc.brokeclockpole_black",
        "loc.clockpole_black",
        "The <red>black cog</red> sits in a ring of fire beyond the north-east door. It is " +
            "too hot to touch without a <red>bucket of water</red> or <red>ice gloves</red>.",
        "The black cog is fitted to its spindle in the cellar.",
    ),
    BLUE(
        "blue",
        "obj.bluecog",
        "varbit.clocktower_blue_cog",
        "loc.brokeclockpole_blue",
        "loc.clockpole_blue",
        "The <red>blue cog</red> is locked in a cell with a rat. There may be a way in from " +
            "the <red>ladder</red> south of the zoo.",
        "The blue cog is fitted to its spindle on the first floor.",
    ),
    WHITE(
        "white",
        "obj.whitecog",
        "varbit.clocktower_white_cog",
        "loc.brokeclockpole_white",
        "loc.clockpole_white",
        "The <red>white cog</red> is behind a gate past a cage of <red>rats</red>, beyond the " +
            "north-west door. Some <red>rat poison</red> might clear the way.",
        "The white cog is fitted to its spindle on the top floor.",
    );

    companion object {
        fun ofObj(obj: String): Cog? = entries.firstOrNull { it.obj == obj }
    }
}
