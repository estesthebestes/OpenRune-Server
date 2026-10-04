package org.rsmod.api.hotreload.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.server.config.DropRatesYaml
import org.rsmod.api.server.config.GameplayConfig
import org.rsmod.api.server.config.ServerConfig

class RestartOnlyFieldsTest {
    private val base =
        ServerConfig(name = "OpenRune", gamePort = 43594, revision = 241, environment = "LIVE", world = 255)

    @Test
    fun `gameplay changes need no restart`() {
        val changed = base.copy(gameplay = GameplayConfig(dropRates = DropRatesYaml(2.0)))
        assertTrue(RestartOnlyFields.diff(base, changed).isEmpty())
    }

    @Test
    fun `port and world changes are flagged`() {
        val changed = base.copy(gamePort = 43595, world = 1)
        val warnings = RestartOnlyFields.diff(base, changed)
        assertEquals(2, warnings.size)
        assertTrue(warnings[0].startsWith("game-port"))
        assertTrue(warnings[1].startsWith("world"))
    }
}
