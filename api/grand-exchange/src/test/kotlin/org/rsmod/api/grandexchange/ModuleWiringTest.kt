package org.rsmod.api.grandexchange

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.fill.InstantFillModel
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.rules.BuyLimits
import org.rsmod.api.grandexchange.rules.NoBuyLimits
import org.rsmod.api.market.MarketModule
import org.rsmod.api.market.MarketPriceSource
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.server.config.GameplayConfig
import org.rsmod.api.server.config.GrandExchangeTaxYaml
import org.rsmod.api.server.config.GrandExchangeYaml
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.server.services.Service

class ModuleWiringTest {
    private fun injector(yaml: GrandExchangeYaml = GrandExchangeYaml()) =
        Guice.createInjector(
            MarketModule,
            GrandExchangeModule(),
            object : AbstractModule() {
                override fun configure() {
                    val config =
                        ServerConfig(
                            name = "test",
                            gamePort = 1,
                            revision = 241,
                            environment = "TEST",
                            world = 1,
                            gameplay = GameplayConfig(grandExchange = yaml),
                        )
                    bind(ServerConfig::class.java).toInstance(config)
                }
            },
        )

    @Test
    fun `the exchange and everything it needs can be built from the config`() {
        val injector = injector(GrandExchangeYaml(tax = GrandExchangeTaxYaml(rate = 0.01, cap = 1_000)))
        val exchange = injector.getInstance(GrandExchange::class.java)
        assertEquals(10, exchange.taxRatePermille)
        assertSame(exchange, injector.getInstance(GrandExchange::class.java))
        assertNotNull(injector.getInstance(GePrices::class.java))
        assertSame(NoBuyLimits, injector.getInstance(BuyLimits::class.java))
        assertNotNull(injector.getInstance(MarketPrices::class.java))
        assertEquals("instant", InstantFillModel.name)
    }

    @Test
    fun `the price service and the guide price source join the shared sets`() {
        val injector = injector()
        val services = injector.getInstance(Key.get(object : TypeLiteral<Set<Service>>() {}))
        assertEquals(1, services.size)
        val sources = injector.getInstance(Key.get(object : TypeLiteral<Set<MarketPriceSource>>() {}))
        assertEquals(1, sources.size)
    }

    @Test
    fun `the documented example config parses into the settings the exchange uses`() {
        val example = java.nio.file.Paths.get("..", "..", "game.example.yml")
        val config = org.rsmod.api.server.config.ServerConfigLoader().read(example)
        val settings = GrandExchangeSettings.from(config.gameplay.grandExchange)
        assertEquals(true, settings.enabled)
        assertEquals("instant", settings.fillModel)
        assertEquals(true, settings.liveFetch)
        assertEquals(5L, settings.refreshMinutes)
        assertEquals(java.nio.file.Paths.get(".data/ge/prices.json"), settings.snapshotPath)
        assertEquals("OpenRune-Server GE price feed (private server) - github.com/OpenRune", settings.userAgent)
        assertEquals(0.02, settings.taxRate)
        assertEquals(5_000_000L, settings.taxCap)
    }

    @Test
    fun `an unknown fill model falls back to instant instead of failing the boot`() {
        val injector = injector(GrandExchangeYaml(fillModel = "gradual"))
        assertNotNull(injector.getInstance(GrandExchange::class.java))
    }
}
