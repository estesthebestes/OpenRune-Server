package org.rsmod.content.other.worldreload.types

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.reload.ShopStockReconciler

/** `::reload shops` puts every opened shared shop back to its configured stock. */
@Singleton
class ShopResetTarget @Inject constructor(private val shops: Shops) : Reloadable {
    override val id: String = "shops"
    override val description: String = "Reset shared shops to their configured stock"
    override val order: Int = 70
    override val includeInAll: Boolean = false

    override fun prepare(context: ReloadContext): ReloadPlan = ReloadPlan { apply() }

    private fun apply(): ReloadSummary {
        var reset = 0
        for (inventory in shops.globalInvs.values) {
            val stock = inventory.type.stock
            val fresh = ShopStockReconciler.reconcile(emptyArray(), stock, stock, inventory.size)
            for (slot in 0 until inventory.size) {
                val objs = fresh.objs
                if (slot < objs.size && inventory[slot] != objs[slot]) {
                    inventory[slot] = objs[slot]
                }
            }
            reset++
        }
        return ReloadSummary("$reset shops reset to their configured stock")
    }
}
