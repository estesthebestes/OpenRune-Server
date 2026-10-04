package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.HasOrb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Orb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.OrbReturned
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Started
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.StrongholdBreached
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.WarlordSlain
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gnomes of the village whose only part in the quest is commentary: Remsai, Kalron, the Local
 * Gnomes and Bolkoy the shopkeeper.
 */
class Villagers
@Inject
constructor(private val quest: TreeGnomeVillageQuest, private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(Remsai) { startDialogue(it.npc) { remsai() } }
        onOpNpc1(Kalron) { startDialogue(it.npc) { kalron() } }
        onOpNpc1(LocalGnome) { startDialogue(it.npc) { localGnome() } }
        onOpNpc1(Bolkoy) { startDialogue(it.npc) { bolkoy(it.npc) } }
        onOpNpc3(Bolkoy) { player.openShop(it.npc) }
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(this, npc, "Bolkoy's Village Shop", "inv.gnomeshop")
    }

    private suspend fun Dialogue.remsai() {
        when (quest.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Well done, well done. Not many find their way in here.")
                chatNpc(
                    happy,
                    "I'm Remsai, a tree gnome. We live in this maze for our protection. Have a " +
                        "look around and enjoy.",
                )
            }
            in Started until StrongholdBreached -> {
                chatNpc(worried, "Oh my, oh my!")
                chatPlayer(quiz, "What's wrong?")
                chatNpc(worried, "The orb, they have the orb. It must be returned or we're doomed.")
            }
            StrongholdBreached,
            HasOrb -> {
                chatPlayer(happy, "Hello Remsai.")
                chatNpc(quiz, "Hello, did you find the orb?")
                if (player.inv.contains(Orb)) {
                    chatPlayer(happy, "I have it here.")
                    chatNpc(happy, "You're our saviour.")
                } else {
                    chatPlayer(sad, "No, I'm afraid not.")
                    chatNpc(worried, "Please, we must have the orb if we are to survive.")
                }
            }
            OrbReturned -> {
                chatPlayer(quiz, "Are you ok?")
                chatNpc(
                    sad,
                    "Khazard's men came. Without the orb we were defenceless. They killed " +
                    "many of " +
                        "us and then took our last hope, the other orbs.",
                )
                chatNpc(
                    sad,
                    "Now surely we're all doomed. Without them the spirit tree is useless.",
                )
            }
            WarlordSlain -> {
                chatPlayer(happy, "I've returned.")
                chatNpc(
                    happy,
                    "You're back, well done brave adventurer. Now the orbs are safe we can " +
                        "perform the ritual for the spirit tree, and live in peace once again.",
                )
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Hi there traveller. You're a legend around these parts.")
                chatPlayer(happy, "Thanks Remsai.")
            }
        }
    }

    private suspend fun Dialogue.kalron() {
        when (quest.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    worried,
                    "I have to find a way out. We built this maze for protection, but I " +
                    "can't get " +
                        "used to it. I'm always getting lost.",
                )
            }
            in Started until OrbReturned -> {
                chatPlayer(happy, "Hello, how are you?")
                chatNpc(
                    worried,
                    "Oh my. I'll never find my way back before Khazard's men come and hunt me " +
                        "down.",
                )
            }
            OrbReturned -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(
                    sad,
                    "Oh my, oh my, the village has been pillaged and I'm still lost. Oh " +
                        "dear.",
                )
            }
            WarlordSlain -> {
                chatPlayer(happy, "Hello little man.")
                chatNpc(sad, "Hello. I hope someone comes and finds me soon. It's getting cold.")
            }
            else -> {
                chatPlayer(happy, "Hello there, you look lost.")
                chatNpc(angry, "Are you trying to be funny?")
                chatPlayer(neutral, "No.")
                chatNpc(confused, "Hmmm...")
            }
        }
    }

    private suspend fun Dialogue.localGnome() {
        when (quest.stage(player)) {
            in 0 until StrongholdBreached -> {
                chatPlayer(happy, "Hello.")
                chatNpc(laugh, "Lardi dee, lardi da.")
                chatPlayer(quiz, "Are you alright?")
                chatNpc(
                    laugh,
                    "Hee hee, lardi da, lardi dee. <col=ffffff>(The gnome appears to be " +
                    "singing.)</col>",
                )
            }
            StrongholdBreached,
            HasOrb -> {
                chatPlayer(happy, "Hello little man.")
                chatNpc(laugh, "Little man stronger than big man. Hee hee, lardi dee, lardi da.")
            }
            OrbReturned -> {
                chatPlayer(happy, "Hi.")
                chatNpc(
                    laugh,
                    "Must save the orbs and kill the Khazard warlord. That will be fun, hee " +
                        "hee.",
                )
            }
            WarlordSlain -> {
                chatPlayer(happy, "Hello gnome.")
                chatNpc(
                    laugh,
                    "Soon we're gonna have the sacred ceremony and boy am I going to party. Lock " +
                        "up your daughters. Hee hee.",
                )
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(laugh, "You're the best!")
                chatPlayer(happy, "Thanks.")
                chatNpc(laugh, "Well I'm better. Hee hee.")
            }
        }
    }

    private suspend fun Dialogue.bolkoy(npc: Npc) {
        when (quest.stage(player)) {
            in 0 until StrongholdBreached -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(happy, "Hello stranger, new to these parts?")
                chatNpc(
                    happy,
                    "I'm Bolkoy by the way. I'm the village shopkeeper. Would you like to buy " +
                        "something?",
                )
            }
            StrongholdBreached,
            HasOrb -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Amazing, you recovered the orb.")
                chatNpc(happy, "Well I am impressed. Would you like to buy something?")
            }
            OrbReturned -> {
                chatPlayer(happy, "Hi.")
                chatNpc(
                    sad,
                    "Oh, hello there. Have you heard? They took the other orbs, it's terrible. I " +
                        "suppose the show must go on.",
                )
                chatNpc(neutral, "Would you like to buy something?")
            }
            WarlordSlain -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    happy,
                    "Hello there. You're that hero who saved the orbs. Soon we will perform the " +
                        "ritual and the village will be safe again.",
                )
                chatNpc(happy, "Anyway, would you like anything from my shop?")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    happy,
                    "Welcome, welcome. It's good to see you again. The village is much safer now " +
                        "you have returned the orbs.",
                )
                chatNpc(
                    happy,
                    "By the way, I'm the village shopkeeper. Would you like to buy something?",
                )
            }
        }
        if (!choice2("What have you got?", true, "No thank you.", false)) {
            chatPlayer(neutral, "No thank you.")
            chatNpc(neutral, "Ok, maybe later.")
            return
        }
        chatPlayer(quiz, "What have you got?")
        chatNpc(happy, "Take a look.")
        player.openShop(npc)
    }

    private companion object {
        const val Remsai = "npc.remsai"
        const val Kalron = "npc.lostgnome"
        const val LocalGnome = "npc.chantergnome"
        const val Bolkoy = "npc.treevillage_shopkeeper1"
    }
}
