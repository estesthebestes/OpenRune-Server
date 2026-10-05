package org.rsmod.api.grandexchange.engine

import org.rsmod.api.grandexchange.offer.CollectionBox
import org.rsmod.api.grandexchange.offer.OfferSlot
import org.rsmod.api.grandexchange.offer.OfferType

/** A finished trade as shown in the trade history interface. */
public data class HistoryEntry(
    val itemId: Int,
    val type: OfferType,
    val quantity: Int,
    val gold: Long,
    val tax: Long,
)

/** Reported to the owner every time part of their offer trades. */
public data class FillNotice(
    val slotIndex: Int,
    val type: OfferType,
    val itemId: Int,
    val filled: Int,
    val unitPrice: Long,
    val completedQuantity: Int,
    val quantity: Int,
    val finished: Boolean,
)

/**
 * One player as the exchange sees them. The exchange owns the rules; the participant owns the
 * storage (varps for slots, inventories for collection boxes and for what escrow is taken from).
 * All calls happen on the game thread.
 */
public interface GeParticipant {
    public val id: Long

    /** How many slots this player may start offers in (3 for free players, 8 for members). */
    public val slotCount: Int

    public fun slot(index: Int): OfferSlot

    public fun setSlot(index: Int, slot: OfferSlot)

    public fun box(index: Int): CollectionBox

    /** Takes [amount] coins from the player, all or nothing. */
    public fun takeCoins(amount: Long): Boolean

    /** Takes [count] of [itemId] (counting noted copies too), all or nothing. */
    public fun takeItems(itemId: Int, count: Int): Boolean

    /** The slot was changed by the exchange; refresh anything that mirrors it. */
    public fun onSlotChanged(index: Int)

    public fun onFill(notice: FillNotice)

    public fun recordHistory(entry: HistoryEntry)
}

/** Where collected goods go. Each function returns how much was actually accepted. */
public interface CollectSink {
    public fun acceptItems(itemId: Int, count: Int, noted: Boolean): Int

    public fun acceptCoins(amount: Long): Long
}

public enum class CollectPart {
    ITEMS,
    COINS,
    ALL,
}

public enum class PlaceFailure {
    INVALID_SLOT,
    SLOT_IN_USE,
    NOT_TRADEABLE,
    INVALID_OFFER,
    TOO_EXPENSIVE,
    BUY_LIMIT,
    NOT_ENOUGH_COINS,
    NOT_ENOUGH_ITEMS,
}

public sealed interface PlaceResult {
    public data class Placed(val slot: OfferSlot, val filledQuantity: Int) : PlaceResult

    public data class Rejected(val reason: PlaceFailure) : PlaceResult
}

public enum class AbortResult {
    ABORTED,
    NOT_OPEN,
}
