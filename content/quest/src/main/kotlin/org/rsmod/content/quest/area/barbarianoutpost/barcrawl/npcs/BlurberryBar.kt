package org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlBar
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Blurberry and his barmen in the Grand Tree. Blurberry serves the barcrawl's Fire Toad Blast
 * himself; his barmen run the cocktail menu and send barcrawlers along to him.
 */
class BlurberryBar @Inject constructor(private val barcrawl: BarcrawlQuest, private val shops: Shops) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(BLURBERRY) { startDialogue(it.npc) { blurberry() } }
        onOpNpc1(BARMAN) { startDialogue(it.npc) { barman(it.npc) } }
        onOpNpc3(BARMAN) { openMenu(it.npc) }
    }

    private suspend fun Dialogue.blurberry() {
        if (barcrawl.canServe(player, BarcrawlBar.Blurberry)) {
            with(barcrawl) { serve(BarcrawlBar.Blurberry, server = "Blurberry") }
            return
        }
        chatPlayer(happy, "Hello.")
        chatNpc(
            happy,
            "Well hello there traveller. If you're looking for a cocktail the barman will happily " +
                "make you one.",
        )
    }

    private suspend fun Dialogue.barman(npc: Npc) {
        chatNpc(happy, "Good day to you. What can I get you to drink?")
        val options = buildList {
            if (barcrawl.canServe(player, BarcrawlBar.Blurberry)) {
                add("I'm trying to do Alfred Grimhand's barcrawl." to Topic.Barcrawl)
            }
            add("What do you have?" to Topic.Menu)
            add("Nothing thanks." to Topic.Nothing)
            add("Can I buy some ingredients?" to Topic.Ingredients)
        }
        when (menu(options)) {
            Topic.Barcrawl -> {
                chatPlayer(neutral, "I'm trying to do Alfred Grimhand's barcrawl.")
                chatNpc(
                    laugh,
                    "Oh, another silly human come to have ${pronoun()} mind melted? You should " +
                        "take that barcrawl card to Blurberry - he always likes to serve the Fire " +
                        "Toad Blast himself!",
                )
                chatPlayer(confused, "Um... thanks?!")
            }
            Topic.Menu -> {
                chatPlayer(quiz, "What do you have.")
                chatNpc(happy, "Here, take a look at our menu.")
                access.openMenu(npc)
            }
            Topic.Nothing -> {
                chatPlayer(neutral, "Nothing thanks.")
                chatNpc(happy, "Ok, take it easy.")
            }
            Topic.Ingredients -> ingredients()
        }
    }

    private suspend fun Dialogue.ingredients() {
        chatPlayer(quiz, "I was just wanting to buy a cocktail ingredient actually.")
        chatNpc(happy, "Sure thing, what did you want?")
        val (line, obj) =
            menu(
                "A lemon" to ("A lemon." to "obj.lemon"),
                "An orange." to ("An orange." to "obj.orange"),
                "A cocktail shaker." to ("A cocktail shaker." to "obj.cocktail_shaker"),
                "Nothing thanks." to ("Actually nothing thanks." to null),
            )
        chatPlayer(neutral, line)
        if (obj == null) {
            return
        }
        if (player.inv.isFull()) {
            mesbox("You don't have enough room in your inventory to buy that.")
            return
        }
        chatNpc(happy, "20 coins please.")
        if (!player.invTakeFee(INGREDIENT_PRICE)) {
            access.mes("You do not have enough money to buy that.")
            return
        }
        access.invAdd(access.inv, obj)
    }

    private fun Dialogue.pronoun(): String =
        if (player.appearance.bodyType == Constants.bodytype_a) "his" else "her"

    private fun ProtectedAccess.openMenu(npc: Npc) {
        shops.open(player, npc, SHOP_TITLE, SHOP_INV)
    }

    private enum class Topic {
        Barcrawl,
        Menu,
        Nothing,
        Ingredients,
    }

    private companion object {
        const val BLURBERRY = "npc.blurberry"
        const val BARMAN = "npc.blurberrybarmen"
        const val SHOP_TITLE = "Blurberry Bar"
        const val SHOP_INV = "inv.blurberrybar"
        const val INGREDIENT_PRICE = 20
    }
}
