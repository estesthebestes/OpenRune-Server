package org.rsmod.content.other.worldreload.types

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TypeFieldPolicyTest {
    @Test
    fun `server-only fields are copied and client fields are only reported`() {
        val live = NpcServerType(id = 10, name = "Cow", attack = 1, respawnRate = 50)
        val fresh = NpcServerType(id = 10, name = "Mad cow", attack = 20, respawnRate = 25)

        val delta = NPC_POLICY.diff(live, fresh)!!
        delta.apply()

        assertEquals(20, live.attack)
        assertEquals(25, live.respawnRate)
        assertEquals("Cow", live.name)
        assertEquals(listOf("name"), delta.restartOnly)
        assertEquals(setOf("attack", "respawnRate"), delta.changedNames)
    }

    @Test
    fun `unchanged types produce no delta`() {
        assertNull(NPC_POLICY.diff(NpcServerType(id = 1), NpcServerType(id = 1)))
    }

    @Test
    fun `types stay usable as map keys after being edited in place`() {
        val live = NpcServerType(id = 7, attack = 1)
        val index = hashMapOf(live to "indexed")

        NPC_POLICY.diff(live, NpcServerType(id = 7, attack = 99))!!.apply()

        assertEquals("indexed", index[live])
        assertSame(live, index.keys.single())
    }
}
