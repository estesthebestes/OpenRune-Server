package org.rsmod.api.market

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.game.type.uncert

public class DefaultMarketPrices
@Inject
constructor(private val sources: Set<@JvmSuppressWildcards MarketPriceSource>) : MarketPrices {
    public constructor() : this(emptySet())

    override fun get(type: ItemServerType): Int {
        val base = uncert(type)
        for (source in sources) {
            val price = source.guidePrice(base)
            if (price != null) {
                return price
            }
        }
        return base.cost
    }
}
