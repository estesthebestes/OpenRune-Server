package org.rsmod.api.grandexchange.price

/** One item's latest instant-buy ([high]) and instant-sell ([low]) price. Either side may be unknown. */
public data class PricePoint(val high: Long?, val low: Long?)

public data class PriceSnapshot(
    val fetchedAtMillis: Long,
    val prices: Map<Int, PricePoint>,
    val buyLimits: Map<Int, Int> = emptyMap(),
    val limitsFetchedAtMillis: Long = 0L,
)

public enum class PriceOrigin {
    /** Taken from the snapshot that was fetched during this run. */
    LIVE,

    /** Taken from the snapshot restored from disk and not refreshed since. */
    SNAPSHOT,

    /** No price known for the item; the cache cost was used. */
    COST,
}

/**
 * What the simulated market offers for one item right now. [buyAt] is the price a buyer pays
 * instantly (the wiki `high`); [sellAt] is what a seller receives instantly (the wiki `low`).
 */
public data class MarketQuote(val buyAt: Long, val sellAt: Long, val origin: PriceOrigin) {
    /** Mid-point used wherever a single "guide price" is wanted. */
    val guide: Long
        get() = (buyAt + sellAt) / 2
}

public fun interface MarketQuoteSource {
    public fun quote(itemId: Int): MarketQuote
}
