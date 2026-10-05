package org.rsmod.content.areas.city.grandexchange

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class JamesBonds : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(NPC) { startDialogue(it.npc) { talk() } }
    }

    private suspend fun Dialogue.talk() {
        chatPlayer(quiz, "Hey, who are you?")
        chatNpc(
            happy,
            "I'm James. It's my job to tell you all there is to know about Old School Bonds.",
        )
        menu()
    }

    private suspend fun Dialogue.menu() {
        when (
            choice4(
                "What is an Old School Bond?",
                Topic.What,
                "How do I buy an Old School Bond in game?",
                Topic.Buy,
                "How do I turn an Old School Bond into membership?",
                Topic.Redeem,
                "More options...",
                Topic.More,
            )
        ) {
            Topic.What -> {
                chatPlayer(quiz, "What is an Old School Bond?")
                chatNpc(
                    happy,
                    "An Old School Bond lets you buy membership for your account using in-game " +
                        "gold.",
                )
                chatNpc(
                    happy,
                    "My sources also tell me that, due to popular demand, they can now be " +
                        "redeemed for a free name change! Handy!",
                )
                menu()
            }
            Topic.Buy -> {
                chatPlayer(quiz, "How do I buy an Old School Bond in game?")
                chatNpc(
                    neutral,
                    "You can get an Old School Bond from other players or from the Grand Exchange.",
                )
                menu()
            }
            Topic.Redeem -> {
                chatPlayer(quiz, "How do I turn an Old School Bond into membership?")
                chatNpc(neutral, "Simply click the bond and choose the redeem option.")
                menu()
            }
            Topic.More -> more()
        }
    }

    private suspend fun Dialogue.more() {
        when (
            choice3(
                "How much membership do I get from an Old School bond?",
                MoreTopic.Duration,
                "Can I use an Old School Bond to pay for anything else?",
                MoreTopic.Other,
                "How do I buy an Old School Bond to give or sell to another player?",
                MoreTopic.Gift,
            )
        ) {
            MoreTopic.Duration -> {
                chatPlayer(quiz, "How much membership do I get from an Old School bond?")
                chatNpc(neutral, "An Old School Bond gives you 14 days of membership.")
            }
            MoreTopic.Other -> {
                chatPlayer(quiz, "Can I use an Old School Bond to pay for anything else?")
                chatNpc(
                    neutral,
                    "At the moment Old School Bonds can only be redeemed for membership and for " +
                        "display name changes.",
                )
            }
            MoreTopic.Gift -> {
                chatPlayer(
                    quiz,
                    "How do I buy an Old School Bond to give or sell to another player?",
                )
                chatNpc(
                    neutral,
                    "Open the Bond Pouch from the Controls Settings and follow the instructions " +
                        "to buy a bond. You can then give the bond to another player or sell it " +
                        "on the Grand Exchange.",
                )
            }
        }
        menu()
    }

    private enum class Topic {
        What,
        Buy,
        Redeem,
        More,
    }

    private enum class MoreTopic {
        Duration,
        Other,
        Gift,
    }

    private companion object {
        const val NPC = "npc.bond_james_bond"
    }
}
