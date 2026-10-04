package org.rsmod.api.hotreload.config

import org.rsmod.api.server.config.ServerConfig

internal object RestartOnlyFields {
    private val fields: List<Pair<String, (ServerConfig) -> Any?>> =
        listOf(
            "name" to ServerConfig::name,
            "game-port" to ServerConfig::gamePort,
            "revision" to ServerConfig::revision,
            "environment" to ServerConfig::environment,
            "world" to ServerConfig::world,
            "database" to ServerConfig::database,
            "central" to ServerConfig::central,
            "login-timing-logs" to ServerConfig::loginTimingLogs,
            "social-pm-trace-logs" to ServerConfig::socialPmTraceLogs,
            "hot-reload" to ServerConfig::hotReload,
        )

    fun diff(old: ServerConfig, new: ServerConfig): List<String> =
        fields.mapNotNull { (key, getter) ->
            val before = getter(old)
            val after = getter(new)
            if (before == after) {
                null
            } else {
                "$key changed: needs a restart to take effect"
            }
        }
}
