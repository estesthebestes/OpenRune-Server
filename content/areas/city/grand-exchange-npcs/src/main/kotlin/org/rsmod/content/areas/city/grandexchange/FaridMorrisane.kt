package org.rsmod.content.areas.city.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class FaridMorrisane
@Inject
constructor(private val window: PriceListWindow, private val random: GameRandom) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(NPC) { startDialogue(it.npc) { talk() } }
        onOpNpc3(NPC) { window.open(this, PriceGuide.Ores) }
        ambientChatter(NPC, random, CHATTER)
    }

    private suspend fun Dialogue.talk() {
        chatPlayer(neutral, "Hello, little boy.")
        chatNpc(
            angry,
            "I'd rather you didn't talk to me like that. I'll have you know I'm an accomplished " +
                "merchant.",
        )
        when (
            choice3(
                "Calm down, junior.",
                1,
                "Can you show me the prices of ores and bars?",
                2,
                "I best go and speak with someone more my height.",
                3,
            )
        ) {
            1 -> calmDown()
            2 -> showPrices()
            3 -> leave()
        }
    }

    private suspend fun Dialogue.calmDown() {
        val pet = if (player.appearance.bodyType == constants.bodytype_b) "darling" else "mate"
        chatPlayer(neutral, "Calm down, junior.")
        chatNpc(angry, "Don't tell me to calm down! And don't call me 'junior'.")
        chatNpc(
            angry,
            "I am Farid Morrisane, son of Ali Morrisane, the greatest merchant in the world!",
        )
        chatPlayer(quiz, "Then why are you here and not him?")
        chatNpc(neutral, "My father has given me the job of growing our business here.")
        chatPlayer(
            laugh,
            "And you're up to the task? What a big boy you are! Mummy and daddy must be so proud!",
        )
        chatNpc(
            angry,
            "Look, $pet - I may be young and I may be short, but I'm a respected merchant around " +
                "here and I've no time for simpletons like you.",
        )
    }

    private suspend fun Dialogue.showPrices() {
        chatPlayer(quiz, "Can you show me the prices of ores and bars?")
        window.open(access, PriceGuide.Ores)
    }

    private suspend fun Dialogue.leave() {
        val title = if (player.appearance.bodyType == constants.bodytype_b) "miss" else "mister"
        chatPlayer(neutral, "I best go and speak with someone more my height.")
        chatNpc(angry, "Then I won't stop you, $title. I have far too much work to do.")
    }

    private companion object {
        const val NPC = "npc.ge_expert_ores"
        val CHATTER =
            listOf(
                "My father shall be so pleased.",
                "Hmm. If divide by 20 and take off 50%...",
                "Woo hoo! What a sale!",
                "What shall I trade next...",
                "I can make so much money here!",
            )
    }
}
