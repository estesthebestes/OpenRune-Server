package org.rsmod.api.stats.plugin.rates

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.rates.GameplayRateValues
import org.rsmod.api.server.config.GameEventYaml
import org.rsmod.api.server.config.GameplayConfig
import org.rsmod.api.server.config.RegenRatesYaml
import org.rsmod.api.server.config.XpRatesYaml

class GameplayRateConfigsTest {
    private val stats = setOf("stat.woodcutting", "stat.prayer")
    private val now = LocalDateTime.of(2026, 10, 10, 12, 0)

    @Test
    fun `defaults match the built in OSRS values`() {
        val built = GameplayRateConfigs.build(GameplayConfig(), emptyList(), now)
        assertEquals(GameplayRateValues(), built)
    }

    @Test
    fun `unknown skills and bad values are rejected`() {
        val config =
            GameplayConfig(
                xp = XpRatesYaml(mapOf("woodcuting" to 2.0)),
                regen = RegenRatesYaml(healthIntervalTicks = 0, runRestoreMultiplier = -1.0),
            )
        val errors = GameplayRateConfigs.validate(config, stats::contains)
        assertEquals(3, errors.size)
        assertTrue(errors[0].contains("unknown skill 'woodcuting'"))
    }

    @Test
    fun `skill names accept a bare or prefixed form`() {
        val config = GameplayConfig(xp = XpRatesYaml(mapOf("woodcutting" to 2.0)))
        assertTrue(GameplayRateConfigs.validate(config, stats::contains).isEmpty())
        val built = GameplayRateConfigs.build(config, emptyList(), now)
        assertEquals(2.0, built.skillXpMultiplier("stat.woodcutting"))
        assertEquals(1.0, built.skillXpMultiplier("stat.prayer"))
    }

    @Test
    fun `overlapping active events multiply and inactive ones are ignored`() {
        val events =
            GameplayRateConfigs.parseEvents(
                listOf(
                    GameEventYaml("Double XP", "2026-10-10", "2026-10-12", globalXp = 2.0),
                    GameEventYaml(
                        name = "Woodcutting Week",
                        start = "2026-10-09 00:00",
                        end = "2026-10-16T00:00",
                        dropMultiplier = 1.5,
                        skillMultipliers = mapOf("woodcutting" to 3.0),
                    ),
                    GameEventYaml("Old", "2026-01-01", "2026-01-02", globalXp = 10.0),
                )
            )
        val built = GameplayRateConfigs.build(GameplayConfig(), events, now)
        assertEquals(listOf("Double XP", "Woodcutting Week"), built.activeEvents)
        assertEquals(6.0, built.skillXpMultiplier("stat.woodcutting"))
        assertEquals(2.0, built.skillXpMultiplier("stat.prayer"))
        assertEquals(1.5, built.eventDropMultiplier)
    }

    @Test
    fun `event end must follow start`() {
        val config = GameplayConfig(events = listOf(GameEventYaml("Bad", "2026-10-12", "2026-10-10")))
        val errors = GameplayRateConfigs.validate(config, stats::contains)
        assertEquals(listOf("events['Bad']: end must be after start"), errors)
    }
}
