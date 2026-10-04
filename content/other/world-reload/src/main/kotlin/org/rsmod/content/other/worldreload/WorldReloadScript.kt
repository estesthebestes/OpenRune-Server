package org.rsmod.content.other.worldreload

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.hotreload.HotReloadRegistry
import org.rsmod.api.hotreload.types.TypeReloadListeners
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.worldreload.spawns.DeferredNpcRemovals
import org.rsmod.content.other.worldreload.spawns.SpawnReloadTarget
import org.rsmod.content.other.worldreload.types.ShopResetTarget
import org.rsmod.content.other.worldreload.types.ShopStockReloadListener
import org.rsmod.content.other.worldreload.types.TypeReloadTarget
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WorldReloadScript
@Inject
constructor(
    private val registry: HotReloadRegistry,
    private val spawns: SpawnReloadTarget,
    private val types: TypeReloadTarget,
    private val shopReset: ShopResetTarget,
    private val typeListeners: TypeReloadListeners,
    private val shopStock: ShopStockReloadListener,
    private val deferredRemovals: DeferredNpcRemovals,
) : PluginScript() {
    override fun ScriptContext.startup() {
        registry.register(spawns)
        registry.register(types)
        registry.register(shopReset)
        typeListeners.add("shop stock", shopStock)
        onEvent<GameLifecycle.LateCycle> { deferredRemovals.process() }
    }
}
