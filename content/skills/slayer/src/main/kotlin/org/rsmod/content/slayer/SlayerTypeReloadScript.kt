package org.rsmod.content.slayer

import jakarta.inject.Inject
import org.rsmod.api.hotreload.types.TypeReloadListeners
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.superior.SlayerSuperiorManager
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SlayerTypeReloadScript
@Inject
constructor(
    private val listeners: TypeReloadListeners,
    private val superiors: SlayerSuperiorManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        listeners.add("slayer") { changes ->
            if (changes.npcFields.values.none { "paramsRaw" in it }) {
                return@add null
            }
            SlayerTaskManager.invalidateTypeIndexes()
            superiors.invalidateTypeIndexes()
            "slayer npc indexes rebuilt"
        }
    }
}
