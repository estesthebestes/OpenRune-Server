package org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs

import jakarta.inject.Inject
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

/** Zembo, who runs Karamja Wines, Spirits, and Beers at Musa Point. */
class Zembo @Inject constructor(private val barcrawl: BarcrawlQuest, private val shops: Shops) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(ZEMBO) { startDialogue(it.npc) { zembo(it.npc) } }
        onOpNpc3(ZEMBO) { openShop(it.npc) }
    }

    private suspend fun Dialogue.zembo(npc: Npc) {
        chatNpc(
            happy,
            "Hey, are you wanting to try some of my fine wines and spirits? All brewed locally on " +
                "Karamja island.",
        )
        val options = buildList {
            add("Yes please." to Topic.Shop)
            add("No, thank you." to Topic.Nothing)
            if (barcrawl.canServe(player, BarcrawlBar.KaramjaSpiritsBar)) {
                add("I'm doing Alfred Grimhand's barcrawl." to Topic.Barcrawl)
            }
        }
        when (menu(options)) {
            Topic.Shop -> access.openShop(npc)
            Topic.Nothing -> chatPlayer(neutral, "No, thank you.")
            Topic.Barcrawl -> with(barcrawl) { serve(BarcrawlBar.KaramjaSpiritsBar, "Zembo") }
        }
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, SHOP_TITLE, SHOP_INV)
    }

    private enum class Topic {
        Shop,
        Nothing,
        Barcrawl,
    }

    private companion object {
        const val ZEMBO = "npc.zembo"
        const val SHOP_TITLE = "Karamja Wines, Spirits, and Beers."
        const val SHOP_INV = "inv.boozeshop"
    }
}
