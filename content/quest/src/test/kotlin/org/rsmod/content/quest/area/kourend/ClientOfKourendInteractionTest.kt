package org.rsmod.content.quest.area.kourend

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
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpot
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
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
@ResourceLock("QuestRequirements")
class ClientOfKourendInteractionTest {
    @Test fun `Veos only offers the quest once X Marks the Spot is complete`() {
        val previous = QuestRequirements.activePolicy()
        try {
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
            val f = Fixture()
            val xMarks = XMarksTheSpot(f.objRepo)
            assertNull(f.script.veosTopic(f.player))
            VarPlayerIntMapSetter.set(f.player, "varbit.cluequest", xMarks.quest.maxSteps)
            assertEquals("Have you got any quests for me?", f.script.veosTopic(f.player))
        } finally {
            QuestRequirements.install(previous)
        }
    }

    @Test fun `accepting the quest hands over the enchanted scroll`() {
        val f = Fixture()
        f.talkToVeos()
        f.finish(listOf(1))
        assertEquals(1, f.stage())
        assertEquals(1, f.count(Scroll))
        assertEquals("Let's talk about your client...", f.script.veosTopic(f.player))
    }

    @Test fun `declining leaves the quest unstarted`() {
        val f = Fixture()
        f.talkToVeos()
        f.finish(listOf(2))
        assertEquals(0, f.stage())
        assertEquals(0, f.count(Scroll))
    }

    @Test fun `a feather attunes a single quill to the scroll`() {
        val f = Fixture(1)
        f.player.inv[0] = InvObj(Scroll, 1)
        f.player.inv[1] = InvObj("obj.feather", 5)
        f.useOn("obj.feather", Scroll)
        assertEquals(1, f.count(Quill))
        assertEquals(4, f.count("obj.feather"))
        f.useOn("obj.feather", Scroll)
        assertEquals(1, f.count(Quill))
        assertEquals(4, f.count("obj.feather"))
        f.useOn(Quill, Scroll)
        assertEquals(1, f.count(Quill))
    }

    @Test fun `coloured feathers also make a quill`() {
        val f = Fixture(1)
        f.player.inv[0] = InvObj(Scroll, 1)
        f.player.inv[1] = InvObj("obj.hunting_stripy_bird_feather", 1)
        f.useOn("obj.hunting_stripy_bird_feather", Scroll)
        assertEquals(1, f.count(Quill))
        assertEquals(0, f.count("obj.hunting_stripy_bird_feather"))
    }

    @Test fun `a feather does nothing outside the quill step`() {
        val f = Fixture(2)
        f.player.inv[0] = InvObj(Scroll, 1)
        f.player.inv[1] = InvObj("obj.feather", 1)
        f.useOn("obj.feather", Scroll)
        assertEquals(0, f.count(Quill))
        assertEquals(1, f.count("obj.feather"))
    }

    @Test fun `shopkeepers refuse to talk until the player has the scroll and quill`() {
        val f = Fixture(1)
        f.player.inv[0] = InvObj(Scroll, 1)
        f.interview(KourendStore.Hosidius)
        f.finish()
        assertEquals(0, f.player.vars["varbit.veos_hosidius"])
        assertTrue(f.output().contains("Enchanted Quill"), f.output())
        assertEquals("Can I ask you about Hosidius?", f.storeTopic(KourendStore.Hosidius))
    }

    @Test fun `each shopkeeper is recorded once and the last one sends the player back`() {
        val f = Fixture(1)
        f.player.inv[0] = InvObj(Scroll, 1)
        f.player.inv[1] = InvObj(Quill, 1)
        val stores = KourendStore.entries
        for ((index, store) in stores.withIndex()) {
            f.interview(store)
            f.finish(listOf(1, 2))
            assertEquals(1, f.player.vars[store.talkedVarbit], "${store.keeper} not recorded")
            assertNull(f.storeTopic(store))
            val expected = if (index == stores.lastIndex) 2 else 1
            assertEquals(expected, f.stage(), "stage after ${store.keeper}")
        }
        assertEquals(1, f.count(Scroll))
        assertEquals(1, f.count(Quill))
    }

