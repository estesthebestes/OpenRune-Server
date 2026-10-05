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

/** The bartender of the Dead Man's Chest in Brimhaven. */
class DeadMansChestBartender
@Inject
constructor(private val barcrawl: BarcrawlQuest, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.deadmans_bartender") { startDialogue(it.npc) { bartender() } }
    }

    private suspend fun Dialogue.bartender() {
        chatNpc(happy, "Yohoho me hearty what would you like to drink?")
        val options = buildList {
            add("Nothing, thank you." to Topic.Nothing)
            add("A pint of Grog please." to Topic.Grog)
            add("A bottle of rum please." to Topic.Rum)
            if (barcrawl.canServe(player, BarcrawlBar.DeadMansChest)) {
                add("I'm doing Alfred Grimhand's Barcrawl." to Topic.Barcrawl)
            }
        }
        when (menu(options)) {
            Topic.Nothing -> chatPlayer(neutral, "Nothing, thank you.")
            Topic.Grog -> {
                chatPlayer(happy, "A pint of Grog please.")
                chatNpc(happy, "One grog coming right up, that'll be three coins.")
                buy("obj.grog", GROG_PRICE)
            }
            Topic.Rum -> {
                chatPlayer(happy, "A bottle of rum please.")
                chatNpc(happy, "That'll be 27 coins.")
                buy("obj.karamja_rum", RUM_PRICE)
            }
            Topic.Barcrawl -> with(barcrawl) { serve(BarcrawlBar.DeadMansChest) }
        }
    }

    private suspend fun Dialogue.buy(obj: String, price: Int) {
        if (!player.invTakeFee(price)) {
            chatPlayer(sad, "Oh dear. I don't seem to have enough money.")
            return
        }
        player.invAddOrDrop(objRepo, obj)
    }

    private enum class Topic {
        Nothing,
        Grog,
        Rum,
        Barcrawl,
    }

    private companion object {
        const val GROG_PRICE = 3
        const val RUM_PRICE = 27
    }
}
