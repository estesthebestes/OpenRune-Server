package org.rsmod.content.quest.area.barbarianoutpost.barcrawl

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.STAGE_STARTED
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts the barcrawl is written against: the stage values the Barbarian guard
 * multinpc switches on, the gate and pipe the guards control, and the bartenders' ops.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BarcrawlCacheTest {
    @Test
    fun questRowIsAMiniquestEndingAtTheCompleteStage() {
        val row = QuestRow.getRow("dbrow.${BarcrawlQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(0, row.questpoints)
    }

    @Test
    fun theGuardShowsToggleVialsOnlyOnceTheCrawlIsDone() {
        val guard = npc("npc.barbguard1")
        assertEquals("varp.barcrawl".asRSCM(RSCMType.VARP), guard.multiVarp)
        val transforms = checkNotNull(guard.transforms)
        val precrawl = "npc.barbguard1_precrawl".asRSCM(RSCMType.NPC)
        assertEquals(precrawl, transforms[0])
        assertEquals(precrawl, transforms[STAGE_STARTED])
        assertEquals("npc.barbguard1_postcrawl".asRSCM(RSCMType.NPC), transforms[STAGE_COMPLETE])
        assertEquals("Toggle-vials", npc("npc.barbguard1_postcrawl").actions.getOpOrNull(2))
    }

    @Test
    fun theStageSitsOnTheCacheVarpInAServerOnlyVarbit() {
        val id = BarcrawlQuest.STAGE_VARBIT.asRSCM(RSCMType.VARBIT)
        assertTrue(id in CUSTOM_IDS, id.toString())
        val varbit = checkNotNull(ServerCacheManager.getVarbit(id))
        assertEquals("varp.barcrawl".asRSCM(RSCMType.VARP), varbit.baseVar.id)
        assertEquals(0, varbit.startBit)
        assertTrue((1 shl (varbit.endBit - varbit.startBit + 1)) > STAGE_COMPLETE)
    }

    @Test
    fun theQuestListVarbitIsTheCacheOneAndHoldsTheWholeStage() {
        val varbit =
            checkNotNull(
                ServerCacheManager.getVarbit(BarcrawlQuest.PROGRESS_VARBIT.asRSCM(RSCMType.VARBIT))
            )
        assertTrue((1 shl (varbit.endBit - varbit.startBit + 1)) > STAGE_COMPLETE)
    }

    @Test
    fun eachSignatureIsItsOwnBitOnAPersistedServerOnlyVarp() {
        val state = "varp.barcrawl_state".asRSCM(RSCMType.VARP)
        assertTrue(state in CUSTOM_IDS, state.toString())
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(state)!!.scope)
        val bits = mutableSetOf<Int>()
        for (bar in BarcrawlBar.entries) {
            val id = bar.varbit.asRSCM(RSCMType.VARBIT)
            assertTrue(id in CUSTOM_IDS, "${bar.varbit} $id")
            val varbit = checkNotNull(ServerCacheManager.getVarbit(id))
            assertEquals(state, varbit.baseVar.id, bar.varbit)
            assertEquals(varbit.startBit, varbit.endBit, bar.varbit)
            assertTrue(bits.add(varbit.startBit), "${bar.varbit} shares a bit")
        }
    }

    @Test
    fun theVialSmashingToggleIsTheCacheVarbit() {
        val varbit = checkNotNull(ServerCacheManager.getVarbit("varbit.auto_smash_vials".asRSCM(RSCMType.VARBIT)))
        assertEquals(varbit.startBit, varbit.endBit)
    }

    @Test
    fun theGateFacesEastWithTheCourseBehindIt() {
        assertLocAt("loc.barbariangatel", CoordGrid(2545, 3570, 0), shape = 0, angle = EAST)
        assertLocAt("loc.barbariangater", CoordGrid(2545, 3569, 0), shape = 0, angle = EAST)
        assertLocAt("loc.agility_obstical_pipe_barbarian", CoordGrid(2552, 3559, 0))
    }

    @Test
    fun everyBartenderCanBeTalkedTo() {
        for (name in BARTENDERS) {
            assertEquals("Talk-to", npc(name).actions.getOpOrNull(0), "$name lost Talk-to")
        }
        assertEquals("Read", item(BarcrawlQuest.CARD).interfaceOptions.getOrNull(0))
    }

    @Test
    fun everyBarIsListedOnceWithItsOwnBit() {
        assertEquals(10, BarcrawlBar.entries.map { it.varbit }.distinct().size)
        assertTrue(BarcrawlBar.entries.all { it.messages.size >= 2 })
    }

    private fun npc(name: String) =
        checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "$name missing" }

    private fun item(name: String) =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { "$name missing" }

    private fun assertLocAt(loc: String, coords: CoordGrid, shape: Int? = null, angle: Int? = null) {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any {
                it.id == id &&
                    square.toCoords(it.level).translate(it.localX, it.localZ) == coords &&
                    (shape == null || it.shape == shape) &&
                    (angle == null || it.angle == angle)
            }
        assertTrue(match, "$loc is not at $coords (shape=$shape angle=$angle)")
    }

    private companion object {
        const val EAST = 2
        val CUSTOM_IDS = 64020..64039
        val BARTENDERS =
            listOf(
                "npc.bluemoon_bartender",
                "npc.blurberry",
                "npc.deadmans_bartender",
                "npc.dragon_bartender",
                "npc.flyinghorse_bartender",
                "npc.foresters_bartender",
                "npc.jollyboar_bartender",
                "npc.zembo",
                "npc.risingsun_barmaid",
                "npc.risingsun_barmaid2",
                "npc.risingsun_barmaid3",
                "npc.rustyanchor_bartender",
            )

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
