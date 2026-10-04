package org.rsmod.content.areas.zeah.doors

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("QuestRequirements")
@OptIn(InternalApi::class)
class KourendDoorsInteractionTest {
    @Test
    fun `woodcutting guild turns away players below 60 woodcutting`() {
        val f = Fixture(CoordGrid(1562, 3488, 0))
        f.setLevel("stat.woodcutting", base = 59, current = 59)
        val gate = f.spawnWoodcuttingWestGate()
        f.op(gate)
        assertTrue(f.output().contains("You need a Woodcutting level of 60"), f.output())
        assertEquals(CoordGrid(1562, 3488, 0), f.player.coords)
        assertTrue(f.has(gate.coords, "loc.wcguild_gatel"))
    }

    @Test
    fun `a boosted woodcutting level opens both gate leaves and walks the player in`() {
        val f = Fixture(CoordGrid(1562, 3488, 0))
        f.setLevel("stat.woodcutting", base = 55, current = 60)
        val gate = f.spawnWoodcuttingWestGate()
        f.op(gate)
        assertEquals(CoordGrid(1563, 3488, 0), f.player.coords)
        assertFalse(f.has(CoordGrid(1562, 3488, 0), "loc.wcguild_gatel"))
        assertFalse(f.has(CoordGrid(1562, 3487, 0), "loc.wcguild_gater"))
        assertTrue(f.has(CoordGrid(1563, 3488, 0), "loc.wcguild_gatel_open"))
        assertTrue(f.has(CoordGrid(1564, 3488, 0), "loc.wcguild_gater_open"))
    }

    @Test
    fun `players inside the woodcutting guild can always leave`() {
        val f = Fixture(CoordGrid(1563, 3487, 0))
        f.setLevel("stat.woodcutting", base = 1, current = 1)
        f.op(f.spawnWoodcuttingWestGate(clickRight = true))
        assertEquals(CoordGrid(1562, 3487, 0), f.player.coords)
    }

    @Test
    fun `Berry welcomes players through the front gate`() {
        val f = Fixture(CoordGrid(1658, 3505, 0))
        f.setLevel("stat.woodcutting", base = 60, current = 60)
        val said = mutableListOf<String>()
        f.spawnNpc("npc.wcguild_guard", CoordGrid(1657, 3506, 0), said)
        f.spawn("loc.wcguild_gater", CoordGrid(1657, 3504, 0), LocAngle.East)
        val gate = f.spawn("loc.wcguild_gatel", CoordGrid(1657, 3505, 0), LocAngle.East)
        f.op(gate)
        assertEquals(CoordGrid(1657, 3505, 0), f.player.coords)
        assertEquals(listOf("Welcome to the Woodcutting Guild, adventurer."), said)
    }

    @Test
    fun `farming guild needs 45 farming, boosts allowed`() {
        val blocked = Fixture(CoordGrid(1248, 3722, 0))
        blocked.setLevel("stat.farming", base = 44, current = 44)
        blocked.op(blocked.spawnFarmingGuildDoor())
        assertTrue(blocked.output().contains("Farming level of 45"), blocked.output())
        assertEquals(CoordGrid(1248, 3722, 0), blocked.player.coords)

        val boosted = Fixture(CoordGrid(1248, 3722, 0))
        boosted.setLevel("stat.farming", base = 42, current = 45)
        boosted.op(boosted.spawnFarmingGuildDoor())
        assertEquals(CoordGrid(1248, 3723, 0), boosted.player.coords)
        assertTrue(boosted.has(CoordGrid(1248, 3722, 0), "loc.kebos_farming_guild_door_left_open"))
        assertTrue(boosted.has(CoordGrid(1249, 3722, 0), "loc.kebos_farming_guild_door_right_open"))
    }

