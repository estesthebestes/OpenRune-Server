package org.rsmod.api.grandexchange.price

import java.io.IOException

public interface PriceFeed {
    /** @throws IOException when the prices could not be retrieved. */
    public fun fetchLatest(): Map<Int, PricePoint>

    /** Per-item buy limits (item id to limit). @throws IOException when it could not be retrieved. */
    public fun fetchBuyLimits(): Map<Int, Int>
}
