package org.rsmod.content.areas.city.grandexchange

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.objExamine
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj

/**
 * Shows a market guide through the stock `ge_pricelist` window: the client draws one card per
 * object in the transmitted inventory and reads the stack count as the price.
 */
@Singleton
internal class PriceListWindow @Inject constructor(private val prices: MarketPrices) {
    fun open(access: ProtectedAccess, guide: PriceGuide) {
        val inv = access.player.priceList
        inv.fillNulls()
        for ((slot, type) in guide.types.withIndex()) {
            inv[slot] = InvObj(type, prices[type]?.coerceAtLeast(1) ?: type.cost.coerceAtLeast(1))
        }
        access.ifOpenMainModal(INTERFACE)
        access.invTransmit(inv)
        access.ifSetEvents(LIST, guide.types.indices, IfEvent.Op10)
        access.player.runClientScript(INIT.asRSCM(RSCMType.CLIENTSCRIPT), inv.type.id, guide.title)
    }

    fun close(player: Player) {
        val inv = player.priceList
        inv.fillNulls()
        player.stopInvTransmit(inv)
    }

    fun examine(player: Player, slot: Int) {
        val obj = player.priceList[slot] ?: return
        val type = getInvObj(obj)
        player.objExamine(type, 1, obj.count)
    }

    private val Player.priceList: Inventory
        get() = invMap.getOrPut(INV)

    companion object {
        const val INTERFACE = "interface.ge_pricelist"
        const val LIST = "component.ge_pricelist:list"
        const val INV = "inv.ge_pricelist"
        const val INIT = "clientscript.[clientscript,ge_pricelist]"
    }
}
