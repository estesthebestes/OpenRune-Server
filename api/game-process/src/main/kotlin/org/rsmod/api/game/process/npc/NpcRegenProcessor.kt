package org.rsmod.api.game.process.npc

import kotlin.math.max
import kotlin.math.sign
import org.rsmod.api.config.rates.GameplayRates
import org.rsmod.game.entity.Npc

public class NpcRegenProcessor {
    public fun process(npc: Npc) {
        npc.processStatsRegen()
    }

    private fun Npc.processStatsRegen() {
        if (regenRate == 0) {
            return
        }

        if (regenClock == 0) {
            val multiplier = GameplayRates.current.npcRegenMultiplier
            regenClock = if (multiplier == 1.0) regenRate else max(1, (regenRate / multiplier).toInt())
            regenStats()
            return
        }

        regenClock--
    }

    private fun Npc.regenStats() {
        attackLvl = attackLvl.stepToward(baseAttackLvl)
        strengthLvl = strengthLvl.stepToward(baseStrengthLvl)
        defenceLvl = defenceLvl.stepToward(baseDefenceLvl)
        hitpoints = hitpoints.stepToward(baseHitpointsLvl)
        rangedLvl = rangedLvl.stepToward(baseRangedLvl)
        magicLvl = magicLvl.stepToward(baseMagicLvl)
    }

    private fun Int.stepToward(target: Int) = this + (target - this).sign
}
