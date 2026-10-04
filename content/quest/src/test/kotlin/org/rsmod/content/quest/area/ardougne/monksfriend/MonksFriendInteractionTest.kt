package org.rsmod.content.quest.area.ardougne.monksfriend

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.BlanketReturned
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.CartAccepted
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Cedric
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.CedricFound
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.ChildsBlanket
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Complete
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.JugOfWater
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.LawRune
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Logs
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Monk
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Omad
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.PartyStarted
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Plank
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.SentForCedric
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Started
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.WaterGiven
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.WoodGiven
import org.rsmod.content.quest.area.ardougne.monksfriend.npcs.ArdougneMonks
import org.rsmod.content.quest.area.ardougne.monksfriend.npcs.BrotherCedric
import org.rsmod.content.quest.area.ardougne.monksfriend.npcs.BrotherOmad
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
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
class MonksFriendInteractionTest {

    @Test
    fun `the whole quest from the stolen blanket to the party`() {
        val f = Fixture()
        f.talk(Omad)
        f.finish(listOf(1, 1, 1))
        assertEquals(Started, f.stage())
        assertEquals(Started, f.player.vars["varp.drunkmonkquest"])
        assertTrue(f.output().contains("secret cave"), f.output())
        assertTrue(f.journal().contains("child's blanket"))

        f.give(ChildsBlanket)
        assertTrue(f.journal().contains("I have the blanket"))
        f.talk(Omad)
        f.finish()
        assertEquals(BlanketReturned, f.stage())
        assertEquals(0, f.count(ChildsBlanket))
        assertTrue(f.output().contains("Goodnight"), f.output())
        assertTrue(f.journal().contains("planning a party"))

        f.talk(Omad)
        f.finish(listOf(1, 2))
        assertEquals(SentForCedric, f.stage())
        assertTrue(f.output().contains("forest north of here"), f.output())
        assertTrue(f.journal().contains("Brother Cedric"))

        f.talk(Omad)
        f.finish()
        assertEquals(SentForCedric, f.stage())

        f.talk(Cedric)
        f.finish()
        assertEquals(CedricFound, f.stage())
        assertTrue(f.output().contains("jug of water"), f.output())

        f.talk(Omad)
        f.finish()
        assertTrue(f.output().contains("very drunk"), f.output())
        assertEquals(CedricFound, f.stage())

        f.give(JugOfWater)
        assertTrue(f.journal().contains("I have a jug of water"))
        f.talk(Cedric)
        f.finish(listOf(2))
        assertEquals(CartAccepted, f.stage())
        assertEquals(0, f.count(JugOfWater))
        assertTrue(f.output().contains("some plain logs"), f.output())

        f.give(Logs)
        assertTrue(f.journal().contains("I have some wood"))
        f.talk(Cedric)
        f.finish()
        assertEquals(WoodGiven, f.stage())
        assertEquals(0, f.count(Logs))
        assertTrue(f.output().contains("mended in no time"), f.output())

        f.talk(Cedric)
        f.finish()
        assertEquals(WoodGiven, f.stage())
        assertTrue(f.output().contains("nearly finished"), f.output())

        f.talk(Omad)
        f.finish()
        f.assertRewards()
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.output().contains("Get down"), f.output())
        assertTrue(f.quest.partyActive(f.player))

        f.talk(Omad)
        f.finish()
        assertTrue(f.output().contains("That was some party"), f.output())
        f.assertRewards()

