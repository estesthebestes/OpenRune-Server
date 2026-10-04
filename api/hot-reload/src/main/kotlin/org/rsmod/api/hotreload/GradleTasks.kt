package org.rsmod.api.hotreload

import com.github.michaelbull.logging.InlineLogger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

/** Runs Gradle tasks in a separate process from the reload worker thread. */
public object GradleTasks {
    private val logger = InlineLogger()
    private const val TAIL_LINES = 40

    public fun run(root: Path, tasks: List<String>, timeoutMinutes: Long = 20) {
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val wrapper = root.resolve(if (windows) "gradlew.bat" else "gradlew")
        if (!Files.exists(wrapper)) {
            throw ReloadException("Cannot run Gradle: ${wrapper.fileName} not found in $root")
        }
        val gradle = listOf(wrapper.absolutePathString()) + tasks + "--console=plain"
        val command = if (windows) listOf("cmd", "/c") + gradle else gradle
        logger.info { "Running Gradle for a reload: ${tasks.joinToString(" ")}" }
        val process =
            ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start()
        val tail = ArrayDeque<String>()
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                logger.debug { "[gradle] $line" }
                tail.addLast(line)
                if (tail.size > TAIL_LINES) tail.removeFirst()
            }
        }
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw ReloadException("Gradle timed out after $timeoutMinutes minutes")
        }
        if (process.exitValue() != 0) {
            val reason =
                tail.firstOrNull { it.startsWith("e: ") }
                    ?: tail.lastOrNull { it.contains("error", ignoreCase = true) }
                    ?: tail.lastOrNull()
            throw ReloadException("Gradle ${tasks.joinToString(" ")} failed: $reason")
        }
    }
}