    @Test
    fun `tithe farm door needs 34 farming to enter`() {
        val blocked = Fixture(CoordGrid(1804, 3501, 0))
        blocked.setLevel("stat.farming", base = 33, current = 33)
        blocked.op(blocked.spawn(TITHE, CoordGrid(1805, 3501, 0), LocAngle.West))
        assertTrue(blocked.output().contains("Farming level of 34"), blocked.output())
        assertEquals(CoordGrid(1804, 3501, 0), blocked.player.coords)

        val allowed = Fixture(CoordGrid(1804, 3501, 0))
        allowed.setLevel("stat.farming", base = 30, current = 34)
        allowed.op(allowed.spawn(TITHE, CoordGrid(1805, 3501, 0), LocAngle.West))
        assertEquals(CoordGrid(1805, 3501, 0), allowed.player.coords)
    }

    @Test
    fun `the forsaken tower stays locked until the quest is started`() {
        withPolicy(QuestRequirementMode.RespectProgress) {
            val f = Fixture(CoordGrid(1382, 3816, 0))
            f.op(f.spawn(FORSAKEN, CoordGrid(1382, 3817, 0), LocAngle.South))
            assertTrue(f.output().contains("The door is locked."), f.output())
            assertEquals(CoordGrid(1382, 3816, 0), f.player.coords)
        }
        withPolicy(QuestRequirementMode.AssumeCompleted) {
            val f = Fixture(CoordGrid(1382, 3816, 0))
            f.op(f.spawn(FORSAKEN, CoordGrid(1382, 3817, 0), LocAngle.South))
            assertEquals(CoordGrid(1382, 3817, 0), f.player.coords)
            assertTrue(f.has(CoordGrid(1382, 3816, 0), "loc.lovaquest_tower_entry_door_open"))
        }
    }

    @Test
    fun `the tower mages keep players out of the tower of magic before the quest`() {
        withPolicy(QuestRequirementMode.RespectProgress) {
            val f = Fixture(CoordGrid(1596, 3819, 0))
            f.op(f.spawnTowerOfMagicDoor())
            assertTrue(f.output().contains("The Tower of Magic is off limits."), f.output())
            assertEquals(CoordGrid(1596, 3819, 0), f.player.coords)
        }
        withPolicy(QuestRequirementMode.AssumeCompleted) {
            val f = Fixture(CoordGrid(1596, 3819, 0))
            f.op(f.spawnTowerOfMagicDoor())
            assertEquals(CoordGrid(1595, 3819, 0), f.player.coords)
            assertTrue(f.has(CoordGrid(1595, 3819, 0), "loc.arcquest_tower_door_left_open"))
            assertTrue(f.has(CoordGrid(1595, 3820, 0), "loc.arcquest_tower_door_right_open"))
        }
    }

    @Test
    fun `the outhouse doors stay shut`() {
        val f = Fixture(CoordGrid(1641, 3563, 0))
        f.op(f.spawn("loc.kore2_hos_door_inactive", CoordGrid(1641, 3564, 0), LocAngle.South))
        assertTrue(f.output().contains("I'm sure this door is locked for a reason."), f.output())

        val vinery = Fixture(CoordGrid(1818, 3537, 0))
        vinery.op(vinery.spawn("loc.hos_grape_odddoor", CoordGrid(1817, 3537, 0), LocAngle.West))
        assertTrue(vinery.output().contains("Occupied!"), vinery.output())
        assertEquals(CoordGrid(1818, 3537, 0), vinery.player.coords)
    }

    @Test
    fun `tent flaps close and open together in place`() {
        val f = Fixture(CoordGrid(1736, 3493, 0))
        f.spawn("loc.mdaughter_tent_door_openl", CoordGrid(1735, 3494, 0), LocAngle.South)
        val flap = f.spawn("loc.mdaughter_tent_door_open", CoordGrid(1736, 3494, 0), LocAngle.South)
        f.op(flap)
        assertTrue(f.has(CoordGrid(1736, 3494, 0), "loc.mdaughter_tent_door"))
        assertTrue(f.has(CoordGrid(1735, 3494, 0), "loc.mdaughter_tent_doorl"))

        val closed = f.find(CoordGrid(1735, 3494, 0), "loc.mdaughter_tent_doorl")
        f.op(closed)
        assertTrue(f.has(CoordGrid(1736, 3494, 0), "loc.mdaughter_tent_door_open"))
        assertTrue(f.has(CoordGrid(1735, 3494, 0), "loc.mdaughter_tent_door_openl"))
    }

