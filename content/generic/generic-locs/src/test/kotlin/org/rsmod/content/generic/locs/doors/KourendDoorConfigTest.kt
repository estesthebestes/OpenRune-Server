package org.rsmod.content.generic.locs.doors

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.generic.locs.gate.GateTranslations
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KourendDoorConfigTest {
    private data class Entry(val id: String, val contentGroup: String, val next: String)

    private data class Spawn(val id: Int, val coords: CoordGrid, val shape: Int, val angle: Int)

    private lateinit var cache: Cache
    private lateinit var entries: Map<String, Entry>
    private lateinit var spawns: List<Spawn>

    @BeforeAll
    fun load() {
        cache = ServerCacheManager.init(240)
        entries = parseEntries(CONFIG).associateBy { it.id }
        spawns = decodeSpawns()
    }

    @AfterAll
    fun close() {
        cache.close()
    }

    @Test
    fun `every entry links to its other stage and back with the paired content group`() {
        assertTrue(entries.isNotEmpty())
        for (entry in entries.values) {
            val type = ServerCacheManager.getObject(entry.id.asRSCM(RSCMType.LOC))
            assertNotNull(type, "${entry.id} is not a loc type")
            val other = entries[entry.next]
            assertNotNull(other, "${entry.id} points at ${entry.next}, which is not configured")
            assertEquals(entry.id, other!!.next, "${entry.next} does not point back at ${entry.id}")
            assertEquals(STAGE_PAIRS[entry.contentGroup], other.contentGroup, entry.id)

            val expectedOp = if (entry.contentGroup in CLOSED_GROUPS) "Open" else "Close"
            assertEquals(expectedOp, type!!.actions.getOpOrNull(0), "${entry.id} op1")
        }
    }

    @Test
    fun `entries are not configured by any other loc config`() {
        val roots = listOf(File(".data/raw-cache/server"), File("content"))
        val others =
            roots
                .flatMap { root -> root.walkTopDown().filter { it.isConfigToml() }.toList() }
                .filterNot { it.canonicalFile == CONFIG.canonicalFile }
        for (file in others) {
            val clashes = parseEntries(file).map { it.id }.filter { it in entries }
            assertTrue(clashes.isEmpty(), "$file also configures $clashes")
        }
    }

    @Test
    fun `every double door and gate panel in kourend has its partner panel`() {
        val byCoords = spawns.filter { it.shape == WALL_STRAIGHT }.groupBy { it.coords }
        val groupsById = entries.values.associate { it.id.asRSCM(RSCMType.LOC) to it.contentGroup }
        fun groupOf(spawn: Spawn): String? = groupsById[spawn.id]

        var checked = 0
        for (spawn in spawns) {
            val group = groupOf(spawn) ?: continue
            val shape = LocShape[spawn.shape]
            val angle = LocAngle[spawn.angle]
            val (partnerCoords, partnerGroup) =
                when (group) {
                    "content.closed_left_door" ->
                        DoorTranslations.translateClose(spawn.coords, shape, angle) to
                            "content.closed_right_door"
                    "content.closed_right_door" ->
                        DoorTranslations.translateCloseOpposite(spawn.coords, shape, angle) to
                            "content.closed_left_door"
                    "content.closed_left_picketgate" ->
                        spawn.coords + GateTranslations.leftGateRightPair(shape, angle) to
                            "content.closed_right_picketgate"
                    "content.closed_right_picketgate" ->
                        spawn.coords - GateTranslations.leftGateRightPair(shape, angle) to
                            "content.closed_left_picketgate"
                    else -> continue
                }
            val partner =
                byCoords[partnerCoords].orEmpty().singleOrNull {
                    it.angle == spawn.angle && groupOf(it) == partnerGroup
                }
            assertNotNull(partner, "${locName(spawn.id)} at ${spawn.coords} has no $partnerGroup")
            checked++
        }
        assertTrue(checked > 0)
    }

    @Test
    fun `every openable door or gate in kourend is configured, scripted or knowingly skipped`() {
        val doorGroups = STAGE_PAIRS.keys.map { it.asRSCM(RSCMType.CONTENT) }.toSet()
        val unhandled = sortedSetOf<String>()
        val spawned = spawns.map { it.id }.toSet()
        val variants =
            spawned.flatMap { ServerCacheManager.getObject(it)?.multiLoc?.toList().orEmpty() }
        for (id in spawned + variants.filter { it != -1 }) {
            val type = ServerCacheManager.getObject(id) ?: continue
            val name = type.name.lowercase()
            val op = type.actions.getOpOrNull(0)
            if (("door" !in name && "gate" !in name) || (op != "Open" && op != "Close")) continue
            val loc = locName(id)
            val configured = loc in entries || type.contentGroup in doorGroups
            if (!configured && loc !in SCRIPTED && loc !in SKIPPED) unhandled += loc
        }
        assertTrue(unhandled.isEmpty(), "Kourend doors with no door config: $unhandled")
    }

    private fun decodeSpawns(): List<Spawn> = buildList {
        for (mx in SQUARES_X) for (mz in SQUARES_Z) {
            val data = runCatching { cache.data(MAPS, (mx shl 8) or mz, 1) }.getOrNull() ?: continue
            val packed = MapLocListDecoder.decode(InlineByteBuf(data)).spawns
            for (i in 0 until packed.size) {
                val loc = MapLocDefinition(packed.getLong(i))
                val coords = CoordGrid((mx shl 6) + loc.localX, (mz shl 6) + loc.localZ, loc.level)
                add(Spawn(loc.id, coords, loc.shape, loc.angle))
            }
        }
    }

    private fun locName(id: Int): String = RSCM.getReverseMapping(RSCMType.LOC, id)

    private fun File.isConfigToml(): Boolean =
        isFile && extension == "toml" && "${File.separator}build${File.separator}" !in path

    private companion object {
        val CONFIG = File(".data/raw-cache/server/loc/kourend/doors.toml")

        val SQUARES_X = 17..30
        val SQUARES_Z = 52..62

        val WALL_STRAIGHT = LocShape.WallStraight.id

        val STAGE_PAIRS =
            listOf(
                    "content.closed_single_door" to "content.opened_single_door",
                    "content.closed_left_door" to "content.opened_left_door",
                    "content.closed_right_door" to "content.opened_right_door",
                    "content.closed_left_picketgate" to "content.opened_left_picketgate",
                    "content.closed_right_picketgate" to "content.opened_right_picketgate",
                )
                .flatMap { (closed, opened) -> listOf(closed to opened, opened to closed) }
                .toMap()

        val CLOSED_GROUPS = STAGE_PAIRS.keys.filter { "closed" in it }.toSet()

        /** Doors whose requirements or behaviour are scripted in `content/areas/zeah`. */
        val SCRIPTED =
            mapOf(
                "loc.kore2_hos_door_inactive" to "locked outhouse door",
                "loc.hos_grape_odddoor" to "occupied vinery outhouse",
                "loc.hosidius_tithe_farm_door" to "Tithe Farm entrance (34 Farming)",
                "loc.wcguild_gatel" to "Woodcutting Guild entrance (60 Woodcutting)",
                "loc.wcguild_gater" to "Woodcutting Guild entrance (60 Woodcutting)",
                "loc.kebos_farming_guild_door_left_closed" to "Farming Guild entrance (45 Farming)",
                "loc.kebos_farming_guild_door_right_closed" to "Farming Guild entrance (45 Farming)",
                "loc.lovaquest_tower_entry_door" to "The Forsaken Tower entrance",
                "loc.arcquest_tower_door_left" to "Tower of Magic entrance (The Ascent of Arceuus)",
                "loc.arcquest_tower_door_right" to "Tower of Magic entrance (The Ascent of Arceuus)",
                "loc.mdaughter_tent_door_open" to "tent flaps swap in place",
                "loc.mdaughter_tent_door_openl" to "tent flaps swap in place",
                "loc.mdaughter_tent_door" to "tent flaps swap in place",
                "loc.mdaughter_tent_doorl" to "tent flaps swap in place",
                "loc.ga_fencegate_l_normal" to "Getting Ahead pen gate, multiloc on varbit.ga",
                "loc.ga_fencegate_r_normal" to "Getting Ahead pen gate, multiloc on varbit.ga",
                "loc.ga_fencegate_l_flour" to "Getting Ahead pen gate, multiloc on varbit.ga",
                "loc.ga_fencegate_r_flour" to "Getting Ahead pen gate, multiloc on varbit.ga",
            )

        val SKIPPED =
            mapOf(
                "loc.akd_hughes_gate_1" to
                    "A Kingdom Divided cell door: no open stage, OSRS behaviour undocumented",
                "loc.akd_lookout_trapdoor_closed" to
                    "A Kingdom Divided lookout trapdoor (multiloc variant), not a wall door",
            )

        fun parseEntries(file: File): List<Entry> {
            val result = mutableListOf<Entry>()
            var id: String? = null
            var group: String? = null
            var next: String? = null
            fun flush() {
                val current = id
                if (current != null && current.startsWith("loc.") && group != null) {
                    result += Entry(current, group!!, next.orEmpty())
                }
                id = null
                group = null
                next = null
            }
            for (raw in file.readLines()) {
                val line = raw.trim()
                when {
                    line.startsWith("[[") -> flush()
                    line.startsWith("id =") -> id = line.quotedValue()
                    line.startsWith("contentGroup =") -> group = line.quotedValue()
                    line.startsWith("\"param.next_loc_stage\"") -> next = line.quotedValue()
                }
            }
            flush()
            return result
        }

        private fun String.quotedValue(): String =
            substringAfter('=').trim().removeSurrounding("\"")
    }
}
