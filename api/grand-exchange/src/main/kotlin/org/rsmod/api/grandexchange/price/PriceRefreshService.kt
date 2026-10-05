package org.rsmod.api.grandexchange.price

import com.github.michaelbull.logging.InlineLogger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.rsmod.server.services.Service

/**
 * Loads the persisted snapshot at boot and then refreshes prices on a daemon thread, never on the
 * game thread. With live fetching disabled only the snapshot is used.
 */
public class PriceRefreshService(
    private val prices: GePrices,
    private val live: Boolean,
    private val refreshMinutes: Long,
) : Service {
    private val logger = InlineLogger()
    private var executor: ScheduledExecutorService? = null

    override suspend fun startup() {
        val restored = prices.restore()
        logger.info {
            if (restored) {
                "Grand Exchange restored ${prices.current?.prices?.size ?: 0} prices from disk."
            } else {
                "Grand Exchange has no stored price snapshot yet."
            }
        }
        if (!live) {
            logger.info { "Grand Exchange live price fetching is disabled." }
            return
        }
        val service =
            Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "ge-price-refresh").apply { isDaemon = true }
            }
        executor = service
        val period = refreshMinutes.coerceAtLeast(1)
        service.scheduleWithFixedDelay(::refreshQuietly, 0, period, TimeUnit.MINUTES)
    }

    private fun refreshQuietly() {
        try {
            if (prices.refresh()) {
                logger.debug { "Grand Exchange prices refreshed (${prices.current?.prices?.size})." }
            } else {
                logger.warn { "Grand Exchange price refresh failed; keeping the previous prices." }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Grand Exchange price refresh crashed; keeping the previous prices." }
        }
    }

    override suspend fun shutdown() {
        executor?.shutdownNow()
        executor = null
    }
}
