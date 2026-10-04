package org.rsmod.api.hotreload.config

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadException
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.server.config.ServerConfigLoader
import org.rsmod.api.server.config.ServerConfigModule

public interface ServerConfigReloadListener {
    public val name: String

    /** Runs on the reload worker; throw to reject the new config before anything changes. */
    public fun validate(config: ServerConfig) {}

    /** Runs on the game thread; returns a one-line summary, or `null` when nothing changed. */
    public fun apply(previous: ServerConfig, current: ServerConfig): String?
}

@Singleton
public class ServerConfigReloadTarget
@Inject
constructor(private val loader: ServerConfigLoader, initial: ServerConfig) : Reloadable {
    override val id: String = "config"
    override val description: String = "game.yml gameplay settings"
    override val order: Int = 20

    private val boot: ServerConfig = initial

    @Volatile
    public var current: ServerConfig = initial
        private set

    private val listeners = CopyOnWriteArrayList<ServerConfigReloadListener>()

    private val path: Path
        get() = ServerConfigModule.configFile.toAbsolutePath().normalize()

    public fun addListener(listener: ServerConfigReloadListener) {
        listeners.removeIf { it.name == listener.name }
        listeners += listener
    }

    override fun watchPaths(): List<Path> = listOf(path)

    override fun prepare(context: ReloadContext): ReloadPlan {
        if (!Files.exists(path)) {
            throw ReloadException("Config file not found: $path")
        }
        val next =
            try {
                loader.read(path)
            } catch (e: Exception) {
                throw ReloadException("Could not parse game.yml: ${e.message}", e)
            }
        val errors =
            listeners.mapNotNull { listener ->
                runCatching { listener.validate(next) }
                    .exceptionOrNull()
                    ?.let { "${listener.name}: ${it.message}" }
            }
        if (errors.isNotEmpty()) {
            throw ReloadException("Rejected game.yml:\n" + errors.joinToString("\n"))
        }
        val previous = current
        val restartWarnings = RestartOnlyFields.diff(boot, next)
        return ReloadPlan { apply(previous, next, restartWarnings) }
    }

    private fun apply(
        previous: ServerConfig,
        next: ServerConfig,
        restartWarnings: List<String>,
    ): ReloadSummary {
        current = next
        val changes = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for (listener in listeners) {
            try {
                listener.apply(previous, next)?.let(changes::add)
            } catch (e: Exception) {
                failures += "${listener.name} FAILED: ${e.message}"
            }
        }
        val message = if (changes.isEmpty()) "no gameplay changes" else changes.joinToString("; ")
        return ReloadSummary(message, failures + restartWarnings)
    }
}
