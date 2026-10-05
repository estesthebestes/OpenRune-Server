package org.rsmod.api.grandexchange.price

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads the OSRS Wiki real-time prices API. The wiki asks for a descriptive `User-Agent` and for
 * clients not to hammer it, so callers are expected to poll slowly from a background thread.
 */
public class WikiPriceFeed(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : PriceFeed {
    override fun fetchLatest(): Map<Int, PricePoint> =
        WikiPriceParser.parseLatest(get("$baseUrl/latest"))

    override fun fetchBuyLimits(): Map<Int, Int> =
        WikiPriceParser.parseBuyLimits(get("$baseUrl/mapping"))

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Price feed answered ${response.code} for $url")
            }
            return response.body.string()
        }
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://prices.runescape.wiki/api/v1/osrs"
    }
}
