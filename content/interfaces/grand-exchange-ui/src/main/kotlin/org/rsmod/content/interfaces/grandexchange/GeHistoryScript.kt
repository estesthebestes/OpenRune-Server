package org.rsmod.content.interfaces.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.script.onIfModalButton
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The trade history list (`ge_history`) and its way back to the offers window. */
internal class GeHistoryScript @Inject constructor(private val windows: GeWindows) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfModalButton("component.ge_history:exchange") { windows.openExchange(this) }
    }
}
