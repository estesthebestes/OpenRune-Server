package org.rsmod.content.areas.zeah.doors

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.generic.locs.gate.GateTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal fun BoundLocInfo.toLocInfo(): LocInfo = LocInfo(layer, coords, entity)

internal fun LocInfo.acrossTile(): CoordGrid =
    when (angle) {
        LocAngle.West -> coords.translateX(-1)
        LocAngle.North -> coords.translateZ(1)
        LocAngle.East -> coords.translateX(1)
        LocAngle.South -> coords.translateZ(-1)
    }

internal fun LocInfo.isOnLocSide(tile: CoordGrid): Boolean =
    when (angle) {
        LocAngle.West -> tile.x >= coords.x
        LocAngle.North -> tile.z <= coords.z
        LocAngle.East -> tile.x <= coords.x
        LocAngle.South -> tile.z >= coords.z
    }

internal fun crossingRoute(from: CoordGrid, wall: LocInfo): List<CoordGrid> {
    val near = if (wall.isOnLocSide(from)) wall.coords else wall.acrossTile()
    val far = if (near == wall.coords) wall.acrossTile() else wall.coords
    return listOf(near, far).dropWhile { it == from }
}

internal fun crossingTiles(from: CoordGrid, route: List<CoordGrid>): Int {
    var tiles = 0
    var previous = from
    for (waypoint in route) {
        tiles += previous.chebyshevDistance(waypoint)
        previous = waypoint
    }
    return maxOf(1, tiles)
}

internal fun crossingOpenTicks(tiles: Int): Int = maxOf(MIN_OPEN_TICKS, tiles + OPEN_TAIL_TICKS)

internal suspend fun ProtectedAccess.crossDoorway(route: List<CoordGrid>) {
    if (route.isEmpty()) {
        return
    }
    forcedWalk(route, crossTiles = crossingTiles(player.coords, route))
}

/** A closed loc and the loc it is briefly replaced by while someone passes through it. */
internal class Leaf(val closed: String, val open: String)

internal class DoubleDoor(val left: Leaf, val right: Leaf)

internal fun LocRepository.swap(
    closed: LocInfo,
    open: String,
    at: CoordGrid,
    angle: LocAngle,
    ticks: Int,
) {
    del(closed, ticks)
    add(at, open, ticks, angle, closed.shape)
}

internal fun LocRepository.openSingleDoor(door: LocInfo, open: String, ticks: Int) {
    val openCoords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
    swap(door, open, openCoords, door.turnAngle(rotations = 1), ticks)
}

/**
 * Opens both leaves of [door] the way the generic double door script does: the left leaf's
 * partner sits where the left leaf would close to, and each leaf swings out on its own side.
 */
internal fun LocRepository.openDoubleDoor(door: DoubleDoor, clicked: LocInfo, ticks: Int) {
    val clickedLeft = clicked.id == door.left.closed.asRSCM(RSCMType.LOC)
    val partnerCoords =
        if (clickedLeft) {
            DoorTranslations.translateClose(clicked.coords, clicked.shape, clicked.angle)
        } else {
            DoorTranslations.translateCloseOpposite(clicked.coords, clicked.shape, clicked.angle)
        }
    val partnerLeaf = if (clickedLeft) door.right else door.left
    val partner = findExact(partnerCoords, clicked.shape)?.takeIf {
        it.id == partnerLeaf.closed.asRSCM(RSCMType.LOC)
    }
    val left = if (clickedLeft) clicked else partner
    val right = if (clickedLeft) partner else clicked
    left?.let {
        val at = DoorTranslations.translateOpen(it.coords, it.shape, it.angle)
        swap(it, door.left.open, at, it.turnAngle(rotations = 3), ticks)
    }
    right?.let {
        val at = DoorTranslations.translateOpen(it.coords, it.shape, it.angle)
        swap(it, door.right.open, at, it.turnAngle(rotations = 1), ticks)
    }
}

/** Opens both leaves of a picket-style gate the way the generic picket gate script does. */
internal fun LocRepository.openGate(gate: DoubleDoor, clicked: LocInfo, ticks: Int) {
    val clickedLeft = clicked.id == gate.left.closed.asRSCM(RSCMType.LOC)
    val pair = GateTranslations.leftGateRightPair(clicked.shape, clicked.angle)
    val partnerCoords = if (clickedLeft) clicked.coords + pair else clicked.coords - pair
    val partnerLeaf = if (clickedLeft) gate.right else gate.left
    val partner = findExact(partnerCoords, clicked.shape)?.takeIf {
        it.id == partnerLeaf.closed.asRSCM(RSCMType.LOC)
    }
    val left = if (clickedLeft) clicked else partner
    val right = if (clickedLeft) partner else clicked
    left?.let {
        val at = it.coords + GateTranslations.leftGateOpen(it.shape, it.angle)
        swap(it, gate.left.open, at, it.turnAngle(rotations = 3), ticks)
    }
    right?.let {
        val at = it.coords + GateTranslations.rightGateOpen(it.shape, it.angle)
        swap(it, gate.right.open, at, it.turnAngle(rotations = 3), ticks)
    }
}

private const val MIN_OPEN_TICKS = 4

private const val OPEN_TAIL_TICKS = 2
