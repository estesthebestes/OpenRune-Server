package org.rsmod.content.areas.zeah.doors

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.LocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KourendQuestDoors @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(FORSAKEN_TOWER_DOOR.closed) { useForsakenTowerDoor(it.loc.toLocInfo()) }
        for (leaf in listOf(TOWER_OF_MAGIC_DOOR.left, TOWER_OF_MAGIC_DOOR.right)) {
            onOpLoc1(leaf.closed) { useTowerOfMagicDoor(it.loc.toLocInfo()) }
        }
    }

    private suspend fun ProtectedAccess.useForsakenTowerDoor(door: LocInfo) {
        val entering = !door.isOnLocSide(player.coords)
        if (entering && !KourendEntryRules.canEnterForsakenTower(player)) {
            mes("The door is locked.")
            return
        }
        val route = crossingRoute(player.coords, door)
        val ticks = crossingOpenTicks(crossingTiles(player.coords, route))
        locRepo.openSingleDoor(door, FORSAKEN_TOWER_DOOR.open, ticks)
        soundSynth(DOOR_OPEN_SOUND)
        crossDoorway(route)
    }

    private suspend fun ProtectedAccess.useTowerOfMagicDoor(door: LocInfo) {
        val entering = door.isOnLocSide(player.coords)
        if (entering && !KourendEntryRules.canEnterTowerOfMagic(player)) {
            startDialogue {
                chatNpcSpecific(
                    TOWER_MAGE_TITLE,
                    TOWER_MAGE,
                    neutral,
                    "The Tower of Magic is off limits. Lord Arceuus does not wish his " +
                        "experiments to be disturbed.",
                )
            }
            return
        }
        val route = crossingRoute(player.coords, door)
        val ticks = crossingOpenTicks(crossingTiles(player.coords, route))
        locRepo.openDoubleDoor(TOWER_OF_MAGIC_DOOR, door, ticks)
        soundSynth(DOOR_OPEN_SOUND)
        crossDoorway(route)
    }

    internal companion object {
        val FORSAKEN_TOWER_DOOR =
            Leaf("loc.lovaquest_tower_entry_door", "loc.lovaquest_tower_entry_door_open")

        val TOWER_OF_MAGIC_DOOR =
            DoubleDoor(
                left = Leaf("loc.arcquest_tower_door_left", "loc.arcquest_tower_door_left_open"),
                right = Leaf("loc.arcquest_tower_door_right", "loc.arcquest_tower_door_right_open"),
            )

        const val TOWER_MAGE = "npc.arceuus_towerguardian"
        const val TOWER_MAGE_TITLE = "Tower Mage"

        const val DOOR_OPEN_SOUND = "synth.door_open"
    }
}
