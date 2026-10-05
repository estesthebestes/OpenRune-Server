package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.REMAINS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_FIND_GILOB
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Black Guard watchtower south of the camp. Its top floor, up a second ladder, holds what the
 * goblins left of Gilob; the remains multiloc only shows at [STAGE_FIND_GILOB].
 */
class GilobsWatchtower
@Inject
constructor(private val dwarfCannon: DwarfCannonQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(REMAINS_LOC) { takeRemains() }
        onOpLoc1(UPPER_LADDER) { climbUp() }
    }

    private suspend fun ProtectedAccess.takeRemains() {
        arriveDelay()
        if (dwarfCannon.stage(player) != STAGE_FIND_GILOB) {
            return
        }
        if (REMAINS in inv) {
            startDialogue { chatPlayer(confused, "What? Carrying one set of Dwarf remains is enough...") }
            return
        }
        anim(PICKUP_ANIM)
        delay(1)
        startDialogue { chatPlayer(sad, "I had better take these remains.") }
        soundSynth(PICKUP_SOUND)
        invAddOrDrop(objRepo, REMAINS)
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        anim(CLIMB_ANIM)
        delay(1)
        telejump(player.coords.translateLevel(1))
    }

    private companion object {
        const val REMAINS_LOC = "loc.mcannonremains_multiloc"
        const val UPPER_LADDER = "loc.mcannonladder"

        const val PICKUP_ANIM = "seq.human_pickupfloor"
        const val PICKUP_SOUND = "synth.pick"
        const val CLIMB_ANIM = "seq.human_reachforladder"
    }
}
