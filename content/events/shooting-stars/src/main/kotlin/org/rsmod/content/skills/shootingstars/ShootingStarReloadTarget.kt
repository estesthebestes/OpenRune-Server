package org.rsmod.content.skills.shootingstars

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Path
import kotlin.io.path.readText
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadException
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSources
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.server.config.ServerConfig

@Singleton
class ShootingStarReloadTarget
@Inject
constructor(private val stars: ShootingStarManager, private val config: ServerConfig) : Reloadable {
    override val id: String = "shootingstars"
    override val description: String = "Shooting star schedule (shootingstars.toml)"
    override val order: Int = 40

    private val bootEnabled: Boolean = ShootingstarsSettings.load().isEnabled

    private val sourceFile: Path?
        get() = ReloadSources.resolve(config.hotReload.paths[id], DEV_FILE)

    override fun watchPaths(): List<Path> = listOfNotNull(sourceFile)

    override fun prepare(context: ReloadContext): ReloadPlan {
        val file = sourceFile
        val text =
            file?.readText()
                ?: javaClass.classLoader
                    .getResourceAsStream(ShootingstarsSettings.RESOURCE)
                    ?.use { it.readBytes().decodeToString() }
                ?: throw ReloadException("Missing ${ShootingstarsSettings.RESOURCE}")
        val next =
            try {
                ShootingstarsSettings.parse(text)
            } catch (e: Exception) {
                throw ReloadException("Could not parse shootingstars.toml: ${e.message}", e)
            }
        validate(next)
        return ReloadPlan { apply(next) }
    }

    private fun validate(settings: ShootingstarsSettings) {
        val errors = buildList {
            if (settings.spawnIntervalMinutes <= 0) add("spawn_interval_minutes must be above 0")
            if (settings.spawnVariationMinutes < 0) add("spawn_variation_minutes must not be negative")
            if (settings.bootSpawnMaxMinutes < settings.bootSpawnMinMinutes) {
                add("boot_spawn_max_minutes must be at least boot_spawn_min_minutes")
            }
        }
        if (errors.isNotEmpty()) {
            throw ReloadException("Rejected shootingstars.toml:\n" + errors.joinToString("\n"))
        }
    }

    private fun apply(next: ShootingstarsSettings): ReloadSummary {
        val previous = ShootingstarsSettings.load()
        ShootingstarsSettings.install(next)
        stars.bindSettings(next)
        val warnings = buildList {
            if (next.isEnabled != bootEnabled) {
                add("is_enabled changed: needs a restart to take effect")
            }
            if (
                next.spawnIntervalMinutes != previous.spawnIntervalMinutes ||
                    next.spawnVariationMinutes != previous.spawnVariationMinutes
            ) {
                add("the new spawn interval applies after the next star spawns")
            }
        }
        val message = if (next == previous) "no changes" else "settings updated"
        return ReloadSummary(message, warnings)
    }

    private companion object {
        private const val DEV_FILE =
            "content/events/shooting-stars/src/main/resources/shootingstars.toml"
    }
}
