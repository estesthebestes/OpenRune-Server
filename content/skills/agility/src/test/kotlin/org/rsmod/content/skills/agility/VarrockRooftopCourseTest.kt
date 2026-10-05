package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ObjectServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.other.pets.PetFollowers
import org.rsmod.content.other.pets.PetInsurance
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VarrockRooftopCourseTest {
    private lateinit var cache: Cache
    private val collision = CollisionFlagMap()
    private val locs = mutableListOf<BoundLocInfo>()

    private val course: Course
        get() = AgilityCourses.courses.single { it.name == "Varrock Rooftop Course" }

    @BeforeAll
    fun load() {
        cache = ServerCacheManager.init(240)
        loadSquare(49, 53)
        loadSquare(50, 53)
    }

    @AfterAll
    fun close() {
        cache.close()
    }

    @Test
    fun `every obstacle stands on the map where the course expects it`() {
        val expected =
            listOf(
                CoordGrid(3221, 3414, 0),
                CoordGrid(3214, 3414, 3),
                CoordGrid(3200, 3416, 3),
                CoordGrid(3191, 3415, 1),
                CoordGrid(3193, 3401, 3),
                CoordGrid(3209, 3397, 3),
                CoordGrid(3233, 3402, 3),
                CoordGrid(3236, 3409, 3),
                CoordGrid(3236, 3416, 3),
            )
        assertEquals(expected.size, course.obstacles.size)
        for ((index, obstacle) in course.obstacles.withIndex()) {
            val loc = obstacleLoc(obstacle)
            assertEquals(expected[index].level, loc.coords.level, "${obstacle.locs} level")
            assertTrue(
                loc.coords.x in expected[index].x - 1..expected[index].x + 1 &&
                    loc.coords.z in expected[index].z - 1..expected[index].z + 1,
                "${obstacle.locs} stands at ${loc.coords}, expected near ${expected[index]}",
            )
        }
    }

    @Test
    fun `every mark of grace spawns on a tile a player can stand on`() {
        for (spawn in course.markSpawns) {
            assertTrue(walkable(spawn), "mark of grace spawns on a blocked tile $spawn")
        }
    }

    @Test
    fun `every obstacle can be used from where the previous one left the player`() {
        val los = RayCastValidator(collision)
        var standing = START
        for (obstacle in course.obstacles) {
            val loc = obstacleLoc(obstacle)
            val type = checkNotNull(ServerCacheManager.getObject(loc.id))
            val route = routeTo(RouteFinding(collision), standing, loc, type)
            val range = APPROACH_RANGES[obstacle.locs.first()]
            val end = route.waypoints.lastOrNull()?.let { CoordGrid(it.x, it.z, it.level) } ?: standing
            if (range == null) {
                assertTrue(
                    route.success && !route.alternative,
                    "${obstacle.locs} cannot be reached from $standing",
                )
            } else {
                assertTrue(
                    Player().apply { coords = end }.isWithinDistance(loc, range),
                    "${obstacle.locs} is out of range $range from $end",
                )
                assertTrue(
                    los.hasLineOfSight(
                        source = end,
                        destination = loc.coords,
                        destWidth = type.width,
                        destLength = type.length,
                        extraFlag = CollisionFlag.BLOCK_PLAYERS,
                    ),
                    "${obstacle.locs} has no line of sight from $end",
                )
            }
            standing = obstacle.landing.resolve(end)
            assertTrue(walkable(standing), "${obstacle.locs} lands on a blocked tile $standing")
        }
    }

    @Test
    fun `a full lap lands on every ledge and pays the obstacle and lap xp`() {
        val f = Fixture()
        var total = 0
        for ((index, obstacle) in course.obstacles.withIndex()) {
            val last = index == course.obstacles.lastIndex
            val loc = obstacleLoc(obstacle)
            val approach = obstacle.locs.first() in APPROACH_RANGES
            val from = f.walkTo(loc)
            val before = f.player.statMap.getFineXP("stat.agility")
            f.interact(loc, approach)
            val gained = f.player.statMap.getFineXP("stat.agility") - before
            assertEquals(obstacle.landing.resolve(from), f.player.coords, "${obstacle.locs} landing")
            val lapXp = if (last) (course.lapXp * 10).toInt() else 0
            assertEquals(
                (obstacle.xp * 10).toInt() + lapXp,
                gained,
                "${obstacle.locs} xp (${f.output()})",
            )
            total += gained
            assertEquals(if (last) 0 else index + 1, f.agilityProgress, "${obstacle.locs} progress")
        }
        assertEquals(0, f.agilityCourse)
        assertEquals(CoordGrid(3236, 3417, 0), f.player.coords)
        assertEquals((course.obstacles.sumOf { it.xp } + course.lapXp) * 10, total.toDouble(), 0.5)
    }

    private fun routeTo(
        routes: RouteFinding,
        from: CoordGrid,
        loc: BoundLocInfo,
        type: ObjectServerType,
    ): org.rsmod.routefinder.Route =
        routes.findRoute(
            level = from.level,
            srcX = from.x,
            srcZ = from.z,
            destX = loc.coords.x,
            destZ = loc.coords.z,
            destWidth = type.width,
            destLength = type.length,
            locAngle = loc.entity.angle,
            locShape = loc.entity.shape,
            blockAccessFlags = type.forceApproachFlags,
        )

    private fun obstacleLoc(obstacle: Obstacle): BoundLocInfo {
        val id = obstacle.locs.first().asRSCM()
        return locs.single { it.id == id }
    }

    private fun walkable(coords: CoordGrid): Boolean {
        val flags = collision[coords.x, coords.z, coords.level] ?: return false
        return flags and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0
    }

    private inner class Fixture {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("varrock-agility-test")
        private var result: Result<Unit>? = null
        private val random = DefaultGameRandom(7L)
        private val clock = MapClock(100)
        private val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val npcs = NpcList()
        private val npcRegistry = NpcRegistry(npcs, collision, events)
        private val regions =
            RegionRegistry(
                RegionListSmall(),
                RegionListLarge(),
                RegionListWorldEntity(),
                normal,
                collision,
                storage,
                npcRegistry,
                ControllerRegistry(clock, ControllerList()),
                ZonePlayerActivityBitSet(),
            )
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getCollision = { collision },
                    getRandom = { random },
                    getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
                    getAreaChecker = { AreaChecker(regions, AreaIndex()) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 3221L
                slotId = 1
                assignUid()
                coords = START
                currentMapClock = 100
                processedMapClock = 100
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
                worn =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())),
                        arrayOfNulls(14),
                    )
            }

        init {
            player.statMap.setBaseLevel("stat.agility", 99)
            player.statMap.setCurrentLevel("stat.agility", 99)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            val npcRepo = NpcRepository(clock, npcRegistry, npcs)
            val followers = PetFollowers(npcRepo, npcs, clock, collision)
            val pets = PetRewards(followers, PetInsurance(), random)
            val objRepo = ObjRepository(clock, ObjRegistry(updates))
            with(AgilityObstacles(objRepo, XpModifiers(emptySet()), pets)) { scripts.startup() }
        }

        val agilityProgress: Int
            get() = player.agilityProgress

        val agilityCourse: Int
            get() = player.agilityCourse

        fun walkTo(loc: BoundLocInfo): CoordGrid {
            val type = checkNotNull(ServerCacheManager.getObject(loc.id))
            player.coords = approachTile(loc, type)
            return player.coords
        }

        fun interact(loc: BoundLocInfo, approach: Boolean) {
            val type = checkNotNull(ServerCacheManager.getObject(loc.id))
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = {
                val access = ProtectedAccess(player, coroutine, context)
                val event =
                    if (approach) LocEvents.Ap1(loc, loc, type) else LocEvents.Op1(loc, loc, type)
                assertTrue(events.publish(access, event), "no handler for ${loc.internalName}")
            }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            repeat(100) {
                result?.getOrThrow()
                if (coroutine.isIdle) return
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            result?.getOrThrow()
            check(coroutine.isIdle) { "${loc.internalName} never finished: ${output()}" }
        }

        private fun approachTile(loc: BoundLocInfo, type: ObjectServerType): CoordGrid {
            val route = routeTo(RouteFinding(collision), player.coords, loc, type)
            return route.waypoints.lastOrNull()?.let { CoordGrid(it.x, it.z, it.level) }
                ?: player.coords
        }

        fun output(): String = client.messages.joinToString("\n")
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()

        override fun write(message: Any) {
            messages += message
        }

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    private var Player.agilityCourse: Int by intVarp(VARP_COURSE)
    private var Player.agilityProgress: Int by intVarp(VARP_PROGRESS)

    private fun loadSquare(x: Int, z: Int) {
        val square = MapSquareKey(x, z)
        val group = (x shl 8) or z
        val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
        val spawns =
            MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
        for (level in 0..3) for (dx in 0 until 64 step 8) for (dz in 0 until 64 step 8) {
            collision.allocateIfAbsent(x * 64 + dx, z * 64 + dz, level)
        }
        val builder = GameMapBuilder()
        GameMapDecoder.putMaps(collision, square, tiles)
        GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
        for ((packed, zone) in builder.zoneBuilders) {
            val base = ZoneKey(packed).toCoords()
            for (entry in zone.build().byte2IntEntrySet()) {
                val key = LocZoneKey(entry.byteKey)
                val entity = LocEntity(entry.intValue)
                val type = ServerCacheManager.getObject(entity.id) ?: continue
                locs += BoundLocInfo(LocInfo(key.layer, base.translate(key.x, key.z), entity), type)
            }
        }
    }

    private companion object {
        val START = CoordGrid(3223, 3414, 0)
    }
}
