package org.rsmod.content.other.worldreload.types

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.hotreload.types.TypeReloadChanges
import org.rsmod.api.hotreload.types.TypeReloadListener
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.reload.ShopStockReconciler
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.events.EventBus
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.Inventory

/** Re-lays out shared shop inventories whose inv type changed in a `::reload types`. */
@Singleton
class ShopStockReloadListener
@Inject
constructor(
    private val shops: Shops,
    private val restock: ShopRestockProcess,
    private val playerList: PlayerList,
    private val eventBus: EventBus,
) : TypeReloadListener {
    override fun onTypesReloaded(changes: TypeReloadChanges): String? {
        if (changes.invs.isEmpty()) {
            return null
        }
        var updated = 0
        var closed = 0
        var dropped = 0
        for ((invId, change) in changes.invs) {
            val type = ServerCacheManager.getInventory(invId) ?: continue
            if (type.scope != InvScope.Shared) continue
            val name = RSCM.getReverseMapping(RSCMType.INV, invId)
            val current = shops.globalInvs[name] ?: continue
            val result =
                ShopStockReconciler.reconcile(current.objs, change.oldStock, type.stock, type.size)
            dropped += result.dropped
            if (result.objs.size == current.objs.size) {
                for (slot in result.objs.indices) {
                    if (current[slot] != result.objs[slot]) {
                        current[slot] = result.objs[slot]
                    }
                }
                restock += current
            } else {
                closed += closeFor(current)
                val replacement = Inventory(type, result.objs)
                shops.globalInvs[name] = replacement
                restock.modifiedShops -= current
                restock += replacement
            }
            updated++
        }
        if (updated == 0) {
            return null
        }
        val extra = if (dropped > 0) ", $dropped items no longer fit" else ""
        return "$updated shops restocked ($closed viewers closed$extra)"
    }

    private fun closeFor(inventory: Inventory): Int {
        var closed = 0
        for (player in playerList) {
            if (player.openedShop?.inv !== inventory) continue
            player.ifCloseSub("interface.shopmain", eventBus)
            player.mes("This shop's stock was just updated; please open it again.")
            closed++
        }
        return closed
    }
}
