package org.rsmod.api.grandexchange

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.grandexchange.engine.CollectPart
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.fill.InstantFillModel
import org.rsmod.api.grandexchange.offer.OfferState
import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.rules.GeTax

/**
 * Drives long random sessions and checks the books after every step: coins and items are only
 * ever created or destroyed by the market and the fee, never by the exchange itself.
 */
class ConservationTest {
    private val tax = GeTax(20, 5_000_000)

    private class World(val exchange: GrandExchange, val players: List<FakePlayer>, val model: RecordingFillModel) {
        val offline = HashSet<Long>()
        val initialCoins = players.sumOf { it.totalCoins() }
        val initialWhips = players.sumOf { it.totalItems(WHIP) }
    }

    private fun run(seed: Int, marketEnabled: Boolean) {
        val random = Random(seed)
        val quotes = FakeQuotes().apply { set(WHIP, 1000, 900) }
        val model = RecordingFillModel(if (marketEnabled) InstantFillModel else NoMarketFillModel)
        val exchange = newExchange(quotes, model, tax)
        val players = (1L..4L).map { FakePlayer(it).apply { coins = 5_000_000; give(WHIP, 200) } }
        val world = World(exchange, players, model)
        players.forEach(exchange::join)

        repeat(1_500) {
            val player = players.random(random)
            val action = random.nextInt(10)
            if (player.id in world.offline && action != 8) {
                return@repeat
            }
            when (action) {
                in 0..4 -> {
                    val type = if (random.nextBoolean()) OfferType.BUY else OfferType.SELL
                    val price = random.nextLong(700, 1300)
                    exchange.place(player, random.nextInt(8), type, WHIP, random.nextInt(1, 30), price)
                }
                5 -> exchange.abort(player, random.nextInt(8))
                6, 7 -> exchange.collect(player, random.nextInt(8), CollectPart.entries.random(random), false, player.wallet)
                8 -> {
                    if (player.id in world.offline) {
                        exchange.join(player)
                        world.offline -= player.id
                    } else {
                        exchange.leave(player)
                        world.offline += player.id
                    }
                }
                else -> {
                    val high = random.nextLong(800, 1200)
                    quotes.set(WHIP, high, high - random.nextLong(10, 150))
                    exchange.sweep(it.toLong(), pricesChanged = true)
                }
            }
            check(world, tax)
        }
    }

    private fun check(world: World, tax: GeTax) {
        val marketBuyQty = world.model.fills.filter { it.type == OfferType.BUY }.sumOf { it.quantity.toLong() }
        val marketBuyGold = world.model.fills.filter { it.type == OfferType.BUY }.sumOf { it.quantity * it.price }
        val marketSellQty = world.model.fills.filter { it.type == OfferType.SELL }.sumOf { it.quantity.toLong() }
        val marketSellGold = world.model.fills.filter { it.type == OfferType.SELL }.sumOf { it.quantity * it.price }
        val fees =
            world.players.sumOf { p ->
                p.notices.filter { it.type == OfferType.SELL }.sumOf { tax.total(it.unitPrice, it.filled) }
            }

        val coins = world.players.sumOf { it.totalCoins() }
        val whips = world.players.sumOf { it.totalItems(WHIP) }
        assertEquals(world.initialCoins + marketSellGold - marketBuyGold, coins + fees, "coins")
        assertEquals(world.initialWhips + marketBuyQty - marketSellQty, whips, "items")

        for (p in world.players) {
            for (slot in p.slots) {
                assertTrue(slot.completedQuantity in 0..slot.quantity)
                assertTrue(slot.state != OfferState.OPEN || slot.remaining > 0)
            }
        }
        val open = world.players.sumOf { p -> p.slots.count { it.isOpen && it.itemId == WHIP && p.id !in world.offline } }
        assertEquals(open, world.exchange.openOfferCount, "book matches the slots of online players")
    }

    @Test
    fun `players only trade`() {
        for (seed in 1..8) run(seed, marketEnabled = false)
    }

    @Test
    fun `players and market`() {
        for (seed in 100..108) run(seed, marketEnabled = true)
    }
}
