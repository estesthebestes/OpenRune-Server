package org.rsmod.content.quest.area.wilderness.entertheabyss

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.ABYSSAL_BOOK
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.EMPTY_ORB
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.FULL_ORB
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.SMALL_POUCH
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_READINGS_TAKEN
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_RESEARCHING
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_SENT_TO_VARROCK
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The Mage of Zamorak in Varrock's Chaos Temple, who only appears once sent there. */
class MageOfZamorakVarrock
@Inject
constructor(private val eta: EnterTheAbyssQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MAGE) {
            if (eta.stage(player) < STAGE_SENT_TO_VARROCK) {
                return@onOpNpc1
            }
            startDialogue(it.npc) { mageDialogue() }
        }
    }

    private suspend fun Dialogue.mageDialogue() {
        if (player.wornOpposedGod() != null) {
            chatNpc(
                angry,
                "How dare you wear such disrespectful attire in this holy place? Remove those " +
                    "immediately if you wish to speak to me.",
            )
            return
        }
        when (eta.stage(player)) {
            STAGE_SENT_TO_VARROCK -> if (player.etaReconsidering) reconsidering() else introduction()
            STAGE_RESEARCHING -> researchProgress()
            STAGE_READINGS_TAKEN -> readingsTaken()
            STAGE_COMPLETE -> afterMiniquest()
        }
    }

    private suspend fun Dialogue.introduction() {
        chatNpc(
            neutral,
            "Ah, you again. The Wilderness is hardly the appropriate place for a conversation " +
                "now, is it? What was it you wanted?",
        )
        chatPlayer(confused, "Err... I didn't really want anything.")
        chatNpc(quiz, "So why did you approach me?")
        chatPlayer(quiz, "I was just wondering why you sell runes in the Wilderness?")
        chatNpc(
            neutral,
            "Well I can't go doing it in the middle of Varrock, can I? In case you hadn't " +
                "noticed, I'm a servant of Zamorak. The Saradominists have made sure that people " +
                "like me are not welcome in these parts.",
        )
        val choice =
            choice2(
                "Where do you get your runes from?",
                1,
                "Interesting. Thanks for the information.",
                2,
            )
        if (choice == 2) {
            chatPlayer(neutral, "Interesting. Thanks for the information.")
            return
        }
        chatPlayer(quiz, "Where do you get your runes from?")
        chatNpc(neutral, "Well we craft them of course.")
        chatPlayer(quiz, "We?")
        chatNpc(
            neutral,
            "My associates and I. Despite the best attempts of the Saradominists, there's still " +
                "more of us around than they'd like.",
        )
        chatPlayer(
            quiz,
            "I can't imagine they like you crafting runes much. Do they not try and stop you?",
        )
        chatNpc(
            laugh,
            "Ha! I'm sure they'd love to, but we have methods of runecrafting that they can only " +
                "dream of!",
        )
        chatPlayer(quiz, "Care to share?")
        chatNpc(
            neutral,
            "Why would I? You are not a member of our institute. How do I know you won't just go " +
                "and share all of our secrets with those Saradominist fools in the Order of " +
                "Wizards.",
        )
        val pitch =
            choice4(
                "Maybe I could make it worth your while?",
                1,
                "But I'm a loyal servant of Zamorak as well!",
                2,
                "You're right. I'm a faithful follower of Saradomin.",
                3,
                "Actually, I'm not interested.",
                4,
            )
        when (pitch) {
            1 -> worthYourWhile()
            2 -> loyalServant()
            3 -> {
                chatPlayer(neutral, "You're right. I'm a faithful follower of Saradomin.")
                chatNpc(angry, "Then you have no place here! Leave, before I make you!")
            }
            4 -> {
                chatPlayer(neutral, "Actually, I'm not interested.")
                chatNpc(neutral, "Then you should be gone, for I am very busy.")
            }
        }
    }

    private suspend fun Dialogue.worthYourWhile() {
        chatPlayer(quiz, "Maybe I could make it worth your while?")
        chatNpc(quiz, "How? What do you have to offer?")
        chatPlayer(quiz, "Well what is it you want?")
        lostAdvantage()
        chatPlayer(happy, "Ah, well I know all about that. I was actually the one to help them do it!")
        chatNpc(shocked, "You did what? You helped the Order of Wizards?")
        chatPlayer(worried, "Err...")
        val answer =
            choice3(
                "Yes, but I can still help you as well.",
                1,
                "I did it so that I could then steal their secrets.",
                2,
                "Sorry, I just remembered that I have to take my pet rat for a walk.",
                3,
            )
        when (answer) {
            1 -> {
                chatPlayer(neutral, "Yes, but I can still help you as well.")
                chatNpc(
                    neutral,
                    "So you're a mercenary with no allegiance? Not the type I like working with, " +
                        "but if you have knowledge of the Rune Essence Mine, I seemingly have " +
                        "little choice.",
                )
                offer()
            }
            2 -> stealSecrets()
            3 -> petRat()
        }
    }

    private suspend fun Dialogue.loyalServant() {
        chatPlayer(neutral, "But I'm a loyal servant of Zamorak as well!")
        chatNpc(
            neutral,
            "Even if you speak the truth, it takes more than just being a follower of Zamorak to " +
                "gain the secrets of the institute. You would need to offer something in return.",
        )
        chatPlayer(quiz, "Like what?")
        lostAdvantage()
        chatPlayer(happy, "Ah, well I know all about that. I was actually the one to help them do it!")
        chatNpc(
            shocked,
            "You did what? You helped the Order of Wizards? I thought you claimed to be a servant " +
                "of Zamorak?",
        )
        chatPlayer(worried, "Err...")
        val answer =
            choice3(
                "I did it so that I could then steal their secrets.",
                1,
                "Okay, fine. I don't really serve Zamorak.",
                2,
                "Sorry, I just remembered that I have to take my pet rat for a walk.",
                3,
            )
        when (answer) {
            1 -> stealSecrets()
            2 -> {
                chatPlayer(sad, "Okay, fine. I don't really serve Zamorak.")
                chatNpc(
                    angry,
                    "Then give me one good reason why I shouldn't have you teleported into the " +
                        "depths of a volcano!",
                )
                val reason =
                    choice2(
                        "Because I can still help you.",
                        1,
                        "Alright, I'll leave. Just don't go teleporting me anywhere.",
                        2,
                    )
                if (reason == 1) {
                    chatPlayer(neutral, "Because I can still help you.")
                    chatNpc(
                        neutral,
                        "You would help both sides for your own gain? I suppose as a " +
                            "Zamorakian, I can respect that, even if I don't like it.",
                    )
                    offer()
                } else {
                    chatPlayer(
                        worried,
                        "Alright, I'll leave. Just don't go teleporting me anywhere.",
                    )
                    chatNpc(angry, "Be glad that I am merciful! Now go!")
                }
            }
            3 -> petRat()
        }
    }

    private suspend fun Dialogue.lostAdvantage() {
        chatNpc(
            neutral,
            "Until recently, our runecrafting secrets allowed us to produce runes at a far " +
                "superior rate compared to the inept Order of Wizards, but something has changed.",
        )
        chatNpc(
            neutral,
            "From what we can gather, they've somehow rediscovered how to access the lost Rune " +
                "Essence Mine.",
        )
    }

    private suspend fun Dialogue.stealSecrets() {
        chatPlayer(shifty, "I did it so that I could then steal their secrets.")
        chatNpc(neutral, "You did? Perhaps I underestimated you.")
        offer()
    }

    private suspend fun Dialogue.petRat() {
        chatPlayer(worried, "Sorry, I just remembered that I have to take my pet rat for a walk.")
        chatNpc(confused, "What?")
        chatPlayer(happy, "Yup! Got to go!")
    }

    private suspend fun Dialogue.offer() {
        player.etaHeardOffer = true
        chatNpc(
            neutral,
            "Alright, if you help us access the Rune Essence Mine, we will share our " +
                "runecrafting secrets with you in return.",
        )
        offerChoice()
    }

    private suspend fun Dialogue.reconsidering() {
        chatNpc(
            quiz,
            "You again. Have you considered my offer? If you help us access the Rune Essence " +
                "Mine, we will share our runecrafting secrets with you in return.",
        )
        offerChoice()
    }

    private suspend fun Dialogue.offerChoice() {
        val choice = choice3("Deal.", 1, "No deal.", 2, "I need to think about it.", 3)
        when (choice) {
            1 -> deal()
            2 -> {
                player.etaReconsidering = true
                chatPlayer(neutral, "No deal.")
                chatNpc(angry, "Fine. I will find another way.")
            }
            3 -> {
                player.etaReconsidering = true
                chatPlayer(neutral, "I need to think about it.")
                chatNpc(neutral, "I will be here once you have decided.")
            }
        }
    }

    private suspend fun Dialogue.deal() {
        chatPlayer(happy, "Deal.")
        chatNpc(
            neutral,
            "Good. Now, all I need from you is the spell that will teleport me to the Rune " +
                "Essence Mine.",
        )
        chatPlayer(worried, "Err... I don't actually know the spell.")
        chatNpc(confused, "What? Then how do you get there.")
        chatPlayer(
            neutral,
            "Oh, well the people who do know the spell just teleport me there directly.",
        )
        chatNpc(
            neutral,
            "Hmm... I see. That makes this slightly more complex, but no matter. You can still " +
                "help us.",
        )
        chatPlayer(quiz, "How?")
        chatNpc(
            neutral,
            "I'll give you a scrying orb with a standard cypher spell cast upon it. The orb will " +
                "absorb mystical energies that it is exposed to.",
        )
        chatNpc(
            neutral,
            "If you teleport to the Rune Essence Mine from three different locations, the orb " +
                "will absorb the energies of the spell and allow us to reverse-engineer the " +
                "magic behind it.",
        )
        chatNpc(quiz, "Do you know of three different people who can teleport you there?")
        chatPlayer(confused, "Maybe?")
        chatNpc(
            neutral,
            "Well if not, I'm sure one of those fools in the Order of Wizards can tell you. Now, " +
                "here's the orb.",
        )
        if (!eta.meetsRequirements(player)) {
            player.etaReconsidering = true
            mesbox("You need to have completed Rune Mysteries to help the Mage of Zamorak.")
            return
        }
        if (!handOrb()) {
            player.etaReconsidering = true
            return
        }
        player.etaReconsidering = false
        player.etaOrbGiven = true
        eta.setStage(access, STAGE_RESEARCHING)
    }

    private suspend fun Dialogue.handOrb(): Boolean {
        if (access.invAdd(access.inv, EMPTY_ORB).failure) {
            objbox(
                EMPTY_ORB,
                "The Mage of Zamorak tries to hand you an orb, but you don't have enough room to " +
                    "take it.",
            )
            return false
        }
        objbox(EMPTY_ORB, "The Mage of Zamorak hands you an orb.")
        return true
    }

    private suspend fun Dialogue.researchProgress() {
        chatNpc(
            quiz,
            "You again. Have you managed to use that scrying orb to obtain the information I need?",
        )
        if (!eta.hasOrb(player) && !orbBanked()) {
            replaceOrb()
            return
        }
        chatPlayer(neutral, "Not yet.")
        chatNpc(
            neutral,
            "You must carry it with you and teleport to the Rune Essence Mine from three " +
                "different locations. Return to me once you have done so.",
        )
        val remaining = eta.readingsRemaining(player)
        val places = if (remaining == 1) "one more location" else "$remaining more locations"
        chatNpc(neutral, "From the look of it, the orb still needs readings from $places.")
    }

    private suspend fun Dialogue.readingsTaken() {
        chatNpc(
            quiz,
            "You again. Have you managed to use that scrying orb to obtain the information I need?",
        )
        when {
            FULL_ORB in player.inv -> handover()
            EMPTY_ORB in player.inv -> {
                chatPlayer(neutral, "Not yet.")
                chatNpc(
                    neutral,
                    "That orb is empty. Teleport to the Rune Essence Mine with it once more and " +
                        "it will pick the readings back up. Return to me once you have done so.",
                )
            }
            orbBanked() -> {
                chatPlayer(neutral, "Yes, but it's in my bank.")
                chatNpc(neutral, "Well it is of no use there. Bring it to me.")
            }
            else -> replaceOrb()
        }
    }

    private suspend fun Dialogue.replaceOrb() {
        chatPlayer(sad, "I lost it. Could I have another?")
        chatNpc(angry, "Fool! Take this, and don't lose it this time!")
        handOrb()
    }

    private fun Dialogue.orbBanked(): Boolean = EMPTY_ORB in access.bank || FULL_ORB in access.bank

    private suspend fun Dialogue.handover() {
        chatPlayer(happy, "Here you go.")
        objbox(FULL_ORB, "You hand the orb to the Mage of Zamorak.")
        chatNpc(neutral, "Right, let's take a look at this orb...")
        chatNpc(
            happy,
            "Yes, this will do nicely. Once again, the Zamorak Magical Institute has overcome " +
                "the Order of Wizards!",
        )
        chatNpc(neutral, "You have done well. Now, time for us to uphold our end of the bargin.")
        chatNpc(
            neutral,
            "The reason we are able to craft so many runes is because we do not visit the runic " +
                "altars in the traditional way. Instead, we have found a way to teleport to them " +
                "directly.",
        )
        chatPlayer(quiz, "How?")
        chatNpc(
            neutral,
            "Via another plane known as the Abyss. It is a complex place that cannot be easily " +
                "explained, but I will share our research notes with you so you may better " +
                "understand it.",
        )
        chatPlayer(quiz, "So can I use the Abyss?")
        chatNpc(
            neutral,
            "Yes. Visit me in the Wilderness whenever you wish to be teleported there. Just be " +
                "careful, for it is a dangerous place. Still, I'm sure you'll agree that the " +
                "risk is worth the reward.",
        )
        chatPlayer(quiz, "How is it dangerous?")
        chatNpc(
            neutral,
            "There are creatures there that will hunt and attack any visitors on sight. The " +
                "magic we use for teleporting there can also be a bit... unstable.",
        )
        chatPlayer(quiz, "What do you mean?")
        chatNpc(neutral, "Just don't expect to be using any prayers in there.")
        chatNpc(
            neutral,
            "Anyway, you may also have this pouch as well. I'm sure you will find it useful. Now, " +
                "we're done here.",
        )
        if (FULL_ORB !in player.inv) {
            mesbox("You no longer have the full scrying orb.")
            return
        }
        val givePouch = !EssencePouch.hasColossalPouch(player) && !hasSmallPouch()
        if (givePouch) {
            doubleobjbox(
                ABYSSAL_BOOK,
                SMALL_POUCH,
                "The Mage of Zamorak hands you a book and a pouch.",
            )
        } else {
            objbox(ABYSSAL_BOOK, "The Mage of Zamorak hands you a book.")
        }
        if (!completeHandover(givePouch)) {
            mesbox("You no longer have the full scrying orb.")
        }
    }

    /**
     * Swaps the full orb for the rewards and finishes the miniquest in a single step, so nothing
     * between taking the orb and paying out can be interrupted. The orb's slot takes the book;
     * anything that still does not fit is dropped at the player's feet. The reward flag stops a
     * repeat payout (items or xp) if the stage is ever rolled back.
     */
    private fun Dialogue.completeHandover(givePouch: Boolean): Boolean {
        if (eta.stage(player) != STAGE_READINGS_TAKEN) {
            return false
        }
        if (access.invDel(access.inv, FULL_ORB).failure) {
            return false
        }
        if (player.etaRewarded) {
            eta.restoreCompletion(player)
            return true
        }
        player.etaRewarded = true
        access.invAddOrDrop(objRepo, ABYSSAL_BOOK)
        if (givePouch) {
            access.invAddOrDrop(objRepo, SMALL_POUCH)
        }
        eta.setStage(access, STAGE_COMPLETE)
        return true
    }

    private fun Dialogue.hasSmallPouch(): Boolean =
        SMALL_POUCH in access.inv || SMALL_POUCH in access.bank

    private suspend fun Dialogue.afterMiniquest() {
        chatNpc(neutral, "Ah, you again. What do you want?")
        afterMiniquestMenu()
    }

    private suspend fun Dialogue.afterMiniquestMenu() {
        val choice =
            choice3(
                "Can you tell me more about the Abyss?",
                1,
                "Can you tell me more about your group?",
                2,
                "I'd better be off.",
                3,
            )
        when (choice) {
            1 -> {
                aboutTheAbyss()
                somethingElse()
            }
            2 -> {
                aboutTheInstitute()
                somethingElse()
            }
            3 -> chatPlayer(neutral, "I'd better be off.")
        }
    }

    private suspend fun Dialogue.somethingElse() {
        chatNpc(quiz, "Now, did you want something else?")
        afterMiniquestMenu()
    }

    private suspend fun Dialogue.aboutTheAbyss() {
        chatPlayer(quiz, "Can you tell me more about the Abyss?")
        chatNpc(
            neutral,
            "It is a hard place to describe. We often refer to it as another plane, but that " +
                "isn't quite accurate. If anything, it is more like a plane that sits between all " +
                "other planes.",
        )
        chatPlayer(quiz, "Right... And what does it have to do with runecrafting?")
        chatNpc(
            neutral,
            "In truth, nothing at all. However, it has everything to do with teleportation. You " +
                "see, time and space work differently in the Abyss.",
        )
        chatNpc(
            neutral,
            "For example, you can travel to the Abyss, walk a few steps and then exit, only to " +
                "find yourself somewhere completely different from where you entered. You might " +
                "even find yourself on a different plane.",
        )
        chatNpc(
            neutral,
            "You can also enter the Abyss and spend days there, but on your return, only " +
                "moments have passed.",
        )
        chatNpc(
            neutral,
            "This is why the Abyss is so useful. It can be used as an effective hub for " +
                "teleportation. In fact, all teleportation that we know of uses the Abyss, you " +
                "just don't realise it.",
        )
        chatPlayer(shocked, "Wait... So when I teleport, I'm actually travelling through the Abyss?")
        chatNpc(
            neutral,
            "In some ways, I suppose you are. Whenever you use a teleport spell, that spell is " +
                "actually travelling through the Abyss and using it to link two places together.",
        )
        chatPlayer(quiz, "So did you discover the Abyss?")
        chatNpc(
            neutral,
            "No. Knowledge of the Abyss has existed for a long time, hence it being used for " +
                "teleportation. However, we believe we may be the first to have properly gained " +
                "access in centuries.",
        )
        chatPlayer(quiz, "How did you manage it?")
        chatNpc(
            neutral,
            "By complete accident. One of our initiates was performing a routine teleportation " +
                "experiment when something went wrong. Instead of ending up at their destination, " +
                "they found themselves in the Abyss.",
        )
    }

    private suspend fun Dialogue.aboutTheInstitute() {
        chatPlayer(quiz, "Can you tell me more about your group?")
        chatNpc(
            neutral,
            "I suppose you have proven yourself trustworthy. We are a group of mages in service " +
                "to Zamorak. Our group is called the Zamorak Magical Institute, or Z.M.I. for " +
                "short.",
        )
        chatNpc(
            neutral,
            "Few actually know of us. Saradominist groups like the Order of Wizards hold sway " +
                "over these lands, so we are forced to work in the shadows. However, make no " +
                "mistake, our power far exceeds theirs.",
        )
        chatPlayer(quiz, "You don't seem to like the Order of Wizards very much.")
        chatNpc(
            angry,
            "And why would we? Did you know that the Order of Wizards wasn't always a " +
                "Saradominist group? Once they allowed mages of all faiths to study with them, but " +
                "they became greedy.",
        )
        chatPlayer(quiz, "What happened?")
        chatNpc(
            angry,
            "They started to desire more control. They banned Zamorakian wizards from certain " +
                "areas of study, claiming them to be too dangerous. In reality, they just wanted " +
                "that knowledge for themselves.",
        )
        chatNpc(
            neutral,
            "It all went wrong for them, of course. A Zamorakian wizard made a great " +
                "breakthrough in teleportation magic, but the Saradominists stole his research.",
        )
        chatNpc(
            neutral,
            "They used it to perform a ritual, but their lack of understanding caused it to go " +
                "wrong. The entire Wizards' Tower burnt down. Many were killed, and years of " +
                "research was destroyed.",
        )
        chatNpc(
            angry,
            "Naturally, they blamed the Zamorakians. Claimed we intentionally burnt the tower " +
                "down. After the tower was rebuilt, we were banished from the Order of Wizards " +
                "and forced underground.",
        )
        chatPlayer(quiz, "And that's how the Z.M.I. was formed?")
        chatNpc(neutral, "Exactly.")
    }

    private companion object {
        const val MAGE = "npc.rcu_zammy_mage1_edge"
    }
}
