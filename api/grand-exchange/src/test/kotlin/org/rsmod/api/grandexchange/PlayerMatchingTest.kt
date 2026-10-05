package org.rsmod.api.grandexchange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.grandexchange.engine.AbortResult
import org.rsmod.api.grandexchange.engine.CollectPart
import org.rsmod.api.grandexchange.engine.CollectSink
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType

class PlayerMatchingTest {
    private val quotes = FakeQuotes().apply { set(WHIP, buyAt = 1000, sellAt = 900) }
    private val exchange = newExchange(quotes)

    private fun player(id: Long, coins: Long = 0, whips: Int = 0) =
        FakePlayer(id).also {
            it.coins = coins
            it.give(WHIP, whips)
            exchange.join(it)
        }

    @Test
    fun `a buy meets a resting sell at the resting price and is refunded`() {
        val seller = player(1, whips = 10)
        val buyer = player(2, coins = 100_000)
        exchange.place(seller, 0, OfferType.SELL, WHIP, 10, 1500)
        assertEquals(OfferState.OPEN, seller.slots[0].state)

        exchange.place(buyer, 0, OfferType.BUY, WHIP, 4, 1600)

        assertEquals(OfferState.COMPLETED, buyer.slots[0].state)
        assertEquals(4, buyer.boxes[0].itemCount)
        assertEquals(4 * 100L, buyer.boxes[0].coins)
        assertEquals(4 * 1500L, buyer.slots[0].completedGold)

        assertEquals(OfferState.OPEN, seller.slots[0].state)
        assertEquals(4, seller.slots[0].completedQuantity)
        assertEquals(6, seller.slots[0].remaining)
        assertEquals(4 * (1500 - 30L), seller.boxes[0].coins)
        assertEquals(4 * 30L, seller.slots[0].tax)
    }

    @Test
    fun `a sell meets a resting buy at the resting price and gets more than it asked`() {
        val buyer = player(2, coins = 100_000)
        val seller = player(1, whips = 3)
        exchange.place(buyer, 0, OfferType.BUY, WHIP, 3, 800)
        assertEquals(OfferState.OPEN, buyer.slots[0].state)

        exchange.place(seller, 0, OfferType.SELL, WHIP, 3, 700)

        assertEquals(OfferState.COMPLETED, buyer.slots[0].state)
        assertEquals(0L, buyer.boxes[0].coins)
        assertEquals(3, buyer.boxes[0].itemCount)
        assertEquals(OfferState.COMPLETED, seller.slots[0].state)
        assertEquals(3 * 800L, seller.slots[0].completedGold)
        assertEquals(3 * (800 - 16L), seller.boxes[0].coins)
    }

    @Test
    fun `the remainder after players goes to the market`() {
        val seller = player(1, whips = 10)
        val buyer = player(2, coins = 100_000)
        exchange.place(seller, 0, OfferType.SELL, WHIP, 10, 1500)

        exchange.place(buyer, 0, OfferType.BUY, WHIP, 12, 1600)

        assertEquals(OfferState.COMPLETED, seller.slots[0].state)
        assertEquals(OfferState.COMPLETED, buyer.slots[0].state)
        assertEquals(12, buyer.boxes[0].itemCount)
        assertEquals(10 * 1500L + 2 * 1000L, buyer.slots[0].completedGold)
        assertEquals(10 * 100L + 2 * 600L, buyer.boxes[0].coins)
    }

    @Test
    fun `offers that do not cross do not trade`() {
        val seller = player(1, whips = 1)
        val buyer = player(2, coins = 100_000)
        exchange.place(seller, 0, OfferType.SELL, WHIP, 1, 1500)
        exchange.place(buyer, 0, OfferType.BUY, WHIP, 1, 950)
        assertEquals(OfferState.OPEN, seller.slots[0].state)
        assertEquals(OfferState.OPEN, buyer.slots[0].state)
        assertEquals(2, exchange.openOfferCount)
    }

    @Test
    fun `better prices trade first and equal prices trade oldest first`() {
        val oldest = player(1, whips = 1)
        val newer = player(2, whips = 1)
        val cheapest = player(3, whips = 1)
        val buyer = player(4, coins = 100_000)
        exchange.place(oldest, 0, OfferType.SELL, WHIP, 1, 1500)
        exchange.place(newer, 0, OfferType.SELL, WHIP, 1, 1500)
        exchange.place(cheapest, 0, OfferType.SELL, WHIP, 1, 1400)

        exchange.place(buyer, 0, OfferType.BUY, WHIP, 1, 1600)
        assertEquals(OfferState.COMPLETED, cheapest.slots[0].state)
        assertEquals(OfferState.OPEN, oldest.slots[0].state)

        exchange.place(buyer, 1, OfferType.BUY, WHIP, 1, 1600)
        assertEquals(OfferState.COMPLETED, oldest.slots[0].state)
        assertEquals(OfferState.OPEN, newer.slots[0].state)
    }

    @Test
    fun `buyers at the best price are served first`() {
        val low = player(1, coins = 100_000)
        val high = player(2, coins = 100_000)
        val seller = player(3, whips = 1)
        exchange.place(low, 0, OfferType.BUY, WHIP, 1, 700)
        exchange.place(high, 0, OfferType.BUY, WHIP, 1, 800)

        exchange.place(seller, 0, OfferType.SELL, WHIP, 1, 600)

        assertEquals(OfferState.COMPLETED, high.slots[0].state)
        assertEquals(OfferState.OPEN, low.slots[0].state)
    }

