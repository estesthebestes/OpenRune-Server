package org.rsmod.content.areas.city.grandexchange

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.plugin.scripts.PluginScript

class GeNpcWiringTest {
    private val injector =
        Guice.createInjector(
            object : AbstractModule() {
                override fun configure() {
                    bind(MarketPrices::class.java)
                        .toInstance(
                            object : MarketPrices {
                                override fun get(type: ItemServerType): Int? = null
                            }
                        )
                    bind(GameRandom::class.java).toInstance(DefaultGameRandom(1L))
                }
            }
        )

    @Test
    fun `the plain scripts are built by the injector`() {
        val scripts =
            listOf(
                PriceListScript::class.java,
                FaridMorrisane::class.java,
                ReloboBlinyo::class.java,
                Hofuthand::class.java,
                BobBarter::class.java,
                MurkyMatt::class.java,
                JamesBonds::class.java,
            )
        for (type in scripts) {
            assertNotNull(injector.getInstance(type), type.simpleName)
            assertTrue(PluginScript::class.java.isAssignableFrom(type))
        }
    }

    @Test
    fun `the tutor has exactly one injectable constructor`() {
        val injectable =
            BrugsenBursen::class.java.declaredConstructors.filter {
                it.isAnnotationPresent(Inject::class.java)
            }
        assertEquals(1, injectable.size)
    }
}
