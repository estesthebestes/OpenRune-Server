package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_TOLD_ELENA
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * King Lathas in the throne room of East Ardougne castle, who admits the plague is a hoax and
 * explains the wall is there to keep his corrupted brother Tyras out. The king is a multi-npc, so
 * the op sits on the base type.
 */
class KingLathas @Inject constructor(private val biohazard: BiohazardQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KING) { startDialogue(it.npc) { king() } }
    }

    private suspend fun Dialogue.king() {
        when {
            biohazard.quest.isQuestCompleted(player) -> afterQuest()
            biohazard.stage(player) == STAGE_TOLD_ELENA -> confrontation()
            else -> chatNpc(angry, "Leave me citizen. I'm far too busy to talk.")
        }
    }

    private suspend fun Dialogue.confrontation() {
        chatPlayer(neutral, "I assume that you are King Lathas of East Ardougne?")
        chatNpc(angry, "You assume correctly, but where do you get such impertinence.")
        chatPlayer(angry, "I get it from finding out that the plague is a hoax.")
        chatNpc(shocked, "A hoax? I've never heard such a ridiculous thing...")
        chatPlayer(neutral, "I have evidence, from Guidor of Varrock.")
        chatNpc(
            sad,
            "Ah... I see. Well then you are right about the plague. But I did it for the good " +
                "of my people.",
        )
        chatPlayer(angry, "When is it ever good to lie to people like that?")
        chatNpc(
            neutral,
            "When it protects them from a far greater danger, a fear too big to fathom.",
        )
        chatPlayer(confused, "I don't understand...")
        chatNpc(
            neutral,
            "When my father was king, he ruled over a united Ardougne. But on his deathbed, he " +
                "could not decide which of his sons should succeed him, so he had the city " +
                "split into two.",
        )
        chatNpc(
            neutral,
            "I became king of East Ardougne while my brother, Tyras, ruled over West Ardougne. " +
                "My brother was a good man and was great at many things. Being a king was not " +
                "one of them.",
        )
        chatNpc(
            neutral,
            "Rather than looking after his people, he went on numerous expeditions to the " +
                "lands west of here. On one of these expeditions, he was captured by the forces " +
                "of the Dark Lord.",
        )
        chatNpc(
            neutral,
            "The Dark Lord agreed to spare his life, but only on one condition... That he " +
                "would drink from the Chalice of Eternity.",
        )
        chatPlayer(quiz, "So what happened?")
        chatNpc(
            worried,
            "The chalice corrupted him. He joined forces with the Dark Lord, the embodiment of " +
                "pure evil...",
        )
        chatNpc(
            neutral,
            "And so I erected the wall, not just to protect my people, but to protect all the " +
                "people of Gielinor. Tyras is king of West Ardougne, sealing off the city was " +
                "the only way to stop him and the Dark Lord.",
        )
        chatNpc(
            sad,
            "I knew people would never believe the truth, so I had the plague made up as an " +
                "excuse for the cordon. I'm not proud of it, but it was the only way to keep " +
                "people safe.",
        )
        chatPlayer(neutral, "I see. Well at least I know now.")
        chatNpc(
            neutral,
            "While my brother is still at large, I must ask that you keep this secret. We " +
                "can't bring the wall down until we know he is no longer a danger.",
        )
        chatPlayer(quiz, "But how do we stop him?")
        chatNpc(happy, "You're willing to help? Gods be praised!")
        chatNpc(
            neutral,
            "My brother is currently gathering strength in the lands to the west. If we are to " +
                "stop him, we must find a way through the mountains. There is an underground " +
                "pass that should lead through but it is full of danger.",
        )
        chatNpc(
            neutral,
            "Return to me when you are ready to face this peril. Until then, I give you " +
                "permission to use my training area. It's located just to the north west of " +
                "the city.",
        )
        chatNpc(
            neutral,
            "I'll also let the mourners know that you are a friend to Ardougne. You will be " +
                "allowed to pass through the gates to West Ardougne whenever you please. Now, " +
                "you'd better be off. You have much to prepare for.",
        )
        biohazard.advanceTo(access, STAGE_COMPLETE)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Good day King Lathas.")
        chatNpc(neutral, "And to you adventurer. I assume you're here about King Tyras?")
        when (choice2("I am. What's our plan?", 1, "I'm just passing through.", 2)) {
            1 -> {
                chatPlayer(neutral, "I am. What's our plan?")
                chatNpc(
                    neutral,
                    "Well, as we've previously discussed, my brother is currently gathering " +
                        "strength in the lands west of here. The only known way into those " +
                        "lands is through the Underground Pass.",
                )
            }
            2 -> {
                chatPlayer(neutral, "I'm just passing through.")
                chatNpc(
                    neutral,
                    "Fair enough. Return to me when you are ready and we will discuss our next " +
                        "steps.",
                )
            }
        }
    }

    private companion object {
        const val KING = "npc.kinglathas"
    }
}
