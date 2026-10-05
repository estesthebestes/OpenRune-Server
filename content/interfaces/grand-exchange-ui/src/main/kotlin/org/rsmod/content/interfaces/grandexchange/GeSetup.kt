package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.grandexchange.draft.OfferMath
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.rules.GeItemCatalog
import org.rsmod.api.player.output.StockMarket
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.entity.Player

/**
 * The state behind the offer setup and status panels, and the one thing that makes the window show
 * the right one.
 *
 * Writing `ge_selectedslot` is not enough on its own: the panels only change when the client runs
 * `ge_offers_switchpanel`, and relying on the client's own var-transmit hooks to do that proved
 * unreliable. Every change of the selected slot therefore ends in [refreshPanel], which runs the
 * script itself with the components the interface was loaded with.
 */
@Singleton
internal class GeSetup @Inject constructor(private val prices: GePrices, private val catalog: GeItemCatalog) {
    /** Opens the setup panel for an empty [slot]. */
    fun begin(player: Player, slot: Int, type: OfferType) {
        player.geSelectedSlot = slot + 1
        player.geNewOfferType = type.code
        player.geSearchItem = NO_ITEM
        player.geNewOfferQuantity = 1
        setPrice(player, 0)
        player.ifSetText("component.ge_offers:setup_marketprice", "")
        player.ifSetText("component.ge_offers:setup_desc", "")
        refreshPanel(player)
    }

    /** Shows the status panel of [slot]. */
    fun view(player: Player, slot: Int, itemId: Int?, type: OfferType) {
        player.geSelectedSlot = slot + 1
        if (itemId != null) {
            describe(player, itemId, type, setup = false)
        }
        refreshPanel(player)
    }

    /** Back to the overview of all slots. */
    fun back(player: Player) {
        player.geSelectedSlot = 0
        refreshPanel(player)
    }

    /** Fills the setup panel for [itemId]; a `null` price means the market price for the side. */
    fun selectItem(player: Player, itemId: Int, quantity: Int, price: Long?) {
        val type = OfferType.fromCode(player.geNewOfferType)
        val info = catalog.resolve(itemId) ?: return
        player.geSearchItem = info.id
        player.geLastSearched = info.id
        player.geNewOfferQuantity = quantity.coerceAtLeast(1)
        setPrice(player, price ?: marketPrice(info.id, type))
        describe(player, info.id, type, setup = true)
        refreshPanel(player)
    }

    fun marketPrice(itemId: Int, type: OfferType): Long {
        val quote = prices.quote(itemId)
        return if (type == OfferType.BUY) quote.buyAt else quote.sellAt
    }

    fun setPrice(player: Player, price: Long) {
        val clamped = price.coerceIn(0L, OfferMath.MAX_PRICE)
        player.geOfferPrice = clamped.toInt()
        StockMarket.writeVarpLong(player, GeIds.CLIENT_OFFER_PRICE_VARP, clamped)
    }

    fun describe(player: Player, itemId: Int, type: OfferType, setup: Boolean) {
        val item = ServerCacheManager.getItem(itemId) ?: return
        val prefix = if (setup) "setup" else "details"
        player.runClientScript(
            DESC_SCRIPT,
            item.examine,
            "",
            "component.ge_offers:${prefix}_desc".asRSCM(RSCMType.COMPONENT),
            "component.ge_offers:${prefix}_fee".asRSCM(RSCMType.COMPONENT),
        )
        val guide = marketPrice(itemId, type)
        player.ifSetText(
            "component.ge_offers:${prefix}_marketprice",
            "Guide price: ${guide.formatAmount} coins",
        )
    }

    /**
     * Sends the item sink vars (an object and a 64-bit price per slot) in their "nothing here"
     * state. The offers and collection scripts read them whenever they look at a slot, and a long
     * var the client has never been sent is not guaranteed to read as 0.
     */
    fun sendItemSinkDefaults(player: Player) {
        for (slot in 0 until SlotCodec.SLOTS) {
            VarPlayerIntMapSetter.set(player, "varp.ge_itemsink_obj_$slot", NO_ITEM)
            StockMarket.writeVarpLong(player, GeIds.itemSinkPriceVarps[slot].id, 0)
        }
    }

    /** Runs `ge_offers_switchpanel` so the window shows the overview, setup or status panel. */
    fun refreshPanel(player: Player) {
        player.runClientScript(
            SWITCH_PANEL_SCRIPT,
            component("frame"),
            TITLE_CHILD,
            component("back"),
            component("index"),
            component("details"),
            component("setup"),
            component("tooltip"),
        )
    }

    private fun component(name: String): Int =
        "component.ge_offers:$name".asRSCM(RSCMType.COMPONENT)

    companion object {
        const val NO_ITEM = -1
        const val DESC_SCRIPT = 5730
        const val SWITCH_PANEL_SCRIPT = 804

        /** The child `steelborder` creates its title text as, which the script rewrites. */
        const val TITLE_CHILD = 1
    }
}
