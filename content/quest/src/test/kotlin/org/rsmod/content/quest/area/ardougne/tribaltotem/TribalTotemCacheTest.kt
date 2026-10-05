package org.rsmod.content.quest.area.ardougne.tribaltotem

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
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.DepotLanding
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.MansionLanding
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.SewerLanding
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.StairsTop
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TribalTotemCacheTest {

    @Test
    fun `the quest row matches the stages and points`() {
        val row = QuestRow.getRow("dbrow.quest_tribaltotem".asRSCM())
        assertEquals(TribalTotemQuest.Complete, row.endstate)
        assertEquals(1, row.questpoints)
        assertTrue(row.requirementQuests.isEmpty())
    }

    @Test
    fun `progress sits on the quest varp and the id is in the range handed to this quest`() {
        val id = "varbit.tribal_totem_progress".asRSCM(RSCMType.VARBIT)
        assertTrue(id in 63960..63979, id.toString())
        val progress = checkNotNull(ServerCacheManager.getVarbit(id))
        assertEquals("varp.totemquest".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > TribalTotemQuest.Complete)
    }

    @Test
    fun `the lock dials are Jagex permanent varbits and hold every letter`() {
        val varp = "varp.totemquest_combodoor_code".asRSCM(RSCMType.VARP)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        val bits = mutableListOf<Int>()
        for (index in 1..4) {
            val name = "varbit.totemquest_combodoor_code$index"
            val type = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT)))
            assertEquals(varp, type.baseVar.id, name)
            assertTrue((1 shl (type.endBit - type.startBit + 1)) >= 26, name)
            bits += (type.startBit..type.endBit).toList()
        }
        assertEquals(bits.size, bits.distinct().size)
    }

    @Test
    fun `the lock interface has the dials, arrows and enter button the script drives`() {
        for (dial in listOf("a", "b", "c", "d")) {
            for (suffix in listOf("", "_left", "_right")) {
                "component.rd_combolock:rd$dial$suffix".asRSCM(RSCMType.COMPONENT)
            }
        }
        "component.rd_combolock:rdenter".asRSCM(RSCMType.COMPONENT)
        "interface.rd_combolock".asRSCM(RSCMType.INTERFACE)
    }

    @Test
    fun `the quest items and npcs exist with the ops the script binds`() {
        for (obj in listOf("obj.tribal_totem", "obj.tribal_totem_label", "obj.swordfish")) {
            checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { obj }
        }
        for (npc in
            listOf(
                "npc.kangai_mau",
                "npc.horacio",
                "npc.rpdt_employee",
                "npc.cromperty_pre_diary",
                "npc.cromperty_post_diary",
            )) {
            val type = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))) { npc }
            assertEquals("Talk-to", type.actions.getOpOrNull(0), npc)
        }
        for (cromperty in listOf("npc.cromperty_pre_diary", "npc.cromperty_post_diary")) {
            val type = checkNotNull(ServerCacheManager.getNpc(cromperty.asRSCM(RSCMType.NPC)))
            assertEquals("Teleport", type.actions.getOpOrNull(2), cromperty)
        }
    }

    @Test
    fun `the scenery the script binds stands where it expects`() {
        assertPlaced("loc.horncrate", CoordGrid(2650, 3273, 0))
        assertPlaced("loc.teleportcrate", CoordGrid(2650, 3271, 0))
        assertPlaced("loc.teleportcrate", CoordGrid(2638, 3320, 0))
        assertPlaced("loc.tribaltotemdoor", CoordGrid(2635, 3321, 0))
        assertPlaced("loc.combodoor", CoordGrid(2634, 3323, 0))
        assertPlaced("loc.totemtrapstairs", CoordGrid(2631, 3322, 0))
        assertPlaced("loc.totemshutchest", CoordGrid(2638, 3324, 1))
        assertEquals("Investigate", loc("loc.horncrate").actions.getOpOrNull(1))
        assertEquals("Investigate", loc("loc.teleportcrate").actions.getOpOrNull(1))
        assertEquals("Open", loc("loc.combodoor").actions.getOpOrNull(0))
        assertEquals("Open", loc("loc.tribaltotemdoor").actions.getOpOrNull(0))
        assertEquals("Climb-up", loc("loc.totemtrapstairs").actions.getOpOrNull(0))
        assertEquals("Investigate", loc("loc.totemtrapstairs").actions.getOpOrNull(1))
        assertEquals("Open", loc("loc.totemshutchest").actions.getOpOrNull(0))
        assertEquals("Search", loc("loc.totemopenchest").actions.getOpOrNull(0))
        assertEquals("Close", loc("loc.totemopenchest").actions.getOpOrNull(1))
        assertEquals(2, placed.count { it.id == "loc.teleportcrate".asRSCM(RSCMType.LOC) })
    }

    @Test
    fun `the lock sits on the second of the two doors on the way west`() {
        assertPlaced("loc.poshdoor", CoordGrid(2636, 3323, 0))
        assertPlaced("loc.combodoor", CoordGrid(2634, 3323, 0))
        assertEquals("Door", loc("loc.poshdooropen").name)
    }

    @Test
    fun `the teleport and stair landings leave the player on open ground`() {
        val landings =
            mapOf(
                "depot" to DepotLanding,
                "mansion" to MansionLanding,
                "stairs top" to StairsTop,
                "sewer" to SewerLanding,
                "sewer ladder bottom" to CoordGrid(2633, 9694, 0),
            )
        for ((name, at) in landings) {
            assertTrue(isOpen(at), "$name at $at is blocked")
        }
        assertTrue(maxOf(Math.abs(MansionLanding.x - 2638), Math.abs(MansionLanding.z - 3320)) <= 1)
        assertTrue(maxOf(Math.abs(DepotLanding.x - 2650), Math.abs(DepotLanding.z - 3271)) <= 1)
    }

    @Test
    fun `the stairs down from the landing come out in the stairs room`() {
        assertPlaced("loc.stairstop", CoordGrid(2631, 3322, 1))
        val down = checkNotNull(ServerCacheManager.getObject("loc.stairstop".asRSCM(RSCMType.LOC)))
        val landing = CoordGrid(2631 + (down.width - 2), 3322 + (down.length + 1), 0)
        assertTrue(isOpen(landing), "the generic stairs down land on $landing")
    }

    @Test
    fun `the sewer drop lies in the sewer that the cellar ladder leads out of`() {
        assertPlaced("loc.ladder_from_cellar", CoordGrid(2632, 9694, 0))
        assertTrue(SewerLanding.z in 9690..9700 && SewerLanding.x in 2630..2650)
    }

    @Test
    fun `the quest npcs are placed in the world`() {
        val spawns = rawSpawns()
        val expected =
            mapOf(
                "npc.kangai_mau" to CoordGrid(2791, 3182, 0),
                "npc.horacio" to CoordGrid(2635, 3311, 0),
                "npc.cromperty_pre_diary" to CoordGrid(2685, 3324, 0),
            )
        for ((npc, at) in expected) {
            assertTrue(spawns.any { it.first == npc && it.second == at }, "$npc at $at")
        }
        assertTrue(isOpen(CoordGrid(2685, 3324, 0)), "Cromperty's tile is blocked")
        assertEquals(3, spawns.count { it.first == "npc.rpdt_employee" })
    }

    private fun isOpen(at: CoordGrid): Boolean {
        val flags = collision[at.x, at.z, at.level]
        val blocked = CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR
        return flags and blocked == 0
    }

    private fun assertPlaced(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertTrue(placed.any { it.id == id && it.coords == at }, "$name is not at $at")
    }

    private fun loc(name: String) =
        checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun rawSpawns(): List<Pair<String, CoordGrid>> {
        val dir =
            listOf("", "../../")
                .map { java.io.File("$it.data/raw-cache/map/npcs") }
                .first { it.isDirectory }
        val pattern =
            Regex(
                "npc = \"(npc[.][a-z0-9_]+)\"\\s*\\r?\\n" +
                    "coords = \"(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)\""
            )
        return dir.listFiles { f -> f.name.endsWith(".toml") }!!.flatMap { file ->
            pattern
                .findAll(file.readText())
                .map {
                    val (name, level, mx, mz, lx, lz) = it.destructured
                    val x = mx.toInt() * 64 + lx.toInt()
                    val z = mz.toInt() * 64 + lz.toInt()
                    name to CoordGrid(x, z, level.toInt())
                }
                .toList()
        }
    }

    private data class Placed(val id: Int, val coords: CoordGrid)

    private companion object {
        val SQUARES = listOf(MapSquareKey(41, 51), MapSquareKey(41, 151))

        val placed = mutableListOf<Placed>()
        val collision = CollisionFlagMap()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            for (square in SQUARES) {
                val group = (square.x shl 8) or square.z
                val tileData = InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0)))
                val locData = InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1)))
                val tiles = MapTileDecoder.decode(tileData)
                val spawns = MapLocListDecoder.decode(locData)
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
                        placed += Placed(loc.id, base.translate(key.x, key.z))
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
