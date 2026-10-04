package org.rsmod.content.quest.area.ardougne.plaguecity.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_DUG_TUNNEL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_REMOVED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_HAS_GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_MOURNER_REFUSED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SNEAKED_IN
import org.rsmod.content.quest.area.ardougne.plaguecity.toldToDig
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mourners the player can talk to during Plague City: the ones patrolling West Ardougne, the
 * Head Mourner, the one snooping around Edmond's garden and the one on guard at the great wall.
 * All of them are multi-npcs, so the ops sit on the base types.
 */
class Mourners @Inject constructor(private val plagueCity: PlagueCityQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (mourner in CITY_MOURNERS) {
            onOpNpc1(mourner) { startDialogue(it.npc) { westArdougneMourner(plagueCity) } }
        }
        onOpNpc1(HEAD_MOURNER) { startDialogue(it.npc) { headMourner() } }
        onOpNpc1(EDMONDS_MOURNER) { startDialogue(it.npc) { nearEdmond() } }
        onOpNpc1(WALL_MOURNER) { startDialogue(it.npc) { atTheWall() } }
    }

    private suspend fun Dialogue.headMourner() {
        if (plagueCity.quest.isQuestCompleted(player)) {
            chatNpc(angry, "Stand back citizen, do not approach me.")
            return
        }
        chatNpc(
            angry,
            "How did you get into West Ardougne? Ah well you'll have to stay, can't risk you " +
                "spreading the plague outside.",
        )
        if (plagueCity.stage(player) in STAGE_MOURNER_REFUSED until STAGE_SNEAKED_IN) {
            when (
                choice4(
                    "I need clearance to enter a plague house.",
                    1,
                    "So what's a mourner?",
                    2,
                    "I haven't got the plague though...",
                    3,
                    "I'm looking for a woman named Elena.",
                    4,
                )
            ) {
                1 -> clearance()
                2 -> whatIsAMourner()
                3 -> notGotThePlague()
                4 -> lookingForElena()
            }
            return
        }
        commonOptions()
    }

    private suspend fun Dialogue.clearance() {
        chatPlayer(
            neutral,
            "I need clearance to enter a plague house. It's in the south east corner of West " +
                "Ardougne.",
        )
        chatNpc(angry, "You must be nuts, absolutely not!")
        when (
            choice3(
                "There's a kidnap victim inside!",
                1,
                "I've got a gas mask though...",
                2,
                "Yes, I'm utterly crazy.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(worried, "There's a kidnap victim inside!")
                chatNpc(
                    neutral,
                    "Well they're as good as dead then, no point in trying to save them.",
                )
            }
            2 -> {
                chatPlayer(neutral, "I've got a gas mask though...")
                chatNpc(
                    neutral,
                    "It's not regulation. Anyway you're not properly trained to deal with the " +
                        "plague.",
                )
                chatPlayer(quiz, "How do I get trained?")
                chatNpc(neutral, "It requires a strict 18 months of training.")
                chatPlayer(sad, "I don't have that sort of time.")
            }
            3 -> {
                chatPlayer(laugh, "Yes, I'm utterly crazy.")
                chatNpc(angry, "You're wasting my time, I have a lot of work to do!")
            }
        }
    }

    private suspend fun Dialogue.nearEdmond() {
        val stage = plagueCity.stage(player)
        when {
            stage == 0 || plagueCity.quest.isQuestCompleted(player) -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(neutral, "Do you have a problem traveller?")
                chatPlayer(
                    quiz,
                    "No, I just wondered why you're wearing that outfit... Is it fancy dress?",
                )
                chatNpc(angry, "No! It's for protection.")
                chatPlayer(quiz, "Protection from what?")
                chatNpc(neutral, "The plague of course...")
            }
            stage < STAGE_HAS_GAS_MASK || (stage == STAGE_HAS_GAS_MASK && !player.toldToDig) -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(shifty, "What are you up to?")
                chatPlayer(quiz, "What do you mean?")
                chatNpc(shifty, "You and that Edmond fella, you're looking very suspicious.")
                chatPlayer(
                    neutral,
                    "We're just gardening. Have you heard any news about West Ardougne?",
                )
                chatNpc(
                    angry,
                    "Just the usual, everyone's sick or dying. I'm furious at King Tyras for " +
                        "bringing this plague to our lands.",
                )
            }
            stage < STAGE_DUG_TUNNEL -> {
                chatPlayer(happy, "Hello.")
                chatNpc(shifty, "What are you up to with old man Edmond?")
                chatPlayer(neutral, "Nothing, we've just been chatting.")
                chatNpc(quiz, "What about his daughter?")
                chatPlayer(confused, "Oh, you know about that then?")
                chatNpc(
                    neutral,
                    "We know about everything that goes on in Ardougne. We have to if we are " +
                        "to contain the plague.",
                )
                chatPlayer(quiz, "Have you see his daughter recently?")
                chatNpc(
                    neutral,
                    "I imagine she's caught the plague. Either way she won't be allowed out of " +
                        "West Ardougne, the risk is too great.",
                )
            }
            stage < STAGE_GRILL_REMOVED -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(shifty, "Been digging have we?")
                chatPlayer(quiz, "What do you mean?")
                chatNpc(neutral, "Your hands are covered in mud.")
                chatPlayer(confused, "Oh that...")
                chatNpc(shifty, "Funny, you don't look like the gardening type.")
                chatPlayer(happy, "Oh no, I love gardening! It's my favorite pastime.")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(shifty, "What are you up to?")
                chatPlayer(neutral, "Nothing.")
                chatNpc(angry, "I don't trust you.")
                chatPlayer(neutral, "You don't have to.")
                chatNpc(
                    angry,
                    "If I find you attempting to cross the wall I'll make sure you never return.",
                )
            }
        }
    }

    private suspend fun Dialogue.atTheWall() {
        if (plagueCity.quest.isQuestCompleted(player)) {
            chatPlayer(happy, "Hi.")
            chatNpc(shifty, "What are you up to?")
            chatPlayer(neutral, "Just sight-seeing.")
            chatNpc(
                neutral,
                "This is no place for sight-seeing. Don't you know there's been a plague " +
                    "outbreak?",
            )
            chatPlayer(neutral, "Yes, I had heard.")
            chatNpc(neutral, "Then I suggest you leave as soon as you can.")
            when (
                choice3(
                    "What brought the plague to Ardougne?",
                    1,
                    "What are the symptoms of the plague?",
                    2,
                    "Thanks for the advice.",
                    3,
                )
            ) {
                1 -> whatBroughtThePlague()
                2 -> symptoms()
                3 -> chatPlayer(neutral, "Thanks for the advice.")
            }
            return
        }
        chatPlayer(happy, "Hello there.")
        chatNpc(neutral, "Can I help you?")
        chatPlayer(quiz, "What are you doing?")
        chatNpc(
            neutral,
            "I'm guarding the border to West Ardougne. No one except we mourners can pass " +
                "through.",
        )
        chatPlayer(quiz, "Why?")
        chatNpc(neutral, "The plague of course. We can't risk cross contamination.")
        when (
            choice3(
                "What brought the plague to Ardougne?",
                1,
                "What are the symptoms of the plague?",
                2,
                "Okay then, see you around.",
                3,
            )
        ) {
            1 -> whatBroughtThePlague()
            2 -> symptoms()
            3 -> {
                chatPlayer(neutral, "Okay then, see you around.")
                chatNpc(shifty, "Maybe...")
            }
        }
    }

    private suspend fun Dialogue.whatBroughtThePlague() {
        chatPlayer(quiz, "What brought the plague to Ardougne?")
        chatNpc(
            neutral,
            "It's all down to King Tyras of West Ardougne. It started when he came back from " +
                "one of his visits to the lands west of here.",
        )
        chatNpc(
            neutral,
            "Some of his men must have unknowingly caught it out there and brought it back " +
                "with them.",
        )
        chatPlayer(quiz, "Does he know how bad the situation is now?")
        chatNpc(
            angry,
            "If he did he wouldn't care. I believe he wants his people to suffer, he's an " +
                "evil man.",
        )
        chatPlayer(quiz, "Isn't that treason?")
        chatNpc(angry, "He's not my king.")
    }

    private suspend fun Dialogue.symptoms() {
        chatPlayer(quiz, "What are the symptoms of the plague?")
        chatNpc(
            neutral,
            "The first signs are typical flu symptoms. These tend to be followed by severe " +
                "nightmares, horrifying hallucinations which drive many to madness.",
        )
        chatPlayer(worried, "Sounds nasty.")
        chatNpc(
            neutral,
            "It gets worse. Next the victim's blood changes into a thick black tar-like " +
                "liquid, at this point they're past help.",
        )
        chatNpc(
            neutral,
            "Their skin is cold to the touch, the victim is now brain dead. Their body " +
                "however lives on driven by the virus, roaming like a zombie, spreading itself " +
                "further wherever possible.",
        )
        chatPlayer(shocked, "I think I've heard enough.")
    }

    companion object {
        /** The plain mourners patrolling West Ardougne; all multi-npcs. */
        val CITY_MOURNERS =
            listOf("npc.mourner1", "npc.mourner2", "npc.mourner3", "npc.mourner_armed")
        const val HEAD_MOURNER = "npc.headmourner"

        /** The one keeping an eye on Edmond's garden. */
        const val EDMONDS_MOURNER = "npc.mournertwa"

        /** The one at the great wall's gate. */
        const val WALL_MOURNER = "npc.mournertwb"
    }
}

