package org.rsmod.events

import java.util.IdentityHashMap

public class EventBus(
    public val unbound: UnboundEventMap = UnboundEventMap(),
    public val keyed: KeyedEventMap = KeyedEventMap(),
    public val suspend: SuspendEventMap = SuspendEventMap(),
) {
    /**
     * When set, every subscription is recorded against this owner (a plugin script) so that
     * [removeByOwner] can later unregister exactly that script's handlers for a hot swap.
     */
    public var currentOwner: Any? = null

    private val ownedRegistrations = IdentityHashMap<Any, MutableList<OwnedRegistration>>()

    public fun <T : UnboundEvent> publish(event: T): Boolean {
        val actions = unbound[event::class.java] ?: return false
        for (action in actions) {
            action(event)
        }
        return true
    }

    public fun <T : UnboundEvent> subscribeUnbound(type: Class<T>, action: T.() -> Unit) {
        unbound.add(type, action)
        track(OwnedRegistration.Unbound(type, action))
    }

    public fun <T : KeyedEvent> publish(event: T): Boolean {
        val action = keyed[event::class.java, event.id] ?: return false
        action(event)
        return true
    }

    public fun <T : KeyedEvent> subscribeKeyed(type: Class<T>, id: Long, action: T.() -> Unit) {
        val previous = keyed.putIfAbsent(type, id, action)
        if (previous != null) {
            error("Event with id already registered: id=$id, type=${type.simpleName}")
        }
        track(OwnedRegistration.Keyed(type, id, action))
    }

    public suspend fun <R, T : SuspendEvent<R>> publish(receiver: R, event: T): Boolean {
        val action = suspend[event::class.java, event.id] ?: return false
        action(receiver, event)
        return true
    }

    public fun <R, T : SuspendEvent<R>> subscribeSuspend(
        type: Class<T>,
        id: Long,
        action: suspend R.(T) -> Unit,
    ) {
        val previous = suspend.putIfAbsent(type, id, action)
        if (previous != null) {
            error("Event with id already registered: id=$id, type=$type")
        }
        track(OwnedRegistration.Suspend(type, id, action))
    }

    public fun <T : SuspendEvent<*>> contains(type: Class<T>, key: Long): Boolean {
        return suspend.contains(type, key)
    }

    public fun <T : SuspendEvent<*>> contains(type: Class<T>, key: Int): Boolean {
        return suspend.contains(type, key.toLong())
    }

    /**
     * Removes every subscriber (unbound, keyed, and suspend) whose backing lambda/method-reference
     * class was defined by [loader]. Used to unregister an external plugin's handlers before
     * reloading it — see `ExternalPluginLoader`.
     */
    public fun removeByClassLoader(loader: ClassLoader): Int {
        return unbound.removeByClassLoader(loader) +
            keyed.removeByClassLoader(loader) +
            suspend.removeByClassLoader(loader)
    }

    /** Removes every subscription made while [owner] was the [currentOwner]. */
    public fun removeByOwner(owner: Any): Int {
        val registrations = ownedRegistrations.remove(owner) ?: return 0
        var removed = 0
        for (registration in registrations) {
            val success =
                when (registration) {
                    is OwnedRegistration.Unbound ->
                        unbound.removeAction(registration.type, registration.action)
                    is OwnedRegistration.Keyed ->
                        keyed.removeIfSame(registration.type, registration.id, registration.action)
                    is OwnedRegistration.Suspend ->
                        suspend.removeIfSame(registration.type, registration.id, registration.action)
                }
            if (success) removed++
        }
        return removed
    }

    private fun track(registration: OwnedRegistration) {
        val owner = currentOwner ?: return
        ownedRegistrations.getOrPut(owner) { mutableListOf() } += registration
    }

    private sealed class OwnedRegistration {
        class Unbound(val type: Class<*>, val action: Any) : OwnedRegistration()

        class Keyed(val type: Class<*>, val id: Long, val action: Any) : OwnedRegistration()

        class Suspend(val type: Class<*>, val id: Long, val action: Any) : OwnedRegistration()
    }

    public companion object {
        public fun composeLongKey(high: Int, low: Int): Long {
            return (high.toLong() shl 32) or low.toLong()
        }
    }
}
