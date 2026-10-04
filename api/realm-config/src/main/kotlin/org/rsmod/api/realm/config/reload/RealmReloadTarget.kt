package org.rsmod.api.realm.config.reload

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadException
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.api.realm.config.RealmConfigLoader
import org.rsmod.api.realm.config.updater.RealmConfigUpdater
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.PlayerList

@Singleton
public class RealmReloadTarget
@Inject
constructor(
    private val realm: Realm,
    private val loader: RealmConfigLoader,
    private val updater: RealmConfigUpdater,
    private val playerList: PlayerList,
    private val config: ServerConfig,
) : Reloadable {
    override val id: String = "realm"
    override val description: String = "Realm settings in the database (XP rates, login message)"
    override val order: Int = 10

    override fun prepare(context: ReloadContext): ReloadPlan {
        val world = config.world
        val next =
            context.awaitDb { loader.select(it, world) }
                ?: throw ReloadException("No realm is configured for world $world.")
        return ReloadPlan { apply(next) }
    }

    private fun apply(next: RealmConfig): ReloadSummary {
        val previous = realm.config
        updater.update(next)
        for (player in playerList) {
            player.globalXpRate = next.globalXpRate
        }
        val changes =
            trackedFields.mapNotNull { (name, getter) ->
                val before = getter(previous)
                val after = getter(next)
                if (before == after) null else "$name $before -> $after"
            }
        val warnings =
            if (previous.id != next.id) listOf("realm id changed; a restart is recommended") else emptyList()
        val message = if (changes.isEmpty()) "no changes" else changes.joinToString("; ")
        return ReloadSummary(message, warnings)
    }

    private companion object {
        private val trackedFields: List<Pair<String, (RealmConfig) -> Any?>> =
            listOf(
                "base xp" to RealmConfig::baseXpRate,
                "global xp" to RealmConfig::globalXpRate,
                "login message" to RealmConfig::loginMessage,
                "login broadcast" to RealmConfig::loginBroadcast,
                "spawn" to RealmConfig::spawnCoord,
                "respawn" to RealmConfig::respawnCoord,
                "dev mode" to RealmConfig::devMode,
                "require registration" to RealmConfig::requireRegistration,
                "auto display names" to RealmConfig::autoAssignDisplayNames,
            )
    }
}
