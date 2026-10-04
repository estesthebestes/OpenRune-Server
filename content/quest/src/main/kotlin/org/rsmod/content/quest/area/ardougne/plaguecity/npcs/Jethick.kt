package org.rsmod.content.quest.area.ardougne.plaguecity.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.BOOK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.PICTURE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_BOOK_RETURNED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GOT_BOOK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_REMOVED
import org.rsmod.content.quest.area.ardougne.plaguecity.metJethick
import org.rsmod.content.quest.area.ardougne.plaguecity.pictureAsked
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Jethick, the old family friend in the West Ardougne town square. He points the player at the
 * Rehnisons once he has seen a picture of Elena, and lends them the book he borrowed so they have
 * a reason to knock.
 */
class Jethick @Inject constructor(private val plagueCity: PlagueCityQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(JETHICK) { startDialogue(it.npc) { jethick() } }
    }

    private suspend fun Dialogue.jethick() {
        val stage = plagueCity.stage(player)
        when {
            plagueCity.quest.isQuestCompleted(player) ->
                chatNpc(
                    neutral,
                    "I'm surprised you're still here. Not many like to stick around this place " +
                        "if they have the choice.",
                )
            stage < STAGE_GRILL_REMOVED ->
                chatNpc(
                    neutral,
                    "Hello, I don't recognise you. We don't get many newcomers around here.",
                )
            stage == STAGE_GRILL_REMOVED && !player.inv.contains(PICTURE) -> noPicture()
            stage == STAGE_GRILL_REMOVED -> showPicture()
            stage == STAGE_GOT_BOOK && !player.inv.contains(BOOK) -> lostBook()
            stage in STAGE_GOT_BOOK until STAGE_BOOK_RETURNED -> {
                chatNpc(neutral, "Hello. We don't get many newcomers around here.")
                chatPlayer(neutral, "I'm looking for a woman from East Ardougne called Elena.")
                pointToRehnisons()
            }
            else -> chatNpc(neutral, "Hello. We don't get many newcomers around here.")
        }
    }

    private suspend fun Dialogue.noPicture() {
        if (player.metJethick) {
            chatNpc(neutral, "Hello. We don't get many newcomers around here.")
            chatPlayer(neutral, "I'm looking for a woman from East Ardougne called Elena.")
            chatNpc(
                quiz,
                "Although the name is familiar, I'll need to know more than that, or see a " +
                    "picture?",
            )
            return
        }
        player.metJethick = true
        chatNpc(
            neutral,
            "Hello, I don't recognise you. We don't get many newcomers around here.",
        )
        chatPlayer(quiz, "How come?")
        chatNpc(
            neutral,
            "The plague of course. Not many people want to come to a place like this and the " +
                "few that do normally get stopped by the mourners.",
        )
        chatNpc(
            sad,
            "All you'll find here now are the dead and the dying. Even our own king has " +
                "abandoned us.",
        )
        chatPlayer(quiz, "Your king?")
        chatNpc(
            neutral,
            "Yes, King Tyras of West Ardougne. He's the brother of King Lathas, the ruler of " +
                "East Ardougne.",
        )
        chatPlayer(quiz, "So where is the king?")
        chatNpc(
            neutral,
            "Well he's always been a bit of an explorer. He's led multiple expeditions into " +
                "the uncharted lands to the west.",
        )
        chatNpc(
            neutral,
            "The plague first started when he came back from one of these expeditions. More " +
                "than a few suspect that some of his men caught it out there and brought it " +
                "back with them.",
        )
        chatNpc(
            angry,
            "The king didn't care though. He just left on another expedition to the west. He " +
                "hasn't been seen since. He left the city warder Bravek in charge but he's no " +
                "better.",
        )
        chatNpc(
            quiz,
            "Anyway, you clearly didn't come here to talk about kings. So tell me, what " +
                "brings you to West Ardougne?",
        )
        chatPlayer(neutral, "I'm looking for a woman from East Ardougne called Elena.")
        chatNpc(
            neutral,
            "East Ardougnian women are easier to find in East Ardougne. Not many would come " +
                "to West Ardougne to find one. Although the name is familiar, what does she " +
                "look like?",
        )
        chatPlayer(confused, "Um... brown hair... in her twenties...")
        chatNpc(
            quiz,
            "Hmmm, that doesn't narrow it down a huge amount... I'll need to know more than " +
                "that, or see a picture?",
        )
        player.pictureAsked = true
    }

    private suspend fun Dialogue.showPicture() {
        if (player.metJethick) {
            chatNpc(neutral, "Hello. We don't get many newcomers around here.")
        } else {
            player.metJethick = true
            chatNpc(
                neutral,
                "Hello, I don't recognise you. We don't get many newcomers around here.",
            )
        }
        chatPlayer(neutral, "I'm looking for a woman from East Ardougne called Elena.")
        chatNpc(
            neutral,
            "East Ardougnian women are easier to find in East Ardougne. Not many would come " +
                "to West Ardougne to find one. Although the name is familiar, what does she " +
                "look like?",
        )
        objbox(PICTURE, "You show Jethick the picture.")
        chatNpc(
            happy,
            "Ah yes. She came over here to help the plague victims. I think she is staying " +
                "over with the Rehnison family.",
        )
        chatNpc(
            neutral,
            "They live in the small timbered building at the far north side of town. I've " +
                "not seen her around here in a while, mind.",
        )
        plagueCity.advanceTo(access, STAGE_GOT_BOOK)
        offerBook()
    }

    private suspend fun Dialogue.lostBook() {
        chatNpc(neutral, "Hello. We don't get many newcomers around here.")
        chatPlayer(neutral, "I'm looking for a woman from East Ardougne called Elena.")
        pointToRehnisons()
        offerBook()
    }

    private suspend fun Dialogue.pointToRehnisons() {
        chatNpc(
            neutral,
            "Ah yes. She came over here to help the plague victims. I think she is staying " +
                "over with the Rehnison family.",
        )
        chatNpc(
            neutral,
            "They live in the small timbered building at the far north side of town. I've " +
                "not seen her around here in a while, mind.",
        )
    }

    private suspend fun Dialogue.offerBook() {
        chatNpc(
            quiz,
            "I don't suppose you could run me a little errand while you're over there? I " +
                "borrowed this book from them, can you return it?",
        )
        val accepted =
            choice2(
                "Yes, I'll return it for you.",
                true,
                "No, I don't have time for that.",
                false,
            )
        if (!accepted) {
            chatPlayer(neutral, "No, I don't have time for that.")
            return
        }
        chatPlayer(happy, "Yes, I'll return it for you.")
        if (player.inv.isFull()) {
            objbox(BOOK, "Jethick shows you the book, but you don't have room to take it.")
            return
        }
        access.invAdd(player.inv, BOOK)
        objbox(BOOK, "Jethick gives you a book.")
    }

    companion object {
        const val JETHICK = "npc.jethick"
    }
}
