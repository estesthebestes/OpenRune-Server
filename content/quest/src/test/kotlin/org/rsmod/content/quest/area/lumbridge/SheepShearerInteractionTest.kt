package org.rsmod.content.quest.area.lumbridge

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
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SheepShearerInteractionTest {
    @Test fun `accepting the quest stores native progress and lends shears`() {
        val f = Fixture()
        f.talk()
        f.finish(listOf(1, 1))
        assertEquals(1, f.script.quest.getQuestStage(f.player))
        assertEquals(1, f.player.vars["varp.sheep"])
        assertEquals(1, f.player.inv.count("obj.shears"))
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test fun `players who already own shears are not given another pair`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.shears", 1)
        f.talk()
        f.finish(listOf(1, 1))
        assertEquals(1, f.player.inv.count("obj.shears"))
    }

    @Test fun `declining the start prompt leaves the quest unstarted`() {
        val f = Fixture()
        f.talk()
        f.finish(listOf(1, 2))
        assertEquals(0, f.script.quest.getQuestStage(f.player))
        assertEquals(0, f.player.inv.count("obj.shears"))
    }

    @Test fun `non-quest options do not start the quest`() {
        for (option in 2..3) {
            val f = Fixture()
            f.talk()
            f.finish(listOf(option))
            assertEquals(0, f.script.quest.getQuestStage(f.player))
        }
    }

    @Test fun `a partial hand-in records each ball delivered`() {
        val f = Fixture(1)
        f.balls(5)
        f.talk()
        f.finish()
        assertEquals(6, f.script.quest.getQuestStage(f.player))
        assertEquals(0, f.player.inv.count(Ball))
        assertTrue(f.output().contains("You give Fred 5 balls of wool."), f.output())
        assertTrue(f.script.questLog(f.access()).contains("<red>15</red> more balls"))
    }

    @Test fun `unspun wool is not accepted`() {
        val f = Fixture(1)
        f.player.inv[0] = InvObj("obj.wool", 3)
        f.talk()
        f.finish()
        assertEquals(1, f.script.quest.getQuestStage(f.player))
        assertEquals(3, f.player.inv.count("obj.wool"))
    }

    @Test fun `the final hand-in completes the quest once and keeps surplus wool`() {
        val f = Fixture(6)
        f.balls(20)
        f.talk()
        f.finish()
        f.assertReward()
        assertEquals(5, f.player.inv.count(Ball))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        f.talk()
        f.finish(listOf(1))
        f.assertReward()
        assertEquals(5, f.player.inv.count(Ball))
    }

    @Test fun `bringing twenty balls before accepting completes the quest`() {
        val f = Fixture()
        f.balls(20)
        f.talk()
        f.finish(listOf(1))
        f.assertReward()
        assertEquals(0, f.player.inv.count(Ball))
    }

    @Test fun `meeting The Thing unlocks the sighting option with Fred`() {
        val f = Fixture(1)
        f.talk(npc = "npc.sheep_shearer_the_thing", op = 3)
        f.finish()
        f.talk()
        f.finish(listOf(2))
        assertTrue(f.output().contains("just two penguins"), f.output())
        assertEquals(1, f.script.quest.getQuestStage(f.player))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("sheep-shearer-test")
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
                uuid = 790L
                slotId = 1
                assignUid()
                coords = CoordGrid(3189, 3273, 0)
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

        val script = SheepShearer(ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap())))

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            VarPlayerIntMapSetter.set(player, "varp.sheep", stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun balls(count: Int) {
            for (slot in 0 until count) player.inv[slot + 1] = InvObj(Ball, 1)
        }

        fun talk(npc: String = "npc.fred_the_farmer", op: Int = 1) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val target = Npc(npc, player.coords.translateZ(1))
            val block: suspend () -> Unit = {
                val event = if (op == 3) NpcEvents.Op3(target) else NpcEvents.Op1(target)
                assertTrue(events.publish(access(), event))
            }
            block.startCoroutine(
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
                        player.ui.containsModal("interface.objectbox") ->
                            ResumePauseButtonInput("component.objectbox:universe", -1)
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

        fun assertReward() {
            assertEquals(21, script.quest.getQuestStage(player))
            assertEquals(21, player.vars["varp.sheep"])
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(150, player.statMap.getXP("stat.crafting"))
            assertEquals(60, player.inv.count("obj.coins"))
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
        private const val Ball = "obj.ball_of_wool"
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
