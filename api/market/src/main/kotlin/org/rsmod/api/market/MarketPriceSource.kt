package org.rsmod.api.market

import dev.openrune.types.ItemServerType

/**
 * Contributes a guide price for an obj. [DefaultMarketPrices] asks every bound source in turn and
 * falls back to the cache cost when none of them knows the obj.
 */
public fun interface MarketPriceSource {
    public fun guidePrice(type: ItemServerType): Int?
}
