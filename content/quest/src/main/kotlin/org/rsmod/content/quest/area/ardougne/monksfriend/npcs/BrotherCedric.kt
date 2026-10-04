package org.rsmod.content.quest.area.ardougne.monksfriend.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.CartAccepted
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.CedricFound
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Cedric
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.JugOfWater
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Logs
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Plank
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.SentForCedric
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.WaterGiven
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.WoodGiven
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BrotherCedric @Inject constructor(private val monksFriend: MonksFriendQuest) :
    PluginScript() {

    private val quest
        get() = monksFriend.quest

    override fun ScriptContext.startup() {
        onOpNpc1(Cedric) { startDialogue(it.npc) { cedricDialogue() } }
        onOpNpcU(Cedric) {
            val item = it.objType.internalName
            val stage = monksFriend.stage(player)
            val accepted =
                (item == JugOfWater && stage == CedricFound) ||
                    ((item == Logs || item == Plank) && stage == CartAccepted)
            if (accepted) {
                startDialogue(it.npc) { cedricDialogue() }
            } else {
                mes("Nothing interesting happens.")
            }
        }
    }

    private suspend fun Dialogue.cedricDialogue() {
        when (quest.questState(player)) {
            QuestProgressState.FINISHED -> afterQuest()
            else ->
                when (monksFriend.stage(player)) {
                    SentForCedric -> firstMeeting()
                    CedricFound -> wantsWater()
                    WaterGiven -> offerCart()
                    CartAccepted -> wantsWood()
                    in WoodGiven..Int.MAX_VALUE -> wood()
                    else -> drunkChat()
                }
        }
    }

    private suspend fun Dialogue.drunkChat() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(drunk, "Honey, money, woman and wine!")
        chatPlayer(confused, "Are you all right, Brother Cedric?")
        chatNpc(drunk, "Yeah, I'm *hic* fine! Never been... *hic*... better, my friend!")
        chatPlayer(neutral, "Well, take care of yourself.")
        chatNpc(drunk, "La la la... *hic* ...la la la.")
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(drunk, "Honey, money, woman and wine!")
        chatPlayer(confused, "Brother Cedric? Are you all right?")
        chatNpc(
            drunk,
            "*hic* Ohhh... no, not really. I'm very, very drunk... and I've got a terrible " +
                "head.",
        )
        chatNpc(
            drunk,
            "If I could just have a jug of water, I might sober up. Then I'd happily help you " +
                "carry the wine back to the monastery.",
        )
        if (monksFriend.stage(player) == SentForCedric) {
            quest.setQuestStage(access, CedricFound)
        }
    }

    private suspend fun Dialogue.wantsWater() {
        chatPlayer(quiz, "How are you feeling, Brother Cedric?")
        chatNpc(drunk, "Ohhh, my head... I could really do with that jug of water.")
        if (player.inv.count(JugOfWater) == 0) {
            chatPlayer(neutral, "I'll see if I can find some.")
            return
        }
        chatPlayer(happy, "I've got some water here for you.")
        if (monksFriend.stage(player) != CedricFound) return
        if (access.invDel(access.inv, JugOfWater, 1).failure) return
        quest.setQuestStage(access, WaterGiven)
        objbox(JugOfWater, "You give Brother Cedric a jug of water.")
        chatNpc(
            happy,
            "Ahhh, that's much better! Thank you. *hic* Now I can think straight again.",
        )
        offerCart()
    }

    private suspend fun Dialogue.offerCart() {
        chatNpc(
            worried,
            "The trouble is, my cart has broken and I can't carry the wine back without it. " +
                "Could you help me fix it?",
        )
        val help =
            choice2("I've helped enough monks today.", false, "Yes, I'd be happy to.", true)
        if (!help) {
            chatPlayer(neutral, "I've helped enough monks today, I think.")
            chatNpc(drunk, "Oh. Well, in that case, I might just have a bit more wine...")
            return
        }
        chatPlayer(happy, "Yes, I'd be happy to.")
        chatNpc(
            happy,
            "Thank you! All I need is some plain logs or a basic plank to mend the cart. " +
                "Please bring me some.",
        )
        if (monksFriend.stage(player) == WaterGiven) {
            quest.setQuestStage(access, CartAccepted)
        }
    }

    private suspend fun Dialogue.wantsWood() {
        chatNpc(quiz, "Did you manage to find some wood for the cart?")
        val item =
            when {
                player.inv.count(Logs) > 0 -> Logs
                player.inv.count(Plank) > 0 -> Plank
                else -> null
            }
        if (item == null) {
            chatPlayer(sad, "Not yet, I'm afraid.")
            return
        }
        chatPlayer(happy, "Yes, I've got some right here.")
        if (monksFriend.stage(player) != CartAccepted) return
        if (access.invDel(access.inv, item, 1).failure) return
        quest.setQuestStage(access, WoodGiven)
        val name = if (item == Logs) "some logs" else "a plank"
        objbox(item, "You give Brother Cedric $name.")
        chatNpc(
            happy,
            "That will do nicely. I'll have the cart mended in no time and then follow you " +
                "back to the monastery.",
        )
        chatNpc(happy, "Tell Brother Omad I'll be along shortly, won't you?")
    }

    private suspend fun Dialogue.wood() {
        chatPlayer(neutral, "Hello again, Brother Cedric.")
        chatNpc(
            happy,
            "I'm nearly finished with the cart. Please go and tell Brother Omad that I'll " +
                "be there soon.",
        )
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(
            happy,
            "Thank you for all your help. Brother Omad is in no state to thank you himself, so " +
                "I'll do it on his behalf.",
        )
    }
}
