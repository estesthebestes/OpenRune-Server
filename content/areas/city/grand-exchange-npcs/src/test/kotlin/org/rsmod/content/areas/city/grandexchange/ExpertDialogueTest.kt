package org.rsmod.content.areas.city.grandexchange

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.constants

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ExpertDialogueTest {
    private fun GeNpcFixture.opensPrices() = player.ui.containsModal("interface.ge_pricelist")

    @Test
    fun `Farid takes offence, then relents when asked for the ores and bars`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_ores", 1)
        f.finish(listOf(2))
        assertTrue(f.said("accomplished"), f.output())
        assertTrue(f.opensPrices())
    }

    @Test
    fun `calming Farid down leaves him bristling and the dialogue ends`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_ores", 1)
        f.finish(listOf(1))
        assertTrue(f.said("Ali Morrisane"), f.output())
        assertTrue(f.said("darling").not() && f.said("mate"), f.output())
        assertFalse(f.opensPrices())
    }

    @Test
    fun `Farid's remarks follow the player's body type`() {
        val f = GeNpcFixture()
        f.player.appearance.bodyType = constants.bodytype_b
        f.op("npc.ge_expert_ores", 1)
        f.finish(listOf(1))
        assertTrue(f.said("darling"), f.output())

        val g = GeNpcFixture()
        g.player.appearance.bodyType = constants.bodytype_b
        g.op("npc.ge_expert_ores", 1)
        g.finish(listOf(3))
        assertTrue(g.said("miss"), g.output())

        val h = GeNpcFixture()
        h.op("npc.ge_expert_ores", 1)
        h.finish(listOf(3))
        assertTrue(h.said("mister"), h.output())
    }

    @Test
    fun `Relobo offers the logs straight away`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_logs", 1)
        f.finish(listOf(2))
        assertTrue(f.opensPrices())
    }

    @Test
    fun `Relobo's story ends in the same two choices`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_logs", 1)
        f.finish(listOf(1, 1))
        assertTrue(f.said("Shilo Village"), f.output())
        assertTrue(f.opensPrices())

        val g = GeNpcFixture()
        g.op("npc.ge_expert_logs", 1)
        g.finish(listOf(1, 2))
        assertTrue(g.said("nice talking to you"), g.output())
        assertFalse(g.opensPrices())
    }

    @Test
    fun `Relobo lets a hurried player go`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_logs", 1)
        f.finish(listOf(3))
        assertTrue(f.said("nice talking to you"), f.output())
        assertFalse(f.opensPrices())
    }

    @Test
    fun `Hofuthand explains himself and then shows the weapons and armour`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_combat", 1)
        f.finish(listOf(1, 1))
        assertTrue(f.said("dwarves"), f.output())
        assertTrue(f.opensPrices())
    }

    @Test
    fun `Hofuthand lets the player leave with or without the story`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_combat", 1)
        f.finish(listOf(3))
        assertTrue(f.said("much on my mind"), f.output())
        assertFalse(f.opensPrices())

        val g = GeNpcFixture()
        g.op("npc.ge_expert_combat", 1)
        g.finish(listOf(1, 2))
        assertTrue(g.said("much on my mind"), g.output())
        assertFalse(g.opensPrices())
    }

    @Test
    fun `Hofuthand direct request opens the list`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_combat", 1)
        f.finish(listOf(2))
        assertTrue(f.opensPrices())
    }

    @Test
    fun `Bob offers decanting to members and potion prices to everyone`() {
        val members = GeNpcFixture(members = true)
        members.op("npc.ge_expert_herbs", 1)
        members.finish(listOf(2))
        assertTrue(members.said("herb & potion prices"), members.output())
        assertTrue(members.opensPrices())

        val free = GeNpcFixture(members = false)
        free.op("npc.ge_expert_herbs", 1)
        free.finish(listOf(2))
        assertTrue(free.said("very latest potion prices"), free.output())
        assertFalse(free.said("decant your potions"))
        assertTrue(free.opensPrices())
    }

    @Test
    fun `Bob tells his story and comes back to the menu`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_herbs", 1)
        f.finish(listOf(1, 4))
        assertTrue(f.said("Ardougne way"), f.output())
        assertFalse(f.opensPrices())
    }

    @Test
    fun `Bob refuses to decant on a free to play world`() {
        val f = GeNpcFixture(members = false)
        f.give("obj.3dose2restore")
        f.give("obj.1dose2restore")
        f.op("npc.ge_expert_herbs", 4)
        f.finish()
        assertTrue(f.said("come back later"), f.output())
        assertEquals(1, f.count("obj.3dose2restore"))
        assertEquals(1, f.count("obj.1dose2restore"))
    }

    @Test
    fun `Murky Matt shows the rune prices from the dialogue and from the option`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_runes", 1)
        f.finish(listOf(2))
        assertTrue(f.said("jewellery"), f.output())
        assertTrue(f.opensPrices())
    }

    @Test
    fun `Murky Matt's pirate story leads back to prices`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_runes", 1)
        f.finish(listOf(1, 1))
        assertTrue(f.said("landlubber"), f.output())
        assertTrue(f.opensPrices())
    }

    @Test
    fun `free players are not offered the jewellery service`() {
        val f = GeNpcFixture(members = false)
        f.op("npc.ge_expert_runes", 1)
        f.finish(listOf(1, 2))
        assertFalse(f.said("combine the charges on yer"), f.output())
        assertFalse(f.opensPrices())
    }

    @Test
    fun `James answers the bond questions and loops back to the menu`() {
        val f = GeNpcFixture()
        f.op("npc.bond_james_bond", 1)
        f.play(listOf(1, 2, 3, 4, 1))
        assertTrue(f.said("14 days"), f.output())
        assertTrue(f.said("name change"), f.output())
        assertTrue(f.said("Bond Pouch").not())
    }

    @Test
    fun `James explains buying a bond to give away`() {
        val f = GeNpcFixture()
        f.op("npc.bond_james_bond", 1)
        f.play(listOf(4, 3))
        assertTrue(f.said("Bond Pouch"), f.output())
    }

    @Test
    fun `Brugsen explains the place and offers the price checker`() {
        val f = GeNpcFixture()
        var opened = 0
        f.script(BrugsenBursen { opened++ })
        f.op("npc.ge_boss", 1)
        f.finish(listOf(1, 3))
        assertTrue(f.said("best trading spot in the land"), f.output())
        assertTrue(f.said("talk to the clerks"), f.output())
        assertEquals(0, opened)
    }

    @Test
    fun `Brugsen opens the price checker from the dialogue and from the Prices option`() {
        val f = GeNpcFixture()
        var opened = 0
        f.script(BrugsenBursen { opened++ })
        f.op("npc.ge_boss", 1)
        f.finish(listOf(2))
        assertTrue(f.said("What had you in mind?"), f.output())
        assertEquals(1, opened)
        f.op("npc.ge_boss", 3)
        f.finish()
        assertEquals(2, opened)
    }

    @Test
    fun `Brugsen lets a player who needs nothing go`() {
        val f = GeNpcFixture()
        var opened = 0
        f.script(BrugsenBursen { opened++ })
        f.op("npc.ge_boss", 1)
        f.finish(listOf(3))
        assertEquals(0, opened)
        assertTrue(f.said("I'm fine, thanks."), f.output())
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
