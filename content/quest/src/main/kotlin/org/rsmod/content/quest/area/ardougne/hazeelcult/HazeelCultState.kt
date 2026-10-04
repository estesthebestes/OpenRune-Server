package org.rsmod.content.quest.area.ardougne.hazeelcult

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Clivet's own state: 0 while he waits by the raft, 1 once he has fled to the hideout. */
var Player.hazeelClivetLocation: Int by intVarBit("varbit.hazeelcult_clivet_location")

/** Alomone: 0 friendly, 1 hostile and attackable, 2 slain. */
var Player.hazeelAlomoneState: Int by intVarBit("varbit.hazeelcult_alomone_vis")

var Player.hazeelAlomoneMet: Boolean by boolVarBit("varbit.hazeelcult_alomone_met")
var Player.hazeelGivenArmour: Boolean by boolVarBit("varbit.hazeelcult_given_armour")
var Player.hazeelFoundArmour: Boolean by boolVarBit("varbit.hazeelcult_found_armour")
var Player.hazeelEvidenceFound: Boolean by boolVarBit("varbit.hazeelcult_jones_cutscene")

/** Butler Jones: 0 working in the house, 1 locked away in the jail cell. */
var Player.hazeelJonesLocation: Int by intVarBit("varbit.hazeelcult_jones_location")

var Player.hazeelPoisonSuccess: Boolean by boolVarBit("varbit.hazeelcult_poison_success")
var Player.hazeelGivenAmulet: Boolean by boolVarBit("varbit.hazeelcult_given_amulet")
var Player.hazeelSewerChat: Boolean by boolVarBit("varbit.hazeelcult_sewer_chat")
var Player.hazeelGivenPoison: Boolean by boolVarBit("varbit.hazeelcult_given_poison")
var Player.hazeelGivenScroll: Boolean by boolVarBit("varbit.hazeelcult_given_scroll")
var Player.hazeelRevived: Boolean by boolVarBit("varbit.hazeelcult_hazeel_cutscene")

/** Scruffy: 0 alive, 1 poisoned, 2 buried. */
var Player.hazeelDogState: Int by intVarBit("varbit.carnillean_dog_vis")

val Player.sidedWithCeril: Boolean
    get() = hazeelClivetLocation == 1

/**
 * The five sewer valves in the order they sit from west to east, which is the order the amulet
 * shows them. Each varbit records whether the valve has been turned away from its starting side,
 * so a fresh player has every valve wrong and the raft refuses to move.
 */
enum class SewerValve(val loc: String, val varbit: String, val startsRight: Boolean) {
    West("loc.sewervalve1", "varbit.hazeel_cult_valve_1", startsRight = false),
    Carnillean("loc.sewervalve2", "varbit.hazeel_cult_valve_2", startsRight = false),
    Clocktower("loc.sewervalve3", "varbit.hazeel_cult_valve_3", startsRight = true),
    Zoo("loc.sewervalve4", "varbit.hazeel_cult_valve_4", startsRight = false),
    East("loc.sewervalve5", "varbit.hazeel_cult_valve_5", startsRight = false);

    val solvedRight: Boolean
        get() = !startsRight

    fun isTurnedRight(player: Player): Boolean = startsRight != (player.vars[varbit] == 1)

    fun isCorrect(player: Player): Boolean = player.vars[varbit] == 1

    companion object {
        fun ofLoc(loc: String): SewerValve? = entries.firstOrNull { it.loc == loc }

        fun correctCount(player: Player): Int = entries.count { it.isCorrect(player) }

        fun amuletPattern(): String =
            entries.joinToString(", ") { if (it.solvedRight) "right" else "left" }
    }
}

/** Where the raft leaves the player for each number of correctly set valves. */
object HazeelSewers {
    val CaveEntry = CoordGrid(2569, 9683, 0)
    val CaveExit = CoordGrid(2584, 3235, 0)
    val RaftStart = CoordGrid(2567, 9679, 0)
    val RaftLanding = CoordGrid(2567, 9680, 0)
    val FirstIsland = CoordGrid(2578, 9687, 0)
    val SecondIsland = CoordGrid(2593, 9694, 0)
    val ThirdIsland = CoordGrid(2599, 9711, 0)
    val FourthIsland = CoordGrid(2616, 9725, 0)
    val HideoutLanding = CoordGrid(2606, 9692, 0)

    /** Three valves strand the raft on the fourth island and four on the third. */
    fun destination(correct: Int): CoordGrid? =
        when (correct) {
            1 -> FirstIsland
            2 -> SecondIsland
            3 -> FourthIsland
            4 -> ThirdIsland
            5 -> HideoutLanding
            else -> null
        }

    fun islandName(correct: Int): String? =
        when (correct) {
            1 -> "first"
            2 -> "second"
            3 -> "fourth"
            4 -> "third"
            else -> null
        }
}
