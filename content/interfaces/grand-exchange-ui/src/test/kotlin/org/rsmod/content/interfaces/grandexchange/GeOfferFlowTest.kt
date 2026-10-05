package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.game.outgoing.varp.VarpLong
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.grandexchange.draft.OfferMath
import org.rsmod.api.grandexchange.engine.CollectPart
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.offer.OfferSlot
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.price.PriceFeed
import org.rsmod.api.grandexchange.price.PricePoint
import org.rsmod.api.grandexchange.rules.CacheItemCatalog
import org.rsmod.api.grandexchange.rules.GeTax
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePObjDialogInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drives the real offers-window handlers with the ops the 241 client sends, and checks that the
 * price the player sees (the client's own arithmetic, ported below) is the price the server
 * confirms and the exchange stores.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GeOfferFlowTest {
    private val catalog = CacheItemCatalog()
    private val whip = "obj.abyssal_whip".asRSCM(RSCMType.OBJ)
    private val notedWhip = checkNotNull(catalog.notedId(whip))
    private val scimitar = "obj.rune_scimitar".asRSCM(RSCMType.OBJ)

    private val feed =
        object : PriceFeed {
            override fun fetchLatest() =
                mapOf(
                    whip to PricePoint(high = 3_000_000, low = 2_900_000),
                    scimitar to PricePoint(high = 21_000, low = 20_000),
                )

            override fun fetchBuyLimits() = emptyMap<Int, Int>()
        }
    private val prices = GePrices(feed, null, { null }).also { check(it.refresh()) }
    private val exchange = GrandExchange(catalog, prices, GeTax(20, 5_000_000))
    private val sessions = GeSessions(exchange, catalog)
    private val setup = GeSetup(prices, catalog, sessions)
    private val windows = GeWindows(exchange, sessions, settings(), setup)
    private val collector = GeCollector(exchange, sessions, catalog)

    private class RecordingClient : Client<Any, Any> {
        val written = ArrayList<Any>()

        override fun write(message: Any) {
            written += message
        }

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    private fun settings() =
        org.rsmod.api.grandexchange.GrandExchangeSettings(
            enabled = true,
            fillModel = "instant",
            liveFetch = false,
            refreshMinutes = 5,
            snapshotPath = java.nio.file.Paths.get("unused.json"),
            userAgent = "test",
            taxRate = 0.02,
            taxCap = 5_000_000,
        )

    private fun player(id: Long): Player =
        Player().apply {
            client = RecordingClient()
            userId = id
            members = true
            currentMapClock = 100
            processedMapClock = 100
            inv =
                Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                    arrayOfNulls(28),
                )
        }

    private val Player.written: List<Any>
        get() = (client as RecordingClient).written

    private fun Player.coins(): Long = inv.totalOf(GeIds.coins) + inv.totalOf(GeIds.platinum) * 1000

    /** One player at the offers window: sends the same ops the client would and answers dialogs. */
    private inner class Window(val player: Player) {
        private val events = EventBus()
        private val coroutine = GameCoroutine("ge-flow-test")
        private var failure: Throwable? = null
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(GeOffersScript(exchange, sessions, collector, catalog, windows, setup)) {
                scripts.startup()
            }
            sessions.login(player)
        }

        val slotSession: GePlayer
            get() = sessions.of(player)

        @OptIn(InternalApi::class)
        private fun run(block: suspend ProtectedAccess.() -> Unit) {
            failure = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { ProtectedAccess(player, coroutine, context).block() }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        result.exceptionOrNull()?.let { failure = it }
                    }
                }
            )
            failure?.let { throw it }
        }

        fun click(component: String, child: Int, op: IfButtonOp = IfButtonOp.Op1) {
            val type =
                ServerCacheManager.fromComponent("component.$component".asRSCM(RSCMType.COMPONENT))
            run {
                events.publish(this, IfModalButton(type, comsub = child, obj = null, op = op))
            }
        }

        fun open() {
            windows.prepare(player)
        }

        fun newOffer(slot: Int, type: OfferType) {
            click("ge_offers:index_$slot", if (type == OfferType.BUY) 3 else 4)
        }

        fun chooseBuy(itemId: Int) {
            assertTrue(coroutine.isSuspended, "the item search is waiting for a choice")
            val item = checkNotNull(ServerCacheManager.getItem(itemId))
            coroutine.resumeWith(ResumePObjDialogInput(item))
            failure?.let { throw it }
        }

        fun answerCount(value: Int) {
            assertTrue(coroutine.isAwaiting(ResumePCountDialogInput::class), "a count is asked for")
            coroutine.resumeWith(ResumePCountDialogInput(value))
            failure?.let { throw it }
        }

        fun sell(invSlot: Int) = click("ge_offers_side:items", invSlot)

        fun setupOp(child: Int, op: IfButtonOp = IfButtonOp.Op1) = click("ge_offers:setup", child, op)

        fun confirm() = click("ge_offers:setup_confirm", -1)

        fun statusOp(child: Int) = click("ge_offers:details_status", child)

        val price: Int
            get() = player.geOfferPrice

        val clientPrice: Long
            get() = player.written.filterIsInstance<VarpLong>().last { it.id == 5753 }.value
    }

    /** The 241 clientscript arithmetic for the price buttons, ported verbatim. */
    private object ClientMath {
        const val MAX = 2_149_631_130_647L

        fun price(current: Long, button: Int): Long =
            when (button) {
                1 -> if (current < MAX) current + 1 else current
                -1 -> if (current > 1) current - 1 else current
                5 -> {
                    val step = maxOf(1L, current / 20)
                    if (MAX - step < current) MAX else current + step
                }
                -5 -> {
                    val step = maxOf(1L, current / 20)
                    if (step >= current) 1 else current - step
                }
                else -> error("no such button $button")
            }

        fun quantity(current: Int, delta: Int, sell: Boolean, available: Int): Int {
            var step = delta
            if (sell) {
                if (step >= Int.MAX_VALUE) return available
                if (step > 0) {
                    if (step > 1 && current == 1) step -= 1
                    return if (available - step < current) available else current + step
                }
            } else {
                if (step >= Int.MAX_VALUE) step = 1000
                if (step > 0) {
                    if (step > 1 && current == 1) step -= 1
                    return if (Int.MAX_VALUE - step < current) Int.MAX_VALUE else current + step
                }
            }
            return if (current <= -step) 1 else current + step
        }
    }

    private fun buyer(id: Long, coins: Int = 100_000_000): Pair<Player, Window> {
        val p = player(id)
        p.inv[0] = InvObj("obj.coins", coins)
        val w = Window(p)
        w.open()
        return p to w
    }

    private fun Window.startBuy(item: Int = whip, slot: Int = 0) {
        newOffer(slot, OfferType.BUY)
        chooseBuy(item)
    }

    private val PRICE_MINUS = 8
    private val PRICE_PLUS = 9
    private val PRICE_MINUS_5 = 10
    private val PRICE_GUIDE = 11
    private val PRICE_ENTER = 12
    private val PRICE_PLUS_5 = 13
    private val PRICE_MINUS_X = 14
    private val PRICE_PLUS_X = 15

    private val QTY_MINUS = 1
    private val QTY_PLUS_1 = 3
    private val QTY_PLUS_10 = 4
    private val QTY_PLUS_100 = 5
    private val QTY_MAX = 6
    private val QTY_ENTER = 7

    @Test
    fun `choosing an item shows the guide price on both sides of the wire`() {
        val (p, w) = buyer(100)
        w.startBuy()
        assertEquals(whip, p.geSearchItem)
        assertEquals(3_000_000, w.price)
        assertEquals(3_000_000L, w.clientPrice)
        assertEquals(1, p.geNewOfferQuantity)
    }

    @Test
    fun `every price button leaves the server on exactly the price the client shows`() {
        val (p, w) = buyer(101)
        w.startBuy()
        var shown = 3_000_000L
        for (button in listOf(5, 5, -5, 1, 1, -1, -5, 5)) {
            val child =
                when (button) {
                    5 -> PRICE_PLUS_5
                    -5 -> PRICE_MINUS_5
                    1 -> PRICE_PLUS
                    else -> PRICE_MINUS
                }
            shown = ClientMath.price(shown, button)
            w.setupOp(child)
            assertEquals(shown, w.price.toLong(), "after button $button")
            assertEquals(shown, w.clientPrice, "the client is sent the same value after $button")
        }
        w.confirm()
        assertEquals(shown.toInt(), w.slotSession.slot(0).price)
        assertEquals(p.geSelectedSlot, 1)
    }

    @Test
    fun `the client-only edits and the server mirror agree through a long random click run`() {
        val (_, w) = buyer(102)
        w.startBuy()
        val random = java.util.Random(7)
        var shown = 3_000_000L
        val buttons = listOf(5, -5, 1, -1)
        repeat(400) {
            val button = buttons[random.nextInt(buttons.size)]
            shown = ClientMath.price(shown, button)
            w.setupOp(
                when (button) {
                    5 -> PRICE_PLUS_5
                    -5 -> PRICE_MINUS_5
                    1 -> PRICE_PLUS
                    else -> PRICE_MINUS
                }
            )
            assertEquals(shown, w.price.toLong())
        }
    }

    @Test
    fun `entering a price sets exactly that price`() {
        val (_, w) = buyer(103)
        w.startBuy()
        w.setupOp(PRICE_ENTER)
        w.answerCount(1_234_567)
        assertEquals(1_234_567, w.price)
        assertEquals(1_234_567L, w.clientPrice)
        w.confirm()
        assertEquals(1_234_567, w.slotSession.slot(0).price)
        assertEquals(OfferState.OPEN, w.slotSession.slot(0).state)
    }

    @Test
    fun `the custom percent buttons need a percentage first and then step by it`() {
        val (p, w) = buyer(104)
        w.startBuy()
        w.setupOp(PRICE_PLUS_X)
        assertEquals(3_000_000, w.price, "no percentage chosen yet, nothing changes")

        w.setupOp(PRICE_PLUS_X, IfButtonOp.Op2)
        w.answerCount(10)
        assertEquals(10, p.gePriceCustom)
        w.setupOp(PRICE_PLUS_X)
        assertEquals(3_300_000, w.price)
        assertEquals(3_300_000L, w.clientPrice)
        w.setupOp(PRICE_MINUS_X)
        assertEquals(2_970_000, w.price)
        w.setupOp(PRICE_MINUS_X)
        assertEquals(2_673_000, w.price)
        assertEquals(2_673_000L, w.clientPrice)
        w.confirm()
        assertEquals(2_673_000, w.slotSession.slot(0).price)
    }

    @Test
    fun `the guide price button restores the guide of the side being set up`() {
        val (_, w) = buyer(105)
        w.startBuy()
        w.setupOp(PRICE_PLUS_5)
        w.setupOp(PRICE_PLUS_5)
        assertTrue(w.price > 3_000_000)
        w.setupOp(PRICE_GUIDE)
        assertEquals(3_000_000, w.price)
        assertEquals(3_000_000L, w.clientPrice)

        val seller = player(106)
        seller.inv[0] = InvObj(ServerCacheManager.getItem(whip)!!, 1)
        val sw = Window(seller)
        sw.open()
        sw.sell(0)
        assertEquals(2_900_000, sw.price, "sellers start at the price that sells instantly")
        sw.setupOp(PRICE_PLUS_5)
        sw.setupOp(PRICE_GUIDE)
        assertEquals(2_900_000, sw.price)
    }

    @Test
    fun `changing the item resets the price and quantity to the new item's guide`() {
        val (p, w) = buyer(107)
        w.startBuy()
        w.setupOp(PRICE_PLUS_5)
        w.setupOp(QTY_PLUS_10)
        assertEquals(3_150_000, w.price)
        assertEquals(10, p.geNewOfferQuantity)

        w.setupOp(0)
        w.chooseBuy(scimitar)
        assertEquals(scimitar, p.geSearchItem)
        assertEquals(21_000, w.price)
        assertEquals(21_000L, w.clientPrice)
        assertEquals(1, p.geNewOfferQuantity)
    }

    @Test
    fun `every quantity button follows the client arithmetic`() {
        val (p, w) = buyer(108)
        w.startBuy()
        var shown = 1
        for ((child, delta) in
            listOf(
                QTY_PLUS_10 to 10,
                QTY_PLUS_1 to 1,
                QTY_PLUS_100 to 100,
                QTY_MAX to Int.MAX_VALUE,
                QTY_MINUS to -1,
            )) {
            shown = ClientMath.quantity(shown, delta, sell = false, available = 0)
            w.setupOp(child)
            assertEquals(shown, p.geNewOfferQuantity, "after delta $delta")
        }
        assertEquals(1110, p.geNewOfferQuantity)

        w.setupOp(QTY_ENTER)
        w.answerCount(25)
        assertEquals(25, p.geNewOfferQuantity)
    }

    @Test
    fun `the first plus ten from one lands on ten, like the client`() {
        val (p, w) = buyer(109)
        w.startBuy()
        w.setupOp(QTY_PLUS_10)
        assertEquals(10, p.geNewOfferQuantity)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        assertEquals(1, p.geNewOfferQuantity)
        w.setupOp(QTY_PLUS_100)
        assertEquals(100, p.geNewOfferQuantity)
    }

    @Test
    fun `selling takes everything held with all and never more`() {
        val p = player(110)
        p.inv[0] = InvObj(ServerCacheManager.getItem(notedWhip)!!, 7)
        val w = Window(p)
        w.open()
        w.sell(0)
        assertEquals(whip, p.geSearchItem, "the unnoted item is offered")
        assertEquals(7, p.geNewOfferQuantity)
        w.setupOp(QTY_MINUS)
        w.setupOp(QTY_MINUS)
        assertEquals(5, p.geNewOfferQuantity)
        w.setupOp(QTY_MAX)
        assertEquals(7, p.geNewOfferQuantity)
        w.setupOp(QTY_PLUS_100)
        assertEquals(7, p.geNewOfferQuantity)
    }

    @Test
    fun `buy all affordable divides the coins by the price shown`() {
        val (p, w) = buyer(111, coins = 10_000_000)
        w.startBuy()
        w.setupOp(QTY_ENTER, IfButtonOp.Op2)
        assertEquals(3, p.geNewOfferQuantity)
    }

    @Test
    fun `a buy above the market fills at the market and refunds the difference`() {
        val (p, w) = buyer(112)
        w.startBuy()
        w.setupOp(PRICE_PLUS_5)
        w.setupOp(QTY_PLUS_10)
        assertEquals(3_150_000, w.price)
        val before = p.coins()
        w.confirm()

        val slot = w.slotSession.slot(0)
        assertEquals(3_150_000, slot.price)
        assertEquals(OfferState.COMPLETED, slot.state)
        assertEquals(10, slot.completedQuantity)
        assertEquals(30_000_000L, slot.completedGold)
        assertEquals(before - 10 * 3_150_000L, p.coins())
        assertEquals(10 * 150_000L, w.slotSession.box(0).coins)
        assertEquals(10, w.slotSession.box(0).itemCount)
    }

    @Test
    fun `a buy below the market rests in its slot at the price chosen and abort refunds the escrow`() {
        val (p, w) = buyer(113)
        w.startBuy()
        w.setupOp(PRICE_MINUS_5)
        w.setupOp(QTY_PLUS_10)
        assertEquals(2_850_000, w.price)
        val before = p.coins()
        w.confirm()

        val slot = w.slotSession.slot(0)
        assertEquals(OfferState.OPEN, slot.state)
        assertEquals(2_850_000, slot.price)
        assertEquals(10, slot.quantity)
        assertEquals(0, slot.completedQuantity)
        assertEquals(before - 10 * 2_850_000L, p.coins())

        val shown = p.written.filterIsInstance<UpdateStockMarketSlotV2>().last { it.slot == 0 }
        val set = shown.update as UpdateStockMarketSlotV2.SetStockMarketSlot
        assertEquals(2, set.status)
        assertEquals(whip, set.obj)
        assertEquals(2_850_000L, set.price)
        assertEquals(10, set.count)
        assertEquals(0, set.completedCount)
        assertEquals(1, p.geSelectedSlot, "the status view of the slot is showing")
        assertEquals(1, exchange.openOfferCount)

        w.statusOp(0)
        val aborted = w.slotSession.slot(0)
        assertEquals(OfferState.ABORTED, aborted.state)
        assertEquals(10 * 2_850_000L, w.slotSession.box(0).coins)
        assertEquals(0, exchange.openOfferCount)
        collector.collectSlot(p, 0, toBank = false)
        assertEquals(before, p.coins())
        assertTrue(w.slotSession.slot(0).isEmpty)
    }

    @Test
    fun `a sell below the market fills at the market price`() {
        val p = player(114)
        p.inv[0] = InvObj(ServerCacheManager.getItem(whip)!!, 1)
        val w = Window(p)
        w.open()
        w.sell(0)
        w.setupOp(PRICE_MINUS_5)
        assertEquals(2_755_000, w.price)
        w.confirm()

        val slot = w.slotSession.slot(0)
        assertEquals(OfferState.COMPLETED, slot.state)
        assertEquals(2_900_000L, slot.completedGold)
        assertEquals(2_900_000L - slot.tax, w.slotSession.box(0).coins)
    }

    @Test
    fun `a sell above the market rests at the price chosen and abort returns the item`() {
        val p = player(115)
        p.inv[0] = InvObj(ServerCacheManager.getItem(notedWhip)!!, 3)
        val w = Window(p)
        w.open()
        w.sell(0)
        w.setupOp(PRICE_PLUS_5)
        assertEquals(3_045_000, w.price)
        w.confirm()

        val slot = w.slotSession.slot(0)
        assertEquals(OfferState.OPEN, slot.state)
        assertEquals(3_045_000, slot.price)
        assertEquals(whip, slot.itemId)
        assertEquals(3, slot.quantity)
        assertEquals(0L, p.inv.totalOf(notedWhip))
        val set =
            (p.written.filterIsInstance<UpdateStockMarketSlotV2>().last { it.slot == 0 }.update
                as UpdateStockMarketSlotV2.SetStockMarketSlot)
        assertEquals(2 or 8, set.status)
        assertEquals(whip, set.obj)
        assertEquals(3_045_000L, set.price)

        w.statusOp(0)
        assertEquals(3, w.slotSession.box(0).itemCount)
        collector.collectSlot(p, 0, toBank = false)
        assertEquals(3L, p.inv.totalOf(notedWhip))
    }

    @Test
    fun `a resting buy fills at its own price when another player's sell arrives later`() {
        val (buyerPlayer, bw) = buyer(116)
        bw.startBuy()
        bw.setupOp(PRICE_MINUS_5)
        bw.setupOp(QTY_PLUS_10)
        bw.confirm()
        assertEquals(OfferState.OPEN, bw.slotSession.slot(0).state)

        val seller = player(117)
        for (i in 0 until 4) seller.inv[i] = InvObj(ServerCacheManager.getItem(whip)!!, 1)
        val sw = Window(seller)
        sw.open()
        sw.sell(0)
        sw.setupOp(QTY_PLUS_1)
        sw.setupOp(QTY_PLUS_1)
        sw.setupOp(QTY_PLUS_1)
        sw.setupOp(PRICE_MINUS_5)
        assertEquals(2_755_000, sw.price)
        sw.confirm()

        val sold = sw.slotSession.slot(0)
        assertEquals(OfferState.COMPLETED, sold.state)
        assertEquals(4 * 2_850_000L, sold.completedGold, "the resting price decides")
        val bought = bw.slotSession.slot(0)
        assertEquals(OfferState.OPEN, bought.state)
        assertEquals(4, bought.completedQuantity)
        assertEquals(4 * 2_850_000L, bought.completedGold)
        assertEquals(4, bw.slotSession.box(0).itemCount)
        val shown =
            (buyerPlayer.written.filterIsInstance<UpdateStockMarketSlotV2>().last { it.slot == 0 }.update
                as UpdateStockMarketSlotV2.SetStockMarketSlot)
        assertEquals(4, shown.completedCount)
        assertEquals(10, shown.count)
        assertEquals(2, shown.status)
    }

    @Test
    fun `a resting sell fills at its own price when another player's buy arrives later`() {
        val p = player(118)
        p.inv[0] = InvObj(ServerCacheManager.getItem(whip)!!, 1)
        val sw = Window(p)
        sw.open()
        sw.sell(0)
        sw.setupOp(PRICE_PLUS_5)
        sw.confirm()
        assertEquals(OfferState.OPEN, sw.slotSession.slot(0).state)

        val (_, bw) = buyer(119)
        bw.startBuy()
        bw.setupOp(PRICE_PLUS_5)
        bw.setupOp(PRICE_PLUS_5)
        assertEquals(3_307_500, bw.price)
        bw.confirm()

        val bought = bw.slotSession.slot(0)
        assertEquals(OfferState.COMPLETED, bought.state)
        assertEquals(3_045_000L, bought.completedGold, "the resting sell price decides")
        assertEquals(3_307_500L - 3_045_000L, bw.slotSession.box(0).coins)
        assertEquals(OfferState.COMPLETED, sw.slotSession.slot(0).state)
        assertEquals(3_045_000L, sw.slotSession.slot(0).completedGold)
    }

    @Test
    fun `the slot and the status view are told the item and price of an offer`() {
        val p = player(120)
        p.inv[0] = InvObj(ServerCacheManager.getItem(notedWhip)!!, 2)
        val w = Window(p)
        w.open()
        w.sell(0)
        w.setupOp(PRICE_PLUS_5)
        w.confirm()

        val slotUpdate = p.written.filterIsInstance<UpdateStockMarketSlotV2>().last { it.slot == 0 }
        val set = slotUpdate.update as UpdateStockMarketSlotV2.SetStockMarketSlot
        assertEquals(whip, set.obj, "the slot shows the unnoted item")
        assertEquals(2, set.count)
        assertEquals(3_045_000L, set.price)

        val descriptions = p.written.filterIsInstance<RunClientScript>().filter { it.id == 5730 }
        val details = "component.ge_offers:details_desc".asRSCM(RSCMType.COMPONENT)
        val shown = descriptions.last { it.values.contains(details) }
        assertEquals(ServerCacheManager.getItem(whip)!!.examine, shown.values.first())
        assertEquals(1, p.geSelectedSlot)
        assertEquals(whip, p.geSearchItem)
        assertNotNull(p.written.filterIsInstance<RunClientScript>().lastOrNull { it.id == 804 })
    }

    @Test
    fun `a slot that is still open keeps its price after the player logs out and in again`() {
        val (p, w) = buyer(121)
        w.startBuy()
        w.setupOp(PRICE_MINUS_5)
        w.confirm()
        sessions.logout(p)

        val again = player(121)
        again.vars.backing.putAll(p.vars.backing)
        sessions.login(again)
        assertEquals(2_850_000, sessions.of(again).slot(0).price)
        assertEquals(OfferState.OPEN, sessions.of(again).slot(0).state)
        sessions.logout(again)
    }

    @Test
    fun `confirming with no price set places nothing`() {
        val (_, w) = buyer(122)
        w.newOffer(0, OfferType.BUY)
        w.confirm()
        assertTrue(w.slotSession.slot(0).isEmpty)
        assertFalse(exchange.openOfferCount > 0 && w.slotSession.slot(0).isOpen)
    }

    @Test
    fun `offer math and the client port agree across the whole price range`() {
        for (start in listOf(1L, 2L, 19L, 20L, 21L, 999L, 123_456L, 2_000_000_000L)) {
            for (button in listOf(5, -5, 1, -1)) {
                val client = ClientMath.price(start, button)
                val server =
                    when (button) {
                        5 -> OfferMath.stepPricePercent(start, 5, up = true)
                        -5 -> OfferMath.stepPricePercent(start, 5, up = false)
                        1 -> OfferMath.stepPrice(start, up = true)
                        else -> OfferMath.stepPrice(start, up = false)
                    }
                assertEquals(client.coerceAtMost(OfferMath.MAX_PRICE), server, "start=$start button=$button")
            }
        }
    }

    @Test
    fun `the setup panel is given the item's description and guide price`() {
        val (p, w) = buyer(123)
        w.startBuy()
        val desc = "component.ge_offers:setup_desc".asRSCM(RSCMType.COMPONENT)
        val script = p.written.filterIsInstance<RunClientScript>().last { it.id == 5730 }
        assertEquals(ServerCacheManager.getItem(whip)!!.examine, script.values.first())
        assertEquals(desc, script.values[2])
        val guide =
            p.written
                .filterIsInstance<net.rsprot.protocol.game.outgoing.interfaces.IfSetText>()
                .last { it.combinedId == "component.ge_offers:setup_marketprice".asRSCM(RSCMType.COMPONENT) }
        assertEquals("Guide price: 3,000,000 coins", guide.text)
    }

    @Test
    fun `an offer that is not collected is still collectable once it has filled`() {
        val (p, w) = buyer(124)
        w.startBuy()
        w.confirm()
        exchange.collect(
            w.slotSession,
            0,
            CollectPart.ALL,
            noted = false,
            sink = InventorySink(p, catalog, false),
        )
        assertEquals(OfferSlot.EMPTY, w.slotSession.slot(0))
        assertEquals(1L, p.inv.totalOf(whip))
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
