package org.rsmod.content.quest.area.ardougne

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The great doors in the wall between East and West Ardougne. They stay shut until Plague City is
 * done; after that the mourners let the player through in either direction. The doorway tiles are
 * map-blocked, so the door pieces are taken out of the way for a few seconds and the player is
 * stepped across one tile per cycle, which the client draws as an ordinary walk.
 */
class WestArdougneGate
@Inject
constructor(private val plagueCity: PlagueCityQuest, private val locRepo: LocRepository) :
    PluginScript() {

    private val leftId = LEFT.asRSCM(RSCMType.LOC)
    private val rightId = RIGHT.asRSCM(RSCMType.LOC)

    override fun ScriptContext.startup() {
        onOpLoc1(LEFT) { open(it.loc) }
        onOpLoc1(RIGHT) { open(it.loc) }
    }

    private suspend fun ProtectedAccess.open(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        if (!plagueCity.quest.isQuestCompleted(player)) {
            mes("You pull on the large wooden doors...")
            delay(2)
            mes("...But they will not open.")
            return
        }
        soundSynth(OPEN_SOUND)
        for (tile in DOOR_TILES) {
            for (piece in locRepo.findAll(tile)) {
                if (piece.id == leftId || piece.id == rightId) {
                    locRepo.del(piece, OPEN_TICKS)
                }
            }
        }
        delay(1)
        walkThrough(door)
    }

    private suspend fun ProtectedAccess.walkThrough(door: BoundLocInfo) {
        val z = door.coords.z.coerceIn(GATE_MIN_Z, GATE_MAX_Z)
        val eastbound = player.coords.x <= WALL_WEST_X
        val destX = if (eastbound) EAST_SIDE_X else WEST_SIDE_X
        val startX = if (eastbound) WEST_SIDE_X else EAST_SIDE_X
        if (player.coords.x != startX || player.coords.z != z) {
            playerWalk(CoordGrid(startX, z, 0))
            if (player.coords.x != startX || player.coords.z != z) {
                teleport(CoordGrid(startX, z, 0))
                delay(1)
            }
        }
        val step = if (eastbound) 1 else -1
        var x = startX + step
        while (x != destX + step) {
            teleport(CoordGrid(x, z, 0))
            delay(1)
            x += step
        }
    }

    private companion object {
        const val LEFT = "loc.ardougnedoor_l"
        const val RIGHT = "loc.ardougnedoor_r"

        val DOOR_TILES =
            listOf(
                CoordGrid(2557, 3299, 0),
                CoordGrid(2558, 3299, 0),
                CoordGrid(2557, 3300, 0),
                CoordGrid(2558, 3300, 0),
            )

        const val WALL_WEST_X = 2557
        const val WEST_SIDE_X = 2556
        const val EAST_SIDE_X = 2559
        const val GATE_MIN_Z = 3299
        const val GATE_MAX_Z = 3300

        const val OPEN_SOUND = "synth.big_wooden_door_open"
        const val OPEN_TICKS = 15
    }
}
