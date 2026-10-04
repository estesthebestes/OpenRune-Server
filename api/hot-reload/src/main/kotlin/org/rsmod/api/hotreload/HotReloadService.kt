package org.rsmod.api.hotreload

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.rsmod.api.db.gateway.GameDbManager

public enum class RequestOutcome {
    Started,
    Coalesced,
    UnknownTarget,
    ShuttingDown,
}

public data class TargetStatus(
    val id: String,
    val description: String,
    val inFlight: Boolean,
    val lastAt: Instant?,
    val lastOk: Boolean?,
    val lastMessage: String?,
)

/**
 * Runs reloads with a two-phase model: [Reloadable.prepare] on a single background worker, then
 * [ReloadPlan.apply] on the game thread via [drainApplies]. A target only ever has one reload in
 * flight; requests that arrive meanwhile are merged into a single follow-up run so the latest
 * change always lands and every requester hears the outcome.
 */
@Singleton
public class HotReloadService
internal constructor(
    private val registry: HotReloadRegistry,
    private val reporter: ReloadReporter,
    private val database: ReloadDatabase,
    private val executor: Executor,
) {
    @Inject
    public constructor(
        registry: HotReloadRegistry,
        reporter: ReloadReporter,
        db: GameDbManager,
    ) : this(registry, reporter, GatewayReloadDatabase(db), newWorker())

    private val states = ConcurrentHashMap<String, TargetState>()
    private val completed = ConcurrentLinkedQueue<Completed>()
    private val logger = InlineLogger()

    @Volatile private var shuttingDown = false

    public fun request(
        id: String,
        requester: ReloadRequester,
        options: Set<String> = emptySet(),
    ): RequestOutcome {
        if (shuttingDown) {
            return RequestOutcome.ShuttingDown
        }
        val target = registry[id] ?: return RequestOutcome.UnknownTarget
        val state = states.computeIfAbsent(id) { TargetState() }
        val run: Run
        synchronized(state) {
            if (state.inFlight) {
                state.rerun = true
                state.pendingRequesters += requester
                state.pendingOptions += options
                return RequestOutcome.Coalesced
            }
            state.inFlight = true
            run = Run(target, setOf(requester), options)
        }
        submit(run)
        return RequestOutcome.Started
    }

    public fun requestAll(
        requester: ReloadRequester,
        options: Set<String> = emptySet(),
    ): List<Pair<String, RequestOutcome>> =
        registry.all().filter { it.includeInAll }.map { it.id to request(it.id, requester, options) }

    public fun status(): List<TargetStatus> =
        registry.all().map { target ->
            val state = states[target.id]
            if (state == null) {
                TargetStatus(target.id, target.description, false, null, null, null)
            } else {
                synchronized(state) {
                    TargetStatus(
                        id = target.id,
                        description = target.description,
                        inFlight = state.inFlight,
                        lastAt = state.lastAt,
                        lastOk = state.lastOk,
                        lastMessage = state.lastMessage,
                    )
                }
            }
        }

    /** Applies every prepared reload. Must only be called from the game thread. */
    public fun drainApplies() {
        while (true) {
            val done = completed.poll() ?: break
            finish(done)
        }
    }

    public fun shutdown() {
        shuttingDown = true
        (executor as? ExecutorService)?.shutdownNow()
    }

    private fun submit(run: Run) {
        executor.execute {
            val start = System.nanoTime()
            val result =
                try {
                    val context = ReloadContext(run.requesters, run.options, database)
                    Completed.Prepared(run, run.target.prepare(context), elapsedMs(start))
                } catch (t: Throwable) {
                    Completed.Failed(run, t)
                }
            completed += result
        }
    }

    private fun finish(done: Completed) {
        val run = done.run
        val id = run.target.id
        val (success, lines) =
            when (done) {
                is Completed.Failed -> false to failureLines(id, done.error)
                is Completed.Prepared -> applyPlan(done)
            }
        reporter.report(run.requesters, success, lines)

        val state = states.computeIfAbsent(id) { TargetState() }
        val next: Run?
        synchronized(state) {
            state.lastAt = Instant.now()
            state.lastOk = success
            state.lastMessage = lines.firstOrNull()
            if (state.rerun && !shuttingDown) {
                next = Run(run.target, state.pendingRequesters.toSet(), state.pendingOptions.toSet())
                state.rerun = false
                state.pendingRequesters.clear()
                state.pendingOptions.clear()
            } else {
                next = null
                state.inFlight = false
            }
        }
        if (next != null) {
            submit(next)
        }
    }

    private fun applyPlan(done: Completed.Prepared): Pair<Boolean, List<String>> {
        val id = done.run.target.id
        val start = System.nanoTime()
        return try {
            val summary = done.plan.apply()
            val applyMs = elapsedMs(start)
            if (applyMs > SLOW_APPLY_MS) {
                logger.warn { "Reload '$id' took ${applyMs}ms on the game thread." }
            }
            val header = "Reload $id: ${summary.message} (${done.prepareMs + applyMs}ms)"
            true to (listOf(header) + summary.warnings.map { "  ! $it" })
        } catch (t: Throwable) {
            false to failureLines(id, t)
        }
    }

    private fun failureLines(id: String, error: Throwable): List<String> {
        if (error !is ReloadException) {
            logger.error(error) { "Reload '$id' failed unexpectedly." }
        }
        val message = error.message?.lineSequence()?.firstOrNull() ?: error.javaClass.simpleName
        val detail = error.message?.lines()?.drop(1)?.filter(String::isNotBlank).orEmpty()
        return listOf("Reload $id FAILED: $message") + detail.map { "  $it" }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private class Run(
        val target: Reloadable,
        val requesters: Set<ReloadRequester>,
        val options: Set<String>,
    )

    private sealed class Completed(val run: Run) {
        class Prepared(run: Run, val plan: ReloadPlan, val prepareMs: Long) : Completed(run)

        class Failed(run: Run, val error: Throwable) : Completed(run)
    }

    private class TargetState {
        var inFlight = false
        var rerun = false
        val pendingRequesters = linkedSetOf<ReloadRequester>()
        val pendingOptions = linkedSetOf<String>()
        var lastAt: Instant? = null
        var lastOk: Boolean? = null
        var lastMessage: String? = null
    }

    private companion object {
        private const val SLOW_APPLY_MS = 50L

        private fun newWorker(): ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "hot-reload").apply { isDaemon = true }
            }
    }
}
