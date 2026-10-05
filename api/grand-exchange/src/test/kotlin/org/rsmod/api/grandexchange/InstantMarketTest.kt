package org.rsmod.api.grandexchange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.grandexchange.engine.PlaceFailure
import org.rsmod.api.grandexchange.engine.PlaceResult
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType

class InstantMarketTest {
    private val quotes = FakeQuotes().apply { set(WHIP, buyAt = 1000, sellAt = 900) }
    private val exchange = newExchange(quotes)

    private fun buyer(coins: Long = 1_000_000) =
        FakePlayer(1).also {
            it.coins = coins
            exchange.join(it)
        }

    private fun seller(items: Int = 10) =
        FakePlayer(2).also {
            it.give(WHIP, items)
            exchange.join(it)
        }

    @Test
    fun `a buy offer at the market price fills at once`() {
        val p = buyer()
        val result = exchange.place(p, 0, OfferType.BUY, WHIP, 5, 1000)
        val placed = assertIs<PlaceResult.Placed>(result)
        assertEquals(5, placed.filledQuantity)
        assertEquals(OfferState.COMPLETED, p.slots[0].state)
        assertEquals(5, p.boxes[0].itemCount)
        assertEquals(WHIP, p.boxes[0].itemId)
        assertEquals(0, p.boxes[0].coins)
        assertEquals(1_000_000 - 5000, p.coins)
        assertEquals(5000L, p.slots[0].completedGold)
    }

    @Test
    fun `a buy offer over the market price fills at the market price and refunds the difference`() {
        val p = buyer()
        exchange.place(p, 0, OfferType.BUY, WHIP, 5, 1200)
        assertEquals(OfferState.COMPLETED, p.slots[0].state)
        assertEquals(5, p.boxes[0].itemCount)
        assertEquals(5 * 200L, p.boxes[0].coins)
        assertEquals(5000L, p.slots[0].completedGold)
        assertEquals(1_000_000 - 6000, p.coins)
    }

    @Test
    fun `a buy offer under the market price stays open with its coins held`() {
        val p = buyer()
        val placed = assertIs<PlaceResult.Placed>(exchange.place(p, 0, OfferType.BUY, WHIP, 5, 999))
        assertEquals(0, placed.filledQuantity)
        assertEquals(OfferState.OPEN, p.slots[0].state)
        assertEquals(1_000_000 - 5 * 999L, p.coins)
        assertTrue(p.boxes[0].isEmpty())
        assertEquals(1, exchange.openOfferCount)
    }

    @Test
    fun `a sell offer at the market price fills at once minus the fee`() {
        val p = seller()
        exchange.place(p, 0, OfferType.SELL, WHIP, 4, 900)
        assertEquals(OfferState.COMPLETED, p.slots[0].state)
        assertEquals(6, p.count(WHIP))
        assertEquals(4 * (900 - 18L), p.boxes[0].coins)
        assertEquals(4 * 900L, p.slots[0].completedGold)
        assertEquals(4 * 18L, p.slots[0].tax)
    }

    @Test
    fun `a sell offer under the market price receives the market price`() {
        val p = seller()
        exchange.place(p, 0, OfferType.SELL, WHIP, 1, 100)
        assertEquals(900L, p.slots[0].completedGold)
        assertEquals(900L - 18, p.boxes[0].coins)
    }

    @Test
    fun `a sell offer above the market price stays open with its items held`() {
        val p = seller()
        exchange.place(p, 0, OfferType.SELL, WHIP, 4, 901)
        assertEquals(OfferState.OPEN, p.slots[0].state)
        assertEquals(6, p.count(WHIP))
        assertEquals(4, p.slots[0].remaining)
    }

    @Test
    fun `the fee is capped per item`() {
        quotes.set(WHIP, 1_500_000_000, 1_000_000_000)
        val p = seller()
        exchange.place(p, 0, OfferType.SELL, WHIP, 2, 1_000_000_000)
        assertEquals(2 * 5_000_000L, p.slots[0].tax)
        assertEquals(2 * (1_000_000_000L - 5_000_000), p.boxes[0].coins)
    }

