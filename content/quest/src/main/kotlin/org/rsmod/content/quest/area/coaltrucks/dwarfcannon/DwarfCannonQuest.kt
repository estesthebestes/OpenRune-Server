package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dwarf Cannon.
 *
 * The stage is `varp.mcannon`, endstate 11 from `dbrow.quest_dwarfcannon`. Two values are pinned by
 * the cache multilocs: Gilob's remains only show on the watchtower at [STAGE_FIND_GILOB], and the
 * camp's cannon turns from `broken_multicannon` into a working `dwarf_multicannon1` at
 * [STAGE_CANNON_FIXED]. Stage 5 has no meaning of its own; it is treated like [STAGE_FIND_LOLLK].
 *
 * The sub-state lives in `varp.mcannonmulti`: one varbit per replaced railing (they drive the six
 * railing multilocs), and the toolkit interface's selected tool, safety switch and spring. The
 * cache's `mcannon_taken_corpse` shares its bit with the sixth railing, so it is not used.
 */
@Singleton
class DwarfCannonQuest :
    QuestScript(
        QUEST_KEY,
        "varp.mcannon",
        rewards {
            xp("stat.crafting", CRAFTING_XP)
            extra("The ability to purchase and use")
            extra("a dwarf multicannon")
            extra("The ability to make cannonballs")
        },
        ItemRewardDisplay(CANNON_BASE, zoom = 250),
        questVarbit = "varbit.dwarf_cannon_progress",
    ) {
    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Dwarf Cannon end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $STAGE_COMPLETE."
        }
        onPlayerLogin { syncVars(player) }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun ownsItem(access: ProtectedAccess, item: String): Boolean =
        item in access.player.inv || item in access.bank

    fun syncVars(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in SUB_STATE_VARBITS) {
            if (player.vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(player, varbit, 0)
            }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Captain Lawgof</col> in the dwarven camp south of the " +
            "<col=800000>Coal Trucks</col>, north-west of the <col=800000>Fishing Guild</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = access.player
            objective(
                "<red>Captain Lawgof</red> has made me an honorary member of the Black Guard. " +
                    "Goblins are breaking through the stockade around his camp, and he wants me " +
                    "to replace the six broken <red>railings</red> using a <red>hammer</red>.",
            ) {
                visibleWhen { stage(p) in STAGE_STARTED..STAGE_RAILINGS_FIXED }
                stageAtLeast(STAGE_RAILINGS_FIXED, "I have fixed all the broken railings.").strike()
            }
            objective(
                "Lawgof lost contact with his watchtower to the south. He wants me to find " +
                    "<red>Gilob</red>, the dwarf in charge there, and tell him a relief guard is " +
                    "on the way.",
            ) {
                visibleWhen { stage(p) == STAGE_FIND_GILOB }
                hasItem("mcannonremains", "I found Gilob's remains at the top of the tower.").strike()
            }
            objective(
                "Gilob is dead. His son <red>Lollk</red> was with him and has been taken by the " +
                    "goblins. They always attack from the south-east, so their hideout should be " +
                    "down there.",
            ) {
                visibleWhen { stage(p) in STAGE_FIND_LOLLK until STAGE_LOLLK_RESCUED }
            }
            objective(
                "I found Lollk tied up in a crate in the <red>goblin cave</red> and he ran home. " +
                    "I should let Captain Lawgof know.",
            ) {
                visibleWhen { stage(p) == STAGE_LOLLK_RESCUED }
            }
            objective(
                "Goblins sabotaged the camp's <red>multicannon</red>. Lawgof gave me a " +
                    "<red>toolkit</red> to repair it: each tool has to be used on the right " +
                    "moving part.",
            ) {
                visibleWhen { stage(p) == STAGE_REPAIR_CANNON }
            }
            objective("I have repaired the cannon. I should tell Captain Lawgof.") {
                visibleWhen { stage(p) == STAGE_CANNON_FIXED }
            }
            objective(
                "Nobody at the camp knows what the cannon fires. I must ask <red>Nulodion</red>, " +
                    "the Black Guard's cannon engineer, at their base just south of " +
                    "<red>Ice Mountain</red>.",
            ) {
                visibleWhen { stage(p) == STAGE_SEE_NULODION }
            }
            objective(
                "Nulodion gave me an <red>ammo mould</red> and his <red>notes</red>. I must take " +
                    "both of them back to Captain Lawgof.",
            ) {
                visibleWhen { stage(p) == STAGE_HAVE_MOULD }
                hasItem("ammo_mould", "I have the ammo mould.")
                hasItem("nulodions_notes", "I have Nulodion's notes.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Captain Lawgof made me an honorary member of the Dwarven Black Guard. I repaired " +
                    "the stockade around his camp by the Coal Trucks.",
            )
            line(
                "I found the remains of Gilob at the watchtower, then rescued his son Lollk from " +
                    "the goblin cave.",
            )
            line(
                "I fixed the camp's sabotaged multicannon, and fetched an ammo mould and " +
                    "instructions from Nulodion, the Black Guard's cannon engineer.",
            )
            line("Nulodion is now willing to sell me a dwarf multicannon of my own.")
        }

    companion object {
        const val QUEST_KEY = "quest_dwarfcannon"

        const val STAGE_STARTED = 1
        const val STAGE_RAILINGS_FIXED = 2
        const val STAGE_FIND_GILOB = 3
        const val STAGE_FIND_LOLLK = 4
        const val STAGE_LOLLK_RESCUED = 6
        const val STAGE_REPAIR_CANNON = 7
        const val STAGE_CANNON_FIXED = 8
        const val STAGE_SEE_NULODION = 9
        const val STAGE_HAVE_MOULD = 10
        const val STAGE_COMPLETE = 11

        const val CRAFTING_XP = 750.0

        const val LAWGOF = "npc.lawgof2"
        const val LOLLK = "npc.dwarfchildtw1"
        const val NULODION = "npc.nulodion"

        const val RAILING = "obj.mcannonrailing1_obj"
        const val HAMMER = "obj.hammer"
        const val REMAINS = "obj.mcannonremains"
        const val TOOLKIT = "obj.mcannontoolkit"
        const val NOTES = "obj.nulodions_notes"
        const val AMMO_MOULD = "obj.ammo_mould"
        const val MANUAL = "obj.mcannonbook"
        const val CANNON_BASE = "obj.twpart1"
        const val CANNON_STAND = "obj.twpart2"
        const val CANNON_BARRELS = "obj.twpart3"
        const val CANNON_FURNACE = "obj.twpart4"
        const val COINS = "obj.coins"

        const val RAILING_COUNT = 6

        val RAILING_VARBITS = (1..RAILING_COUNT).map { "varbit.mcannon_railing${it}_fixed" }

        const val TOOL_TOOTHED = "varbit.mcannonmulti_tool1"
        const val TOOL_PLIERS = "varbit.mcannonmulti_tool2"
        const val TOOL_HOOK = "varbit.mcannonmulti_tool3"
        const val SAFETY_ON = "varbit.mcannon_safety_on"
        const val SPRING_SET = "varbit.mcannon_spring_set"

        val TOOL_VARBITS = listOf(TOOL_TOOTHED, TOOL_PLIERS, TOOL_HOOK)

        private val SUB_STATE_VARBITS = RAILING_VARBITS + TOOL_VARBITS + SAFETY_ON + SPRING_SET

        /** Inside the goblin cave, just beside the mud pile that climbs back out. */
        val CAVE_ARRIVAL = CoordGrid(2620, 9796, 0)

        /** Outside the cave mouth, south-east of the Fishing Guild. */
        val CAVE_EXIT = CoordGrid(2623, 3391, 0)
    }
}
