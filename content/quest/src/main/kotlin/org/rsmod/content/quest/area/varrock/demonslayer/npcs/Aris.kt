package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_KEY_HUNT
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.varrock.demonslayer.WallyVision
import org.rsmod.content.quest.area.varrock.demonslayer.silverlightCaseEmpty
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Aris
@Inject
constructor(private val demonSlayer: DemonSlayerQuest, private val vision: WallyVision) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.aris") { startDialogue(it.npc) { aris(it.npc) } }
    }

    private suspend fun Dialogue.aris(npc: Npc) {
        when (demonSlayer.stage(player)) {
            STAGE_COMPLETE -> afterQuest()
            STAGE_STARTED -> afterStarting()
            STAGE_KEY_HUNT ->
                if (player.silverlightCaseEmpty) afterSilverlight() else afterTalkingToPrysin()
            else -> beforeQuest(npc)
        }
    }

    private suspend fun Dialogue.beforeQuest(npc: Npc) {
        chatNpc(
            happy,
            "Hello young one. Cross my palm with silver and the future will be revealed to you.",
        )
        if (access.invCoinTotal() < 1) {
            chatPlayer(sad, "Oh dear. I don't have any money.")
            return
        }
        if (!choice2("Yes.", true, "No.", false)) {
            chatPlayer(neutral, "No, I don't believe in that stuff.")
            chatNpc(neutral, "Ok suit yourself.")
            return
        }
        if (!access.invTakeFee(1)) {
            chatPlayer(sad, "Oh dear. I don't have any money.")
            return
        }
        demonSlayer.rollIncantation(player)
        chatPlayer(happy, "Okay, here you go.")
        npc.anim("seq.qip_ds_reading_crystalball")
        access.soundSynth("synth.crystal_sing")
        chatNpc(
            neutral,
            "Come closer, and listen carefully to what the future holds for you, as I peer " +
                "into the swirling mists of the crystal ball.",
        )
        chatNpc(neutral, "I can see images forming. I can see you.")
        chatNpc(
            neutral,
            "You are holding a very impressive looking sword. I'm sure I recognise that sword...",
        )
        chatNpc(worried, "There is a big dark shadow appearing now.")
        chatNpc(shocked, "Aaargh!")
        chatPlayer(worried, "Are you all right?")
        chatNpc(shocked, "It's Delrith! Delrith is coming!")
        chatPlayer(quiz, "Who's Delrith?")
        chatNpc(worried, "Delrith...")
        chatNpc(worried, "Delrith is a powerful demon.")
        chatNpc(
            worried,
            "Oh! I really hope he didn't see me looking at him through my crystal ball!",
        )
        chatNpc(
            neutral,
            "He tried to destroy this city 150 years ago. He was stopped just in time by the " +
                "great hero Wally.",
        )
        chatNpc(
            neutral,
            "Using his magic sword Silverlight, Wally managed to trap the demon in the stone " +
                "circle just south of this city.",
        )
        chatNpc(
            shocked,
            "Ye gods! Silverlight was the sword you were holding in my vision! You are the one " +
                "destined to stop the demon this time.",
        )
        questionsBeforeVision()
    }

    private suspend fun Dialogue.questionsBeforeVision() {
        while (true) {
            when (
                choice4(
                    "How am I meant to fight a demon who can destroy cities?",
                    1,
                    "Okay, where is he? I'll kill him for you!",
                    2,
                    "Wally doesn't sound like a very heroic name.",
                    3,
                    "So how did Wally kill Delrith?",
                    4,
                    title = "What would you like to say?",
                )
            ) {
                1 -> howToFight()
                2 -> whereIsHe()
                3 -> wallysName()
                else -> {
                    howWallyWon()
                    return questionsAfterVision()
                }
            }
        }
    }

    private suspend fun Dialogue.questionsAfterVision() {
        while (true) {
            when (
                choice5(
                    "How am I meant to fight a demon who can destroy cities?",
                    1,
                    "Okay, where is he? I'll kill him for you!",
                    2,
                    "What is the magical incantation?",
                    3,
                    "Where can I find Silverlight?",
                    4,
                    "Okay, thanks. I'll do my best to stop the demon.",
                    5,
                    title = "What would you like to say?",
                )
            ) {
                1 -> howToFight()
                2 -> whereIsHe()
                3 -> incantation()
                4 -> whereIsSilverlight()
                else -> {
                    chatPlayer(happy, "Ok thanks. I'll do my best to stop the demon.")
                    chatNpc(happy, "Good luck, and may Guthix be with you!")
                    demonSlayer.quest.advanceQuestStage(access)
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.howToFight() {
        chatPlayer(worried, "How am I meant to fight a demon who can destroy cities?!")
        chatNpc(
            neutral,
            "If you face Delrith while he is still weak from being summoned, and use the " +
                "correct weapon, you will not find the task too arduous.",
        )
        chatNpc(
            happy,
            "Do not fear. If you follow the path of the great hero Wally, then you are sure to " +
                "defeat the demon.",
        )
    }

    private suspend fun Dialogue.whereIsHe() {
        chatPlayer(happy, "Okay, where is he? I'll kill him for you!")
        chatNpc(laugh, "Ah, the overconfidence of the young!")
        chatNpc(
            neutral,
            "Delrith can't be harmed by ordinary weapons. You must face him using the same " +
                "weapon that Wally used.",
        )
    }

    private suspend fun Dialogue.wallysName() {
        chatPlayer(quiz, "Wally doesn't sound a very heroic name.")
        chatNpc(
            neutral,
            "Yes I know. Maybe that is why history doesn't remember him. However he was a very " +
                "great hero.",
        )
        chatNpc(
            worried,
            "Who knows how much pain and suffering Delrith would have brought forth without " +
                "Wally to stop him!",
        )
        chatNpc(neutral, "It looks like you are going to need to perform similar heroics.")
    }

    private suspend fun Dialogue.howWallyWon() {
        chatPlayer(quiz, "So how did Wally kill Delrith?")
        chatNpc(
            neutral,
            "Wally managed to arrive at the stone circle just as Delrith was summoned by a cult " +
                "of chaos druids...",
        )
        val played = with(vision) { access.play(demonSlayer) }
        if (!played) {
            narrateVision()
        }
        chatNpc(
            neutral,
            "By reciting the correct magical incantation, and thrusting Silverlight into " +
                "Delrith while he was newly summoned, Wally was able to imprison Delrith in the " +
                "stone block in the centre of the circle.",
        )
        chatNpc(neutral, "Delrith will come forth from the stone circle again.")
        chatNpc(
            worried,
            "I would imagine an evil sorcerer is already starting on the rituals to summon " +
                "Delrith as we speak.",
        )
    }

    private suspend fun Dialogue.narrateVision() {
        chatNpcSpecific("Wally", WALLY, angry, "Die, foul demon!")
        chatNpcSpecific("Wally", WALLY, confused, "Now, what was that incantation again?")
        chatNpcSpecific("Wally", WALLY, angry, demonSlayer.incantationSpoken(player))
        chatNpcSpecific("Wally", WALLY, laugh, "I am the greatest demon slayer EVER!")
    }

    private suspend fun Dialogue.incantation() {
        chatPlayer(quiz, "What is the magical incantation?")
        chatNpc(neutral, "Oh yes, let me think a second...")
        chatNpc(
            happy,
            "Alright, I think I've got it now, it goes.... " +
                "${demonSlayer.incantationSpoken(player, ending = ".")} Have you got that?",
        )
        chatPlayer(neutral, "I think so, yes.")
    }

    private suspend fun Dialogue.whereIsSilverlight() {
        chatPlayer(quiz, "Where can I find Silverlight?")
        chatNpc(
            neutral,
            "Silverlight has been passed down through Wally's descendants. I believe it is " +
                "currently in the care of one of the King's knights called Sir Prysin.",
        )
        chatNpc(
            neutral,
            "He shouldn't be too hard to find. He lives in the royal palace in this city. Tell " +
                "him Aris sent you.",
        )
    }

    private suspend fun Dialogue.whereIsDemon() {
        chatPlayer(quiz, "Where can I find the demon?")
        chatNpc(
            neutral,
            "Just head south and you should find the stone circle just outside the city gate.",
        )
    }

    private suspend fun Dialogue.pressOn() {
        chatPlayer(neutral, "Well I'd better press on with it.")
        chatNpc(neutral, "See you anon.")
    }

    private suspend fun Dialogue.afterStarting() {
        chatNpc(neutral, "Greetings. How goes thy quest?")
        chatPlayer(neutral, "I'm still working on it.")
        chatNpc(happy, "Well if you need any advice I'm always here, young one.")
        while (true) {
            when (
                choice4(
                    "What is the magical incantation?",
                    1,
                    "Where can I find Silverlight?",
                    2,
                    "Stop calling me that!",
                    3,
                    "Well I'd better press on with it.",
                    4,
                    title = "What would you like to say?",
                )
            ) {
                1 -> incantation()
                2 -> whereIsSilverlight()
                3 -> return stopCallingMeThat()
                else -> return pressOn()
            }
        }
    }

    private suspend fun Dialogue.afterTalkingToPrysin() {
        chatNpc(neutral, "How goes the quest?")
        chatPlayer(
            neutral,
            "I found Sir Prysin. Unfortunately I haven't got the sword yet. He's made it " +
                "complicated for me.",
        )
        chatNpc(worried, "Ok, hurry, we haven't much time.")
        while (true) {
            when (
                menu(
                    "What is the magical incantation?" to 1,
                    "Well I'd better press on with it." to 2,
                    title = "What would you like to say?",
                )
            ) {
                1 -> incantation()
                else -> return pressOn()
            }
        }
    }

    private suspend fun Dialogue.afterSilverlight() {
        chatNpc(neutral, "How goes the quest?")
        chatPlayer(neutral, "I have the sword now. I just need to kill the demon, I think.")
        chatNpc(happy, "Yep, that's right.")
        while (true) {
            when (
                choice3(
                    "What is the magical incantation?",
                    1,
                    "Well I'd better press on with it",
                    2,
                    "Where can I find the demon?",
                    3,
                    title = "What would you like to say?",
                )
            ) {
                1 -> incantation()
                2 -> return pressOn()
                else -> return whereIsDemon()
            }
        }
    }

    private suspend fun Dialogue.stopCallingMeThat() {
        chatPlayer(angry, "Stop calling me that!")
        chatNpc(neutral, "In the scheme of things you are very young.")
        when (
            choice2(
                "Ok but how old are you?",
                1,
                "Oh if it's in the scheme of things that's ok.",
                2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Ok, but how old are you?")
                chatNpc(
                    shifty,
                    "Count the number of legs on the stools in the Blue Moon inn, and multiply " +
                        "that number by seven.",
                )
                chatPlayer(confused, "Er, yeah, whatever.")
            }
            else -> {
                chatPlayer(neutral, "Oh if it's in the scheme of things that's ok.")
                chatNpc(happy, "You show wisdom for one so young.")
            }
        }
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Greetings young one.")
        chatNpc(happy, "You're a hero now. That was a good bit of demonslaying.")
        while (true) {
            when (
                choice3(
                    "How do you know I killed it?",
                    1,
                    "Thanks.",
                    2,
                    "Stop calling me that!",
                    3,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "How do you know I killed it?")
                    chatNpc(neutral, "You forget. I'm good at knowing things.")
                }
                2 -> return chatPlayer(happy, "Thanks.")
                else -> return stopCallingMeThat()
            }
        }
    }

    private companion object {
        const val WALLY = "npc.qip_ds_wally"
    }
}