    @Test
    fun `the getting ahead pen gate opens whichever look it has`() {
        val f = Fixture(CoordGrid(1257, 3685, 0))
        f.spawn("loc.ga_fencegate_r", CoordGrid(1258, 3686, 0), LocAngle.North)
        val gate = f.spawn("loc.ga_fencegate_l", CoordGrid(1257, 3686, 0), LocAngle.North)
        f.op(gate, vis = "loc.ga_fencegate_l_flour")
        assertFalse(f.has(CoordGrid(1257, 3686, 0), "loc.ga_fencegate_l"))
        assertFalse(f.has(CoordGrid(1258, 3686, 0), "loc.ga_fencegate_r"))
        assertTrue(f.has(CoordGrid(1257, 3687, 0), "loc.openfencegate_l"))
        assertTrue(f.has(CoordGrid(1257, 3688, 0), "loc.openfencegate_r"))
    }

    @Test
    fun `every scripted kourend door has an op handler`() {
        val f = Fixture(CoordGrid(1600, 3600, 0))
        for (loc in SCRIPTED) {
            val registered = f.events.contains(LocEvents.Op1::class.java, loc.asRSCM(RSCMType.LOC))
            assertTrue(registered, "$loc has no op1 handler")
        }
    }

    private fun withPolicy(mode: QuestRequirementMode, block: () -> Unit) {
        val previous = QuestRequirements.activePolicy()
        try {
            QuestRequirements.install(QuestRequirementPolicy(mode))
            block()
        } finally {
            QuestRequirements.install(previous)
        }
    }

    private class Fixture(start: CoordGrid) {
        val events = EventBus()
        private val collision = CollisionFlagMap()
        private val clock = MapClock().apply { cycle = 100 }
        private val updates = ZoneUpdateMap()
        private val zones = LocZoneStorage()
        private val activity = ZonePlayerActivityBitSet()
        private val npcs = NpcRegistry(NpcList(), collision, events)
        private val players = PlayerRegistry(PlayerList(), collision, activity, events)
        private val normal = LocRegistryNormal(updates, collision, zones)
        private val regions =
            RegionRegistry(
                RegionListSmall(),
                RegionListLarge(),
                RegionListWorldEntity(),
                normal,
                collision,
                zones,
                npcs,
                ControllerRegistry(clock, ControllerList()),
                activity,
            )
        private val locRegistry =
            LocRegistry(zones, normal, LocRegistryRegion(updates, collision, zones, regions))
        private val locs = LocRepository(clock, locRegistry, regions)
        private val npcSearch =
            NpcSearch(
                Hunt(RayCastValidator(collision), players, npcs, ObjRegistry(updates), locRegistry)
            )
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("kourend-doors-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getCollision = { collision },
                )

        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 7101L
                slotId = 1
                assignUid()
                coords = start
                currentMapClock = 100
                processedMapClock = 100
            }

        init {
            for (dx in -16..16 step 8) for (dz in -16..16 step 8) {
                collision.allocateIfAbsent(start.x + dx, start.z + dz, start.level)
            }
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(KourendGuildEntrances(locs, npcSearch)) { context.startup() }
            with(KourendQuestDoors(locs)) { context.startup() }
            with(KourendSceneryDoors(locs)) { context.startup() }
        }

        fun setLevel(stat: String, base: Int, current: Int) {
            player.statMap.setBaseLevel(stat, base.toByte())
            player.statMap.setCurrentLevel(stat, current.toByte())
        }

        fun spawn(loc: String, at: CoordGrid, angle: LocAngle): LocInfo {
            val shape = LocShape.WallStraight
            val entity = LocEntity(loc.asRSCM(RSCMType.LOC), shape.id, angle.id)
            val info = LocInfo(LocLayerConstants.of(shape.id), at, entity)
            assertTrue(locs.add(info, Int.MAX_VALUE), "could not spawn $loc at $at")
            return info
        }

