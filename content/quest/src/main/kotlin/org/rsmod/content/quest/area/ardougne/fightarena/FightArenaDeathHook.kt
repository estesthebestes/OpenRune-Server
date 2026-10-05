package org.rsmod.content.quest.area.ardougne.fightarena

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops.Companion.DROP_DURATION_STANDARD
import org.rsmod.api.death.PlayerDeathDrops.Companion.PVP_REVEAL_DELAY
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling

/**
 * A player who falls inside their own private arena keeps everything they carry and wear. The
 * server has no gravestones, so anything dropped there would vanish with the instance.
 */
class FightArenaDeathHook @Inject constructor(private val site: ArenaSite) : PlayerDeathHook {

    override val priority: Int
        get() = Priority

    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!site.isInside(context.player)) {
            return null
        }
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = context.player,
            dropDuration = DROP_DURATION_STANDARD,
            revealDelay = PVP_REVEAL_DELAY,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
            keepInventory = true,
        )
    }

    private companion object {
        const val Priority = 100
    }
}
