package org.rsmod.api.stats.plugin.rates

import java.time.LocalDateTime
import org.rsmod.api.config.rates.GameplayRateValues
import org.rsmod.api.config.rates.ScheduledGameEvent
import org.rsmod.api.server.config.GameEventYaml
import org.rsmod.api.server.config.GameplayConfig

internal object GameplayRateConfigs {
    fun statKey(name: String): String = if (name.startsWith("stat.")) name else "stat.$name"

    fun validate(gameplay: GameplayConfig, isKnownStat: (String) -> Boolean): List<String> {
        val errors = mutableListOf<String>()
        fun positive(key: String, value: Double) {
            if (value <= 0.0) errors += "$key must be above 0 (was $value)"
        }
        fun atLeast(key: String, value: Int, min: Int) {
            if (value < min) errors += "$key must be at least $min (was $value)"
        }
        fun skills(prefix: String, map: Map<String, Double>) {
            for ((name, value) in map) {
                if (!isKnownStat(statKey(name))) errors += "$prefix: unknown skill '$name'"
                positive("$prefix.$name", value)
            }
        }

        skills("xp.skill-multipliers", gameplay.xp.skillMultipliers)
        with(gameplay.regen) {
            atLeast("regen.health-interval-ticks", healthIntervalTicks, 1)
            atLeast("regen.health-amount", healthAmount, 0)
            atLeast("regen.stat-restore-interval-ticks", statRestoreIntervalTicks, 1)
            atLeast("regen.boost-decay-interval-ticks", boostDecayIntervalTicks, 1)
            positive("regen.run-restore-multiplier", runRestoreMultiplier)
            positive("regen.run-drain-multiplier", runDrainMultiplier)
            positive("regen.prayer-drain-multiplier", prayerDrainMultiplier)
            atLeast("regen.prayer-regen-amount", prayerRegenAmount, 0)
            atLeast("regen.special-attack-interval-ticks", specialAttackIntervalTicks, 1)
            atLeast("regen.special-attack-amount", specialAttackAmount, 0)
            positive("regen.npc-regen-multiplier", npcRegenMultiplier)
        }
        with(gameplay.skilling) {
            positive("skilling.success-multiplier", successMultiplier)
            positive("skilling.resource-respawn-multiplier", resourceRespawnMultiplier)
            if (resourceDepleteMultiplier < 0.0) {
                errors += "skilling.resource-deplete-multiplier must not be negative"
            }
        }
        with(gameplay.combat) {
            positive("combat.player-damage-multiplier", playerDamageMultiplier)
            positive("combat.npc-damage-multiplier", npcDamageMultiplier)
        }
        with(gameplay.world) {
            positive("world.npc-respawn-multiplier", npcRespawnMultiplier)
            atLeast("world.loot-visible-ticks", lootVisibleTicks, 1)
            positive("world.shop-restock-multiplier", shopRestockMultiplier)
        }
        for (event in gameplay.events) {
            val label = "events['${event.name}']"
            try {
                val parsed = parseEvent(event)
                if (!parsed.end.isAfter(parsed.start)) errors += "$label: end must be after start"
            } catch (e: IllegalArgumentException) {
                errors += "$label: ${e.message}"
            }
            positive("$label.global-xp", event.globalXp)
            positive("$label.drop-multiplier", event.dropMultiplier)
            skills("$label.skill-multipliers", event.skillMultipliers)
        }
        return errors
    }

    fun parseEvents(events: List<GameEventYaml>): List<ScheduledGameEvent> = events.map(::parseEvent)

    private fun parseEvent(event: GameEventYaml): ScheduledGameEvent =
        ScheduledGameEvent(
            name = event.name,
            start = ScheduledGameEvent.parseTime(event.start),
            end = ScheduledGameEvent.parseTime(event.end),
            globalXp = event.globalXp,
            dropMultiplier = event.dropMultiplier,
            skillXp = event.skillMultipliers.mapKeys { statKey(it.key) },
        )