        fun spawnWoodcuttingWestGate(clickRight: Boolean = false): LocInfo {
            val left = spawn("loc.wcguild_gatel", CoordGrid(1562, 3488, 0), LocAngle.East)
            val right = spawn("loc.wcguild_gater", CoordGrid(1562, 3487, 0), LocAngle.East)
            return if (clickRight) right else left
        }

        fun spawnFarmingGuildDoor(): LocInfo {
            spawn(FARMING_DOOR_RIGHT, CoordGrid(1249, 3723, 0), LocAngle.South)
            return spawn(FARMING_DOOR_LEFT, CoordGrid(1248, 3723, 0), LocAngle.South)
        }

        fun spawnTowerOfMagicDoor(): LocInfo {
            spawn("loc.arcquest_tower_door_right", CoordGrid(1596, 3820, 0), LocAngle.West)
            return spawn("loc.arcquest_tower_door_left", CoordGrid(1596, 3819, 0), LocAngle.West)
        }

        fun spawnNpc(type: String, at: CoordGrid, said: MutableList<String>) {
            val npc = Npc(type, at)
            npcs.add(npc)
            npc.infoProtocol =
                object : NpcInfoProtocol by NoopNpcInfo {
                    override fun isActive() = true

                    override fun setSay(text: String) {
                        said += text
                    }
                }
        }

        fun find(at: CoordGrid, loc: String): LocInfo =
            checkNotNull(locs.findAll(at).firstOrNull { it.id == loc.asRSCM(RSCMType.LOC) })

        fun has(at: CoordGrid, loc: String): Boolean =
            locs.findAll(at).any { it.id == loc.asRSCM(RSCMType.LOC) }

        fun op(loc: LocInfo, vis: String? = null) {
            val baseType = checkNotNull(ServerCacheManager.getObject(loc.id))
            val bound = BoundLocInfo(loc, baseType)
            val visEntity =
                vis?.let { LocEntity(it.asRSCM(RSCMType.LOC), loc.shapeId, loc.angleId) }
            val visBound = visEntity?.let { bound.copy(entity = it) } ?: bound
            val type = checkNotNull(ServerCacheManager.getObject(visBound.id))
            start { assertTrue(events.publish(this, LocEvents.Op1(bound, visBound, type))) }
            finish()
        }

        private fun start(block: suspend ProtectedAccess.() -> Unit) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { ProtectedAccess(player, coroutine, context).block() }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            result?.getOrThrow()
        }

        private fun finish() {
            repeat(100) {
                if (coroutine.isIdle) return
                if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                    val parent =
                        listOf("chat_left", "chat_right", "messagebox").firstOrNull {
                            player.ui.containsModal("interface.$it")
                        } ?: error("Unknown dialogue: ${output()}")
                    coroutine.resumeWith(ResumePauseButtonInput("component.$parent:continue", -1))
                } else {
                    player.currentMapClock++
                    player.processedMapClock = player.currentMapClock
                    coroutine.advance()
                }
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
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

    companion object {
        private const val TITHE = "loc.hosidius_tithe_farm_door"
        private const val FORSAKEN = "loc.lovaquest_tower_entry_door"
        private const val FARMING_DOOR_LEFT = "loc.kebos_farming_guild_door_left_closed"
        private const val FARMING_DOOR_RIGHT = "loc.kebos_farming_guild_door_right_closed"

        val SCRIPTED =
            listOf(
                "loc.kore2_hos_door_inactive",
                "loc.hos_grape_odddoor",
                "loc.hosidius_tithe_farm_door",
                "loc.wcguild_gatel",
                "loc.wcguild_gater",
                "loc.kebos_farming_guild_door_left_closed",
                "loc.kebos_farming_guild_door_right_closed",
                "loc.lovaquest_tower_entry_door",
                "loc.arcquest_tower_door_left",
                "loc.arcquest_tower_door_right",
                "loc.mdaughter_tent_door",
                "loc.mdaughter_tent_doorl",
                "loc.mdaughter_tent_door_open",
                "loc.mdaughter_tent_door_openl",
                "loc.ga_fencegate_l_normal",
                "loc.ga_fencegate_r_normal",
                "loc.ga_fencegate_l_flour",
                "loc.ga_fencegate_r_flour",
            )

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
