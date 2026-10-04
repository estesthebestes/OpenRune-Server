package org.rsmod.content.other.worldreload.types

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TypeSourcesTest {
    @Test
    fun `watches the server toml data and every pack config folder`(@TempDir root: Path) {
        val server = root.resolve(".data/raw-cache/server").createDirectories()
        val questPack = root.resolve("content/quest/pack/src/main/resources/pack/configs")
            .createDirectories()
        root.resolve("content/quest/pack/build/resources/main/pack/configs").createDirectories()
        root.resolve("content/quest/src/main/kotlin").createDirectories()

        val dirs = TypeSources.dirs(root).map { it.toRealPath() }.toSet()

        assertEquals(setOf(server, questPack).map { it.toRealPath() }.toSet(), dirs)
    }

    @Test
    fun `missing folders are simply not watched`(@TempDir root: Path) {
        Files.createDirectories(root.resolve("content"))
        assertEquals(emptyList<Path>(), TypeSources.dirs(root))
    }
}