    @Test fun `the journal strikes off each shopkeeper as they are interviewed`() {
        val f = Fixture(1)
        f.player.inv[0] = InvObj(Scroll, 1)
        f.player.inv[1] = InvObj(Quill, 1)
        f.interview(KourendStore.Piscarilius)
        f.finish(listOf(2))
        val journal = f.script.questLog(f.access())
        assertTrue(journal.contains("<str>Leenz in Port Piscarilius</str>"), journal)
        assertTrue(journal.contains("<blue><red>Regath</red> in <red>Arceuus</red></blue>"), journal)
        assertTrue(journal.contains("<str>I attuned a feather"), journal)
        assertFalse(journal.contains("mysterious orb"), journal)
    }

    @Test fun `Veos replaces a lost scroll but not one that is still carried`() {
        val f = Fixture(1)
        f.talkToVeos()
        f.finish(listOf(2))
        assertEquals(1, f.count(Scroll))
        f.talkToVeos()
        f.finish(listOf(2))
        assertEquals(1, f.count(Scroll))
        assertEquals(1, f.stage())
    }

    @Test fun `a full backpack defers the replacement scroll`() {
        val f = Fixture(1)
        f.fillInventory()
        f.talkToVeos()
        f.finish(listOf(2))
        assertEquals(0, f.count(Scroll))
    }

    @Test fun `returning the information swaps the scroll and quill for the orb`() {
        val f = Fixture(2)
        f.player.inv[0] = InvObj(Scroll, 1)
        f.player.inv[1] = InvObj(Quill, 1)
        f.bank[0] = InvObj(Scroll, 1)
        f.talkToVeos()
        f.finish()
        assertEquals(4, f.stage())
        assertEquals(0, f.count(Scroll))
        assertEquals(0, f.count(Quill))
        assertEquals(0, f.bank.count(Scroll))
        assertEquals(1, f.count(Orb))
    }

    @Test fun `Veos hands out a new orb only when it is missing`() {
        val f = Fixture(4)
        f.talkToVeos()
        f.finish(listOf(2))
        assertEquals(1, f.count(Orb))
        f.talkToVeos()
        f.finish(listOf(2))
        assertEquals(1, f.count(Orb))
        f.player.inv[0] = null
        f.talkToVeos()
        f.finish(listOf(1))
        assertEquals(1, f.count(Orb))
        assertEquals(4, f.stage())
    }

    @Test fun `the orb only shatters beside the Dark Altar`() {
        val f = Fixture(4)
        f.player.inv[0] = InvObj(Orb, 1)
        f.player.coords = CoordGrid(1640, 3800, 0)
        f.held(Orb)
        assertEquals(4, f.stage())
        assertEquals(1, f.count(Orb))
        f.player.coords = CoordGrid(1712, 3880, 0)
        f.held(Orb)
        assertEquals(5, f.stage())
        assertEquals(0, f.count(Orb))
        assertEquals(1, f.count("obj.broken_glass"))
    }

    @Test fun `the orb does nothing before Veos asks for it`() {
        val f = Fixture(3)
        f.player.inv[0] = InvObj(Orb, 1)
        f.player.coords = CoordGrid(1712, 3880, 0)
        f.held(Orb)
        assertEquals(3, f.stage())
        assertEquals(1, f.count(Orb))
    }

    @Test fun `the client completes the quest with every reward once`() {
        val f = Fixture(5)
        f.bank[0] = InvObj(Orb, 1)
        f.talkToVeos()
        f.finish()
        f.assertReward()
        assertEquals(0, f.bank.count(Orb))
        assertEquals("I've lost something you've given me.", f.script.veosTopic(f.player))
        f.talkToVeos()
        f.finish()
        f.assertReward()
    }

    @Test fun `an interrupted reveal can be replayed`() {
        val f = Fixture(6)
        f.talkToVeos()
        f.finish()
        f.assertReward()
    }

