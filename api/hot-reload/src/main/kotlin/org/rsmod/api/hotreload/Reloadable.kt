package org.rsmod.api.hotreload

import java.nio.file.Path

/**
 * Something that can be refreshed while the server runs.
 *
 * [prepare] runs on the hot-reload worker thread: it may read files or the database and must not
 * touch live game state. The returned [ReloadPlan] is applied on the game thread at the start of
 * the next cycle, which is where state is actually swapped.
 */
public interface Reloadable {
    public val id: String
    public val description: String
    public val order: Int
        get() = 100

    public val watchDebounceMs: Long?
        get() = null

    /** `false` keeps a target out of `::reload all` (resets and code swaps stay explicit). */
    public val includeInAll: Boolean
        get() = true

    public fun watchPaths(): List<Path> = emptyList()

    public fun prepare(context: ReloadContext): ReloadPlan
}

public fun interface ReloadPlan {
    public fun apply(): ReloadSummary
}

public data class ReloadSummary(val message: String, val warnings: List<String> = emptyList())

public class ReloadException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

public fun noChanges(message: String = "no changes"): ReloadPlan = ReloadPlan {
    ReloadSummary(message)
}
