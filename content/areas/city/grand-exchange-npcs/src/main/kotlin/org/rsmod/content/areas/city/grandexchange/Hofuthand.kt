package org.rsmod.content.areas.city.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class Hofuthand
@Inject
constructor(private val window: PriceListWindow, private val random: GameRandom) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(NPC) { startDialogue(it.npc) { talk() } }
        onOpNpc3(NPC) { window.open(this, PriceGuide.WeaponsAndArmour) }
        ambientChatter(NPC, random, CHATTER)
    }

    private suspend fun Dialogue.talk() {
        chatPlayer(happy, "Hello!")
        chatNpc(
            quiz,
            "What? Oh, hello. I was deep in thought. Did you want me to show you the prices of " +
                "weapons and armour?",
        )
        when (
            choice3(
                "You seem a bit flustered.",
                1,
                "Yes, show me the prices of weapons and armour.",
                2,
                "I'll leave you alone.",
                3,
            )
        ) {
            1 -> flustered()
            2 -> showPrices()
            3 -> leave()
        }
    }

    private suspend fun Dialogue.flustered() {
        chatPlayer(quiz, "You seem a bit flustered.")
        chatNpc(
            neutral,
            "Sorry, I'm just deep in thought. I'm waiting for many deals to complete today.",
        )
        chatPlayer(quiz, "What sort of things are you selling?")
        chatNpc(
            neutral,
            "Good old weapons and armour! My people - dwarves, you understand - are hoping I can " +
                "trade with humans.",
        )
        chatPlayer(happy, "It looks like you've come to the right place for that.")
        chatNpc(happy, "I have indeed, my friend. Now, can I help you?")
        when (
            choice2(
                "Yes, show me the prices of weapons and armour.",
                1,
                "I'll leave you alone.",
                2,
            )
        ) {
            1 -> showPrices()
            2 -> leave()
        }
    }

    private suspend fun Dialogue.showPrices() {
        chatPlayer(neutral, "Yes, show me the prices of weapons and armour.")
        window.open(access, PriceGuide.WeaponsAndArmour)
    }

    private suspend fun Dialogue.leave() {
        chatPlayer(neutral, "I'll leave you alone.")
        chatNpc(neutral, "Thank you, I have much on my mind.")
    }

    private companion object {
        const val NPC = "npc.ge_expert_combat"
        val CHATTER =
            listOf(
                "Wow, that's cheap.",
                "Oh. That didn't sell so well.",
                "Hmmm. If I spend twenty thousand on that, then...",
                "Jackpot! I'm in the money now!",
                "Hahaha! Trading the likes of which I have never seen.",
            )
    }
}
