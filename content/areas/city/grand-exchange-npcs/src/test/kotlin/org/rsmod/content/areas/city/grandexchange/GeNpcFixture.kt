package org.rsmod.content.areas.city.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.random.DefaultGameRandom
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
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class GeNpcFixture(members: Boolean = true, private val priceOf: (ItemServerType) -> Int? = { it.cost }) {
    val events = EventBus()
    val random = DefaultGameRandom(7L)
    private val client = RecordingClient()
    private val coroutine = GameCoroutine("ge-npcs-test")
    private var result: Result<Unit>? = null
    private val context =
        ProtectedAccessContextFactory.empty()
            .copy(
                getEventBus = { events },
                getAlignment = { TextAlignment() },
                getNpcInteractions = { NpcInteractions(events) },
                getRandom = { random },
            )

    val prices =
        object : MarketPrices {
            override fun get(type: ItemServerType): Int? = priceOf(type)
        }
    val window = PriceListWindow(prices)
    val decanter = Decanter(prices)

    @OptIn(InternalApi::class)
    val player =
        Player().apply {
            this.client = this@GeNpcFixture.client
            uuid = 811L
            slotId = 1
            assignUid()
            coords = CoordGrid(3165, 3485, 0)
            currentMapClock = 100
            processedMapClock = 100
            this.members = members
            inv =
                Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                    arrayOfNulls(28),
                )
        }

    init {
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val all: List<PluginScript> =
            listOf(
                PriceListScript(window),
                FaridMorrisane(window, random),
                ReloboBlinyo(window, random),
                Hofuthand(window, random),
                BobBarter(window, decanter, random),
                MurkyMatt(window, random),
                JamesBonds(),
            )
        for (script in all) {
            with(script) { scripts.startup() }
        }
    }

    fun script(script: PluginScript) {
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
    }

    fun give(obj: String, count: Int = 1, slot: Int = freeSlot()) {
        val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
        if (type.isStackable || count == 1) {
            player.inv[slot] = InvObj(type, count)
            return
        }
        repeat(count) { player.inv[freeSlot()] = InvObj(type, 1) }
    }

    fun giveNoted(obj: String, count: Int, slot: Int = freeSlot()) {
        val base = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
        player.inv[slot] = InvObj(checkNotNull(ServerCacheManager.getItem(base.certlink)), count)
    }

    fun count(obj: String): Int = player.inv.count(obj)

    fun countNoted(obj: String): Int {
        val base = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
        return player.inv.objs.filterNotNull().filter { it.id == base.certlink }.sumOf { it.count }
    }

    fun fill() {
        for (slot in 0 until 28) {
            if (player.inv[slot] == null) {
                player.inv[slot] = InvObj("obj.bronze_dagger", 1)
            }
        }
    }

    private fun freeSlot(): Int = (0 until 28).first { player.inv[it] == null }

    fun op(npc: String, op: Int) =
        start {
            val target = Npc(npc, coords.translateZ(1))
            val event =
                when (op) {
                    1 -> NpcEvents.Op1(target)
                    3 -> NpcEvents.Op3(target)
                    4 -> NpcEvents.Op4(target)
                    5 -> NpcEvents.Op5(target)
                    else -> error("op=$op")
                }
            assertTrue(events.publish(this, event), "no handler for $npc op$op")
        }

    fun access() = ProtectedAccess(player, coroutine, context)

    fun start(block: suspend ProtectedAccess.() -> Unit) {
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
                    this@GeNpcFixture.result = result
                }
            }
        )
        result?.getOrThrow()
    }

    fun finish(options: List<Int> = emptyList(), count: Int = 1) {
        val selections = options.iterator()
        repeat(400) {
            if (coroutine.isIdle) return
            advance(selections, count)
        }
        fail<Unit>("Interaction did not finish: ${output()}")
    }

    fun play(options: List<Int>) {
        val selections = options.iterator()
        repeat(400) {
            val atMenu =
                coroutine.isAwaiting(ResumePauseButtonInput::class) &&
                    player.ui.containsModal("interface.chatmenu")
            if (!selections.hasNext() && atMenu || coroutine.isIdle) return
            advance(selections, 1)
        }
        fail<Unit>("Interaction did not settle: ${output()}")
    }

    private fun advance(options: Iterator<Int>, count: Int) {
        if (coroutine.isAwaiting(ResumePCountDialogInput::class)) {
            coroutine.resumeWith(ResumePCountDialogInput(count))
        } else if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
            val input =
                when {
                    player.ui.containsModal("interface.chatmenu") ->
                        ResumePauseButtonInput(
                            "component.chatmenu:options",
                            if (options.hasNext()) options.next() else 1,
                        )
                    player.ui.containsModal("interface.decant") ->
                        ResumePauseButtonInput(
                            "component.decant:decant_${if (options.hasNext()) options.next() else 4}",
                            -1,
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

    fun output(): String = client.messages.joinToString("\n")

    fun said(text: String): Boolean = output().replace("<br>", " ").contains(text)

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
        fun initTransactions() {
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

        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
