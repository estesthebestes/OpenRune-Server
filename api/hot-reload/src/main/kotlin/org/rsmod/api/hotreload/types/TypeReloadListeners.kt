package org.rsmod.api.hotreload.types

import dev.openrune.types.InvStock
import jakarta.inject.Singleton
import java.util.concurrent.CopyOnWriteArrayList

public data class InvTypeChange(val oldSize: Int, val oldStock: List<InvStock>, val oldFlags: Int)

/** Which server type ids (and which of their fields) a `::reload types` changed in place. */
public data class TypeReloadChanges(
    val npcFields: Map<Int, Set<String>>,
    val itemFields: Map<Int, Set<String>>,
    val locFields: Map<Int, Set<String>>,
    val invs: Map<Int, InvTypeChange>,
) {
    val npcs: Set<Int>
        get() = npcFields.keys

    val items: Set<Int>
        get() = itemFields.keys

    val locs: Set<Int>
        get() = locFields.keys
}

/**
 * Called on the game thread after server types were edited in place, so systems that index types
 * at boot can rebuild. Returns a one-line summary, or `null` when nothing was affected.
 */
public fun interface TypeReloadListener {
    public fun onTypesReloaded(changes: TypeReloadChanges): String?
}

@Singleton
public class TypeReloadListeners {
    private val listeners = CopyOnWriteArrayList<Pair<String, TypeReloadListener>>()

    public fun add(name: String, listener: TypeReloadListener) {
        listeners.removeIf { it.first == name }
        listeners += name to listener
    }

    public fun all(): List<Pair<String, TypeReloadListener>> = listeners.toList()
}
