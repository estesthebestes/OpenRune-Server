package org.rsmod.content.areas.city.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.npc.events.AiTimerEvents
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MarketGuideTest {
    @Test
    fun `every guide lists the wiki items in order under their cache names`() {
        val expected =
            mapOf(
                PriceGuide.Ores to WikiGuides.ores,
                PriceGuide.Logs to WikiGuides.logs,
                PriceGuide.HerbsAndPotions to WikiGuides.herbsAndPotions,
                PriceGuide.Runes to WikiGuides.runes,
                PriceGuide.WeaponsAndArmour to WikiGuides.weaponsAndArmour,
            )
        for ((guide, names) in expected) {
            assertEquals(
                names.map { it.lowercase() },
                guide.types.map { it.name.lowercase() },
                guide.title,
            )
        }
    }

    @Test
    fun `no guide repeats an item and every guide fits the price list inventory`() {
        val capacity = checkNotNull(ServerCacheManager.getInventory("inv.ge_pricelist".asRSCM())).size
        for (guide in PriceGuide.entries) {
            assertEquals(guide.items.size, guide.items.toSet().size, guide.title)
            assertTrue(guide.items.size <= capacity, guide.title)
        }
    }

    @Test
    fun `the guides carry the titles the wiki gives the windows`() {
        assertEquals(
            listOf("Ores and Bars", "Logs", "Herbs and Potions", "Runes", "Weapons and Armour"),
            PriceGuide.entries.map { it.title },
        )
    }

    @Test
    fun `an expert's Prices option opens the guide and fills it with the market price`() {
        val experts =
            mapOf(
                "npc.ge_expert_ores" to PriceGuide.Ores,
                "npc.ge_expert_logs" to PriceGuide.Logs,
                "npc.ge_expert_herbs" to PriceGuide.HerbsAndPotions,
                "npc.ge_expert_runes" to PriceGuide.Runes,
                "npc.ge_expert_combat" to PriceGuide.WeaponsAndArmour,
            )
        for ((npc, guide) in experts) {
            val f = GeNpcFixture(priceOf = { 7 })
            f.op(npc, 3)
            f.finish()
            assertTrue(f.player.ui.containsModal("interface.ge_pricelist"), npc)
            val inv = f.player.invMap["inv.ge_pricelist"]
            assertNotNull(inv, npc)
            val shown = inv!!.objs.filterNotNull()
            assertEquals(guide.types.map { it.id }, shown.map { it.id }, npc)
            assertTrue(shown.all { it.count == 7 }, npc)
        }
    }

    @Test
    fun `an item the market has no price for is listed at its cache value and never for free`() {
        val f = GeNpcFixture(priceOf = { null })
        f.op("npc.ge_expert_ores", 3)
        f.finish()
        val shown = checkNotNull(f.player.invMap["inv.ge_pricelist"]).objs.filterNotNull()
        val copper = shown.first { it.id == "obj.copper_ore".asRSCM() }
        assertEquals(checkNotNull(ServerCacheManager.getItem(copper.id)).cost.coerceAtLeast(1), copper.count)
        assertTrue(shown.all { it.count >= 1 })
    }

    @Test
    fun `a longer list does not leave the previous one behind`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_combat", 3)
        f.finish()
        f.op("npc.ge_expert_logs", 3)
        f.finish()
        val shown = checkNotNull(f.player.invMap["inv.ge_pricelist"]).objs.filterNotNull()
        assertEquals(PriceGuide.Logs.types.size, shown.size)
    }

    @Test
    fun `examining a listed item sends the examine with the price shown`() {
        val f = GeNpcFixture(priceOf = { 7 })
        f.op("npc.ge_expert_logs", 3)
        f.finish()
        f.window.examine(f.player, 0)
        assertTrue(f.output().contains("RunClientScript"), f.output())
        f.window.examine(f.player, 99)
    }

    @Test
    fun `closing the window empties the transmitted list`() {
        val f = GeNpcFixture()
        f.op("npc.ge_expert_runes", 3)
        f.finish()
        f.window.close(f.player)
        assertTrue(checkNotNull(f.player.invMap["inv.ge_pricelist"]).isEmpty())
    }

    @Test
    fun `the experts and the tutor are stationary and the experts talk to themselves`() {
        for (name in
            listOf(
                "npc.ge_expert_ores",
                "npc.ge_expert_logs",
                "npc.ge_expert_herbs",
                "npc.ge_expert_runes",
                "npc.ge_expert_combat",
            )) {
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM()))
            assertEquals(0, type.wanderRange, name)
            assertEquals(300, type.timer, name)
        }
        for (name in
            listOf(
                "npc.ge_boss",
                "npc.ge_clerk_1",
                "npc.ge_clerk_2",
                "npc.ge_clerk_3",
                "npc.ge_clerk_4",
                "npc.bond_james_bond",
            )) {
            assertEquals(0, checkNotNull(ServerCacheManager.getNpc(name.asRSCM())).wanderRange, name)
        }
    }

    @Test
    fun `each expert mutters to themselves and reschedules within the wiki's window`() {
        for (name in
            listOf(
                "npc.ge_expert_ores",
                "npc.ge_expert_logs",
                "npc.ge_expert_herbs",
                "npc.ge_expert_runes",
                "npc.ge_expert_combat",
            )) {
            val f = GeNpcFixture()
            val npc = Npc(name, CoordGrid(3170, 3485, 0))
            assertTrue(f.events.publish(AiTimerEvents.Type(npc)), name)
            assertTrue(npc.aiTimerStart in 250..350, "$name ${npc.aiTimerStart}")
        }
    }

    @Test
    fun `the three banker models at the exchange are generic bankers`() {
        for (name in listOf("npc.banker1_east", "npc.banker1_west", "npc.banker2_east")) {
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM()))
            assertTrue(type.isContentType("content.banker"), name)
            assertEquals(0, type.wanderRange, name)
        }
        val deadmanVariant = checkNotNull(ServerCacheManager.getNpc("npc.deadman_banker_grey_west".asRSCM()))
        assertEquals(0, deadmanVariant.wanderRange)
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
