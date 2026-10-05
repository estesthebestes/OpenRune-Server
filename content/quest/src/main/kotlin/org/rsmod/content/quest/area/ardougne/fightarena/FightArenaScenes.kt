package org.rsmod.content.quest.area.ardougne.fightarena

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.BouncerDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.CellKeys
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.HasKeys
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardBeaten
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardFight
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.OgreDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.OgreFight
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.SammyFreed
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.ScorpionDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.ScorpionFight
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Opponent
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction

/**
 * The arena scenes: freeing Sammy, the four fights and what follows each of them. State changes
 * are written in plain, non-suspending steps before the scene that explains them, so a player who
 * logs out or is interrupted partway still stands at a stage the guards and Lady Servil can pick
 * up from.
 */
@Singleton
class FightArenaScenes
@Inject
constructor(private val quest: FightArenaQuest, private val site: ArenaSite) {

    /** Sammy's cell is opened with the keys; the quest moves on into the arena. */
    suspend fun ProtectedAccess.freeSammyWithDialogue() {
        if (quest.stage(player) != HasKeys || player.inv.count(CellKeys) == 0) {
            return
        }
        startDialogue { sammyUnlocked() }
        if (quest.stage(player) != HasKeys || player.inv.count(CellKeys) == 0) {
            return
        }
        freeSammy()
    }

    private suspend fun Dialogue.sammyUnlocked() {
        chatPlayer(happy, "Sammy, look, I have the keys!")
        sammy(happy, "You've done it! Quickly, before the guards come back, open the door!")
        mesbox("You unlock the cell door.")
        sammy(
            happy,
            "Thank you, thank you! But I can't leave without my father. He must still be in " +
                "the arena!",
        )
        chatPlayer(neutral, "Then let's find him. I'll follow you.")
    }

    /** The player has the keys and Sammy is out of his cell: on to the arena and the ogre. */
    private suspend fun ProtectedAccess.freeSammy() {
        quest.advanceTo(this, SammyFreed)
        if (!site.enter(this)) {
            return
        }
        openingScene()
    }

    /** Back into the arena for whatever fight the quest stage calls for. */
    suspend fun ProtectedAccess.returnToArena() {
        val stage = quest.stage(player)
        if (stage < SammyFreed || stage >= KhazardBeaten || stage == OgreDefeated) {
            return
        }
        if (!site.enter(this)) {
            return
        }
        when (stage) {
            SammyFreed,
            OgreFight -> {
                populate(withKhazard = false)
                beginFight(Opponent.Ogre)
            }
            ScorpionFight -> {
                populate(withKhazard = true)
                scorpionIntro()
                beginFight(Opponent.Scorpion)
            }
            ScorpionDefeated -> {
                populate(withKhazard = true)
                bouncerIntro()
                beginFight(Opponent.Bouncer)
            }
            BouncerDefeated -> {
                populate(withKhazard = true)
                khazardConfrontation()
            }
            KhazardFight -> {
                populate(withKhazard = false)
                beginFight(Opponent.General)
            }
        }
    }

    /** An arena opponent has fallen to the player. */
    suspend fun ProtectedAccess.opponentDefeated(opponent: Opponent) {
        when (opponent) {
            Opponent.Ogre -> ogreDefeated()
            Opponent.Scorpion -> scorpionDefeated()
            Opponent.Bouncer -> bouncerDefeated()
            Opponent.General -> generalDefeated()
        }
    }

    /** Hengrad has said his piece: the scorpion is next. */
    suspend fun ProtectedAccess.backToArena() {
        quest.advanceTo(this, ScorpionFight)
        if (!site.enter(this)) {
            return
        }
        populate(withKhazard = true)
        scorpionIntro()
        beginFight(Opponent.Scorpion)
    }

    suspend fun ProtectedAccess.leaveArena() {
        site.leave(this, FightArenaPlaces.ArenaExit)
    }

    /** Guards drag a player who left in the middle of the jail scene back to their cell. */
    suspend fun ProtectedAccess.sendToCell() {
        site.leave(this, FightArenaPlaces.HengradCell)
    }

    private suspend fun ProtectedAccess.openingScene() {
        delay(2)
        val sammy = site.spawn(player, SammyType, FightArenaPlaces.SammyArena)
        val justin = site.spawn(player, JustinCutsceneType, FightArenaPlaces.JustinArena)
        val ogre = site.spawn(player, OgreCutsceneType, FightArenaPlaces.OgreCutscene)
        sammy?.faceSquare(FightArenaPlaces.JustinArena)
        delay(1)
        sammy?.say("Father!")
        delay(2)
        justin?.say("Sammy! Stay back!")
        delay(2)
        sammy?.say("The ogre is attacking my father! Help him, please!")
        delay(2)
        ogre?.let(site::remove)
        beginFight(Opponent.Ogre)
        justin?.say("Aaargh!")
    }

    /** Puts the real, attackable opponent into the arena and sets it on the player. */
    private fun ProtectedAccess.beginFight(opponent: Opponent) {
        val (type, at) =
            when (opponent) {
                Opponent.Ogre -> OgreType to FightArenaPlaces.OgreFight
                Opponent.Scorpion -> ScorpionType to FightArenaPlaces.ScorpionFight
                Opponent.Bouncer -> BouncerType to FightArenaPlaces.BouncerFight
                Opponent.General -> GeneralType to FightArenaPlaces.GeneralFight
            }
        if (opponent == Opponent.Ogre) {
            quest.advanceTo(this, OgreFight)
        }
        val npc = site.spawn(player, type, at, Direction.South) ?: return
        site.engage(npc, player)
    }

    /** The onlookers the cache shows in the arena at the player's stage, none of them hostile. */
    private fun ProtectedAccess.populate(withKhazard: Boolean) {
        site.spawn(player, SammyType, FightArenaPlaces.SammyArena)
        if (withKhazard) {
            site.spawn(player, GeneralPropType, FightArenaPlaces.GeneralArena)
            site.spawn(player, JustinType, FightArenaPlaces.JustinKhazard)
            site.spawn(player, FamilyGuardType, FightArenaPlaces.FamilyGuard)
        } else {
            site.spawn(player, JustinType, FightArenaPlaces.JustinArena)
        }
    }

    private suspend fun ProtectedAccess.ogreDefeated() {
        quest.advanceTo(this, OgreDefeated)
        delay(1)
        val general = site.spawn(player, GeneralCutsceneType, FightArenaPlaces.GeneralArena)
        val guardOne = site.spawn(player, GuardCutsceneType, FightArenaPlaces.ArenaGuardCutscene)
        val guardTwo =
            site.spawn(player, GuardCutsceneTwoType, FightArenaPlaces.ArenaGuardCutsceneTwo)
        general?.say("Well fought! I haven't been this entertained in years!")
        delay(2)
        startDialogue { khazardTakesThePlayer() }
        ifCloseChat()
        guardOne?.say("Come along, you!")
        guardTwo?.say("In the cells with you!")
        delay(2)
        mes("The guards drag you away to the cells.")
        site.leave(this, FightArenaPlaces.HengradCell)
    }

    private suspend fun Dialogue.khazardTakesThePlayer() {
        khazard(laugh, "Bravo! A fine display, stranger. Few survive one of my ogres.")
        khazard(neutral, "I have been watching you from my box ever since you set foot in my arena.")
        chatPlayer(quiz, "General Khazard, I presume?")
        khazard(
            angry,
            "Indeed. And you must be the meddler who let my prisoners out of their cell.",
        )
        chatPlayer(angry, "These people don't belong to you. They belong to nobody!")
        sammy(sad, "Please, General, let my father and me go home.")
        khazard(
            laugh,
            "Everybody in these lands belongs to me. But I am a fair man. Let us make a trade. " +
                "Fight for my amusement, and if you win, the Servils walk free.",
        )
        chatPlayer(confused, "And if I refuse?")
        khazard(
            angry,
            "Refuse? You mistake this for a choice. Guards! Take ${player.displayName} to the " +
                "cells!",
        )
    }

    private suspend fun ProtectedAccess.scorpionIntro() {
        if (player.arenaScorpionIntro) {
            return
        }
        player.arenaScorpionIntro = true
        val general = generalProp()
        delay(1)
        general?.say("Ladies and gentlemen! Our new champion has earned a proper challenge!")
        delay(3)
        general?.say("Release the Khazard scorpion!")
        delay(2)
    }

    private suspend fun ProtectedAccess.scorpionDefeated() {
        quest.advanceTo(this, ScorpionDefeated)
        delay(1)
        bouncerIntro()
        beginFight(Opponent.Bouncer)
    }

    private suspend fun ProtectedAccess.bouncerIntro() {
        if (player.arenaBouncerIntro) {
            return
        }
        player.arenaBouncerIntro = true
        val general = generalProp()
        delay(1)
        general?.say("Impressive. But a scorpion is a mere snack. Now meet Bouncer!")
        delay(3)
        general?.say("Let the hellhound loose!")
        delay(2)
    }

    private suspend fun ProtectedAccess.bouncerDefeated() {
        quest.advanceTo(this, BouncerDefeated)
        delay(1)
        khazardConfrontation()
    }

    /** After Bouncer: Khazard keeps his word about the Servils, then comes for the player. */
    private suspend fun ProtectedAccess.khazardConfrontation() {
        val general = generalProp()
        if (!player.arenaKhazardIntro) {
            startDialogue { khazardTurnsOnThePlayer() }
            ifCloseChat()
            player.arenaKhazardIntro = true
        }
        quest.advanceTo(this, KhazardFight)
        general?.let(site::remove)
        removeProps()
        delay(1)
        val npc = site.spawn(player, GeneralType, FightArenaPlaces.GeneralFight, Direction.South)
        npc?.let { site.engage(it, player) }
    }

    private suspend fun Dialogue.khazardTurnsOnThePlayer() {
        khazard(shocked, "Bouncer... dead? That hound was the pride of my arena!")
        khazard(angry, "What manner of monster are you?")
        chatPlayer(angry, "I did what you asked. I won, so the Servils go free. That was the deal.")
        khazard(
            angry,
            "So it was. I keep my bargains. Guards, see the Servils out of my arena.",
        )
        sammy(worried, "Don't fight him! Get out of here while you still can!")
        khazard(
            verymad,
            "But nobody makes a fool of General Khazard and walks away. I'll finish you " +
                "myself!",
        )
    }

    private suspend fun ProtectedAccess.generalDefeated() {
        quest.advanceTo(this, KhazardBeaten)
        delay(1)
        startDialogue {
            chatPlayer(shocked, "Is he... dead?")
            khazard(
                angry,
                "Dead? You arrogant fool! It will take more than an adventurer to kill me.",
            )
            khazard(
                neutral,
                "I should finish you for your insolence, but I will not. You fought better than " +
                    "any of my champions ever did.",
            )
            khazard(
                neutral,
                "For that, you have earned your freedom, and the Servils' with it. Do not " +
                    "mistake this for weakness. Stand against me again and you will not be so " +
                    "lucky.",
            )
            khazard(neutral, "Now go. I have more important business elsewhere.")
        }
        site.leave(this, FightArenaPlaces.ArenaExit)
    }

    private fun ProtectedAccess.removeProps() {
        for (prop in listOf(SammyType, JustinType, FamilyGuardType)) {
            propsOf(prop).forEach(site::remove)
        }
    }

    private fun ProtectedAccess.propsOf(type: String): List<Npc> =
        site.npcsOf(player).filter { it.type.id == type.asRSCM(RSCMType.NPC) }

    private fun ProtectedAccess.generalProp(): Npc? =
        propsOf(GeneralPropType).firstOrNull()
            ?: propsOf(GeneralCutsceneType).firstOrNull()

    private suspend fun Dialogue.khazard(
        mesanim: MesAnimType,
        text: String,
    ) = chatNpcSpecific("General Khazard", GeneralHead, mesanim, text)

    private suspend fun Dialogue.sammy(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Sammy Servil", SammyHead, mesanim, text)

    companion object {
        const val SammyType = "npc.sammy_servil_vis_noop"
        const val SammyHead = "npc.sammy_servil_vis"
        const val JustinType = "npc.justin_servil_vis"
        const val JustinCutsceneType = "npc.justin_servil_cutscene"
        const val GeneralHead = "npc.general_khazard_vis"
        const val GeneralPropType = "npc.general_khazard_vis"
        const val GeneralCutsceneType = "npc.general_khazard_cutscene"
        const val GeneralType = "npc.general_khazard"
        const val FamilyGuardType = "npc.arena_guard_family_vis"
        const val GuardCutsceneType = "npc.arena_guard4_cutscene"
        const val GuardCutsceneTwoType = "npc.arena_guard5_cutscene"
        const val OgreCutsceneType = "npc.arena_ogre_cutscene"
        const val OgreType = "npc.arena_ogre"
        const val ScorpionType = "npc.arena_scorpion"
        const val BouncerType = "npc.arena_bouncer"
    }
}
