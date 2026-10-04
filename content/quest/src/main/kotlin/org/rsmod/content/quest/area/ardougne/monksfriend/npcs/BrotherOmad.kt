package org.rsmod.content.quest.area.ardougne.monksfriend.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.BlanketReturned
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.CedricFound
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.ChildsBlanket
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.LawRune
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.LawRuneReward
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Monk
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Omad
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.PartyStarted
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.SentForCedric
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Started
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.WoodGiven
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BrotherOmad
@Inject
constructor(private val monksFriend: MonksFriendQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    private val quest
        get() = monksFriend.quest

    override fun ScriptContext.startup() {
        onOpNpc1(Omad) { startDialogue(it.npc) { omadDialogue() } }
        onOpNpcU(Omad) {
            if (it.objType.internalName == ChildsBlanket && monksFriend.stage(player) == Started) {
                startDialogue(it.npc) { omadDialogue() }
            } else {
                mes("Nothing interesting happens.")
            }
        }
    }

    private suspend fun Dialogue.omadDialogue() {
        when (quest.questState(player)) {
            QuestProgressState.NOT_STARTED -> beforeQuest()
            QuestProgressState.IN_PROGRESS -> duringQuest()
            QuestProgressState.FINISHED -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatPlayer(quiz, "Hello there. Are you all right?")
        chatNpc(
            sad,
            "*yawn* Oh... hello. I'm sorry, I can barely keep my eyes open. I haven't had a " +
                "proper night's sleep in days.",
        )
        val why = choice2("Why can't you sleep?", true, "I'm too busy to chat.", false)
        if (!why) {
            chatPlayer(neutral, "I'm too busy to chat.")
            chatNpc(neutral, "Ah, of course. Don't let me keep you. *yawn*")
            return
        }
        chatPlayer(quiz, "Why can't you sleep?")
        chatNpc(
            sad,
            "It's the little one we look after here at the monastery. Some thieves from the " +
                "forest stole his precious blanket, and now he cries day and night.",
        )
        chatNpc(
            sad,
            "He won't settle without it, and nobody here can get a wink of sleep. I'm at my " +
                "wits' end.",
        )
        val help = choice2("Can I help?", true, "I hope you find it.", false)
        if (!help) {
            chatPlayer(neutral, "I hope you find it.")
            chatNpc(sad, "Thank you. I could use all the luck I can get. *yawn*")
            return
        }
        chatPlayer(happy, "Can I help?")
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "Sorry, I can't right now.")
            chatNpc(neutral, "Oh. Well, never mind. If you change your mind, I'll be here.")
            return
        }
        if (monksFriend.stage(player) != 0) return
        quest.setQuestStage(access, Started)
        chatPlayer(happy, "Yes, I'll help you find the blanket.")
        chatNpc(
            happy,
            "Oh, bless you! I think the thieves hide in a secret cave somewhere in the forest, " +
                "west of here.",
        )
        chatNpc(
            quiz,
            "I don't know exactly how to get in, but it must be somewhere near the stone " +
                "circle. Please bring the blanket back to me. Be careful, they're dangerous.",
        )
    }

    private suspend fun Dialogue.duringQuest() {
        when (monksFriend.stage(player)) {
            Started -> blanket()
            BlanketReturned -> askAboutCedric()
            SentForCedric -> waitingForCedric()
            CedricFound -> foundCedric()
            WoodGiven -> celebrate()
            PartyStarted -> party(resumed = true)
            else -> cedricNeedsHelp()
        }
    }

    private suspend fun Dialogue.blanket() {
        chatPlayer(neutral, "Hello Brother Omad.")
        chatNpc(quiz, "Hello again. Did you manage to get the child's blanket back?")
        if (player.inv.count(ChildsBlanket) == 0) {
            chatPlayer(sad, "Not yet, I'm afraid.")
            chatNpc(
                sad,
                "Oh dear. Please hurry. I really, really need some sleep, and so does everyone " +
                    "else in the monastery.",
            )
            return
        }
        chatPlayer(happy, "Yes, I've got it here.")
        if (monksFriend.stage(player) != Started) return
        if (access.invDel(access.inv, ChildsBlanket, 1).failure) return
        quest.setQuestStage(access, BlanketReturned)
        objbox(ChildsBlanket, "You give the child's blanket to Brother Omad.")
        chatNpc(
            happy,
            "Thank you, thank you! That is a weight off my mind. I'm going to put this " +
                "straight into the cot and then collapse into bed. Goodnight!",
        )
    }

    private suspend fun Dialogue.askAboutCedric() {
        chatPlayer(quiz, "How are you feeling now, Brother Omad?")
        chatNpc(
            happy,
            "Much better, thanks to you! The little one is fast asleep at last. Now I can " +
                "finally get on with organising his birthday party.",
        )
        chatNpc(
            neutral,
            "He's one year old tomorrow, you know. We'll need wine for the celebration, and " +
                "Brother Cedric was supposed to fetch it. That was three days ago.",
        )
        val asked = choice2("Who's Brother Cedric?", true, "Enjoy it! See you soon!", false)
        if (!asked) {
            chatPlayer(happy, "Enjoy it! See you soon!")
            chatNpc(happy, "Thank you, my friend. Goodbye for now!")
            return
        }
        chatPlayer(quiz, "Who's Brother Cedric?")
        chatNpc(
            worried,
            "He is a brother from our monastery who went off to fetch the wine for the party. " +
                "I fear he has drunk it all and got lost in the forest somewhere.",
        )
        chatNpc(quiz, "I don't suppose you could look for him and bring him back for me?")
        when (
            choice3("I've no time.", 0, "Where should I look?", 1, "Can I come to the party?", 2)
        ) {
            0 -> {
                chatPlayer(neutral, "I've no time, I'm afraid.")
                chatNpc(sad, "Oh well, goodbye then.")
            }
            1 -> {
                chatPlayer(quiz, "Where should I look?")
                sendForCedric()
                chatNpc(
                    neutral,
                    "Try the forest north of here. I think he went that way. Do bring him back " +
                        "quickly, there's still so much to prepare.",
                )
            }
            else -> {
                chatPlayer(quiz, "Can I come to the party?")
                sendForCedric()
                chatNpc(
                    happy,
                    "Of course you can, once the wine has been found. Do go and look for " +
                        "Brother Cedric, he must be somewhere in the forest.",
                )
            }
        }
    }

    private fun Dialogue.sendForCedric() {
        if (monksFriend.stage(player) == BlanketReturned) {
            quest.setQuestStage(access, SentForCedric)
        }
    }

    private suspend fun Dialogue.waitingForCedric() {
        chatPlayer(quiz, "Have you seen Brother Cedric yet?")
        chatNpc(
            sad,
            "No, he hasn't come back. Please keep looking for him, he must be somewhere in " +
                "the forest.",
        )
    }

    private suspend fun Dialogue.foundCedric() {
        chatPlayer(neutral, "I've found Brother Cedric, but he's very drunk.")
        chatNpc(
            worried,
            "Oh dear. Please do what you can to sober him up and bring him back to me.",
        )
    }

    private suspend fun Dialogue.cedricNeedsHelp() {
        chatPlayer(neutral, "Brother Cedric is having trouble with his cart.")
        chatNpc(
            quiz,
            "Is he? Then do offer him a hand, there's a good soul. We can't have a party " +
                "without wine.",
        )
    }

    private suspend fun Dialogue.celebrate() {
        chatPlayer(happy, "Brother Cedric is on his way. He's just mending his cart.")
        chatNpc(
            happy,
            "Marvellous! Thank you so much, you've saved the party and my sanity. Please, take " +
                "these runes as a token of my gratitude.",
        )
        if (monksFriend.stage(player) != WoodGiven) return
        access.invAddOrDrop(objRepo, LawRune, LawRuneReward)
        quest.setQuestStage(access, PartyStarted)
        objbox(LawRune, "Brother Omad gives you $LawRuneReward law runes.")
        chatNpc(happy, "Now then, let's get this party started!")
        party(resumed = false)
    }

    private suspend fun Dialogue.party(resumed: Boolean) {
        if (resumed) {
            chatNpc(happy, "Ah, there you are! The party's about to start. Come along!")
        }
        access.anim("seq.emote_dance")
        chatNpc(laugh, "Party!")
        monk(laugh, "Woop!")
        chatPlayer(laugh, "Yeah!")
        chatNpc(laugh, "Let's boogie!")
        monk(laugh, "Oh baby!")
        chatPlayer(laugh, "GO!")
        chatNpc(laugh, "Get down!")
        monk(laugh, "Feel the rhythm!")
        chatPlayer(laugh, "Dance!")
        chatNpc(laugh, "Oh my!")
        monk(laugh, "Watch me go!")
        chatPlayer(laugh, "You go!")
        if (monksFriend.stage(player) != PartyStarted) return
        monksFriend.startParty(player)
        quest.completeQuest(access)
    }

    private suspend fun Dialogue.monk(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Monk", Monk, mesanim, text)

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Dum dee do la la! *hiccup* That was some party!")
    }
}
