package org.rsmod.api.grandexchange.engine

import org.rsmod.api.grandexchange.book.BookEntry
import org.rsmod.api.grandexchange.book.OrderBook
import org.rsmod.api.grandexchange.fill.FillModel
import org.rsmod.api.grandexchange.fill.InstantFillModel
import org.rsmod.api.grandexchange.fill.MarketOffer
import org.rsmod.api.grandexchange.offer.OfferSlot
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.price.MarketQuoteSource
import org.rsmod.api.grandexchange.rules.BuyLimits
import org.rsmod.api.grandexchange.rules.GeItemCatalog
import org.rsmod.api.grandexchange.rules.GeTax
import org.rsmod.api.grandexchange.rules.NoBuyLimits

/**
 * The Grand Exchange: offer lifecycle, player-to-player matching and trading with the simulated
 * market.
 *
 * A new offer first meets opposing offers of other online players (price-time priority; the trade
 * happens at the price of the offer that was already resting, as on OSRS). Whatever is left then
 * trades against the market as far as the [FillModel] allows, and the rest rests in the book.
 *
 * The book only knows offers of players who are online ([join] on login, [leave] on logout). Every
 * mutation runs on the game thread, in one synchronous step that updates both sides, so nothing is
 * ever created or destroyed outside of escrow, fills and the fee.
 */
