package org.rsmod.content.quest.area.ardougne.monksfriend.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.BlanketReturned
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Monk
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ArdougneMonks @Inject constructor(private val monksFriend: MonksFriendQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(Monk) { startDialogue(it.npc) { monkDialogue() } }
    }

    private suspend fun Dialogue.monkDialogue() {
        val quest = monksFriend.quest
        when {
            quest.isQuestCompleted(player) && monksFriend.partyActive(player) -> afterParty()
            quest.isQuestInProgress(player) && monksFriend.stage(player) >= BlanketReturned ->
                partyPlanned()
            else -> greeting()
        }
    }

    private suspend fun Dialogue.partyPlanned() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(happy, "Can't wait for the party!")
    }

    private suspend fun Dialogue.afterParty() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(laugh, "*hiccup* Wasn't that a fantastic party?")
    }

    private suspend fun Dialogue.greeting() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(neutral, "Peace be with you, traveller.")
    }
}