    @Test fun `the antique lamp grants 500 experience in the chosen skill`() {
        val f = Fixture(7)
        f.player.inv[0] = InvObj(Lamp, 1)
        f.held(Lamp, lampChoice = 1)
        assertEquals(0, f.count(Lamp))
        assertEquals(500, f.player.statMap.getXP("stat.strength"))
        assertEquals(1, f.player.vars["varbit.veos_housereward"])
    }

    @Test fun `lost rewards are reclaimed without returning used lamps`() {
        val f = Fixture(7)
        f.player.inv[0] = InvObj(Lamp, 1)
        f.held(Lamp)
        f.talkToVeos()
        f.finish()
        assertEquals(1, f.count(Lamp))
        assertEquals(1, f.count(Memoirs))
        f.talkToVeos()
        f.finish()
        assertEquals(1, f.count(Lamp))
        assertEquals(1, f.count(Memoirs))
    }

    @Test fun `the memoirs are not replaced while the Book of the Dead is owned`() {
        val f = Fixture(7)
        VarPlayerIntMapSetter.set(f.player, "varbit.veos_housereward", 2)
        f.bank[0] = InvObj("obj.book_of_the_dead", 1)
        f.talkToVeos()
        f.finish()
        assertEquals(0, f.count(Memoirs))
        assertEquals(0, f.count(Lamp))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("client-of-kourend-test")
        private var result: Result<Unit>? = null
        private var lampChoice = 0
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                )

        val bank =
            Inventory(
                checkNotNull(ServerCacheManager.getInventory("inv.bank".asRSCM())),
                arrayOfNulls(800),
            )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 792L
                slotId = 1
                assignUid()
                coords = CoordGrid(1824, 3690, 0)
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
                invMap["inv.bank"] = bank
            }

        val objRepo = ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap()))

        val script = ClientOfKourend(objRepo)

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            VarPlayerIntMapSetter.set(player, "varbit.veos_progress", stage)
        }

        fun stage() = script.quest.getQuestStage(player)

        fun count(obj: String) = player.inv.count(obj)

        fun storeTopic(store: KourendStore) = script.storeTopic(player, store)

        fun access() = ProtectedAccess(player, coroutine, context)

        fun fillInventory() {
            for (slot in 0 until 28) player.inv[slot] = InvObj("obj.bronze_dagger", 1)
        }

        fun talkToVeos() = start {
            startDialogue(Npc("npc.veos", coords)) { with(script) { veosPiscariliusQuest() } }
        }

        fun interview(store: KourendStore) = start {
            startDialogue(Npc("npc.hosidius_generalstore", coords)) {
                with(script) { storeInterview(store) }
            }
        }

        fun useOn(first: String, second: String) {
            val firstSlot = slotOf(first)
            val secondSlot = slotOf(second)
            val firstType = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val secondType = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            start {
                val event = HeldUEvents.Type(firstType, firstSlot, secondType, secondSlot)
                assertTrue(events.publish(this, event))
            }
            finish()
        }

        fun held(obj: String, lampChoice: Int = 0) {
            this.lampChoice = lampChoice
            val slot = slotOf(obj)
            if (player.inv[slot] == null) player.inv[slot] = InvObj(obj, 1)
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            start {
                val event = HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv)
                assertTrue(events.publish(this, event))
            }
            finish()
        }

        private fun slotOf(obj: String): Int =
            (0 until 28).firstOrNull { player.inv[it]?.id == obj.asRSCM() } ?: 0

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
            repeat(300) {
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
                        player.ui.containsModal("interface.xpreward") ->
                            ResumePauseButtonInput("component.xpreward:universe", lampChoice)
                        player.ui.containsModal("interface.objectbox") ->
                            ResumePauseButtonInput("component.objectbox:universe", -1)
                        player.ui.containsModal("interface.objectbox_double") ->
                            ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
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
            assertEquals(7, stage())
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(2, count(Lamp))
            assertEquals(1, count(Memoirs))
            assertEquals(0, count(Orb))
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
        private const val Scroll = "obj.veos_scroll"
        private const val Quill = "obj.veos_quill"
        private const val Orb = "obj.veos_orb"
        private const val Lamp = "obj.veos_lamp"
        private const val Memoirs = "obj.veos_kharedsts_memoirs"
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
