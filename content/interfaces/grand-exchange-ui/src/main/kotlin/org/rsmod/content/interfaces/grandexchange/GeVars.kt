package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpServerType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

/** Which offer slot the offers window is showing: 0 for the overview, otherwise slot + 1. */
internal var Player.geSelectedSlot by intVarBit("varbit.ge_selectedslot")

/** 0 while setting up a buy offer, 1 for a sell offer. */
internal var Player.geNewOfferType by intVarBit("varbit.ge_newoffer_type")
internal var Player.geNewOfferQuantity by intVarBit("varbit.ge_newoffer_quantity")

/** The custom percentage of the `-X%` / `+X%` price buttons. */
internal var Player.gePriceCustom by intVarBit("varbit.ge_price_custom")

/** The fee rate the client shows, in tenths of a percent. */
internal var Player.geTaxRate by intVarBit("varbit.ge_transmit_taxrate")

/** The item being set up; `-1` when none. */
internal var Player.geSearchItem by intVarp("varp.tradingpost_search")

internal var Player.geLastOfferItem by intVarp("varp.ge_last_offer_item")
internal var Player.geLastOfferQuantity by intVarp("varp.ge_last_offer_quantity")
internal var Player.geLastOfferPrice by intVarp("varp.ge_last_offer_price")
internal var Player.geLastOfferType by intVarp("varp.ge_last_offer_type")
internal var Player.geLastSearched by intVarp("varp.ge_last_searched")

private val OFFER_PRICE = AttributeKey<Int>()

/**
 * The server's copy of the offer price being set up. The client keeps it in a 64-bit varp that has
 * no gameval name (see [GeIds.CLIENT_OFFER_PRICE_VARP]) and the allocated server varps are all in
 * use by the slot records, so it lives on the player for the length of the session.
 */
internal var Player.geOfferPrice: Int
    get() = attr[OFFER_PRICE] ?: 0
    set(value) {
        attr[OFFER_PRICE] = value
    }

/** The cache types behind the persistent exchange state, resolved once the cache is loaded. */
internal object GeIds {
    val slotWords: Array<VarpServerType> by lazy {
        Array(SlotCodec.WORDS) { varp("varp.ge_slots_$it") }
    }

    val taxVarps: Array<VarpServerType> by lazy {
        Array(SlotCodec.SLOTS) { varp("varp.ge_tax_slot_long_$it") }
    }

    /** The long item sink price varps; the offers scripts read them when they look at a slot. */
    val itemSinkPriceVarps: Array<VarpServerType> by lazy {
        Array(SlotCodec.SLOTS) { varp("varp.ge_itemsink_price_long_$it") }
    }

    /** `varplayer_5753`, the long varp the setup screen reads the price from. */
    const val CLIENT_OFFER_PRICE_VARP: Int = 5753

    val boxInvs: List<String> =
        List(SlotCodec.SLOTS) { if (it < 6) "inv.tradingpost_sell_$it" else "inv.ge_collect_$it" }

    const val HISTORY_INV: String = "inv.ge_history"

    val coins: Int by lazy { "obj.coins".asRSCM(RSCMType.OBJ) }
    val platinum: Int by lazy { "obj.platinum".asRSCM(RSCMType.OBJ) }

    private fun varp(name: String): VarpServerType =
        ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))
            ?: error("Grand Exchange varp is missing from the cache: $name")
}

internal fun Inventory.totalOf(id: Int): Long {
    var total = 0L
    for (obj in objs) {
        if (obj != null && obj.id == id && obj.vars == 0) {
            total += obj.count
        }
    }
    return total
}

internal fun invObj(id: Int, count: Int): InvObj {
    val type = ServerCacheManager.getItem(id) ?: error("Unknown item $id")
    return InvObj(type, count)
}
