package org.rsmod.api.hotreload

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves where a reload target reads its data from. An explicit `hot-reload.paths.<id>`
 * override wins, then the development source path relative to the working directory (`gradlew
 * run` uses the repository root). `null` means neither exists, so the target should fall back to
 * its classpath copy.
 */
public object ReloadSources {
    public val workingDir: Path
        get() = Paths.get("").toAbsolutePath()

    public fun resolve(override: String?, devPath: String): Path? {
        if (!override.isNullOrBlank()) {
            val path = workingDir.resolve(override).normalize()
            return path.takeIf(Files::exists)
        }
        val dev = workingDir.resolve(devPath).normalize()
        return dev.takeIf(Files::exists)
    }
}
