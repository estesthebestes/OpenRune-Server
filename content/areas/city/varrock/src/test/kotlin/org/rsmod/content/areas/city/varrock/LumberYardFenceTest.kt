package org.rsmod.content.areas.city.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class LumberYardFenceTest {
    @Test
    fun `the lumber yard's broken fence is the climbable loc`() {
        val id = "loc.gertrudefence".asRSCM()
        val type = checkNotNull(ServerCacheManager.getObject(id))
        assertEquals(LUMBER_YARD_FENCE_ID, id)
        assertEquals("Broken fence", type.name)
        assertEquals("Climb-over", type.actions.getOpOrNull(0))
    }

    companion object {
        private const val LUMBER_YARD_FENCE_ID = 2618

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
