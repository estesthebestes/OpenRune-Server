package org.rsmod.content.interfaces.grandexchange

import org.rsmod.api.grandexchange.engine.FillNotice
import org.rsmod.api.grandexchange.engine.GeParticipant
import org.rsmod.api.grandexchange.engine.HistoryEntry
import org.rsmod.api.grandexchange.offer.CollectionBox
import org.rsmod.api.grandexchange.offer.OfferSlot
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.grandexchange.rules.GeItemCatalog
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.StockMarket
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.entity.Player

/** A player's side of the exchange: slot records in varps, collection boxes in inventories. */
internal class GePlayer(val player: Player, private val catalog: GeItemCatalog) : GeParticipant {
    private val boxes = Array(SlotCodec.SLOTS) { index ->
        InvCollectionBox { player.invMap.getOrPut(GeIds.boxInvs[index]) }
    }

    override val id: Long
        get() = player.userId

    override val slotCount: Int
        get() = if (player.members) MEMBER_SLOTS else FREE_SLOTS

    override fun slot(index: Int): OfferSlot {
        val tax = player.vars.backing[GeIds.taxVarps[index].id].toLong()
        return SlotCodec.read(readWords(), index, tax)
    }

    override fun setSlot(index: Int, slot: OfferSlot) {
        val words = readWords()
        SlotCodec.write(words, index, slot)
        for (word in SlotCodec.wordsOf(index)) {
            val varp = GeIds.slotWords[word]
            if (player.vars.backing[varp.id] != words[word]) {
                VarPlayerIntMapSetter.set(player, varp, words[word])
            }
        }
        VarPlayerIntMapSetter.set(player, GeIds.taxVarps[index], SlotCodec.clampTax(slot.tax))
    }

    override fun box(index: Int): CollectionBox = boxes[index]

    override fun takeCoins(amount: Long): Boolean {
        val inv = player.inv
        val loose = inv.totalOf(GeIds.coins)
        val tokens = inv.totalOf(GeIds.platinum)
        if (amount <= 0 || loose + tokens * TOKEN_VALUE < amount) {
            return false
        }
        val fromCoins = minOf(loose, amount)
        val remainder = amount - fromCoins
        val tokensNeeded = (remainder + TOKEN_VALUE - 1) / TOKEN_VALUE
        val change = tokensNeeded * TOKEN_VALUE - remainder
        if (fromCoins > Int.MAX_VALUE || tokensNeeded > Int.MAX_VALUE) {
            return false
        }
        val result =
            player.invTransaction(inv) {
                val target = select(inv)
                if (fromCoins > 0) {
                    delete(target, GeIds.coins, fromCoins.toInt())
                }
                if (tokensNeeded > 0) {
                    delete(target, GeIds.platinum, tokensNeeded.toInt())
                }
                if (change > 0) {
                    add(target, GeIds.coins, change.toInt())
                }
            }
        return result.success
    }

    override fun takeItems(itemId: Int, count: Int): Boolean {
        if (count <= 0) {
            return false
        }
        val inv = player.inv
        val noted = catalog.notedId(itemId)
        val plain = inv.totalOf(itemId)
        val notedHeld = if (noted != null) inv.totalOf(noted) else 0L
        if (plain + notedHeld < count) {
            return false
        }
        val fromPlain = minOf(plain, count.toLong()).toInt()
        val fromNoted = count - fromPlain
        val result =
            player.invTransaction(inv) {
                val target = select(inv)
                if (fromPlain > 0) {
                    delete(target, itemId, fromPlain)
                }
                if (fromNoted > 0 && noted != null) {
                    delete(target, noted, fromNoted)
                }
            }
        return result.success
    }

    override fun onSlotChanged(index: Int) {
        pushSlot(index)
    }

    override fun onFill(notice: FillNotice) {
        val name = catalog.name(notice.itemId)
        val buying = notice.type == OfferType.BUY
        if (notice.finished) {
            val verb = if (buying) "buying" else "selling"
            player.mes("Grand Exchange: Finished $verb ${notice.quantity.formatAmount} x $name.")
            player.midiJingle(TRADE_JINGLE)
        } else {
            val verb = if (buying) "Bought" else "Sold"
            val progress = "${notice.completedQuantity.formatAmount} / ${notice.quantity.formatAmount}"
            player.mes("Grand Exchange: $verb $progress x $name.")
        }
    }

    override fun recordHistory(entry: HistoryEntry) {
        GeHistoryStore.push(player, entry)
    }

    /** Sends one slot as the client keeps it, plus the fee total the status panel reads. */
    fun pushSlot(index: Int) {
        val slot = slot(index)
        if (slot.isEmpty) {
            StockMarket.resetSlot(player, index)
        } else {
            StockMarket.setSlot(
                player = player,
                slot = index,
                status = clientStatus(slot),
                obj = slot.itemId,
                price = slot.price.toLong(),
                count = slot.quantity,
                completedCount = slot.completedQuantity,
                completedGold = slot.completedGold,
            )
        }
        StockMarket.writeVarpLong(player, GeIds.taxVarps[index].id, slot.tax)
    }

    fun pushAllSlots() {
        for (index in 0 until SlotCodec.SLOTS) {
            pushSlot(index)
        }
    }

    private fun readWords(): IntArray =
        IntArray(SlotCodec.WORDS) { player.vars.backing[GeIds.slotWords[it].id] }

    private fun clientStatus(slot: OfferSlot): Int {
        val state =
            when (slot.state) {
                OfferState.OPEN -> StockMarket.STATE_OPEN
                OfferState.COMPLETED -> StockMarket.STATE_COMPLETED
                OfferState.ABORTED -> StockMarket.STATE_ABORTED
                OfferState.EMPTY -> 0
            }
        return if (slot.type == OfferType.SELL) state or StockMarket.SELL_FLAG else state
    }

    companion object {
        const val FREE_SLOTS = 3
        const val MEMBER_SLOTS = 8
        const val TOKEN_VALUE = 1000L
        const val TRADE_JINGLE = "jingle.grand_exchange_trade_jingle"
    }
}
