package org.rsmod.content.areas.city.grandexchange

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class JewelleryTest {
    private val games1 = "obj.necklace_of_minigames_1"
    private val games2 = "obj.necklace_of_minigames_2"
    private val games8 = "obj.necklace_of_minigames_8"
    private val glory1 = "obj.amulet_of_glory_1"
    private val glory4 = "obj.amulet_of_glory_4"

    private fun GeNpcFixture.combine() {
        op("npc.ge_expert_runes", 4)
        finish()
    }

    @Test
    fun `two games necklaces of one charge become one of two`() {
        val f = GeNpcFixture()
        f.give(games1)
        f.give(games1)
        f.combine()
        assertEquals(1, f.count(games2))
        assertEquals(0, f.count(games1))
        assertTrue(f.said("all done"), f.output())
    }

    @Test
    fun `charges are pooled into full items and one remainder`() {
        val f = GeNpcFixture()
        f.give(glory1)
        f.give("obj.amulet_of_glory_3")
        f.give("obj.amulet_of_glory_2")
        f.give("obj.amulet_of_glory_2")
        f.combine()
        assertEquals(2, f.count(glory4))
        assertEquals(0, f.count(glory1) + f.count("obj.amulet_of_glory_2") + f.count("obj.amulet_of_glory_3"))
    }

    @Test
    fun `an uneven pool keeps its remainder charged`() {
        val f = GeNpcFixture()
        f.give(glory4)
        f.give("obj.amulet_of_glory_3")
        f.give(glory1)
        f.combine()
        assertEquals(2, f.count(glory4))
        assertEquals(0, f.count("obj.amulet_of_glory_3"))
        val f2 = GeNpcFixture()
        f2.give(glory4)
        f2.give("obj.amulet_of_glory_3")
        f2.combine()
        assertEquals(1, f2.count(glory4))
        assertEquals(1, f2.count("obj.amulet_of_glory_3"))
    }

    @Test
    fun `noted jewellery is combined and comes back noted`() {
        val f = GeNpcFixture()
        f.giveNoted(games1, 2)
        f.combine()
        assertEquals(1, f.countNoted(games2))
        assertEquals(0, f.countNoted(games1))
    }

    @Test
    fun `different kinds are never mixed`() {
        val f = GeNpcFixture()
        f.give(games1)
        f.give(glory1)
        f.combine()
        assertEquals(1, f.count(games1))
        assertEquals(1, f.count(glory1))
        assertTrue(f.said("nothing I can combine"), f.output())
    }

    @Test
    fun `fountain of rune charges above the normal capacity are left alone`() {
        val f = GeNpcFixture()
        f.give("obj.amulet_of_glory_6")
        f.give(glory1)
        f.combine()
        assertEquals(1, f.count("obj.amulet_of_glory_6"))
        assertEquals(1, f.count(glory1))
    }

    @Test
    fun `a pack with no jewellery gets the plain refusal`() {
        val f = GeNpcFixture()
        f.give("obj.bronze_dagger")
        f.combine()
        assertTrue(f.said("got nothing that I can combine"), f.output())
    }

    @Test
    fun `eight charge items are pooled up to eight`() {
        val f = GeNpcFixture()
        f.give(games8)
        f.give("obj.necklace_of_minigames_4")
        f.give("obj.necklace_of_minigames_3")
        f.combine()
        assertEquals(1, f.count("obj.necklace_of_minigames_8"))
        assertEquals(1, f.count("obj.necklace_of_minigames_7"))
    }

    @Test
    fun `free players are turned away`() {
        val f = GeNpcFixture(members = false)
        f.give(games1)
        f.give(games1)
        f.combine()
        assertEquals(2, f.count(games1))
        assertTrue(f.said("member"), f.output())
    }

    @Test
    fun `Murky Matt combines from the dialogue as well`() {
        val f = GeNpcFixture()
        f.give(games1)
        f.give(games1)
        f.op("npc.ge_expert_runes", 1)
        f.finish(listOf(3))
        assertEquals(1, f.count(games2))
    }

    @Test
    fun `rings of forging cost 250 each and use the player's ruby rings`() {
        val f = GeNpcFixture()
        f.give("obj.ruby_ring", 3)
        f.give("obj.coins", 1_000)
        f.op("npc.ge_expert_runes", 1)
        f.finish(listOf(4), count = 2)
        assertEquals(2, f.count("obj.ring_of_forging"))
        assertEquals(1, f.count("obj.ruby_ring"))
        assertEquals(500, f.count("obj.coins"))
    }

    @Test
    fun `the number is capped by the rings and the coins held`() {
        val f = GeNpcFixture()
        f.give("obj.ruby_ring", 5)
        f.give("obj.coins", 600)
        f.op("npc.ge_expert_runes", 1)
        f.finish(listOf(4), count = 10)
        assertEquals(2, f.count("obj.ring_of_forging"))
        assertEquals(3, f.count("obj.ruby_ring"))
        assertEquals(100, f.count("obj.coins"))
    }

    @Test
    fun `asking for none enchants nothing`() {
        val f = GeNpcFixture()
        f.give("obj.ruby_ring", 2)
        f.give("obj.coins", 1_000)
        f.op("npc.ge_expert_runes", 1)
        f.finish(listOf(4), count = 0)
        assertEquals(0, f.count("obj.ring_of_forging"))
        assertEquals(1_000, f.count("obj.coins"))
        assertTrue(f.said("be seeing ya"), f.output())
    }

    @Test
    fun `without a ruby ring or the coins the request is refused`() {
        val noRing = GeNpcFixture()
        noRing.give("obj.coins", 1_000)
        noRing.op("npc.ge_expert_runes", 1)
        noRing.finish(listOf(4))
        assertTrue(noRing.said("bring me yer ruby rings"), noRing.output())

        val poor = GeNpcFixture()
        poor.give("obj.ruby_ring", 1)
        poor.give("obj.coins", 249)
        poor.op("npc.ge_expert_runes", 1)
        poor.finish(listOf(4))
        assertTrue(poor.said("250gp per ring"), poor.output())
        assertEquals(1, poor.count("obj.ruby_ring"))
        assertEquals(0, poor.count("obj.ring_of_forging"))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() = GeNpcFixture.initTransactions()

        @JvmStatic
        @AfterAll
        fun restore() = GeNpcFixture.restore()
    }
}
