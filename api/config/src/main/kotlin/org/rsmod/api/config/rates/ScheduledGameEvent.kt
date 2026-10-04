package org.rsmod.api.config.rates

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

public data class ScheduledGameEvent(
    val name: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val globalXp: Double = 1.0,
    val dropMultiplier: Double = 1.0,
    val skillXp: Map<String, Double> = emptyMap(),
) {
    public fun isActive(now: LocalDateTime): Boolean = !now.isBefore(start) && now.isBefore(end)

    public fun isUpcoming(now: LocalDateTime): Boolean = now.isBefore(start)

    public companion object {
        /** Accepts `2026-10-10T18:00`, `2026-10-10 18:00` or a bare date (start of that day). */
        public fun parseTime(text: String): LocalDateTime {
            val trimmed = text.trim()
            return try {
                LocalDateTime.parse(trimmed.replace(' ', 'T'))
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(trimmed).atStartOfDay()
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("Invalid date/time '$text'", e)
                }
            }
        }
    }
}
