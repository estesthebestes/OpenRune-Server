package org.rsmod.content.interfaces.grandexchange

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.grandexchange.GrandExchangeSettings
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.player.ironman.IronmanActivity
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.output.StockMarket
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player

/** Opens the exchange interfaces, applying the rules every entry point shares. */
@Singleton
internal class GeWindows
@Inject
constructor(
    private val exchange: GrandExchange,
    private val sessions: GeSessions,
    private val settings: GrandExchangeSettings,
) {
    /** Whether the player is turned away from the exchange; tells them why. */
    fun refuses(player: Player): Boolean {
        if (IronmanRestrictions.block(player, IronmanActivity.GRAND_EXCHANGE)) {
            return true
        }
        if (!settings.enabled) {
            player.mes("The Grand Exchange is closed.")
            return true
        }
        return false
    }

    private fun allowed(access: ProtectedAccess): Boolean = !refuses(access.player)

    fun openExchange(access: ProtectedAccess): Boolean {
        if (!allowed(access)) {
            return false
        }
        val player = access.player
        val session = sessions.of(player)
        player.geSelectedSlot = 0
        player.geSearchItem = NO_ITEM
        player.geNewOfferQuantity = 1
        player.geTaxRate = exchange.taxRatePermille
        if (player.geLastOfferItem == 0) {
            player.geLastOfferItem = NO_ITEM
        }
        player.geOfferPrice = 0
        StockMarket.writeVarpLong(player, GeIds.CLIENT_OFFER_PRICE_VARP, 0)
        sessions.transmitBoxes(player)
        access.invTransmit(access.inv)
        session.pushAllSlots()
        access.ifOpenMainSidePair(
            main = "interface.ge_offers",
            side = "interface.ge_offers_side",
            transparency = -2,
        )
        return true
    }

    fun openCollect(access: ProtectedAccess): Boolean {
        if (!allowed(access)) {
            return false
        }
        sessions.of(access.player).pushAllSlots()
        access.ifOpenMainModal("interface.ge_collect")
        return true
    }

    fun openHistory(access: ProtectedAccess): Boolean {
        if (!allowed(access)) {
            return false
        }
        access.ifOpenMainModal("interface.ge_history")
        sendHistory(access)
        return true
    }

    /** Rebuilds the history list in the interface the way the client scripts expect it. */
    fun sendHistory(access: ProtectedAccess) {
        val player = access.player
        player.runClientScript(HISTORY_INIT.asRSCM(RSCMType.CLIENTSCRIPT))
        val addLine = HISTORY_ADD_LINE.asRSCM(RSCMType.CLIENTSCRIPT)
        for ((index, entry) in GeHistoryStore.read(player).withIndex()) {
            player.runClientScript(
                addLine,
                index,
                entry.itemId,
                entry.type.code,
                entry.quantity,
                entry.gold,
                entry.tax,
            )
        }
        player.runClientScript(HISTORY_FINISH.asRSCM(RSCMType.CLIENTSCRIPT))
    }

    private companion object {
        const val NO_ITEM = -1
        const val HISTORY_INIT = "clientscript.[clientscript,ge_history_init]"
        const val HISTORY_ADD_LINE = "clientscript.[clientscript,ge_history_addline]"
        const val HISTORY_FINISH = "clientscript.[clientscript,ge_history_finish]"
    }
}
