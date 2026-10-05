package org.rsmod.content.areas.city.grandexchange

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.game.entity.Player

/** One family's inputs and outputs, applied to the backpack as a single all-or-nothing change. */
internal class DoseTransfer(private val holding: DoseHolding) {
    var coins = 0
    var spareVessels = 0
    var surplusVessels = 0
    var plan: List<DoseCount> = emptyList()

    fun apply(player: Player): Boolean {
        if (!holding.hasNoted && apply(player, notedOutput = false)) {
            return true
        }
        return apply(player, notedOutput = true)
    }

    private fun apply(player: Player, notedOutput: Boolean): Boolean {
        val family = holding.family
        val results =
            player.invTransaction(player.inv, autoCommit = false) {
                val inv = select(player.inv)
                for (doses in 1..family.capacity) {
                    val item = family.item(doses)
                    val plain = holding.unnotedCount(doses).toInt()
                    if (plain > 0) {
                        delete(inv, item.id, plain)
                    }
                    val noted = holding.notedCount(doses).toInt()
                    if (noted > 0) {
                        delete(inv, item.certlink, noted)
                    }
                }
                val empty = family.empty
                if (empty != null && spareVessels > 0) {
                    val plain = minOf(spareVessels, player.inv.count(empty.internalName))
                    if (plain > 0) {
                        delete(inv, empty.id, plain)
                    }
                    if (spareVessels > plain) {
                        delete(inv, empty.certlink, spareVessels - plain)
                    }
                }
                if (coins > 0) {
                    delete(inv, COINS_ID, coins)
                }
                for (planned in plan) {
                    val item = family.item(planned.doses)
                    add(inv, item.id, planned.count.toInt(), cert = notedOutput)
                }
                if (empty != null && surplusVessels > 0) {
                    add(inv, empty.id, surplusVessels, cert = notedOutput)
                }
            }
        if (!results.success) {
            return false
        }
        results.commitAll()
        return true
    }

    private companion object {
        val COINS_ID: Int by lazy { "obj.coins".asRSCM(RSCMType.OBJ) }
    }
}
