package org.rsmod.api.hotreload.watch

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import org.rsmod.api.hotreload.HotReloadRegistry
import org.rsmod.api.hotreload.HotReloadService
import org.rsmod.api.hotreload.ReloadRequester

/**
 * Watches every reload target's [org.rsmod.api.hotreload.Reloadable.watchPaths] and requests a
 * reload once a target's files have been quiet for its debounce window. Directories are watched
 * recursively; file paths are matched exactly through their parent directory.
 */
@Singleton
public class HotReloadWatcher
@Inject
constructor(private val registry: HotReloadRegistry, private val reloads: HotReloadService) {
    private val logger = InlineLogger()

    @Volatile private var thread: Thread? = null

    @Volatile private var refreshNeeded = true

    private var watchService: WatchService? = null
    private val keyDirs = HashMap<WatchKey, Path>()
    private val dirRoots = HashMap<Path, MutableSet<String>>()
    private val fileRoots = HashMap<Path, MutableSet<String>>()
    private val debounceById = HashMap<String, Long>()

    init {
        registry.addChangeListener { refreshNeeded = true }
    }

    @Synchronized
    public fun start(defaultDebounceMs: Long) {
        if (thread != null) {
            return
        }
        val ws = FileSystems.getDefault().newWatchService()
        watchService = ws
        refreshNeeded = true
        val worker = Thread({ loop(ws, defaultDebounceMs) }, "hot-reload-watcher")
        worker.isDaemon = true
        worker.start()
        thread = worker
        logger.info { "Hot-reload watcher started (debounce ${defaultDebounceMs}ms)." }
    }

    @Synchronized
    public fun stop() {
        val running = thread ?: return
        runCatching { watchService?.close() }
        running.join(1_000)
        thread = null
        watchService = null
    }

    private fun loop(service: WatchService, defaultDebounceMs: Long) {
        val debouncer = Debouncer()
        try {
            while (true) {
                if (refreshNeeded) {
                    refreshNeeded = false
                    registerTargets(service, defaultDebounceMs)
                }
                val timeout = debouncer.nextDeadlineIn() ?: IDLE_POLL_MS
                val key = service.poll(timeout.coerceAtMost(IDLE_POLL_MS), TimeUnit.MILLISECONDS)
                if (key != null) {
                    handle(service, key, debouncer)
                }
                for (id in debouncer.due()) {
                    requestReload(id)
                }
            }
        } catch (_: ClosedWatchServiceException) {
        } catch (_: InterruptedException) {
        }
    }

    private fun requestReload(id: String) {
        logger.info { "File change detected; reloading '$id'." }
        reloads.request(id, ReloadRequester.Watcher)
    }

    private fun handle(service: WatchService, key: WatchKey, debouncer: Debouncer) {
        val dir = keyDirs[key]
        for (event in key.pollEvents()) {
            if (dir == null) {
                continue
            }
            if (event.kind() == OVERFLOW) {
                targetsFor(dir).forEach { debouncer.touch(it, debounceById.getValue(it)) }
                continue
            }
            val child = dir.resolve(event.context() as Path)
            if (isIgnored(child)) {
                continue
            }
            if (event.kind() == ENTRY_CREATE && child.isDirectory()) {
                val owners = targetsFor(child)
                if (owners.isNotEmpty()) {
                    registerTree(service, child)
                }
            }
            targetsFor(child).forEach { debouncer.touch(it, debounceById.getValue(it)) }
        }
        if (!key.reset()) {
            keyDirs.remove(key)
        }
    }

    private fun targetsFor(path: Path): Set<String> {
        val normalized = path.toAbsolutePath().normalize()
        val owners = mutableSetOf<String>()
        fileRoots[normalized]?.let(owners::addAll)
        for ((root, ids) in dirRoots) {
            if (normalized.startsWith(root)) {
                owners += ids
            }
        }
        return owners
    }

    private fun registerTargets(service: WatchService, defaultDebounceMs: Long) {
        dirRoots.clear()
        fileRoots.clear()
        debounceById.clear()
        for (target in registry.all()) {
            val paths = target.watchPaths()
            if (paths.isEmpty()) {
                continue
            }
            debounceById[target.id] = target.watchDebounceMs ?: defaultDebounceMs
            for (raw in paths) {
                val path = raw.toAbsolutePath().normalize()
                if (path.isDirectory()) {
                    dirRoots.getOrPut(path) { mutableSetOf() } += target.id
                    registerTree(service, path)
                } else {
                    val parent = path.parent ?: continue
                    if (!Files.isDirectory(parent)) {
                        continue
                    }
                    fileRoots.getOrPut(path) { mutableSetOf() } += target.id
                    register(service, parent)
                }
            }
        }
        logger.debug { "Watching ${keyDirs.size} directories for ${debounceById.size} targets." }
    }

    private fun registerTree(service: WatchService, root: Path) {
        Files.walk(root).use { stream ->
            stream.filter(Files::isDirectory).forEach { register(service, it) }
        }
    }

    private fun register(service: WatchService, dir: Path) {
        val key = dir.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        keyDirs[key] = dir.toAbsolutePath().normalize()
    }

    private fun isIgnored(path: Path): Boolean {
        val name = path.name
        return name.endsWith("~") ||
            name.endsWith(".tmp") ||
            name.endsWith(".swp") ||
            name.contains("___jb_") ||
            name.startsWith(".#")
    }

    private companion object {
        private const val IDLE_POLL_MS = 500L
    }
}
