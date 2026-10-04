package org.rsmod.api.stats.plugin.rates

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import java.time.LocalDateTime
import org.rsmod.api.config.rates.GameplayRateValues
import org.rsmod.api.config.rates.GameplayRates
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.hotreload.config.ServerConfigReloadListener
import org.rsmod.api.hotreload.config.ServerConfigReloadTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.api.server.config.GameplayConfig
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class GameplayRatesScript
@Inject
constructor(
    private val config: ServerConfig,
    private val configTarget: ServerConfigReloadTarget,
    private val playerList: PlayerList,
) : PluginScript() {
    private var cyclesSinceEventCheck = 0
    private lateinit var knownStats: Set<String>

    override fun ScriptContext.startup() {
        knownStats =
            ServerCacheManager.getStats().values.mapTo(hashSetOf()) {
                RSCM.getReverseMapping(RSCMType.STAT, it.id)
            }
        val errors = GameplayRateConfigs.validate(config.gameplay, ::isKnownStat)
        check(errors.isEmpty()) {
            "Invalid gameplay settings in game.yml:\n" + errors.joinToString("\n")
        }
        val events = GameplayRateConfigs.parseEvents(config.gameplay.events)
        GameplayRates.installEvents(events)
        GameplayRates.install(GameplayRateConfigs.build(config.gameplay, events, now()))
        configTarget.addListener(ConfigListener())
        onEvent<GameLifecycle.LateCycle> { checkEvents() }
    }

    private fun checkEvents() {
        if (++cyclesSinceEventCheck < EVENT_CHECK_CYCLES) {
            return
        }
        cyclesSinceEventCheck = 0
        val previous = GameplayRates.current
        val active = GameplayRates.events.filter { it.isActive(now()) }.map { it.name }
        if (active == previous.activeEvents) {
            return
        }
        install(configTarget.current.gameplay, previous)
    }

    private fun install(gameplay: GameplayConfig, previous: GameplayRateValues): GameplayRateValues {
        val next = GameplayRateConfigs.build(gameplay, GameplayRates.events, now())
        GameplayRates.install(next)
        rearmTimers(previous, next)
        announce(previous.activeEvents, next.activeEvents)
        return next
    }

    private fun rearmTimers(previous: GameplayRateValues, next: GameplayRateValues) {
        val timers =
            buildList {
                if (previous.healthIntervalTicks != next.healthIntervalTicks) {
                    add("timer.health_regen" to next.healthIntervalTicks)
                }
                if (previous.statRestoreIntervalTicks != next.statRestoreIntervalTicks) {
                    add("timer.stat_regen" to next.statRestoreIntervalTicks)
                }
                if (previous.boostDecayIntervalTicks != next.boostDecayIntervalTicks) {
                    add("timer.stat_boost_restore" to next.boostDecayIntervalTicks)
                }
                if (previous.specialAttackIntervalTicks != next.specialAttackIntervalTicks) {
                    add("timer.spec_regen" to next.specialAttackIntervalTicks)
                }
            }
        if (timers.isEmpty()) {
            return
        }
        for (player in playerList) {
            for ((timer, interval) in timers) {
                player.softTimer(timer, interval)
            }
        }
    }

    private fun announce(before: List<String>, after: List<String>) {
        val started = after - before.toSet()
        val ended = before - after.toSet()
        if (started.isEmpty() && ended.isEmpty()) {
            return
        }
        for (player in playerList) {
            started.forEach { player.mes("<col=0040ff>$it has started!</col>") }
            ended.forEach { player.mes("<col=0040ff>$it has ended.</col>") }
        }
    }

    private fun isKnownStat(key: String): Boolean = key in knownStats

    private fun now(): LocalDateTime = LocalDateTime.now()

    private inner class ConfigListener : ServerConfigReloadListener {
        override val name: String = "gameplay rates"

        override fun validate(config: ServerConfig) {
            val errors = GameplayRateConfigs.validate(config.gameplay, ::isKnownStat)
            require(errors.isEmpty()) { errors.joinToString("\n") }
        }

        override fun apply(previous: ServerConfig, current: ServerConfig): String? {
            GameplayRates.installEvents(GameplayRateConfigs.parseEvents(current.gameplay.events))
            val before = GameplayRates.current
            val after = install(current.gameplay, before)
            val changes = GameplayRateConfigs.describeChanges(before, after)
            return if (changes.isEmpty()) null else "rates: " + changes.joinToString(", ")
        }
    }

    private companion object {
        private const val EVENT_CHECK_CYCLES = 100
    }
}
