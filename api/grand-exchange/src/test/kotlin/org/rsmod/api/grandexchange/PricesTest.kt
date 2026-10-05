package org.rsmod.api.grandexchange

import java.io.IOException
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.price.PriceFeed
import org.rsmod.api.grandexchange.price.PriceOrigin
import org.rsmod.api.grandexchange.price.PricePoint
import org.rsmod.api.grandexchange.price.PriceSnapshot
import org.rsmod.api.grandexchange.price.SnapshotStore
import org.rsmod.api.grandexchange.price.WikiPriceParser

class PricesTest {
    private val latestJson =
        """
        {"data": {
          "2": {"high": 293, "highTime": 1791172122, "low": 284, "lowTime": 1791172125},
          "6": {"high": 193632, "highTime": 1791171573, "low": null, "lowTime": null},
          "8": {"high": null, "highTime": null, "low": 189200, "lowTime": 1791171800},
          "10": {"high": null, "highTime": null, "low": null, "lowTime": null},
          "x": {"high": 5, "low": 4}
        }}
        """

    private val mappingJson =
        """
        [
          {"examine": "A weapon from the Abyss.", "id": 4151, "members": true, "lowalch": 48000,
           "limit": 70, "value": 120001, "highalch": 72000, "icon": "Abyssal whip.png",
           "name": "Abyssal whip"},
          {"id": 1511, "name": "Logs", "limit": 25000, "value": 4},
          {"id": 77, "name": "No limit item", "value": 1}
        ]
        """

    private class FakeFeed(
        var latest: Map<Int, PricePoint> = emptyMap(),
        var limits: Map<Int, Int> = emptyMap(),
        var failing: Boolean = false,
    ) : PriceFeed {
        var latestCalls = 0
        var limitCalls = 0

        override fun fetchLatest(): Map<Int, PricePoint> {
            latestCalls++
            if (failing) throw IOException("offline")
            return latest
        }

        override fun fetchBuyLimits(): Map<Int, Int> {
            limitCalls++
            if (failing) throw IOException("offline")
            return limits
        }
    }

    @Test
    fun `latest prices parse with missing sides and junk keys`() {
        val parsed = WikiPriceParser.parseLatest(latestJson)
        assertEquals(PricePoint(293, 284), parsed[2])
        assertEquals(PricePoint(193632, null), parsed[6])
        assertEquals(PricePoint(null, 189200), parsed[8])
        assertNull(parsed[10])
        assertEquals(3, parsed.size)
    }

    @Test
    fun `mapping parses buy limits`() {
        val limits = WikiPriceParser.parseBuyLimits(mappingJson)
        assertEquals(mapOf(4151 to 70, 1511 to 25000), limits)
    }

    @Test
    fun `both sides known gives a buy at high and a sell at low`() {
        val prices = GePrices(FakeFeed(mapOf(2 to PricePoint(293, 284))), null, { null })
        assertTrue(prices.refresh())
        val quote = prices.quote(2)
        assertEquals(293, quote.buyAt)
        assertEquals(284, quote.sellAt)
        assertEquals(PriceOrigin.LIVE, quote.origin)
        assertEquals(288, quote.guide)
    }

    @Test
    fun `a missing side uses the other one and a missing item uses the cache cost`() {
        val feed = FakeFeed(mapOf(6 to PricePoint(500, null), 8 to PricePoint(null, 300)))
        val prices = GePrices(feed, null, { id -> if (id == 99) 42 else null })
        prices.refresh()
        assertEquals(500, prices.quote(6).sellAt)
        assertEquals(500, prices.quote(6).buyAt)
        assertEquals(300, prices.quote(8).buyAt)
        val cost = prices.quote(99)
        assertEquals(PriceOrigin.COST, cost.origin)
        assertEquals(42, cost.buyAt)
        assertEquals(42, cost.sellAt)
        assertEquals(1, prices.quote(12345).buyAt)
        assertNull(prices.guideOrNull(99))
        assertNotNull(prices.guideOrNull(6))
    }

