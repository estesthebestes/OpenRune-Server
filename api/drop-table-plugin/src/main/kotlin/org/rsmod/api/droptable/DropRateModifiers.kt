package org.rsmod.api.droptable

import dtx.impl.chance.RateBoosts
import org.rsmod.api.config.rates.GameplayRates
import org.rsmod.game.entity.Player

public object DropRateModifiers {
    @Volatile public var serverMultiplier: Double = 1.0

    @Volatile public var playerMultiplier: (Player) -> Double = { 1.0 }

    public val effectiveServerMultiplier: Double
        get() = serverMultiplier * GameplayRates.current.eventDropMultiplier

    public fun multiplierFor(player: Player): Double =
        effectiveServerMultiplier * playerMultiplier(player)

    public fun install() {
        RateBoosts.multiplier = { target, _ ->
            if (target is Player) multiplierFor(target) else effectiveServerMultiplier
        }
    }
}
