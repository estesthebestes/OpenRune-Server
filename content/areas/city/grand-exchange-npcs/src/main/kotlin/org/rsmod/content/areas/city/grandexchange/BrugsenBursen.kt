package org.rsmod.content.areas.city.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.interfaces.equipment.prices.GuidePriceScript
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal fun interface PriceChecker {
    fun open(access: ProtectedAccess)
}

internal class BrugsenBursen(private val priceChecker: PriceChecker) : PluginScript() {
    @Inject constructor(guide: GuidePriceScript) : this(PriceChecker { guide.open(it) })

    override fun ScriptContext.startup() {
        onOpNpc1(NPC) { startDialogue(it.npc) { talk() } }
        onOpNpc3(NPC) { priceChecker.open(this) }
    }

    private suspend fun Dialogue.talk() {
        chatNpc(happy, "Hello, and welcome to the Grand Exchange! How can I help you?")
        menu()
    }

    private suspend fun Dialogue.menu() {
        when (
            choice3(
                "What is this place?",
                Topic.Place,
                "Can you tell me the price of an item?",
                Topic.Price,
                "I'm fine, thanks.",
                Topic.Leave,
            )
        ) {
            Topic.Place -> place()
            Topic.Price -> price()
            Topic.Leave -> chatPlayer(neutral, "I'm fine, thanks.")
        }
    }

    private suspend fun Dialogue.place() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            happy,
            "This is the Grand Exchange, the best trading spot in the land! People come from all " +
                "across the country to buy and sell every item under the sun.",
        )
        chatNpc(
            neutral,
            "When you want to buy or sell an item, talk to the clerks in the middle. They'll " +
                "tell you how to set up a trade offer.",
        )
        chatNpc(quiz, "Now, is there something I can do for you?")
        menu()
    }

    private suspend fun Dialogue.price() {
        chatPlayer(quiz, "Can you tell me the price of an item?")
        chatNpc(quiz, "What had you in mind?")
        priceChecker.open(access)
    }

    private enum class Topic {
        Place,
        Price,
        Leave,
    }

    private companion object {
        const val NPC = "npc.ge_boss"
    }
}
