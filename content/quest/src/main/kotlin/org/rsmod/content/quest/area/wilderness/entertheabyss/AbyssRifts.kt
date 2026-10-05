package org.rsmod.content.quest.area.wilderness.entertheabyss

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.table.QuestRow
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.content.quest.area.entrana.EntranaRules
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.runecrafting.altar.KourendAltar
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** An inner-ring rift and the altar whose ruins it opens into, behind [quest] where OSRS has one. */
internal enum class AbyssRift(val loc: String, val altar: String, val quest: String? = null) {
    Air("loc.abyss_exit_to_air", "dbrow.runecrafting_altar_air"),
    Mind("loc.abyss_exit_to_mind", "dbrow.runecrafting_altar_mind"),
    Water("loc.abyss_exit_to_water", "dbrow.runecrafting_altar_water"),
    Earth("loc.abyss_exit_to_earth", "dbrow.runecrafting_altar_earth"),
    Fire("loc.abyss_exit_to_fire", "dbrow.runecrafting_altar_fire"),
    Body("loc.abyss_exit_to_body", "dbrow.runecrafting_altar_body"),
    Cosmic("loc.abyss_exit_to_cosmic", "dbrow.runecrafting_altar_cosmic", "quest_lostcity"),
    Chaos("loc.abyss_exit_to_chaos", "dbrow.runecrafting_altar_chaos"),
    Nature("loc.abyss_exit_to_nature", "dbrow.runecrafting_altar_nature"),
    Law("loc.abyss_exit_to_law", "dbrow.runecrafting_altar_law", "quest_trollstronghold"),
    Death("loc.abyss_exit_to_death", "dbrow.runecrafting_altar_death", "quest_mourningsendpart2"),
    Blood("loc.abyss_exit_to_blood_parent", "dbrow.runecrafting_altar_blood", "quest_sinsofthefather"),
    ;

    fun entrance(): CoordGrid? = RunecraftingAltarsRow.getRow(altar.asRSCM()).entrance
}

/**
 * The inner-ring rifts. Each lands the player where the altar's ruins do, with no talisman or tiara
 * needed; the law rift also applies Entrana's ban on weapons and armour.
 *
 * The blood rift leads to both blood altars: its op1 repeats the last destination
 * (`varbit.abyss_blood_rift_last_used`) and op2 takes the other. Kourend's blood altar, like the
 * soul rift's Soul Altar, is locked until the player has crafted there and then used dark essence
 * on the rift once; the essence is not used up.
 */
class AbyssRifts : PluginScript() {

    override fun ScriptContext.startup() {
        for (rift in AbyssRift.entries - AbyssRift.Blood) {
            onOpLoc1(rift.loc) { enter(rift) }
        }
        onOpLoc1(AbyssRift.Blood.loc) { enterBloodRift(repeatLast = true) }
        onOpLoc2(AbyssRift.Blood.loc) { enterBloodRift(repeatLast = false) }
        onOpLoc1(SOUL_RIFT) { enterKourend(KourendAltar.Soul) }
        onOpLocU(AbyssRift.Blood.loc) { useOnRift(KourendAltar.Blood, it.objType) }
        onOpLocU(SOUL_RIFT) { useOnRift(KourendAltar.Soul, it.objType) }
    }

    internal fun ProtectedAccess.enter(rift: AbyssRift): Boolean {
        val quest = rift.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            val name = QuestRow.getRow("dbrow.$quest".asRSCM()).displayname
            mes("You need to have completed $name to use this rift.")
            return false
        }
        if (rift == AbyssRift.Law && EntranaRules.carriesForbidden(player)) {
            mes("You cannot take weapons or armour through the law rift.")
            return false
        }
        val entrance = rift.entrance() ?: return false
        travel(entrance)
        return true
    }

    internal suspend fun ProtectedAccess.enterBloodRift(repeatLast: Boolean) {
        val lastWasKourend = vars[LAST_BLOOD_RIFT] == KOUREND
        val toKourend = if (repeatLast) lastWasKourend else !lastWasKourend
        val travelled = if (toKourend) enterKourend(KourendAltar.Blood) else enter(AbyssRift.Blood)
        if (travelled) {
            vars[LAST_BLOOD_RIFT] = if (toKourend) KOUREND else TRUE_ALTAR
        }
    }

    internal suspend fun ProtectedAccess.enterKourend(altar: KourendAltar): Boolean {
        if (!altar.hasCrafted(player)) {
            mes("You have not yet unlocked this rift.")
            return false
        }
        if (!altar.riftRedirected(player)) {
            val name = altar.displayName()
            startDialogue {
                mesbox(
                    "The rift cannot find the true $name Altar. However, if you bring a sample of " +
                        "the Dark Altar's power to the rift, the Dark Altar can redirect the rift to " +
                        "the place in Arceuus where it focuses the $name Altar's energy.",
                )
            }
            return false
        }
        travel(altar.landing())
        return true
    }

    internal suspend fun ProtectedAccess.useOnRift(altar: KourendAltar, obj: ItemServerType) {
        if (obj.internalName !in DARK_ESSENCE) {
            mes("The rift does not respond to that.")
            return
        }
        val name = altar.displayName()
        when {
            !altar.hasCrafted(player) ->
                startDialogue {
                    objbox(
                        obj.internalName,
                        "The ${name.lowercase()} rift will not respond to you until you have " +
                            "crafted some ${name.lowercase()} runes.",
                    )
                }
            altar.riftRedirected(player) ->
                startDialogue {
                    mesbox(
                        "The rift is already connected to the place in Arceuus where the Dark " +
                            "Altar focuses the energy of the true $name Altar.",
                    )
                }
            else -> {
                altar.redirectRift(player)
                startDialogue {
                    objbox(
                        obj.internalName,
                        "The power of the Dark Altar redirects the rift to the place in Arceuus " +
                            "where the energy of the true $name Altar is focused.",
                    )
                }
            }
        }
    }

    private fun ProtectedAccess.travel(dest: CoordGrid) {
        mes("You feel a powerful force take hold of you...", ChatType.Spam)
        telejump(dest)
        soundSynth("synth.teleport_all")
    }

    private fun KourendAltar.displayName(): String =
        when (this) {
            KourendAltar.Blood -> "Blood"
            KourendAltar.Soul -> "Soul"
        }

    private fun KourendAltar.landing(): CoordGrid =
        when (this) {
            KourendAltar.Blood -> KOUREND_BLOOD_LANDING
            KourendAltar.Soul -> KOUREND_SOUL_LANDING
        }

    internal companion object {
        const val SOUL_RIFT = "loc.abyss_exit_to_soul"
        const val LAST_BLOOD_RIFT = "varbit.abyss_blood_rift_last_used"
        const val TRUE_ALTAR = 0
        const val KOUREND = 1

        val DARK_ESSENCE = setOf("obj.arceuus_essence_block_dark", "obj.bigblankrune")

        /** Beside each Kourend altar, on its south side. */
        val KOUREND_BLOOD_LANDING = CoordGrid(1716, 3827)
        val KOUREND_SOUL_LANDING = CoordGrid(1814, 3852)
    }
}
