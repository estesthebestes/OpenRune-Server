package org.rsmod.content.other.commands

import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.hotreload.HotReloadService
import org.rsmod.api.hotreload.ReloadRequester
import org.rsmod.api.hotreload.RequestOutcome
import org.rsmod.api.player.output.mes
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.config.RealmConfigWriter
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HotReloadCommands
@Inject
constructor(
    private val reloads: HotReloadService,
    private val playerList: PlayerList,
    private val realm: Realm,
    private val db: GameDbManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("reload", "Reload a hot-reload target (::reload to list)", ::reload)
        onCommand("xprate", "Show or set a player's personal XP rate", ::xpRate)
        onCommand("globalxp", "Show or set the realm-wide XP multiplier", ::globalXp)
        onCommand("basexp", "Show or set the XP rate new characters start with", ::baseXp)
    }

    private fun reload(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                listTargets(player)
                return@with
            }
            val target = args[0]
            val options = args.drop(1).toSet()
            val requester = player.reloadRequester()
            if (target == "all") {
                val outcomes = reloads.requestAll(requester, options)
                player.mes("Reloading ${outcomes.size} targets...")
                return@with
            }
            val message =
                when (reloads.request(target, requester, options)) {
                    RequestOutcome.Started -> "Reloading $target..."
                    RequestOutcome.Coalesced -> "$target is already reloading; it will run again after."
                    RequestOutcome.ShuttingDown -> "The server is shutting down."
                    RequestOutcome.UnknownTarget ->
                        "Unknown target '$target'. Targets: " +
                            reloads.status().joinToString(", ") { it.id }
                }
            player.mes(message)
        }

    private fun listTargets(player: Player) {
        val now = Instant.now()
        for (status in reloads.status()) {
            val state =
                when {
                    status.inFlight -> "reloading..."
                    status.lastAt == null -> "never reloaded"
                    else -> {
                        val ago = Duration.between(status.lastAt, now).toSeconds()
                        val result = if (status.lastOk == true) "ok" else "FAILED"
                        "$result ${formatAgo(ago)} ago"
                    }
                }
            player.mes("${status.id} - ${status.description}: $state")
        }
        player.mes("Use ::reload <target> or ::reload all")
    }

    private fun formatAgo(seconds: Long): String =
        when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h"
        }

    private fun xpRate(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes(describeXp(player))
                return@with
            }
            val rate = parseRate(args.last()) ?: return@with player.mes(INVALID_RATE)
            val target =
                if (args.size == 1) {
                    player
                } else {
                    val name = args.dropLast(1).joinToString(" ").replace('_', ' ')
                    findPlayer(name) ?: return@with player.mes("No online player named '$name'.")
                }
            target.xpRate = rate
            player.mes("${target.displayName}'s XP rate is now x${format(rate)}.")
            if (target !== player) {
                target.mes("Your XP rate was set to x${format(rate)}.")
            }
            player.mes(describeXp(target))
        }

    private fun globalXp(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes("Global XP multiplier: x${format(realm.config.globalXpRate)}")
                return@with
            }
            val rate = parseRate(args[0]) ?: return@with player.mes(INVALID_RATE)
            writeRealmRate(player, "Global XP multiplier", rate, RealmConfigWriter::updateGlobalXpRate)
        }

    private fun baseXp(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes("New characters start at x${format(realm.config.baseXpRate)}")
                return@with
            }
            val rate = parseRate(args[0]) ?: return@with player.mes(INVALID_RATE)
            writeRealmRate(player, "Base XP rate", rate, RealmConfigWriter::updateBaseXpRate)
        }

    private fun writeRealmRate(
        player: Player,
        label: String,
        rate: Double,
        write: (DatabaseConnection, Int, Int) -> Int,
    ) {
        val realmId = realm.config.id
        val hundreds = (rate * 100).roundToInt()
        val requester = player.reloadRequester()
        val uid = player.uid
        db.request(
            request = { connection -> GameDbResult.Ok(write(connection, realmId, hundreds)) },
            response = { result ->
                val current = uid.resolve(playerList)
                result.fold(
                    onOk = {
                        current?.mes("$label set to x${format(rate)}; reloading realm...")
                        reloads.request("realm", requester)
                    },
                    onErr = { current?.mes("Could not update the realm: $it") },
                )
            },
        )
    }

    private fun describeXp(player: Player): String {
        val effective = player.xpRate * player.globalXpRate
        return "${player.displayName}: personal x${format(player.xpRate)}, " +
            "global x${format(player.globalXpRate)}, effective x${format(effective)}"
    }

    private fun findPlayer(name: String): Player? =
        playerList.firstOrNull { it.displayName.equals(name, ignoreCase = true) }

    private fun parseRate(text: String): Double? {
        val value = text.removePrefix("x").toDoubleOrNull() ?: return null
        if (value <= 0.0 || value > MAX_RATE) {
            return null
        }
        return (value * 100).roundToInt() / 100.0
    }

    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun Player.reloadRequester(): ReloadRequester = ReloadRequester.Admin(uid, displayName)

    private companion object {
        private const val MAX_RATE = 1000.0
        private const val INVALID_RATE = "Rate must be a number above 0 and at most 1000."
    }
}
