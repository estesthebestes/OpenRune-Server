package org.rsmod.content.quest.area.wilderness.entertheabyss

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_SENT_TO_VARROCK
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Mage of Zamorak at the north end of the River Lum. He starts Enter the Abyss, runs the Battle
 * Runes shop (more stock once the miniquest is done) and, after it, teleports players to the Abyss.
 */
class MageOfZamorakWilderness
@Inject
constructor(
    private val eta: EnterTheAbyssQuest,
    private val abyss: AbyssTeleport,
    private val shops: Shops,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MAGE) { startDialogue(it.npc) { mageDialogue(it.npc) } }
        onOpNpc3(MAGE) { openShop() }
        onOpNpc4(MAGE) { teleport(it.npc) }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!eta.isComplete(player)) {
            return
        }
        abyss.teleport(this, npc)
    }

    private suspend fun Dialogue.mageDialogue(npc: Npc) {
        when (player.wornOpposedGod()) {
            OpposedGod.Saradomin -> return chatNpc(angry, "I don't speak to Saradominist filth.")
            OpposedGod.Guthix -> return chatNpc(angry, "Pathetic Guthixian... Don't bother me.")
            null -> {}
        }
        val stage = eta.stage(player)
        when {
            eta.isComplete(player) -> standard(npc)
            stage >= STAGE_SENT_TO_VARROCK -> alreadyToldYou()
            else -> firstMeeting()
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(
            neutral,
            "If you want to talk, this isn't the place for it. Meet me in Varrock's Chaos " +
                "Temple, by the rune shop. Unless you're here to buy something?",
        )
        if (!eta.meetsRequirements(player)) {
            mesbox("You need to have completed Rune Mysteries before you can help the Mage of Zamorak.")
            return
        }
        eta.setStage(access, STAGE_SENT_TO_VARROCK)
        shopOrLeave()
    }

    private suspend fun Dialogue.alreadyToldYou() {
        chatNpc(
            neutral,
            "I already told you to meet me in Varrock's Chaos Temple, by the rune shop. Unless " +
                "you're here to buy something?",
        )
        shopOrLeave()
    }

    private suspend fun Dialogue.shopOrLeave() {
        val choice = choice2("Let's see what you're selling.", 1, "Alright, I'll go.", 2)
        if (choice == 1) {
            chatPlayer(neutral, "Let's see what you're selling.")
            access.openShop()
        } else {
            chatPlayer(neutral, "Alright, I'll go.")
        }
    }

    private suspend fun Dialogue.standard(npc: Npc) {
        chatNpc(
            neutral,
            "This isn't the place to talk. Visit me in Varrock's Chaos Temple if you have " +
                "something to discuss. Unless you're here to teleport or buy something?",
        )
        val choice =
            choice3(
                "Let's see what you're selling.",
                1,
                "Could you teleport me to the Abyss?",
                2,
                "Alright, I'll go.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "Let's see what you're selling.")
                access.openShop()
            }
            2 -> {
                chatPlayer(quiz, "Could you teleport me to the Abyss?")
                abyss.teleport(access, npc)
            }
            3 -> chatPlayer(neutral, "Alright, I'll go.")
        }
    }

    private fun ProtectedAccess.openShop() {
        if (eta.isComplete(player)) {
            shops.open(player, TITLE, UPGRADED_STOCK, buyPercentage = 55.0, sellPercentage = 100.0, changePercentage = 0.2)
        } else {
            shops.open(player, TITLE, BASIC_STOCK, buyPercentage = 40.0, sellPercentage = 130.0, changePercentage = 3.0)
        }
    }

    private companion object {
        const val MAGE = "npc.rcu_zammy_mage1"
        const val TITLE = "Battle Runes"
        const val BASIC_STOCK = "inv.darkruneshop_crap"
        const val UPGRADED_STOCK = "inv.darkruneshop_uber"
    }
}