/**
 * What any mourner inside West Ardougne says to an outsider. Shared with the plague house guards,
 * who fall back to it when the player has nothing for them.
 */
internal suspend fun Dialogue.westArdougneMourner(plagueCity: PlagueCityQuest) {
    if (plagueCity.quest.isQuestCompleted(player)) {
        chatNpc(angry, "Stand back citizen, do not approach me.")
        return
    }
    chatNpc(
        quiz,
        "Hmmm, how did you get over here? You're not one of this rabble. Ah well, you'll " +
            "have to stay. Can't risk you going back now.",
    )
    commonOptions()
}

private suspend fun Dialogue.commonOptions() {
    when (
        choice3(
            "So what's a mourner?",
            1,
            "I haven't got the plague though...",
            2,
            "I'm looking for a woman named Elena.",
            3,
        )
    ) {
        1 -> whatIsAMourner()
        2 -> notGotThePlague()
        3 -> lookingForElena()
    }
}

private suspend fun Dialogue.whatIsAMourner() {
    chatPlayer(quiz, "So what's a mourner?")
    chatNpc(
        neutral,
        "We're working for King Lathas of East Ardougne. He has tasked us with containing " +
            "the accursed plague sweeping West Ardougne.",
    )
    chatNpc(
        neutral,
        "We also do our best to ease these people's suffering. We're nicknamed mourners " +
            "because we spend a lot of time at plague victim funerals, no one else is allowed " +
            "to risk attending.",
    )
    chatNpc(neutral, "It's a demanding job, and we get little thanks from the people here.")
}

private suspend fun Dialogue.notGotThePlague() {
    chatPlayer(neutral, "I haven't got the plague though...")
    chatNpc(
        neutral,
        "Can't risk you being a carrier. That protective clothing you have isn't regulation " +
            "issue. It won't meet safety standards.",
    )
}

private suspend fun Dialogue.lookingForElena() {
    chatPlayer(neutral, "I'm looking for a woman named Elena.")
    chatNpc(
        neutral,
        "Ah yes, I've heard of her. A healer I believe. She must be mad coming over here " +
            "voluntarily.",
    )
    chatNpc(
        neutral,
        "I hear rumours she has probably caught the plague now. Very tragic, a stupid waste " +
            "of life.",
    )
}
