package org.rsmod.content.interfaces.grandexchange

import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player

internal var Player.geCollectReminders by boolVarBit("varbit.ge_collect_reminders")

/** What the clerks say, and the login reminder their dialogue switches on. */
internal class GeClerkDialogue(
    private val windows: GeWindows,
    private val itemSets: GeItemSets,
    private val sessions: GeSessions,
) {
    fun remind(player: Player) {
        if (!player.geCollectReminders) {
            return
        }
        val session = sessions.of(player)
        if ((0 until SlotCodec.SLOTS).any { !session.box(it).isEmpty() }) {
            player.mes("You have items waiting to be collected at the Grand Exchange.")
        }
    }

    suspend fun greet(dialogue: Dialogue) {
        with(dialogue) {
            chatNpc(
                neutral,
                "Welcome to the Grand Exchange. Would you like to trade now, or exchange item " +
                    "sets?",
            )
            menu(this, includeHowTo = true)
        }
    }

    private suspend fun menu(dialogue: Dialogue, includeHowTo: Boolean) {
        with(dialogue) {
            val reminders = player.geCollectReminders
            val reminderOption =
                if (reminders) {
                    "I don't want Exchange collection reminders on login."
                } else {
                    "I'd like Exchange collection reminders on login, please."
                }
            val topic =
                if (includeHowTo) {
                    choice5(
                        "How do I use the Grand Exchange?",
                        Topic.HowTo,
                        "I'd like to set up trade offers please.",
                        Topic.Trade,
                        "Can you help me with item sets?",
                        Topic.Sets,
                        reminderOption,
                        Topic.Reminders,
                        "I'm fine, thanks.",
                        Topic.Leave,
                    )
                } else {
                    choice4(
                        "I'd like to set up trade offers please.",
                        Topic.Trade,
                        "Can you help me with item sets?",
                        Topic.Sets,
                        reminderOption,
                        Topic.Reminders,
                        "I'm fine, thanks.",
                        Topic.Leave,
                    )
                }
            when (topic) {
                Topic.HowTo -> howTo(this)
                Topic.Trade -> {
                    chatPlayer(neutral, "I'd like to set up trade offers please.")
                    windows.openExchange(access)
                }
                Topic.Sets -> {
                    chatPlayer(quiz, "Can you help me with item sets?")
                    itemSets.open(access)
                }
                Topic.Reminders -> reminders(this, enable = !reminders)
                Topic.Leave -> chatPlayer(neutral, "I'm fine, thanks.")
            }
        }
    }

    private suspend fun howTo(dialogue: Dialogue) {
        with(dialogue) {
            chatPlayer(quiz, "How do I use the Grand Exchange?")
            chatNpc(
                neutral,
                "My colleague and I can set up trade offers for you. You can offer to sell items " +
                    "or to buy them.",
            )
            chatNpc(
                neutral,
                "To sell something, you give us the items and tell us how much you want for them.",
            )
            chatNpc(
                neutral,
                "We'll look for someone who wants to buy at your price and carry out the trade. " +
                    "You can then collect the cash here, or at any bank.",
            )
            chatNpc(
                neutral,
                "To buy something, you tell us what you want and hand over the cash you're " +
                    "willing to spend on it.",
            )
            chatNpc(
                neutral,
                "We'll look for someone selling at your price and carry out the trade. You can " +
                    "collect the items here, or at any bank, along with any left-over cash.",
            )
            chatNpc(
                neutral,
                "Sometimes it takes a while to find a matching offer. If you change your mind, " +
                    "we'll cancel the offer and return your unused items and cash.",
            )
            chatNpc(
                neutral,
                "That's everything you need to get started. Would you like to trade now, or " +
                    "exchange item sets?",
            )
            menu(this, includeHowTo = false)
        }
    }

    private suspend fun reminders(dialogue: Dialogue, enable: Boolean) {
        with(dialogue) {
            player.geCollectReminders = enable
            if (enable) {
                chatPlayer(neutral, "I'd like Exchange collection reminders on login, please.")
                chatNpc(
                    neutral,
                    "Okay, when you log in we'll send you a message about any items that are " +
                        "waiting for you to collect.",
                )
                when (
                    choice2(
                        "Thank you.",
                        true,
                        "I don't want Exchange collection reminders on login.",
                        false,
                    )
                ) {
                    true -> chatPlayer(happy, "Thank you.")
                    false -> reminders(this, enable = false)
                }
            } else {
                chatPlayer(neutral, "I don't want Exchange collection reminders on login.")
                chatNpc(
                    neutral,
                    "Okay, when you log in you will no longer be reminded about items waiting to " +
                        "be collected, unless the items only arrived after you logged out.",
                )
                when (
                    choice2(
                        "Thank you.",
                        true,
                        "I'd like Exchange collection reminders on login, please.",
                        false,
                    )
                ) {
                    true -> chatPlayer(happy, "Thank you.")
                    false -> reminders(this, enable = true)
                }
            }
        }
    }

    private enum class Topic {
        HowTo,
        Trade,
        Sets,
        Reminders,
        Leave,
    }
}
