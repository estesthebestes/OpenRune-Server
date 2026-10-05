package org.rsmod.content.quest.area.ardougne.fightarena

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
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.BouncerDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.Complete
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.HasKeys
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardFight
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.OgreDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.SammyFreed
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.ScorpionDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.ScorpionFight
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FightArenaCacheTest {

    @Test
    fun `the quest row matches the stages and points`() {
        val row = QuestRow.getRow("dbrow.quest_fightarena".asRSCM())
        assertEquals(Complete, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(50, row.recommendedCombat)
        assertTrue(row.requirementQuests.isEmpty())
        assertEquals("npc.lady_servil", row.startnpc.single().internalName)
    }

    @Test
    fun `progress sits on the quest varp in a server-only varbit inside the allotted range`() {
        val id = "varbit.fight_arena_progress".asRSCM(RSCMType.VARBIT)
        assertTrue(id in 63940..63959, id.toString())
        val progress = checkNotNull(ServerCacheManager.getVarbit(id))
        assertEquals("varp.arenaquest".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertEquals(0, progress.startBit)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > Complete)
    }

    @Test
    fun `the small flags are Jagex's own varbits on the secondary quest varp`() {
        val secondary = "varp.arenaquest_secondary".asRSCM(RSCMType.VARP)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(secondary)!!.scope)
        for (name in
            listOf(
                "varbit.arenaquest_met_sammy",
                "varbit.arenaquest_scorpion_cutscene",
                "varbit.arenaquest_bouncer_cutscene",
                "varbit.arenaquest_khazard_cutscene",
                "varbit.arenaquest_attempted_entry",
            )) {
            val type = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT)))
            assertEquals(secondary, type.baseVar.id, name)
        }
    }

    @Test
    fun `the stage numbers match when the cache shows each npc`() {
        val expected =
            mapOf(
                "sammy_servil" to (0..HasKeys),
                "sammy_servil_arena" to (SammyFreed..BouncerDefeated),
                "justin_servil" to (0 until ScorpionFight),
                "justin_servil_khazard" to (ScorpionFight..BouncerDefeated),
                "arena_guard_family" to (ScorpionFight..BouncerDefeated),
                "general_khazard_arena" to (ScorpionFight..KhazardFight),
                "arena_ogre_cage" to (0 until OgreDefeated),
                "arena_scorpion_cage" to (0 until ScorpionDefeated),
                "arena_bouncer_cage" to (0 until BouncerDefeated),
            )
        val varp = "varp.arenaquest".asRSCM(RSCMType.VARP)
        for ((name, shown) in expected) {
            val type = checkNotNull(ServerCacheManager.getNpc("npc.$name".asRSCM(RSCMType.NPC)))
            assertEquals(varp, type.multiVarp, name)
            for (stage in 0..Complete) {
                val resolved = type.multiNpc.getOrNull(stage)?.let { it and 0xFFFF }
                val visible = resolved != null && resolved != 0xFFFF
                assertEquals(stage in shown, visible, "$name at stage $stage")
            }
        }
    }

    @Test
    fun `the vanishing Lady Servil resolves to the npc the script talks to`() {
        val base = checkNotNull(ServerCacheManager.getNpc("npc.lady_servil".asRSCM(RSCMType.NPC)))
        val visible = base.multiNpc.first() and 0xFFFF
        assertEquals("npc.lady_servil_vis".asRSCM(RSCMType.NPC), visible)
        for (name in listOf("npc.sammy_servil_vis", "npc.arena_guard2", "npc.hengrad")) {
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
            assertEquals("Talk-to", type.actions.getOpOrNull(0), name)
        }
    }

    @Test
    fun `the fighters can be fought`() {
        for (name in
            listOf(
                "npc.arena_ogre",
                "npc.arena_scorpion",
                "npc.arena_bouncer",
                "npc.general_khazard",
                "npc.arena_guard1",
                "npc.arena_guard4",
                "npc.arena_guard5",
                "npc.arena_guard6",
                "npc.arena_guard7",
            )) {
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
            assertEquals("Attack", type.actions.getOpOrNull(1), name)
            assertTrue(type.hasParam(params.attack_anim), "attack animation of $name")
            assertTrue(type.hasParam(params.defend_anim), "defend animation of $name")
        }
        val levels =
            mapOf(
                "npc.arena_ogre" to 63,
                "npc.arena_scorpion" to 44,
                "npc.arena_bouncer" to 137,
                "npc.general_khazard" to 142,
            )
        for ((name, level) in levels) {
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
            assertEquals(level, type.combatLevel, name)
        }
        for (name in
            listOf(
                "npc.arena_ogre_cutscene",
                "npc.arena_guard4_cutscene",
                "npc.general_khazard_cutscene",
                "npc.sammy_servil_vis_noop",
            )) {
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
            assertTrue((0..4).all { type.actions.getOpOrNull(it).isNullOrBlank() }, name)
        }
    }

    @Test
    fun `the quest items and scenery exist`() {
        for (name in
            listOf("obj.khazard_helmet", "obj.khazard_platemail", "obj.khazard_cellkeys", "obj.khali_brew")) {
            assertTrue(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ)) != null, name)
        }
        assertEquals("Search", loc("loc.arena_guard_chest_shut").actions.getOpOrNull(0))
        assertEquals("Open", loc("loc.fightarena_door1").actions.getOpOrNull(0))
        assertEquals("Open", loc("loc.fightarena_door2").actions.getOpOrNull(0))
        assertEquals("Escape", loc("loc.fightarena_door2_escape").actions.getOpOrNull(0))
        assertEquals("Quick-escape", loc("loc.fightarena_door2_escape").actions.getOpOrNull(1))
        assertEquals("Open", loc("loc.arena_prisondoor").actions.getOpOrNull(0))
        assertEquals("Open", loc("loc.arena_jeremydoor").actions.getOpOrNull(0))
    }

    @Test
    fun `the scenery the script binds stands where it expects`() {
        assertPlaced("loc.arena_guard_chest_shut", CoordGrid(2613, 3189, 0))
        assertPlaced("loc.fightarena_door1", ArenaEntrance.West.coords)
        assertPlaced("loc.fightarena_door1", ArenaEntrance.East.coords)
        assertPlaced("loc.fightarena_door2", FightArenaPlaces.ArenaDoor)
        assertPlaced("loc.arena_jeremydoor", CoordGrid(2617, 3167, 0))
        assertPlaced("loc.arena_prisondoor", CoordGrid(2617, 3163, 0))
        assertPlaced("loc.arena_prisondoor", CoordGrid(2617, 3159, 0))
        assertPlaced("loc.arena_prisondoor", CoordGrid(2600, 3142, 0))
        assertPlaced("loc.arena_prisondoor", CoordGrid(2595, 3142, 0))
        assertPlaced("loc.arena_prisondoor", CoordGrid(2589, 3142, 0))
        assertEquals(10289, (40 shl 8) or 49)
    }

    @Test
    fun `the quest npcs are placed in the world`() {
        val spawns = rawSpawns()
        val expected =
            mapOf(
                "npc.lady_servil" to CoordGrid(2567, 3196, 0),
                "npc.sammy_servil" to FightArenaPlaces.SammyCell,
                "npc.hengrad" to CoordGrid(2598, 3142, 0),
                "npc.khazard_barman" to CoordGrid(2566, 3140, 0),
                "npc.arena_guard2" to CoordGrid(2614, 3143, 0),
                "npc.arena_guard3" to CoordGrid(2610, 3193, 0),
                "npc.arena_guard_door_1" to CoordGrid(2584, 3142, 0),
                "npc.arena_guard_door_2" to CoordGrid(2618, 3172, 0),
                "npc.arena_spectator" to FightArenaPlaces.Spectator,
                "npc.justin_servil" to FightArenaPlaces.JustinArena,
                "npc.justin_servil_khazard" to FightArenaPlaces.JustinKhazard,
                "npc.general_khazard_arena" to FightArenaPlaces.GeneralArena,
                "npc.sammy_servil_arena" to FightArenaPlaces.SammyArena,
                "npc.arena_guard_family" to FightArenaPlaces.FamilyGuard,
            )
        for ((npc, at) in expected) {
            assertTrue(spawns.any { it.first == npc && it.second == at }, "$npc at $at")
        }
        for (npc in listOf("npc.fightslave", "npc.fightslave_2", "npc.fightslave_joe", "npc.fightslave_kelvin")) {
            assertTrue(spawns.any { it.first == npc }, npc)
        }
    }

    @Test
    fun `every place the quest sends the player to is open ground`() {
        val places =
            mapOf(
                "arena entry" to FightArenaPlaces.ArenaEntry,
                "arena exit" to FightArenaPlaces.ArenaExit,
                "Hengrad's cell" to FightArenaPlaces.HengradCell,
                "Sammy" to FightArenaPlaces.SammyArena,
                "Justin" to FightArenaPlaces.JustinArena,
                "Justin by Khazard" to FightArenaPlaces.JustinKhazard,
                "family guard" to FightArenaPlaces.FamilyGuard,
                "guard one" to FightArenaPlaces.ArenaGuardCutscene,
                "guard two" to FightArenaPlaces.ArenaGuardCutsceneTwo,
            )
        for ((name, at) in places) {
            assertTrue(isOpen(at), "$name at $at is blocked")
        }
    }

    @Test
    fun `each opponent has room to stand where it appears`() {
        val footprints =
            mapOf(
                "ogre" to (FightArenaPlaces.OgreFight to 2),
                "ogre in the opening scene" to (FightArenaPlaces.OgreCutscene to 2),
                "scorpion" to (FightArenaPlaces.ScorpionFight to 3),
                "bouncer" to (FightArenaPlaces.BouncerFight to 2),
                "general" to (FightArenaPlaces.GeneralFight to 2),
            )
        for ((name, spot) in footprints) {
            val (at, size) = spot
            for (dx in 0 until size) for (dz in 0 until size) {
                val tile = at.translate(dx, dz)
                assertTrue(isOpen(tile), "$name footprint tile $tile is blocked")
            }
        }
    }

    @Test
    fun `both sides of every door the script walks through are open`() {
        val wall =
            listOf(
                ArenaEntrance.West.coords to LocAngle.West,
                ArenaEntrance.East.coords to LocAngle.North,
                CoordGrid(2617, 3167, 0) to LocAngle.West,
                CoordGrid(2617, 3163, 0) to LocAngle.West,
                CoordGrid(2617, 3159, 0) to LocAngle.West,
                CoordGrid(2600, 3142, 0) to LocAngle.South,
                CoordGrid(2595, 3142, 0) to LocAngle.South,
                CoordGrid(2589, 3142, 0) to LocAngle.South,
            )
        for ((coords, angle) in wall) {
            val (near, far) = FightArenaDoors.sides(coords, angle)
            assertTrue(isOpen(near), "$near beside the door at $coords is blocked")
            assertTrue(isOpen(far), "$far beside the door at $coords is blocked")
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
        val placed = mutableListOf<Placed>()
        val collision = CollisionFlagMap()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            val square = MapSquareKey(40, 49)
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

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
