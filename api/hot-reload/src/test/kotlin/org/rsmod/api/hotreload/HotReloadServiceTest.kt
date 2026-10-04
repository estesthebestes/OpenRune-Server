package org.rsmod.api.hotreload

import java.util.concurrent.Executor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotReloadServiceTest {
    private val queued = ArrayDeque<Runnable>()
    private val deferred = Executor { queued += it }
    private val reports = mutableListOf<Triple<Set<ReloadRequester>, Boolean, List<String>>>()
    private val reporter = ReloadReporter { r, ok, lines -> reports += Triple(r, ok, lines) }
    private val registry = HotReloadRegistry()
    private val service = HotReloadService(registry, reporter, NoReloadDatabase, deferred)

    @Test
    fun `prepare result is only applied when the game thread drains`() {
        var applied = 0
        registry.register(target("demo") { ReloadPlan { applied++; ReloadSummary("done") } })

        assertEquals(RequestOutcome.Started, service.request("demo", ReloadRequester.Server))
        runWorker()
        assertEquals(0, applied)

        service.drainApplies()
        assertEquals(1, applied)
        assertTrue(reports.single().second)
        assertTrue(reports.single().third.first().startsWith("Reload demo: done"))
    }

    @Test
    fun `failed prepare reports failure and applies nothing`() {
        registry.register(target("bad") { throw ReloadException("broken file") })

        service.request("bad", ReloadRequester.Server)
        runWorker()
        service.drainApplies()

        val (_, ok, lines) = reports.single()
        assertFalse(ok)
        assertEquals("Reload bad FAILED: broken file", lines.first())
        assertFalse(service.status().single().inFlight)
    }

    @Test
    fun `requests during a reload coalesce into one rerun that notifies everyone`() {
        var prepares = 0
        registry.register(target("demo") { prepares++; noChanges() })
        val first = ReloadRequester.Server
        val second = ReloadRequester.Watcher

        assertEquals(RequestOutcome.Started, service.request("demo", first))
        assertEquals(RequestOutcome.Coalesced, service.request("demo", second))
        assertEquals(RequestOutcome.Coalesced, service.request("demo", second))

        runWorker()
        service.drainApplies()
        runWorker()
        service.drainApplies()

        assertEquals(2, prepares)
        assertEquals(setOf(first), reports[0].first)
        assertEquals(setOf(second), reports[1].first)
        assertFalse(service.status().single().inFlight)
    }

    @Test
    fun `unknown targets and shutdown are rejected`() {
        assertEquals(RequestOutcome.UnknownTarget, service.request("nope", ReloadRequester.Server))
        registry.register(target("demo") { noChanges() })
        service.shutdown()
        assertEquals(RequestOutcome.ShuttingDown, service.request("demo", ReloadRequester.Server))
    }

    @Test
    fun `request all follows target order`() {
        registry.register(target("b", order = 2) { noChanges() })
        registry.register(target("a", order = 1) { noChanges() })
        val ids = service.requestAll(ReloadRequester.Server).map { it.first }
        assertEquals(listOf("a", "b"), ids)
    }

    private fun runWorker() {
        while (queued.isNotEmpty()) {
            queued.removeFirst().run()
        }
    }

    private fun target(id: String, order: Int = 100, prepare: (ReloadContext) -> ReloadPlan) =
        object : Reloadable {
            override val id: String = id
            override val description: String = id
            override val order: Int = order

            override fun prepare(context: ReloadContext): ReloadPlan = prepare(context)
        }
}
