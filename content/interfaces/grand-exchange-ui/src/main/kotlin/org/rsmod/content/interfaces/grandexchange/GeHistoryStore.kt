package org.rsmod.content.interfaces.grandexchange

import org.rsmod.api.grandexchange.engine.HistoryEntry
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.game.entity.Player

/**
 * The last finished trades, newest first, kept in a persistent inventory so no table is needed.
 * Every trade takes four stacks: the item with the quantity as its count, then three coin stacks
 * whose counts carry the low 30 bits of the gold, its high bits plus the trade type, and the fee.
 * Counts are stored plus one because a stack cannot hold zero.
 */
internal object GeHistoryStore {
    const val ENTRIES = 10
    private const val STRIDE = 4
    private const val LOW_BITS = 30
    private const val LOW_MASK = (1L shl LOW_BITS) - 1
    private const val TYPE_SHIFT = 12

    fun push(player: Player, entry: HistoryEntry) {
        val inv = player.invMap.getOrPut(GeIds.HISTORY_INV)
        for (slot in (ENTRIES - 1) * STRIDE - 1 downTo 0) {
            inv[slot + STRIDE] = inv[slot]
        }
        val counts = counts(entry)
        inv[0] = invObj(entry.itemId, counts[0])
        for (part in 1 until STRIDE) {
            inv[part] = invObj(GeIds.coins, counts[part])
        }
    }

    fun read(player: Player): List<HistoryEntry> {
        val inv = player.invMap.getOrPut(GeIds.HISTORY_INV)
        return buildList {
            for (index in 0 until ENTRIES) {
                val base = index * STRIDE
                val item = inv[base] ?: continue
                val counts = IntArray(STRIDE) { if (it == 0) item.count else inv[base + it]?.count ?: 1 }
                add(entryOf(item.id, counts))
            }
        }
    }

    /** The stack counts of one trade: quantity, low gold bits, high gold bits and type, fee. */
    fun counts(entry: HistoryEntry): IntArray {
        val gold = entry.gold.coerceAtLeast(0)
        val high = (gold ushr LOW_BITS) or (entry.type.code.toLong() shl TYPE_SHIFT)
        return intArrayOf(
            entry.quantity.coerceAtLeast(1),
            ((gold and LOW_MASK) + 1).toInt(),
            (high + 1).toInt(),
            entry.tax.coerceIn(0, Int.MAX_VALUE - 1L).toInt() + 1,
        )
    }

    fun entryOf(itemId: Int, counts: IntArray): HistoryEntry {
        val low = counts[1] - 1L
        val high = counts[2] - 1L
        val type = OfferType.fromCode((high shr TYPE_SHIFT).toInt())
        val gold = ((high and ((1L shl TYPE_SHIFT) - 1)) shl LOW_BITS) or low
        return HistoryEntry(itemId, type, counts[0], gold, counts[3] - 1L)
    }
}
