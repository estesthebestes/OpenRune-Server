package org.rsmod.api.shops.reload

import dev.openrune.types.InvStock
import dev.openrune.types.util.UncheckedType
import org.rsmod.game.inv.InvObj

public object ShopStockReconciler {
    public data class Result(val objs: Array<InvObj?>, val dropped: Int) {
        override fun equals(other: Any?): Boolean =
            other is Result && objs.contentEquals(other.objs) && dropped == other.dropped

        override fun hashCode(): Int = 31 * objs.contentHashCode() + dropped
    }

    /**
     * Lays a shop out for its new stock list while keeping what players did to it: stock items
     * keep their current (depleted or overstocked) counts and move to their new slot, new stock
     * items start full, player-sold items move to the free slots after the stock, and removed stock
     * that was untouched disappears. Items that no longer fit are counted in [Result.dropped].
     */
    @OptIn(UncheckedType::class)
    public fun reconcile(
        current: Array<InvObj?>,
        oldStock: List<InvStock>,
        newStock: List<InvStock>,
        newSize: Int,
    ): Result {
        val result = arrayOfNulls<InvObj>(newSize)
        val remaining = current.filterNotNull().toMutableList()

        for ((slot, stock) in newStock.withIndex()) {
            if (slot >= newSize) break
            val existing = remaining.firstOrNull { it.id == stock.obj }
            if (existing != null) {
                remaining.remove(existing)
            }
            result[slot] = existing ?: InvObj(stock.obj, stock.count)
        }

        var dropped = 0
        var next = 0
        for (obj in remaining) {
            val previous = oldStock.firstOrNull { it.obj == obj.id }
            if (previous != null && obj.count == previous.count) {
                continue
            }
            while (next < newSize && result[next] != null) next++
            if (next >= newSize) {
                dropped++
                continue
            }
            result[next] = obj
        }
        return Result(result, dropped)
    }
}
