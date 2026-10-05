package org.rsmod.content.quest.area.ardougne.sheepherder

import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator

/**
 * The geometry of herding, kept free of game state so the map tests can solve every route with
 * exactly the rules the prod uses.
 *
 * A prod drives the sheep straight away from the player: along the axis on which the player is
 * further from it, or diagonally when the player stands exactly diagonal to it. It keeps going for
 * up to [PUSH_TILES] tiles and stops at the first step that a fence, wall, rock or tree blocks; it
 * never turns, so the player always knows which way it will go.
 */
object HerdingRules {
    const val PUSH_TILES = 4

    /** Pen interior: the walls stand between x 2594/2595, x 2609/2610, z 3350/3351 and z 3364/3365. */
    const val PEN_MIN_X = 2595
    const val PEN_MAX_X = 2609
    const val PEN_MIN_Z = 3351
    const val PEN_MAX_Z = 3364

    /** The two tiles directly outside the western gate leaves (2594, 3361) and (2594, 3362). */
    val GATE_THRESHOLD = listOf(CoordGrid(2594, 3361, 0), CoordGrid(2594, 3362, 0))

    fun isInPen(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in PEN_MIN_X..PEN_MAX_X && coords.z in PEN_MIN_Z..PEN_MAX_Z

    fun isThreshold(coords: CoordGrid): Boolean = coords in GATE_THRESHOLD

    /** The step direction a prod from [player] gives a sheep at [sheep], or null on the same tile. */
    fun direction(player: CoordGrid, sheep: CoordGrid): Direction? {
        val dx = sheep.x - player.x
        val dz = sheep.z - player.z
        if (dx == 0 && dz == 0) {
            return null
        }
        return when {
            abs(dx) > abs(dz) -> Direction.of(dx.sign, 0)
            abs(dz) > abs(dx) -> Direction.of(0, dz.sign)
            else -> Direction.of(dx.sign, dz.sign)
        }
    }

    /**
     * The tiles a sheep at [from] walks through when driven [dir], in order; empty when the first
     * step is blocked. The walk ends early on the gate threshold, where the sheep is penned.
     */
    fun push(
        steps: StepValidator,
        from: CoordGrid,
        dir: Direction,
        extraFlag: Int = 0,
        maxTiles: Int = PUSH_TILES,
    ): List<CoordGrid> {
        val path = mutableListOf<CoordGrid>()
        var at = from
        repeat(maxTiles) {
            if (!steps.canTravel(at.level, at.x, at.z, dir.dx, dir.dz, extraFlag = extraFlag)) {
                return path
            }
            at = at.translate(dir.dx, dir.dz)
            path += at
            if (isThreshold(at)) {
                return path
            }
        }
        return path
    }

    /** Where the player must stand to drive a sheep at [sheep] in [dir]. */
    fun standFor(sheep: CoordGrid, dir: Direction): CoordGrid = sheep.translate(-dir.dx, -dir.dz)

    enum class Direction(val dx: Int, val dz: Int, val label: String) {
        NORTH(0, 1, "north"),
        NORTH_EAST(1, 1, "north-east"),
        EAST(1, 0, "east"),
        SOUTH_EAST(1, -1, "south-east"),
        SOUTH(0, -1, "south"),
        SOUTH_WEST(-1, -1, "south-west"),
        WEST(-1, 0, "west"),
        NORTH_WEST(-1, 1, "north-west");

        val isCardinal: Boolean
            get() = dx == 0 || dz == 0

        companion object {
            fun of(dx: Int, dz: Int): Direction = entries.first { it.dx == dx && it.dz == dz }
        }
    }
}
