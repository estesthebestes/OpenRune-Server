package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

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
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CAVE_ARRIVAL
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CAVE_EXIT
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_CANNON_FIXED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_FIND_GILOB
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.CannonStyle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DwarfCannonCacheTest {

    @Test
    fun `the quest row matches the stages and points`() {
        val row = QuestRow.getRow("dbrow.quest_dwarfcannon".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals("npc.lawgof2", row.startnpc.single().internalName)
    }

    @Test
    fun `progress sits on the quest varp in a server-only varbit inside the allotted range`() {
        val id = "varbit.dwarf_cannon_progress".asRSCM(RSCMType.VARBIT)
        assertTrue(id in 64000..64019, id.toString())
        val progress = checkNotNull(ServerCacheManager.getVarbit(id))
        assertEquals("varp.mcannon".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertEquals(0, progress.startBit)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > STAGE_COMPLETE)
    }

    @Test
    fun `the cannon style is a permanent server-only flag in the allotted range`() {
        val varp = "varp.dwarf_cannon_state".asRSCM(RSCMType.VARP)
        assertTrue(varp in 64000..64019, varp.toString())
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        val ornate = "varbit.dwarf_cannon_ornate".asRSCM(RSCMType.VARBIT)
        assertTrue(ornate in 64000..64019, ornate.toString())
        assertEquals(varp, ServerCacheManager.getVarbit(ornate)!!.baseVar.id)
    }

    @Test
    fun `the cannon state is kept in Jagex's own permanent vars`() {
        for (name in listOf("varp.dropcannon", "varp.rockthrower", "varp.ownedmcannon", "varp.mcannon")) {
            val varp = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP)))
            assertEquals(VarpLifetime.Perm, varp.scope, name)
        }
        for (name in listOf("varbit.mcannon_balltype", "varbit.mcannon_decayed")) {
            assertTrue(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT)) != null, name)
        }
    }

    @Test
    fun `the railings are bound to the cache's railing varbits`() {
        for (n in 1..6) {
            val type = loc("loc.mcannon_railing${n}_multiloc")
            assertEquals("varbit.mcannon_railing${n}_fixed".asRSCM(RSCMType.VARBIT), type.multiVarBit)
        }
    }

    @Test
    fun `the remains only show on the watchtower at the stage the script uses`() {
        val type = loc("loc.mcannonremains_multiloc")
        assertEquals("varp.mcannon".asRSCM(RSCMType.VARP), type.multiVarp)
        for (stage in 0..STAGE_COMPLETE) {
            val visible = type.multiLoc.getOrNull(stage)?.let { it >= 0 } ?: false
            assertEquals(stage == STAGE_FIND_GILOB, visible, "stage $stage")
        }
    }

    @Test
    fun `the camp cannon turns from broken to working at the repaired stage`() {
        val type = loc("loc.mcannon_cannon_multiloc")
        assertEquals("varp.mcannon".asRSCM(RSCMType.VARP), type.multiVarp)
        val working = "loc.dwarf_multicannon1".asRSCM(RSCMType.LOC)
        val broken = "loc.broken_multicannon".asRSCM(RSCMType.LOC)
        for (stage in 0 until STAGE_CANNON_FIXED) {
            assertEquals(broken, shown(type.multiLoc, type.multiDefault, stage), "stage $stage")
        }
        for (stage in STAGE_CANNON_FIXED..STAGE_COMPLETE) {
            assertEquals(working, shown(type.multiLoc, type.multiDefault, stage), "stage $stage")
        }
    }

    @Test
    fun `the multicannon locs and parts carry the options the scripts bind`() {
        val cannon = loc("loc.dwarf_multicannon1")
        assertEquals("Fire", cannon.actions.getOpOrNull(0))
        assertEquals("Pick-up", cannon.actions.getOpOrNull(1))
        assertEquals("Empty", cannon.actions.getOpOrNull(2))
        assertEquals("Load X", cannon.actions.getOpOrNull(3))
        for (style in CannonStyle.entries) {
            for (symbol in style.stageLocs + style.brokenLoc) {
                assertTrue(
                    ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)) != null,
                    symbol,
                )
            }
            for (symbol in style.parts) {
                assertTrue(ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ)) != null, symbol)
            }
        }
        assertEquals("Set-up", item("obj.twpart1").interfaceOptions.getOrNull(0))
        assertEquals("Set-up", item("obj.league_3_multicannon_base").interfaceOptions.getOrNull(0))
        for (symbol in CannonStyle.Ornate.parts) {
            assertEquals("Dismantle", item(symbol).interfaceOptions.getOrNull(3), symbol)
        }
    }

    @Test
    fun `every gameval the scripts name resolves`() {
        val synths =
            listOf(
                "tbcu_repair_fence",
                "hammering_1",
                "rogue_gear_select",
                "mousetrap_spring",
                "rogue_placegear",
                "mcannon_turn",
                "mcannon_setup",
                "mcannon_fire",
                "unlock",
                "pick",
            )
        for (name in synths) "synth.$name".asRSCM(RSCMType.SYNTH)
        val seqs =
            listOf(
                "mcannon_hammer_anim",
                "human_pickupfloor",
                "human_reachforladder",
                "human_pickuptable",
                "mcannon_interface_gun_idle",
                "mcannon_interface_gun_idle_with_spring",
                "mcannon_interface_gun_safety_on",
                "mcannon_interface_gun_safety_on_with_spring",
                "mcannon_interface_gun_attach_spring",
                "mcannon_interface_safety_off_attaching_spring",
                "mcannon_interface_gun_work_without_spring",
                "mcannon_interface_gun_work_with_spring",
                "mcannon_ne_turn",
                "mcannon_e_turn",
                "mcannon_se_turn",
                "mcannon_s_turn",
                "mcannon_sw_turn",
                "mcannon_w_turn",
                "mcannon_nw_turn",
                "mcannon_n_turn",
            )
        for (name in seqs) "seq.$name".asRSCM(RSCMType.SEQ)
        val spotanims =
            listOf(
                "cannonball_travel",
                "cannonball_travel_granite",
                "cannonball_travel_league",
                "cannonball_travel_granite_league",
                "smokepuff_huge",
            )
        for (name in spotanims) "spotanim.$name".asRSCM(RSCMType.SPOTANIM)
        val areas =
            listOf(
                "abyss",
                "ancient_cavern",
                "catacombs_of_kourend",
                "forthos_dungeon",
                "fremennik_slayer_dungeon",
                "lighthouse_dungeon",
                "jormungandprison",
                "kraken_cove",
                "molch_and_lizardman_temple",
                "revenant_caves",
                "tapoyauik",
                "cryptoftonali",
                "slayer_tower",
                "smoke_dungeon",
                "godwars_dungeon",
            )
        for (name in areas) "area.$name".asRSCM(RSCMType.AREA)
        val npcs =
            listOf(
                "king_dragon",
                "kalphite_queen",
                "kalphite_flyingqueen",
                "smoke_devil_boss",
                "myarm_giant_roc",
                "lawgof2",
                "nulodion",
                "dwarfchildtw1",
                "mcannonguard",
                "mcannonguard1",
                "mcannonguard2",
                "mcannonguard3",
                "mcannonguard4",
            )
        for (name in npcs) "npc.$name".asRSCM(RSCMType.NPC)
        "timer.dwarf_cannon_rotate".asRSCM(RSCMType.TIMER)
        "interface.mcannon_interface".asRSCM(RSCMType.INTERFACE)
        "interface.book".asRSCM(RSCMType.INTERFACE)
        val tools =
            listOf(
                "mcannon_tool1",
                "mcannon_tool2",
                "mcannon_tool3",
                "mcannon_safety",
                "mcannon_spring",
                "mcannon_gear",
                "mcannon_firing_mechanism",
            )
        for (name in tools) "component.mcannon_interface:$name".asRSCM(RSCMType.COMPONENT)
        "inv.mcannonshop".asRSCM(RSCMType.INV)
        for (name in listOf("ca_tier_status_medium", "ca_tier_status_hard", "ca_tier_status_elite")) {
            "varbit.$name".asRSCM(RSCMType.VARBIT)
        }
        val items =
            listOf(
                "obj.mcannonbook",
                "obj.mcannonremains",
                "obj.mcannontoolkit",
                "obj.nulodions_notes",
                "obj.ammo_mould",
                "obj.mcannonrailing1_obj",
                "obj.hammer",
                "obj.mcannonball",
                "obj.granite_cannonball",
                "obj.league_3_multicannon_pack",
            )
        for (name in items) item(name)
    }

    @Test
    fun `the scenery the scripts bind stands where the quest expects`() {
        assertPlaced("loc.mcannon_railing1_multiloc", CoordGrid(2555, 3479, 0))
        assertPlaced("loc.mcannon_railing2_multiloc", CoordGrid(2557, 3468, 0))
        assertPlaced("loc.mcannon_railing3_multiloc", CoordGrid(2559, 3458, 0))
        assertPlaced("loc.mcannon_railing4_multiloc", CoordGrid(2563, 3457, 0))
        assertPlaced("loc.mcannon_railing5_multiloc", CoordGrid(2573, 3457, 0))
        assertPlaced("loc.mcannon_railing6_multiloc", CoordGrid(2577, 3457, 0))
        assertPlaced("loc.mcannon_cannon_multiloc", CoordGrid(2562, 3461, 0))
        assertPlaced("loc.mcannonremains_multiloc", CoordGrid(2567, 3444, 2))
        assertPlaced("loc.mcannonladder", CoordGrid(2570, 3443, 1))
        assertPlaced("loc.mcannoncave", CoordGrid(2622, 3392, 0))
        assertPlaced("loc.mcanmudpile", CoordGrid(2621, 9796, 0))
        assertPlaced("loc.mcannoncrateboy", CoordGrid(2571, 9850, 0))
        assertPlaced("loc.mcannondoor", CoordGrid(3015, 3453, 0))
        assertPlaced("loc.mcannon_dwarf_railing_gate", CoordGrid(2555, 3475, 0))
        assertPlaced("loc.mcannon_dwarf_railing_gate_mir", CoordGrid(2555, 3474, 0))
        assertPlaced("loc.mcannon_dwarf_railing_gate", CoordGrid(2567, 3456, 0))
        assertPlaced("loc.mcannon_dwarf_railing_gate_mir", CoordGrid(2568, 3456, 0))
    }

    @Test
    fun `the quest npcs are placed in the world`() {
        val spawns = rawSpawns()
        assertTrue(spawns.any { it.first == "npc.lawgof2" && it.second == CoordGrid(2567, 3460, 0) })
        assertTrue(spawns.any { it.first == "npc.nulodion" && it.second == CoordGrid(3011, 3453, 0) })
        assertTrue(spawns.count { it.first.startsWith("npc.mcannonguard") } >= 7)
    }

    @Test
    fun `the places the quest teleports to are open ground`() {
        assertTrue(isOpen(CAVE_ARRIVAL), "cave arrival")
        assertTrue(isOpen(CAVE_EXIT), "cave exit")
        for (tile in GoblinCave.LOLLK_SPAWNS) {
            assertTrue(isOpen(tile), "Lollk spawn $tile")
        }
        assertTrue(isOpen(GoblinCave.LOLLK_ESCAPE), "Lollk escape")
    }

    @Test
    fun `the camp gates and Nulodion's door use the generic gate and door groups`() {
        val groups =
            mapOf(
                "loc.mcannon_dwarf_railing_gate_mir" to "content.closed_left_picketgate",
                "loc.mcannon_dwarf_railing_gate_mir_inact" to "content.opened_left_picketgate",
                "loc.mcannon_dwarf_railing_gate" to "content.closed_right_picketgate",
                "loc.mcannon_dwarf_railing_gate_inact" to "content.opened_right_picketgate",
                "loc.mcannondoor" to "content.closed_single_door",
                "loc.mcannondoor1" to "content.opened_single_door",
            )
        for ((name, group) in groups) {
            assertEquals(group.asRSCM(RSCMType.CONTENT), loc(name).contentGroup, name)
        }
    }

    @Test
    fun `the stockade is enclosed until a gate is opened`() {
        val outside = CoordGrid(2567, 3450, 0)
        val inside = CoordGrid(2567, 3460, 0)
        assertTrue(isOpen(inside), "tile inside the camp")
        val route =
            RouteFinding(collision)
                .findRoute(
                    level = 0,
                    srcX = outside.x,
                    srcZ = outside.z,
                    destX = inside.x,
                    destZ = inside.z,
                    moveNear = false,
                )
        assertTrue(!route.success, "the camp must only be entered through its gates")
    }

    private fun shown(multi: IntArray?, default: Int, stage: Int): Int {
        val value = multi?.getOrNull(stage) ?: default
        return if (value < 0) default else value
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

    private fun item(name: String) =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

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
        val placed = mutableListOf<Placed>()
        val collision = CollisionFlagMap()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            for ((sx, sz) in listOf(39 to 53, 39 to 54, 40 to 52, 40 to 53, 40 to 54, 40 to 153, 41 to 54, 47 to 53, 47 to 54)) {
                val square = MapSquareKey(sx, sz)
                val group = (sx shl 8) or sz
                val tileData = InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0)))
                val locData = InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1)))
                val tiles = MapTileDecoder.decode(tileData)
                val spawns = MapLocListDecoder.decode(locData)
                for (level in 0..3) for (x in sx * 64 until sx * 64 + 64 step 8) {
                    for (z in sz * 64 until sz * 64 + 64 step 8) {
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
