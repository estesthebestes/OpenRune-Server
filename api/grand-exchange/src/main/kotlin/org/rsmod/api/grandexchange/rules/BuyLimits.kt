package org.rsmod.api.grandexchange.rules

/**
 * Seam for the OSRS "buying limit per 4 hours" rule. Version 1 ships [NoBuyLimits]; a real
 * implementation can be bound in its place (the wiki mapping limits are already cached by
 * [org.rsmod.api.grandexchange.price.GePrices.buyLimit]) without touching the exchange itself.
 */
public interface BuyLimits {
    /** Whether [participantId] may place a buy offer for [quantity] of [itemId] right now. */
    public fun canBuy(participantId: Long, itemId: Int, quantity: Int, nowMillis: Long): Boolean

    /** Called whenever buy quantity is actually bought, so limits can be consumed. */
    public fun recordBuy(participantId: Long, itemId: Int, quantity: Int, nowMillis: Long)
}

public object NoBuyLimits : BuyLimits {
    override fun canBuy(participantId: Long, itemId: Int, quantity: Int, nowMillis: Long): Boolean =
        true

    override fun recordBuy(participantId: Long, itemId: Int, quantity: Int, nowMillis: Long) {}
}
