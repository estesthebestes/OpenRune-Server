package org.rsmod.api.shops.reload

import dev.openrune.types.InvStock
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
class ShopStockReconcilerTest {
    private val pot = 1
    private val jug = 2
    private val bucket = 3
    private val sold = 4

    @Test
    fun `depleted stock keeps its count and new stock starts full`() {
        val old = listOf(InvStock(pot, 5, 100), InvStock(jug, 5, 100))
        val new = listOf(InvStock(jug, 5, 100), InvStock(bucket, 10, 100), InvStock(pot, 5, 100))
        val current = arrayOf<InvObj?>(InvObj(pot, 2), InvObj(jug, 5), null, null)

        val result = ShopStockReconciler.reconcile(current, old, new, newSize = 4)

        assertEquals(InvObj(jug, 5), result.objs[0])
        assertEquals(InvObj(bucket, 10), result.objs[1])
        assertEquals(InvObj(pot, 2), result.objs[2])
        assertNull(result.objs[3])
        assertEquals(0, result.dropped)
    }

    @Test
    fun `player sold items move after the stock and untouched removed stock disappears`() {
        val old = listOf(InvStock(pot, 5, 100), InvStock(jug, 5, 100))
        val new = listOf(InvStock(bucket, 3, 100))
        val current = arrayOf<InvObj?>(InvObj(pot, 5), InvObj(jug, 1), InvObj(sold, 7))

        val result = ShopStockReconciler.reconcile(current, old, new, newSize = 3)

        assertEquals(InvObj(bucket, 3), result.objs[0])
        assertEquals(InvObj(jug, 1), result.objs[1])
        assertEquals(InvObj(sold, 7), result.objs[2])
    }

    @Test
    fun `items that no longer fit are counted as dropped`() {
        val stock = listOf(InvStock(pot, 5, 100))
        val current = arrayOf<InvObj?>(InvObj(pot, 5), InvObj(sold, 1), InvObj(jug, 1))

        val result = ShopStockReconciler.reconcile(current, stock, stock, newSize = 2)

        assertEquals(InvObj(pot, 5), result.objs[0])
        assertEquals(InvObj(sold, 1), result.objs[1])
        assertEquals(1, result.dropped)
    }
}
