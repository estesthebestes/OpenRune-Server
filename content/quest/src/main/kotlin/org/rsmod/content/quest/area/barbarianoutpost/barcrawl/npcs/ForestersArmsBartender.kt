package org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlBar
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The bartender of the Forester's Arms in Seers' Village. */
class ForestersArmsBartender
@Inject
constructor(private val barcrawl: BarcrawlQuest, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.foresters_bartender") { startDialogue(it.npc) { bartender() } }
    }

    private suspend fun Dialogue.bartender() {
        chatNpc(happy, "Good morning, what would you like?")
        val options = buildList {
            add("What do you have?" to Topic.Menu)
            add("Beer please." to Topic.Beer)
            if (barcrawl.canServe(player, BarcrawlBar.ForestersArms)) {
                add("I'm doing Alfred Grimhand's Barcrawl." to Topic.Barcrawl)
            }
            add("I don't really want anything thanks." to Topic.Nothing)
        }
        when (menu(options)) {
            Topic.Menu -> menuOfTheDay()
            Topic.Beer -> beer()
            Topic.Barcrawl -> with(barcrawl) { serve(BarcrawlBar.ForestersArms) }
            else -> chatPlayer(neutral, "I don't really want anything thanks.")
        }
    }

    private suspend fun Dialogue.menuOfTheDay() {
        chatPlayer(quiz, "What do you have?")
        chatNpc(
            happy,
            "Well we have beer, or if you want some food, we have our home made stew and meat pies.",
        )
        val choice =
            menu(
                "Beer please." to Topic.Beer,
                "I'll try the meat pie." to Topic.Pie,
                "Could I have some stew please?" to Topic.Stew,
                "I don't really want anything thanks." to Topic.Nothing,
            )
        when (choice) {
            Topic.Beer -> beer()
            Topic.Pie -> {
                chatPlayer(happy, "I'll try the meat pie.")
                chatNpc(happy, "Ok, that'll be 16 coins.")
                buy("obj.meat_pie", PIE_PRICE, "You buy a nice hot meat pie.")
            }
            Topic.Stew -> {
                chatPlayer(happy, "Could I have some stew please?")
                chatNpc(happy, "A bowl of stew, that'll be 20 coins please.")
                buy("obj.stew", STEW_PRICE, "You buy a bowl of home made stew.")
            }
            else -> chatPlayer(neutral, "I don't really want anything thanks.")
        }
    }

    private suspend fun Dialogue.beer() {
        chatPlayer(happy, "Beer please.")
        chatNpc(happy, "One beer coming up. Ok, that'll be two coins.")
        buy("obj.beer", BEER_PRICE, "You buy a pint of beer.")
    }

    private suspend fun Dialogue.buy(obj: String, price: Int, message: String) {
        if (!player.invTakeFee(price)) {
            chatPlayer(sad, "Oh dear. I don't seem to have enough money.")
            return
        }
        access.mes(message)
        player.invAddOrDrop(objRepo, obj)
    }

    private enum class Topic {
        Menu,
        Beer,
        Pie,
        Stew,
        Barcrawl,
        Nothing,
    }

    private companion object {
        const val BEER_PRICE = 2
        const val PIE_PRICE = 16
        const val STEW_PRICE = 20
    }
}
