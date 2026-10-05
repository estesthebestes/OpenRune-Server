package org.rsmod.content.areas.city.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class PriceListScript @Inject constructor(private val window: PriceListWindow) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfClose(PriceListWindow.INTERFACE) { window.close(player) }
        onIfModalButton(PriceListWindow.LIST) { window.examine(player, it.comsub) }
    }
}
