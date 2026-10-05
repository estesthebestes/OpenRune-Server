package org.rsmod.content.areas.city.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ReloboBlinyo
@Inject
constructor(private val window: PriceListWindow, private val random: GameRandom) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(NPC) { startDialogue(it.npc) { talk() } }
        onOpNpc3(NPC) { window.open(this, PriceGuide.Logs) }
        ambientChatter(NPC, random, CHATTER)
    }

    private suspend fun Dialogue.talk() {
        chatNpc(happy, "Hello, my friend. Would you like me to show you the prices of logs?")
        when (
            choice3(
                "You look like you've travelled a fair distance.",
                1,
                "Okay, show me the prices of logs.",
                2,
                "Sorry, I need to be making tracks.",
                3,
            )
        ) {
            1 -> travelled()
            2 -> showPrices()
            3 -> leave()
        }
    }

    private suspend fun Dialogue.travelled() {
        chatPlayer(quiz, "You look like you've travelled a fair distance.")
        chatNpc(quiz, "What gave me away?")
        chatPlayer(neutral, "I don't mean to be rude, but the clothing and hair, for starters.")
        chatNpc(
            happy,
            "Ah, yes. I'm from Shilo Village on Karamja. It's a style I've had since I was little.",
        )
        chatPlayer(quiz, "Then tell me, why are you so far from home?")
        chatNpc(
            happy,
            "This Grand Exchange! Isn't it marvellous! I've never seen anything like it.",
        )
        chatNpc(
            neutral,
            "My people were saddened when I chose to leave home and travel here, but I hope to " +
                "make some serious profit, then return to my tribe.",
        )
        chatPlayer(quiz, "So, what are you selling?")
        chatNpc(
            happy,
            "Logs! Of all kinds! That's my plan, at least. Living in the jungle as we do, my " +
                "people understand exotic wood very well indeed.",
        )
        when (
            choice2(
                "Okay, show me the prices of logs.",
                1,
                "Sorry, I need to be making tracks.",
                2,
            )
        ) {
            1 -> showPrices()
            2 -> leave()
        }
    }

    private suspend fun Dialogue.showPrices() {
        chatPlayer(neutral, "Okay, show me the prices of logs.")
        window.open(access, PriceGuide.Logs)
    }

    private suspend fun Dialogue.leave() {
        chatPlayer(neutral, "Sorry, I need to be making tracks.")
        chatNpc(happy, "Okay, nice talking to you!")
    }

    private companion object {
        const val NPC = "npc.ge_expert_logs"
        val CHATTER =
            listOf(
                "Just one more offer! One more!",
                "Such a joy it is to be in this building.",
                "Yes! Yet another great deal.",
                "My people shall be so proud!",
                "Hmmm. These numbers don't quite add up.",
            )
    }
}
