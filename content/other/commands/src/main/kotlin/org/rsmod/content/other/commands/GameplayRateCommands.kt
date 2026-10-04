package org.rsmod.content.other.commands

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.rsmod.api.config.rates.GameplayRates
import org.rsmod.api.droptable.DropRateModifiers
import org.rsmod.api.player.output.mes
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GameplayRateCommands : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("rates", "Show the live gameplay rates", ::rates)
        onCommand("skillxp", "Show or set a per-skill XP multiplier until reload", ::skillXp)
        onCommand("droprate", "Show or set the server drop multiplier until reload", ::dropRate)
        onCommand("events", "List scheduled gameplay events", ::events)
    }

    private fun rates(cheat: Cheat) =
        with(cheat) {
            val r = GameplayRates.current
            val skills =
                r.skillXp.entries.joinToString(", ") {
                    "${it.key.removePrefix("stat.")} x${fmt(it.value)}"
                }
            val skillSuffix = if (skills.isEmpty()) "" else "; $skills"
            player.mes(
                "XP: personal x${fmt(player.xpRate)}, global x${fmt(player.globalXpRate)}, " +
                    "event x${fmt(r.eventGlobalXp)}$skillSuffix"
            )
            player.mes(
                "Regen: hp ${r.healthAmount}/${r.healthIntervalTicks}t, " +
                    "stats ${r.statRestoreIntervalTicks}t, boosts ${r.boostDecayIntervalTicks}t, " +
                    "spec ${r.specialAttackAmount}/${r.specialAttackIntervalTicks}t, " +
                    "npc x${fmt(r.npcRegenMultiplier)}"
            )
            player.mes(
                "Run: restore x${fmt(r.runRestoreMultiplier)}, drain x${fmt(r.runDrainMultiplier)}; " +
                    "Prayer: drain x${fmt(r.prayerDrainMultiplier)}, regen ${r.prayerRegenAmount}"
            )
            player.mes(
                "Skilling: success x${fmt(r.skillingSuccessMultiplier)}, " +
                    "respawn x${fmt(r.resourceRespawnMultiplier)}, " +
                    "deplete x${fmt(r.resourceDepleteMultiplier)}"
            )
            player.mes(
                "Combat: player dmg x${fmt(r.playerDamageMultiplier)}, " +
                    "npc dmg x${fmt(r.npcDamageMultiplier)}, " +
                    "aggression ${if (r.npcAggression) "on" else "off"}"
            )
            player.mes(
                "World: npc respawn x${fmt(r.npcRespawnMultiplier)}, loot ${r.lootVisibleTicks}t, " +
                    "restock x${fmt(r.shopRestockMultiplier)}, " +
                    "drops x${fmt(DropRateModifiers.effectiveServerMultiplier)}"
            )
            if (r.activeEvents.isNotEmpty()) {
                player.mes("Active events: ${r.activeEvents.joinToString(", ")}")
            }
        }

    private fun skillXp(cheat: Cheat) =
        with(cheat) {
            val current = GameplayRates.current
            if (args.isEmpty()) {
                if (current.skillXp.isEmpty()) {
                    player.mes("No per-skill XP multipliers are set.")
                } else {
                    current.skillXp.forEach { (stat, value) ->
                        player.mes("${stat.removePrefix("stat.")}: x${fmt(value)}")
                    }
                }
                return@with
            }
            if (args.size != 2) {
                return@with player.mes("Use ::skillxp <skill> <multiplier>")
            }
            val stat = "stat.${args[0].removePrefix("stat.")}"
            val value = args[1].removePrefix("x").toDoubleOrNull()
            if (value == null || value <= 0.0) {
                return@with player.mes("Multiplier must be a number above 0.")
            }
            val updated =
                if (value == 1.0) current.skillXp - stat else current.skillXp + (stat to value)
            GameplayRates.install(current.copy(skillXp = updated))
            player.mes(
                "${stat.removePrefix("stat.")} XP multiplier set to x${fmt(value)} " +
                    "until restart or ::reload config."
            )
        }

    private fun dropRate(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes(
                    "Server drop multiplier: x${fmt(DropRateModifiers.serverMultiplier)} " +
                        "(effective x${fmt(DropRateModifiers.effectiveServerMultiplier)})"
                )
                return@with
            }
            val value = args[0].removePrefix("x").toDoubleOrNull()
            if (value == null || value <= 0.0) {
                return@with player.mes("Multiplier must be a number above 0.")
            }
            DropRateModifiers.serverMultiplier = value
            player.mes("Drop multiplier set to x${fmt(value)} until restart or ::reload config.")
        }

    private fun events(cheat: Cheat) =
        with(cheat) {
            val events = GameplayRates.events
            if (events.isEmpty()) {
                return@with player.mes("No events are scheduled in game.yml.")
            }
            val now = LocalDateTime.now()
            for (event in events) {
                val state =
                    when {
                        event.isActive(now) -> "ACTIVE until ${event.end.format(TIME)}"
                        event.isUpcoming(now) -> "starts ${event.start.format(TIME)}"
                        else -> "ended ${event.end.format(TIME)}"
                    }
                player.mes(
                    "${event.name}: $state (xp x${fmt(event.globalXp)}, " +
                        "drops x${fmt(event.dropMultiplier)})"
                )
            }
        }

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", value).trimEnd('0')
        }

    private companion object {
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
