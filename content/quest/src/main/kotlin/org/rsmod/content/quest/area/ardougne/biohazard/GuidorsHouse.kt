package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.PRIEST_GOWN_BOTTOM
import org.rsmod.content.quest.area.ardougne.PRIEST_GOWN_TOP
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.PLAGUE_SAMPLE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GUIDOR_TESTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.VIALS
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.vialName
import org.rsmod.content.quest.area.ardougne.wearingPriestGown
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * South-east Varrock: the guard on the gate who searches anyone carrying Elena's vials, Guidor's
 * wife Julie, who will only let a priest in to see him, and Guidor himself, a man of science who
 * runs Elena's samples through the touch paper and finds nothing at all. Asyff's free priest gown
 * is handed out through [asyffPriestGown], called from the Fancy Clothes Store script.
 */
class GuidorsHouse
@Inject
constructor(
    private val biohazard: BiohazardQuest,
    private val doors: QuestDoors,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GUARD) {
            startDialogue(it.npc) {
                chatNpc(
                    neutral,
                    "Please don't disturb me, I've got to keep any eye out for suspicious " +
                        "individuals.",
                )
            }
        }
        onOpLoc1(GATE_LEFT) { gate(it.loc, left = true) }
        onOpLoc1(GATE_RIGHT) { gate(it.loc, left = false) }
        onOpNpc1(JULIE) { startDialogue(it.npc) { julie() } }
        onOpNpc1(GUIDOR) { startDialogue(it.npc) { guidor() } }
        onOpLoc1(BEDROOM_DOOR) { bedroomDoor(it.loc) }
    }

    fun asyffOffersGown(player: Player): Boolean =
        biohazard.quest.isQuestInProgress(player) && !player.freeClothes

    suspend fun Dialogue.asyffPriestGown() {
        chatPlayer(quiz, "Do you have a spare Priest Gown?")
        chatNpc(
            neutral,
            "Well I do sell them. I don't hand them out for free though. This is a shop not a " +
                "charity.",
        )
        chatPlayer(worried, "Please! It's really important.")
        chatNpc(
            neutral,
            "Well I suppose you can have this old one. It's not in good enough condition to sell.",
        )
        player.freeClothes = true
        access.invAddOrDrop(objRepo, PRIEST_GOWN_TOP)
        access.invAddOrDrop(objRepo, PRIEST_GOWN_BOTTOM)
        objbox(PRIEST_GOWN_TOP, "You are given a Priest Gown.")
        chatPlayer(happy, "Thank you.")
    }

    private suspend fun ProtectedAccess.gate(gate: BoundLocInfo, left: Boolean) {
        arriveDelay()
        faceLoc(gate)
        val entering = player.coords.x < gate.coords.x
        if (entering) {
            searchPlayer()
        } else if (stayingForGuidor()) {
            return
        }
        val leftGate = if (left) doors.asInfo(gate) else doors.leftOfGate(gate, GATE_LEFT)
        val rightGate = if (left) doors.rightOfGate(gate, GATE_RIGHT) else doors.asInfo(gate)
        doors.openGate(this, leftGate, GATE_LEFT_OPEN, rightGate, GATE_RIGHT_OPEN)
    }

    /** The guard confiscates the vials; the plague sample means nothing to him. */
    private suspend fun ProtectedAccess.searchPlayer() {
        if (biohazard.stage(player) !in STAGE_GOT_SAMPLES..STAGE_GUIDOR_TESTED) {
            return
        }
        val carried = VIALS.filter { inv.contains(it) }
        if (carried.isEmpty() && !inv.contains(PLAGUE_SAMPLE)) {
            return
        }
        for (vial in carried) {
            invDel(inv, vial)
        }
        startDialogue {
            chatNpcSpecific(
                "Guard",
                GUARD,
                neutral,
                "Halt. I need to conduct a search on you. There have been reports of someone " +
                    "bringing a virus into this area of Varrock.",
            )
            mesbox("The guard searches you.")
            for (vial in carried) {
                objbox(vial, "He takes the vial of ${vialName(vial)} from you.")
            }
            chatNpcSpecific("Guard", GUARD, neutral, "You may now pass.")
        }
    }

    private suspend fun ProtectedAccess.stayingForGuidor(): Boolean {
        if (biohazard.stage(player) !in STAGE_GOT_SAMPLES..STAGE_GUIDOR_TESTED) {
            return false
        }
        if (VIALS.none { inv.contains(it) }) {
            return false
        }
        var stay = false
        startDialogue {
            chatPlayer(
                neutral,
                "I should probably talk to Guidor before leaving the area. Otherwise I'll need " +
                    "to get my vials smuggled in again.",
            )
            stay = !choice2("Yes.", true, "No.", false, title = "Leave anyway?")
        }
        return stay
    }

    private suspend fun Dialogue.julie() {
        val stage = biohazard.stage(player)
        when {
            biohazard.quest.isQuestCompleted(player) -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    worried,
                    "Oh hello, I can't chat now. I have to keep an eye on my husband. He's very " +
                        "ill!",
                )
                chatPlayer(sad, "I'm sorry to hear that!")
            }
            stage < STAGE_GOT_TOUCH_PAPER -> chatNpc(
                worried,
                "Oh dear! Oh dear! I don't have time to chat!",
            )
            stage == STAGE_GOT_TOUCH_PAPER && player.wearingPriestGown() -> {
                chatNpc(
                    happy,
                    "A priest! thank goodness! My husband is very ill! Perhaps you could read " +
                        "him his last rites?",
                )
                chatPlayer(neutral, "I'll see what I can do.")
                player.metJulie = true
            }
            stage == STAGE_GOT_TOUCH_PAPER -> {
                chatPlayer(neutral, "Hello. I'm a friend of Elena, here to see Guidor.")
                chatNpc(
                    sad,
                    "I'm afraid that Guidor is not long for this world! So I'm not letting " +
                        "people see him now.",
                )
                chatPlayer(sad, "I'm really sorry to hear about Guidor.")
                chatPlayer(neutral, "But I do have some very important business to attend to!")
                chatNpc(
                    angry,
                    "You heartless rogue! What could be more important than Guidor's life? A " +
                        "life spent well, if not always wisely... I just hope that Saradomin " +
                        "shows mercy on his soul!",
                )
                chatPlayer(quiz, "Guidor is a religious man?")
                chatNpc(
                    neutral,
                    "Oh goodness no! But I am! If only I could get him to see a priest!",
                )
                chatPlayer(quiz, "A priest? Hmmm...")
                player.metJulie = true
            }
            else -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(worried, "Hello there. I fear Guidor may not be long for this world!")
            }
        }
    }

    private suspend fun ProtectedAccess.bedroomDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        val inside = player.coords.x > door.coords.x
        val stage = biohazard.stage(player)
        when {
            inside || stage >= STAGE_GUIDOR_TESTED -> doors.open(this, door, BEDROOM_DOOR_OPEN)
            stage < STAGE_GOT_TOUCH_PAPER ->
                startDialogue {
                    chatPlayer(
                        neutral,
                        "That's someone's bedroom. I'm not going in there without a reason.",
                    )
                }
            player.wearingPriestGown() -> doors.open(this, door, BEDROOM_DOOR_OPEN)
            else -> {
                player.metJulie = true
                startDialogue {
                    chatNpcSpecific(
                        "Julie",
                        JULIE,
                        worried,
                        "Please leave my husband alone. He's very sick, and I don't want anyone " +
                            "bothering him.",
                    )
                    chatPlayer(sad, "I'm sorry to hear that. Is there anything I can do?")
                    chatNpcSpecific(
                        "Julie",
                        JULIE,
                        neutral,
                        "Thank you, but I just want him to see a priest.",
                    )
                    chatPlayer(quiz, "A priest? Hmmm...")
                }
            }
        }
    }

    private suspend fun Dialogue.guidor() {
        val stage = biohazard.stage(player)
        when {
            biohazard.quest.isQuestCompleted(player) -> {
                chatPlayer(happy, "Hello again Guidor. How are you doing?")
                chatNpc(neutral, "I'm hanging in there.")
                chatPlayer(happy, "Good for you.")
            }
            stage >= STAGE_GUIDOR_TESTED -> {
                chatPlayer(happy, "Hello again Guidor.")
                chatNpc(
                    confused,
                    "Well, hello traveller. I still can't understand why they would lie about " +
                        "the plague.",
                )
                chatPlayer(quiz, "It's strange, anyway how are you doing?")
                chatNpc(neutral, "I'm hanging in there.")
                chatPlayer(happy, "Good for you.")
            }
            stage < STAGE_GOT_TOUCH_PAPER || !player.wearingPriestGown() ->
                chatNpc(angry, "I don't really want any visitors just now.")
            else -> testSamples()
        }
    }

    private suspend fun Dialogue.testSamples() {
        chatPlayer(neutral, "Hello, you must be Guidor. I understand that you are unwell.")
        chatNpc(
            angry,
            "Is my wife asking priests to visit me now? I'm a man of science for god's sake. I " +
                "know she means well but it's only a little cough!",
        )
        chatPlayer(neutral, "She made out it was something more serious.")
        chatNpc(neutral, "Well it's not killed me yet. So what do you want?")
        when (
            choice2(
                "I've come to ask your assistance in stopping a plague.",
                1,
                "I was just going to bless your room and I've done that now.",
                2,
            )
        ) {
            1 -> askForHelp()
            2 -> {
                chatPlayer(neutral, "I was just going to bless your room and I've done that now.")
                chatNpc(neutral, "Oh. Goodbye then.")
            }
        }
    }

    private suspend fun Dialogue.askForHelp() {
        chatPlayer(neutral, "I've come to ask your assistance in stopping a plague.")
        chatNpc(quiz, "You mean the plague of West Ardougne?")
        chatPlayer(
            neutral,
            "That's the one. I have a sample here from your former student, Elena.",
        )
        chatNpc(quiz, "Elena eh?")
        chatPlayer(
            neutral,
            "Yes, she wants you to analyse it. You might be the only one who can help.",
        )
        chatNpc(happy, "Right then, sounds like we'd better get to work!")
        chatPlayer(neutral, "I have the plague sample.")
        chatNpc(
            neutral,
            "Now I'll be needing some liquid honey, some sulphuric broline, and then...",
        )
        chatPlayer(quiz, "... some ethenea?")
        chatNpc(happy, "Indeed!")
        when {
            VIALS.any { !player.inv.contains(it) } ->
                chatNpc(
                    neutral,
                    "Look, I need all three reagents to test the plague sample. Come back when " +
                        "you've got them.",
                )
            !player.inv.contains(TOUCH_PAPER) ->
                chatNpc(
                    sad,
                    "Oh. You don't have any touch paper. I won't be able to help after all.",
                )
            !player.inv.contains(PLAGUE_SAMPLE) ->
                chatNpc(
                    neutral,
                    "Seems like you don't actually have the plague sample. It's a long way to " +
                        "come empty handed... and quite a long way back too.",
                )
            else -> {
                for (item in VIALS + TOUCH_PAPER + PLAGUE_SAMPLE) {
                    access.invDel(player.inv, item)
                }
                biohazard.advanceTo(access, STAGE_GUIDOR_TESTED)
                access.soundSynth(TEST_SOUND)
                chatNpc(
                    confused,
                    "Now I'll just apply these to the sample and... I don't get it... the touch " +
                        "paper has remained the same.",
                )
                chatPlayer(
                    neutral,
                    "That's why Elena wanted you to do it, because she wasn't sure what was " +
                        "happening.",
                )
                chatNpc(
                    neutral,
                    "Well that's just it, nothing has happened. I don't know what this sample " +
                        "is, but it certainly isn't toxic.",
                )
                chatPlayer(quiz, "So what about the plague?")
                chatNpc(
                    shocked,
                    "This result can only mean one thing... there is no plague. It seems that " +
                        "someone has been lying about all of it. The only question is... why?",
                )
                chatPlayer(
                    worried,
                    "Well this is worrying. I'd better go and tell Elena right away.",
                )
            }
        }
    }

    private companion object {
        const val GUARD = "npc.bioguard1"
        const val GATE_LEFT = "loc.guidorgatelclosed"
        const val GATE_RIGHT = "loc.guidorgaterclosed"
        const val GATE_LEFT_OPEN = "loc.guidorgatelopen"
        const val GATE_RIGHT_OPEN = "loc.guidorgateropen"
        const val JULIE = "npc.guidors_wife"
        const val GUIDOR = "npc.guidor"
        const val BEDROOM_DOOR = "loc.guidordoor"
        const val BEDROOM_DOOR_OPEN = "loc.guidordooropen"
        const val TEST_SOUND = "synth.bubbling_vials"
    }
}
