package org.rsmod.api.hotreload.hotswap

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

internal data class ClassFileStamp(val path: Path, val modified: Long, val size: Long, val crc: Long)

/** Finds the Kotlin output directories of every content and api module and fingerprints them. */
internal class CompiledClassIndex(private val root: Path) {
    fun classDirs(): List<Path> =
        MODULE_ROOTS.flatMap { moduleRoot ->
            val base = root.resolve(moduleRoot)
            if (!base.isDirectory()) return@flatMap emptyList()
            Files.walk(base).use { stream ->
                stream
                    .filter { it.isDirectory() && it.name == "build" && !isExternalPlugin(it.parent) }
                    .map { it.resolve("classes").resolve("kotlin").resolve("main") }
                    .filter { it.isDirectory() }
                    .toList()
            }
        }

    /**
     * Modules with a `plugin.properties` are external plugins (e.g. the test-bot modules): their
     * classes live in the plugin loader's own classloader and reload through it, not `::hotswap`.
     */
    private fun isExternalPlugin(module: Path): Boolean =
        module.resolve("src").resolve("main").resolve("resources").resolve("plugin.properties").isRegularFile()

    /** Fingerprints every class file, re-reading only files whose size or timestamp changed. */
    fun snapshot(previous: Map<String, ClassFileStamp>?): Map<String, ClassFileStamp> {
        val result = HashMap<String, ClassFileStamp>()
        for (dir in classDirs()) {
            Files.walk(dir).use { stream ->
                for (file in stream.filter { it.isRegularFile() && it.extension == "class" }) {
                    val name = dir.relativize(file).invariantSeparatorsPathString
                        .removeSuffix(".class")
                        .replace('/', '.')
                    val modified = file.getLastModifiedTime().toMillis()
                    val size = file.fileSize()
                    val old = previous?.get(name)
                    result[name] =
                        if (old != null && old.modified == modified && old.size == size) {
                            old.copy(path = file)
                        } else {
                            ClassFileStamp(file, modified, size, crc(file))
                        }
                }
            }
        }
        return result
    }

    private fun crc(file: Path): Long {
        if (!file.exists()) return -1
        val crc = CRC32()
        crc.update(Files.readAllBytes(file))
        return crc.value
    }

    private companion object {
        private val MODULE_ROOTS = listOf("content", "api")
    }
}
