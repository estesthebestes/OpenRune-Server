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

/** The bartender of the Dragon Inn in Yanille. */
class DragonInnBartender
@Inject
constructor(private val barcrawl: BarcrawlQuest, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.dragon_bartender") { startDialogue(it.npc) { bartender() } }
    }

    private suspend fun Dialogue.bartender() {
        chatNpc(happy, "What can I get you?")
        chatPlayer(quiz, "What's on the menu?")
        chatNpc(happy, "Dragon Bitter and Greenman's Ale, oh and some cheap beer.")
        val options = buildList {
            add("I'll give it a miss I think." to Topic.Nothing)
            add("I'll try the Dragon Bitter." to Topic.Bitter)
            add("Can I have some Greenman's Ale?" to Topic.Greenmans)
            if (barcrawl.canServe(player, BarcrawlBar.DragonInn)) {
                add("I'm doing Alfred Grimhand's Barcrawl." to Topic.Barcrawl)
            }
            add("One cheap beer please!" to Topic.Beer)
        }
        when (menu(options)) {
            Topic.Nothing -> {
                chatPlayer(neutral, "I'll give it a miss I think.")
                chatNpc(neutral, "Come back when you're a little thirstier.")
            }
            Topic.Bitter -> {
                chatPlayer(happy, "I'll try the Dragon Bitter.")
                chatNpc(happy, "Ok, that'll be two coins.")
                if (pay(BITTER_PRICE)) {
                    access.mes("You buy a pint of Dragon Bitter.")
                    player.invAddOrDrop(objRepo, "obj.dragon_bitter")
                }
            }
            Topic.Greenmans -> {
                chatPlayer(happy, "Can I have some Greenman's Ale?")
                chatNpc(happy, "Ok, that'll be ten coins.")
                if (pay(GREENMANS_PRICE)) {
                    access.mes("You buy a pint of Greenman's Ale.")
                    player.invAddOrDrop(objRepo, "obj.greenmans_ale")
                }
            }
            Topic.Beer -> {
                chatPlayer(happy, "One cheap beer please!")
                chatNpc(happy, "That'll be 2 gold coins please!")
                if (pay(BEER_PRICE)) {
                    player.invAddOrDrop(objRepo, "obj.beer")
                    objbox("obj.beer", "You buy a pint of cheap beer.")
                    chatNpc(happy, "Have a super day!")
                }
            }
            Topic.Barcrawl -> with(barcrawl) { serve(BarcrawlBar.DragonInn) }
        }
    }

    private suspend fun Dialogue.pay(price: Int): Boolean {
        if (player.invTakeFee(price)) {
            return true
        }
        chatPlayer(sad, "Oh dear. I don't seem to have enough money.")
        return false
    }

    private enum class Topic {
        Nothing,
        Bitter,
        Greenmans,
        Barcrawl,
        Beer,
    }

    private companion object {
        const val BITTER_PRICE = 2
        const val GREENMANS_PRICE = 10
        const val BEER_PRICE = 2
    }
}
