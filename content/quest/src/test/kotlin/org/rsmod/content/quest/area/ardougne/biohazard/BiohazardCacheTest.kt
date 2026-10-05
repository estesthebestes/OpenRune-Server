package org.rsmod.content.quest.area.ardougne.biohazard

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import java.io.File
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_HQ_REFUSED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_MET_OMART
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.canStep
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BiohazardCacheTest {

    @Test
    fun `the quest row matches the stages and points`() {
        val row = QuestRow.getRow("dbrow.quest_biohazard".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(3, row.questpoints)
    }

    @Test
    fun `progress sits on the quest varp and the flags share one permanent server varp`() {
        val progress = checkNotNull(ServerCacheManager.getVarbit("varbit.biohazard_progress".asRSCM()))
        assertEquals("varp.biohazard".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertEquals(0, progress.startBit)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > STAGE_COMPLETE)

        val varp = "varp.biohazard_state".asRSCM(RSCMType.VARP)
        assertTrue(varp <= 0xFFFF, "varbits can only sit on 16-bit varps")
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        val names =
            listOf(
                "varbit.biohazard_birdfeed_thrown",
                "varbit.biohazard_chancy_vial",
                "varbit.biohazard_davinci_vial",
                "varbit.biohazard_hops_vial",
                "varbit.biohazard_dummy_hits",
                "varbit.biohazard_met_jerico",
            )
        val bits =
            names.flatMap { name ->
                val type = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
                assertEquals(varp, type.baseVar.id, name)
                (type.startBit..type.endBit).toList()
            }
        assertEquals(bits.size, bits.distinct().size)
        for (name in listOf("chancy", "davinci", "hops")) {
            val vial = checkNotNull(ServerCacheManager.getVarbit("varbit.biohazard_${name}_vial".asRSCM()))
            assertTrue((1 shl (vial.endBit - vial.startBit + 1)) > VIAL_SULPHURIC_BROLINE, name)
        }
        val hits = checkNotNull(ServerCacheManager.getVarbit("varbit.biohazard_dummy_hits".asRSCM()))
        assertTrue((1 shl (hits.endBit - hits.startBit + 1)) > 6)
    }

    @Test
    fun `the chat flags are the cache's own varbits on one shared varp`() {
        val names =
            listOf("met_omart", "met_julie", "free_clothes", "postquest_chat")
                .map { "varbit.biohazard_$it" }
        val types = names.map { checkNotNull(ServerCacheManager.getVarbit(it.asRSCM())) { it } }
        assertEquals(1, types.map { it.baseVar.id }.distinct().size)
        assertEquals("varp.elenaquest_extra_bits".asRSCM(RSCMType.VARP), types.first().baseVar.id)
    }

    @Test
    fun `the custom ids stay inside the assigned block`() {
        val ids =
            listOf(
                "varp.biohazard_state",
                "varbit.biohazard_progress",
                "varbit.biohazard_birdfeed_thrown",
                "varbit.biohazard_chancy_vial",
                "varbit.biohazard_davinci_vial",
                "varbit.biohazard_hops_vial",
                "varbit.biohazard_dummy_hits",
                "varbit.biohazard_met_jerico",
            )
        for (id in ids) assertTrue(id.asRSCM() in 63920..63939, id)
    }

    @Test
    fun `the watchtower and the cauldron only offer their option at the cache stages`() {
        val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), EventBus())
        fun option(symbol: String, stage: Int): String? {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val info =
                LocInfo(
                    LocLayerConstants.of(LocShape.CentrepieceStraight.id),
                    CoordGrid(2560, 3304, 0),
                    LocEntity(type.id, LocShape.CentrepieceStraight.id, LocAngle.West.id),
                )
            val player = Player()
            VarPlayerIntMapSetter.set(player, "varp.biohazard", stage)
            val shown =
                interactions.multiLoc(BoundLocInfo(info, type), type, player.vars) ?: return null
            return ServerCacheManager.getObject(shown.id)?.actions?.ops?.firstOrNull()?.text
        }
        for (stage in 0..STAGE_COMPLETE) {
            assertEquals(
                if (stage == STAGE_MET_OMART) "Investigate" else null,
                option("loc.biowatchtower", stage),
                "watchtower at $stage",
            )
            assertEquals(
                if (stage == STAGE_HQ_REFUSED) "Inspect" else null,
                option("loc.mournercauldron", stage),
                "cauldron at $stage",
            )
        }
    }

    @Test
    fun `Elena's house npc shows only once she is home`() {
        val interactions = NpcInteractions(EventBus())
        val elena = checkNotNull(ServerCacheManager.getNpc("npc.elena2".asRSCM()))
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varbit.plaguecity_elena_at_home", 0)
        assertNull(interactions.multiNpc(elena, player.vars))
        VarPlayerIntMapSetter.set(player, "varbit.plaguecity_elena_at_home", 1)
        assertEquals("npc.elena2_vis".asRSCM(), interactions.multiNpc(elena, player.vars)?.id)
    }

    @Test
    fun `the quest npcs are spawned once through their base types`() {
        val spawns = rawNpcSpawns()
        for (name in
            listOf(
                "npc.elena2",
                "npc.jerico",
                "npc.omart",
                "npc.kilron",
                "npc.bionurse",
                "npc.mourner_armed_guard",
                "npc.mournerstew2",
                "npc.chemist",
                "npc.gambler1",
                "npc.gambler2",
                "npc.artist1",
                "npc.artist2",
                "npc.drunk1",
                "npc.drunk2",
                "npc.bioguard1",
                "npc.guidors_wife",
                "npc.guidor",
                "npc.kinglathas",
            )) {
            assertEquals(1, spawns.count { it.first == name }, name)
        }
        for (name in listOf("npc.omart_vis", "npc.kilron_vis", "npc.kinglathas_vis", "npc.elena2_vis")) {
            assertEquals(0, spawns.count { it.first == name }, "a second $name")
        }
    }

    @Test
    fun `the scenery stands where the scripts work it`() {
        assertPlaced("loc.biowatchtower", CoordGrid(2559, 3301, 0))
        assertPlaced("loc.mournercauldron", CoordGrid(2543, 3332, 0))
        assertPlaced("loc.mournerstewfence", CoordGrid(2541, 3331, 0))
        assertPlaced("loc.mournerstewdoor", CoordGrid(2551, 3320, 0))
        assertPlaced("loc.mournerstewdoor", CoordGrid(2551, 3328, 0))
        assertPlaced("loc.mournerstewdoorup", CoordGrid(2547, 3325, 1))
        assertPlaced("loc.mournerquaters_gatel", CoordGrid(2551, 3326, 1))
        assertPlaced("loc.mournerquaters_gater", CoordGrid(2551, 3325, 1))
        assertPlaced("loc.mournercrateup", CoordGrid(2554, 3327, 1))
        assertPlaced("loc.bionursescupboardshut", CoordGrid(2517, 3276, 0))
        assertPlaced("loc.jericoscupboardshut", CoordGrid(2611, 3326, 0))
        assertPlaced("loc.elenadoor2", CoordGrid(2592, 3339, 0))
        assertPlaced("loc.guidorgatelclosed", CoordGrid(3264, 3405, 0))
        assertPlaced("loc.guidorgaterclosed", CoordGrid(3264, 3406, 0))
        assertPlaced("loc.guidordoor", CoordGrid(3282, 3382, 0))
        assertPlaced("loc.lathastraining_gatel", CoordGrid(2517, 3356, 0))
        assertPlaced("loc.lathastraining_gater", CoordGrid(2518, 3356, 0))
        assertPlaced("loc.biohazardlooserailing", CoordGrid(2522, 3375, 0))
        assertEquals(13, placed.count { it.id == "loc.biohazarddummy".asRSCM(RSCMType.LOC) })
    }

    @Test
    fun `the closed doors and gates shut off the side the scripts treat as outside`() {
        assertTrue(!collision.canStep(CoordGrid(3263, 3405, 0), Direction.East), "guidor gate")
        assertTrue(!collision.canStep(CoordGrid(3263, 3406, 0), Direction.East), "guidor gate")
        assertTrue(!collision.canStep(CoordGrid(2551, 3320, 0), Direction.North), "hq front door")
        assertTrue(!collision.canStep(CoordGrid(2551, 3328, 0), Direction.South), "hq back door")
        assertTrue(!collision.canStep(CoordGrid(2551, 3325, 1), Direction.East), "hq cage gate")
        assertTrue(!collision.canStep(CoordGrid(2517, 3356, 0), Direction.North), "camp gate")
        assertTrue(!collision.canStep(CoordGrid(3282, 3382, 0), Direction.East), "guidor bedroom")
        assertTrue(!collision.canStep(CoordGrid(2541, 3331, 0), Direction.East), "hq fence")
    }

    @Test
    fun `the pigeon cages sit on open ground`() {
        for (z in 3323..3325) {
            val tile = CoordGrid(2618, z, 0)
            assertTrue(Direction.entries.any { collision.canStep(tile, it) }, "$tile is boxed in")
        }
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

    private fun assertPlaced(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertNotNull(placed.firstOrNull { it.id == id && it.coords == at }, "$name is not at $at")
    }

    private companion object {
        lateinit var cache: dev.openrune.filesystem.Cache
        val collision = CollisionFlagMap()
        var placed = emptyList<BiohazardMap.Placed>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            BiohazardMap.load(cache)
            placed = BiohazardMap.apply(collision, null)
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
