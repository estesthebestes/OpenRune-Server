package org.rsmod.content.interfaces.grandexchange

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.grandexchange.draft.OfferMath
import org.rsmod.api.grandexchange.engine.AbortResult
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.engine.PlaceFailure
import org.rsmod.api.grandexchange.engine.PlaceResult
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.grandexchange.rules.GeItemCatalog
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The offers window (`ge_offers`) and its inventory side panel (`ge_offers_side`).
 *
 * The 241 client scripts apply every button to their own copy of the setup varps for instant
 * feedback, and also send the op to the server. The server is authoritative: it applies the same
 * change to its copy and writes the result back, so both always agree.
 */
internal class GeOffersScript
@Inject
constructor(
    private val exchange: GrandExchange,
    private val sessions: GeSessions,
    private val collector: GeCollector,
    private val catalog: GeItemCatalog,
    private val windows: GeWindows,
    private val setup: GeSetup,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onIfOpen("interface.ge_offers") {
            logger.debug { "GE offers window opened: player=${player.username}" }
            player.enableOffersEvents()
        }
        onIfClose("interface.ge_offers") {
            logger.debug { "GE offers window closed: player=${player.username}" }
            player.geSelectedSlot = 0
        }

        for (slot in 0 until SlotCodec.SLOTS) {
            onIfModalButton("component.ge_offers:index_$slot") {
                logOp("index_$slot", it.comsub, it.op)
                slotButton(slot, it.comsub, it.op)
            }
        }
        onIfModalButton("component.ge_offers:back") {
            logOp("back", it.comsub, it.op)
            setup.back(player)
        }
        onIfModalButton("component.ge_offers:history") {
            logOp("history", it.comsub, it.op)
            windows.openHistory(this)
        }
        onIfModalButton("component.ge_offers:collectall") {
            logOp("collectall", it.comsub, it.op)
            collectAllButton(it.comsub, it.op)
        }
        onIfModalButton("component.ge_offers:setup") {
            logOp("setup", it.comsub, it.op)
            setupButton(it.comsub, it.op)
        }
        onIfModalButton("component.ge_offers:setup_confirm") {
            logOp("setup_confirm", it.comsub, it.op)
            confirmOffer()
        }
        onIfModalButton("component.ge_offers:details_status") {
            logOp("details_status", it.comsub, it.op)
            statusButton(it.comsub)
        }
        onIfModalButton("component.ge_offers:details_collect") {
            logOp("details_collect", it.comsub, it.op)
            detailsCollectButton(it.comsub, it.op)
        }
        onIfModalButton("component.ge_offers_side:items") {
            logOp("side:items", it.comsub, it.op)
            sideItemButton(it.comsub, it.op)
        }
    }

    private fun ProtectedAccess.logOp(component: String, comsub: Int, op: IfButtonOp) {
        logger.debug {
            "GE button: player=${player.username} component=$component comsub=$comsub op=$op " +
                "selectedSlot=${player.geSelectedSlot} type=${player.geNewOfferType} " +
                "item=${player.geSearchItem} qty=${player.geNewOfferQuantity} price=${player.geOfferPrice}"
        }
    }

    private fun Player.enableOffersEvents() {
        for (slot in 0 until SlotCodec.SLOTS) {
            ifSetEvents("component.ge_offers:index_$slot", 0..4, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3)
        }
        ifSetEvents("component.ge_offers:collectall", 0..1, IfEvent.Op1, IfEvent.Op2)
        ifSetEvents("component.ge_offers:setup", 0..SETUP_LAST_CHILD, IfEvent.Op1, IfEvent.Op2)
        ifSetEvents("component.ge_offers:details_status", 0..1, IfEvent.Op1)
        ifSetEvents(
            "component.ge_offers:details_collect",
            0..5,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op10,
        )
        ifSetEvents("component.ge_offers_side:items", inv.indices, IfEvent.Op1, IfEvent.Op10)
    }

    private suspend fun ProtectedAccess.slotButton(slot: Int, child: Int, op: IfButtonOp) {
        when (child) {
            CHILD_VIEW ->
                when (op) {
                    IfButtonOp.Op1 -> viewSlot(slot)
                    IfButtonOp.Op2 -> abortSlot(slot)
                    IfButtonOp.Op3 -> modifySlot(slot)
                    else -> Unit
                }
            CHILD_BUY -> if (op == IfButtonOp.Op1) createOffer(slot, OfferType.BUY)
            CHILD_SELL -> if (op == IfButtonOp.Op1) createOffer(slot, OfferType.SELL)
        }
    }

    private fun ProtectedAccess.viewSlot(slot: Int) {
        val session = sessions.of(player)
        val offer = session.slot(slot)
        if (slot >= session.slotCount && offer.isEmpty) {
            return
        }
        setup.view(player, slot, if (offer.isEmpty) null else offer.itemId, offer.type)
    }

    private suspend fun ProtectedAccess.createOffer(slot: Int, type: OfferType) {
        val session = sessions.of(player)
        if (slot >= session.slotCount) {
            logger.debug { "GE create offer refused: slot $slot is beyond ${session.slotCount} slots" }
            mes("You need to be a member to use that offer slot.")
            return
        }
        if (!session.slot(slot).isEmpty || !session.box(slot).isEmpty()) {
            logger.debug { "GE create offer ignored: slot $slot is in use (${session.slot(slot).state})" }
            return
        }
        logger.debug { "GE create $type offer in slot $slot" }
        beginSetup(slot, type)
        if (type == OfferType.BUY) {
            chooseBuyItem()
        }
    }

    private fun ProtectedAccess.beginSetup(slot: Int, type: OfferType) {
        setup.begin(player, slot, type)
    }

    private fun ProtectedAccess.abortSlot(slot: Int) {
        val session = sessions.of(player)
        if (exchange.abort(session, slot) == AbortResult.ABORTED) {
            mes("Abort request acknowledged. Please be aware that your offer may have already been completed.")
            setup.refreshPanel(player)
        }
    }

    /**
     * Aborts the offer and tries to hand everything back so the slot is free for a new one, with
     * the same item and price pre-filled for the part that was not traded yet.
     */
    private fun ProtectedAccess.modifySlot(slot: Int) {
        val session = sessions.of(player)
        val before = session.slot(slot)
        if (exchange.abort(session, slot) != AbortResult.ABORTED) {
            return
        }
        val leftBehind = collector.collectSlot(player, slot, toBank = false)
        if (leftBehind || !session.slot(slot).isEmpty) {
            mes("Your offer was aborted. Make room in your inventory, collect, and set up the new offer.")
            return
        }
        beginSetup(slot, before.type)
        selectItem(before.itemId, before.remaining.coerceAtLeast(1), before.price.toLong())
    }

    private fun ProtectedAccess.collectAllButton(child: Int, op: IfButtonOp) {
        when (child) {
            0 -> {
                val toBank = op == IfButtonOp.Op2
                if (op != IfButtonOp.Op1 && !toBank) {
                    return
                }
                if (collector.collectAll(player, toBank)) {
                    mes("You don't have enough inventory space to collect everything.")
                }
                leaveFreedSlot()
            }
            1 -> if (op == IfButtonOp.Op1) repeatLastOffer()
        }
    }

    private fun ProtectedAccess.repeatLastOffer() {
        val item = player.geLastOfferItem
        if (item <= 0) {
            return
        }
        val free = firstFreeSlot(sessions.of(player))
        if (free == null) {
            mes("You have no free offer slot.")
            return
        }
        beginSetup(free, OfferType.fromCode(player.geLastOfferType))
        selectItem(item, player.geLastOfferQuantity.coerceAtLeast(1), player.geLastOfferPrice.toLong())
    }

    /** A slot that has been collected empty has nothing left to show, so go back to the overview. */
    private fun ProtectedAccess.leaveFreedSlot() {
        val shown = player.geSelectedSlot - 1
        if (shown in 0 until SlotCodec.SLOTS && sessions.of(player).slot(shown).isEmpty) {
            setup.back(player)
        }
    }

    private fun firstFreeSlot(session: GePlayer): Int? =
        (0 until session.slotCount).firstOrNull {
            session.slot(it).isEmpty && session.box(it).isEmpty()
        }

    private suspend fun ProtectedAccess.chooseBuyItem() {
        val chosen = objDialog("What would you like to buy?")
        if (player.geSelectedSlot == 0 || player.geNewOfferType != OfferType.BUY.code) {
            return
        }
        val info = catalog.resolve(chosen.id)
        if (info == null) {
            mes("You can't trade that item on the Grand Exchange.")
            return
        }
        selectItem(info.id, quantity = 1, price = null)
    }

    private fun ProtectedAccess.selectItem(itemId: Int, quantity: Int, price: Long?) {
        setup.selectItem(player, itemId, quantity, price)
    }

    private suspend fun ProtectedAccess.setupButton(child: Int, op: IfButtonOp) {
        val selected = player.geSearchItem
        if (child == CHILD_ITEM_BOX) {
            if (op == IfButtonOp.Op1) {
                if (player.geNewOfferType == OfferType.BUY.code) {
                    chooseBuyItem()
                } else {
                    mes("Choose the item to sell from your inventory.")
                }
            }
            return
        }
        if (selected <= 0) {
            return
        }
        val type = OfferType.fromCode(player.geNewOfferType)
        when (child) {
            CHILD_QTY_MINUS -> if (op == IfButtonOp.Op1) stepQuantity(-1)
            CHILD_QTY_PLUS_SMALL, CHILD_QTY_PLUS_1 -> if (op == IfButtonOp.Op1) stepQuantity(1)
            CHILD_QTY_PLUS_10 -> if (op == IfButtonOp.Op1) stepQuantity(10)
            CHILD_QTY_PLUS_100 -> if (op == IfButtonOp.Op1) stepQuantity(100)
            CHILD_QTY_MAX -> if (op == IfButtonOp.Op1) stepQuantity(Int.MAX_VALUE)
            CHILD_QTY_ENTER ->
                when (op) {
                    IfButtonOp.Op1 -> enterQuantity(type)
                    IfButtonOp.Op2 -> buyAllAffordable(type)
                    else -> Unit
                }
            CHILD_PRICE_MINUS ->
                if (op == IfButtonOp.Op1) changePrice("-1", OfferMath.stepPrice(price(), up = false))
            CHILD_PRICE_PLUS ->
                if (op == IfButtonOp.Op1) changePrice("+1", OfferMath.stepPrice(price(), up = true))
            CHILD_PRICE_MINUS_5 ->
                if (op == IfButtonOp.Op1) {
                    changePrice("-5%", OfferMath.stepPricePercent(price(), 5, up = false))
                }
            CHILD_PRICE_PLUS_5 ->
                if (op == IfButtonOp.Op1) {
                    changePrice("+5%", OfferMath.stepPricePercent(price(), 5, up = true))
                }
            CHILD_PRICE_GUIDE ->
                if (op == IfButtonOp.Op1) {
                    changePrice("guide", setup.marketPrice(selected, type))
                    setup.refreshPanel(player)
                }
            CHILD_PRICE_ENTER -> if (op == IfButtonOp.Op1) enterPrice()
            CHILD_PRICE_MINUS_X -> customPercent(op, up = false)
            CHILD_PRICE_PLUS_X -> customPercent(op, up = true)
        }
    }

    private fun ProtectedAccess.price(): Long = player.geOfferPrice.toLong()

    private fun ProtectedAccess.changePrice(source: String, price: Long) {
        setup.setPrice(player, price, source)
    }

    private fun ProtectedAccess.stepQuantity(delta: Int) {
        val type = OfferType.fromCode(player.geNewOfferType)
        val item = player.geSearchItem
        val before = player.geNewOfferQuantity
        player.geNewOfferQuantity =
            OfferMath.stepQuantity(
                current = before,
                delta = delta,
                type = type,
                available = available(item),
                isBond = item == BOND_ID && type == OfferType.BUY,
            )
        logger.debug {
            "GE quantity: player=${player.username} delta=$delta before=$before " +
                "after=${player.geNewOfferQuantity}"
        }
    }

    private suspend fun ProtectedAccess.enterQuantity(type: OfferType) {
        val title = if (type == OfferType.BUY) "How many do you wish to buy?" else "How many do you wish to sell?"
        val entered = countDialog(title)
        if (entered <= 0 || player.geSearchItem <= 0) {
            return
        }
        val cap = if (type == OfferType.SELL) available(player.geSearchItem) else Int.MAX_VALUE
        player.geNewOfferQuantity = entered.coerceIn(1, cap)
        setup.refreshPanel(player)
    }

    private fun ProtectedAccess.buyAllAffordable(type: OfferType) {
        if (type != OfferType.BUY) {
            return
        }
        player.geNewOfferQuantity = OfferMath.affordableQuantity(coinTotal(), price())
    }

    private suspend fun ProtectedAccess.enterPrice() {
        val entered = countDialog("Set a price for each item:")
        if (entered > 0 && player.geSearchItem > 0) {
            setup.setPrice(player, entered.toLong(), "enter")
            setup.refreshPanel(player)
        }
    }

    private suspend fun ProtectedAccess.customPercent(op: IfButtonOp, up: Boolean) {
        when (op) {
            IfButtonOp.Op1 -> {
                val percent = player.gePriceCustom
                if (percent > 0) {
                    val label = (if (up) "+" else "-") + percent + "%x"
                    setup.setPrice(player, OfferMath.stepPricePercent(price(), percent, up), label)
                    setup.refreshPanel(player)
                }
            }
            IfButtonOp.Op2 -> {
                val entered = countDialog("Set a percentage to change the price by (1-100):")
                if (entered in 1..MAX_CUSTOM_PERCENT) {
                    player.gePriceCustom = entered
                }
            }
            else -> Unit
        }
    }

    /** The inventory side panel: clicking an item while setting up a sell offer. */
    private fun ProtectedAccess.sideItemButton(slot: Int, op: IfButtonOp) {
        val held = inv[slot] ?: return
        if (op == IfButtonOp.Op10) {
            objExamine(inv, slot)
            return
        }
        if (op != IfButtonOp.Op1) {
            return
        }
        val session = sessions.of(player)
        val selected = player.geSelectedSlot - 1
        val choosing =
            selected in 0 until session.slotCount &&
                session.slot(selected).isEmpty &&
                session.box(selected).isEmpty() &&
                player.geNewOfferType == OfferType.SELL.code
        if (!choosing) {
            val free = firstFreeSlot(session)
            if (free == null) {
                mes("You have no free offer slot.")
                return
            }
            beginSetup(free, OfferType.SELL)
        }
        val info = catalog.resolve(held.id)
        if (info == null) {
            mes("You can't trade that item on the Grand Exchange.")
            return
        }
        val quantity = if (getInvObj(held).isStackable) held.count else 1
        selectItem(info.id, quantity.coerceAtMost(available(info.id)), price = null)
    }

    private fun ProtectedAccess.confirmOffer() {
        val slot = player.geSelectedSlot - 1
        val item = player.geSearchItem
        val price = player.geOfferPrice.toLong()
        val type = OfferType.fromCode(player.geNewOfferType)
        val quantity = player.geNewOfferQuantity
        logger.debug {
            "GE confirm: player=${player.username} slot=$slot type=$type item=$item " +
                "quantity=$quantity price=$price"
        }
        if (slot !in 0 until SlotCodec.SLOTS || item <= 0 || price <= 0) {
            logger.debug { "GE confirm ignored: no valid slot, item or price" }
            return
        }
        val session = sessions.of(player)
        when (val result = exchange.place(session, slot, type, item, quantity, price)) {
            is PlaceResult.Placed -> {
                logger.debug {
                    "GE placed: slot=$slot price=${result.slot.price} " +
                        "filled=${result.filledQuantity} state=${result.slot.state}"
                }
                player.geLastOfferItem = item
                player.geLastOfferQuantity = quantity
                player.geLastOfferPrice = price.toInt()
                player.geLastOfferType = type.code
                setup.view(player, slot, item, type)
            }
            is PlaceResult.Rejected -> {
                logger.debug { "GE place rejected: ${result.reason}" }
                mes(rejection(result.reason, type))
            }
        }
    }

    private fun rejection(reason: PlaceFailure, type: OfferType): String =
        when (reason) {
            PlaceFailure.INVALID_SLOT -> "You need to be a member to use that offer slot."
            PlaceFailure.SLOT_IN_USE -> "That offer slot is in use. Collect from it first."
            PlaceFailure.NOT_TRADEABLE -> "You can't trade that item on the Grand Exchange."
            PlaceFailure.INVALID_OFFER -> "That isn't a valid offer."
            PlaceFailure.TOO_EXPENSIVE -> "Too much money!"
            PlaceFailure.BUY_LIMIT -> "You've reached the buying limit for that item."
            PlaceFailure.NOT_ENOUGH_COINS -> "You don't have enough coins to make that offer."
            PlaceFailure.NOT_ENOUGH_ITEMS ->
                if (type == OfferType.SELL) "You don't have enough of that item to make that offer."
                else "You don't have enough coins to make that offer."
        }

    private fun ProtectedAccess.statusButton(child: Int) {
        val slot = player.geSelectedSlot - 1
        if (slot !in 0 until SlotCodec.SLOTS) {
            return
        }
        when (child) {
            0 -> abortSlot(slot)
            1 -> modifySlot(slot)
        }
    }

    /** The two item stacks under an offer: sub 2 is the item, sub 3 the coins. */
    private fun ProtectedAccess.detailsCollectButton(child: Int, op: IfButtonOp) {
        val slot = player.geSelectedSlot - 1
        if (slot !in 0 until SlotCodec.SLOTS) {
            return
        }
        collector.collectDisplayed(this, slot, isItem = child == DETAILS_ITEM_CHILD, op = op)
        leaveFreedSlot()
    }

    /** What the player holds of an item for sale, counting noted copies. */
    private fun ProtectedAccess.available(itemId: Int): Int {
        if (itemId <= 0) {
            return 1
        }
        val noted = catalog.notedId(itemId)
        val total = inv.totalOf(itemId) + (if (noted != null) inv.totalOf(noted) else 0L)
        return total.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun ProtectedAccess.coinTotal(): Long =
        inv.totalOf(GeIds.coins) + inv.totalOf(GeIds.platinum) * GePlayer.TOKEN_VALUE

    private companion object {
        const val NO_ITEM = -1
        const val BOND_ID = 13190
        const val MAX_CUSTOM_PERCENT = 100
        const val DESC_SCRIPT = 5730

        const val CHILD_VIEW = 2
        const val CHILD_BUY = 3
        const val CHILD_SELL = 4

        const val CHILD_ITEM_BOX = 0
        const val CHILD_QTY_MINUS = 1
        const val CHILD_QTY_PLUS_SMALL = 2
        const val CHILD_QTY_PLUS_1 = 3
        const val CHILD_QTY_PLUS_10 = 4
        const val CHILD_QTY_PLUS_100 = 5
        const val CHILD_QTY_MAX = 6
        const val CHILD_QTY_ENTER = 7
        const val CHILD_PRICE_MINUS = 8
        const val CHILD_PRICE_PLUS = 9
        const val CHILD_PRICE_MINUS_5 = 10
        const val CHILD_PRICE_GUIDE = 11
        const val CHILD_PRICE_ENTER = 12
        const val CHILD_PRICE_PLUS_5 = 13
        const val CHILD_PRICE_MINUS_X = 14
        const val CHILD_PRICE_PLUS_X = 15
        const val SETUP_LAST_CHILD = 15

        const val DETAILS_ITEM_CHILD = 2
    }
}
