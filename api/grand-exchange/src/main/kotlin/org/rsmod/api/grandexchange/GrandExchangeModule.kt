package org.rsmod.api.grandexchange

import com.github.michaelbull.logging.InlineLogger
import com.google.inject.Provider
import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import okhttp3.OkHttpClient
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.fill.FillModel
import org.rsmod.api.grandexchange.fill.FillModels
import org.rsmod.api.grandexchange.fill.InstantFillModel
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.price.MarketQuoteSource
import org.rsmod.api.grandexchange.price.PriceFeed
import org.rsmod.api.grandexchange.price.PriceRefreshService
import org.rsmod.api.grandexchange.price.SnapshotStore
import org.rsmod.api.grandexchange.price.WikiPriceFeed
import org.rsmod.api.grandexchange.rules.BuyLimits
import org.rsmod.api.grandexchange.rules.CacheItemCatalog
import org.rsmod.api.grandexchange.rules.GeItemCatalog
import org.rsmod.api.grandexchange.rules.GeTax
import org.rsmod.api.grandexchange.rules.NoBuyLimits
import org.rsmod.api.market.MarketPriceSource
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.plugin.module.PluginModule
import org.rsmod.server.services.Service

public class GrandExchangeModule : PluginModule() {
    override fun bind() {
        bindProvider(SettingsProvider::class.java)
        bindProvider(PricesProvider::class.java)
        bindProvider(FillModelProvider::class.java)
        bindProvider(TaxProvider::class.java)
        bindProvider(ExchangeProvider::class.java)
        bindBaseInstance<GeItemCatalog>(CacheItemCatalog::class.java)
        bindBaseInstance<MarketQuoteSource>(PricesAsQuotes::class.java)
        bindSingleton<BuyLimits>(NoBuyLimits)
        addSetBinding<Service>(GrandExchangePriceService::class.java)
        addSetBinding<MarketPriceSource>(GrandExchangeGuidePrices::class.java)
    }

    private class SettingsProvider @Inject constructor(private val config: ServerConfig) :
        Provider<GrandExchangeSettings> {
        override fun get(): GrandExchangeSettings =
            GrandExchangeSettings.from(config.gameplay.grandExchange)
    }

    private class PricesProvider @Inject constructor(private val settings: GrandExchangeSettings) :
        Provider<GePrices> {
        override fun get(): GePrices {
            val feed: PriceFeed? =
                if (settings.liveFetch) WikiPriceFeed(OkHttpClient(), settings.userAgent) else null
            return GePrices(
                feed = feed,
                store = SnapshotStore(settings.snapshotPath),
                costOf = { id -> ServerCacheManager.getItem(id)?.cost?.takeIf { it > 0 } },
            )
        }
    }

    private class FillModelProvider @Inject constructor(private val settings: GrandExchangeSettings) :
        Provider<FillModel> {
        private val logger = InlineLogger()

        override fun get(): FillModel =
            try {
                FillModels.create(settings.fillModel)
            } catch (e: IllegalArgumentException) {
                logger.warn { "${e.message}. Falling back to '${InstantFillModel.name}'." }
                InstantFillModel
            }
    }

    private class TaxProvider @Inject constructor(private val settings: GrandExchangeSettings) :
        Provider<GeTax> {
        override fun get(): GeTax = GeTax.fromRate(settings.taxRate, settings.taxCap)
    }

    private class ExchangeProvider
    @Inject
    constructor(
        private val catalog: GeItemCatalog,
        private val quotes: MarketQuoteSource,
        private val tax: GeTax,
        private val limits: BuyLimits,
        private val fillModel: FillModel,
    ) : Provider<GrandExchange> {
        override fun get(): GrandExchange = GrandExchange(catalog, quotes, tax, limits, fillModel)
    }
}

internal class PricesAsQuotes @Inject constructor(private val prices: GePrices) : MarketQuoteSource {
    override fun quote(itemId: Int) = prices.quote(itemId)
}

internal class GrandExchangePriceService
@Inject
constructor(prices: GePrices, settings: GrandExchangeSettings) :
    Service by PriceRefreshService(
        prices = prices,
        live = settings.enabled && settings.liveFetch,
        refreshMinutes = settings.refreshMinutes,
    )

/**
 * Feeds the exchange's guide prices into the shared item price lookups: examine text, the drop
 * warning threshold, shop sell values and the items kept on death ordering.
 */
internal class GrandExchangeGuidePrices
@Inject
constructor(private val prices: GePrices, private val settings: GrandExchangeSettings) :
    MarketPriceSource {
    override fun guidePrice(type: ItemServerType): Int? {
        if (!settings.enabled) {
            return null
        }
        return prices.guideOrNull(type.id)
    }
}
