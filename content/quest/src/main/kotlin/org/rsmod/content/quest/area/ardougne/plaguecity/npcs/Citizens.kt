package org.rsmod.content.quest.area.ardougne.plaguecity.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The men and women of West Ardougne. They have nothing to do with the quest's progress, but
 * they are who the player asks about Elena while searching the city.
 */
class Citizens @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        for (npc in MEN) {
            onOpNpc1(npc) { startDialogue(it.npc) { curseKingTyras("Man") } }
        }
        for (npc in WOMEN_MOURNERS_COMPLAINT) {
            onOpNpc1(npc) { startDialogue(it.npc) { mournersComplaint() } }
        }
        for (npc in WOMEN_LIFE_IS_TOUGH) {
            onOpNpc1(npc) { startDialogue(it.npc) { lifeIsTough() } }
        }
        for (npc in WOMEN_CURSE_TYRAS) {
            onOpNpc1(npc) { startDialogue(it.npc) { curseKingTyras("Woman") } }
        }
        for (npc in OUTSIDERS) {
            onOpNpc1(npc) {
                startDialogue(it.npc) {
                    chatPlayer(happy, "Good day.")
                    chatNpc(worried, "An outsider! Can you get me out of this hell hole?")
                    chatPlayer(sad, "Sorry, that's not what I'm here to do.")
                }
            }
        }
    }

    private suspend fun Dialogue.curseKingTyras(who: String) {
        if (who == "Woman") {
            chatPlayer(happy, "Good day.")
        }
        chatNpc(angry, "We don't have good days here anymore. Curse King Tyras.")
        when (
            choice3(
                "Oh okay, bad day then.", 1,
                "Why, what has he done?", 2,
                "I'm looking for a woman called Elena.", 3,
            )
        ) {
            1 -> chatPlayer(neutral, "Oh okay, bad day then.")
            2 -> {
                chatPlayer(quiz, "Why, what has he done?")
                chatNpc(
                    angry,
                    "His army curses our city with this plague then wanders off again, " +
                        "leaving us to clear up the pieces.",
                )
            }
            3 -> {
                chatPlayer(neutral, "I'm looking for a woman called Elena.")
                chatNpc(neutral, "Not heard of her.")
            }
        }
    }

    private suspend fun Dialogue.mournersComplaint() {
        chatPlayer(happy, "Hello, how's it going?")
        chatNpc(
            angry,
            "Bah, those mourners... they're meant to be helping us, but I think they're doing " +
                "more harm here than good. They won't even let me send a letter out to my " +
                "family.",
        )
        when (
            choice2(
                "Have you seen a lady called Elena around here?", 1,
                "You should stand up to them more.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Have you seen a lady called Elena around here?")
                chatNpc(neutral, "Yes, I've seen her. Very helpful person.")
                chatNpc(
                    neutral,
                    "Not for the last few days though... I thought maybe she'd gone home.",
                )
            }
            2 -> {
                chatPlayer(neutral, "You should stand up to them more.")
                chatNpc(worried, "Oh I'm not one to cause a fuss.")
            }
        }
    }

    private suspend fun Dialogue.lifeIsTough() {
        chatPlayer(happy, "Hello, how's it going?")
        chatNpc(sad, "Life is tough.")
        when (
            choice3(
                "Yes, living in a plague city must be hard.", 1,
                "I'm sorry to hear that.", 2,
                "I'm looking for a lady called Elena.", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Yes, living in a plague city must be hard.")
                chatNpc(
                    angry,
                    "Plague? Pah, that's no excuse for the treatment we've received. It's " +
                        "obvious pretty quickly if someone has the plague.",
                )
                chatNpc(
                    neutral,
                    "I'm thinking about making a break for it. I'm perfectly healthy, not " +
                        "gonna infect anyone.",
                )
            }
            2 -> {
                chatPlayer(sad, "I'm sorry to hear that.")
                chatNpc(sad, "Well, ain't much either you or me can do about it.")
            }
            3 -> {
                chatPlayer(neutral, "I'm looking for a lady called Elena.")
                chatNpc(
                    neutral,
                    "I've not heard of her. Old Jethick knows a lot of people, maybe he'll know " +
                        "where you can find her.",
                )
            }
        }
    }

    private companion object {
        val MEN =
            listOf(
                "npc.dskin_w_ardoungecitizen1",
                "npc.w_ardougnecitizen1",
                "npc.w_ardougnecitizen2",
                "npc.w_ardougnecitizen3",
                "npc.w_ardougnecitizen4",
                "npc.w_ardougnecitizen5",
            )
        val WOMEN_MOURNERS_COMPLAINT =
            listOf("npc.w_ardoungecitizenlady1", "npc.w_ardoungecitizenlady4")
        val WOMEN_LIFE_IS_TOUGH = listOf("npc.dskin_w_ardoungecitizenlady3")
        val WOMEN_CURSE_TYRAS = listOf("npc.dskin_w_ardoungecitizenlady4")
        val OUTSIDERS = listOf("npc.dskin_w_ardoungecitizen2", "npc.w_ardoungecitizenlady2")
    }
}
