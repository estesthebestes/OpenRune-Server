package org.rsmod.content.interfaces.grandexchange

import org.rsmod.api.grandexchange.offer.CollectionBox
import org.rsmod.game.inv.Inventory

/**
 * A collection box kept in a persistent inventory the offers interface listens to: the item in
 * slot 0, coins in slot 1 and platinum tokens (1,000 coins each) in slot 2. Coins only spill into
 * tokens once they no longer fit a coin stack, which is what the client scripts expect.
 */
internal class InvCollectionBox(private val inv: () -> Inventory) : CollectionBox {
    override val itemId: Int
        get() = inv()[ITEM_SLOT]?.id ?: 0

    override val itemCount: Int
        get() = inv()[ITEM_SLOT]?.count ?: 0

    override val coins: Long
        get() {
            val box = inv()
            val loose = box[COIN_SLOT]?.count ?: 0
            val tokens = box[TOKEN_SLOT]?.count ?: 0
            return loose + tokens * COINS_PER_TOKEN
        }

    override fun addItems(itemId: Int, count: Int) {
        require(count >= 0) { "count=$count" }
        if (count == 0) {
            return
        }
        val box = inv()
        val held = box[ITEM_SLOT]
        check(held == null || held.id == itemId) {
            "A collection box holds one kind of item (has ${held?.id}, adding $itemId)"
        }
        box[ITEM_SLOT] = invObj(itemId, Math.addExact(held?.count ?: 0, count))
    }

    override fun addCoins(amount: Long) {
        require(amount >= 0) { "amount=$amount" }
        if (amount > 0) {
            storeCoins(Math.addExact(coins, amount))
        }
    }

    override fun removeItems(count: Int) {
        val box = inv()
        val held = box[ITEM_SLOT]
        require(held != null && count in 0..held.count) { "count=$count held=${held?.count}" }
        val left = held.count - count
        box[ITEM_SLOT] = if (left == 0) null else invObj(held.id, left)
    }

    override fun removeCoins(amount: Long) {
        val held = coins
        require(amount in 0..held) { "amount=$amount held=$held" }
        storeCoins(held - amount)
    }

    private fun storeCoins(total: Long) {
        val box = inv()
        val loose: Long
        val tokens: Long
        if (total <= Int.MAX_VALUE) {
            loose = total
            tokens = 0
        } else {
            loose = total % COINS_PER_TOKEN
            tokens = total / COINS_PER_TOKEN
        }
        box[COIN_SLOT] = if (loose > 0) invObj(GeIds.coins, loose.toInt()) else null
        box[TOKEN_SLOT] = if (tokens > 0) invObj(GeIds.platinum, tokens.toInt()) else null
    }

    companion object {
        const val ITEM_SLOT = 0
        const val COIN_SLOT = 1
        const val TOKEN_SLOT = 2
        const val COINS_PER_TOKEN = 1000L
    }
}
