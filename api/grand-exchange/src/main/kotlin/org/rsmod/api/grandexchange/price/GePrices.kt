package org.rsmod.api.grandexchange.price

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Holds the market prices the exchange trades at.
 *
 * Resolution order for an item is the snapshot fetched live during this run, then the snapshot
 * restored from disk, then the cache cost. [refresh] is meant for a background thread; everything
 * else is safe to call from the game thread because readers only touch an immutable snapshot.
 */
public class GePrices(
    private val feed: PriceFeed?,
    private val store: SnapshotStore?,
    private val costOf: (Int) -> Int?,
    private val clock: () -> Long = System::currentTimeMillis,
) : MarketQuoteSource {
    @Volatile private var snapshot: PriceSnapshot? = null

    @Volatile private var fetchedThisRun: Boolean = false
    private val versionCounter = AtomicLong()

    /** Bumped every time new prices are installed; lets the game thread notice a change cheaply. */
    public val version: Long
        get() = versionCounter.get()

    public val current: PriceSnapshot?
        get() = snapshot

    /** Restores the persisted snapshot. Returns whether one was found. */
    public fun restore(): Boolean {
        val restored = store?.load() ?: return false
        install(restored, live = false)
        return true
    }

    /**
     * Fetches fresh prices, persists them and installs them. A failure leaves the previous prices
     * in place. Buy limits are re-read at most once per [limitsMaxAgeMillis].
     */
    public fun refresh(limitsMaxAgeMillis: Long = DAY_MILLIS): Boolean {
        val source = feed ?: return false
        val now = clock()
        val previous = snapshot
        val prices =
            try {
                source.fetchLatest()
            } catch (_: IOException) {
                return false
            } catch (_: RuntimeException) {
                return false
            }
        if (prices.isEmpty()) {
            return false
        }
        var limits = previous?.buyLimits ?: emptyMap()
        var limitsAt = previous?.limitsFetchedAtMillis ?: 0L
        if (limits.isEmpty() || now - limitsAt >= limitsMaxAgeMillis) {
            try {
                limits = source.fetchBuyLimits().ifEmpty { limits }
                limitsAt = now
            } catch (_: IOException) {
                // Keep the previous limits; they change rarely.
            } catch (_: RuntimeException) {
                // Same as above.
            }
        }
        val fresh = PriceSnapshot(now, prices, limits, limitsAt)
        install(fresh, live = true)
        try {
            store?.save(fresh)
        } catch (_: IOException) {
            // The in-memory prices are still good; the next refresh retries the write.
        }
        return true
    }

    private fun install(next: PriceSnapshot, live: Boolean) {
        snapshot = next
        if (live) {
            fetchedThisRun = true
        }
        versionCounter.incrementAndGet()
    }

    public fun buyLimit(itemId: Int): Int? = snapshot?.buyLimits?.get(itemId)

    override fun quote(itemId: Int): MarketQuote {
        val point = snapshot?.prices?.get(itemId)
        val high = point?.high
        val low = point?.low
        if (high != null || low != null) {
            val buy = (high ?: low!!).coerceIn(1L, Int.MAX_VALUE.toLong())
            val sell = (low ?: high!!).coerceIn(1L, Int.MAX_VALUE.toLong())
            val origin = if (fetchedThisRun) PriceOrigin.LIVE else PriceOrigin.SNAPSHOT
            return MarketQuote(buy, sell, origin)
        }
        val cost = (costOf(itemId) ?: 1).coerceAtLeast(1).toLong()
        return MarketQuote(cost, cost, PriceOrigin.COST)
    }

    /** The guide price for display purposes, or `null` when only the cache cost is known. */
    public fun guideOrNull(itemId: Int): Int? {
        val quote = quote(itemId)
        return if (quote.origin == PriceOrigin.COST) null else quote.guide.toInt()
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
