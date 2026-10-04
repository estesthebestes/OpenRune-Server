package org.rsmod.content.quest.area.rimmington.witchspotion

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.quest.area.rimmington.witchspotion.npcs.Hetty
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
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
class WitchsPotionInteractionTest {
    @Test fun `accepting Hetty's offer starts the quest in the native varbit`() {
        val f = Fixture()
        f.talk()
        f.finish(listOf(1, 1))
        assertEquals(1, f.stage())
        assertEquals(1, f.player.vars["varp.hetty"])
    }

    @Test fun `declining or leaving keeps the quest unstarted`() {
        for (options in listOf(listOf(1, 2), listOf(2, 2))) {
            val f = Fixture()
            f.talk()
            f.finish(options)
            assertEquals(0, f.stage(), "options $options")
        }
    }

    @Test fun `the witch branch can still lead into the quest`() {
        val f = Fixture()
        f.talk()
        f.finish(listOf(2, 1, 1))
        assertEquals(1, f.stage())
    }

    @Test fun `partial ingredients are read back and kept`() {
        val f = Fixture(1)
        f.give(RatsTail, Onion)
        f.talk()
        f.finish()
        val output = f.output().replace("<br>", " ")
        assertTrue(output.contains("I have the rat's tail (ewww), I don't have any burnt meat,"), output)
        assertTrue(output.contains("I have an onion, and I don't have an eye of newt."), output)
        assertEquals(1, f.stage())
        assertEquals(1, f.player.inv.count(RatsTail))
    }

    @Test fun `all four ingredients are taken together and the potion is brewed`() {
        val f = Fixture(1)
        f.give(RatsTail, Onion, BurntMeat, EyeOfNewt)
        f.talk()
        f.finish()
        assertEquals(2, f.stage())
        for (ingredient in listOf(RatsTail, Onion, BurntMeat, EyeOfNewt)) {
            assertEquals(0, f.player.inv.count(ingredient), ingredient)
        }
    }

    @Test fun `drinking from the cauldron completes the quest once`() {
        val f = Fixture(2)
        f.drink()
        assertEquals(3, f.stage())
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(325, f.player.statMap.getXP("stat.magic"))
        f.drink()
        assertEquals(325, f.player.statMap.getXP("stat.magic"))
    }

    @Test fun `the cauldron does nothing before the potion is brewed`() {
        val f = Fixture(1)
        f.drink()
        assertEquals(1, f.stage())
        assertEquals(0, f.player.statMap.getXP("stat.magic"))
    }

    @Test fun `Hetty asks about magic after the quest`() {
        val f = Fixture(3)
        f.talk()
        f.finish()
        assertTrue(f.output().contains("How's your magic coming along?"), f.output())
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("witchs-potion-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 792L
                slotId = 1
                assignUid()
                coords = CoordGrid(2967, 3204, 0)
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

        val quest = WitchsPotionQuest()

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            val clock = MapClock(100)
            val updates = ZoneUpdateMap()
            val collision = CollisionFlagMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcs = NpcList()
            val regions =
                RegionRegistry(
                    RegionListSmall(),
                    RegionListLarge(),
                    RegionListWorldEntity(),
                    normal,
                    collision,
                    storage,
                    NpcRegistry(npcs, collision, events),
                    ControllerRegistry(clock, ControllerList()),
                    ZonePlayerActivityBitSet(),
                )
            val locs =
                LocRepository(
                    clock,
                    LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)),
                    regions,
                )
            with(quest) { scripts.startup() }
            with(Hetty(quest, locs, WorldRepository(updates))) { scripts.startup() }
            with(HettysCauldron(quest)) { scripts.startup() }
            VarPlayerIntMapSetter.set(player, "varbit.witchs_potion_progress", stage)
        }

        fun stage() = quest.quest.getQuestStage(player)

        fun access() = ProtectedAccess(player, coroutine, context)

        fun give(vararg objs: String) {
            objs.forEachIndexed { slot, obj -> player.inv[slot] = InvObj(obj, 1) }
        }

        fun talk() = start {
            assertTrue(events.publish(this, NpcEvents.Op1(Npc("npc.hetty", coords.translateZ(1)))))
        }

        fun drink() {
            val type = checkNotNull(ServerCacheManager.getObject("loc.hettycauldron".asRSCM()))
            val cauldron = BoundLocInfo(LocInfo(2, CoordGrid(2967, 3205, 0), LocEntity(type.id, 10, 0)), type)
            start { assertTrue(events.publish(this, LocEvents.Op1(cauldron, cauldron, type))) }
            finish()
        }

        private fun start(block: suspend ProtectedAccess.() -> Unit) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { access().block() }
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

        fun finish(options: List<Int> = emptyList()) {
            val selections = options.iterator()
            repeat(200) {
                if (coroutine.isIdle) return
                advance(selections)
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun advance(options: Iterator<Int>) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val input =
                    when {
                        player.ui.containsModal("interface.chatmenu") ->
                            ResumePauseButtonInput(
                                "component.chatmenu:options",
                                if (options.hasNext()) options.next() else 1,
                            )
                        else -> {
                            val parent =
                                listOf("chat_left", "chat_right", "messagebox").firstOrNull {
                                    player.ui.containsModal("interface.$it")
                                } ?: error("Unknown dialogue: ${output()}")
                            ResumePauseButtonInput("component.$parent:continue", -1)
                        }
                    }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            result?.getOrThrow()
        }

        fun output() = client.messages.joinToString("\n")
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
        private const val RatsTail = "obj.rats_tail"
        private const val Onion = "obj.onion"
        private const val BurntMeat = "obj.burnt_meat"
        private const val EyeOfNewt = "obj.eye_of_newt"
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
