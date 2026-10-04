package org.rsmod.content.quest.area.ardougne.hazeelcult

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
import org.rsmod.api.config.refs.params
import org.rsmod.api.table.QuestRow
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HazeelCultCacheTest {

    @Test
    fun `the quest row matches the stages and points`() {
        val row = QuestRow.getRow("dbrow.quest_hazeelcult".asRSCM())
        assertEquals(HazeelCultQuest.Complete, row.endstate)
        assertEquals(1, row.questpoints)
        assertTrue(row.requirementQuests.isEmpty())
    }

    @Test
    fun `progress sits on the quest varp and the valves share one permanent server varp`() {
        val progress =
            checkNotNull(ServerCacheManager.getVarbit("varbit.hazeel_cult_progress".asRSCM()))
        assertEquals("varp.hazeelcultquest".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > HazeelCultQuest.Complete)

        val varp = "varp.hazeel_cult_state".asRSCM(RSCMType.VARP)
        assertTrue(varp <= 0xFFFF, "varbits can only sit on 16-bit varps")
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        val bits =
            SewerValve.entries.flatMap { valve ->
                val type =
                    checkNotNull(ServerCacheManager.getVarbit(valve.varbit.asRSCM(RSCMType.VARBIT)))
                assertEquals(varp, type.baseVar.id, valve.varbit)
                (type.startBit..type.endBit).toList()
            }
        assertEquals(bits.size, bits.distinct().size)
    }

    @Test
    fun `every custom id sits in the range handed to this quest`() {
        val ids =
            listOf(
                "varp.hazeel_cult_state".asRSCM(RSCMType.VARP),
                "varbit.hazeel_cult_progress".asRSCM(RSCMType.VARBIT),
            ) + SewerValve.entries.map { it.varbit.asRSCM(RSCMType.VARBIT) }
        assertTrue(ids.all { it in 63860..63879 }, ids.toString())
    }

    @Test
    fun `the flags are Jagex's own varbits on the secondary quest varp`() {
        val secondary = "varp.hazeelcult_secondary".asRSCM(RSCMType.VARP)
        val names =
            listOf(
                "varbit.hazeelcult_clivet_location",
                "varbit.hazeelcult_alomone_vis",
                "varbit.hazeelcult_alomone_met",
                "varbit.hazeelcult_given_armour",
                "varbit.hazeelcult_found_armour",
                "varbit.hazeelcult_jones_cutscene",
                "varbit.hazeelcult_jones_location",
                "varbit.hazeelcult_poison_success",
                "varbit.hazeelcult_given_amulet",
                "varbit.hazeelcult_sewer_chat",
                "varbit.hazeelcult_given_poison",
                "varbit.hazeelcult_given_scroll",
                "varbit.hazeelcult_hazeel_cutscene",
                "varbit.carnillean_dog_vis",
            )
        for (name in names) {
            val type = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT)))
            assertEquals(secondary, type.baseVar.id, name)
        }
    }

    @Test
    fun `the quest npcs resolve through the varbits the script sets`() {
        val expected =
            mapOf(
                "npc.clivet_hazeel_cultist" to "varbit.hazeelcult_clivet_location",
                "npc.clivet_hazeel_cultist_hideout" to "varbit.hazeelcult_clivet_location",
                "npc.alomone_hazeel_cultist" to "varbit.hazeelcult_alomone_vis",
                "npc.butler_jones_hazeel_cultist" to "varbit.hazeelcult_jones_location",
                "npc.carnillean_dog" to "varbit.carnillean_dog_vis",
            )
        for ((npc, varbit) in expected) {
            val type = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))) { npc }
            assertEquals(varbit.asRSCM(RSCMType.VARBIT), type.multiVarBit, npc)
        }
        val alomone = "npc.alomone_hazeel_cultist".asRSCM(RSCMType.NPC)
        val resolved =
            checkNotNull(ServerCacheManager.getNpc(alomone)).multiNpc.map { it.toInt() and 0xFFFF }
        assertEquals("npc.alomone_hazeel_cultist_1op".asRSCM(RSCMType.NPC), resolved[0])
        assertEquals("npc.alomone_hazeel_cultist_2op".asRSCM(RSCMType.NPC), resolved[1])
        val attackable =
            checkNotNull(ServerCacheManager.getNpc("npc.alomone_hazeel_cultist_2op".asRSCM()))
        assertEquals("Attack", attackable.actions.getOpOrNull(1))
        for (npc in listOf(alomone, "npc.alomone_hazeel_cultist_2op".asRSCM(RSCMType.NPC))) {
            val type = checkNotNull(ServerCacheManager.getNpc(npc))
            assertTrue(type.paramOrNull(params.attack_anim) != null, "attack animation of $npc")
        }
    }

    @Test
    fun `the valves the cult sets stand at the coordinates the amulet is read against`() {
        val coords =
            listOf(
                CoordGrid(2562, 3247, 0),
                CoordGrid(2572, 3263, 0),
                CoordGrid(2585, 3245, 0),
                CoordGrid(2597, 3263, 0),
                CoordGrid(2611, 3242, 0),
            )
        for ((valve, at) in SewerValve.entries.zip(coords)) {
            assertPlaced(valve.loc, at)
            assertEquals("Turn", loc(valve.loc).actions.getOpOrNull(0))
        }
        assertEquals(
            coords.map { it.x },
            coords.map { it.x }.sorted(),
            "the amulet reads the valves from west to east",
        )
    }

    @Test
    fun `the scenery the script binds stands where it expects`() {
        assertPlaced("loc.hazeelcultcave", CoordGrid(2585, 3233, 0))
        assertPlaced("loc.hazeelcultstairs", CoordGrid(2570, 9683, 0))
        assertPlaced("loc.hazeelsewerraft", HazeelSewers.RaftStart)
        assertPlaced("loc.hazeelsewerraft", CoordGrid(2578, 9686, 0))
        assertPlaced("loc.hazeelsewerraft", CoordGrid(2591, 9694, 0))
        assertPlaced("loc.hazeelsewerraft", CoordGrid(2598, 9711, 0))
        assertPlaced("loc.hazeelsewerraft", CoordGrid(2614, 9725, 0))
        assertPlaced("loc.hazeelsewerraft", CoordGrid(2606, 9693, 0))
        assertEquals(6, placed.count { it.id == "loc.hazeelsewerraft".asRSCM(RSCMType.LOC) })
        assertPlaced("loc.carnillean_ladder_down", CoordGrid(2570, 3267, 0))
        assertPlaced("loc.carnillean_ladder_up", CoordGrid(2544, 9694, 0))
        assertPlaced("loc.carnillean_stairs", CoordGrid(2568, 3268, 0))
        assertPlaced("loc.carnillean_stairstop", CoordGrid(2568, 3268, 1))
        assertPlaced("loc.carnilleanrange", CoordGrid(2538, 9699, 0))
        assertPlaced("loc.carnilleancrate", CoordGrid(2545, 9696, 0))
        assertPlaced("loc.carnilleanbookcase", CoordGrid(2572, 3270, 1))
        assertPlaced("loc.carnilleanshutchest", CoordGrid(2571, 3269, 2))
        assertPlaced("loc.hazeel_chest_closed", CoordGrid(2611, 9674, 0))
        assertPlaced("loc.hazeelcbshut", CoordGrid(2573, 3267, 1))
        assertPlaced("loc.ladder", CoordGrid(2573, 3271, 1))
        assertEquals("Search", loc("loc.carnilleanshutchest_normal").actions.getOpOrNull(0))
        assertEquals("Knock-at", loc("loc.carnilleanbookcase_knock").actions.getOpOrNull(0))
        assertEquals("Search", loc("loc.hazeelcbopen").actions.getOpOrNull(0))
        assertEquals("Shut", loc("loc.hazeelcbopen").actions.getOpOrNull(1))
        assertEquals("Open", loc("loc.hazeelcbshut").actions.getOpOrNull(0))
        assertEquals("Inspect", loc("loc.carnilleanrange").actions.getOpOrNull(0))
    }

    @Test
    fun `the cave, the raft and the stairs leave the player on open ground`() {
        val landings =
            mapOf(
                "cave entry" to HazeelSewers.CaveEntry,
                "cave exit" to HazeelSewers.CaveExit,
                "raft landing" to HazeelSewers.RaftLanding,
                "first island" to HazeelSewers.FirstIsland,
                "second island" to HazeelSewers.SecondIsland,
                "third island" to HazeelSewers.ThirdIsland,
                "fourth island" to HazeelSewers.FourthIsland,
                "hideout" to HazeelSewers.HideoutLanding,
                "house upstairs" to CoordGrid(2568, 3267, 1),
                "house ground floor" to CoordGrid(2568, 3271, 0),
                "basement" to CoordGrid(2544, 9695, 0),
                "house ladder" to CoordGrid(2570, 3268, 0),
                "hidden room" to CoordGrid(2572, 3271, 1),
                "hidden room door" to CoordGrid(2572, 3270, 1),
            )
        for ((name, at) in landings) {
            assertTrue(isOpen(at), "$name at $at is blocked")
        }
    }

    @Test
    fun `each island lies beside its raft`() {
        val rafts =
            mapOf(
                HazeelSewers.RaftLanding to HazeelSewers.RaftStart,
                HazeelSewers.FirstIsland to CoordGrid(2578, 9686, 0),
                HazeelSewers.SecondIsland to CoordGrid(2591, 9694, 0),
                HazeelSewers.ThirdIsland to CoordGrid(2598, 9711, 0),
                HazeelSewers.FourthIsland to CoordGrid(2614, 9725, 0),
                HazeelSewers.HideoutLanding to CoordGrid(2606, 9693, 0),
            )
        for ((landing, raft) in rafts) {
            val distance = maxOf(Math.abs(landing.x - raft.x), Math.abs(landing.z - raft.z))
            assertTrue(distance <= 2, "$landing is $distance tiles from the raft at $raft")
        }
    }

    @Test
    fun `the quest npcs are placed in the world`() {
        val spawns = rawSpawns()
        val expected =
            mapOf(
                "npc.sir_ceril_carnillean" to CoordGrid(2565, 3271, 0),
                "npc.clivet_hazeel_cultist" to CoordGrid(2566, 9683, 0),
                "npc.alomone_hazeel_cultist" to CoordGrid(2608, 9671, 0),
                "npc.butler_jones_hazeel_cultist" to CoordGrid(2566, 3268, 0),
            )
        for ((npc, at) in expected) {
            assertTrue(spawns.any { it.first == npc && it.second == at }, "$npc at $at")
        }
        for (npc in
            listOf(
                "npc.claus_carnillean",
                "npc.philipe_carnillean",
                "npc.carnillean_wife",
                "npc.guard_carnillean",
                "npc.clivet_hazeel_cultist_hideout",
                "npc.hazeel_cultist",
                "npc.hazeel_cultist_2",
                "npc.carnillean_dog",
            )) {
            assertTrue(spawns.any { it.first == npc }, npc)
        }
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
        val SQUARES =
            listOf(
                MapSquareKey(40, 50),
                MapSquareKey(40, 51),
                MapSquareKey(40, 151),
                MapSquareKey(39, 151),
            )

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