        f.talk(Cedric)
        f.finish()
        assertTrue(f.output().contains("on his behalf"), f.output())
        f.assertRewards()
    }

    @Test
    fun `declining the start prompt leaves the quest unstarted`() {
        val f = Fixture()
        f.talk(Omad)
        f.finish(listOf(1, 1, 2))
        assertEquals(0, f.stage())
        assertTrue(f.output().contains("change your mind"), f.output())
    }

    @Test
    fun `the other options leave the quest unstarted`() {
        val busy = Fixture()
        busy.talk(Omad)
        busy.finish(listOf(2))
        assertEquals(0, busy.stage())

        val hope = Fixture()
        hope.talk(Omad)
        hope.finish(listOf(1, 2))
        assertEquals(0, hope.stage())
    }

    @Test
    fun `Omad waits for the blanket and refuses anything else`() {
        val f = Fixture(Started)
        f.give(Logs)
        f.talk(Omad)
        f.finish()
        assertEquals(Started, f.stage())
        assertEquals(1, f.count(Logs))
        assertTrue(f.output().contains("Not yet"), f.output())

        f.use(Omad, Logs)
        f.finish()
        assertEquals(Started, f.stage())
        assertEquals(1, f.count(Logs))
        assertTrue(f.output().contains("Nothing interesting happens"), f.output())
    }

    @Test
    fun `using the blanket on Omad hands it in once`() {
        val f = Fixture(Started)
        f.give(ChildsBlanket)
        f.use(Omad, ChildsBlanket)
        f.finish()
        assertEquals(BlanketReturned, f.stage())
        assertEquals(0, f.count(ChildsBlanket))
    }

    @Test
    fun `asking about Cedric only starts the search when Omad is agreed with`() {
        for ((option, expected) in
            listOf(1 to BlanketReturned, 2 to SentForCedric, 3 to SentForCedric)) {
            val f = Fixture(BlanketReturned)
            f.talk(Omad)
            f.finish(listOf(1, option))
            assertEquals(expected, f.stage(), "option $option")
        }

        val leave = Fixture(BlanketReturned)
        leave.talk(Omad)
        leave.finish(listOf(2))
        assertEquals(BlanketReturned, leave.stage())
    }

    @Test
    fun `Cedric is only a drunk until Omad sends the player to find him`() {
        for (stage in listOf(0, Started, BlanketReturned)) {
            val f = Fixture(stage)
            f.talk(Cedric)
            f.finish()
            assertEquals(stage, f.stage())
            assertTrue(f.output().contains("woman and wine"), f.output())
        }
    }

    @Test
    fun `Cedric refuses to be helped without water, and wrong items change nothing`() {
        val f = Fixture(CedricFound)
        f.give(Logs)
        f.talk(Cedric)
        f.finish()
        assertEquals(CedricFound, f.stage())
        assertEquals(1, f.count(Logs))
        assertTrue(f.output().contains("find some"), f.output())

        f.use(Cedric, Logs)
        f.finish()
        assertEquals(CedricFound, f.stage())
        assertEquals(1, f.count(Logs))
    }

    @Test
    fun `water given with the item use is atomic and consumes one jug`() {
        val f = Fixture(CedricFound)
        f.give(JugOfWater)
        f.give(JugOfWater)
        f.use(Cedric, JugOfWater)
        f.finish(listOf(2))
        assertEquals(CartAccepted, f.stage())
        assertEquals(1, f.count(JugOfWater))
    }

    @Test
    fun `declining to mend the cart keeps the water given stage`() {
        val f = Fixture(CedricFound)
        f.give(JugOfWater)
        f.talk(Cedric)
        f.finish(listOf(1))
        assertEquals(WaterGiven, f.stage())
        assertEquals(0, f.count(JugOfWater))
        assertTrue(f.output().contains("wine..."), f.output())

        f.talk(Cedric)
        f.finish(listOf(2))
        assertEquals(CartAccepted, f.stage())
    }

    @Test
    fun `only plain logs or a plank mend the cart`() {
        val oak = Fixture(CartAccepted)
        oak.give("obj.oak_logs")
        oak.talk(Cedric)
        oak.finish()
        assertEquals(CartAccepted, oak.stage())
        assertEquals(1, oak.count("obj.oak_logs"))
        assertTrue(oak.output().contains("Not yet"), oak.output())

        oak.use(Cedric, "obj.oak_logs")
        oak.finish()
        assertEquals(CartAccepted, oak.stage())

        val plank = Fixture(CartAccepted)
        plank.give(Plank)
        plank.talk(Cedric)
        plank.finish()
        assertEquals(WoodGiven, plank.stage())
        assertEquals(0, plank.count(Plank))
        assertTrue(plank.output().contains("a plank"), plank.output())

        val both = Fixture(CartAccepted)
        both.give(Logs)
        both.give(Plank)
        both.use(Cedric, Plank)
        both.finish()
        assertEquals(WoodGiven, both.stage())
        assertEquals(0, both.count(Logs))
        assertEquals(1, both.count(Plank))
    }

    @Test
    fun `Omad tells the player to help Cedric until the wood is in`() {
        for (stage in listOf(WaterGiven, CartAccepted)) {
            val f = Fixture(stage)
            f.talk(Omad)
            f.finish()
            assertEquals(stage, f.stage())
            assertTrue(f.output().contains("trouble with his cart"), f.output())
        }
    }

    @Test
    fun `the party is not repeated when it was interrupted after the runes were given`() {
        val f = Fixture(PartyStarted)
        f.talk(Omad)
        f.finish()
        assertEquals(Complete, f.stage())
        assertEquals(0, f.count(LawRune))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(2000, f.player.statMap.getXP("stat.woodcutting").toInt())
    }

    @Test
    fun `a full inventory still completes the quest once and nothing pays twice`() {
        val f = Fixture(WoodGiven)
        for (slot in 0 until 28) f.player.inv[slot] = InvObj("obj.bronze_axe", 1)
        f.talk(Omad)
        f.finish()
        assertEquals(Complete, f.stage())
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(0, f.count(LawRune))
        assertEquals(8, f.onFloor(LawRune))
        f.talk(Omad)
        f.finish()
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(8, f.onFloor(LawRune))
        assertEquals(2000, f.player.statMap.getXP("stat.woodcutting").toInt())
    }

    @Test
    fun `the monks wait for the party, then hiccup for twenty minutes, then calm down`() {
        val before = Fixture(Started)
        before.talk(Monk)
        before.finish()
        assertTrue(before.output().contains("Peace be with you"), before.output())

        val planned = Fixture(SentForCedric)
        planned.talk(Monk)
        planned.finish()
        assertTrue(planned.output().contains("party"), planned.output())

        val after = Fixture(WoodGiven)
        after.talk(Omad)
        after.finish()
        after.talk(Monk)
        after.finish()
        assertTrue(after.output().contains("fantastic party"), after.output())

        after.player.currentMapClock += MonksFriendQuest.PartyTicks + 1
        after.player.processedMapClock = after.player.currentMapClock
        assertFalse(after.quest.partyActive(after.player))
        after.talk(Monk)
        after.finish()
        assertTrue(after.output().contains("Peace be with you"), after.output())
    }

    @Test
    fun `the journal follows every stage`() {
        val f = Fixture()
        val expected =
            mapOf(
                Started to "ladder",
                BlanketReturned to "ask him how he is",
                SentForCedric to "Ardougne Zoo",
                CedricFound to "very drunk",
                WaterGiven to "cart he was pulling",
                CartAccepted to "plank",
                WoodGiven to "mending his cart",
                PartyStarted to "party is about to begin",
            )
        for ((stage, text) in expected) {
            VarPlayerIntMapSetter.set(f.player, "varp.drunkmonkquest", stage)
            assertTrue(f.journal().contains(text), "stage $stage: ${f.journal()}")
        }
        VarPlayerIntMapSetter.set(f.player, "varp.drunkmonkquest", Complete)
        assertTrue(f.quest.completedLog(f.access()).contains("8 law runes"))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("monks-friend-test")
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
                uuid = 791L
                observerUUID = 791L
                slotId = 1
                assignUid()
                coords = CoordGrid(2604, 3207, 0)
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

        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        private val objRepo = ObjRepository(MapClock(100), objRegistry)
        val quest = MonksFriendQuest()

        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { context.startup() }
            with(BrotherOmad(quest, objRepo)) { context.startup() }
            with(BrotherCedric(quest)) { context.startup() }
            with(ArdougneMonks(quest)) { context.startup() }
            VarPlayerIntMapSetter.set(player, "varp.drunkmonkquest", stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage() = quest.stage(player)

        fun journal() = quest.questLog(access())

        fun count(obj: String) = player.inv.count(obj)

        fun onFloor(obj: String): Int =
            objRegistry.findAll(player.coords).filter { it.type == obj.asRSCM() }.sumOf { it.count }

        fun give(obj: String, count: Int = 1) {
            val slot = (0 until 28).first { player.inv[it] == null }
            player.inv[slot] = InvObj(obj, count)
        }

        fun talk(npc: String) = start(npc) { NpcEvents.Op1(it) }

        fun use(npc: String, obj: String) =
            start(npc) {
                val id = obj.asRSCM()
                val slot = (0 until 28).first { player.inv[it]?.id == id }
                val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
                val npcType = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM()))
                NpcUDefaultEvents.OpType(it, slot, type, npcType)
            }

        private fun start(npc: String, event: (Npc) -> SuspendEvent<ProtectedAccess>) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val target = Npc(npc, player.coords.translateZ(1))
            val block: suspend () -> Unit = { assertTrue(events.publish(access(), event(target))) }
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
            repeat(400) {
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

        fun assertRewards() {
            assertEquals(Complete, stage())
            assertEquals(Complete, player.vars["varp.drunkmonkquest"])
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(2000, player.statMap.getXP("stat.woodcutting").toInt())
            assertEquals(8, count(LawRune))
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
