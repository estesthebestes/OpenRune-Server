package org.rsmod.plugin.scripts

import jakarta.inject.Inject
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.queue.EngineQueueCache

public class ScriptContext
@Inject
constructor(
    public val eventBus: EventBus,
    public val cheatCommandMap: CheatCommandMap,
    public val engineQueueCache: EngineQueueCache,
) {
    /** Runs [block] with every event and command registration attributed to [owner]. */
    public fun <T> withOwner(owner: PluginScript, block: () -> T): T {
        val previousEventOwner = eventBus.currentOwner
        val previousCheatOwner = cheatCommandMap.currentOwner
        eventBus.currentOwner = owner
        cheatCommandMap.currentOwner = owner
        try {
            return block()
        } finally {
            eventBus.currentOwner = previousEventOwner
            cheatCommandMap.currentOwner = previousCheatOwner
        }
    }

    /** Unregisters every event handler and command that [owner] registered via [withOwner]. */
    public fun removeByOwner(owner: PluginScript): Int =
        eventBus.removeByOwner(owner) + cheatCommandMap.removeByOwner(owner)
}