    fun build(
        gameplay: GameplayConfig,
        events: List<ScheduledGameEvent>,
        now: LocalDateTime,
    ): GameplayRateValues {
        val active = events.filter { it.isActive(now) }
        val eventSkillXp = mutableMapOf<String, Double>()
        for (event in active) {
            for ((stat, value) in event.skillXp) {
                eventSkillXp[stat] = (eventSkillXp[stat] ?: 1.0) * value
            }
        }
        return GameplayRateValues(
            skillXp = gameplay.xp.skillMultipliers.mapKeys { statKey(it.key) },
            healthIntervalTicks = gameplay.regen.healthIntervalTicks,
            healthAmount = gameplay.regen.healthAmount,
            statRestoreIntervalTicks = gameplay.regen.statRestoreIntervalTicks,
            boostDecayIntervalTicks = gameplay.regen.boostDecayIntervalTicks,
            runRestoreMultiplier = gameplay.regen.runRestoreMultiplier,
            runDrainMultiplier = gameplay.regen.runDrainMultiplier,
            prayerDrainMultiplier = gameplay.regen.prayerDrainMultiplier,
            prayerRegenAmount = gameplay.regen.prayerRegenAmount,
            specialAttackIntervalTicks = gameplay.regen.specialAttackIntervalTicks,
            specialAttackAmount = gameplay.regen.specialAttackAmount,
            npcRegenMultiplier = gameplay.regen.npcRegenMultiplier,
            skillingSuccessMultiplier = gameplay.skilling.successMultiplier,
            resourceRespawnMultiplier = gameplay.skilling.resourceRespawnMultiplier,
            resourceDepleteMultiplier = gameplay.skilling.resourceDepleteMultiplier,
            playerDamageMultiplier = gameplay.combat.playerDamageMultiplier,
            npcDamageMultiplier = gameplay.combat.npcDamageMultiplier,
            npcAggression = gameplay.combat.npcAggression,
            npcRespawnMultiplier = gameplay.world.npcRespawnMultiplier,
            lootVisibleTicks = gameplay.world.lootVisibleTicks,
            shopRestockMultiplier = gameplay.world.shopRestockMultiplier,
            eventGlobalXp = active.fold(1.0) { acc, event -> acc * event.globalXp },
            eventSkillXp = eventSkillXp,
            eventDropMultiplier = active.fold(1.0) { acc, event -> acc * event.dropMultiplier },
            activeEvents = active.map { it.name },
        )
    }

    fun describeChanges(old: GameplayRateValues, new: GameplayRateValues): List<String> =
        fields.mapNotNull { (name, getter) ->
            val before = getter(old)
            val after = getter(new)
            if (before == after) null else "$name $before -> $after"
        }

    private val fields: List<Pair<String, (GameplayRateValues) -> Any?>> =
        listOf(
            "skill xp" to GameplayRateValues::skillXp,
            "health interval" to GameplayRateValues::healthIntervalTicks,
            "health amount" to GameplayRateValues::healthAmount,
            "stat restore interval" to GameplayRateValues::statRestoreIntervalTicks,
            "boost decay interval" to GameplayRateValues::boostDecayIntervalTicks,
            "run restore" to GameplayRateValues::runRestoreMultiplier,
            "run drain" to GameplayRateValues::runDrainMultiplier,
            "prayer drain" to GameplayRateValues::prayerDrainMultiplier,
            "prayer regen" to GameplayRateValues::prayerRegenAmount,
            "spec interval" to GameplayRateValues::specialAttackIntervalTicks,
            "spec amount" to GameplayRateValues::specialAttackAmount,
            "npc regen" to GameplayRateValues::npcRegenMultiplier,
            "skilling success" to GameplayRateValues::skillingSuccessMultiplier,
            "resource respawn" to GameplayRateValues::resourceRespawnMultiplier,
            "resource deplete" to GameplayRateValues::resourceDepleteMultiplier,
            "player damage" to GameplayRateValues::playerDamageMultiplier,
            "npc damage" to GameplayRateValues::npcDamageMultiplier,
            "npc aggression" to GameplayRateValues::npcAggression,
            "npc respawn" to GameplayRateValues::npcRespawnMultiplier,
            "loot visible" to GameplayRateValues::lootVisibleTicks,
            "shop restock" to GameplayRateValues::shopRestockMultiplier,
            "event xp" to GameplayRateValues::eventGlobalXp,
            "event skill xp" to GameplayRateValues::eventSkillXp,
            "event drops" to GameplayRateValues::eventDropMultiplier,
            "active events" to GameplayRateValues::activeEvents,
        )
}
