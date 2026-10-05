package org.rsmod.content.quest.area.ardougne.sheepherder.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.FEED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.HALGRIVE
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.REWARD_COINS
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_DISPOSED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Councillor Halgrive, in the East Ardougne graveyard: starts Sheep Herder, hands out the poisoned
 * feed (again, if it is lost) and pays the 3,100 coins once all four sheep are burned.
 */
class CouncillorHalgrive @Inject constructor(private val sheep: SheepHerderQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(HALGRIVE) { startDialogue(it.npc) { halgrive() } }
    }

    private suspend fun Dialogue.halgrive() {
        when (sheep.quest.questState(player)) {
            QuestProgressState.NOT_STARTED -> offer()
            QuestProgressState.IN_PROGRESS ->
                if (sheep.stage(player) == STAGE_DISPOSED) payment() else progress()
            QuestProgressState.FINISHED -> afterQuest()
        }
    }

    private suspend fun Dialogue.offer() {
        chatNpc(sad, "Hello there. I've been better, I must admit.")
        val ask =
            choice2("What's wrong?", true, "That's life for you.", false)
        if (!ask) {
            chatPlayer(neutral, "That's life for you.")
            chatNpc(sad, "I suppose it is. Take care of yourself.")
            return
        }
        chatPlayer(quiz, "What's wrong?")
        chatNpc(
            worried,
            "It's this plague in West Ardougne. We've mostly managed to keep it contained " +
                "behind the wall, but now some sheep have escaped from a farm near here.",
        )
        chatNpc(
            worried,
            "They've been found discoloured, and the mourners suspect they're infected with " +
                "the plague.",
        )
        chatPlayer(confused, "Are you sure it's really the plague?")
        chatNpc(
            neutral,
            "The mourners are the experts, and whatever it is, those sheep are clearly diseased.",
        )
        chatNpc(
            neutral,
            "As a health official it's my job to deal with this. I need someone to herd the " +
                "sheep into an enclosure, kill them quickly and cleanly, and then incinerate " +
                "the remains.",
        )
        chatNpc(
            sad,
            "The trouble is I can't find any volunteers. Everyone is far too frightened of " +
                "catching the plague.",
        )
        if (!startQuestPrompt(sheep.quest)) {
            chatPlayer(neutral, "No, I'm sorry. I don't think I can help.")
            chatNpc(
                sad,
                "I understand. If you know of anyone else who would be willing, please send " +
                    "them my way.",
            )
            return
        }
        chatPlayer(happy, "Okay, I'll do it.")
        if (access.inv.isFull()) {
            chatNpc(
                neutral,
                "Splendid. You'll need to carry the poisoned feed, though, and your pack is " +
                    "full. Make some room and come back to me.",
            )
            return
        }
        sheep.resetSheep(player)
        sheep.quest.setQuestStage(access, STAGE_STARTED)
        access.invAdd(access.inv, FEED, 1)
        objbox(FEED, "The councillor gives you some poisoned sheep feed.")
        chatNpc(
            neutral,
            "Thank you. The sheep are being kept near Farmer Brumty's enclosure, to the " +
                "north-west of here. Herd them in there, then feed them this poison.",
        )
        chatNpc(
            worried,
            "Don't go anywhere near them without protective clothing, mind. Doctor Orbon, in " +
                "the church, has some.",
        )
    }

    private suspend fun Dialogue.progress() {
        if (sheep.hasFeed(player, access.bank)) {
            chatNpc(
                worried,
                "Please hurry. The longer those sheep are alive, the more danger the town is " +
                    "in. Remember, Doctor Orbon in the church has protective clothing.",
            )
            return
        }
        chatPlayer(sad, "I've lost the poisoned sheep feed you gave me.")
        if (access.inv.isFull()) {
            chatNpc(neutral, "I've more here, but you've nowhere to put it. Make some space first.")
            return
        }
        access.invAdd(access.inv, FEED, 1)
        objbox(FEED, "The councillor gives you some more poisoned sheep feed.")
        chatNpc(worried, "Please be quick about it, and try not to lose this lot.")
    }

    private suspend fun Dialogue.payment() {
        chatNpc(quiz, "Have you disposed of those sheep yet?")
        chatPlayer(happy, "Yes. All four are dead and their remains burned.")
        if (!hasRoomForCoins()) {
            chatNpc(
                neutral,
                "Excellent! But you've no room for your payment. Make a little space in your " +
                    "pack and come back to me.",
            )
            return
        }
        chatNpc(
            happy,
            "Excellent work! The town owes you a great debt. Here's the 100 coins you paid for " +
                "the protective suit, and 3,000 coins as a reward for your trouble.",
        )
        if (sheep.stage(player) != STAGE_DISPOSED) {
            return
        }
        sheep.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(
            happy,
            "Ah, hello again. Thanks to you those poor sheep are gone and the town is a good " +
                "deal safer.",
        )
    }

    private fun Dialogue.hasRoomForCoins(): Boolean {
        val held = access.inv.count(COINS)
        if (held > 0) {
            return held <= Int.MAX_VALUE - REWARD_COINS
        }
        return !access.inv.isFull()
    }
}
