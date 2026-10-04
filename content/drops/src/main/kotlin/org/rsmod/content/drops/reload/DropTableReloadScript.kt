package org.rsmod.content.drops.reload

import jakarta.inject.Inject
import org.rsmod.api.hotreload.HotReloadRegistry
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DropTableReloadScript
@Inject
constructor(private val registry: HotReloadRegistry, private val target: DropTableReloadTarget) :
    PluginScript() {
    override fun ScriptContext.startup() {
        registry.register(target)
    }
}
