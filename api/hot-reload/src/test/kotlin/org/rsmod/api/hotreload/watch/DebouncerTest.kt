package org.rsmod.api.hotreload.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DebouncerTest {
    private var now = 0L
    private val debouncer = Debouncer { now }

    @Test
    fun `repeated touches push the deadline back`() {
        debouncer.touch("drops", 500)
        now = 400
        debouncer.touch("drops", 500)
        now = 800
        assertEquals(emptyList<String>(), debouncer.due())
        now = 900
        assertEquals(listOf("drops"), debouncer.due())
        assertEquals(emptyList<String>(), debouncer.due())
    }

    @Test
    fun `targets are debounced independently`() {
        debouncer.touch("config", 100)
        debouncer.touch("types", 3000)
        now = 150
        assertEquals(listOf("config"), debouncer.due())
        assertEquals(2850L, debouncer.nextDeadlineIn())
        now = 3000
        assertEquals(listOf("types"), debouncer.due())
        assertNull(debouncer.nextDeadlineIn())
    }
}
