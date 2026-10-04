package org.rsmod.content.other.worldreload.spawns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpawnDiffTest {
    @Test
    fun `identical spawn sets produce no diff`() {
        val spawns = mapOf("cow@a" to 2, "goblin@b" to 1)
        assertTrue(SpawnDiff.of(spawns, spawns).isEmpty)
    }

    @Test
    fun `duplicates are counted as a multiset`() {
        val current = mapOf("cow@a" to 3, "goblin@b" to 1)
        val desired = mapOf("cow@a" to 1, "imp@c" to 2)
        val diff = SpawnDiff.of(current, desired)
        assertEquals(mapOf("imp@c" to 2), diff.added)
        assertEquals(mapOf("cow@a" to 2, "goblin@b" to 1), diff.removed)
    }
}
