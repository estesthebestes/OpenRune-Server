package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import dev.openrune.types.InvStackType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import dev.openrune.types.varp.bits
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpLong
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.grandexchange.GrandExchangeSettings
import org.rsmod.api.grandexchange.engine.CollectPart
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.engine.HistoryEntry
import org.rsmod.api.grandexchange.engine.PlaceResult
import org.rsmod.api.grandexchange.offer.OfferSlot
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.price.MarketQuote
import org.rsmod.api.grandexchange.price.MarketQuoteSource
import org.rsmod.api.grandexchange.price.PriceOrigin
import org.rsmod.api.grandexchange.rules.CacheItemCatalog
import org.rsmod.api.grandexchange.rules.GeTax
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GePlayerAdapterTest {
    private val catalog = CacheItemCatalog()
    private val whip = "obj.abyssal_whip".asRSCM(RSCMType.OBJ)
    private val notedWhip = checkNotNull(catalog.notedId(whip))

    private val quotes =
        object : MarketQuoteSource {
            override fun quote(itemId: Int) = MarketQuote(3_000_000, 2_900_000, PriceOrigin.LIVE)
        }
    private val exchange = GrandExchange(catalog, quotes, GeTax(20, 5_000_000))
    private val sessions = GeSessions(exchange, catalog)

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

    private fun player(id: Long, members: Boolean = true): Player =
        Player().apply {
            client = RecordingClient()
            userId = id
            this.members = members
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

    @Test
    fun `slot records persist in the allocated varps and stay independent`() {
        val p = player(1)
        val session = GePlayer(p, catalog)
        val first = OfferSlot(OfferState.OPEN, OfferType.BUY, whip, 10, 3_000_000, 4, 12_000_000, 0)
        val last = OfferSlot(OfferState.COMPLETED, OfferType.SELL, whip, 3, 2_900_000, 3, 8_700_000, 174_000)
        session.setSlot(0, first)
        session.setSlot(7, last)

        val reread = GePlayer(p, catalog)
        assertEquals(first, reread.slot(0))
        assertEquals(last, reread.slot(7))
        assertTrue((1..6).all { reread.slot(it).isEmpty })
        assertEquals(174_000, p.vars.backing[GeIds.taxVarps[7].id])

        val allocated = (64100..64139).toSet() - 64132
        val used = GeIds.slotWords.map { it.id }
        assertEquals(allocated, used.toSet())
        assertTrue(used.filter { p.vars.backing[it] != 0 }.all { it in allocated })
    }

    @Test
    fun `a box keeps coins as coins until they no longer fit a stack`() {
        val p = player(2)
        val box = GePlayer(p, catalog).box(0)
        box.addItems(whip, 5)
        box.addCoins(1_500)
        assertEquals(1_500L, box.coins)
        assertEquals(1_500, p.invMap.getOrPut(GeIds.boxInvs[0])[InvCollectionBox.COIN_SLOT]?.count)

        box.addCoins(Int.MAX_VALUE.toLong() + 1_000)
        val inv = p.invMap.getOrPut(GeIds.boxInvs[0])
        assertEquals(Int.MAX_VALUE.toLong() + 2_500, box.coins)
        assertEquals(147, inv[InvCollectionBox.COIN_SLOT]?.count)
        assertEquals(2_147_486, inv[InvCollectionBox.TOKEN_SLOT]?.count)

        box.removeCoins(box.coins)
        assertEquals(0L, box.coins)
        box.removeItems(5)
        assertTrue(box.isEmpty())
    }

    @Test
    fun `escrow takes coins then platinum and hands change back`() {
        val p = player(3)
        val session = GePlayer(p, catalog)
        p.inv[0] = InvObj("obj.coins", 500)
        p.inv[1] = InvObj("obj.platinum", 3)
        assertEquals(3_500L, p.coins())

        assertFalse(session.takeCoins(3_501))
        assertEquals(3_500L, p.coins())

        assertTrue(session.takeCoins(1_200))
        assertEquals(2_300L, p.coins())
        assertTrue(session.takeCoins(2_300))
        assertEquals(0L, p.coins())
    }

    @Test
    fun `escrow takes unnoted items first and then noted ones, all or nothing`() {
        val p = player(4)
        val session = GePlayer(p, catalog)
        p.inv[0] = InvObj(ServerCacheManager.getItem(whip)!!, 1)
        p.inv[1] = InvObj(ServerCacheManager.getItem(notedWhip)!!, 4)

        assertFalse(session.takeItems(whip, 6))
        assertEquals(1L, p.inv.totalOf(whip))
        assertEquals(4L, p.inv.totalOf(notedWhip))

        assertTrue(session.takeItems(whip, 3))
        assertEquals(0L, p.inv.totalOf(whip))
        assertEquals(2L, p.inv.totalOf(notedWhip))
    }

    @Test
    fun `two players trade through real inventories and collect what they earned`() {
        val seller = player(10)
        val buyer = player(11)
        seller.inv[0] = InvObj(ServerCacheManager.getItem(notedWhip)!!, 5)
        buyer.inv[0] = InvObj("obj.coins", 50_000_000)
        val sellerStart = seller.coins()
        sessions.login(seller)
        sessions.login(buyer)
        val s = sessions.of(seller)
        val b = sessions.of(buyer)

        val offered = exchange.place(s, 0, OfferType.SELL, whip, 5, 3_500_000)
        assertEquals(0, (offered as PlaceResult.Placed).filledQuantity)
        assertEquals(0L, seller.inv.totalOf(notedWhip))

        val bought = exchange.place(b, 0, OfferType.BUY, whip, 2, 3_600_000)
        assertEquals(2, (bought as PlaceResult.Placed).filledQuantity)
        assertEquals(50_000_000L - 2 * 3_500_000, buyer.coins() + b.box(0).coins)

        val sinkNotes = InventorySink(buyer, catalog, toBank = false)
        exchange.collect(b, 0, CollectPart.ALL, noted = true, sink = sinkNotes)
        assertTrue(b.slot(0).isEmpty)
        assertEquals(2L, buyer.inv.totalOf(notedWhip))
        assertEquals(50_000_000L - 2 * 3_500_000, buyer.coins())

        exchange.collect(s, 0, CollectPart.ALL, noted = false, sink = InventorySink(seller, catalog, false))
        val fee = 2 * (3_500_000L * 20 / 1000)
        assertEquals(sellerStart + 2 * 3_500_000L - fee, seller.coins())
        assertEquals(OfferState.OPEN, s.slot(0).state)

        exchange.abort(s, 0)
        exchange.collect(s, 0, CollectPart.ALL, noted = true, sink = InventorySink(seller, catalog, false))
        assertTrue(s.slot(0).isEmpty)
        assertEquals(3L, seller.inv.totalOf(notedWhip))
    }

    @Test
    fun `the client is told about every slot change`() {
        val p = player(12)
        p.inv[0] = InvObj("obj.coins", 10_000_000)
        sessions.login(p)
        val session = sessions.of(p)
        val before = p.written.size
        exchange.place(session, 2, OfferType.BUY, whip, 1, 2_000_000)

        val updates = p.written.drop(before).filterIsInstance<UpdateStockMarketSlotV2>()
        assertTrue(updates.isNotEmpty())
        assertEquals(2, updates.last().slot)
        val set = updates.last().update as UpdateStockMarketSlotV2.SetStockMarketSlot
        assertEquals(whip, set.obj)
        assertEquals(2_000_000L, set.price)
        assertEquals(1, set.count)
        assertEquals(0, set.completedCount)
        assertEquals(2, set.status)
    }

    @Test
    fun `offers leave the book on logout and rejoin on login`() {
        val p = player(13)
        p.inv[0] = InvObj("obj.coins", 10_000_000)
        sessions.login(p)
        exchange.place(sessions.of(p), 0, OfferType.BUY, whip, 1, 2_000_000)
        val resting = exchange.openOfferCount
        assertTrue(resting >= 1)

        sessions.logout(p)
        assertEquals(resting - 1, exchange.openOfferCount)
        assertFalse(exchange.isOnline(13))

        val again = player(13)
        again.vars.backing.putAll(p.vars.backing)
        sessions.login(again)
        assertEquals(resting, exchange.openOfferCount)
        assertTrue(exchange.isOnline(13))
        sessions.logout(again)
    }

    @Test
    fun `the history keeps the newest ten trades in a persistent inventory`() {
        val p = player(14)
        for (n in 1..12) {
            GeHistoryStore.push(
                p,
                HistoryEntry(whip, if (n % 2 == 0) OfferType.SELL else OfferType.BUY, n, n * 1_000_000_007L, n * 3L),
            )
        }
        val read = GeHistoryStore.read(p)
        assertEquals(GeHistoryStore.ENTRIES, read.size)
        assertEquals(12, read.first().quantity)
        assertEquals(3, read.last().quantity)
        assertEquals(OfferType.SELL, read.first().type)
        assertEquals(12 * 1_000_000_007L, read.first().gold)
        assertEquals(36L, read.first().tax)
    }

    @Test
    fun `history counts round trip at the extremes`() {
        val entry = HistoryEntry(whip, OfferType.SELL, Int.MAX_VALUE, OfferSlot.MAX_TOTAL, Int.MAX_VALUE - 1L)
        assertEquals(entry, GeHistoryStore.entryOf(whip, GeHistoryStore.counts(entry)))
        val small = HistoryEntry(whip, OfferType.BUY, 1, 0, 0)
        assertEquals(small, GeHistoryStore.entryOf(whip, GeHistoryStore.counts(small)))
    }

    @Test
    fun `the packed configs make the exchange state persistent and keep it off the wire`() {
        for (name in GeIds.boxInvs + GeIds.HISTORY_INV) {
            val type = checkNotNull(ServerCacheManager.getInventory(name.asRSCM(RSCMType.INV))) { name }
            assertEquals(InvScope.Perm, type.scope, name)
            assertEquals(InvStackType.Always, type.stack, name)
            assertFalse(type.protect, name)
        }
        for (name in GeIds.boxInvs) {
            assertEquals(3, ServerCacheManager.getInventory(name.asRSCM(RSCMType.INV))!!.size, name)
        }
        assertEquals(40, ServerCacheManager.getInventory(GeIds.HISTORY_INV.asRSCM(RSCMType.INV))!!.size)
        for (varp in GeIds.slotWords + GeIds.taxVarps) {
            assertEquals(VarpLifetime.Perm, varp.scope)
            assertEquals(VarpTransmitLevel.Never, varp.transmit)
        }
        assertEquals(64100, GeIds.slotWords.first().id)
        assertEquals(64139, GeIds.slotWords.last().id)
        assertEquals(5754, GeIds.taxVarps.first().id)
    }

    @Test
    fun `every script registers its handlers against names that exist in the cache`() {
        val prices = GePrices(null, null, { null })
        val windows = GeWindows(exchange, sessions, settings(enabled = true), GeSetup(GePrices(null, null, { null }), catalog))
        val collector = GeCollector(exchange, sessions, catalog)
        val scripts =
            listOf(
                GeOffersScript(exchange, sessions, collector, catalog, windows, GeSetup(prices, catalog)),
                GeCollectScript(sessions, collector, GeSetup(prices, catalog)),
                GeHistoryScript(windows),
                GeEntryScript(exchange, sessions, windows, prices),
            )
        for (script in scripts) {
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(script) { context.startup() }
        }
        val names =
            mapOf(
                RSCMType.VARBIT to
                    listOf(
                        "ge_selectedslot",
                        "ge_newoffer_type",
                        "ge_newoffer_quantity",
                        "ge_price_custom",
                        "ge_transmit_taxrate",
                    ),
                RSCMType.VARP to
                    listOf(
                        "tradingpost_search",
                        "ge_last_searched",
                        "ge_last_offer_item",
                        "ge_last_offer_quantity",
                        "ge_last_offer_price",
                        "ge_last_offer_type",
                    ),
                RSCMType.CLIENTSCRIPT to
                    listOf(
                        "[clientscript,ge_history_init]",
                        "[clientscript,ge_history_addline]",
                        "[clientscript,ge_history_finish]",
                        "[clientscript,ge_offers_setdesc]",
                    ),
                RSCMType.JINGLE to listOf("grand_exchange_trade_jingle"),
                RSCMType.INTERFACE to listOf("ge_offers", "ge_offers_side", "ge_collect", "ge_history"),
                RSCMType.COMPONENT to
                    listOf(
                        "ge_offers:setup_desc",
                        "ge_offers:setup_fee",
                        "ge_offers:setup_marketprice",
                        "ge_offers:details_desc",
                        "ge_offers:details_fee",
                        "ge_offers:details_marketprice",
                    ),
            )
        for ((type, list) in names) {
            for (name in list) {
                val prefix = type.name.lowercase()
                assertTrue("$prefix.$name".asRSCM(type) >= 0, "$prefix.$name")
            }
        }
        assertEquals(5730, "clientscript.[clientscript,ge_offers_setdesc]".asRSCM(RSCMType.CLIENTSCRIPT))
        assertEquals(1644, "clientscript.[clientscript,ge_history_init]".asRSCM(RSCMType.CLIENTSCRIPT))
    }

    @Test
    fun `the client varps the offers window reads are transmitted when written`() {
        for (name in listOf("armourhitsound", "bankpin_2", "bank_extratab", "tradingpost_search", "ge_last_offer_item")) {
            val varp = checkNotNull(ServerCacheManager.getVarp("varp.$name".asRSCM(RSCMType.VARP))) { name }
            assertNotEquals(VarpTransmitLevel.Never, varp.transmit, name)
        }
        val expected =
            mapOf(
                "ge_selectedslot" to (375 to 4..7),
                "ge_newoffer_type" to (563 to 31..31),
                "ge_newoffer_quantity" to (563 to 0..30),
                "ge_price_custom" to (3750 to 13..19),
                "ge_transmit_taxrate" to (375 to 20..28),
            )
        for ((name, place) in expected) {
            val bit = checkNotNull(ServerCacheManager.getVarbit("varbit.$name".asRSCM(RSCMType.VARBIT))) { name }
            assertEquals(place.first, bit.varp, name)
            assertEquals(place.second, bit.bits, name)
        }
    }

    private val setupPrices = GePrices(null, null, { 3_000_000 })
    private val setup = GeSetup(setupPrices, catalog)

    private fun Player.script(id: Int) =
        written.filterIsInstance<RunClientScript>().filter { it.id == id }

    private fun component(name: String) = "component.ge_offers:$name".asRSCM(RSCMType.COMPONENT)

    private val switchPanelArgs
        get() =
            listOf(
                component("frame"),
                1,
                component("back"),
                component("index"),
                component("details"),
                component("setup"),
                component("tooltip"),
            )

    @Test
    fun `starting an offer writes the slot and then runs the panel switch itself`() {
        val p = player(30)
        setup.begin(p, 0, OfferType.SELL)

        val selected = p.written.indexOfFirst { it is VarpSmall && it.id == 375 && it.value == 1 shl 4 }
        val typeAndQuantity =
            p.written.indexOfFirst { it is VarpLarge && it.id == 563 && it.value == (1 shl 31) or 1 }
        val switch = p.written.indexOfFirst { it is RunClientScript && it.id == 804 }
        assertTrue(selected >= 0, "ge_selectedslot reaches the client")
        assertTrue(typeAndQuantity >= 0, "sell type and quantity reach the client")
        assertTrue(switch > selected && switch > typeAndQuantity, "the switch runs after the vars")
        assertEquals(switchPanelArgs, (p.written[switch] as RunClientScript).values)
    }

    @Test
    fun `choosing an item sends the price as a long and refreshes the panel`() {
        val p = player(31)
        setup.begin(p, 2, OfferType.BUY)
        val whipId = catalog.resolve(whip)!!.id
        setup.selectItem(p, whipId, 1, null)

        assertEquals(whipId, p.geSearchItem)
        assertEquals(3_000_000, p.geOfferPrice)
        val price = p.written.filterIsInstance<VarpLong>().last { it.id == 5753 }
        assertEquals(3_000_000L, price.value)
        assertTrue(p.written.indexOf(price) < p.written.indexOfLast { it is RunClientScript && it.id == 804 })
        assertEquals(3, p.geSelectedSlot)
        assertTrue(p.script(5730).isNotEmpty(), "the item description is set")
    }

    @Test
    fun `an empty slot is sent as an empty offer for that slot, never as a reset`() {
        val p = player(34)
        val session = GePlayer(p, catalog)
        session.pushSlot(3)
        val update = p.written.filterIsInstance<UpdateStockMarketSlotV2>().single()
        assertEquals(3, update.slot)
        assertTrue(update.update is UpdateStockMarketSlotV2.SetStockMarketSlot)
        assertEquals(0, (update.update as UpdateStockMarketSlotV2.SetStockMarketSlot).status)
    }

    @Test
    fun `viewing a slot and going back both refresh the panel`() {
        val p = player(32)
        setup.view(p, 4, whip, OfferType.BUY)
        assertEquals(5, p.geSelectedSlot)
        assertEquals(switchPanelArgs, p.script(804).last().values)
        setup.back(p)
        assertEquals(0, p.geSelectedSlot)
        assertEquals(2, p.script(804).size)
    }

    @Test
    fun `opening the window resets every var it reads, including the item sink slots`() {
        val windows = GeWindows(exchange, sessions, settings(enabled = true), setup)
        val p = player(33)
        p.geLastOfferItem = 0
        windows.prepare(p)

        assertEquals(-1, p.geSearchItem)
        assertEquals(-1, p.geLastOfferItem)
        assertEquals(1, p.geNewOfferQuantity)
        assertEquals(exchange.taxRatePermille, p.geTaxRate)
        for (slot in 0 until 8) {
            val varp = "varp.ge_itemsink_obj_$slot".asRSCM(RSCMType.VARP)
            assertEquals(-1, p.vars.backing[varp])
            assertTrue(p.written.any { (it is VarpSmall && it.id == varp && it.value == -1) })
        }
        for (slot in 0 until 8) {
            val sink = GeIds.itemSinkPriceVarps[slot].id
            assertTrue(p.written.filterIsInstance<VarpLong>().any { it.id == sink && it.value == 0L })
        }
        assertEquals(8, p.written.filterIsInstance<UpdateStockMarketSlotV2>().size)
        assertTrue(p.written.filterIsInstance<VarpLong>().any { it.id == 5753 && it.value == 0L })
    }

    @Test
    fun `ironmen are turned away and everyone else gets in`() {
        val windows = GeWindows(exchange, sessions, settings(enabled = true), GeSetup(GePrices(null, null, { null }), catalog))
        val normal = player(20)
        assertFalse(windows.refuses(normal))
        assertTrue(normal.written.filterIsInstance<MessageGame>().isEmpty())

        val ironman = player(21)
        ironman.gamemode = PlayerGamemode.IRONMAN
        assertTrue(windows.refuses(ironman))
        val message = ironman.written.filterIsInstance<MessageGame>().single()
        assertEquals("As an Ironman, you cannot use the Grand Exchange.", message.message)

        val closed = GeWindows(exchange, sessions, settings(enabled = false), GeSetup(GePrices(null, null, { null }), catalog))
        assertTrue(closed.refuses(player(22)))
        assertNotEquals(0, SlotCodec.WORDS)
    }

    private fun settings(enabled: Boolean) =
        GrandExchangeSettings(
            enabled = enabled,
            fillModel = "instant",
            liveFetch = false,
            refreshMinutes = 5,
            snapshotPath = java.nio.file.Paths.get("unused.json"),
            userAgent = "test",
            taxRate = 0.02,
            taxCap = 5_000_000,
        )

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