    @Test
    fun `before anything is loaded every item falls back to its cost`() {
        val prices = GePrices(FakeFeed(), null, { 7 })
        assertEquals(PriceOrigin.COST, prices.quote(1).origin)
        assertEquals(7, prices.quote(1).buyAt)
    }

    @Test
    fun `a failed refresh keeps the previous prices and does not bump the version`() {
        val feed = FakeFeed(mapOf(2 to PricePoint(100, 90)))
        val prices = GePrices(feed, null, { null })
        prices.refresh()
        val version = prices.version
        feed.failing = true
        assertFalse(prices.refresh())
        assertEquals(version, prices.version)
        assertEquals(100, prices.quote(2).buyAt)
    }

    @Test
    fun `an empty answer is treated as a failure`() {
        val prices = GePrices(FakeFeed(emptyMap()), null, { null })
        assertFalse(prices.refresh())
    }

    @Test
    fun `restoring from disk serves snapshot prices until a live fetch lands`() {
        val dir = Files.createTempDirectory("ge-prices")
        val store = SnapshotStore(dir.resolve("nested").resolve("prices.json"))
        val feed = FakeFeed(mapOf(2 to PricePoint(100, 90)), mapOf(2 to 10))
        val first = GePrices(feed, store, { null }, clock = { 1_000 })
        first.refresh()

        val offline = GePrices(FakeFeed(failing = true), store, { null })
        assertTrue(offline.restore())
        val restored = offline.quote(2)
        assertEquals(PriceOrigin.SNAPSHOT, restored.origin)
        assertEquals(100, restored.buyAt)
        assertEquals(10, offline.buyLimit(2))
        assertFalse(offline.refresh())
        assertEquals(PriceOrigin.SNAPSHOT, offline.quote(2).origin)

        val back = GePrices(FeedReturning(mapOf(2 to PricePoint(120, 110))), store, { null })
        back.restore()
        back.refresh()
        assertEquals(PriceOrigin.LIVE, back.quote(2).origin)
        assertEquals(120, back.quote(2).buyAt)
    }

    private class FeedReturning(private val latest: Map<Int, PricePoint>) : PriceFeed {
        override fun fetchLatest(): Map<Int, PricePoint> = latest

        override fun fetchBuyLimits(): Map<Int, Int> = emptyMap()
    }

    @Test
    fun `without a feed or a snapshot nothing breaks`() {
        val dir = Files.createTempDirectory("ge-prices-empty")
        val prices = GePrices(null, SnapshotStore(dir.resolve("missing.json")), { 5 })
        assertFalse(prices.restore())
        assertFalse(prices.refresh())
        assertEquals(5, prices.quote(1).buyAt)
    }

    @Test
    fun `buy limits are only re-read when stale`() {
        val feed = FakeFeed(mapOf(2 to PricePoint(1, 1)), mapOf(2 to 10))
        var now = 0L
        val prices = GePrices(feed, null, { null }, clock = { now })
        prices.refresh()
        now += 60_000
        prices.refresh()
        assertEquals(1, feed.limitCalls)
        now += 25L * 60 * 60 * 1000
        prices.refresh()
        assertEquals(2, feed.limitCalls)
        assertEquals(3, feed.latestCalls)
    }

    @Test
    fun `snapshots round trip and a corrupt file is ignored`() {
        val dir = Files.createTempDirectory("ge-snapshot")
        val file = dir.resolve("prices.json")
        val store = SnapshotStore(file)
        val snapshot =
            PriceSnapshot(
                fetchedAtMillis = 5,
                prices = mapOf(1 to PricePoint(10, 8), 2 to PricePoint(null, 3), 3 to PricePoint(9, null)),
                buyLimits = mapOf(1 to 100),
                limitsFetchedAtMillis = 4,
            )
        store.save(snapshot)
        store.save(snapshot)
        assertEquals(snapshot, store.load())

        Files.writeString(file, "{ this is not json")
        assertNull(store.load())
    }
}
