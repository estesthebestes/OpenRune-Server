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

/** The bartender of the Jolly Boar Inn, north-east of Varrock. */
class JollyBoarBartender
@Inject
constructor(private val barcrawl: BarcrawlQuest, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.jollyboar_bartender") { startDialogue(it.npc) { bartender() } }
    }

    private suspend fun Dialogue.bartender() {
        chatNpc(happy, "Can I help you?")
        val options = buildList {
            add("I'll have a beer please." to Topic.Beer)
            add("Any hints where I can go adventuring?" to Topic.Adventure)
            add("Heard any good gossip?" to Topic.Gossip)
            if (barcrawl.canServe(player, BarcrawlBar.JollyBoarInn)) {
                add("I'm doing Alfred Grimhands Barcrawl." to Topic.Barcrawl)
            }
        }
        when (menu(options)) {
            Topic.Beer -> beer()
            Topic.Adventure -> adventure()
            Topic.Gossip -> gossip()
            Topic.Barcrawl -> with(barcrawl) { serve(BarcrawlBar.JollyBoarInn) }
        }
    }

    private suspend fun Dialogue.beer() {
        chatPlayer(happy, "I'll have a pint of beer please.")
        chatNpc(happy, "Ok, that'll be two coins please.")
        if (!player.invTakeFee(BEER_PRICE)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        player.invAddOrDrop(objRepo, BEER)
    }

    private suspend fun Dialogue.adventure() {
        chatPlayer(quiz, "Any hints on where I can go adventuring?")
        chatNpc(neutral, "Ooh, now. Let me see...")
        chatNpc(
            neutral,
            "Well there is the Varrock sewers. There are tales of untold horrors coming out at " +
                "night and stealing babies from houses.",
        )
        chatPlayer(happy, "Sounds perfect! Where's the entrance?")
        chatNpc(neutral, "It's just to the east of the palace.")
    }

    private suspend fun Dialogue.gossip() {
        chatPlayer(quiz, "Heard any gossip?")
        chatNpc(
            neutral,
            "I'm not that well up on the gossip out here. I've heard that the bartender in the " +
                "Blue Moon Inn has gone a little crazy, he keeps claiming he is part of something " +
                "called an online game.",
        )
        chatNpc(
            neutral,
            "What that means, I don't know. That's probably old news by now though.",
        )
    }

    private enum class Topic {
        Beer,
        Adventure,
        Gossip,
        Barcrawl,
    }

    private companion object {
        const val BEER = "obj.beer"
        const val BEER_PRICE = 2
    }
}
