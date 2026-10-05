package org.rsmod.content.quest.area.yanille

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

/** Wizard Distentor, head of the Wizards' Guild and a Rune Essence Mine teleporter. */
class WizardDistentor @Inject constructor(private val runeMysteries: RuneMysteriesQuest) :
    PluginScript() {

    private val quest
        get() = runeMysteries.quest

    override fun ScriptContext.startup() {
        onOpNpc1(DISTENTOR) { startDialogue(it.npc) { distentorDialogue() } }
        onOpNpc3(DISTENTOR) { teleport(it.npc) }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!quest.isQuestCompleted(player)) {
            return
        }
        teleportToRuneEssenceMine(npc, EssenceMineTeleporter.Distentor)
    }

    private suspend fun Dialogue.distentorDialogue() {
        chatNpc(happy, "Welcome to the Magicians' Guild!")
        chatPlayer(happy, "Hello there.")
        chatNpc(quiz, "What can I do for you?")
        if (!quest.isQuestCompleted(player)) {
            justLooking()
            return
        }
        val teleport =
            choice2(
                "Nothing thanks, I'm just looking around.",
                false,
                "Can you teleport me to the Rune Essence?",
                true,
            )
        if (!teleport) {
            justLooking()
            return
        }
        chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
        teleportToRuneEssenceMine(EssenceMineTeleporter.Distentor)
    }

    private suspend fun Dialogue.justLooking() {
        chatPlayer(neutral, "Nothing thanks, I'm just looking around.")
        chatNpc(neutral, "That's fine with me.")
    }

    private companion object {
        const val DISTENTOR = "npc.guild_wizard"
    }
}
