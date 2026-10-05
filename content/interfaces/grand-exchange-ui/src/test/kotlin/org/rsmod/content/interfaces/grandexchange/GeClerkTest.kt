package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import java.nio.file.Paths
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
import org.rsmod.api.grandexchange.GrandExchangeSettings
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.rules.CacheItemCatalog
import org.rsmod.api.grandexchange.rules.GeTax
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
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
class GeClerkTest {
    private val catalog = CacheItemCatalog()
    private val exchange = GrandExchange(catalog, GePrices(null, null, { null }), GeTax(20, 5_000_000))
    private val sessions = GeSessions(exchange, catalog)
    private val itemSets = GeItemSets()
    private val windows =
        GeWindows(
            exchange,
            sessions,
            GrandExchangeSettings(true, "instant", false, 5, Paths.get("unused.json"), "test", 0.02, 5_000_000),
            GeSetup(GePrices(null, null, { null }), catalog, sessions),
        )
    private val clerks = GeClerkDialogue(windows, itemSets, sessions)

    private class Recording : Client<Any, Any> {
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

    private inner class Scene {
        val events = EventBus()
        val client = Recording()
        val coroutine = GameCoroutine("ge-clerk-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(getEventBus = { events }, getAlignment = { TextAlignment() })

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Scene.client
                uuid = 901L
                userId = 901L
                slotId = 1
                assignUid()
                coords = CoordGrid(3165, 3485, 0)
                currentMapClock = 100
                processedMapClock = 100
                members = true
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
            }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun said(text: String) = client.messages.joinToString("\n").replace("<br>", " ").contains(text)

        fun give(obj: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            if (type.isStackable || count == 1) {
                player.inv[(0 until 28).first { player.inv[it] == null }] = InvObj(type, count)
            } else {
                repeat(count) { player.inv[(0 until 28).first { player.inv[it] == null }] = InvObj(type, 1) }
            }
        }

        fun count(obj: String) = player.inv.count(obj)

        fun talk(options: List<Int> = emptyList()) {
            val npc = Npc("npc.ge_clerk_1", player.coords.translateZ(1))
            start { startDialogue(npc) { clerks.greet(this) } }
            val selections = options.iterator()
            repeat(300) {
                val atMenu =
                    coroutine.isAwaiting(ResumePauseButtonInput::class) &&
                        player.ui.containsModal("interface.chatmenu")
                if (coroutine.isIdle || (atMenu && !selections.hasNext())) return
                advance(selections)
            }
            fail<Unit>("clerk dialogue did not settle")
        }

        private fun start(block: suspend ProtectedAccess.() -> Unit) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { access().block() }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Scene.result = result
                    }
                }
            )
            result?.getOrThrow()
        }

        private fun advance(options: Iterator<Int>) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val input =
                    if (player.ui.containsModal("interface.chatmenu")) {
                        ResumePauseButtonInput(
                            "component.chatmenu:options",
                            if (options.hasNext()) options.next() else 1,
                        )
                    } else {
                        val parent =
                            listOf("chat_left", "chat_right").first {
                                player.ui.containsModal("interface.$it")
                            }
                        ResumePauseButtonInput("component.$parent:continue", -1)
                    }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            result?.getOrThrow()
        }
    }

    @Test
    fun `the clerk greets with the trade or sets question and lets the player go`() {
        val s = Scene()
        s.talk(listOf(5))
        assertTrue(s.said("Would you like to trade now, or exchange item sets?"))
        assertTrue(s.said("I'm fine, thanks."))
    }

    @Test
    fun `the how to branch explains buying and selling and returns without its own option`() {
        val s = Scene()
        s.talk(listOf(1, 4))
        assertTrue(s.said("To sell something"))
        assertTrue(s.said("To buy something"))
        assertTrue(s.said("cancel the offer"))
        assertTrue(s.said("I'm fine, thanks."))
    }

    @Test
    fun `collection reminders can be switched on and off from the dialogue`() {
        val s = Scene()
        assertFalse(s.player.geCollectReminders)
        s.talk(listOf(4, 1))
        assertTrue(s.player.geCollectReminders)
        assertTrue(s.said("we'll send you a message about any items"))

        val t = Scene()
        t.player.geCollectReminders = true
        t.talk(listOf(4, 1))
        assertFalse(t.player.geCollectReminders)
        assertTrue(t.said("no longer be reminded"))
    }

    @Test
    fun `the reminder option flips back from the confirmation menu`() {
        val s = Scene()
        s.talk(listOf(4, 2))
        assertFalse(s.player.geCollectReminders)
        assertTrue(s.said("no longer be reminded"))
    }

    @Test
    fun `players with reminders on are told about waiting items at login`() {
        val s = Scene()
        s.player.geCollectReminders = true
        sessions.of(s.player).box(2).addCoins(500)
        clerks.remind(s.player)
        assertTrue(s.said("waiting to be collected"))

        val quiet = Scene()
        quiet.player.geCollectReminders = true
        clerks.remind(quiet.player)
        assertFalse(quiet.said("waiting to be collected"))

        val off = Scene()
        sessions.of(off.player).box(0).addCoins(500)
        clerks.remind(off.player)
        assertFalse(off.said("waiting to be collected"))
    }

    private fun setIndex(setObj: String): Int {
        val id = setObj.asRSCM(RSCMType.OBJ)
        val list = checkNotNull(ServerCacheManager.getEnum("enum.ge_item_sets".asRSCM(RSCMType.ENUM)))
        return list.values.entries
            .first { (_, enumId) ->
                checkNotNull(ServerCacheManager.getEnum(enumId as Int)).values[-1] == id
            }
            .key
    }

    private fun partsOf(setObj: String): List<Int> {
        val index = setIndex(setObj)
        val list = checkNotNull(ServerCacheManager.getEnum("enum.ge_item_sets".asRSCM(RSCMType.ENUM)))
        val set = checkNotNull(ServerCacheManager.getEnum(list.values[index] as Int))
        return set.values.keys.filter { it >= 0 }.sorted().map { set.values[it] as Int }
    }

    @Test
    fun `the cache holds the item set lists the clerks use`() {
        val list = checkNotNull(ServerCacheManager.getEnum("enum.ge_item_sets".asRSCM(RSCMType.ENUM)))
        assertTrue(list.values.size > 20)
        assertEquals(list.values.size, itemSets.setCount)
        assertTrue(partsOf("obj.set_adamant_legs").size >= 4)
    }

    @Test
    fun `a full set of parts packs into the set item and takes nothing else`() {
        val s = Scene()
        val parts = partsOf("obj.set_adamant_legs")
        for (part in parts) {
            s.player.inv[(0 until 28).first { s.player.inv[it] == null }] =
                InvObj(checkNotNull(ServerCacheManager.getItem(part)), 1)
        }
        s.give("obj.coins", 10)
        itemSets.pack(s.access(), setIndex("obj.set_adamant_legs"), IfButtonOp.Op1)
        assertEquals(1, s.count("obj.set_adamant_legs"))
        assertEquals(10, s.count("obj.coins"))
        for (part in parts) {
            assertEquals(0L, s.player.inv.totalOf(part))
        }
    }

    @Test
    fun `noted parts are used when the plain ones are missing`() {
        val s = Scene()
        val parts = partsOf("obj.set_adamant_legs")
        for (part in parts) {
            val type = checkNotNull(ServerCacheManager.getItem(part))
            s.player.inv[(0 until 28).first { s.player.inv[it] == null }] =
                InvObj(checkNotNull(ServerCacheManager.getItem(type.certlink)), 1)
        }
        itemSets.pack(s.access(), setIndex("obj.set_adamant_legs"), IfButtonOp.Op1)
        assertEquals(1, s.count("obj.set_adamant_legs"))
    }

    @Test
    fun `a missing part lists the contents instead and keeps every item`() {
        val s = Scene()
        val parts = partsOf("obj.set_adamant_legs")
        for (part in parts.drop(1)) {
            s.player.inv[(0 until 28).first { s.player.inv[it] == null }] =
                InvObj(checkNotNull(ServerCacheManager.getItem(part)), 1)
        }
        itemSets.pack(s.access(), setIndex("obj.set_adamant_legs"), IfButtonOp.Op1)
        assertEquals(0, s.count("obj.set_adamant_legs"))
        assertTrue(s.said("contains:"))
        assertEquals(parts.size - 1, s.player.inv.objs.count { it != null })
    }

    @Test
    fun `a set in the pack unpacks into its parts, noted when the set was noted`() {
        val s = Scene()
        s.give("obj.set_adamant_legs")
        itemSets.unpack(s.access(), 0, IfButtonOp.Op1)
        assertEquals(0, s.count("obj.set_adamant_legs"))
        val parts = partsOf("obj.set_adamant_legs")
        for (part in parts) {
            assertEquals(1L, s.player.inv.totalOf(part))
        }

        val noted = Scene()
        val setType = checkNotNull(ServerCacheManager.getItem("obj.set_adamant_legs".asRSCM()))
        noted.player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(setType.certlink)), 1)
        itemSets.unpack(noted.access(), 0, IfButtonOp.Op1)
        for (part in parts) {
            val partType = checkNotNull(ServerCacheManager.getItem(part))
            assertEquals(1L, noted.player.inv.totalOf(partType.certlink))
        }
    }

    @Test
    fun `unpacking with no room leaves the set where it is`() {
        val s = Scene()
        s.give("obj.set_adamant_legs")
        for (slot in 1 until 28) {
            s.player.inv[slot] = InvObj("obj.bronze_dagger", 1)
        }
        itemSets.unpack(s.access(), 0, IfButtonOp.Op1)
        assertEquals(1, s.count("obj.set_adamant_legs"))
        assertTrue(s.said("enough inventory space"))
    }

    @Test
    fun `ultimate ironmen cannot assemble sets`() {
        val s = Scene()
        s.player.gamemode = PlayerGamemode.ULTIMATE_IRONMAN
        for (part in partsOf("obj.set_adamant_legs")) {
            s.player.inv[(0 until 28).first { s.player.inv[it] == null }] =
                InvObj(checkNotNull(ServerCacheManager.getItem(part)), 1)
        }
        itemSets.pack(s.access(), setIndex("obj.set_adamant_legs"), IfButtonOp.Op1)
        assertEquals(0, s.count("obj.set_adamant_legs"))
        assertTrue(s.said("cannot assemble"))
    }

    @Test
    fun `examining a set card shows the item's examine text`() {
        val s = Scene()
        itemSets.pack(s.access(), setIndex("obj.set_adamant_legs"), IfButtonOp.Op10)
        val examine = checkNotNull(ServerCacheManager.getItem("obj.set_adamant_legs".asRSCM())).examine
        assertTrue(examine.isNotBlank())
        assertTrue(s.said(examine))
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
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
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
