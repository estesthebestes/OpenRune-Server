package org.rsmod.content.quest.area.ardougne.fightarena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
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
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.PlayerDeath
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHandlingResolver
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.Direction
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FightArenaDeathTest {

    @Test
    fun `a death inside the arena keeps everything carried and worn`() {
        val f = Fixture(inside = true)
        f.dress()
        val before = f.contents()
        f.die()
        assertEquals(before, f.contents())
        assertEquals(0, f.groundItems())
    }

    @Test
    fun `a death inside the arena keeps a full inventory and every worn item`() {
        val f = Fixture(inside = true)
        f.dress()
        for (slot in 0 until 28) {
            if (f.player.inv[slot] == null) f.player.inv[slot] = InvObj("obj.logs", 1)
        }
        val before = f.contents()
        f.die()
        assertEquals(before, f.contents())
        assertEquals(0, f.groundItems())
    }

    @Test
    fun `an ironman keeps everything too`() {
        val f = Fixture(inside = true)
        f.dress()
        val before = f.contents()
        f.die(PlayerGamemode.IRONMAN)
        assertEquals(before, f.contents())
        assertEquals(0, f.groundItems())
    }

    @Test
    fun `a death outside the arena still uses the default handling`() {
        val f = Fixture(inside = false)
        f.dress()
        val before = f.contents()
        f.die()
        assertEquals(3, f.contents().size)
        assertTrue(before.size > 3)
        assertTrue(f.groundItems() > 0)
    }

    @Test
    fun `the quest stage lets a fallen player resume`() {
        for (stage in listOf(6, 7, 9, 10, 11, 12)) {
            val f = Fixture(inside = true)
            VarPlayerIntMapSetter.set(f.player, "varbit.fight_arena_progress", stage)
            f.dress()
            f.die()
            assertEquals(stage, f.quest.stage(f.player), "stage $stage")
            assertNotNull(f.quest.nextOpponent(stage), "stage $stage")
        }
    }

    @Test
    fun `the hook claims only players inside their arena`() {
        val player = Player()
        val handling = FightArenaDeathHook(StubSite(true)).handleDeath(context(player))
        assertNotNull(handling)
        assertTrue(handling!!.keepInventory)
        assertEquals(Int.MAX_VALUE, handling.keepCount)
        assertEquals(UntradeableHandling.KEEP, handling.untradeableHandling)
        assertTrue(handling.dropReceiver === player)
        assertNull(FightArenaDeathHook(StubSite(false)).handleDeath(context(player)))
    }

    @Test
    fun `the hook is asked before hooks that would otherwise claim an instance death`() {
        val greedy =
            object : PlayerDeathHook {
                override fun handleDeath(context: PlayerDeathContext) =
                    PlayerDeathHandling(
                        keepCount = 3,
                        dropReceiver = context.player,
                        dropDuration = 1,
                        revealDelay = 1,
                        supplyPile = false,
                        untradeableHandling = UntradeableHandling.KEEP,
                    )
            }
        val player = Player()
        val inside = linkedSetOf(greedy, FightArenaDeathHook(StubSite(true)))
        assertTrue(PlayerDeathHandlingResolver(inside).resolve(context(player)).keepInventory)
        val outside = linkedSetOf(greedy, FightArenaDeathHook(StubSite(false)))
        assertEquals(3, PlayerDeathHandlingResolver(outside).resolve(context(player)).keepCount)
    }

    private fun context(player: Player) =
        PlayerDeathContext(
            player = player,
            coords = CoordGrid(2600, 3160, 0),
            inWilderness = false,
            wildernessLevel = -1,
            inRevenantCaves = false,
            inInstance = true,
            isSkulled = false,
            hasProtectItem = false,
            recentPvpDamage = false,
            gamemode = 0,
            killer = null,
        )

    private class StubSite(private val inside: Boolean) : ArenaSite {
        override suspend fun enter(access: ProtectedAccess) = true

        override suspend fun leave(access: ProtectedAccess, dest: CoordGrid) {}

        override fun isInside(player: Player) = inside

        override fun spawn(player: Player, type: String, at: CoordGrid, face: Direction?): Npc? =
            null

        override fun remove(npc: Npc) {}

        override fun npcsOf(player: Player): List<Npc> = emptyList()

        override fun engage(npc: Npc, player: Player) {}

        override fun owns(npc: Npc) = false
    }

    private class Fixture(inside: Boolean) {
        private val events = EventBus()
        private val collision = CollisionFlagMap()
        private val clock = MapClock(100)
        private val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val activity = ZonePlayerActivityBitSet()
        private val npcList = NpcList()
        private val regions =
            RegionRegistry(
                RegionListSmall(),
                RegionListLarge(),
                RegionListWorldEntity(),
                LocRegistryNormal(updates, collision, storage),
                collision,
                storage,
                NpcRegistry(npcList, collision, events),
                ControllerRegistry(clock, ControllerList()),
                activity,
            )
        private val objs = ObjRepository(clock, ObjRegistry(updates))
        private val areaChecker = AreaChecker(regions, AreaIndex())
        private val prices =
            object : MarketPrices {
                override fun get(type: ItemServerType): Int? = null
            }
        private val drops = PlayerDeathDrops(clock, objs, prices, GroundItemDropResolver(emptySet()))
        private val resolver =
            PlayerDeathHandlingResolver(setOf(FightArenaDeathHook(StubSite(inside))))
        private val death = PlayerDeath(clock, drops, resolver, emptySet(), areaChecker)
        val quest = FightArenaQuest()

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                client = RecordingClient()
                uuid = 906L
                observerUUID = 906L
                slotId = 1
                assignUid()
                coords = FightArenaPlaces.ArenaEntry
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
            for (x in 2576..2632 step 8) for (z in 3136..3200 step 8) {
                collision.allocateIfAbsent(x, z, 0)
            }
            for (x in 3184..3256 step 8) for (z in 3200..3264 step 8) {
                collision.allocateIfAbsent(x, z, 0)
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
        }

        fun dress() {
            player.inv[0] = InvObj("obj.khali_brew", 1)
            player.inv[1] = InvObj("obj.khazard_cellkeys", 1)
            player.inv[2] = InvObj("obj.coins", 5000)
            player.inv[3] = InvObj("obj.logs", 1)
            player.inv[4] = InvObj("obj.bronze_dagger", 1)
            player.inv[5] = InvObj("obj.shark", 4)
            player.worn[0] = InvObj("obj.khazard_helmet", 1)
            player.worn[4] = InvObj("obj.khazard_platemail", 1)
            player.worn[3] = InvObj("obj.bronze_sword", 1)
        }

        fun contents(): List<Pair<Int, Int>> =
            (player.inv.filterNotNull { true } + player.worn.filterNotNull { true })
                .map { it.id to it.count }
                .sortedBy { it.first }

        fun groundItems(): Int {
            var count = 0
            for (x in 2576..2632) for (z in 3136..3200) {
                count += objs.findAll(CoordGrid(x, z, 0)).count()
            }
            return count
        }

        /**
         * Runs the item handling of the real death sequence. The rest of the sequence (delays,
         * animations, respawn) needs a live player action and does not touch the items.
         */
        fun die(gamemode: Int = PlayerGamemode.NORMAL) {
            player.gamemode = gamemode
            val method =
                PlayerDeath::class.java.declaredMethods.first {
                    it.name.startsWith("handleDeathDrops")
                }
            method.isAccessible = true
            method.invoke(death, player, player.coords.packed)
        }
    }

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) {}

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in
                listOf(
                    "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                    "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
                )) {
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
