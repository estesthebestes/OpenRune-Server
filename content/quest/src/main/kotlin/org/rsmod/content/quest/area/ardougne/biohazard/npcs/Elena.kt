package org.rsmod.content.quest.area.ardougne.biohazard.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.DISTILLATOR
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.ETHENEA
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.LIQUID_HONEY
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.PLAGUE_SAMPLE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GUIDOR_TESTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_TOLD_ELENA
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.SULPHURIC_BROLINE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.vialObj
import org.rsmod.content.quest.area.ardougne.biohazard.VIAL_NONE
import org.rsmod.content.quest.area.ardougne.biohazard.chancyVial
import org.rsmod.content.quest.area.ardougne.biohazard.daVinciVial
import org.rsmod.content.quest.area.ardougne.biohazard.hopsVial
import org.rsmod.content.quest.area.ardougne.biohazard.postQuestChat
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.mudDug
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Elena at home in East Ardougne, where she moves once Plague City frees her (her house npc is a
 * multi on `varbit.plaguecity_elena_at_home`, so the ops sit on the base type). She starts
 * Biohazard, runs the tests on the distillator and sends the player to Guidor, then to the king.
 */
class Elena
@Inject
constructor(
    private val biohazard: BiohazardQuest,
    private val plagueCity: PlagueCityQuest,
    private val objRepo: ObjRepository,
    private val doors: QuestDoors,
) : PluginScript() {

    private val quest
        get() = biohazard.quest

    override fun ScriptContext.startup() {
        onOpNpc1(ELENA) { startDialogue(it.npc) { elena() } }
        onOpNpcU(ELENA) { useOnElena(it.npc, it.objType.internalName) }
        onOpLoc1(FRONT_DOOR) { frontDoor(it.loc) }
    }

    /** Her house is tiny, so the door swings out into the street rather than into the room. */
    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        doors.open(this, door, FRONT_DOOR_OPEN, outward = true)
    }

    private suspend fun ProtectedAccess.useOnElena(npc: Npc, obj: String) {
        arriveDelay()
        faceEntitySquare(npc)
        val onQuest = biohazard.stage(player) in STAGE_STARTED until STAGE_GOT_SAMPLES
        if (obj == DISTILLATOR && onQuest) {
            startDialogue(npc) { returnDistillator() }
            return
        }
        mes("Nothing interesting happens.")
    }

    private suspend fun Dialogue.elena() {
        when (biohazard.stage(player)) {
            0 -> notStarted()
            in STAGE_STARTED until STAGE_GOT_SAMPLES -> {
                if (player.inv.contains(DISTILLATOR)) {
                    returnDistillator()
                } else if (biohazard.stage(player) == STAGE_STARTED) {
                    chatPlayer(happy, "Hello Elena.")
                    chatNpc(
                        neutral,
                        "Have you spoken to Jerico yet? He lives in a house south east of here, " +
                            "north of the chapel. He should be able to get you over the wall.",
                    )
                } else {
                    chatNpc(quiz, "You're back, did you find the distillator?")
                    chatPlayer(sad, "I'm afraid not.")
                    chatNpc(
                        worried,
                        "I can't test the samples without the distillator. Please don't give " +
                            "up until you find it.",
                    )
                }
            }
            in STAGE_GOT_SAMPLES..STAGE_GOT_TOUCH_PAPER -> backAlready()
            STAGE_GUIDOR_TESTED -> guidorReport()
            STAGE_TOLD_ELENA -> {
                chatPlayer(happy, "Hello Elena.")
                chatNpc(angry, "You must go and see King Lathas immediately!")
            }
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatPlayer(happy, "Good day to you, Elena.")
        chatNpc(happy, "You too, thanks for freeing me.")
        if (!plagueCity.quest.isQuestCompleted(player)) {
            return
        }
        chatNpc(sad, "It's just a shame the mourners confiscated my equipment.")
        chatPlayer(quiz, "What did they take?")
        chatNpc(
            neutral,
            "My distillator. I can't test any plague samples without it. They're holding it in " +
                "the Mourner Headquarters in West Ardougne.",
        )
        chatNpc(
            quiz,
            "I must somehow retrieve that distillator if I am to find a cure for this awful " +
                "affliction. Do you think you could help me?",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "I'm busy at the moment, I'm afraid.")
            chatNpc(neutral, "Fair enough.")
            return
        }
        quest.setQuestStage(access, STAGE_STARTED)
        player.mudDug = false
        chatPlayer(happy, "Of course I can help. Where do we start?")
        chatNpc(
            happy,
            "I was hoping you would say that. Unfortunately they discovered the tunnel and " +
                "filled it in. We need another way over the wall.",
        )
        chatPlayer(quiz, "Any ideas?")
        chatNpc(
            neutral,
            "My father's friend Jerico is in communication with West Ardougne. He might be " +
                "able to help us. He lives in a house south east of here, north of the chapel.",
        )
    }

    private suspend fun Dialogue.returnDistillator() {
        chatNpc(quiz, "You're back, did you find the distillator?")
        chatPlayer(happy, "Yes, here it is!")
        if (access.invDel(player.inv, DISTILLATOR).failure) {
            return
        }
        for (item in SAMPLE_KIT) {
            access.invAddOrDrop(objRepo, item)
        }
        biohazard.advanceTo(access, STAGE_GOT_SAMPLES)
        chatNpc(happy, "Great! Now I can finally run these tests.")
        access.soundSynth(TEST_SOUND)
        mesbox("Elena uses the distillator to test some samples.")
        chatNpc(
            confused,
            "I don't understand... the touch paper hasn't changed colour at all...",
        )
        chatPlayer(quiz, "What does that mean?")
        chatNpc(
            neutral,
            "I'm not sure. My old mentor in Varrock, Guidor, might know though. Could you take " +
                "these to him and see what he thinks? You'll need to drop by the chemist in " +
                "Rimmington first to pick up some more touch paper.",
        )
        doubleobjbox(
            PLAGUE_SAMPLE,
            SULPHURIC_BROLINE,
            "Elena gives you a plague sample along with three vials.",
        )
        chatNpc(
            neutral,
            "Now, last time I went to see Guidor, some one told the guards I was carrying some " +
                "plague samples. Annoyingly, that means they've started searching everyone " +
                "going to that part of the city.",
        )
        chatNpc(
            neutral,
            "Ironically, the guards won't be smart enough to recognise the sample itself, so " +
                "you should be able to get that in just fine. They'll probably confiscate the " +
                "rest though.",
        )
        chatPlayer(quiz, "So what do I do?")
        chatNpc(
            neutral,
            "Well I know the chemist has still been managing to get chemicals to Guidor. Maybe " +
                "he can help you smuggle the things in.",
        )
        chatPlayer(happy, "Great, I'll see if he can help. See you soon.")
    }

    private suspend fun Dialogue.backAlready() {
        chatNpc(quiz, "What are you doing back here?")
        when (
            choice4(
                "I just find it hard to say goodbye sometimes.",
                1,
                "I'm afraid I've lost some of the stuff that you gave me...",
                2,
                "I've forgotten what I need to do.",
                3,
                "Nothing.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(sad, "I just find it hard to say goodbye sometimes.")
                chatNpc(
                    neutral,
                    "I see... I understand what you mean... But let's discuss that later.",
                )
                backAlready()
            }
            2 -> lostItems()
            3 -> {
                chatPlayer(confused, "I've forgotten what I need to do.")
                chatNpc(
                    neutral,
                    "Go to Rimmington and get some touch paper from the chemist. Once you've " +
                        "done that, you'll need to take those things to Guidor in Varrock.",
                )
                chatNpc(
                    neutral,
                    "You'll probably need to smuggle some of the items in to stop them getting " +
                        "confiscated. If you need help moving them discreetly, the chemist " +
                        "might be able to assist you.",
                )
                chatPlayer(happy, "Okay, I'll get to it.")
            }
            4 -> chatPlayer(neutral, "Nothing.")
        }
    }

    private suspend fun Dialogue.lostItems() {
        chatPlayer(worried, "I'm afraid I've lost some of the stuff that you gave me...")
        val withErrandBoys =
            listOf(player.chancyVial, player.daVinciVial, player.hopsVial)
                .filter { it != VIAL_NONE }
                .mapNotNull { vialObj(it) }
        val missing = SAMPLE_KIT.filter { !player.inv.contains(it) && it !in withErrandBoys }
        if (missing.isEmpty()) {
            chatNpc(neutral, "Are you sure? Looks like you have everything to me.")
            return
        }
        chatNpc(happy, "That's alright, I've got plenty.")
        if (player.inv.freeSpace() < missing.size) {
            doubleobjbox(
                PLAGUE_SAMPLE,
                SULPHURIC_BROLINE,
                "Elena tries to give you some items but you don't have enough room for them.",
            )
            return
        }
        for (item in missing) {
            access.invAdd(player.inv, item)
        }
        doubleobjbox(PLAGUE_SAMPLE, SULPHURIC_BROLINE, "Elena replaces your lost items.")
        chatNpc(happy, "There you go. That should be everything.")
        chatPlayer(happy, "Great, I'll be on my way.")
    }

    private suspend fun Dialogue.guidorReport() {
        chatNpc(quiz, "You're back! So what did Guidor say?")
        chatPlayer(neutral, "Nothing.")
        chatNpc(confused, "What?")
        chatPlayer(
            neutral,
            "He ran his tests and discovered nothing. The plague... it doesn't exist.",
        )
        chatNpc(confused, "Doesn't exist? But I don't understand... why?")
        chatPlayer(neutral, "I don't know, but I think we're about to uncover something huge.")
        chatNpc(angry, "Well I think there's only one thing for it. The king must be confronted!")
        chatPlayer(quiz, "The king?")
        chatNpc(
            neutral,
            "Yes. King Lathas of East Ardougne. He was the one that had the wall built. He was " +
                "the one who called the mourners in. He must know what's really going on here.",
        )
        chatPlayer(neutral, "Well I'd better go and speak to the king then.")
        chatNpc(neutral, "Be quick. You should find him in the castle.")
        biohazard.advanceTo(access, STAGE_TOLD_ELENA)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello Elena.")
        chatNpc(happy, "Hey, how are you?")
        chatPlayer(happy, "Good thanks, yourself?")
        if (!player.postQuestChat) {
            player.postQuestChat = true
            chatNpc(quiz, "Not bad, did you speak to the king?")
            chatPlayer(neutral, "I did, he admitted the plague is a hoax.")
            chatNpc(quiz, "Did he say why?")
            chatPlayer(neutral, "He did. He asked me to keep it a secret though.")
            chatNpc(confused, "Even from me?")
            chatPlayer(
                neutral,
                "I'm afraid so. Don't worry though, I'm working with the king to resolve all of " +
                    "this.",
            )
            chatNpc(
                worried,
                "I hope so. Even without the plague, I still worry about the people of West " +
                    "Ardougne.",
            )
            return
        }
        chatNpc(
            neutral,
            "Not bad, I do wish you could tell me what's going on with the plague though.",
        )
        chatPlayer(
            neutral,
            "So do I Elena. I promise I'll tell you everything once this is all over.",
        )
        chatNpc(neutral, "I hope that's not too far off.")
        chatPlayer(neutral, "It shouldn't be, don't worry.")
    }

    companion object {
        /** The multi-npc in her house; shows `npc.elena2_vis` once she is home. */
        const val ELENA = "npc.elena2"
        const val FRONT_DOOR = "loc.elenadoor2"
        const val FRONT_DOOR_OPEN = "loc.elenadoor2open"

        const val TEST_SOUND = "synth.bubbling_vials"

        val SAMPLE_KIT = listOf(PLAGUE_SAMPLE, ETHENEA, LIQUID_HONEY, SULPHURIC_BROLINE)
    }
}