public class GrandExchange(
    private val catalog: GeItemCatalog,
    private val quotes: MarketQuoteSource,
    private val tax: GeTax,
    private val limits: BuyLimits = NoBuyLimits,
    private val fillModel: FillModel = InstantFillModel,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val book = OrderBook()
    private val online = HashMap<Long, GeParticipant>()

    public val taxRatePermille: Int
        get() = tax.ratePermille

    public val openOfferCount: Int
        get() = book.size

    public fun isOnline(participantId: Long): Boolean = online.containsKey(participantId)

    /** Puts the player's open offers back in the book and lets them trade against what is there. */
    public fun join(player: GeParticipant) {
        online[player.id] = player
        for (index in 0 until MAX_SLOTS) {
            val slot = player.slot(index)
            if (slot.isOpen) {
                book.add(player.id, index, slot.itemId, slot.type, slot.price.toLong())
            }
        }
        for (index in 0 until MAX_SLOTS) {
            if (player.slot(index).isOpen) {
                tradeSlot(player, index)
            }
        }
    }

    /** Takes the player's offers out of the book. Their slots keep the offers for the next login. */
    public fun leave(player: GeParticipant) {
        book.removeOwner(player.id)
        online.remove(player.id)
    }

    public fun place(
        owner: GeParticipant,
        index: Int,
        type: OfferType,
        itemId: Int,
        quantity: Int,
        price: Long,
    ): PlaceResult {
        if (index !in 0 until owner.slotCount) {
            return PlaceResult.Rejected(PlaceFailure.INVALID_SLOT)
        }
        if (!owner.slot(index).isEmpty || !owner.box(index).isEmpty()) {
            return PlaceResult.Rejected(PlaceFailure.SLOT_IN_USE)
        }
        val info = catalog.resolve(itemId) ?: return PlaceResult.Rejected(PlaceFailure.NOT_TRADEABLE)
        if (quantity <= 0 || price !in 1..Int.MAX_VALUE.toLong()) {
            return PlaceResult.Rejected(PlaceFailure.INVALID_OFFER)
        }
        val total = quantity.toLong() * price
        if (total > OfferSlot.MAX_TOTAL) {
            return PlaceResult.Rejected(PlaceFailure.TOO_EXPENSIVE)
        }
        if (type == OfferType.BUY && !limits.canBuy(owner.id, info.id, quantity, clock())) {
            return PlaceResult.Rejected(PlaceFailure.BUY_LIMIT)
        }
        val escrowed =
            when (type) {
                OfferType.BUY -> owner.takeCoins(total)
                OfferType.SELL -> owner.takeItems(info.id, quantity)
            }
        if (!escrowed) {
            val reason =
                if (type == OfferType.BUY) PlaceFailure.NOT_ENOUGH_COINS
                else PlaceFailure.NOT_ENOUGH_ITEMS
            return PlaceResult.Rejected(reason)
        }

        online[owner.id] = owner
        owner.setSlot(index, OfferSlot(OfferState.OPEN, type, info.id, quantity, price.toInt()))
        book.add(owner.id, index, info.id, type, price)
        tradeSlot(owner, index)
        val placed = owner.slot(index)
        owner.onSlotChanged(index)
        return PlaceResult.Placed(placed, placed.completedQuantity)
    }

    public fun abort(owner: GeParticipant, index: Int): AbortResult {
        if (index !in 0 until MAX_SLOTS) {
            return AbortResult.NOT_OPEN
        }
        val slot = owner.slot(index)
        if (!slot.isOpen) {
            return AbortResult.NOT_OPEN
        }
        book.remove(owner.id, index)
        val remaining = slot.remaining
        val box = owner.box(index)
        when (slot.type) {
            OfferType.BUY -> box.addCoins(remaining.toLong() * slot.price)
            OfferType.SELL -> box.addItems(slot.itemId, remaining)
        }
        val aborted = slot.copy(state = OfferState.ABORTED)
        owner.setSlot(index, aborted)
        if (aborted.completedQuantity > 0) {
            owner.recordHistory(historyOf(aborted))
        }
        owner.onSlotChanged(index)
        return AbortResult.ABORTED
    }

    /**
     * Moves the box contents into [sink] as far as it accepts them, then frees the slot once the
     * offer is finished and the box is empty.
     */
    public fun collect(
        owner: GeParticipant,
        index: Int,
        part: CollectPart,
        noted: Boolean,
        sink: CollectSink,
    ) {
        if (index !in 0 until MAX_SLOTS) {
            return
        }
        val box = owner.box(index)
        if (part != CollectPart.COINS && box.itemCount > 0) {
            val offered = box.itemCount
            val taken = sink.acceptItems(box.itemId, offered, noted).coerceIn(0, offered)
            box.removeItems(taken)
        }
        if (part != CollectPart.ITEMS && box.coins > 0) {
            val offered = box.coins
            val taken = sink.acceptCoins(offered).coerceIn(0L, offered)
            box.removeCoins(taken)
        }
        settle(owner, index)
    }

    /** Frees a finished slot whose collection box is empty. */
    public fun settle(owner: GeParticipant, index: Int) {
        val slot = owner.slot(index)
        if (slot.state.isFinished && owner.box(index).isEmpty()) {
            owner.setSlot(index, OfferSlot.EMPTY)
            owner.onSlotChanged(index)
        }
    }

    /** Re-checks every resting offer against the market. Call once per cycle. */
    public fun sweep(tick: Long, pricesChanged: Boolean) {
        if (!fillModel.shouldSweep(pricesChanged, tick)) {
            return
        }
        for (entry in book.all()) {
            val owner = online[entry.ownerId] ?: continue
            if (owner.slot(entry.slotIndex).isOpen) {
                tradeWithMarket(owner, entry.slotIndex)
                owner.onSlotChanged(entry.slotIndex)
            }
        }
    }

    private fun tradeSlot(owner: GeParticipant, index: Int) {
        tradeWithPlayers(owner, index)
        tradeWithMarket(owner, index)
    }

    private fun tradeWithPlayers(taker: GeParticipant, takerIndex: Int) {
        for (resting in book.opposing(taker.slot(takerIndex).itemId, taker.slot(takerIndex).type)) {
            val takerSlot = taker.slot(takerIndex)
            if (!takerSlot.isOpen) {
                return
            }
            if (resting.ownerId == taker.id) {
                continue
            }
            if (!crosses(takerSlot, resting)) {
                return
            }
            val maker = online[resting.ownerId]
            if (maker == null) {
                book.remove(resting.ownerId, resting.slotIndex)
                continue
            }
            val makerSlot = maker.slot(resting.slotIndex)
            if (!makerSlot.isOpen) {
                book.remove(resting.ownerId, resting.slotIndex)
                continue
            }
            val price = resting.price
            val quantity =
                minOf(takerSlot.remaining, makerSlot.remaining, goldRoom(takerSlot, price), goldRoom(makerSlot, price))
            if (quantity <= 0) {
                continue
            }
            val buyer = if (takerSlot.type == OfferType.BUY) taker to takerIndex else maker to resting.slotIndex
            val seller = if (takerSlot.type == OfferType.SELL) taker to takerIndex else maker to resting.slotIndex
            fillBuyer(buyer.first, buyer.second, quantity, price)
            fillSeller(seller.first, seller.second, quantity, price)
        }
    }

    private fun tradeWithMarket(owner: GeParticipant, index: Int) {
        val slot = owner.slot(index)
        if (!slot.isOpen) {
            return
        }
        val fill =
            fillModel.marketFill(
                MarketOffer(slot.type, slot.itemId, slot.price.toLong(), slot.quantity, slot.remaining),
                quotes.quote(slot.itemId),
            ) ?: return
        val quantity = minOf(fill.quantity, slot.remaining, goldRoom(slot, fill.price))
        if (quantity <= 0) {
            return
        }
        when (slot.type) {
            OfferType.BUY -> fillBuyer(owner, index, quantity, fill.price)
            OfferType.SELL -> fillSeller(owner, index, quantity, fill.price)
        }
    }

    private fun crosses(taker: OfferSlot, resting: BookEntry): Boolean =
        when (taker.type) {
            OfferType.BUY -> taker.price >= resting.price
            OfferType.SELL -> taker.price <= resting.price
        }

    /** How many items still fit at [price] before the slot's gold total would pass the cap. */
    private fun goldRoom(slot: OfferSlot, price: Long): Int {
        val room = (OfferSlot.MAX_TOTAL - slot.completedGold) / price
        return room.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun fillBuyer(owner: GeParticipant, index: Int, quantity: Int, unitPrice: Long) {
        val slot = owner.slot(index)
        check(slot.isOpen && slot.type == OfferType.BUY && quantity in 1..slot.remaining) {
            "Invalid buy fill: $quantity of $slot"
        }
        check(unitPrice <= slot.price) { "Buy fill at $unitPrice is above the offer price ${slot.price}" }
        val box = owner.box(index)
        box.addItems(slot.itemId, quantity)
        val refund = (slot.price - unitPrice) * quantity
        if (refund > 0) {
            box.addCoins(refund)
        }
        limits.recordBuy(owner.id, slot.itemId, quantity, clock())
        val next =
            advance(slot, quantity, gross = unitPrice * quantity, fee = 0L)
        commit(owner, index, next, quantity, unitPrice)
    }

    private fun fillSeller(owner: GeParticipant, index: Int, quantity: Int, unitPrice: Long) {
        val slot = owner.slot(index)
        check(slot.isOpen && slot.type == OfferType.SELL && quantity in 1..slot.remaining) {
            "Invalid sell fill: $quantity of $slot"
        }
        val gross = unitPrice * quantity
        val fee = tax.total(unitPrice, quantity, catalog.resolve(slot.itemId)?.taxExempt ?: false)
        owner.box(index).addCoins(gross - fee)
        commit(owner, index, advance(slot, quantity, gross, fee), quantity, unitPrice)
    }

    private fun advance(slot: OfferSlot, quantity: Int, gross: Long, fee: Long): OfferSlot {
        val completed = slot.completedQuantity + quantity
        return slot.copy(
            state = if (completed == slot.quantity) OfferState.COMPLETED else OfferState.OPEN,
            completedQuantity = completed,
            completedGold = slot.completedGold + gross,
            tax = slot.tax + fee,
        )
    }

    private fun commit(owner: GeParticipant, index: Int, next: OfferSlot, filled: Int, unitPrice: Long) {
        owner.setSlot(index, next)
        val finished = next.state == OfferState.COMPLETED
        if (finished) {
            book.remove(owner.id, index)
            owner.recordHistory(historyOf(next))
        }
        owner.onFill(
            FillNotice(
                slotIndex = index,
                type = next.type,
                itemId = next.itemId,
                filled = filled,
                unitPrice = unitPrice,
                completedQuantity = next.completedQuantity,
                quantity = next.quantity,
                finished = finished,
            )
        )
        owner.onSlotChanged(index)
    }

    private fun historyOf(slot: OfferSlot): HistoryEntry =
        HistoryEntry(slot.itemId, slot.type, slot.completedQuantity, slot.completedGold, slot.tax)

    public companion object {
        public const val MAX_SLOTS: Int = 8
    }
}
