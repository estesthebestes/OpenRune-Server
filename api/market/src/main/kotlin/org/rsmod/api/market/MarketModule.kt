package org.rsmod.api.market

import org.rsmod.module.ExtendedModule

public object MarketModule : ExtendedModule() {
    override fun bind() {
        newSetBinding<MarketPriceSource>()
        bindBaseInstance<MarketPrices>(DefaultMarketPrices::class.java)
    }
}
