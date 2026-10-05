package org.rsmod.content.areas.city.grandexchange

import org.rsmod.game.entity.Player

internal enum class CombineOutcome {
    NothingCarried,
    NothingToCombine,
    NoSpace,
    Done,
}

/** Pools the charges of every teleport jewellery family in the backpack into the fewest items. */
internal object JewelleryCombiner {
    fun combine(player: Player): CombineOutcome {
        val holdings = player.inv.holdings(DoseFamilies.jewellery)
        if (holdings.isEmpty()) {
            return CombineOutcome.NothingCarried
        }
        var combined = 0
        var blocked = 0
        for (holding in holdings.values) {
            val plan = DoseMath.redistribute(holding.totalDoses, holding.family.capacity)
            if (DoseMath.matches(plan, holding.held())) {
                continue
            }
            val transfer = DoseTransfer(holding)
            transfer.plan = plan
            if (transfer.apply(player)) combined++ else blocked++
        }
        return when {
            blocked > 0 && combined == 0 -> CombineOutcome.NoSpace
            combined > 0 -> CombineOutcome.Done
            else -> CombineOutcome.NothingToCombine
        }
    }
}
