package org.rsmod.api.grandexchange.draft

import org.rsmod.api.grandexchange.offer.OfferType

/**
 * The quantity and price arithmetic of the offer setup screen. The client runs the same maths in
 * its own scripts for instant feedback, so the server has to land on the same numbers.
 */
public object OfferMath {
    public const val MAX_PRICE: Long = Int.MAX_VALUE.toLong()

    /** `+1` / `-1` coin, clamped to the valid price range. */
    public fun stepPrice(price: Long, up: Boolean): Long =
        if (up) {
            if (price < MAX_PRICE) price + 1 else price
        } else {
            if (price > 1) price - 1 else price
        }

    /** The `+5%` / `-5%` and custom percentage buttons: a step of at least one coin. */
    public fun stepPricePercent(price: Long, percent: Int, up: Boolean): Long {
        val step = maxOf(1L, price * percent / 100)
        return if (up) {
            if (MAX_PRICE - step < price) MAX_PRICE else price + step
        } else {
            if (step >= price) 1 else price - step
        }
    }

    /**
     * The quantity buttons. [delta] is the signed amount on the button; a delta of [Int.MAX_VALUE]
     * is the "+1K" button when buying and "All" when selling. [available] is how many items the
     * player holds (sell offers cannot go above it).
     */
    public fun stepQuantity(
        current: Int,
        delta: Int,
        type: OfferType,
        available: Int,
        isBond: Boolean = false,
    ): Int {
        if (isBond) {
            return 1
        }
        var step = delta
        if (type == OfferType.SELL) {
            if (step >= Int.MAX_VALUE) {
                return available
            }
        } else if (step >= Int.MAX_VALUE) {
            step = 1000
        }
        val cap = if (type == OfferType.SELL) available else Int.MAX_VALUE
        if (step > 0) {
            if (step > 1 && current == 1) {
                step -= 1
            }
            return if (cap - step < current) cap else current + step
        }
        return if (current <= -step) 1 else current + step
    }

    /** The "All" button on a buy offer: as many as the coins in hand pay for, at least one. */
    public fun affordableQuantity(coins: Long, price: Long): Int {
        if (price <= 0) {
            return 1
        }
        if (coins <= 0) {
            return 1
        }
        return (coins / price).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
}
