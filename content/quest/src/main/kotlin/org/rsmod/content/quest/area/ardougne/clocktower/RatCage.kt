package org.rsmod.content.quest.area.ardougne.clocktower

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rat cage in the north-west of the Clock Tower dungeon, between the player and the white cog.
 *
 * Two levers on the corridor wall work the cage's two gates. Pulling a lever swaps its gate for
 * the other state shared by everyone in the dungeon, and both fall back to the map's resting
 * state after [LeverResetTicks]. The outer gate rests shut and the inner gate rests open. Rat
 * poison in the food trough kills the rats, whose death throes shake the far gate loose for the
 * player that poisoned them.
 */
class RatCage
@Inject
constructor(
    private val clockTower: ClockTowerQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val npcList: NpcList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (lever in Lever.entries) {
            onOpLoc1(lever.restLoc) { pull(it.loc, lever, pulled = false) }
            onOpLoc1(lever.pulledLoc) { pull(it.loc, lever, pulled = true) }
        }
        for (gate in Gate.entries) {
            onOpLoc1(gate.shutLoc) { mes(GateStuck) }
        }
        onOpLoc1(FarGate) { throughFarGate(it.loc) }
        onOpLoc1(CellWall) { throughCellWall(it.loc) }
        onOpLocU(Trough, RatPoison) { poisonTrough() }
    }

    private suspend fun ProtectedAccess.pull(lever: BoundLocInfo, which: Lever, pulled: Boolean) {
        arriveDelay()
        val up = which.restsUp == pulled
        anim(if (up) PullUpSeq else PullDownSeq)
        soundSynth(LeverSound)
        mes("You pull the lever ${if (up) "up" else "down"}.")
        if (pulled) {
            locRepo.del(lever, Int.MAX_VALUE)
            restoreGate(which.gate)
        } else {
            locRepo.change(lever, which.pulledLoc, LeverResetTicks)
            swapGate(which.gate)
        }
    }

    private fun swapGate(gate: Gate) {
        val resting = gate.currentLoc() ?: return
        locRepo.del(resting, LeverResetTicks)
        val angle = gate.swappedAngle
        locRepo.add(gate.coords, gate.swappedLoc, LeverResetTicks, angle, LocShape.WallStraight)
    }

    private fun restoreGate(gate: Gate) {
        val swapped = gate.currentLoc() ?: return
        if (swapped.id == gate.swappedLoc.asRSCM(RSCMType.LOC)) {
            locRepo.del(swapped, Int.MAX_VALUE)
        }
    }

    private fun Gate.currentLoc(): LocInfo? = locRepo.findExact(coords, LocShape.WallStraight)

    private suspend fun ProtectedAccess.throughFarGate(gate: BoundLocInfo) {
        arriveDelay()
        if (!clockTower.ratsPoisoned(player)) {
            mes(GateStuck)
            return
        }
        val westward = coords.x >= gate.coords.x
        if (westward) {
            mesbox(
                "The death throes of the rats seem to have shaken the door loose of its hinges. " +
                    "You pick it up and go through."
            )
        }
        val dest = if (westward) gate.coords.translateX(-1) else gate.coords
        crossWall(dest, westward)
    }

    private suspend fun ProtectedAccess.throughCellWall(wall: BoundLocInfo) {
        arriveDelay()
        val westward = coords.x > wall.coords.x
        val dest = if (westward) wall.coords else wall.coords.translateX(1)
        val open = LocAngle[(wall.angle.id + 1) and 0x3]
        locRepo.del(wall, WallOpenTicks)
        locRepo.add(wall.coords, CellWallOpen, WallOpenTicks, open, LocShape.WallStraight)
        crossWall(dest, westward)
    }

    private suspend fun ProtectedAccess.crossWall(dest: CoordGrid, westward: Boolean) {
        anim(WalkSeq)
        exactMove(
            start = coords,
            end = dest,
            delay1 = 0,
            delay2 = WallWalkCycles,
            dir = if (westward) Constants.em_face_west else Constants.em_face_east,
            teleportType = TeleportType.Exempt,
        )
        delay(1)
    }

    private suspend fun ProtectedAccess.poisonTrough() {
        arriveDelay()
        if (!clockTower.isActive(player)) {
            mes(Constants.dm_default)
            return
        }
        if (invDel(inv, RatPoison).failure) {
            return
        }
        clockTower.poisonRats(player)
        anim(PourSeq)
        val rats =
            npcList.filterNotNull().filter { npc ->
                npc.isVisible && Rats.any { npc.isType(it) } && inCage(npc.coords)
            }
        try {
            mes("The rats swarm towards the poisoned food...")
            delay(2)
            mes("... and devour it hungrily.")
            delay(2)
            mes("You see them smashing against the gates in a panic.")
            delay(2)
            mes("They seem to be dying.")
            for (rat in rats) {
                rat.anim(RatDeathSeq)
            }
            delay(RatDeathTicks)
        } finally {
            for (rat in rats) {
                if (rat.isVisible) {
                    npcRepo.despawn(rat, RatRespawnTicks)
                }
            }
        }
    }

    enum class Gate(
        val coords: CoordGrid,
        val shutLoc: String,
        val restsOpen: Boolean,
    ) {
        OUTER(CoordGrid(2595, 9657, 0), "loc.ctratgatea", restsOpen = false),
        INNER(CoordGrid(2593, 9657, 0), "loc.ctratgateb", restsOpen = true);

        val swappedLoc: String
            get() = if (restsOpen) shutLoc else OpenGate

        val swappedAngle: LocAngle
            get() = if (restsOpen) LocAngle.East else LocAngle.South
    }

    enum class Lever(
        val coords: CoordGrid,
        val restLoc: String,
        val pulledLoc: String,
        val restsUp: Boolean,
        val gate: Gate,
    ) {
        WEST(CoordGrid(2591, 9661, 0), "loc.ctlevera", "loc.ctlevera2", false, Gate.OUTER),
        EAST(CoordGrid(2593, 9661, 0), "loc.ctleverb", "loc.ctleverb2", true, Gate.INNER),
    }

    companion object {
        const val FarGate = "loc.ctratgatec"
        const val Trough = "loc.ctfoodtrough"
        const val CellWall = "loc.secretdoor2"
        const val CellWallOpen = "loc.secretdooropen2"
        const val OpenGate = "loc.inactiveprisondoor"
        const val RatPoison = "obj.rat_poison"

        val Rats = listOf("npc.clocktower_rat", "npc.clocktower_rat2", "npc.clocktower_rat3")

        const val LeverResetTicks = 100
        const val WallOpenTicks = 4
        const val RatDeathTicks = 3
        const val RatRespawnTicks = 100

        fun inCage(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 2579..2592 && coords.z in 9653..9660

        private const val GateStuck = "This door does not seem to be openable."
        private const val PullUpSeq = "seq.macro_lever_switch_up"
        private const val PullDownSeq = "seq.macro_lever_switch_down"
        private const val PourSeq = "seq.human_pickuptable"
        private const val WalkSeq = "seq.human_walk_f"
        private const val RatDeathSeq = "seq.giant_rat_update_death"
        private const val LeverSound = "synth.lever"

        private const val WallWalkCycles = 30
    }
}
