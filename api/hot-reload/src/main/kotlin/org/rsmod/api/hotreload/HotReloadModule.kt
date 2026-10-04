package org.rsmod.api.hotreload

import org.rsmod.plugin.module.PluginModule

public class HotReloadModule : PluginModule() {
    override fun bind() {
        bindBaseInstance<ReloadReporter>(PlayerReloadReporter::class.java)
    }
}
