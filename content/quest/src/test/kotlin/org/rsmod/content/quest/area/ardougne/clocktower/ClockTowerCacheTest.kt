package org.rsmod.content.quest.area.ardougne.clocktower

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.clocktower.RatCage.Gate
import org.rsmod.content.quest.area.ardougne.clocktower.RatCage.Lever
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ClockTowerCacheTest {

    @Test
    fun `the quest row matches the stages and points`() {
        val row = QuestRow.getRow("dbrow.quest_clocktower".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertTrue(row.requirementQuests.isEmpty())
    }

    @Test
    fun `progress sits on the quest varp and the flags share one permanent server varp`() {
        val progress = checkNotNull(ServerCacheManager.getVarbit("varbit.clocktower_progress".asRSCM()))
        assertEquals("varp.cogquest".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > STAGE_COMPLETE)

        val varp = "varp.clocktower_state".asRSCM(RSCMType.VARP)
        assertTrue(varp <= 0xFFFF, "varbits can only sit on 16-bit varps")
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        val names = Cog.entries.map { it.varbit } + ClockTowerQuest.RATS_POISONED_VARBIT
        val bits =
            names.flatMap { name ->
                val type =
                    checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
                assertEquals(varp, type.baseVar.id, name)
                (type.startBit..type.endBit).toList()
            }
        assertEquals(bits.size, bits.distinct().size)
    }

    @Test
    fun `each level holds one broken spindle, of the colour that level's cog fits`() {
        assertPlaced(Cog.RED.brokenSpindle, CoordGrid(2568, 3243, 0))
        assertPlaced(Cog.BLUE.brokenSpindle, CoordGrid(2569, 3240, 1))
        assertPlaced(Cog.WHITE.brokenSpindle, CoordGrid(2567, 3241, 2))
        assertPlaced(Cog.BLACK.brokenSpindle, CoordGrid(2570, 9642, 0))
        for (cog in Cog.entries) {
            assertEquals(1, placed.count { it.id == cog.brokenSpindle.asRSCM(RSCMType.LOC) }, cog.name)
            assertEquals(3, placed.count { it.id == cog.fittedSpindle.asRSCM(RSCMType.LOC) }, cog.name)
            assertEquals("Clock spindle", loc(cog.brokenSpindle).name)
            assertEquals("Take", item(cog.obj).options.getOpOrNull(2))
        }
    }

    @Test
    fun `the levers, gates, trough and walls stand where the cage script works them`() {
        for (lever in Lever.entries) {
            assertPlaced(lever.restLoc, lever.coords)
            assertEquals("Pull", loc(lever.restLoc).actions.getOpOrNull(0))
            assertEquals("Pull", loc(lever.pulledLoc).actions.getOpOrNull(0))
            assertEquals("Open", loc(lever.gate.shutLoc).actions.getOpOrNull(0))
        }
        val outer = placed.single { it.id == Gate.OUTER.shutLoc.asRSCM(RSCMType.LOC) }
        assertEquals(Gate.OUTER.coords, outer.coords)
        assertEquals(LocShape.WallStraight.id, outer.shape)
        assertEquals(LocAngle.East.id, outer.angle)
        val inner =
            placed.single {
                it.id == RatCage.OpenGate.asRSCM(RSCMType.LOC) && it.coords == Gate.INNER.coords
            }
        assertEquals(LocAngle.South.id, inner.angle, "the inner gate rests open")
        assertTrue(placed.none { it.id == Gate.INNER.shutLoc.asRSCM(RSCMType.LOC) })
        assertPlaced(RatCage.FarGate, FAR_GATE)
        assertEquals("Go-through", loc(RatCage.FarGate).actions.getOpOrNull(0))
        assertPlaced(RatCage.Trough, CoordGrid(2586, 9654, 0))
        assertTrue(RatCage.inCage(CoordGrid(2586, 9655, 0)))

        val wall = placed.single { it.id == RatCage.CellWall.asRSCM(RSCMType.LOC) }
        assertEquals(CELL_WALL, wall.coords)
        assertEquals(LocShape.WallStraight.id, wall.shape)
        assertEquals(LocAngle.East.id, wall.angle)
        assertEquals("Push", loc(RatCage.CellWall).actions.getOpOrNull(0))
    }

    @Test
    fun `the rats live in the cage and the white cog lies beyond its far gate`() {
        val rats = rawSpawns("npcs").filter { it.first in RatCage.Rats }
        assertEquals(11, rats.size)
        assertTrue(rats.all { RatCage.inCage(it.second) })
        val objs = rawSpawns("objs")
        val white = objs.single { it.first == Cog.WHITE.obj }.second
        assertTrue(white.x < FAR_GATE.x)
        assertEquals(1, objs.count { it.first == RatCage.RatPoison })
        for (cog in Cog.entries) assertEquals(1, objs.count { it.first == cog.obj }, cog.name)
        val blue = objs.single { it.first == Cog.BLUE.obj }.second
        assertTrue(blue.x < CELL_WALL.x + 1, "the blue cog lies inside the cell the wall closes")
        assertEquals(
            CoordGrid(2569, 3249, 0),
            rawSpawns("npcs").single { it.first == ClockTowerQuest.KOJO }.second,
        )
    }

    private fun assertPlaced(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertTrue(placed.any { it.id == id && it.coords == at }, "$name is not at $at")
    }

    private fun loc(name: String) =
        checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun rawSpawns(kind: String): List<Pair<String, CoordGrid>> {
        val dir =
            listOf("", "../../")
                .map { java.io.File("$it.data/raw-cache/map/$kind") }
                .first { it.isDirectory }
        val key = if (kind == "npcs") "npc" else "obj"
        val pattern =
            Regex("$key = \"($key[.][a-z0-9_]+)\"\\s*\\r?\\ncoords = \"(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)\"")
        return dir.listFiles { f -> f.name.endsWith(".toml") }!!.flatMap { file ->
            pattern
                .findAll(file.readText())
                .map {
                    val (name, level, mx, mz, lx, lz) = it.destructured
                    name to CoordGrid(mx.toInt() * 64 + lx.toInt(), mz.toInt() * 64 + lz.toInt(), level.toInt())
                }
                .toList()
        }
    }

    private data class Placed(val id: Int, val coords: CoordGrid, val shape: Int, val angle: Int)

    private companion object {
        val FAR_GATE = CoordGrid(2579, 9656, 0)
        val CELL_WALL = CoordGrid(2575, 9631, 0)

        val SQUARES = listOf(MapSquareKey(40, 50), MapSquareKey(40, 150))

        val placed = mutableListOf<Placed>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            for (square in SQUARES) {
                val group = (square.x shl 8) or square.z
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                val collision = CollisionFlagMap()
                for (level in 0..3) for (x in square.x * 64 until square.x * 64 + 64 step 8) {
                    for (z in square.z * 64 until square.z * 64 + 64 step 8) {
                        collision.allocateIfAbsent(x, z, level)
                    }
                }
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        val loc = LocEntity(entry.intValue)
                        placed += Placed(loc.id, base.translate(key.x, key.z), loc.shape, loc.angle)
                    }
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
