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

/** The bartender of the Flying Horse Inn in East Ardougne. */
class FlyingHorseBartender
@Inject
constructor(private val barcrawl: BarcrawlQuest, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.flyinghorse_bartender") { startDialogue(it.npc) { bartender() } }
    }

    private suspend fun Dialogue.bartender() {
        chatNpc(happy, "Would you like to buy a drink?")
        chatPlayer(quiz, "What do you serve?")
        chatNpc(laugh, "Beer!")
        val options = buildList {
            add("I'll have a beer then." to Topic.Beer)
            add("I'll not have anything then." to Topic.Nothing)
            if (barcrawl.canServe(player, BarcrawlBar.FlyingHorseInn)) {
                add("I'm doing Alfred Grimhand's Barcrawl." to Topic.Barcrawl)
            }
        }
        when (menu(options)) {
            Topic.Beer -> {
                chatPlayer(happy, "I'll have a beer then.")
                chatNpc(happy, "Ok, that'll be two coins.")
                if (!player.invTakeFee(BEER_PRICE)) {
                    chatPlayer(sad, "Oh dear. I don't seem to have enough money.")
                    return
                }
                player.invAddOrDrop(objRepo, "obj.beer")
            }
            Topic.Nothing -> chatPlayer(neutral, "I'll not have anything then.")
            Topic.Barcrawl -> with(barcrawl) { serve(BarcrawlBar.FlyingHorseInn) }
        }
    }

    private enum class Topic {
        Beer,
        Nothing,
        Barcrawl,
    }

    private companion object {
        const val BEER_PRICE = 2
    }
}
