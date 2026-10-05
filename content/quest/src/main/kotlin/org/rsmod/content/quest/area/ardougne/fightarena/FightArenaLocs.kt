package org.rsmod.content.quest.area.ardougne.fightarena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.ArmourTaken
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.CellKeys
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.HasKeys
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.InPrison
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardBeaten
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardHelmet
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardPlatemail
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.OgreDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.SammyFreed
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.Started
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The two guarded doors into the arena's prison, each with a door guard on the outside. */
enum class ArenaEntrance(val coords: CoordGrid) {
    West(CoordGrid(2585, 3141, 0)),
    East(CoordGrid(2617, 3171, 0)),
}

/**
 * The prison's walls are locs with no open form, so a door "opens" by lifting its wall piece out
 * for a moment and stepping the player across, like the Ardougne gate does.
 */
@Singleton
class FightArenaDoors @Inject constructor(private val locRepo: LocRepository) {

    private val guardedDoorType by lazy {
        ServerCacheManager.getObject(GuardedDoor.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $GuardedDoor")
    }

    /** Whether the player stands on the same side of the wall loc as the loc's own tile. */
    fun onLocTile(player: Player, coords: CoordGrid, angle: LocAngle): Boolean {
        val (near, far) = sides(coords, angle)
        return player.coords.chebyshevDistance(near) <= player.coords.chebyshevDistance(far)
    }

    suspend fun ProtectedAccess.cross(loc: LocInfo) {
        val (near, far) = sides(loc.coords, loc.angle)
        val dest = if (onLocTile(player, loc.coords, loc.angle)) far else near
        soundSynth(DoorSound)
        locRepo.del(loc, HideTicks)
        delay(1)
        teleport(dest, TeleportType.Exempt)
        delay(1)
    }

    suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        cross(LocInfo(loc.layer, loc.coords, loc.entity))
    }

    suspend fun ProtectedAccess.passDoorGuard(entrance: ArenaEntrance) {
        val door = locRepo.findExact(entrance.coords, guardedDoorType) ?: return
        cross(door)
    }

    companion object {
        const val GuardedDoor = "loc.fightarena_door1"
        const val DoorSound = "synth.door_open"
        const val HideTicks = 3

        /** The two tiles either side of a wall loc: the one it stands on, then the one across it. */
        fun sides(coords: CoordGrid, angle: LocAngle): Pair<CoordGrid, CoordGrid> =
            coords to
                when (angle) {
                    LocAngle.West -> coords.translateX(-1)
                    LocAngle.North -> coords.translateZ(1)
                    LocAngle.East -> coords.translateX(1)
                    LocAngle.South -> coords.translateZ(-1)
                }
    }
}

