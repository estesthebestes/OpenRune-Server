package org.rsmod.content.quest.area.ardougne.sheepherder.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.CLOTHING_PRICE
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.JACKET
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.ORBON
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_DISPOSED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.TROUSERS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Doctor Orbon, inside the East Ardougne church. Sells the plague jacket and trousers for 100
 * coins during the quest, and again, for whatever pieces are missing, if they are lost.
 */
class DoctorOrbon @Inject constructor(private val sheep: SheepHerderQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ORBON) { startDialogue(it.npc) { orbon() } }
    }

    private suspend fun Dialogue.orbon() {
        when (sheep.stage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED -> duringQuest()
            STAGE_DISPOSED -> disposed()
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatPlayer(happy, "Hello doctor.")
        chatNpc(
            neutral,
            "Good day. I'm afraid I'm rather busy with the plague, so I can't spare much time.",
        )
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello doctor.")
        chatNpc(happy, "Hello again. I hear those sheep have been dealt with. Well done.")
    }

    private suspend fun Dialogue.duringQuest() {
        if (!hasAnyClothing()) {
            firstVisit()
            return
        }
        followUp()
    }

    private suspend fun Dialogue.firstVisit() {
        chatPlayer(
            neutral,
            "Councillor Halgrive sent me. I need some protective clothing to dispose of some " +
                "diseased sheep.",
        )
        chatNpc(
            worried,
            "Ah yes. I only have the one suit left, I'm afraid. It would cost me 100 coins to " +
                "replace it, so I cannot possibly sell it for less.",
        )
        offerSuit()
    }

    private suspend fun Dialogue.followUp() {
        chatPlayer(happy, "Hello again.")
        chatNpc(
            quiz,
            "Have you disposed of those sheep yet? The whole town is at risk while they are " +
                "wandering about.",
        )
        chatPlayer(neutral, "Not yet.")
        chatNpc(worried, "Then please be quick about it.")
        if (sheep.ownsClothing(player, access.bank)) {
            return
        }
        chatNpc(
            quiz,
            "You seem to have mislaid some of the protective clothing. I could let you have a " +
                "replacement for 100 coins.",
        )
        offerSuit()
    }

    private suspend fun Dialogue.disposed() {
        chatPlayer(happy, "Hello doctor.")
        chatNpc(quiz, "Have you disposed of those sheep yet?")
        chatPlayer(happy, "Yes, they're all dead and burned.")
        chatNpc(happy, "Thank goodness. Go and tell Councillor Halgrive. He'll want to hear it.")
    }

    private suspend fun Dialogue.offerSuit() {
        val buy = choice2("Okay, I'll take it.", true, "Sorry doc, that's too much.", false)
        if (!buy) {
            chatPlayer(sad, "Sorry doc, that's too much.")
            chatNpc(
                neutral,
                "I'm afraid I can't go any lower. Just don't go near those sheep without " +
                    "protection.",
            )
            return
        }
        chatPlayer(neutral, "Okay, I'll take it.")
        val pieces = listOf(JACKET, TROUSERS).filterNot { sheep.owns(player, access.bank, it) }
        if (access.inv.count(COINS) < CLOTHING_PRICE) {
            chatNpc(
                neutral,
                "That's okay. Come back when you have the 100 coins, and just don't go near " +
                    "those sheep until then.",
            )
            return
        }
        if (!hasRoomFor(pieces.size)) {
            chatNpc(neutral, "You'll need more space in your pack to carry the clothing.")
            return
        }
        if (access.invDel(access.inv, COINS, CLOTHING_PRICE).failure) {
            return
        }
        for (piece in pieces) {
            access.invAdd(access.inv, piece, 1)
        }
        if (pieces.size == 2) {
            doubleobjbox(JACKET, TROUSERS, "Doctor Orbon gives you a plague jacket and trousers.")
        } else {
            objbox(pieces.single(), "Doctor Orbon gives you a replacement.")
        }
        chatNpc(
            neutral,
            "This suit will protect you from the infection, but only while you are wearing " +
                "all of it.",
        )
    }

    private fun Dialogue.hasAnyClothing(): Boolean =
        sheep.owns(player, access.bank, JACKET) || sheep.owns(player, access.bank, TROUSERS)

    /** Paying exactly the coin stack frees its slot, so it counts towards the space needed. */
    private fun Dialogue.hasRoomFor(slots: Int): Boolean {
        val freed = if (access.inv.count(COINS) == CLOTHING_PRICE) 1 else 0
        return access.inv.freeSpace() + freed >= slots
    }
}
