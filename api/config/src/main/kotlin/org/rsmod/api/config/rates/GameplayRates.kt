package org.rsmod.api.config.rates

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Live gameplay tuning values, swapped atomically when `game.yml` is reloaded or a scheduled event
 * starts or ends. Defaults match OSRS, so an unconfigured server behaves exactly as before.
 */
public object GameplayRates {
    @Volatile
    public var current: GameplayRateValues = GameplayRateValues()
        private set

    @Volatile
    public var events: List<ScheduledGameEvent> = emptyList()
        private set

    public fun install(values: GameplayRateValues) {
        current = values
    }

    public fun installEvents(scheduled: List<ScheduledGameEvent>) {
        events = scheduled
    }

    public fun skillXp(stat: String): Double = current.skillXpMultiplier(stat)

    public fun scaleTicks(ticks: Int, multiplier: Double): Int =
        if (multiplier == 1.0) ticks else max(1, (ticks * multiplier).roundToInt())
}

public data class GameplayRateValues(
    val skillXp: Map<String, Double> = emptyMap(),
    val healthIntervalTicks: Int = 100,
    val healthAmount: Int = 1,
    val statRestoreIntervalTicks: Int = 100,
    val boostDecayIntervalTicks: Int = 100,
    val runRestoreMultiplier: Double = 1.0,
    val runDrainMultiplier: Double = 1.0,
    val prayerDrainMultiplier: Double = 1.0,
    val prayerRegenAmount: Int = 0,
    val specialAttackIntervalTicks: Int = 50,
    val specialAttackAmount: Int = 100,
    val npcRegenMultiplier: Double = 1.0,
    val skillingSuccessMultiplier: Double = 1.0,
    val resourceRespawnMultiplier: Double = 1.0,
    val resourceDepleteMultiplier: Double = 1.0,
    val playerDamageMultiplier: Double = 1.0,
    val npcDamageMultiplier: Double = 1.0,
    val npcAggression: Boolean = true,
    val npcRespawnMultiplier: Double = 1.0,
    val lootVisibleTicks: Int = 200,
    val shopRestockMultiplier: Double = 1.0,
    val eventGlobalXp: Double = 1.0,
    val eventSkillXp: Map<String, Double> = emptyMap(),
    val eventDropMultiplier: Double = 1.0,
    val activeEvents: List<String> = emptyList(),
) {
    public fun skillXpMultiplier(stat: String): Double =
        (skillXp[stat] ?: 1.0) * eventGlobalXp * (eventSkillXp[stat] ?: 1.0)
}
