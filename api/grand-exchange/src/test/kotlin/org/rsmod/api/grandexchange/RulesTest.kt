package org.rsmod.api.grandexchange

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.grandexchange.draft.OfferMath
import org.rsmod.api.grandexchange.fill.FillModels
import org.rsmod.api.grandexchange.fill.InstantFillModel
import org.rsmod.api.grandexchange.offer.OfferSlot
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.grandexchange.rules.GeTax
import org.rsmod.api.grandexchange.rules.TaxExemptItems

class RulesTest {
    private val tax = GeTax.fromRate(0.02, 5_000_000)

    @Test
    fun `tax is two percent rounded down per item`() {
        assertEquals(0, tax.perItem(1))
        assertEquals(0, tax.perItem(49))
        assertEquals(1, tax.perItem(50))
        assertEquals(1, tax.perItem(99))
        assertEquals(2, tax.perItem(100))
        assertEquals(20, tax.perItem(1000))
        assertEquals(2_400, tax.perItem(120_001))
    }

    @Test
    fun `tax is charged per item and capped at five million`() {
        assertEquals(5_000_000, tax.perItem(250_000_000))
        assertEquals(5_000_000, tax.perItem(2_000_000_000))
        assertEquals(4_999_980, tax.perItem(249_999_000))
        assertEquals(100, tax.total(1000, 5))
        assertEquals(10_000_000, tax.total(2_000_000_000, 2))
    }

    @Test
    fun `a seller at 49 or 50 coins receives the same`() {
        assertEquals(49, 49 - tax.perItem(49))
        assertEquals(49, 50 - tax.perItem(50))
        assertEquals(98, 99 - tax.perItem(99))
        assertEquals(98, 100 - tax.perItem(100))
    }

    @Test
    fun `exempt items pay nothing and rate and cap come from config`() {
        assertEquals(0, tax.perItem(1_000_000, exempt = true))
        val custom = GeTax.fromRate(0.05, 1_000)
        assertEquals(50, custom.ratePermille)
        assertEquals(1_000, custom.perItem(1_000_000))
        assertEquals(0, GeTax.fromRate(0.0, 5_000_000).perItem(1_000_000))
        assertEquals(GeTax.MAX_RATE_PERMILLE, GeTax.fromRate(5.0, 1).ratePermille)
    }

    @Test
    fun `the wiki exempt list is matched by name`() {
        for (name in listOf("Old school bond", "Bronze arrow", "Lobster", "Energy potion(3)", "Energy potion(4)", "Ring of dueling(8)", "Hammer", "Varrock teleport")) {
            assertTrue(TaxExemptItems.isExempt(name), name)
        }
        for (name in listOf("Abyssal whip", "Ring of dueling(7)", "Super energy(4)", "Dragon dart", "Shark")) {
            assertFalse(TaxExemptItems.isExempt(name), name)
        }
    }

    @Test
    fun `slot records survive a round trip including the extremes`() {
        val extremes =
            OfferSlot(
                OfferState.COMPLETED,
                OfferType.SELL,
                SlotCodec.MAX_ITEM_ID,
                Int.MAX_VALUE,
                Int.MAX_VALUE,
                Int.MAX_VALUE,
                OfferSlot.MAX_TOTAL,
                tax = 123,
            )
        assertEquals(extremes, SlotCodec.decode(SlotCodec.encode(extremes), extremes.tax))
        val sample = OfferSlot(OfferState.OPEN, OfferType.BUY, 4151, 10, 1_500, 4, 6_000, 0)
        assertEquals(sample, SlotCodec.decode(SlotCodec.encode(sample)))
        assertEquals(OfferSlot.EMPTY, SlotCodec.decode(SlotCodec.encode(OfferSlot.EMPTY)))
        assertTrue(SlotCodec.encode(OfferSlot.EMPTY).all { it == 0 })
        assertEquals(SlotCodec.WORDS, SlotCodec.encode(sample).size)
    }