    @Test
    fun `a player never trades with their own offers`() {
        val noMarket = newExchange(quotes, fillModel = NoMarketFillModel)
        val p = FakePlayer(1).also {
            it.coins = 100_000
            it.give(WHIP, 1)
            noMarket.join(it)
        }
        noMarket.place(p, 0, OfferType.SELL, WHIP, 1, 1500)
        noMarket.place(p, 1, OfferType.BUY, WHIP, 1, 1600)
        assertEquals(OfferState.OPEN, p.slots[0].state)
        assertEquals(OfferState.OPEN, p.slots[1].state)
    }

    @Test
    fun `offers rejoin the book on login and offline players never match`() {
        val seller = player(1, whips = 1)
        exchange.place(seller, 0, OfferType.SELL, WHIP, 1, 1500)
        exchange.leave(seller)
        assertEquals(0, exchange.openOfferCount)
        assertEquals(OfferState.OPEN, seller.slots[0].state)

        val noMarket = newExchange(quotes, fillModel = NoMarketFillModel)
        val s2 = FakePlayer(5).also {
            it.give(WHIP, 1)
            noMarket.join(it)
        }
        val b2 = FakePlayer(6).also {
            it.coins = 10_000
            noMarket.join(it)
        }
        noMarket.place(s2, 0, OfferType.SELL, WHIP, 1, 1500)
        noMarket.leave(s2)
        noMarket.place(b2, 0, OfferType.BUY, WHIP, 1, 1600)
        assertEquals(OfferState.OPEN, b2.slots[0].state)

        noMarket.join(s2)
        assertEquals(OfferState.COMPLETED, s2.slots[0].state)
        assertEquals(OfferState.COMPLETED, b2.slots[0].state)
        assertEquals(1600L, b2.slots[0].completedGold)
        assertEquals(0L, b2.boxes[0].coins)
        assertEquals(1600L, s2.slots[0].completedGold)
    }

    @Test
    fun `an offer that was resting before a price change fills on login`() {
        val buyer = player(1, coins = 10_000)
        exchange.place(buyer, 0, OfferType.BUY, WHIP, 2, 800)
        exchange.leave(buyer)
        quotes.set(WHIP, buyAt = 750, sellAt = 700)
        exchange.join(buyer)
        assertEquals(OfferState.COMPLETED, buyer.slots[0].state)
        assertEquals(2 * 50L, buyer.boxes[0].coins)
    }

    @Test
    fun `aborting returns what is still held and keeps what was filled`() {
        val seller = player(1, whips = 10)
        val buyer = player(2, coins = 100_000)
        exchange.place(buyer, 0, OfferType.BUY, WHIP, 10, 800)
        exchange.place(seller, 0, OfferType.SELL, WHIP, 4, 700)

        assertEquals(AbortResult.ABORTED, exchange.abort(buyer, 0))

        assertEquals(OfferState.ABORTED, buyer.slots[0].state)
        assertEquals(4, buyer.boxes[0].itemCount)
        assertEquals(6 * 800L, buyer.boxes[0].coins)
        assertEquals(1, buyer.history.size)
        assertEquals(4, buyer.history.single().quantity)
        assertEquals(0, exchange.openOfferCount)

        assertEquals(AbortResult.NOT_OPEN, exchange.abort(buyer, 0))
        assertEquals(AbortResult.NOT_OPEN, exchange.abort(buyer, 1))
    }

    @Test
    fun `aborting a sell returns the unsold items and an untouched offer leaves no history`() {
        val seller = player(1, whips = 5)
        exchange.place(seller, 0, OfferType.SELL, WHIP, 5, 2000)
        exchange.abort(seller, 0)
        assertEquals(5, seller.boxes[0].itemCount)
        assertEquals(0, seller.history.size)
        exchange.collect(seller, 0, CollectPart.ALL, noted = false, sink = seller.wallet)
        assertEquals(5, seller.count(WHIP))
        assertTrue(seller.slots[0].isEmpty)
    }

    @Test
    fun `an aborted offer can no longer be matched`() {
        val seller = player(1, whips = 1)
        val buyer = player(2, coins = 10_000)
        exchange.place(seller, 0, OfferType.SELL, WHIP, 1, 950)
        exchange.abort(seller, 0)
        exchange.place(buyer, 0, OfferType.BUY, WHIP, 1, 960)
        assertEquals(OfferState.OPEN, buyer.slots[0].state)
        assertEquals(9_040L, buyer.coins)
    }

    @Test
    fun `collecting only frees the slot once the box is empty`() {
        val p = player(1, coins = 10_000)
        exchange.place(p, 0, OfferType.BUY, WHIP, 3, 1200)
        assertEquals(3, p.boxes[0].itemCount)
        assertEquals(600L, p.boxes[0].coins)

        val itemsOnly = object : CollectSink {
            override fun acceptItems(itemId: Int, count: Int, noted: Boolean): Int = 2

            override fun acceptCoins(amount: Long): Long = 0
        }
        exchange.collect(p, 0, CollectPart.ALL, noted = false, sink = itemsOnly)
        assertEquals(1, p.boxes[0].itemCount)
        assertEquals(600L, p.boxes[0].coins)
        assertEquals(OfferState.COMPLETED, p.slots[0].state)

        exchange.collect(p, 0, CollectPart.COINS, noted = false, sink = p.wallet)
        assertEquals(OfferState.COMPLETED, p.slots[0].state)
        exchange.collect(p, 0, CollectPart.ITEMS, noted = false, sink = p.wallet)
        assertTrue(p.slots[0].isEmpty)
        assertEquals(10_000 - 3 * 1000L, p.coins)
        assertEquals(1, p.count(WHIP))
    }
}
