package org.rsmod.game.cheat

import java.util.IdentityHashMap
import org.rsmod.game.entity.Player

public class CheatCommandMap {
    public val commands: MutableMap<String, CheatHandler> = hashMapOf()

    /** When set, registered command names are recorded against it; see [removeByOwner]. */
    public var currentOwner: Any? = null

    private val ownedCommands = IdentityHashMap<Any, MutableList<Pair<String, CheatHandler>>>()

    public fun execute(player: Player, command: String, args: List<String>): Boolean {
        val handler = this[command] ?: return false
        val cheat = Cheat(player, command, args)
        handler.action(cheat)
        return true
    }

    public fun put(name: String, handler: CheatHandler) {
        commands[name] = handler
        currentOwner?.let { ownedCommands.getOrPut(it) { mutableListOf() } += name to handler }
    }

    /** Removes the commands registered while [owner] was the [currentOwner]. */
    public fun removeByOwner(owner: Any): Int {
        val owned = ownedCommands.remove(owner) ?: return 0
        var removed = 0
        for ((name, handler) in owned) {
            if (commands[name] === handler) {
                commands.remove(name)
                removed++
            }
        }
        return removed
    }

    public operator fun set(name: String, handler: CheatHandler) {
        check(!commands.containsKey(name)) {
            "Command `$name` is already registered. " +
                "You may use the `put` function to bypass this check."
        }
        put(name, handler)
    }

    public operator fun get(name: String): CheatHandler? = commands[name]

    /**
     * Removes every command whose [CheatHandler.registrant] is [loader]. Used to unregister an
     * external plugin's commands before reloading it — see `ExternalPluginLoader`.
     */
    public fun removeByClassLoader(loader: ClassLoader): Int {
        var removed = 0
        val iterator = commands.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.registrant === loader) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }
}
