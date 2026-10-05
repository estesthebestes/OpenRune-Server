package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2.SetStockMarketSlot
import net.rsprot.protocol.game.outgoing.varp.VarpLong
import org.rsmod.game.entity.Player

/**
 * Grand Exchange slot state as the client keeps it. The offers interface reads it back through the
 * `stockmarket_*` clientscript commands, so it has to be sent for every slot at login and whenever
 * a slot changes.
 *
 * The V2 packet is used because the 241 client scripts read the price and the gold received as
 * 64-bit values.
 */
public object StockMarket {
    /** Low three bits of the status: the state of the offer. */
    public const val STATE_PENDING: Int = 1
    public const val STATE_OPEN: Int = 2
    public const val STATE_COMPLETED: Int = 4
    public const val STATE_ABORTED: Int = 5

    /** Bit set on the status of a sell offer. */
    public const val SELL_FLAG: Int = 8

    /** @see [UpdateStockMarketSlotV2] */
    public fun setSlot(
        player: Player,
        slot: Int,
        status: Int,
        obj: Int,
        price: Long,
        count: Int,
        completedCount: Int,
        completedGold: Long,
    ) {
        val update = SetStockMarketSlot(status, obj, price, count, completedCount, completedGold)
        player.client.write(UpdateStockMarketSlotV2(slot, update))
    }

    /**
     * Empties [slot] on the client.
     *
     * This sends an offer in state 0 rather than a `ResetStockMarketSlot`: rsprot's V2 encoder writes
     * the reset with a hard-coded slot of 0, so resetting any other slot would instead wipe slot 0.
     */
    public fun resetSlot(player: Player, slot: Int) {
        setSlot(player, slot, status = 0, obj = 0, price = 0, count = 0, completedCount = 0, completedGold = 0)
    }

    /**
     * Writes a client varp whose type is `long`. These cannot go through the int varp store, so the
     * caller keeps the authoritative value itself.
     */
    public fun writeVarpLong(player: Player, varp: Int, value: Long) {
        player.client.write(VarpLong(varp, value))
    }
}
