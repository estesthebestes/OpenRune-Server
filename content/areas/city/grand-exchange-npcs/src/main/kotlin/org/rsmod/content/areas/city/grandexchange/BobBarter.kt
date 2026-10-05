package org.rsmod.content.areas.city.grandexchange

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.chatModalUnclamp by intVarBit("varbit.chatmodal_unclamp")

internal class BobBarter
@Inject
constructor(
    private val window: PriceListWindow,
    private val decanter: Decanter,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(NPC) { startDialogue(it.npc) { talk() } }
        onOpNpc3(NPC) { window.open(this, PriceGuide.HerbsAndPotions) }
        onOpNpc4(NPC) { startDialogue(it.npc) { decant() } }
        ambientChatter(NPC, random, CHATTER)
    }

    private suspend fun Dialogue.talk() {
        chatNpc(
            happy,
            "Hello, chum, fancy buyin' some designer jewellery? They've come all the way from " +
                "Ardougne! Most pukka!",
        )
        chatPlayer(neutral, "Erm, no. I'm all set, thanks.")
        if (player.members) {
            chatNpc(
                happy,
                "Okay, chum, so what can I do for you? I can tell you the very latest herb & " +
                    "potion prices, or perhaps I could help you decant your potions.",
            )
        } else {
            chatNpc(
                happy,
                "Okay, chum, would you like me to show you the very latest potion prices?",
            )
        }
        while (true) {
            val topic =
                if (player.members) {
                    choice4(
                        "Who are you?",
                        Topic.Who,
                        "Can you show me the prices for herbs and potions?",
                        Topic.Prices,
                        "Can you decant things for me?",
                        Topic.Decant,
                        "I'll leave you to it.",
                        Topic.Leave,
                    )
                } else {
                    choice3(
                        "Who are you?",
                        Topic.Who,
                        "Can you show me the prices for potions?",
                        Topic.Prices,
                        "I'll leave you to it.",
                        Topic.Leave,
                    )
                }
            when (topic) {
                Topic.Who -> who()
                Topic.Prices -> {
                    val label = if (player.members) "herbs and potions" else "potions"
                    chatPlayer(quiz, "Can you show me the prices for $label?")
                    window.open(access, PriceGuide.HerbsAndPotions)
                    return
                }
                Topic.Decant -> {
                    chatPlayer(quiz, "Can you decant things for me?")
                    decant()
                    return
                }
                Topic.Leave -> {
                    chatPlayer(neutral, "I'll leave you to it.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.who() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(happy, "Why, I'm Bob! Your friendly seller of smashin' goods!")
        chatPlayer(quiz, "So what do you have to sell?")
        chatNpc(
            neutral,
            "Oh, not much at the moment. Cuz, ya know, business being so well and cushie.",
        )
        chatPlayer(quiz, "You don't really look like you're being so successful.")
        chatNpc(
            laugh,
            "You plonka! It's all a show, innit! If I let people know I'm doing good business " +
                "they'll want a share of the moolah!",
        )
        chatPlayer(neutral, "You conveniently have a response for everything.")
        chatNpc(happy, "That's the Ardougne way, my friend.")
    }

    private suspend fun Dialogue.decant() {
        if (!player.members) {
            chatNpc(sad, "Sorry, mate, you'll have to come back later for that.")
            return
        }
        val target = access.chooseDoses()
        if (target < 1) {
            return
        }
        val result = decanter.decant(player, target)
        if (!result.found) {
            chatNpc(neutral, "I don't think you've got anything that I can decant.")
            return
        }
        if (target == MAX_DOSES) {
            chatNpc(happy, "There, all done.")
            return
        }
        if (random.randomBoolean()) {
            chatNpc(neutral, "Two plus two is four, minus one that's three...")
        }
        chatNpc(neutral, "Let's see what you have...")
        val vessels = vesselPrices()
        when {
            result.shortOfSpace > 0 -> chatNpc(sad, "You're a bit short of inventory space.")
            result.shortOfVessels > 0 && result.decanted == 0 ->
                chatNpc(sad, "You're a bit short of empty vessels or cash. $vessels")
            result.shortOfVessels > 0 ->
                chatNpc(
                    neutral,
                    "There, I've done what I can, but you're a bit short of empty vessels or " +
                        "cash. $vessels",
                )
            else -> chatNpc(happy, "There, all done.")
        }
    }

    private fun vesselPrices(): String =
        "Empty vials cost ${decanter.containerPrice(VIAL)} coins each and cups cost " +
            "${decanter.containerPrice(CUP)} coins each."

    private suspend fun ProtectedAccess.chooseDoses(): Int {
        player.chatModalUnclamp = 1
        ClientScripts.topLevelChatboxResetBackground(player)
        ifOpenSub(DECANT_INTERFACE, CHAT_MODAL, IfSubType.Modal)
        for (button in BUTTONS) {
            ifSetEvents(button, -1..-1, IfEvent.PauseButton)
        }
        val input = pauseButton()
        return BUTTONS.indexOfFirst { input.isComponentType(it) } + 1
    }

    private enum class Topic {
        Who,
        Prices,
        Decant,
        Leave,
    }

    private companion object {
        const val NPC = "npc.ge_expert_herbs"
        const val VIAL = "obj.vial_empty"
        const val CUP = "obj.cup_empty"
        const val MAX_DOSES = 4
        const val DECANT_INTERFACE = "interface.decant"
        const val CHAT_MODAL = "component.chatbox:chatmodal"
        val BUTTONS =
            listOf(
                "component.decant:decant_1",
                "component.decant:decant_2",
                "component.decant:decant_3",
                "component.decant:decant_4",
            )
        val CHATTER =
            listOf(
                "Please, please, please work.",
                "I'm in the money!",
                "I could have sworn that would have worked.",
                "Now what should I buy?",
                "Hope this item sells well.",
            )
    }
}
