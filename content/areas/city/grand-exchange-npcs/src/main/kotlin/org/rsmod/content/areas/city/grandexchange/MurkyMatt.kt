package org.rsmod.content.areas.city.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class MurkyMatt
@Inject
constructor(private val window: PriceListWindow, private val random: GameRandom) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(NPC) { startDialogue(it.npc) { talk() } }
        onOpNpc3(NPC) { window.open(this, PriceGuide.Runes) }
        onOpNpc4(NPC) { startDialogue(it.npc) { combineJewellery() } }
        ambientChatter(NPC, random, CHATTER)
    }

    private suspend fun Dialogue.talk() {
        if (player.members) {
            chatNpc(
                neutral,
                "Arrr, what is it that ye be wantin? I can tell ye all about the prices of " +
                    "runes, or perhaps I could combine the charges on yer teleport jewellery.",
            )
        } else {
            chatNpc(
                neutral,
                "Arrr, what is it that ye be wantin? I can tell ye all about the prices of runes.",
            )
        }
        val topic =
            if (player.members) {
                choice5(
                    "What's a pirate doing here?",
                    Topic.Pirate,
                    "Tell me about the prices of runes.",
                    Topic.Prices,
                    "Combine the charges on my teleport jewellery, please.",
                    Topic.Combine,
                    "I hear you deal in rings of forging?",
                    Topic.Forging,
                    "I got to go, erm, swab some decks! Yarr!",
                    Topic.Swab,
                )
            } else {
                choice4(
                    "What's a pirate doing here?",
                    Topic.Pirate,
                    "Tell me about the prices of runes.",
                    Topic.Prices,
                    "I hear you deal in rings of forging?",
                    Topic.Forging,
                    "I got to go, erm, swab some decks! Yarr!",
                    Topic.Swab,
                )
            }
        when (topic) {
            Topic.Pirate -> pirate()
            Topic.Prices -> prices()
            Topic.Combine -> combine()
            Topic.Forging -> forging()
            Topic.Swab -> swab()
        }
    }

    private suspend fun Dialogue.pirate() {
        chatPlayer(quiz, "What's a pirate doing here?")
        chatNpc(angry, "By my sea-blistered skin, I could ask the same of you!")
        chatPlayer(confused, "But... I'm not a pirate?")
        chatNpc(
            quiz,
            "No? Then what's that smell? The smell o' someone spent too long at sea without a " +
                "bath!",
        )
        chatPlayer(neutral, "I think that's probably you.")
        chatNpc(
            laugh,
            "Har har har! We've got a stern landlubber 'ere! Well, let me tell ye, I'm here for " +
                "the Grand Exchange! Gonna cash in me loot!",
        )
        chatPlayer(
            quiz,
            "Don't you just want to sell it in a shop or trade it to someone specific?",
        )
        chatNpc(
            angry,
            "By my wave-battered bones! Not when I can sell to the whole world from this very " +
                "spot!",
        )
        val topic =
            if (player.members) {
                choice3(
                    "Tell me about the prices of runes.",
                    Topic.Prices,
                    "Combine the charges on my teleport jewellery, please.",
                    Topic.Combine,
                    "I got to go, erm, swab some decks! Yarr!",
                    Topic.Swab,
                )
            } else {
                choice2(
                    "Tell me about the prices of runes.",
                    Topic.Prices,
                    "I got to go, erm, swab some decks! Yarr!",
                    Topic.Swab,
                )
            }
        when (topic) {
            Topic.Prices -> prices()
            Topic.Combine -> combine()
            else -> swab()
        }
    }

    private suspend fun Dialogue.prices() {
        chatPlayer(neutral, "Tell me about the prices of runes.")
        window.open(access, PriceGuide.Runes)
    }

    private suspend fun Dialogue.combine() {
        chatPlayer(neutral, "Combine the charges on my teleport jewellery, please.")
        combineJewellery()
    }

    private suspend fun Dialogue.combineJewellery() {
        if (!player.members) {
            chatNpc(sad, "Arrr, ye'll have to come back when ye be a member o' the crew.")
            return
        }
        when (JewelleryCombiner.combine(player)) {
            CombineOutcome.NothingCarried ->
                chatNpc(neutral, "Arrr, ye've got nothing that I can combine.")
            CombineOutcome.NothingToCombine ->
                chatNpc(
                    neutral,
                    "Arrr, ye've nothing I can combine. Maybe ye needs to take yer stuff up to " +
                        "the Wilderness and charge it on the Fountain of Rune. It be more " +
                        "powerful than me.",
                )
            CombineOutcome.NoSpace ->
                chatNpc(neutral, "Arrr, I think ye be a bit short of inventory space.")
            CombineOutcome.Done -> chatNpc(happy, "Arr, all done.")
        }
    }

    private suspend fun Dialogue.forging() {
        chatPlayer(quiz, "I hear you deal in rings of forging?")
        val rings = player.inv.count(RUBY_RING)
        if (rings == 0) {
            chatNpc(neutral, "Arrrr, bring me yer ruby rings to enchant.")
            return
        }
        if (player.inv.count(COINS) < RING_FEE) {
            chatNpc(neutral, "Arrrr, but they arrre ${RING_FEE}gp per ring.")
            return
        }
        chatNpc(quiz, "Arrr, ${RING_FEE}gp per ring. How many would ye like me to enchant?")
        val requested = access.countDialog("How many rings of forging?")
        if (requested <= 0) {
            chatNpc(neutral, "Arrr, be seeing ya.")
            return
        }
        val count = minOf(requested, rings, player.inv.count(COINS) / RING_FEE)
        val paid = player.invDel(player.inv, COINS, count * RING_FEE)
        if (paid.failure) {
            chatNpc(neutral, "Arrrr, but they arrre ${RING_FEE}gp per ring.")
            return
        }
        player.invDel(player.inv, RUBY_RING, count)
        player.invAdd(player.inv, RING_OF_FORGING, count, strict = false)
        chatNpc(happy, "Arrr, pleasure doin' business with ya.")
    }

    private suspend fun Dialogue.swab() {
        chatPlayer(neutral, "I got to go, erm, swab some decks! Yarr!")
        chatNpc(
            angry,
            "Hold yer tongue right there! I'll not have ye thinkin' all seafarers do is scrub " +
                "decks, mind parrots and drink rum.",
        )
        chatNpc(angry, "There be a lot more to a pirate than meets the eye.")
        chatPlayer(happy, "Aye-aye, captain!")
        chatNpc(neutral, "...")
        chatPlayer(laugh, "Oh, come on! Lighten up!")
    }

    private enum class Topic {
        Pirate,
        Prices,
        Combine,
        Forging,
        Swab,
    }

    private companion object {
        const val NPC = "npc.ge_expert_runes"
        const val COINS = "obj.coins"
        const val RUBY_RING = "obj.ruby_ring"
        const val RING_OF_FORGING = "obj.ring_of_forging"
        const val RING_FEE = 250
        val CHATTER =
            listOf(
                "No! Me prices, they be goin' down!",
                "Arrr! Another good sale!",
                "I'm lovin' this Grand Exchange! Arrr!",
                "Sure be a busy place, today.",
                "Yarrr! I'm gonna be rich, I tell ye!",
            )
    }
}
