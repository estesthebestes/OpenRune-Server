package org.rsmod.content.quest.area.ardougne.plaguecity

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import java.io.File
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_FREED_ELENA
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Edmond
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Mourners
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueHouse.Companion.BASEMENT_ARRIVAL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueHouse.Companion.GROUND_ARRIVAL
import org.rsmod.events.EventBus
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.canStep
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PlagueCityCacheTest {

    @Test
    fun `the quest row matches the stages and points`() {
        val row = QuestRow.getRow("dbrow.quest_plaguecity".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
    }

    @Test
    fun `progress sits on the quest varp and the flags share one permanent server varp`() {
        val progress = checkNotNull(ServerCacheManager.getVarbit("varbit.plaguecity_progress".asRSCM()))
        assertEquals("varp.elenaquest".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertEquals(0, progress.startBit)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > STAGE_COMPLETE)

        val varp = "varp.plaguecity_state".asRSCM(RSCMType.VARP)
        assertTrue(varp <= 0xFFFF, "varbits can only sit on 16-bit varps")
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        val names =
            listOf(
                "varbit.plaguecity_buckets_poured",
                "varbit.plaguecity_told_to_dig",
                "varbit.plaguecity_met_jethick",
                "varbit.plaguecity_got_note",
                "varbit.plaguecity_read_scroll",
            )
        val bits =
            names.flatMap { name ->
                val type = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
                assertEquals(varp, type.baseVar.id, name)
                (type.startBit..type.endBit).toList()
            }
        assertEquals(bits.size, bits.distinct().size)
        val buckets = checkNotNull(ServerCacheManager.getVarbit("varbit.plaguecity_buckets_poured".asRSCM()))
        assertTrue((1 shl (buckets.endBit - buckets.startBit + 1)) > PlagueCityQuest.BUCKETS_NEEDED)
    }

    @Test
    fun `the custom ids stay inside the assigned block`() {
        val ids =
            listOf(
                "varp.plaguecity_state",
                "varbit.plaguecity_progress",
                "varbit.plaguecity_buckets_poured",
                "varbit.plaguecity_told_to_dig",
                "varbit.plaguecity_met_jethick",
                "varbit.plaguecity_got_note",
                "varbit.plaguecity_read_scroll",
            )
        for (id in ids) assertTrue(id.asRSCM() in 63900..63919, id)
    }

    @Test
    fun `the cell npc reads the quest varp and hides at the freed stage`() {
        val interactions = NpcInteractions(EventBus())
        val cell = checkNotNull(ServerCacheManager.getNpc("npc.elenap".asRSCM()))
        assertEquals("varp.elenaquest".asRSCM(RSCMType.VARP), cell.multiVarp)
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varp.elenaquest", STAGE_FREED_ELENA - 1)
        assertEquals("npc.elenap_vis".asRSCM(), interactions.multiNpc(cell, player.vars)?.id)
        VarPlayerIntMapSetter.set(player, "varp.elenaquest", STAGE_FREED_ELENA)
        assertNull(interactions.multiNpc(cell, player.vars))
    }

    @Test
    fun `edmond swaps between the garden and the sewer with one cache varbit`() {
        val interactions = NpcInteractions(EventBus())
        val top = checkNotNull(ServerCacheManager.getNpc("npc.edmond_top".asRSCM()))
        val bottom = checkNotNull(ServerCacheManager.getNpc("npc.edmond_bottom".asRSCM()))
        val player = Player()
        val varbit = "varbit.plaguecity_can_see_edmond_up_top"
        VarPlayerIntMapSetter.set(player, varbit, 0)
        assertEquals("npc.edmond".asRSCM(), interactions.multiNpc(top, player.vars)?.id)
        assertNull(interactions.multiNpc(bottom, player.vars))
        VarPlayerIntMapSetter.set(player, varbit, 1)
        assertNull(interactions.multiNpc(top, player.vars))
        assertEquals("npc.edmond".asRSCM(), interactions.multiNpc(bottom, player.vars)?.id)
    }

    @Test
    fun `every cache symbol the scripts name resolves`() {
        val dir =
            listOf("", "content/quest/")
                .map { File("${it}src/main/kotlin/org/rsmod/content/quest/area/ardougne") }
                .first { it.isDirectory }
        val pattern = Regex("\"((?:seq|synth|spotanim|loc|npc|obj|stat|varbit|varp)\\.[a-z0-9_]+)\"")
        val files = dir.walkTopDown().filter { it.extension == "kt" && !it.path.contains("clocktower") }.toList()
        assertTrue(files.size >= 10, "found only ${files.size} source files under $dir")
        val missing = mutableListOf<String>()
        for (file in files) {
            for (match in pattern.findAll(file.readText())) {
                val symbol = match.groupValues[1]
                try {
                    symbol.asRSCM()
                } catch (e: Throwable) {
                    missing += "${file.name}: $symbol"
                }
            }
        }
        assertTrue(missing.isEmpty(), "unresolved symbols: $missing")
    }

    @Test
    fun `the quest scenery stands where the scripts work it`() {
        assertPlaced("loc.plaguemudpatch2", EdmondsGarden.MUD_PATCH_TILE)
        assertPlaced("loc.plaguemudpatch1", CoordGrid(2566, 3331, 0))
        assertPlaced("loc.plaguemudpatch1", CoordGrid(2566, 3333, 0))
        assertPlaced("loc.plague_grill", ArdougneSewer.GRILL_TILE)
        assertPlaced("loc.plague_hanging_rope_multi", ArdougneSewer.GRILL_TILE)
        assertPlaced("loc.plague_straight_rope_multi", CoordGrid(2514, 9740, 0))
        assertPlaced("loc.plague_straight_rope_end_multi", CoordGrid(2514, 9741, 0))
        assertPlaced("loc.plaguesewerpipe_open", CoordGrid(2514, 9737, 0))
        assertPlaced("loc.plaguemudpile", ArdougneSewer.MUD_PILE_TILE)
        assertPlaced("loc.plaguemanholeclosed", CoordGrid(2529, 3303, 0))
        assertPlaced("loc.rehnisondoorshut", CoordGrid(2531, 3328, 0))
        assertPlaced("loc.rehnisonstairs", CoordGrid(2527, 3332, 0))
        assertPlaced("loc.rehnisonstairstop", CoordGrid(2527, 3332, 1))
        assertPlaced("loc.bravekdoorshut", CoordGrid(2530, 3314, 0))
        assertPlaced("loc.w_ardougnedoubledoorl", CoordGrid(2525, 3311, 0))
        assertPlaced("loc.w_ardougnedoubledoorr", CoordGrid(2526, 3311, 0))
        for (tile in PlagueHouse.DOOR_TILES) assertPlaced("loc.plagueelenadoorshut", tile)
        assertPlaced("loc.plaguekeybarrel", CoordGrid(2534, 3268, 0))
        assertPlaced("loc.plaguehousestairsdown", CoordGrid(2536, 3268, 0))
        assertPlaced("loc.plaguehousestairsup", CoordGrid(2536, 9671, 0))
        assertPlaced("loc.elenagateshut", CoordGrid(2539, 9672, 0))
        assertPlaced("loc.ardougnedoor_l", CoordGrid(2557, 3300, 0))
        assertPlaced("loc.ardougnedoor_r", CoordGrid(2557, 3299, 0))
    }

    @Test
    fun `the big double doors are placed with their right leaf in step`() {
        val left = placed.filter { it.id == "loc.w_ardougnedoubledoorl".asRSCM() }
        val right = placed.filter { it.id == "loc.w_ardougnedoubledoorr".asRSCM() }
        assertTrue(left.isNotEmpty() && right.isNotEmpty())
        val civic = left.single { it.coords == CoordGrid(2525, 3311, 0) }
        assertTrue(right.any { it.coords == civic.coords.translateX(1) && it.angle == civic.angle })
    }

    @Test
    fun `the arrival tiles are walkable and the sewer corridor is open`() {
        val free =
            listOf(
                EdmondsGarden.SEWER_ARRIVAL,
                ArdougneSewer.SQUARE_ARRIVAL,
                ArdougneSewer.PIPE_ARRIVAL,
                ArdougneSewer.GARDEN_ARRIVAL,
                RehnisonHouse.UPSTAIRS,
                RehnisonHouse.DOWNSTAIRS,
                BASEMENT_ARRIVAL,
                GROUND_ARRIVAL,
                Edmond.PLAYER_PULL_TILE,
                Edmond.EDMOND_PULL_TILE,
            )
        for (tile in free) {
            assertTrue(Direction.entries.any { collision.canStep(tile, it) }, "$tile is boxed in")
        }
        assertTrue(
            reachable(EdmondsGarden.SEWER_ARRIVAL, ArdougneSewer.PIPE_ARRIVAL),
            "the sewer corridor links the mud pile to the pipe",
        )
        assertTrue(reachable(Edmond.PLAYER_PULL_TILE, ArdougneSewer.PIPE_ARRIVAL), "pull tile to pipe")
        assertTrue(reachable(Edmond.EDMOND_PULL_TILE, Edmond.PLAYER_PULL_TILE), "edmond to player tile")
        assertTrue(reachable(BASEMENT_ARRIVAL, CoordGrid(2539, 9672, 0)), "basement")
        assertTrue(!collision.canStep(CoordGrid(2539, 9672, 0), Direction.East), "cell door shuts")
    }

    @Test
    fun `the doors shut off the side the scripts treat as inside`() {
        assertTrue(!collision.canStep(CoordGrid(2531, 3328, 0), Direction.North), "rehnison door")
        assertTrue(collision.canStep(CoordGrid(2531, 3329, 0), Direction.East))
        assertTrue(!collision.canStep(CoordGrid(2529, 3314, 0), Direction.East), "bravek door")
        assertTrue(!collision.canStep(CoordGrid(2533, 3272, 0), Direction.South), "plague door a")
        assertTrue(!collision.canStep(CoordGrid(2540, 3273, 0), Direction.South), "plague door b")
        assertTrue(!collision.canStep(CoordGrid(2556, 3299, 0), Direction.East), "the wall holds")
    }

    @Test
    fun `the multi npcs are spawned once, through their base type`() {
        val spawns = rawNpcSpawns()
        assertEquals(1, spawns.count { it.first == Edmond.EDMOND_TOP })
        assertEquals(1, spawns.count { it.first == Edmond.EDMOND_BOTTOM })
        assertEquals(0, spawns.count { it.first == Edmond.EDMOND_HEAD }, "a second Edmond")
        assertEquals(1, spawns.count { it.first == Mourners.HEAD_MOURNER })
        assertEquals(0, spawns.count { it.first == "npc.headmourner_vis" }, "a second Head Mourner")
        assertEquals(1, spawns.count { it.first == "npc.elenap" })
        assertEquals(1, spawns.count { it.first == "npc.jethick" })
        assertEquals(1, spawns.count { it.first == "npc.bravek" })
    }

    private fun rawNpcSpawns(): List<Pair<String, CoordGrid>> {
        val dir =
            listOf("", "../../")
                .map { File("${it}.data/raw-cache/map/npcs") }
                .first { it.isDirectory }
        val pattern =
            Regex(
                "npc = \"(npc[.][a-z0-9_]+)\"\\s*\\r?\\ncoords = " +
                    "\"(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)\""
            )
        return dir.listFiles { f -> f.name.endsWith(".toml") }!!.flatMap { file ->
            pattern
                .findAll(file.readText())
                .map {
                    val (name, level, mx, mz, lx, lz) = it.destructured
                    name to
                        CoordGrid(
                            mx.toInt() * 64 + lx.toInt(),
                            mz.toInt() * 64 + lz.toInt(),
                            level.toInt(),
                        )
                }
                .toList()
        }
    }

    private fun reachable(from: CoordGrid, to: CoordGrid): Boolean {
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty() && seen.size < 40_000) {
            val at = queue.removeFirst()
            if (at == to) return true
            for (direction in listOf(Direction.North, Direction.South, Direction.East, Direction.West)) {
                if (!collision.canStep(at, direction)) continue
                val next = at.translate(direction.xOff, direction.zOff)
                if (next.chebyshevDistance(from) > 60) continue
                if (seen.add(next)) queue.addLast(next)
            }
        }
        return false
    }

    private fun assertPlaced(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertTrue(placed.any { it.id == id && it.coords == at }, "$name is not at $at")
    }

    private companion object {
        lateinit var cache: dev.openrune.filesystem.Cache
        val collision = CollisionFlagMap()
        var placed = emptyList<PlagueCityMap.Placed>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            PlagueCityMap.load(cache)
            placed = PlagueCityMap.apply(collision, null)
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
