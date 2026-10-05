package org.rsmod.api.grandexchange.offer

public enum class OfferType(public val code: Int) {
    BUY(0),
    SELL(1);

    public val opposite: OfferType
        get() = if (this == BUY) SELL else BUY

    public companion object {
        public fun fromCode(code: Int): OfferType = if (code == SELL.code) SELL else BUY
    }
}

public enum class OfferState(public val code: Int) {
    EMPTY(0),
    OPEN(1),

    /** Fully filled. The slot stays occupied until its collection box has been emptied. */
    COMPLETED(2),

    /** Aborted by the player. Same collection rule as [COMPLETED]. */
    ABORTED(3);

    public val isFinished: Boolean
        get() = this == COMPLETED || this == ABORTED

    public companion object {
        public fun fromCode(code: Int): OfferState = entries.firstOrNull { it.code == code } ?: EMPTY
    }
}

/**
 * One of a player's offer slots. [tax] is the convenience fee charged so far (sells only).
 *
 * Escrow is implied by the numbers: an open buy holds `remaining * price` coins and an open sell
 * holds `remaining` items.
 */
public data class OfferSlot(
    val state: OfferState = OfferState.EMPTY,
    val type: OfferType = OfferType.BUY,
    val itemId: Int = 0,
    val quantity: Int = 0,
    val price: Int = 0,
    val completedQuantity: Int = 0,
    val completedGold: Long = 0,
    val tax: Long = 0,
) {
    val remaining: Int
        get() = quantity - completedQuantity

    val isEmpty: Boolean
        get() = state == OfferState.EMPTY

    val isOpen: Boolean
        get() = state == OfferState.OPEN

    public companion object {
        public val EMPTY: OfferSlot = OfferSlot()

        /** Largest total (`quantity * price`, or gold exchanged) a single offer may reach. */
        public const val MAX_TOTAL: Long = Int.MAX_VALUE * 1000L + 999
    }
}
