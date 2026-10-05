package org.rsmod.content.quest.area.wilderness.entertheabyss

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The one-way tunnel from the south of the Abyss's outer ring into the Abyssal Nexus. Its Nexus end
 * cannot be entered; players arriving this way already carry the Abyss's skull and prayer drain.
 */
class AbyssNexusPassage : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(OUTER_PASSAGE) { enterNexus() }
        onOpLoc1(NEXUS_PASSAGE) { mes("It looks impossible to pass through from this side.") }
    }

    internal fun ProtectedAccess.enterNexus() {
        telejump(NEXUS_LANDING)
    }

    internal companion object {
        const val OUTER_PASSAGE = "loc.rcu_abyss_to_overseer"
        const val NEXUS_PASSAGE = "loc.rcu_overseer_to_abyss"

        /** The first open tile south of the tunnel's Nexus end. */
        val NEXUS_LANDING = CoordGrid(3039, 4800)
    }
}
