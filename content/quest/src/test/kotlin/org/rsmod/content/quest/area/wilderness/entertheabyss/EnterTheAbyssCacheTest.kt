package org.rsmod.content.quest.area.wilderness.entertheabyss

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
import kotlin.math.abs
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.QUEST_KEY
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.readingVarbit
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.content.skills.runecrafting.essence.EssencePortals
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins Enter the Abyss to the cache and the map: the miniquest row, the stage varp and reading
 * varbits, both Mage of Zamorak multinpcs and every teleporter's ops, the items, shops and
 * bracelets the scripts name, the spawns, the mine portal for every teleporter, each return tile,
 * and the Abyss itself: every gate's layouts, frames, front and inside tiles, and every rift.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class EnterTheAbyssCacheTest {

    @Test fun `the miniquest row has no quest points, 1,000 Runecraft xp and Rune Mysteries`() {
        val row = QuestRow.getRow("dbrow.$QUEST_KEY".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(0, row.questpoints)
        assertEquals(
            mapOf("runecrafting" to (EnterTheAbyssQuest.RUNECRAFT_XP * 10).toInt()),
            row.statXpAwarded.associate { it.t0.displayName to it.t1 },
        )
        assertEquals(listOf("dbrow.quest_runemysteries".asRSCM()), row.requirementQuests.map { it.rowId })
        assertTrue(row.requirementStats.isEmpty(), "no skill requirement")
    }

    @Test fun `progress sits on the quest varp and every custom id is in the range handed to this port`() {
        val id = EnterTheAbyssQuest.PROGRESS_VARBIT.asRSCM(RSCMType.VARBIT)
        assertTrue(id in 64080..64099, id.toString())
        val progress = varbit(EnterTheAbyssQuest.PROGRESS_VARBIT)
        assertEquals("varp.abyssal_miniquest".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertEquals(0, progress.startBit)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > STAGE_COMPLETE)
        for (name in listOf("varbit.rc_kourend_blood_crafted", "varbit.rc_kourend_soul_crafted")) {
            assertTrue(name.asRSCM(RSCMType.VARBIT) in 64080..64099, name)
            val crafted = varbit(name)
            assertEquals(crafted.startBit, crafted.endBit)
            assertEquals("varp.generic_storage_65531".asRSCM(RSCMType.VARP), crafted.baseVar.id)
        }
    }

    @Test fun `stage, readings and dialogue flags are permanent and never overlap`() {
        assertEquals(VarpLifetime.Perm, varp("varp.abyssal_miniquest").scope)
        val readings = EssenceMineTeleporter.entries.map { varbit(readingVarbit(it)) }
        assertEquals(EssenceMineTeleporter.entries.size, readings.map { it.startBit }.toSet().size)
        for (reading in readings) {
            assertEquals("varp.abyssal_warp".asRSCM(RSCMType.VARP), reading.baseVar.id)
            assertEquals(reading.startBit, reading.endBit, "one bit per source")
        }
        assertEquals(VarpLifetime.Perm, varp("varp.abyssal_warp").scope)
        val flags =
            listOf("intro", "reconsider", "orb", "reward").map { varbit("varbit.abyssal_miniquest_$it") }
        assertEquals(flags.size, flags.map { it.startBit }.toSet().size)
        for (flag in flags) assertEquals("varp.runemysteries_secondary".asRSCM(RSCMType.VARP), flag.baseVar.id)
        assertEquals(VarpLifetime.Perm, varp("varp.runemysteries_secondary").scope)
    }

    @Test fun `the wilderness mage only offers Teleport once the miniquest is complete`() {
        val base = npc("npc.rcu_zammy_mage1")
        assertEquals("varp.abyssal_miniquest".asRSCM(RSCMType.VARP), base.multiVarp)
        val transforms = checkNotNull(base.transforms)
        for (stage in 0..STAGE_COMPLETE) {
            val actions = npc(transforms[stage]).actions
            assertEquals("Talk-to", actions.getOpOrNull(0), "stage $stage")
            assertEquals("Trade", actions.getOpOrNull(2), "stage $stage")
            assertEquals(if (stage == STAGE_COMPLETE) "Teleport" else null, actions.getOpOrNull(3), "stage $stage")
        }
    }

    @Test fun `the Varrock temple mage only exists once the player has been sent there`() {
        val base = npc("npc.rcu_zammy_mage1_edge")
        assertEquals("varp.abyssal_miniquest".asRSCM(RSCMType.VARP), base.multiVarp)
        val transforms = checkNotNull(base.transforms)
        assertEquals(-1, transforms[0])
        for (stage in EnterTheAbyssQuest.STAGE_SENT_TO_VARROCK..STAGE_COMPLETE) {
            val type = npc(transforms[stage])
            assertEquals("Mage of Zamorak", type.name)
            assertEquals("Talk-to", type.actions.getOpOrNull(0))
        }
    }

    @Test fun `every teleporter shows Teleport on the op its script handles`() {
        val ops =
            mapOf(
                "npc.head_wizard" to 2,
                "npc.aubury" to 3,
                "npc.guild_wizard" to 2,
                "npc.gnome_brimstail" to 2,
            )
        for ((name, index) in ops) {
            val base = npc(name)
            assertEquals("varp.runemysteries".asRSCM(RSCMType.VARP), base.multiVarp, name)
            val complete = npc(checkNotNull(base.transforms)[6])
            assertEquals("Teleport", complete.actions.getOpOrNull(index), name)
            val started = npc(checkNotNull(base.transforms)[5])
            assertEquals(null, started.actions.getOpOrNull(index), "$name hides Teleport before Rune Mysteries")
        }
        for (name in listOf("npc.cromperty_pre_diary", "npc.cromperty_post_diary")) {
            assertEquals("Teleport", npc(name).actions.getOpOrNull(2), name)
        }
    }

    @Test fun `the orb, rewards, shops and bracelets are the items the scripts name`() {
        for (orb in listOf(EnterTheAbyssQuest.EMPTY_ORB, EnterTheAbyssQuest.FULL_ORB)) {
            val type = item(orb)
            assertEquals("Scrying orb", type.name)
            assertEquals("Destroy", type.interfaceOptions[4])
        }
        assertNotEquals(item(EnterTheAbyssQuest.EMPTY_ORB).examine, item(EnterTheAbyssQuest.FULL_ORB).examine)
        assertEquals("Abyssal book", item(EnterTheAbyssQuest.ABYSSAL_BOOK).name)
        assertEquals("Small pouch", item(EnterTheAbyssQuest.SMALL_POUCH).name)
        for ((index, bracelet) in AbyssTeleport.BRACELETS.withIndex()) {
            assertEquals("Abyssal bracelet(${5 - index})", item(bracelet).name)
        }
        for (shop in listOf("inv.darkruneshop_crap", "inv.darkruneshop_uber")) {
            assertNotNull(ServerCacheManager.getInventory(shop.asRSCM(RSCMType.INV)), shop)
        }
    }

    @Test fun `every teleporter's portal value shows an exit portal in the mine`() {
        val portal = checkNotNull(ServerCacheManager.getObject("loc.blankrunestone_exit_portal".asRSCM(RSCMType.LOC)))
        assertEquals(EssencePortals.PORTAL_VARBIT.asRSCM(RSCMType.VARBIT), portal.multiVarBit)
        val transforms = checkNotNull(portal.transforms)
        for (teleporter in EssenceMineTeleporter.entries) {
            assertNotEquals(-1, transforms[teleporter.portal], teleporter.name)
        }
        assertEquals(EssenceMineTeleporter.entries.size, EssenceMineTeleporter.entries.map { it.portal }.toSet().size)
    }

    @Test fun `the mages and teleporters are spawned where the scripts expect them`() {
        assertEquals(listOf(CoordGrid(3106, 3558)), spawns("npc.rcu_zammy_mage1"))
        val temple = spawns("npc.rcu_zammy_mage1_edge").single()
        assertTrue(temple.x in 3253..3264 && temple.z in 3380..3391, "inside the Chaos Temple: $temple")
        assertEquals(listOf(CoordGrid(2685, 3324)), spawns("npc.cromperty_pre_diary"))
        assertEquals(listOf(CoordGrid(2409, 9817)), spawns("npc.gnome_brimstail"))
        val distentor = spawns("npc.guild_wizard").single()
        assertTrue(distentor.x in 2585..2596, "inside the Wizards' Guild: $distentor")
    }

    @Test fun `every return tile is walkable beside its teleporter`() {
        for (teleporter in EssenceMineTeleporter.entries) {
            assertTrue(open(teleporter.returnCoord), "${teleporter.name} return ${teleporter.returnCoord}")
        }
    }

    @Test fun `the outer and inner rings are only joined through the obstacles`() {
        val outer = reachable(AbyssGate.entries.first().front)
        assertTrue(outer.size > 500, "the outer ring, not a pocket: ${outer.size}")
        assertFalse(INNER_RING in outer, "the obstacles separate the rings")
        assertTrue(reachable(INNER_RING).size > 100, "the inner ring exists")
    }

    @Test fun `every gate is a multiloc on the layout varbit, placed by its front tile`() {
        val outer = reachable(AbyssGate.entries.first().front)
        val inner = reachable(INNER_RING)
        for (gate in AbyssGate.entries) {
            val type = loc(gate.loc)
            assertEquals(AbyssGate.LAYOUT_VARBIT.asRSCM(RSCMType.VARBIT), type.multiVarBit, gate.name)
            val placedAt = placedLocs.filter { it.first == gate.loc.asRSCM(RSCMType.LOC) }.map { it.second }
            assertEquals(1, placedAt.size, "${gate.name} placed once")
            val origin = placedAt.single()
            assertTrue(abs(gate.front.x - origin.x) <= 3 && abs(gate.front.z - origin.z) <= 3, "${gate.name} front ${gate.front} by $origin")
            assertTrue(open(gate.front) && gate.front in outer, "${gate.name} front ${gate.front} on the outer ring")
            assertTrue(open(gate.inside) && gate.inside in inner, "${gate.name} inside ${gate.inside} on the inner ring")
        }
    }

    @Test fun `each layout has one blockage at its own gate and exactly one passage`() {
        for (layout in AbyssGate.LAYOUTS) {
            val shown = AbyssGate.entries.map { it to shownName(it, layout) }
            val blocked = shown.filter { it.second == "loc.rcu_outer_entrance_blocksquare" }.map { it.first }
            assertEquals(listOf(AbyssGate.entries[layout]), blocked, "layout $layout")
            assertEquals(1, AbyssGate.entries.count { it.obstacle(layout) == AbyssObstacle.Passage }, "layout $layout")
            assertEquals(10, AbyssGate.entries.count { it.obstacle(layout).let { o -> o != null && o != AbyssObstacle.Passage } }, "layout $layout")
        }
    }

    @Test fun `obstacles show the op the script handles and clear through their own frames`() {
        val ops =
            mapOf(
                AbyssObstacle.Rock to "Mine",
                AbyssObstacle.Tendrils to "Chop",
                AbyssObstacle.Boil to "Burn-down",
                AbyssObstacle.Eyes to "Distract",
                AbyssObstacle.Gap to "Squeeze-through",
                AbyssObstacle.Passage to "Go-through",
            )
        for ((obstacle, op) in ops) assertEquals(op, loc(obstacle.shown).actions.getOpOrNull(0), obstacle.name)
        val frames =
            mapOf(
                AbyssObstacle.Rock to listOf("teeth2", "teeth3"),
                AbyssObstacle.Tendrils to listOf("tendrils2", "tendrils3"),
                AbyssObstacle.Boil to listOf("boil2", "boil3"),
                AbyssObstacle.Eyes to listOf("eyes2", "eyes3"),
            )
        for (gate in AbyssGate.entries) {
            for ((obstacle, names) in frames) {
                assertEquals(names.map { "loc.rcu_abyssal_barrier_$it" }, obstacle.clearing.map { shownName(gate, it) }, "${gate.name} ${obstacle.name}")
            }
        }
        for (seq in listOf("seq.human_mining_bronze_pickaxe", "seq.human_woodcutting_bronze_axe", "seq.human_createfire", "seq.sanctuary", "seq.human_crawling")) {
            seq.asRSCM(RSCMType.SEQ)
        }
    }

    @Test fun `every rift opens into its altar from the inner ring`() {
        val inner = reachable(INNER_RING)
        for (rift in AbyssRift.entries) {
            if (rift != AbyssRift.Blood) assertEquals("Exit-through", loc(rift.loc).actions.getOpOrNull(0), rift.name)
            assertNotNull(rift.entrance(), "${rift.name} altar entrance")
            val at = placedLocs.single { it.first == rift.loc.asRSCM(RSCMType.LOC) }.second
            val reach = (-2..2).flatMap { dx -> (-2..2).map { dz -> at.translate(dx, dz) } }
            assertTrue(reach.any { it in inner }, "${rift.name} at $at stands on the inner ring")
        }
    }

    @Test fun `the blood rift shows the last-used altar first and the soul rift has one exit`() {
        val parent = loc(AbyssRift.Blood.loc)
        assertEquals(AbyssRifts.LAST_BLOOD_RIFT.asRSCM(RSCMType.VARBIT), parent.multiVarBit)
        val children = checkNotNull(parent.transforms).filter { it != -1 }.map { checkNotNull(ServerCacheManager.getObject(it)) }
        assertEquals(listOf("Exit-through", "Exit-through (Kourend)"), (0..1).map { children[AbyssRifts.TRUE_ALTAR].actions.getOpOrNull(it) })
        assertEquals(listOf("Exit-through (Kourend)", "Exit-through"), (0..1).map { children[AbyssRifts.KOUREND].actions.getOpOrNull(it) })
        assertEquals(VarpLifetime.Perm, varp(varbit(AbyssRifts.LAST_BLOOD_RIFT).baseVar.id).scope)
        assertEquals("Exit-through", loc(AbyssRifts.SOUL_RIFT).actions.getOpOrNull(0))
    }

    @Test fun `the Kourend rift flags are permanent and dark essence is the item the rifts take`() {
        for (name in listOf(
            "varbit.zeah_blood_altar_unlocked", "varbit.zeah_soul_altar_unlocked",
            "varbit.rc_kourend_blood_crafted", "varbit.rc_kourend_soul_crafted",
        )) {
            assertEquals(VarpLifetime.Perm, varp(varbit(name).baseVar.id).scope, name)
        }
        assertEquals(listOf("Dark essence block", "Dark essence fragments"), AbyssRifts.DARK_ESSENCE.map { item(it).name })
    }

    @Test fun `every blood and soul destination is walkable beside its altar`() {
        val bloodRow = org.rsmod.api.table.runecrafting.RunecraftingAltarsRow.getRow("dbrow.runecrafting_altar_blood".asRSCM())
        val room = checkNotNull(bloodRow.entrance)
        val outside = checkNotNull(bloodRow.exit)
        assertEquals("loc.bloodtemple_exit_portal".asRSCM(RSCMType.LOC), bloodRow.exitPortal?.id)
        val destinations =
            listOf(
                Triple(room, "loc.blood_altar", 10),
                Triple(outside, "loc.bloodtemple_ruined", 3),
                Triple(AbyssRifts.KOUREND_BLOOD_LANDING, "loc.archeus_altar_blood", 3),
                Triple(AbyssRifts.KOUREND_SOUL_LANDING, "loc.archeus_altar_soul", 3),
            )
        for ((tile, altar, range) in destinations) {
            assertTrue(open(tile), "$tile walkable")
            val at = placedLocs.single { it.first == altar.asRSCM(RSCMType.LOC) }.second
            assertTrue(abs(tile.x - at.x) <= range && abs(tile.z - at.z) <= range, "$tile near $altar at $at")
        }
        val portals = placedLocs.filter { it.first == "loc.bloodtemple_exit_portal".asRSCM(RSCMType.LOC) }.map { it.second }
        assertTrue(portals.any { abs(it.x - room.x) <= 3 && abs(it.z - room.z) <= 3 }, "an exit portal beside the landing: $portals")
    }

    @Test fun `the Nexus tunnel only leads one way, from the outer ring into the Nexus`() {
        val outerEnd = placedLocs.single { it.first == AbyssNexusPassage.OUTER_PASSAGE.asRSCM(RSCMType.LOC) }.second
        val nexusEnd = placedLocs.single { it.first == AbyssNexusPassage.NEXUS_PASSAGE.asRSCM(RSCMType.LOC) }.second
        assertEquals(CoordGrid(3039, 4804), outerEnd)
        assertEquals(CoordGrid(3039, 4801), nexusEnd)
        assertEquals("Enter", loc(AbyssNexusPassage.OUTER_PASSAGE).actions.getOpOrNull(0))
        val outer = reachable(AbyssGate.entries.first().front)
        assertTrue(outerEnd.translateZ(1) in outer, "the tunnel is entered from the outer ring")
        val nexus = reachable(AbyssNexusPassage.NEXUS_LANDING)
        assertTrue(open(AbyssNexusPassage.NEXUS_LANDING))
        assertTrue(nexus.size > 500, "the Nexus, not a pocket: ${nexus.size}")
        assertTrue(nexus.none { it in outer }, "no walking route back to the outer ring")
        assertTrue(placedLocs.any { it.first == "loc.abyssalsire_exit_lever".asRSCM(RSCMType.LOC) && it.second in nexus.flatMap { t -> (-2..2).flatMap { dx -> (-2..2).map { dz -> t.translate(dx, dz) } } } }, "the landing reaches the Nexus's appendage")
    }

    @Test fun `the appendage operates once and drops into its spent form`() {
        assertEquals("Operate", loc(AbyssNexusAppendage.APPENDAGE).actions.getOpOrNull(0))
        val down = loc(AbyssNexusAppendage.APPENDAGE_DOWN)
        assertEquals((0..4).map { null }, (0..4).map { down.actions.getOpOrNull(it) }, "no ops while it is down")
        assertTrue(open(AbyssNexusAppendage.LUMBRIDGE), "Lumbridge landing walkable")
    }

    private fun reachable(start: CoordGrid): Set<CoordGrid> {
        val validator = StepValidator(collision)
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            for ((dx, dz) in listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)) {
                val next = CoordGrid(tile.x + dx, tile.z + dz, tile.level)
                if (next.x !in ABYSS_X || next.z !in ABYSS_Z || next in seen) continue
                if (validator.canTravel(tile.level, tile.x, tile.z, dx, dz)) {
                    seen += next
                    queue += next
                }
            }
        }
        return seen
    }

    private fun shownName(gate: AbyssGate, state: Int): String =
        dev.openrune.rscm.RSCM.getReverseMapping(RSCMType.LOC, checkNotNull(loc(gate.loc).transforms)[state])

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun npc(id: Int) = checkNotNull(ServerCacheManager.getNpc(id)) { "npc $id" }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun varbit(name: String) = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }

    private fun varp(name: String) = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))) { name }

    private fun varp(id: Int) = checkNotNull(ServerCacheManager.getVarp(id)) { "varp $id" }

    private companion object {
        val ABYSS_X = 3008..3071
        val ABYSS_Z = 4736..4863
        val INNER_RING = CoordGrid(3032, 4820)

        /** The Abyss and the squares holding each teleporter's return tile. */
        val SQUARES = listOf(50 to 50, 47 to 74, 47 to 75, 48 to 149, 50 to 53, 41 to 51, 37 to 153, 40 to 48, 50 to 75, 55 to 152, 26 to 59, 28 to 60)

        val collision = CollisionFlagMap()
        val placedLocs = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        fun file(path: String): java.io.File =
            listOf("", "../../").map { java.io.File("$it$path") }.first { it.exists() }

        fun spawns(npc: String): List<CoordGrid> =
            file(".data/raw-cache/map/npcs").listFiles()!!.filter { it.extension == "toml" }.flatMap { spawnFile ->
                val lines = spawnFile.readLines()
                lines.indices.filter { lines[it].trim() == "npc = \"$npc\"" }.map { index ->
                    val packed = lines[index + 1].substringAfter('"').substringBefore('"').split('_').map(String::toInt)
                    CoordGrid(packed[1] * 64 + packed[3], packed[2] * 64 + packed[4], packed[0])
                }
            }

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for ((sx, sz) in SQUARES) {
                val group = (sx shl 8) or sz
                val tileData = checkNotNull(cache.data(MAPS, group, 0)) { "map $sx,$sz" }
                val locData = checkNotNull(cache.data(MAPS, group, 1)) { "locs $sx,$sz" }
                val square = MapSquareKey(sx, sz)
                for (level in 0..3) for (x in sx * 64 until sx * 64 + 64 step 8) {
                    for (z in sz * 64 until sz * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                val tiles = MapTileDecoder.decode(InlineByteBuf(tileData))
                GameMapDecoder.putMaps(collision, square, tiles)
                val builder = GameMapBuilder()
                GameMapDecoder.putLocs(builder, collision, square, tiles, MapLocListDecoder.decode(InlineByteBuf(locData)))
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        placedLocs += LocEntity(entry.intValue).id to base.translate(key.x, key.z)
                    }
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
