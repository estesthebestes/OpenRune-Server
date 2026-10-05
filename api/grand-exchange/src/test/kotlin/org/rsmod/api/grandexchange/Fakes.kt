package org.rsmod.api.grandexchange

import org.rsmod.api.grandexchange.engine.CollectSink
import org.rsmod.api.grandexchange.engine.FillNotice
import org.rsmod.api.grandexchange.engine.GeParticipant
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.engine.HistoryEntry
import org.rsmod.api.grandexchange.fill.FillModel
import org.rsmod.api.grandexchange.fill.MarketFill
import org.rsmod.api.grandexchange.fill.MarketOffer
import org.rsmod.api.grandexchange.offer.CollectionBox
import org.rsmod.api.grandexchange.offer.InMemoryCollectionBox
import org.rsmod.api.grandexchange.offer.OfferSlot
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.price.MarketQuote
import org.rsmod.api.grandexchange.price.MarketQuoteSource
import org.rsmod.api.grandexchange.price.PriceOrigin
import org.rsmod.api.grandexchange.rules.GeItemCatalog
import org.rsmod.api.grandexchange.rules.GeItemInfo
import org.rsmod.api.grandexchange.rules.GeTax

const val WHIP = 4151
const val LOGS = 1511
const val BOND = 13190
const val UNTRADEABLE = 9999
const val COINS_PER_PLAT = 1000

class FakeCatalog : GeItemCatalog {
    private val items =
        mapOf(
            WHIP to GeItemInfo(WHIP, "Abyssal whip", 120_001),
            LOGS to GeItemInfo(LOGS, "Logs", 40),
            BOND to GeItemInfo(BOND, "Old school bond", 0, taxExempt = true),
        )

    override fun resolve(itemId: Int): GeItemInfo? = items[itemId]

    override fun notedId(itemId: Int): Int? = if (itemId in items) itemId + 1 else null

    override fun name(itemId: Int): String = items[itemId]?.name ?: "Item $itemId"
}

class FakeQuotes : MarketQuoteSource {
    private val quotes = HashMap<Int, MarketQuote>()

    fun set(itemId: Int, buyAt: Long, sellAt: Long) {
        quotes[itemId] = MarketQuote(buyAt, sellAt, PriceOrigin.LIVE)
    }

    override fun quote(itemId: Int): MarketQuote =
        quotes[itemId] ?: MarketQuote(1, 1, PriceOrigin.COST)
}

class RecordingFillModel(private val delegate: FillModel) : FillModel by delegate {
    data class Fill(val type: OfferType, val itemId: Int, val quantity: Int, val price: Long)

    val fills = ArrayList<Fill>()

    override fun marketFill(offer: MarketOffer, quote: MarketQuote): MarketFill? {
        val fill = delegate.marketFill(offer, quote)
        if (fill != null) {
            fills += Fill(offer.type, offer.itemId, fill.quantity, fill.price)
        }
        return fill
    }
}

/** A fill model that never trades with the market, leaving only player-to-player trades. */
object NoMarketFillModel : FillModel {
    override val name: String = "none"

    override fun marketFill(offer: MarketOffer, quote: MarketQuote): MarketFill? = null

    override fun shouldSweep(pricesChanged: Boolean, tick: Long): Boolean = false
}

class FakePlayer(override val id: Long, override val slotCount: Int = 8) : GeParticipant {
    val slots = Array(GrandExchange.MAX_SLOTS) { OfferSlot.EMPTY }
    val boxes = Array(GrandExchange.MAX_SLOTS) { InMemoryCollectionBox() }
    var coins: Long = 0
    val items = HashMap<Int, Int>()
    val notices = ArrayList<FillNotice>()
    val history = ArrayList<HistoryEntry>()
    var changed = 0

    fun give(itemId: Int, count: Int) {
        items.merge(itemId, count, Int::plus)
    }

    fun count(itemId: Int): Int = items[itemId] ?: 0

    override fun slot(index: Int): OfferSlot = slots[index]

    override fun setSlot(index: Int, slot: OfferSlot) {
        slots[index] = slot
    }

    override fun box(index: Int): CollectionBox = boxes[index]

    override fun takeCoins(amount: Long): Boolean {
        if (amount > coins) {
            return false
        }
        coins -= amount
        return true
    }

    override fun takeItems(itemId: Int, count: Int): Boolean {
        if (count(itemId) < count) {
            return false
        }
        items[itemId] = count(itemId) - count
        return true
    }

    override fun onSlotChanged(index: Int) {
        changed++
    }

    override fun onFill(notice: FillNotice) {
        notices += notice
    }

    override fun recordHistory(entry: HistoryEntry) {
        history += entry
    }

    /** A sink that puts everything into this player's wallet, like an inventory with free space. */
    val wallet = object : CollectSink {
        override fun acceptItems(itemId: Int, count: Int, noted: Boolean): Int {
            give(itemId, count)
            return count
        }

        override fun acceptCoins(amount: Long): Long {
            coins += amount
            return amount
        }
    }

    fun escrowedCoins(): Long =
        slots.filter { it.isOpen && it.type == OfferType.BUY }.sumOf { it.remaining.toLong() * it.price }

    fun escrowedItems(itemId: Int): Long =
        slots
            .filter { it.isOpen && it.type == OfferType.SELL && it.itemId == itemId }
            .sumOf { it.remaining.toLong() }

    fun boxCoins(): Long = boxes.sumOf { it.coins }

    fun boxItems(itemId: Int): Long =
        boxes.filter { it.itemId == itemId }.sumOf { it.itemCount.toLong() }

    fun totalCoins(): Long = coins + boxCoins() + escrowedCoins()

    fun totalItems(itemId: Int): Long = count(itemId) + boxItems(itemId) + escrowedItems(itemId)
}

fun newExchange(
    quotes: FakeQuotes = FakeQuotes(),
    fillModel: FillModel = org.rsmod.api.grandexchange.fill.InstantFillModel,
    tax: GeTax = GeTax(20, 5_000_000),
) = GrandExchange(FakeCatalog(), quotes, tax, fillModel = fillModel)

inline fun <reified T> assertIs(value: Any?): T {
    org.junit.jupiter.api.Assertions.assertTrue(value is T, "Expected ${T::class.simpleName} but was $value")
    return value as T
}
