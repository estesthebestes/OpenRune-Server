package org.rsmod.api.grandexchange.book

import java.util.TreeSet
import org.rsmod.api.grandexchange.offer.OfferType

/** A resting offer. Orders by price, then by arrival; the live numbers stay in the owner's slot. */
public class BookEntry(
    public val ownerId: Long,
    public val slotIndex: Int,
    public val itemId: Int,
    public val type: OfferType,
    public val price: Long,
    public val sequence: Long,
)

/**
 * Open offers of players who are online, grouped per item. Best price first, oldest first among
 * equals (price-time priority). Not thread safe: only the game thread may touch it.
 */
public class OrderBook {
    private class ItemBook {
        val buys = TreeSet<BookEntry>(compareByDescending<BookEntry> { it.price }.thenBy { it.sequence })
        val sells = TreeSet<BookEntry>(compareBy<BookEntry> { it.price }.thenBy { it.sequence })

        fun side(type: OfferType): TreeSet<BookEntry> = if (type == OfferType.BUY) buys else sells
    }

    private val items = HashMap<Int, ItemBook>()
    private val byOwner = HashMap<Long, MutableMap<Int, BookEntry>>()
    private var nextSequence = 0L

    public val size: Int
        get() = byOwner.values.sumOf { it.size }

    public fun add(ownerId: Long, slotIndex: Int, itemId: Int, type: OfferType, price: Long): BookEntry {
        remove(ownerId, slotIndex)
        val entry = BookEntry(ownerId, slotIndex, itemId, type, price, nextSequence++)
        items.getOrPut(itemId) { ItemBook() }.side(type).add(entry)
        byOwner.getOrPut(ownerId) { HashMap() }[slotIndex] = entry
        return entry
    }

    public fun remove(ownerId: Long, slotIndex: Int): BookEntry? {
        val owned = byOwner[ownerId] ?: return null
        val entry = owned.remove(slotIndex) ?: return null
        if (owned.isEmpty()) {
            byOwner.remove(ownerId)
        }
        val book = items[entry.itemId]
        if (book != null) {
            book.side(entry.type).remove(entry)
            if (book.buys.isEmpty() && book.sells.isEmpty()) {
                items.remove(entry.itemId)
            }
        }
        return entry
    }

    public fun removeOwner(ownerId: Long) {
        val slots = byOwner[ownerId]?.keys?.toList() ?: return
        for (slot in slots) {
            remove(ownerId, slot)
        }
    }

    public fun find(ownerId: Long, slotIndex: Int): BookEntry? = byOwner[ownerId]?.get(slotIndex)

    /** Offers on the opposite side of [takerType] for [itemId], best first. */
    public fun opposing(itemId: Int, takerType: OfferType): List<BookEntry> =
        items[itemId]?.side(takerType.opposite)?.toList() ?: emptyList()

    public fun all(): List<BookEntry> = byOwner.values.flatMap { it.values }

    public fun ownedBy(ownerId: Long): List<BookEntry> = byOwner[ownerId]?.values?.toList() ?: emptyList()
}