    @Test
    fun `random slot records round trip`() {
        val random = Random(42)
        repeat(5_000) {
            val quantity = random.nextInt(1, Int.MAX_VALUE)
            val slot =
                OfferSlot(
                    state = OfferState.entries[random.nextInt(1, 4)],
                    type = OfferType.entries[random.nextInt(2)],
                    itemId = random.nextInt(0, SlotCodec.MAX_ITEM_ID + 1),
                    quantity = quantity,
                    price = random.nextInt(1, Int.MAX_VALUE),
                    completedQuantity = random.nextInt(0, quantity + 1),
                    completedGold = random.nextLong(0, OfferSlot.MAX_TOTAL + 1),
                )
            assertEquals(slot, SlotCodec.decode(SlotCodec.encode(slot)))
        }
    }

    @Test
    fun `the fee saturates into a single int`() {
        assertEquals(0, SlotCodec.clampTax(-5))
        assertEquals(1234, SlotCodec.clampTax(1234))
        assertEquals(Int.MAX_VALUE, SlotCodec.clampTax(Long.MAX_VALUE))
    }

    @Test
    fun `price buttons follow the client scripts`() {
        assertEquals(1001, OfferMath.stepPrice(1000, up = true))
        assertEquals(999, OfferMath.stepPrice(1000, up = false))
        assertEquals(1, OfferMath.stepPrice(1, up = false))
        assertEquals(OfferMath.MAX_PRICE, OfferMath.stepPrice(OfferMath.MAX_PRICE, up = true))

        assertEquals(1050, OfferMath.stepPricePercent(1000, 5, up = true))
        assertEquals(950, OfferMath.stepPricePercent(1000, 5, up = false))
        assertEquals(11, OfferMath.stepPricePercent(10, 5, up = true))
        assertEquals(1, OfferMath.stepPricePercent(1, 5, up = false))
        assertEquals(OfferMath.MAX_PRICE, OfferMath.stepPricePercent(OfferMath.MAX_PRICE - 1, 5, up = true))
        assertEquals(1, OfferMath.stepPricePercent(10, 100, up = false))
    }

    @Test
    fun `quantity buttons follow the client scripts`() {
        val buy = OfferType.BUY
        val sell = OfferType.SELL
        assertEquals(2, OfferMath.stepQuantity(1, 1, buy, 0))
        assertEquals(10, OfferMath.stepQuantity(1, 10, buy, 0))
        assertEquals(110, OfferMath.stepQuantity(10, 100, buy, 0))
        assertEquals(1010, OfferMath.stepQuantity(10, Int.MAX_VALUE, buy, 0))
        assertEquals(1000, OfferMath.stepQuantity(1, Int.MAX_VALUE, buy, 0))
        assertEquals(1, OfferMath.stepQuantity(1, -1, buy, 0))
        assertEquals(5, OfferMath.stepQuantity(6, -1, buy, 0))

        assertEquals(7, OfferMath.stepQuantity(6, 10, sell, 7))
        assertEquals(7, OfferMath.stepQuantity(1, Int.MAX_VALUE, sell, 7))
        assertEquals(3, OfferMath.stepQuantity(2, 1, sell, 7))
        assertEquals(1, OfferMath.stepQuantity(1, 1, OfferType.BUY, 0, isBond = true))
    }

    @Test
    fun `all on a buy offer buys what the coins pay for`() {
        assertEquals(10, OfferMath.affordableQuantity(10_050, 1_000))
        assertEquals(1, OfferMath.affordableQuantity(10, 1_000))
        assertEquals(1, OfferMath.affordableQuantity(0, 1_000))
        assertEquals(Int.MAX_VALUE, OfferMath.affordableQuantity(Long.MAX_VALUE, 1))
    }

    @Test
    fun `fill models are chosen by name`() {
        assertEquals(InstantFillModel, FillModels.create("instant"))
        assertEquals(InstantFillModel, FillModels.create(" Instant "))
        val unknown = runCatching { FillModels.create("gradual") }.exceptionOrNull()
        assertTrue(unknown is IllegalArgumentException)
        assertTrue((unknown as IllegalArgumentException).message!!.contains("instant"))
    }
}
