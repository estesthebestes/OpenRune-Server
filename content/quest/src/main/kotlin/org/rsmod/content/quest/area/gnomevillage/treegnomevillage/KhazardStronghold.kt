package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.HasOrb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Orb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.StrongholdBreached
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Khazard stronghold at the north end of the battlefield: the wall the ballista crumbles, the
 * door that only opens from the inside, the chest holding the first orb and the two commanders
 * guarding it. The ladder between the floors is the generic ladder script's.
 */
class KhazardStronghold
@Inject
constructor(
    private val quest: TreeGnomeVillageQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val search: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CrumbledWall) { climbWall(it.loc) }
        onOpLoc1(Door) { openDoor(it.loc) }
        onOpLoc1(ChestClosed) { openChest(it.loc) }
        onOpLoc1(ChestOpen) { searchChest() }
        onOpLoc2(ChestOpen) { closeChest(it.loc) }
    }

    /** The wall is the stronghold's south face; the inside is north of it. */
    private suspend fun ProtectedAccess.climbWall(wall: BoundLocInfo) {
        if (quest.stage(player) < StrongholdBreached) {
            mesbox("The wall is far too high and solid to climb.")
            return
        }
        val goingIn = player.coords.z < wall.coords.z
        val start = if (goingIn) wall.coords.translateZ(-1) else wall.coords.translateZ(1)
        val end = if (goingIn) wall.coords.translateZ(1) else wall.coords.translateZ(-1)
        if (player.coords != start) {
            playerWalk(start)
            arriveDelay()
        }
        if (goingIn) {
            mesbox(
                "The wall has been reduced to rubble. It should be possible to climb over the " +
                    "remains..."
            )
        }
        soundSynth(ClimbSound)
        anim(ClimbSeq)
        exactMove(
            start = start,
            end = end,
            delay1 = 0,
            delay2 = ClimbTicks * ClientCyclesPerTick,
            dir = if (goingIn) Constants.em_face_north else Constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(ClimbTicks)
        if (goingIn) {
            confront()
        }
    }

    /** The ground floor commander spots the intruder the first time they climb in. */
    private suspend fun ProtectedAccess.confront() {
        val commander = findCommander() ?: return
        if (commander.isInCombat()) {
            return
        }
        commander.say("What? How did you manage to get in here.")
        say("I've come for the orb.")
        commander.say("I'll never let you take it.")
        commander.opPlayer2(player, aiInteractions)
    }

    private fun ProtectedAccess.alertCommander(shout: String) {
        val commander = findCommander() ?: return
        if (commander.isInCombat()) {
            return
        }
        commander.say(shout)
        commander.opPlayer2(player, aiInteractions)
    }

    private fun ProtectedAccess.findCommander() =
        npcFind(player.coords, Commander, CommanderRadius, HuntVis.Off, search)

    /** The door only opens from the inside, which is north of it. */
    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        if (player.coords.z <= door.coords.z) {
            mesbox(
                "The door seems to be locked from the inside. I'll need to find another way to " +
                    "get in."
            )
            return
        }
        soundSynth(DoorSound)
        locRepo.del(door, DoorOpenTicks)
        locRepo.add(
            door.coords.translateZ(1),
            DoorOpened,
            DoorOpenTicks,
            door.turnAngle(rotations = 1),
            door.shape,
        )
        playerWalk(door.coords)
    }

    private fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        alertCommander("Oi! You! Get out of there.")
        soundSynth(ChestOpenSound)
        locRepo.del(chest, ChestOpenTicks)
        locRepo.add(chest.coords, ChestOpen, ChestOpenTicks, chest.angle, chest.shape)
    }

    private fun ProtectedAccess.closeChest(chest: BoundLocInfo) {
        soundSynth(ChestCloseSound)
        locRepo.del(chest, ChestOpenTicks)
        locRepo.add(chest.coords, ChestClosed, ChestOpenTicks, chest.angle, chest.shape)
    }

    private suspend fun ProtectedAccess.searchChest() {
        val stage = quest.stage(player)
        val orbHere =
            stage == StrongholdBreached || (stage == HasOrb && !player.inv.contains(Orb))
        if (!orbHere) {
            mes("You find nothing of interest.")
            return
        }
        anim(SearchSeq)
        invAddOrDrop(objRepo, Orb)
        quest.advanceTo(this, HasOrb)
        objbox(
            Orb,
            "You search the chest. Inside you find the gnomes' stolen orb of protection.",
        )
    }

    private companion object {
        const val CrumbledWall = "loc.khazzacklowwall"
        const val Door = "loc.khazard_stronghold_door"
        const val DoorOpened = "loc.poordooropen"
        const val ChestClosed = "loc.chestclosed_khazard"
        const val ChestOpen = "loc.chestopen_khazard"
        const val Commander = "npc.khazard_commander"
        const val CommanderRadius = 12

        const val ClimbSeq = "seq.human_walk_crumbledwall"
        const val ClimbSound = "synth.climb_wall"
        const val ClimbTicks = 3
        const val ClientCyclesPerTick = 30

        const val SearchSeq = "seq.human_pickupfloor"
        const val DoorSound = "synth.door_open"
        const val DoorOpenTicks = 3
        const val ChestOpenSound = "synth.chest_open"
        const val ChestCloseSound = "synth.chest_close"
        const val ChestOpenTicks = 100
    }
}
