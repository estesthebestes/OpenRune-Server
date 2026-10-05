package org.rsmod.api.grandexchange.offer

/**
 * What an offer slot has earned or got back and that the player has not collected yet: at most one
 * kind of item (always the unnoted item) plus coins.
 */
public interface CollectionBox {
    /** The item held, or `0` when there is none. */
    public val itemId: Int
    public val itemCount: Int
    public val coins: Long

    public fun isEmpty(): Boolean = itemCount == 0 && coins == 0L

    public fun addItems(itemId: Int, count: Int)

    public fun addCoins(amount: Long)

    public fun removeItems(count: Int)

    public fun removeCoins(amount: Long)
}

public class InMemoryCollectionBox : CollectionBox {
    override var itemId: Int = 0
        private set

    override var itemCount: Int = 0
        private set

    override var coins: Long = 0
        private set

    override fun addItems(itemId: Int, count: Int) {
        require(count >= 0) { "count=$count" }
        if (count == 0) {
            return
        }
        check(itemCount == 0 || this.itemId == itemId) {
            "A collection box holds one kind of item (has ${this.itemId}, adding $itemId)"
        }
        this.itemId = itemId
        itemCount = Math.addExact(itemCount, count)
    }

    override fun addCoins(amount: Long) {
        require(amount >= 0) { "amount=$amount" }
        coins = Math.addExact(coins, amount)
    }

    override fun removeItems(count: Int) {
        require(count in 0..itemCount) { "count=$count held=$itemCount" }
        itemCount -= count
        if (itemCount == 0) {
            itemId = 0
        }
    }

    override fun removeCoins(amount: Long) {
        require(amount in 0..coins) { "amount=$amount held=$coins" }
        coins -= amount
    }
}
