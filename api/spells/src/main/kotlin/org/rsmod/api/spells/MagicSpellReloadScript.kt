package org.rsmod.api.spells

import jakarta.inject.Inject
import org.rsmod.api.hotreload.types.TypeReloadListeners
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class MagicSpellReloadScript
@Inject
constructor(private val spells: MagicSpellRegistry, private val listeners: TypeReloadListeners) :
    PluginScript() {
    override fun ScriptContext.startup() {
        listeners.add("spells") { changes ->
            val changed = changes.items.count(spells::isSpellObj)
            if (changed == 0) {
                return@add null
            }
            spells.rebuild()
            "$changed spells rebuilt"
        }
    }
}
