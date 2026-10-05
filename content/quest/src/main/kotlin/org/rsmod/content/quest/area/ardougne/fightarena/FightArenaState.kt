package org.rsmod.content.quest.area.ardougne.fightarena

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

var Player.arenaMetSammy: Boolean by boolVarBit("varbit.arenaquest_met_sammy")
var Player.arenaScorpionIntro: Boolean by boolVarBit("varbit.arenaquest_scorpion_cutscene")
var Player.arenaBouncerIntro: Boolean by boolVarBit("varbit.arenaquest_bouncer_cutscene")
var Player.arenaKhazardIntro: Boolean by boolVarBit("varbit.arenaquest_khazard_cutscene")
var Player.arenaTriedDoor: Boolean by boolVarBit("varbit.arenaquest_attempted_entry")

val Player.wearingKhazardArmour: Boolean
    get() =
        worn.count(FightArenaQuest.KhazardHelmet) > 0 &&
            worn.count(FightArenaQuest.KhazardPlatemail) > 0

val Player.holdsKhazardArmour: Boolean
    get() =
        inv.count(FightArenaQuest.KhazardHelmet) + worn.count(FightArenaQuest.KhazardHelmet) > 0 ||
            inv.count(FightArenaQuest.KhazardPlatemail) +
                worn.count(FightArenaQuest.KhazardPlatemail) > 0

/** Places in the fight arena compound, in world coordinates. */
object FightArenaPlaces {
    /** The prison cell Hengrad shares with the player once Khazard has locked them away. */
    val HengradCell = CoordGrid(2599, 3143, 0)

    /** Outside the arena door, on the prison hall side. Players leave the arena here. */
    val ArenaExit = CoordGrid(2607, 3151, 0)

    /** Inside the arena door: where a visit begins. */
    val ArenaEntry = CoordGrid(2604, 3155, 0)

    val ArenaDoor = CoordGrid(2606, 3152, 0)

    val SammyCell = CoordGrid(2616, 3168, 0)

    val SammyArena = CoordGrid(2603, 3153, 0)
    val JustinArena = CoordGrid(2600, 3166, 0)
    val JustinKhazard = CoordGrid(2602, 3153, 0)
    val GeneralArena = CoordGrid(2600, 3153, 0)
    val FamilyGuard = CoordGrid(2606, 3154, 0)
    val Spectator = CoordGrid(2583, 3171, 0)

    /** Tile of the ogre that attacks Justin in the opening scene, near Justin. */
    val OgreCutscene = CoordGrid(2598, 3168, 0)

    /** Where each opponent first stands; clear of the pillar blocks and 2x2 or 3x3 footprints. */
    val OgreFight = CoordGrid(2597, 3158, 0)
    val ScorpionFight = CoordGrid(2597, 3158, 0)
    val BouncerFight = CoordGrid(2597, 3158, 0)
    val GeneralFight = GeneralArena

    val ArenaGuardCutscene = CoordGrid(2603, 3156, 0)
    val ArenaGuardCutsceneTwo = CoordGrid(2603, 3158, 0)

    const val ArenaRegionId = 10289
}
