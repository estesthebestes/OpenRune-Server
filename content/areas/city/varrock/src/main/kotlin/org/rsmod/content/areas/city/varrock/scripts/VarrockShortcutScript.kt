package org.rsmod.content.areas.city.varrock.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockShortcutScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.lumbridge_sc_fencejump") { jumpFence() }
        onOpLoc1(LUMBER_YARD_FENCE) { climbBrokenFence(it.loc) }
    }

    private fun ProtectedAccess.climbBrokenFence(loc: BoundLocInfo) {
        val start = coords
        val fromSouth = start.z <= loc.coords.z
        val dest = loc.coords.translateZ(if (fromSouth) 1 else -1)
        val facing = if (fromSouth) FACE_NORTH else FACE_SOUTH
        exactMove(start, dest, CLIMB_START_CYCLES, CLIMB_END_CYCLES, facing)
        anim("seq.human_walk_style", delay = CLIMB_START_CYCLES)
    }

    private suspend fun ProtectedAccess.jumpFence() {
        arriveDelay()
        if (player.agilityLvl < FENCE_AGILITY) {
            mes("You need an Agility level of $FENCE_AGILITY to negotiate this obstacle.")
            return
        }
        val north = coords.z >= FENCE_NORTH_SIDE.z
        val dest = if (north) FENCE_SOUTH_SIDE else FENCE_NORTH_SIDE
        val facing = if (north) FACE_SOUTH else FACE_NORTH
        exactMove(coords, dest, delay1 = JUMP_START_CYCLES, delay2 = JUMP_END_CYCLES, dir = facing)
        anim("seq.human_spot_jump")
        soundSynth("synth.varrock_fence_jump")
        delay(1)
    }

    private companion object {
        const val FENCE_AGILITY = 13
        const val JUMP_START_CYCLES = 15
        const val JUMP_END_CYCLES = 30
        const val FACE_SOUTH = 0
        const val FACE_NORTH = 1024
        const val CLIMB_START_CYCLES = 30
        const val CLIMB_END_CYCLES = 94
        const val LUMBER_YARD_FENCE = "loc.gertrudefence"
        val FENCE_SOUTH_SIDE = CoordGrid(3240, 3334, 0)
        val FENCE_NORTH_SIDE = CoordGrid(3240, 3335, 0)
    }
}
