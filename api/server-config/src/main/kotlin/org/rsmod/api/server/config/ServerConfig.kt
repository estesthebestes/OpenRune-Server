package org.rsmod.api.server.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
public data class OpenRuneCentralGameConfig(
    @JsonProperty("same-instance") val sameInstance: Boolean = false,
    @JsonProperty("http-port") val httpPort: Int = 8080,
    val host: String = "",
    @JsonProperty("link-port") val linkPort: Int = 9091,
    @JsonProperty("world-key") val worldKey: String = "",
    val postgres: CentralPostgresYaml? = null,
)

public data class CentralPostgresYaml(
    @JsonProperty("jdbc-url") val jdbcUrl: String = "",
    val user: String = "openrune",
    val password: String = "openrune",
    @JsonProperty("pool-size") val poolSize: Int = 10,
    @JsonProperty("embedded-pgdata-dir") val embeddedPgdataDir: String = ".data/postgres",
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class GameDatabaseYaml(
    val postgres: PostgresDbYaml? = null,
)

public data class PostgresDbYaml(
    @JsonProperty("jdbc-url") val jdbcUrl: String = "",
    val user: String = "openrune",
    val password: String = "openrune",
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class GameplayConfig(
    @JsonProperty("quest-requirements")
    val questRequirements: QuestRequirementsYaml = QuestRequirementsYaml(),
    @JsonProperty("drop-rates")
    val dropRates: DropRatesYaml = DropRatesYaml(),
    val xp: XpRatesYaml = XpRatesYaml(),
    val regen: RegenRatesYaml = RegenRatesYaml(),
    val skilling: SkillingRatesYaml = SkillingRatesYaml(),
    val combat: CombatRatesYaml = CombatRatesYaml(),
    val world: WorldRatesYaml = WorldRatesYaml(),
    val events: List<GameEventYaml> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class XpRatesYaml(
    @JsonProperty("skill-multipliers") val skillMultipliers: Map<String, Double> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class RegenRatesYaml(
    @JsonProperty("health-interval-ticks") val healthIntervalTicks: Int = 100,
    @JsonProperty("health-amount") val healthAmount: Int = 1,
    @JsonProperty("stat-restore-interval-ticks") val statRestoreIntervalTicks: Int = 100,
    @JsonProperty("boost-decay-interval-ticks") val boostDecayIntervalTicks: Int = 100,
    @JsonProperty("run-restore-multiplier") val runRestoreMultiplier: Double = 1.0,
    @JsonProperty("run-drain-multiplier") val runDrainMultiplier: Double = 1.0,
    @JsonProperty("prayer-drain-multiplier") val prayerDrainMultiplier: Double = 1.0,
    @JsonProperty("prayer-regen-amount") val prayerRegenAmount: Int = 0,
    @JsonProperty("special-attack-interval-ticks") val specialAttackIntervalTicks: Int = 50,
    @JsonProperty("special-attack-amount") val specialAttackAmount: Int = 100,
    @JsonProperty("npc-regen-multiplier") val npcRegenMultiplier: Double = 1.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class SkillingRatesYaml(
    @JsonProperty("success-multiplier") val successMultiplier: Double = 1.0,
    @JsonProperty("resource-respawn-multiplier") val resourceRespawnMultiplier: Double = 1.0,
    @JsonProperty("resource-deplete-multiplier") val resourceDepleteMultiplier: Double = 1.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class CombatRatesYaml(
    @JsonProperty("player-damage-multiplier") val playerDamageMultiplier: Double = 1.0,
    @JsonProperty("npc-damage-multiplier") val npcDamageMultiplier: Double = 1.0,
    @JsonProperty("npc-aggression") val npcAggression: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class WorldRatesYaml(
    @JsonProperty("npc-respawn-multiplier") val npcRespawnMultiplier: Double = 1.0,
    @JsonProperty("loot-visible-ticks") val lootVisibleTicks: Int = 200,
    @JsonProperty("shop-restock-multiplier") val shopRestockMultiplier: Double = 1.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class GameEventYaml(
    val name: String,
    val start: String,
    val end: String,
    @JsonProperty("global-xp") val globalXp: Double = 1.0,
    @JsonProperty("drop-multiplier") val dropMultiplier: Double = 1.0,
    @JsonProperty("skill-multipliers") val skillMultipliers: Map<String, Double> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class HotReloadYaml(
    val watch: Boolean = false,
    @JsonProperty("debounce-ms") val debounceMs: Long = 500,
    val code: Boolean = false,
    val paths: Map<String, String> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class DropRatesYaml(
    val multiplier: Double = 1.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class QuestRequirementsYaml(
    val mode: String = "assume-completed",
    @JsonProperty("virtual-completions")
    val virtualCompletions: Set<String> = emptySet(),
    @JsonProperty("virtual-lines")
    val virtualLines: Set<String> = emptySet(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class ServerConfig(
    val name: String,
    @JsonProperty("game-port") val gamePort: Int,
    val revision: Int,
    val environment: String,
    val world: Int,
    val gameplay: GameplayConfig = GameplayConfig(),
    val database: GameDatabaseYaml? = null,
    val central: OpenRuneCentralGameConfig? = null,
    @JsonProperty("login-timing-logs") val loginTimingLogs: Boolean = false,
    @JsonProperty("social-pm-trace-logs") val socialPmTraceLogs: Boolean = false,
    @JsonProperty("hot-reload") val hotReload: HotReloadYaml = HotReloadYaml(),
)
