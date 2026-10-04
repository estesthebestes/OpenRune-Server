package org.rsmod.api.hotreload

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Singleton
public class HotReloadRegistry {
    private val targets = ConcurrentHashMap<String, Reloadable>()
    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()
    private val logger = InlineLogger()

    public fun register(target: Reloadable) {
        require(ID_PATTERN.matches(target.id)) { "Invalid reload target id: '${target.id}'" }
        val previous = targets.put(target.id, target)
        if (previous != null && previous !== target) {
            logger.warn { "Reload target '${target.id}' was replaced by ${target.javaClass.name}" }
        }
        changeListeners.forEach { it() }
    }

    public fun unregister(id: String) {
        if (targets.remove(id) != null) {
            changeListeners.forEach { it() }
        }
    }

    public operator fun get(id: String): Reloadable? = targets[id]

    public fun all(): List<Reloadable> = targets.values.sortedWith(compareBy({ it.order }, { it.id }))

    internal fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    private companion object {
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9:_-]*")
    }
}
