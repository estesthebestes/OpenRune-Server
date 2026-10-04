package org.rsmod.api.hotreload.watch

internal class Debouncer(private val clock: () -> Long = System::currentTimeMillis) {
    private val deadlines = LinkedHashMap<String, Long>()

    fun touch(id: String, delayMs: Long) {
        deadlines[id] = clock() + delayMs
    }

    fun due(): List<String> {
        val now = clock()
        val ready = deadlines.filterValues { it <= now }.keys.toList()
        ready.forEach(deadlines::remove)
        return ready
    }

    fun nextDeadlineIn(): Long? {
        val next = deadlines.values.minOrNull() ?: return null
        return (next - clock()).coerceAtLeast(0)
    }
}
