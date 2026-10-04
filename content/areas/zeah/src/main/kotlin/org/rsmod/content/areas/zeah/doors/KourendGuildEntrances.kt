package org.rsmod.content.areas.zeah.doors

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.areas.zeah.doors.KourendEntryRules.FARMING_GUILD_LEVEL
import org.rsmod.content.areas.zeah.doors.KourendEntryRules.TITHE_FARM_LEVEL
import org.rsmod.content.areas.zeah.doors.KourendEntryRules.WOODCUTTING_GUILD_LEVEL
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KourendGuildEntrances
@Inject
constructor(private val locRepo: LocRepository, private val npcSearch: NpcSearch) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (leaf in listOf(WOODCUTTING_GATE.left, WOODCUTTING_GATE.right)) {
            onOpLoc1(leaf.closed) { useWoodcuttingGuildGate(it.loc.toLocInfo()) }
        }
        for (leaf in listOf(FARMING_GUILD_DOOR.left, FARMING_GUILD_DOOR.right)) {
            onOpLoc1(leaf.closed) { useFarmingGuildDoor(it.loc.toLocInfo()) }
        }
        onOpLoc1(TITHE_FARM_DOOR) { useTitheFarmDoor(it.loc.toLocInfo()) }
    }

    private suspend fun ProtectedAccess.useWoodcuttingGuildGate(gate: LocInfo) {
        val entering = player.coords.x !in WOODCUTTING_GUILD_X
        if (entering && !KourendEntryRules.canEnterWoodcuttingGuild(player)) {
            mes(
                "You need a Woodcutting level of $WOODCUTTING_GUILD_LEVEL to enter the " +
                    "Woodcutting Guild."
            )
            return
        }
        val route = crossingRoute(player.coords, gate)
        locRepo.openGate(WOODCUTTING_GATE, gate, openTicks(route))
        soundSynth(GATE_OPEN_SOUND)
        crossDoorway(route)
        if (entering) {
            npcFind(gate.coords, BERRY, BERRY_RANGE, HuntVis.Off, npcSearch)?.say(BERRY_WELCOME)
        }
    }

    private suspend fun ProtectedAccess.useFarmingGuildDoor(door: LocInfo) {
        val entering = !door.isOnLocSide(player.coords)
        if (entering && !KourendEntryRules.canEnterFarmingGuild(player)) {
            mes("You need a Farming level of $FARMING_GUILD_LEVEL to enter the Farming Guild.")
            return
        }
        val route = crossingRoute(player.coords, door)
        locRepo.openDoubleDoor(FARMING_GUILD_DOOR, door, openTicks(route))
        soundSynth(DOOR_OPEN_SOUND)
        crossDoorway(route)
    }

    private fun ProtectedAccess.useTitheFarmDoor(door: LocInfo) {
        val entering = !door.isOnLocSide(player.coords)
        if (entering && !KourendEntryRules.canEnterTitheFarm(player)) {
            mes("You need a Farming level of $TITHE_FARM_LEVEL to enter the Tithe Farm.")
            return
        }
        val dest = if (entering) door.coords else door.acrossTile()
        teleport(dest, TeleportType.Exempt)
    }

    private fun ProtectedAccess.openTicks(route: List<CoordGrid>): Int =
        crossingOpenTicks(crossingTiles(player.coords, route))

    internal companion object {
        val WOODCUTTING_GATE =
            DoubleDoor(
                left = Leaf("loc.wcguild_gatel", "loc.wcguild_gatel_open"),
                right = Leaf("loc.wcguild_gater", "loc.wcguild_gater_open"),
            )

        /** The cache names this door's leaves mirrored: `_right_` is the left-hand leaf. */
        val FARMING_GUILD_DOOR =
            DoubleDoor(
                left =
                    Leaf(
                        "loc.kebos_farming_guild_door_right_closed",
                        "loc.kebos_farming_guild_door_right_open",
                    ),
                right =
                    Leaf(
                        "loc.kebos_farming_guild_door_left_closed",
                        "loc.kebos_farming_guild_door_left_open",
                    ),
            )

        const val TITHE_FARM_DOOR = "loc.hosidius_tithe_farm_door"

        /** Everything between the western and eastern gates is inside the guild. */
        val WOODCUTTING_GUILD_X = 1563..1657

        const val BERRY = "npc.wcguild_guard"
        const val BERRY_RANGE = 5
        const val BERRY_WELCOME = "Welcome to the Woodcutting Guild, adventurer."

        const val GATE_OPEN_SOUND = "synth.picketgate_open"
        const val DOOR_OPEN_SOUND = "synth.door_open"
    }
}
