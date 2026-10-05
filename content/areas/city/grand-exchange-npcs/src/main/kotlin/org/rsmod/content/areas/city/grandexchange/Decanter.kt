package org.rsmod.content.areas.city.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.market.MarketPrices
import org.rsmod.game.entity.Player

internal data class DecantResult(
    val found: Boolean,
    val decanted: Int = 0,
    val unchanged: Int = 0,
    val shortOfVessels: Int = 0,
    val shortOfSpace: Int = 0,
) {
    val shortOfAnything: Boolean
        get() = shortOfVessels > 0 || shortOfSpace > 0
}

/**
 * Redistributes the doses of every potion family in the backpack into containers of one size. Spare
 * containers are used first, then bought from the decanter at the market price plus a small fee.
 */
@Singleton
internal class Decanter @Inject constructor(private val prices: MarketPrices) {
    fun containerPrice(container: String): Int {
        val type = checkNotNull(ServerCacheManager.getItem(container.asRSCM(RSCMType.OBJ)))
        return (prices[type] ?: type.cost).coerceAtLeast(1) + CONTAINER_FEE
    }

    fun decant(player: Player, target: Int): DecantResult {
        val holdings = player.inv.holdings(DoseFamilies.decantable)
        if (holdings.isEmpty()) {
            return DecantResult(found = false)
        }
        var decanted = 0
        var unchanged = 0
        var vessels = 0
        var space = 0
        for (holding in holdings.values) {
            when (decant(player, holding, target)) {
                Step.Done -> decanted++
                Step.Unchanged -> unchanged++
                Step.NoVessels -> vessels++
                Step.NoSpace -> space++
            }
        }
        return DecantResult(true, decanted, unchanged, vessels, space)
    }

    private enum class Step {
        Done,
        Unchanged,
        NoVessels,
        NoSpace,
    }

    private fun decant(player: Player, holding: DoseHolding, target: Int): Step {
        val family = holding.family
        val plan = DoseMath.redistribute(holding.totalDoses, minOf(target, family.capacity))
        if (DoseMath.matches(plan, holding.held())) {
            return Step.Unchanged
        }
        val containersOut = DoseMath.containers(plan)
        val extra = containersOut - holding.containers
        val transfer = DoseTransfer(holding)
        transfer.plan = plan
        val empty = checkNotNull(family.empty)
        if (extra > 0) {
            val spare = minOf(spareVessels(player, holding), extra).toInt()
            val bought = (extra - spare).toInt()
            val cost = bought.toLong() * containerPrice(empty.internalName)
            if (cost > player.inv.count("obj.coins")) {
                return Step.NoVessels
            }
            transfer.spareVessels = spare
            transfer.coins = cost.toInt()
        } else {
            transfer.surplusVessels = (-extra).toInt()
        }
        return if (transfer.apply(player)) Step.Done else Step.NoSpace
    }

    private fun spareVessels(player: Player, holding: DoseHolding): Long {
        val empty = checkNotNull(holding.family.empty)
        val plain = player.inv.count(empty.internalName).toLong()
        val noted = if (empty.canCert) player.inv.count(noteName(empty.certlink)).toLong() else 0L
        return plain + noted
    }

    private fun noteName(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)

    private companion object {
        const val CONTAINER_FEE = 2
    }
}
