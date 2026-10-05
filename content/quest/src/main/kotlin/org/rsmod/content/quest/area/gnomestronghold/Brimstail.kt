package org.rsmod.content.quest.area.gnomestronghold

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.content.skills.runecrafting.essence.teleportToRuneEssenceMine
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Brimstail in his cave under the Tree Gnome Stronghold, a Rune Essence Mine teleporter. */
class Brimstail @Inject constructor(private val runeMysteries: RuneMysteriesQuest) :
    PluginScript() {

    private val quest
        get() = runeMysteries.quest

    override fun ScriptContext.startup() {
        onOpNpc1(BRIMSTAIL) { startDialogue(it.npc) { brimstailDialogue() } }
        onOpNpc3(BRIMSTAIL) { teleport(it.npc) }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!quest.isQuestCompleted(player)) {
            return
        }
        teleportToRuneEssenceMine(npc, EssenceMineTeleporter.Brimstail)
    }

    private suspend fun Dialogue.brimstailDialogue() {
        chatNpc(happy, "Hello adventurer, what can I do for you?")
        if (!quest.isQuestCompleted(player)) {
            chatPlayer(neutral, "Nothing, thanks.")
            return
        }
        val teleport =
            choice2(
                "Can you teleport me to the Rune Essence Mine?",
                true,
                "Nothing, thanks.",
                false,
            )
        if (!teleport) {
            chatPlayer(neutral, "Nothing, thanks.")
            return
        }
        chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
        chatNpc(happy, "Okay. Hold onto your hat!")
        teleportToRuneEssenceMine(EssenceMineTeleporter.Brimstail)
    }

    private companion object {
        const val BRIMSTAIL = "npc.gnome_brimstail"
    }
}
