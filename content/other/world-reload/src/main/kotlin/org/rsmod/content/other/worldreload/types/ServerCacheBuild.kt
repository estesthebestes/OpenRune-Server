package org.rsmod.content.other.worldreload.types

import java.nio.file.Path
import org.rsmod.api.hotreload.GradleTasks

/** Runs `gradlew :or-cache:buildCache` so edited TOML type data lands in the SERVER cache. */
internal object ServerCacheBuild {
    fun run(root: Path) {
        GradleTasks.run(root, listOf(":or-cache:buildCache"))
    }
}
