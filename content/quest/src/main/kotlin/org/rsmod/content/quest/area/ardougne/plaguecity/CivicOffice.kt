package org.rsmod.content.quest.area.ardougne.plaguecity

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.HANGOVER_CURE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.SCRUFFY_NOTE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_CLERK_PERMISSION
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_CURED_BRAVEK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GOT_WARRANT
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_TALKED_BRAVEK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_TALKED_MILLI
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.WARRANT
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Civic Office of West Ardougne on the north side of the town square: the clerk at the front
 * desk, the hungover city warder Bravek behind his door, and the office's big double doors.
 */
class CivicOffice
@Inject
constructor(private val plagueCity: PlagueCityQuest, private val doors: QuestDoors) :
    PluginScript() {

    private val drinkingType =
        ServerCacheManager.getNpc(BRAVEK_DRINKING.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $BRAVEK_DRINKING")

    override fun ScriptContext.startup() {
        onOpNpc1(CLERK) { startDialogue(it.npc) { clerk() } }
        onOpNpc1(CLERK_ASSISTANT) {
            startDialogue(it.npc) {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    neutral,
                    "Sorry, I don't have time to talk. Bravek needs lots of help running the " +
                        "city.",
                )
            }
        }
        onOpNpc1(BRAVEK) { startDialogue(it.npc) { bravek(it.npc) } }
        onOpNpcU(BRAVEK) { useOnBravek(it.npc, it.objType.internalName) }
        onOpLoc1(BRAVEK_DOOR) { bravekDoor(it.loc) }
        onOpLoc1(DOUBLE_DOOR_LEFT) {
            doors.openDouble(
                this,
                doors.asInfo(it.loc),
                DOUBLE_DOOR_LEFT_OPEN,
                doors.rightLeafOf(it.loc, DOUBLE_DOOR_RIGHT),
                DOUBLE_DOOR_RIGHT_OPEN,
            )
        }
        onOpLoc1(DOUBLE_DOOR_RIGHT) {
            doors.openDouble(
                this,
                doors.leftLeafOf(it.loc, DOUBLE_DOOR_LEFT),
                DOUBLE_DOOR_LEFT_OPEN,
                doors.asInfo(it.loc),
                DOUBLE_DOOR_RIGHT_OPEN,
            )
        }
    }

    private suspend fun Dialogue.clerk() {
        val stage = plagueCity.stage(player)
        if (stage == STAGE_CLERK_PERMISSION) {
            chatNpc(neutral, "Bravek will see you now but keep it short!")
            chatPlayer(happy, "Thanks, I won't take much of his time.")
            return
        }
        chatNpc(
            happy,
            "Hello, welcome to the Civic Office of West Ardougne. How can I help you?",
        )
        if (stage in STAGE_TALKED_MILLI until STAGE_CLERK_PERMISSION) {
            when (
                choice3(
                    "I need permission to enter a plague house.",
                    1,
                    "Who is through that door?",
                    2,
                    "I'm just looking thanks.",
                    3,
                )
            ) {
                1 -> plagueHousePermission()
                2 -> throughThatDoor(canAskUrgent = true)
                3 -> chatPlayer(neutral, "I'm just looking thanks.")
            }
            return
        }
        when (choice2("Who is through that door?", 1, "I'm just looking thanks.", 2)) {
            1 -> throughThatDoor(canAskUrgent = false)
            2 -> chatPlayer(neutral, "I'm just looking thanks.")
        }
    }

    private suspend fun Dialogue.throughThatDoor(canAskUrgent: Boolean) {
        chatPlayer(quiz, "Who is through that door?")
        chatNpc(neutral, "The city warder Bravek is in there.")
        chatPlayer(quiz, "Can I go in?")
        if (plagueCity.stage(player) > STAGE_CLERK_PERMISSION) {
            chatNpc(neutral, "I suppose so.")
            return
        }
        chatNpc(neutral, "He has asked not to be disturbed.")
        if (canAskUrgent) {
            disturbBravek()
        }
    }

    private suspend fun Dialogue.plagueHousePermission() {
        chatPlayer(neutral, "I need permission to enter a plague house.")
        chatNpc(
            shocked,
            "Rather you than me! The mourners normally deal with that stuff, you should speak " +
                "to them. Their headquarters are right near the city gate.",
        )
        when (
            choice2(
                "I'll try asking them then.",
                1,
                "Surely you don't let them run everything for you?",
                2,
            )
        ) {
            1 -> chatPlayer(neutral, "I'll try asking them then.")
            2 -> {
                chatPlayer(quiz, "Surely you don't let them run everything for you?")
                chatNpc(
                    neutral,
                    "Well, they do know what they're doing here. If they did start doing " +
                        "something badly Bravek, the city warder, would have the power to " +
                        "override them. I can't see that happening though.",
                )
                when (
                    choice3(
                        "I'll try asking them then.",
                        1,
                        "Can I speak to Bravek anyway?",
                        2,
                        "This is urgent though! Someone's been kidnapped!",
                        3,
                    )
                ) {
                    1 -> chatPlayer(neutral, "I'll try asking them then.")
                    2 -> {
                        chatPlayer(quiz, "Can I speak to Bravek anyway?")
                        chatNpc(neutral, "He has asked not to be disturbed.")
                        disturbBravek()
                    }
                    3 -> urgent()
                }
            }
        }
    }

    private suspend fun Dialogue.disturbBravek() {
        when (
            choice3(
                "This is urgent though! Someone's been kidnapped!",
                1,
                "Okay, I'll leave him alone.",
                2,
                "Do you know when he will be available?",
                3,
            )
        ) {
            1 -> urgent()
            2 -> chatPlayer(neutral, "Okay, I'll leave him alone.")
            3 -> {
                chatPlayer(quiz, "Do you know when he will be available?")
                chatNpc(neutral, "Oh I don't know, an hour or so maybe.")
            }
        }
    }

    private suspend fun Dialogue.urgent() {
        chatPlayer(worried, "This is urgent though! Someone's been kidnapped!")
        chatNpc(neutral, "I'll see what I can do I suppose.")
        chatNpc(neutral, "Mr Bravek, there's someone here who really needs to speak to you.")
        plagueCity.advanceTo(access, STAGE_CLERK_PERMISSION)
        chatNpcSpecific(
            "Bravek",
            BRAVEK,
            drunk,
            "I suppose they can come in then. If they keep it short.",
        )
    }

    private suspend fun ProtectedAccess.bravekDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        val inside = player.coords.x >= door.coords.x
        if (inside || plagueCity.stage(player) >= STAGE_CLERK_PERMISSION) {
            doors.open(this, door, BRAVEK_DOOR_OPEN)
            return
        }
        startDialogue {
            chatNpcSpecific(
                "Bravek",
                BRAVEK,
                angry,
                "Go away, I'm busy! I'm... Umm... In a meeting!",
            )
        }
    }

    private suspend fun ProtectedAccess.useOnBravek(npc: Npc, obj: String) {
        arriveDelay()
        faceEntitySquare(npc)
        val stage = plagueCity.stage(player)
        if (obj == HANGOVER_CURE && stage in STAGE_CLERK_PERMISSION..STAGE_TALKED_BRAVEK) {
            startDialogue(npc) { giveCure(npc) }
            return
        }
        mes("Bravek doesn't seem interested in that.")
    }

    private suspend fun Dialogue.bravek(npc: Npc) {
        when (plagueCity.stage(player)) {
            in 0 until STAGE_CLERK_PERMISSION -> {
                chatPlayer(happy, "Hey Bravek.")
                chatNpc(
                    neutral,
                    "Hello there. Can't stop to talk I'm afraid, I've been left with a lot of " +
                        "work to do thanks to those mourners.",
                )
            }
            STAGE_CLERK_PERMISSION -> headHurts()
            STAGE_TALKED_BRAVEK -> {
                chatNpc(
                    drunk,
                    "Uurgh! My head still hurts too much to think straight. Oh for one of " +
                        "Trudi's hangover cures!",
                )
                if (player.inv.contains(HANGOVER_CURE)) {
                    giveCure(npc)
                } else if (!player.inv.contains(SCRUFFY_NOTE)) {
                    chatPlayer(quiz, "Do you know what's in the cure?")
                    handNote()
                }
            }
            STAGE_CURED_BRAVEK -> {
                chatNpc(happy, "Thanks again for the hangover cure.")
                chatNpc(quiz, "Ah now what was it you wanted me to do for you?")
                chatPlayer(
                    neutral,
                    "I need to rescue Elena. She's now a kidnap victim! She's being held in a " +
                        "plague house, I need permission to enter.",
                )
                chatNpc(neutral, "Well the mourners deal with that sort of thing...")
                warrantOptions()
            }
            else -> {
                chatNpc(happy, "Thanks again for the hangover cure.")
                chatPlayer(happy, "Not a problem, happy to help out.")
                chatNpc(
                    happy,
                    "I'm just having a little drop of whisky, then I'll feel really good.",
                )
            }
        }
    }

    private suspend fun Dialogue.headHurts() {
        chatNpc(drunk, "My head hurts! I'll speak to you another day...")
        when (choice2("This is really important though!", 1, "Okay, goodbye.", 2)) {
            1 -> {
                chatPlayer(worried, "This is really important though!")
                chatNpc(
                    drunk,
                    "I can't possibly speak to you with my head spinning like this... I went a " +
                        "bit heavy on the drink again last night. Curse my herbalist, she made " +
                        "the best hang over cures. Darn inconvenient of her catching the plague.",
                )
                when (
                    choice3(
                        "Okay, goodbye.",
                        1,
                        "You shouldn't drink so much then!",
                        2,
                        "Do you know what's in the cure?",
                        3,
                    )
                ) {
                    1 -> chatPlayer(neutral, "Okay, goodbye.")
                    2 -> shouldntDrink()
                    3 -> askCure()
                }
            }
            2 -> chatPlayer(neutral, "Okay, goodbye.")
        }
    }

    private suspend fun Dialogue.shouldntDrink() {
        chatPlayer(angry, "You shouldn't drink so much then!")
        chatNpc(
            drunk,
            "Well positions of responsibility are hard, I need something to take my mind " +
                "off things... Especially with the problems this place has.",
        )
        when (
            choice3(
                "Okay, goodbye.",
                1,
                "Do you know what's in the cure?",
                2,
                "I don't think drink is the solution.",
                3,
            )
        ) {
            1 -> chatPlayer(neutral, "Okay, goodbye.")
            2 -> askCure()
            3 -> {
                chatPlayer(neutral, "I don't think drink is the solution.")
                chatNpc(
                    drunk,
                    "I don't feel well enough to have a philosophical discussion about it " +
                        "right now. My head hurts.",
                )
                when (choice2("Do you know what's in the cure?", 1, "Okay, goodbye.", 2)) {
                    1 -> askCure()
                    2 -> chatPlayer(neutral, "Okay, goodbye.")
                }
            }
        }
    }

    private suspend fun Dialogue.askCure() {
        chatPlayer(quiz, "Do you know what's in the cure?")
        handNote()
    }

    private suspend fun Dialogue.handNote() {
        chatNpc(
            drunk,
            "Hmmm let me think... Ouch! Thinking isn't clever. Ah here, she did scribble it " +
                "down for me.",
        )
        if (player.inv.isFull()) {
            objbox(
                SCRUFFY_NOTE,
                "Bravek waves a tatty piece of paper at you, but you don't have room to take it.",
            )
            return
        }
        access.invAdd(player.inv, SCRUFFY_NOTE)
        player.gotNote = true
        plagueCity.advanceTo(access, STAGE_TALKED_BRAVEK)
        objbox(SCRUFFY_NOTE, "Bravek hands you a tatty piece of paper.")
    }

    private suspend fun Dialogue.giveCure(npc: Npc) {
        chatPlayer(happy, "Try this.")
        if (access.invDel(player.inv, HANGOVER_CURE).failure) {
            return
        }
        plagueCity.advanceTo(access, STAGE_CURED_BRAVEK)
        access.npcChangeType(npc, drinkingType, DRINK_TICKS)
        access.soundSynth(DRINK_SOUND)
        objbox(
            HANGOVER_CURE,
            "You give Bravek the hangover cure. Bravek gulps down the foul-looking liquid.",
        )
        npc.say("Grruurgh!")
        access.delay(2)
        npc.resetTransmog()
        chatNpc(
            happy,
            "Ooh that's much better! Thanks, that's the clearest my head has felt in a month. " +
                "Ah now, what was it you wanted me to do for you?",
        )
        chatPlayer(
            neutral,
            "I need to rescue a kidnap victim called Elena. She's being held in a plague " +
                "house, I need permission to enter.",
        )
        chatNpc(neutral, "Well the mourners deal with that sort of thing...")
        warrantOptions()
    }

    private suspend fun Dialogue.warrantOptions() {
        when (
            choice3(
                "Okay, I'll go speak to them.",
                1,
                "Is that all anyone says around here?",
                2,
                "They won't listen to me!",
                3,
            )
        ) {
            1 -> chatPlayer(neutral, "Okay, I'll go speak to them.")
            2 -> {
                chatPlayer(angry, "Is that all anyone says around here?")
                chatNpc(neutral, "Well, they know best about plague issues.")
                when (
                    choice2(
                        "Don't you want to take an interest in it at all?",
                        1,
                        "They won't listen to me!",
                        2,
                    )
                ) {
                    1 -> noInterest()
                    2 -> wontListen()
                }
            }
            3 -> wontListen()
        }
    }

    private suspend fun Dialogue.noInterest() {
        chatPlayer(quiz, "Don't you want to take an interest in it at all?")
        chatNpc(
            worried,
            "Nope, I don't wish to take a deep interest in plagues. That stuff is too scary " +
                "for me!",
        )
        when (
            choice3(
                "I see why people say you're a weak leader.",
                1,
                "Okay, I'll talk to the mourners.",
                2,
                "They won't listen to me!",
                3,
            )
        ) {
            1 -> {
                chatPlayer(angry, "I see why people say you're a weak leader.")
                chatNpc(
                    neutral,
                    "Bah, people always criticise their leaders but delegating is the only way " +
                        "to lead. I delegate all plague issues to the mourners.",
                )
                chatPlayer(angry, "This whole city is a plague issue!")
            }
            2 -> chatPlayer(neutral, "Okay, I'll talk to the mourners.")
            3 -> wontListen()
        }
    }

    private suspend fun Dialogue.wontListen() {
        chatPlayer(angry, "They won't listen to me!")
        chatPlayer(
            neutral,
            "They say I'm not properly equipped to go in the house, though I do have a very " +
                "effective gas mask.",
        )
        chatNpc(
            neutral,
            "Hmmm, well I guess they're not taking the issue of a kidnapping seriously " +
                "enough. They do go a bit far sometimes.",
        )
        chatNpc(
            happy,
            "I've heard of Elena, she has helped us a lot... Okay, I'll give you this warrant " +
                "to enter the house.",
        )
        if (player.inv.isFull()) {
            objbox(WARRANT, "Bravek waves a warrant at you, but you don't have room to take it.")
            return
        }
        access.invAdd(player.inv, WARRANT)
        plagueCity.advanceTo(access, STAGE_GOT_WARRANT)
        objbox(WARRANT, "Bravek hands you a warrant.")
    }

    companion object {
        const val CLERK = "npc.clerk"
        const val CLERK_ASSISTANT = "npc.clerk2"
        const val BRAVEK = "npc.bravek"
        const val BRAVEK_DRINKING = "npc.bravek_hangover_cure_anim"

        const val BRAVEK_DOOR = "loc.bravekdoorshut"
        const val BRAVEK_DOOR_OPEN = "loc.bravekdooropen"
        const val DOUBLE_DOOR_LEFT = "loc.w_ardougnedoubledoorl"
        const val DOUBLE_DOOR_RIGHT = "loc.w_ardougnedoubledoorr"
        const val DOUBLE_DOOR_LEFT_OPEN = "loc.w_ardougnedoubledoorlopen"
        const val DOUBLE_DOOR_RIGHT_OPEN = "loc.w_ardougnedoubledoorropen"

        const val DRINK_SOUND = "synth.drink"
        const val DRINK_TICKS = 4
    }
}
