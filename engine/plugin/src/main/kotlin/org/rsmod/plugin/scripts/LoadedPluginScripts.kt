package org.rsmod.plugin.scripts

import java.util.concurrent.CopyOnWriteArrayList

/** Every plugin script instance that has been started, so hot swap can restart them. */
public object LoadedPluginScripts {
    private val scripts = CopyOnWriteArrayList<PluginScript>()

    public fun add(script: PluginScript) {
        scripts += script
    }

    public fun all(): List<PluginScript> = scripts.toList()

    public fun byClassName(name: String): PluginScript? = scripts.firstOrNull {
        it.javaClass.name == name
    }
}
