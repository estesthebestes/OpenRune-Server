package org.rsmod.content.areas.city.grandexchange

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DecantTest {
    private val s4 = "obj.4dose2restore"
    private val s3 = "obj.3dose2restore"
    private val s2 = "obj.2dose2restore"
    private val s1 = "obj.1dose2restore"
    private val vial = "obj.vial_empty"

    private fun GeNpcFixture.doses(): Int =
        4 * count(s4) + 3 * count(s3) + 2 * count(s2) + count(s1) +
            4 * countNoted(s4) + 3 * countNoted(s3) + 2 * countNoted(s2) + countNoted(s1)

    private fun GeNpcFixture.decant(target: Int) {
        op("npc.ge_expert_herbs", 4)
        finish(listOf(target))
    }

    @Test
    fun `the maths pours doses into the fullest containers and a remainder`() {
        assertEquals(listOf(DoseCount(4, 2), DoseCount(3, 1)), DoseMath.redistribute(11, 4))
        assertEquals(listOf(DoseCount(3, 3), DoseCount(1, 1)), DoseMath.redistribute(10, 3))
        assertEquals(listOf(DoseCount(1, 7)), DoseMath.redistribute(7, 1))
        assertEquals(listOf(DoseCount(2, 5)), DoseMath.redistribute(10, 2))
        assertTrue(DoseMath.redistribute(0, 4).isEmpty())
        assertEquals(2, DoseMath.containers(DoseMath.redistribute(8, 4)))
    }

    @Test
    fun `a three dose and a one dose potion make one full potion and an empty vial`() {
        val f = GeNpcFixture()
        f.give(s3)
        f.give(s1)
        f.decant(4)
        assertEquals(1, f.count(s4))
        assertEquals(0, f.count(s3) + f.count(s1))
        assertEquals(1, f.count(vial))
        assertTrue(f.said("There, all done."), f.output())
    }

    @Test
    fun `decanting to four doses never loses a dose and returns one vial per potion saved`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give(s3)
        f.give(s2)
        f.give(s2)
        f.give(s1)
        f.give(s1)
        val before = f.doses()
        f.decant(4)
        assertEquals(before, f.doses())
        assertEquals(3, f.count(s4))
        assertEquals(1, f.count(s1))
        assertEquals(0, f.count(s3) + f.count(s2))
        assertEquals(2, f.count(vial))
    }

    @Test
    fun `a leftover of fewer than four doses stays in one container`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give(s3)
        f.decant(4)
        assertEquals(1, f.count(s4))
        assertEquals(1, f.count(s3))
        assertEquals(0, f.count(vial))
    }

    @Test
    fun `decanting to fewer doses spends the player's own empty vials before buying any`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give(vial)
        f.give("obj.coins", 100)
        f.decant(2)
        assertEquals(2, f.count(s2))
        assertEquals(0, f.count(vial))
        assertEquals(100, f.count("obj.coins"))
        assertTrue(f.said("There, all done."), f.output())

        val g = GeNpcFixture()
        g.give(s4)
        g.give(vial)
        g.give("obj.coins", 100)
        g.decant(1)
        assertEquals(4, g.count(s1))
        assertEquals(0, g.count(vial))
        assertEquals(100 - 2 * 4, g.count("obj.coins"))
    }

    @Test
    fun `missing vials are sold at the market price plus two coins each`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give("obj.coins", 100)
        f.decant(1)
        assertEquals(4, f.count(s1))
        assertEquals(100 - 3 * 4, f.count("obj.coins"))
    }

    @Test
    fun `without the coins nothing is touched and Bob quotes the prices`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give("obj.coins", 11)
        f.decant(1)
        assertEquals(1, f.count(s4))
        assertEquals(0, f.count(s1))
        assertEquals(11, f.count("obj.coins"))
        assertTrue(f.said("short of empty vessels or cash"), f.output())
        assertTrue(f.said("Empty vials cost 4 coins each and cups cost 4 coins each."), f.output())
    }

    @Test
    fun `a stack of noted potions comes back noted`() {
        val f = GeNpcFixture()
        f.giveNoted(s3, 4)
        f.giveNoted(s1, 4)
        f.decant(4)
        assertEquals(4, f.countNoted(s4))
        assertEquals(4, f.countNoted(vial))
        assertEquals(0, f.count(s4))
        assertEquals(0, f.countNoted(s3) + f.countNoted(s1))
    }

    @Test
    fun `noted and unnoted doses are pooled`() {
        val f = GeNpcFixture()
        f.give(s3)
        f.giveNoted(s1, 1)
        val before = f.doses()
        f.decant(4)
        assertEquals(before, f.doses())
        assertEquals(1, f.countNoted(s4))
    }

    @Test
    fun `when the potions would not fit unnoted they are returned as notes`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give("obj.coins", 1_000)
        f.fill()
        f.decant(1)
        assertEquals(4, f.countNoted(s1))
        assertEquals(0, f.count(s1))
        assertEquals(0, f.count(s4))
    }

    @Test
    fun `nothing to decant gets the stock reply and takes nothing`() {
        val f = GeNpcFixture()
        f.give("obj.coins", 100)
        f.give("obj.bronze_dagger")
        f.decant(4)
        assertTrue(f.said("anything that I can decant"), f.output())
        assertEquals(100, f.count("obj.coins"))
    }

    @Test
    fun `different potions are decanted separately`() {
        val f = GeNpcFixture()
        f.give(s3)
        f.give(s1)
        f.give("obj.2dose1attack")
        f.give("obj.2dose1attack")
        f.decant(4)
        assertEquals(1, f.count(s4))
        assertEquals(1, f.count("obj.4dose1attack"))
        assertEquals(2, f.count(vial))
    }

    @Test
    fun `guthix rest goes into cups`() {
        val f = GeNpcFixture()
        f.give("obj.cup_guthix_rest_2")
        f.give("obj.cup_guthix_rest_2")
        f.decant(4)
        assertEquals(1, f.count("obj.cup_guthix_rest_4"))
        assertEquals(1, f.count("obj.cup_empty"))
        assertEquals(0, f.count(vial))
    }

    @Test
    fun `barbarian mixes hold two doses at most`() {
        val f = GeNpcFixture()
        f.give("obj.brutal_1dose1attack")
        f.give("obj.brutal_1dose1attack")
        f.decant(4)
        assertEquals(1, f.count("obj.brutal_2dose1attack"))
        assertEquals(1, f.count(vial))
    }

    @Test
    fun `potions from other activities are left alone`() {
        val f = GeNpcFixture()
        f.give("obj.raids_vial_overload_2")
        f.give("obj.raids_vial_overload_1")
        f.decant(4)
        assertEquals(1, f.count("obj.raids_vial_overload_2"))
        assertEquals(1, f.count("obj.raids_vial_overload_1"))
        assertTrue(f.said("anything that I can decant"), f.output())
    }

    @Test
    fun `oils and moth mixes are decanted too`() {
        val f = GeNpcFixture()
        f.give("obj.sacred_oil2")
        f.give("obj.sacred_oil1")
        f.give("obj.sacred_oil1")
        f.give("obj.hunter_mix_sunmoth_1dose")
        f.give("obj.hunter_mix_sunmoth_1dose")
        f.decant(4)
        assertEquals(1, f.count("obj.sacred_oil4"))
        assertEquals(1, f.count("obj.hunter_mix_sunmoth_2dose"))
        assertEquals(3, f.count(vial))
    }

    @Test
    fun `a family never holds more than its own capacity`() {
        val index = DoseFamilies.decantable
        val potion = checkNotNull(index.find("obj.4dose2restore".asRSCM(RSCMType.OBJ))).family
        assertEquals(4, potion.capacity)
        val mix = checkNotNull(index.find("obj.brutal_2dose1attack".asRSCM(RSCMType.OBJ))).family
        assertEquals(2, mix.capacity)
        val moth = checkNotNull(index.find("obj.hunter_mix_sunmoth_2dose".asRSCM(RSCMType.OBJ))).family
        assertEquals(2, moth.capacity)
    }

    @Test
    fun `already decanted potions are reported as done without any change`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give(s4)
        f.decant(4)
        assertEquals(2, f.count(s4))
        assertFalse(f.said("short of"), f.output())
        assertTrue(f.said("There, all done."), f.output())
    }

    @Test
    fun `decanting to fewer doses introduces the work with one of Bob's lines`() {
        val f = GeNpcFixture()
        f.give(s4)
        f.give("obj.coins", 100)
        f.decant(2)
        assertTrue(f.said("Let's see what you have"), f.output())
    }

    @Test
    fun `the Decant option closed without a choice does nothing`() {
        val f = GeNpcFixture()
        f.give(s3)
        f.give(s1)
        f.op("npc.ge_expert_herbs", 4)
        assertTrue(f.player.ui.containsModal("interface.decant"))
        assertEquals(1, f.count(s3))
        assertEquals(1, f.count(s1))
    }

    @Test
    fun `talking to Bob and choosing to decant reaches the same service`() {
        val f = GeNpcFixture()
        f.give(s3)
        f.give(s1)
        f.op("npc.ge_expert_herbs", 1)
        f.finish(listOf(3, 4))
        assertEquals(1, f.count(s4))
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