/** The chest of guard armour, the prison doors and gates, and the arena door. */
class FightArenaLocs
@Inject
constructor(
    private val quest: FightArenaQuest,
    private val scenes: FightArenaScenes,
    private val doors: FightArenaDoors,
    private val site: ArenaSite,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(ArmourChest) { searchChest() }

        onOpLoc1(GuardedDoor) { openGuardedDoor(it.loc) }

        onOpLoc1(ArenaDoor) { arenaDoor() }
        onOpLoc1(EscapeDoor) { confirmLeave() }
        onOpLoc2(EscapeDoor) { leaveNow() }

        for (gate in listOf(PrisonGate, SammyGate)) {
            onOpLoc1(gate) { mes("The gate is securely locked.") }
            onOpLocU(gate) { mes("Nothing interesting happens.") }
        }
        onOpLocU(PrisonGate, CellKeys) { keysOnGate(it.loc, sammysCell = false) }
        onOpLocU(SammyGate, CellKeys) { keysOnGate(it.loc, sammysCell = true) }
    }

    private fun ProtectedAccess.searchChest() {
        if (quest.stage(player) < Started) {
            mes("The chest is securely locked.")
            return
        }
        val needHelmet = player.inv.count(KhazardHelmet) + player.worn.count(KhazardHelmet) == 0
        val needPlate = player.inv.count(KhazardPlatemail) + player.worn.count(KhazardPlatemail) == 0
        val needed = listOf(needHelmet, needPlate).count { it }
        if (needed == 0) {
            mes("You search the chest but find nothing.")
            return
        }
        if (inv.freeSpace() < needed) {
            mes(
                "You search the chest and find a helmet and platebody. However, you don't have " +
                    "enough room to take them."
            )
            return
        }
        if (needHelmet) {
            invAddOrDrop(objRepo, KhazardHelmet)
        }
        if (needPlate) {
            invAddOrDrop(objRepo, KhazardPlatemail)
        }
        quest.advanceTo(this, ArmourTaken)
        mes("You search the chest and find some Khazard armour.")
    }

    /**
     * The door guards open their doors to anyone in the armour who has talked their way in; from
     * inside, the doors always open. Otherwise the door is held shut and the guard says so.
     */
    private suspend fun ProtectedAccess.openGuardedDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        val inside = doors.onLocTile(player, door.coords, door.angle)
        val allowed = inside || (player.wearingKhazardArmour && quest.stage(player) >= InPrison)
        if (allowed) {
            with(doors) { cross(door) }
            return
        }
        startDialogue {
            chatPlayer(confused, "This door seems to be locked.")
            chatNpcSpecific(
                "Door Guard",
                DoorGuardHead,
                bored,
                "Well, you could always try asking me nicely.",
            )
        }
    }

    private suspend fun ProtectedAccess.arenaDoor() {
        arriveDelay()
        if (site.isInside(player)) {
            confirmLeave()
            return
        }
        val stage = quest.stage(player)
        if (stage in SammyFreed until KhazardBeaten && stage != OgreDefeated) {
            startDialogue {
                val back =
                    choice2("Yes.", true, "No.", false, title = "Return to the arena?")
                if (back) {
                    with(scenes) { access.returnToArena() }
                }
            }
            return
        }
        if (stage < SammyFreed) {
            player.arenaTriedDoor = true
        }
        mes("The door is securely locked.")
    }

    private suspend fun ProtectedAccess.confirmLeave() {
        arriveDelay()
        if (!site.isInside(player)) {
            return
        }
        startDialogue {
            val leave =
                choice2("Yes.", true, "No.", false, title = "Are you sure you want to leave?")
            if (leave) {
                with(scenes) { access.leaveArena() }
            }
        }
    }

    private suspend fun ProtectedAccess.leaveNow() {
        arriveDelay()
        if (!site.isInside(player)) {
            return
        }
        with(scenes) { leaveArena() }
    }

    private suspend fun ProtectedAccess.keysOnGate(gate: BoundLocInfo, sammysCell: Boolean) {
        arriveDelay()
        faceLoc(gate)
        val stage = quest.stage(player)
        if (sammysCell && stage == HasKeys) {
            with(scenes) { freeSammyWithDialogue() }
            return
        }
        if (stage >= OgreDefeated && gate.coords == HengradGate) {
            mes("You unlock the gate.")
            with(doors) { cross(gate) }
            return
        }
        if (sammysCell && stage >= SammyFreed) {
            mes("You unlock the gate. The cell is empty now.")
            return
        }
        if (stage >= InPrison) {
            mes("You unlock the gate and look inside, but Sammy isn't in this cell.")
            return
        }
        mes("The gate is securely locked.")
    }

    private companion object {
        const val ArmourChest = "loc.arena_guard_chest_shut"
        const val GuardedDoor = "loc.fightarena_door1"
        const val ArenaDoor = "loc.fightarena_door2"
        const val EscapeDoor = "loc.fightarena_door2_escape"
        const val PrisonGate = "loc.arena_prisondoor"
        const val SammyGate = "loc.arena_jeremydoor"
        const val DoorGuardHead = "npc.arena_guard_door_1"

        val HengradGate = CoordGrid(2600, 3142, 0)
    }
}