    @Test
    fun `exempt items pay no fee`() {
        quotes.set(BOND, 10_000_000, 9_000_000)
        val p = FakePlayer(3)
        p.give(BOND, 1)
        exchange.join(p)
        exchange.place(p, 0, OfferType.SELL, BOND, 1, 9_000_000)
        assertEquals(0L, p.slots[0].tax)
        assertEquals(9_000_000L, p.boxes[0].coins)
    }

    @Test
    fun `items with no price use the cost fallback quote`() {
        val p = buyer()
        exchange.place(p, 0, OfferType.BUY, LOGS, 10, 5)
        assertEquals(OfferState.COMPLETED, p.slots[0].state)
        assertEquals(10 * 4L, p.boxes[0].coins)
    }

    @Test
    fun `a rejected offer changes nothing`() {
        val p = buyer(coins = 100)
        val broke = exchange.place(p, 0, OfferType.BUY, WHIP, 1, 1000)
        assertEquals(PlaceResult.Rejected(PlaceFailure.NOT_ENOUGH_COINS), broke)
        assertEquals(100L, p.coins)
        assertTrue(p.slots[0].isEmpty)

        val noItems = exchange.place(p, 0, OfferType.SELL, WHIP, 1, 900)
        assertEquals(PlaceResult.Rejected(PlaceFailure.NOT_ENOUGH_ITEMS), noItems)
        assertEquals(
            PlaceResult.Rejected(PlaceFailure.NOT_TRADEABLE),
            exchange.place(p, 0, OfferType.BUY, UNTRADEABLE, 1, 1),
        )
        assertEquals(
            PlaceResult.Rejected(PlaceFailure.INVALID_OFFER),
            exchange.place(p, 0, OfferType.BUY, WHIP, 0, 1),
        )
        assertEquals(
            PlaceResult.Rejected(PlaceFailure.TOO_EXPENSIVE),
            exchange.place(p, 0, OfferType.BUY, WHIP, Int.MAX_VALUE, Int.MAX_VALUE.toLong()),
        )
        assertEquals(0, exchange.openOfferCount)
    }

    @Test
    fun `free players only have three slots and a slot is busy until collected`() {
        val p = FakePlayer(9, slotCount = 3).also {
            it.coins = 10_000
            exchange.join(it)
        }
        assertEquals(
            PlaceResult.Rejected(PlaceFailure.INVALID_SLOT),
            exchange.place(p, 3, OfferType.BUY, WHIP, 1, 10),
        )
        exchange.place(p, 0, OfferType.BUY, WHIP, 1, 1000)
        assertEquals(
            PlaceResult.Rejected(PlaceFailure.SLOT_IN_USE),
            exchange.place(p, 0, OfferType.BUY, WHIP, 1, 1000),
        )
        exchange.collect(p, 0, org.rsmod.api.grandexchange.engine.CollectPart.ALL, false, p.wallet)
        assertTrue(p.slots[0].isEmpty)
        assertIs<PlaceResult.Placed>(exchange.place(p, 0, OfferType.BUY, WHIP, 1, 1000))
    }

    @Test
    fun `a resting offer fills when the market moves to it`() {
        val p = buyer()
        exchange.place(p, 0, OfferType.BUY, WHIP, 3, 800)
        assertEquals(OfferState.OPEN, p.slots[0].state)
        quotes.set(WHIP, buyAt = 700, sellAt = 600)
        exchange.sweep(tick = 1, pricesChanged = true)
        assertEquals(OfferState.COMPLETED, p.slots[0].state)
        assertEquals(3 * 700L, p.slots[0].completedGold)
        assertEquals(3 * 100L, p.boxes[0].coins)
    }

    @Test
    fun `a sweep with unchanged prices leaves resting offers alone`() {
        val p = buyer()
        exchange.place(p, 0, OfferType.BUY, WHIP, 3, 800)
        quotes.set(WHIP, buyAt = 700, sellAt = 600)
        exchange.sweep(tick = 1, pricesChanged = false)
        assertEquals(OfferState.OPEN, p.slots[0].state)
    }

    @Test
    fun `completion is reported and recorded in the history`() {
        val p = buyer()
        exchange.place(p, 0, OfferType.BUY, WHIP, 2, 1000)
        val notice = p.notices.single()
        assertTrue(notice.finished)
        assertEquals(2, notice.filled)
        assertEquals(1, p.history.size)
        assertEquals(2, p.history.single().quantity)
        assertEquals(2000L, p.history.single().gold)
    }
}
