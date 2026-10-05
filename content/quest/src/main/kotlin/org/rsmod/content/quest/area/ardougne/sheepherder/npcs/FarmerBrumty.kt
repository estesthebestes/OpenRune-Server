package org.rsmod.content.quest.area.ardougne.sheepherder.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepColour
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.BRUMTY
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepState
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Farmer Brumty, beside his enclosure. Warns about the infection and points to the cattleprod, and
 * hands back the remains of any sheep that has been fed but whose bones have since gone missing.
 */
class FarmerBrumty @Inject constructor(private val sheep: SheepHerderQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BRUMTY) { startDialogue(it.npc) { brumty() } }
    }

    private suspend fun Dialogue.brumty() {
        if (sheep.stage(player) != STAGE_STARTED) {
            chatPlayer(happy, "Hello there.")
            chatNpc(
                sad,
                "Hello. I'm afraid I'm not much company at the moment, what with my sheep " +
                    "wandering off and turning strange colours.",
            )
            return
        }
        chatPlayer(
            neutral,
            "Councillor Halgrive has asked me to round up those diseased sheep and kill them.",
        )
        chatNpc(
            sad,
            "I thought it might come to that. Be careful with them: if you touch them you " +
                "risk getting infected.",
        )
        chatNpc(
            neutral,
            "There should be a cattleprod in the barn by the incinerator. Use that to herd " +
                "them in, and you'll not have to lay a hand on them.",
        )
        if (lostBones().isEmpty()) {
            return
        }
        val lost = choice2("I've lost some sheep bones.", true, "I'll be careful.", false)
        if (!lost) {
            chatPlayer(neutral, "I'll be careful.")
            return
        }
        returnBones()
    }

    private suspend fun Dialogue.returnBones() {
        val bones = lostBones()
        chatPlayer(sad, "I've lost some sheep bones.")
        if (access.inv.freeSpace() < bones.size) {
            chatNpc(
                neutral,
                "I picked up what you left lying about, but you've not got room for " +
                    "${if (bones.size == 1) "it" else "them all"}.",
            )
            return
        }
        chatNpc(
            neutral,
            "I gathered up what you left lying around. I wasn't going to touch them without " +
                "gloves, mind.",
        )
        for (colour in bones) {
            access.invAdd(access.inv, colour.bones, 1)
        }
        access.mes("Farmer Brumty gives you the ${bones.joinToString { it.label }} sheep's bones.")
    }

    /** Colours fed but not yet burned whose bones are nowhere on the player or in the bank. */
    private fun Dialogue.lostBones(): List<SheepColour> =
        SheepColour.entries.filter {
            sheep.state(player, it) == SheepState.BONES &&
                it.bones !in access.inv &&
                it.bones !in access.bank
        }
}
