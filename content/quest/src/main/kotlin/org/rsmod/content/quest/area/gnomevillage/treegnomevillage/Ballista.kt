package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.StrongholdBreached
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.TrackersSent
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gnome ballista in the south-west corner of the battlefield. The height and y coordinates are
 * entered for the player; the x coordinate is the one the mad tracker hinted at, picked from a
 * menu. A direct hit breaches the stronghold wall for good.
 */
class Ballista
@Inject
constructor(
    private val quest: TreeGnomeVillageQuest,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc2(BallistaLoc) { fire(it.loc) }
    }

    private suspend fun ProtectedAccess.fire(ballista: BoundLocInfo) {
        val stage = quest.stage(player)
        when {
            stage < TrackersSent ->
                mesbox(
                    "The ballista is damaged. It cannot be used until the gnomes have finished " +
                        "their repairs."
                )
            stage >= StrongholdBreached ->
                mesbox("The Khazard stronghold has already been breached.")
            !player.hasKnownAllCoordinates() ->
                mesbox(
                    "You don't have the stronghold's coordinates yet. The tracker gnomes should " +
                        "have them."
                )
            else -> aim(ballista)
        }
    }

    private suspend fun ProtectedAccess.aim(ballista: BoundLocInfo) {
        startDialogue {
            chatPlayer(
                quiz,
                "That tracker gnome was a bit vague about the x coordinate! What could it be?",
            )
        }
        val x =
            choice4(
                "0001",
                1,
                "0002",
                2,
                "0003",
                3,
                "0004",
                4,
                title = "Enter the x-coordinate of the stronghold",
            )
        mesbox("You enter the height and y coordinates you got from the tracker gnomes.")
        faceLoc(ballista)
        soundArea(worldRepo, ballista.coords, LaunchSound, radius = SoundRadius)
        locAnim(worldRepo, ballista, LaunchSeq)
        delay(FlightTicks)
        if (x - 1 != player.ballistaAnswer) {
            mesbox("The huge spear completely misses the Khazard stronghold!")
            startDialogue { chatPlayer(sad, "Evidently that wasn't the right x coordinate.") }
            return
        }
        soundArea(worldRepo, StrongholdWall, HitSound, radius = SoundRadius)
        quest.advanceTo(this, StrongholdBreached)
        mesbox(
            "The huge spear flies through the air and screams down directly into the Khazard " +
                "stronghold. A deafening crash echoes over the battlefield as the front " +
                "entrance is reduced to rubble."
        )
    }

    private companion object {
        const val BallistaLoc = "loc.catabow"
        const val LaunchSeq = "seq.catabow_launch"
        const val LaunchSound = "synth.treevillage_catabow_launch"
        const val HitSound = "synth.treevillage_catabow_hit"
        const val SoundRadius = 15
        const val FlightTicks = 4

        val StrongholdWall = CoordGrid(2509, 3253, 0)
    }
}
