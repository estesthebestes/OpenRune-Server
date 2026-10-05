package org.rsmod.content.quest.area.ardougne.sheepherder

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.ardougne.sheepherder.HerdingRules.Direction
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_COMPLETE
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Sheep Herder is written against, and proves on the real collision
 * map that every colour can be herded from each of its spawns to the western gate using nothing
 * but the prod rules in [HerdingRules], with the player standing only on reachable open ground.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SheepHerderCacheTest {

    @Test fun `the quest row matches the stages, points and requirements`() {
        val row = QuestRow.getRow("dbrow.${SheepHerderQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(4, row.questpoints)
        assertTrue(row.requirementStats.isEmpty())
        assertTrue(row.requirementQuests.isEmpty())
    }

    @Test fun `the progress and colour bits sit on their own permanent varps without overlapping`() {
        val questVarp = "varp.sheepherderquest".asRSCM(RSCMType.VARP)
        val colourVarp = "varp.sheepherdervar".asRSCM(RSCMType.VARP)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(colourVarp)!!.scope)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(questVarp)!!.scope)

        val progress = ServerCacheManager.getVarbit("varbit.sheepherder_progress".asRSCM(RSCMType.VARBIT))!!
        assertEquals(questVarp, progress.baseVar.id)
        assertEquals(0, progress.startBit)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > STAGE_COMPLETE)

        val bits = SheepColour.entries.flatMap { colour ->
            val type = ServerCacheManager.getVarbit(colour.varbit.asRSCM(RSCMType.VARBIT))!!
            assertEquals(colourVarp, type.baseVar.id, colour.varbit)
            assertTrue((1 shl (type.endBit - type.startBit + 1)) >= SheepState.entries.size)
            (type.startBit..type.endBit).toList()
        }
        assertEquals(bits.size, bits.distinct().size)
    }

    @Test fun `field sheep prod as their colour and pen sheep only show when penned`() {
        for (colour in SheepColour.entries) {
            val field = npc(colour.fieldNpc)
            val shown = checkNotNull(ServerCacheManager.getNpc(field.transforms!![0]))
            assertEquals("${colour.title} Sheep", shown.name)
            assertEquals("Prod", shown.actions.getOpOrNull(0))
            val pen = npc(colour.enclosureNpc)
            assertEquals(colour.varbit.asRSCM(RSCMType.VARBIT), pen.multiVarBit)
            assertEquals(-1, pen.transforms!![SheepState.LOOSE.ordinal])
            assertEquals(shown.id, pen.transforms!![SheepState.PENNED.ordinal])
            assertEquals(-1, pen.transforms!!.getOrElse(SheepState.BONES.ordinal) { -1 })
            assertNotNull(item(colour.bones))
        }
        assertEquals(SheepColour.entries.size, SheepColour.entries.map { it.bones }.distinct().size)
    }

    @Test fun `npcs, locs and items carry the ops the scripts answer`() {
        for (name in listOf(SheepHerderQuest.HALGRIVE, SheepHerderQuest.ORBON, SheepHerderQuest.BRUMTY)) {
            assertTrue(talkable(npc(name)), "$name has no Talk-to")
        }
        assertEquals("Open", loc(PlagueEnclosure.GATE_LEFT).actions.getOpOrNull(0))
        assertEquals("Open", loc(PlagueEnclosure.GATE_RIGHT).actions.getOpOrNull(0))
        assertEquals("Incinerator", loc(PlagueEnclosure.INCINERATOR).name)
        assertEquals(WEAPON_SLOT, item(SheepHerderQuest.CATTLEPROD).wearpos1)
        assertEquals(TORSO_SLOT, item(SheepHerderQuest.JACKET).wearpos1)
        assertEquals(LEGS_SLOT, item(SheepHerderQuest.TROUSERS).wearpos1)
        assertFalse(item(SheepHerderQuest.FEED).stackable)
        for (seq in listOf(SheepHerding.PROD_SEQ, PlagueEnclosure.FEED_SEQ, PlagueEnclosure.FURNACE_SEQ, PlagueEnclosure.SHEEP_DEATH_SEQ)) {
            assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
        }
        for (synth in listOf(SheepHerding.BLEAT_SOUND, PlagueEnclosure.OPEN_SOUND, PlagueEnclosure.FURNACE_SOUND)) {
            assertTrue(synth.asRSCM(RSCMType.SYNTH) >= 0, synth)
        }
        for (timer in listOf(SheepHerding.RESTLESS_TIMER, SheepHerding.STUCK_TIMER)) {
            assertTrue(timer.asRSCM(RSCMType.TIMER) >= 0, timer)
        }
        assertTrue(SheepHerding.PUFF.asRSCM(RSCMType.SPOTANIM) >= 0)
    }

    @Test fun `the multinpcs a script binds resolve through a base type that is spawned`() {
        val spawned = rawSpawns("npcs").map { it.first }.toSet()
        for (colour in SheepColour.entries) {
            assertTrue(colour.fieldNpc in spawned, colour.fieldNpc)
            assertTrue(colour.enclosureNpc in spawned, colour.enclosureNpc)
        }
        for (name in listOf(SheepHerderQuest.ORBON, SheepHerderQuest.BRUMTY, SheepHerderQuest.HALGRIVE)) {
            assertTrue(name in spawned, name)
        }
    }

    @Test fun `the gate, incinerator and every spawn stand where the scripts expect`() {
        assertLoc(PlagueEnclosure.GATE_RIGHT, HerdingRules.GATE_THRESHOLD[0])
        assertLoc(PlagueEnclosure.GATE_LEFT, HerdingRules.GATE_THRESHOLD[1])
        assertLoc(PlagueEnclosure.INCINERATOR, INCINERATOR)
        val npcs = rawSpawns("npcs")
        for (colour in SheepColour.entries) {
            assertEquals(3, fieldSpawns(colour).size, colour.name)
            val pen = npcs.filter { it.first == colour.enclosureNpc }
            assertEquals(1, pen.size)
            assertTrue(HerdingRules.isInPen(pen.single().second))
        }
        assertTrue(npcs.any { it.first == "npc.councillor_halgrive" })
        assertTrue(npcs.any { it.first == SheepHerderQuest.ORBON })
        val brumty = npcs.single { it.first == SheepHerderQuest.BRUMTY }.second
        assertFalse(HerdingRules.isInPen(brumty))
        val prod = rawSpawns("objs").single { it.first == SheepHerderQuest.CATTLEPROD }.second
        assertTrue(HerdingRules.isInPen(prod), "the cattleprod should lie inside the enclosure")
        assertTrue(prod.chebyshevDistance(INCINERATOR) <= 4)
    }

    @Test fun `the sheep areas lie in their compass directions from the enclosure`() {
        val centre = CoordGrid(2602, 3358, 0)
        fun home(colour: SheepColour) = fieldSpawns(colour).first()
        assertTrue(home(SheepColour.RED).z < HerdingRules.PEN_MIN_Z, "red should be south")
        assertTrue(home(SheepColour.GREEN).x > HerdingRules.PEN_MAX_X, "green should be east")
        with(home(SheepColour.BLUE)) { assertTrue(x < HerdingRules.PEN_MIN_X && z > HerdingRules.PEN_MAX_Z, "blue should be north-west") }
        with(home(SheepColour.YELLOW)) { assertTrue(z > HerdingRules.PEN_MAX_Z && kotlin.math.abs(x - centre.x) < 15, "yellow should be north") }
    }

    @Test fun `the enclosure is sealed except through the gate`() {
        val inside = flood(CoordGrid(2598, 3358, 0))
        assertTrue(inside.all { HerdingRules.isInPen(it) }, "the enclosure leaks")
        assertTrue(CoordGrid(PlagueEnclosure.INSIDE_X, 3361, 0) in inside)
        assertFalse(HerdingRules.GATE_THRESHOLD.any { it in inside })
        assertTrue(open(INCINERATOR_STAND))
        assertTrue(INCINERATOR_STAND in inside)
    }

    @Test fun `a prod moves a sheep directly away from each side`() {
        val sheep = CoordGrid(2590, 3375, 0)
        assertEquals(Direction.NORTH, HerdingRules.direction(sheep.translateZ(-1), sheep))
        assertEquals(Direction.SOUTH, HerdingRules.direction(sheep.translateZ(1), sheep))
        assertEquals(Direction.EAST, HerdingRules.direction(sheep.translateX(-1), sheep))
        assertEquals(Direction.WEST, HerdingRules.direction(sheep.translateX(1), sheep))
        assertEquals(Direction.NORTH_EAST, HerdingRules.direction(sheep.translate(-1, -1), sheep))
        assertEquals(Direction.SOUTH_WEST, HerdingRules.direction(sheep.translate(1, 1), sheep))
        assertEquals(Direction.NORTH, HerdingRules.direction(sheep.translate(-1, -2), sheep), "the larger offset wins")
        assertNull(HerdingRules.direction(sheep, sheep))
        val path = HerdingRules.push(steps, sheep, Direction.NORTH)
        assertEquals((1..HerdingRules.PUSH_TILES).map { sheep.translateZ(it) }, path)
    }

    @Test fun `fences and walls stop a sheep short and never let it into the pen`() {
        val againstWall = CoordGrid(2594, 3358, 0)
        assertTrue(HerdingRules.push(steps, againstWall, Direction.EAST).isEmpty())
        val northOfPen = CoordGrid(2600, 3367, 0)
        val south = HerdingRules.push(steps, northOfPen, Direction.SOUTH)
        assertEquals(CoordGrid(2600, 3365, 0), south.last(), "the north fence should stop it at 3365")
        assertTrue(south.none { HerdingRules.isInPen(it) })
        val west = HerdingRules.push(steps, CoordGrid(2594, 3366, 0), Direction.SOUTH)
        assertTrue(west.contains(HerdingRules.GATE_THRESHOLD[1]))
        assertEquals(HerdingRules.GATE_THRESHOLD[1], west.last(), "a sheep crossing the threshold stops there to be penned")
    }

    @Test fun `every spawn of every colour can be herded to the gate`() {
        val reachable = flood(BRUMTY_STAND)
        for (colour in SheepColour.entries) {
            for (home in fieldSpawns(colour)) {
                val prods = solve(home, reachable)
                assertNotNull(prods, "${colour.name} sheep at $home can't be herded to the gate")
                println("${colour.name} from $home: ${prods!!.size} prods ${compress(prods)}")
            }
        }
    }

    /** Fewest cardinal prods from [home] to the gate threshold, each from a reachable open tile. */
    private fun solve(home: CoordGrid, reachable: Set<CoordGrid>): List<Direction>? {
        val previous = hashMapOf<CoordGrid, Pair<CoordGrid, Direction>?>(home to null)
        val queue = ArrayDeque(listOf(home))
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            if (HerdingRules.isThreshold(at)) {
                val prods = mutableListOf<Direction>()
                var step = previous[at]
                while (step != null) {
                    prods.add(0, step.second)
                    step = previous[step.first]
                }
                return prods
            }
            for (dir in Direction.entries.filter { it.isCardinal }) {
                val stand = HerdingRules.standFor(at, dir)
                if (stand !in reachable || !steps.canTravel(0, stand.x, stand.z, dir.dx, dir.dz)) continue
                val path = HerdingRules.push(steps, at, dir)
                if (path.isEmpty()) continue
                val dest = path.last()
                if (dest !in previous && MapSquareKey(dest.x / 64, dest.z / 64) in LOADED) {
                    previous[dest] = at to dir
                    queue += dest
                }
            }
        }
        return null
    }

    private fun compress(prods: List<Direction>): String =
        prods.fold(mutableListOf<Pair<Direction, Int>>()) { acc, d ->
            if (acc.lastOrNull()?.first == d) acc[acc.lastIndex] = d to acc.last().second + 1 else acc += d to 1
            acc
        }.joinToString(", ") { "${it.second}x ${it.first.label}" }

    private fun fieldSpawns(colour: SheepColour): List<CoordGrid> =
        rawSpawns("npcs").filter { it.first == colour.fieldNpc }.map { it.second }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun flood(from: CoordGrid): Set<CoordGrid> {
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (MapSquareKey(n.x / 64, n.z / 64) in LOADED && seen.add(n)) queue += n
            }
        }
        return seen
    }

    private fun talkable(type: dev.openrune.types.NpcServerType): Boolean =
        type.actions.getOpOrNull(0) == "Talk-to" ||
            type.transforms.orEmpty().any { id ->
                id > 0 && ServerCacheManager.getNpc(id)?.actions?.getOpOrNull(0) == "Talk-to"
            }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun assertLoc(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertTrue(placed.any { it.first == id && it.second == at }, "$name is not at $at")
    }

    private fun rawSpawns(kind: String): List<Pair<String, CoordGrid>> {
        val dir = listOf("", "../../").map { java.io.File("$it.data/raw-cache/map/$kind") }.first { it.isDirectory }
        val key = if (kind == "npcs") "npc" else "obj"
        val pattern = Regex("$key = \"($key[.][a-z0-9_]+)\"\\s*\\r?\\ncoords = \"(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)\"")
        return dir.listFiles { f -> f.name.endsWith(".toml") }!!.flatMap { file ->
            pattern.findAll(file.readText()).map {
                val (name, level, mx, mz, lx, lz) = it.destructured
                name to CoordGrid(mx.toInt() * 64 + lx.toInt(), mz.toInt() * 64 + lz.toInt(), level.toInt())
            }.toList()
        }
    }

    private companion object {
        const val WEAPON_SLOT = 3
        const val TORSO_SLOT = 4
        const val LEGS_SLOT = 7

        val INCINERATOR = CoordGrid(2606, 3360, 0)
        val INCINERATOR_STAND = CoordGrid(2604, 3360, 0)
        val BRUMTY_STAND = CoordGrid(2592, 3358, 0)

        val LOADED = listOf(39 to 52, 39 to 53, 40 to 51, 40 to 52, 40 to 53, 41 to 51, 41 to 52, 41 to 53)
            .map { (x, z) -> MapSquareKey(x, z) }.toSet()

        val collision = CollisionFlagMap()
        val steps = StepValidator(collision)
        val placed = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for (square in LOADED) {
                val group = (square.x shl 8) or square.z
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in square.x * 64 until square.x * 64 + 64 step 8) {
                    for (z in square.z * 64 until square.z * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        placed += LocEntity(entry.intValue).id to base.translate(key.x, key.z)
                    }
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
