package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object GnomeMaze {
    /** Beside the Elkoy who stands outside the hedges at the north-west of the maze. */
    val Entrance = CoordGrid(2503, 3191, 0)

    /** The maze side of the loose railing, beside the Elkoy who stands at the end of the maze. */
    val RailingApproach = CoordGrid(2515, 3160, 0)

    /** The railing hangs on the south edge of this tile; the village itself lies north of it. */
    val RailingTile = CoordGrid(2515, 3161, 0)

    fun southOfRailing(coords: CoordGrid): Boolean = coords.z < RailingTile.z
}

private const val ElkoyHead = "npc.elkoy_1op"
private const val FadeOutDuration = 15
private const val FadeInDuration = 50

internal fun ProtectedAccess.fadeToBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 255,
        endColour = 0,
        endTransparency = 0,
        clientDuration = FadeOutDuration,
    )
}

internal fun ProtectedAccess.fadeFromBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 0,
        endColour = 0,
        endTransparency = 255,
        clientDuration = FadeInDuration,
    )
}

/** Elkoy walks the player through the maze, then has a parting word at the other end. */
internal suspend fun ProtectedAccess.elkoyGuides(
    dest: CoordGrid,
    parting: String,
    box: String? = null,
) {
    if (box != null) {
        mesbox(box)
    }
    fadeToBlack()
    delay(1)
    teleport(dest)
    delay(1)
    fadeFromBlack()
    closeFadeOverlay()
    startDialogue { chatNpcSpecific("Elkoy", ElkoyHead, happy, parting) }
}

/** The loose railing between the end of the maze and the village; it squeezes both ways. */
class LooseRailing @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Railing) { squeeze(it.loc) }
    }

    private suspend fun ProtectedAccess.squeeze(railing: BoundLocInfo) {
        val towardsVillage = GnomeMaze.southOfRailing(player.coords)
        val start = if (towardsVillage) railing.coords.translateZ(-1) else railing.coords
        val end = if (towardsVillage) railing.coords else railing.coords.translateZ(-1)
        if (player.coords != start) {
            playerWalk(start)
            arriveDelay()
        }
        mes("You squeeze through the loose railing.")
        soundSynth(SqueezeSound)
        anim(SqueezeSeq)
        exactMove(
            start = start,
            end = end,
            delay1 = 0,
            delay2 = SqueezeTicks * ClientCyclesPerTick,
            dir = if (towardsVillage) Constants.em_face_north else Constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(SqueezeTicks)
    }

    private companion object {
        const val Railing = "loc.treegnomelooserailing"
        const val SqueezeSeq = "seq.railing_squeeze"
        const val SqueezeSound = "synth.squeeze_thru_crack"
        const val SqueezeTicks = 2
        const val ClientCyclesPerTick = 30
    }
}
