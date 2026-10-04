package org.rsmod.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventBusOwnerTest {
    @Test
    fun `removeByOwner removes only that owner's handlers`() {
        val bus = EventBus()
        val first = Any()
        val second = Any()
        var firstCalls = 0
        var secondCalls = 0

        bus.currentOwner = first
        bus.subscribeUnbound(Ping::class.java) { firstCalls++ }
        bus.subscribeKeyed(Op::class.java, 1L) {}
        bus.currentOwner = second
        bus.subscribeUnbound(Ping::class.java) { secondCalls++ }
        bus.currentOwner = null

        assertEquals(2, bus.removeByOwner(first))
        bus.publish(Ping)
        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    fun `a removed keyed handler can be registered again`() {
        val bus = EventBus()
        val owner = Any()
        bus.currentOwner = owner
        bus.subscribeKeyed(Op::class.java, 7L) {}
        bus.currentOwner = null

        bus.removeByOwner(owner)
        var called = false
        bus.subscribeKeyed(Op::class.java, 7L) { called = true }
        bus.publish(Op(7L))
        assertTrue(called)
    }

    @Test
    fun `untracked registrations are untouched`() {
        val bus = EventBus()
        var calls = 0
        bus.subscribeUnbound(Ping::class.java) { calls++ }
        assertEquals(0, bus.removeByOwner(Any()))
        bus.publish(Ping)
        assertEquals(1, calls)
    }

    private data object Ping : UnboundEvent

    private data class Op(override val id: Long) : KeyedEvent
}
