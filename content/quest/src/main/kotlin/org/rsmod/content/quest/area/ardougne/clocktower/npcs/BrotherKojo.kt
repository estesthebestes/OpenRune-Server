package org.rsmod.content.quest.area.ardougne.clocktower.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.KOJO
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.REWARD_COINS
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BrotherKojo
@Inject
constructor(private val clockTower: ClockTowerQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KOJO) { startDialogue(it.npc) { kojoDialogue() } }
    }

    private suspend fun Dialogue.kojoDialogue() {
        when (clockTower.quest.questState(player)) {
            QuestProgressState.NOT_STARTED -> beforeQuest()
            QuestProgressState.IN_PROGRESS -> duringQuest()
            QuestProgressState.FINISHED -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatPlayer(neutral, "Hello there, monk.")
        chatNpc(
            neutral,
            "Greetings, adventurer. I am Brother Kojo. You wouldn't happen to know what time it " +
                "is, would you?",
        )
        chatPlayer(neutral, "No, sorry. I haven't a clue.")
        chatNpc(
            worried,
            "Precisely! The clock tower has broken down, so nobody in town can tell the time. I " +
                "must get it fixed before the townsfolk lose their patience!",
        )
        chatNpc(
            quiz,
            "I don't suppose you could help me with the repairs? I would pay you for your " +
                "trouble.",
        )
        if (!startQuestPrompt(clockTower.quest)) {
            chatPlayer(neutral, "Not right now, old monk.")
            chatNpc(neutral, "Very well. Come back and tell me if you change your mind.")
            return
        }
        clockTower.clearProgress(player)
        clockTower.quest.setQuestStage(access, STAGE_STARTED)
        chatPlayer(neutral, "All right, old monk. What needs doing?")
        chatNpc(
            happy,
            "Bless you, kind soul! Down in the cellar you will find four cogs. They are far too " +
                "heavy for me, but you should manage to carry them one at a time.",
        )
        chatNpc(
            neutral,
            "One belongs on each floor of the tower, but I can't quite remember which goes " +
                "where. I'm sure you'll work it out easily enough.",
        )
        chatPlayer(neutral, "I'll do my best.")
        chatNpc(
            happy,
            "Thank you again! And do be careful, the cellar is full of strange beasts!",
        )
    }

    private suspend fun Dialogue.duringQuest() {
        when (clockTower.placedCount(player)) {
            0 -> {
                chatPlayer(neutral, "Hello again.")
                chatNpc(
                    quiz,
                    "Oh, hello. Are you having trouble? The cogs are in the four rooms beneath " +
                        "us. You need to put one cog on a pole on each of the tower's levels.",
                )
                chatPlayer(neutral, "Right, I'll get on with it.")
            }
            1 -> {
                chatPlayer(happy, "I've fitted a cog!")
                chatNpc(happy, "Excellent! Come and see me once you've done the other three.")
            }
            2 -> {
                chatPlayer(happy, "That's two done!")
                chatNpc(happy, "Two to go, then.")
            }
            3 -> chatNpc(happy, "Just one left.")
            else -> reward()
        }
    }

    private suspend fun Dialogue.reward() {
        chatPlayer(happy, "I've put all of the cogs back!")
        chatNpc(
            happy,
            "Really..? Wait, listen! Marvellous, simply marvellous! You've done it! You really " +
                "are clever!",
        )
        chatNpc(
            happy,
            "Now everyone in town will know the right time again! Thank you so much for all of " +
                "your help, and here is the reward I promised you!",
        )
        if (!clockTower.isActive(player) || !clockTower.allPlaced(player)) {
            return
        }
        access.invAddOrDrop(objRepo, COINS, REWARD_COINS)
        clockTower.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(neutral, "Hello again, Brother Kojo.")
        chatNpc(
            happy,
            "Ah, hello there, traveller. You did a grand job on the clock; it's as good as new.",
        )
    }
}
