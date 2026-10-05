package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.AMMO_MOULD
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CANNON_BARRELS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CANNON_BASE
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CANNON_FURNACE
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CANNON_STAND
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.COINS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.MANUAL
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.NOTES
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.NULODION
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_HAVE_MOULD
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_SEE_NULODION
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.cannonStage
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.cannonStyle
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.clearCannonVars
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nulodion, the Black Guard's cannon engineer, in the west house of their camp south of Ice
 * Mountain. His door stays locked until Lawgof sends the player to him. Once the quest is done he
 * sells the multicannon, as a discounted set through dialogue or piece by piece in his store.
 */
class Nulodion
@Inject
constructor(
    private val dwarfCannon: DwarfCannonQuest,
    private val shops: Shops,
    private val objRepo: ObjRepository,
    private val cannons: Multicannons,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(NULODION) { startDialogue(it.npc) { nulodion(it.npc) } }
        onOpNpc3(NULODION) { trade(it.npc) }
    }

    private fun canTrade(access: ProtectedAccess): Boolean =
        QuestRequirements.hasCompleted(access.player, DwarfCannonQuest.QUEST_KEY)

    private suspend fun ProtectedAccess.trade(npc: Npc) {
        if (!canTrade(this)) {
            startDialogue(npc) { refuseTrade() }
            return
        }
        openShop(npc)
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, SHOP_TITLE, SHOP_INV)
    }

    private suspend fun Dialogue.refuseTrade() {
        chatNpc(
            neutral,
            "Sorry, the Black Guard's weapons are top secret. I can't sell them to just anybody.",
        )
    }

    private suspend fun Dialogue.nulodion(npc: Npc) {
        when (dwarfCannon.stage(player)) {
            STAGE_SEE_NULODION -> giveMould()
            STAGE_HAVE_MOULD -> replaceItems()
            else -> standard(npc)
        }
    }

    private suspend fun Dialogue.giveMould() {
        chatPlayer(happy, "Hello there.")
        chatNpc(quiz, "Can I help you?")
        chatPlayer(neutral, "Captain Lawgof sent me. He's having trouble with his cannon.")
        chatNpc(shocked, "Of course, we forgot to send the ammo mould!")
        chatPlayer(confused, "It fires mould?")
        chatNpc(
            laugh,
            "Don't be silly - the ammo's made by using a mould. Here, take these to him. The " +
                "instructions explain everything.",
        )
        if (player.inv.freeSpace() < 2) {
            chatNpc(
                neutral,
                "You can't take these things unless you free up some space in your inventory. I " +
                    "suggest you visit a bank and then come back to talk to me.",
            )
            return
        }
        access.invAdd(player.inv, NOTES)
        access.invAdd(player.inv, AMMO_MOULD)
        dwarfCannon.advanceTo(access, STAGE_HAVE_MOULD)
        doubleobjbox(NOTES, AMMO_MOULD, "The Cannon Engineer gives you some notes and a mould.")
        chatPlayer(happy, "That's great, thanks.")
        chatNpc(happy, "Thank you, adventurer. The Dwarf Black Guard will remember this.")
    }

    private suspend fun Dialogue.replaceItems() {
        chatPlayer(happy, "Hello again.")
        if (!dwarfCannon.ownsItem(access, NOTES)) {
            chatPlayer(sad, "I've lost the notes.")
            chatNpc(neutral, "Here, take these...")
            access.invAddOrDrop(objRepo, NOTES)
            objbox(NOTES, "The Cannon Engineer gives you some more notes.")
            return
        }
        if (!dwarfCannon.ownsItem(access, AMMO_MOULD)) {
            chatPlayer(sad, "I've lost the cannonball mould.")
            chatNpc(bored, "Deary me, you are trouble. Here, take this one.")
            access.invAddOrDrop(objRepo, AMMO_MOULD)
            objbox(AMMO_MOULD, "The Cannon Engineer gives you another mould.")
            return
        }
        chatNpc(quiz, "So has the Captain figured out how to work the cannon yet?")
        chatPlayer(neutral, "Not yet, but I'm sure he will.")
        chatNpc(neutral, "If you can get those items to him it'll be a great help.")
    }

    private suspend fun Dialogue.standard(npc: Npc) {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Hello traveller, how's things?")
        chatPlayer(happy, "Not bad thanks, yourself?")
        chatNpc(neutral, "I'm good, just working hard as usual...")
        if (!canTrade(access)) {
            return
        }
        val topic =
            choice4(
                "I was hoping you might sell me a cannon.",
                Topic.Sell,
                "I've lost my cannon.",
                Topic.Lost,
                "I want to know more about the cannon.",
                Topic.Info,
                "Well, take care of yourself then.",
                Topic.Leave,
            )
        when (topic) {
            Topic.Sell -> sellCannon(npc)
            Topic.Lost -> lostCannon()
            Topic.Info -> cannonInfo()
            Topic.Leave -> {
                chatPlayer(happy, "Well, take care of yourself then.")
                chatNpc(happy, "Take care, adventurer.")
            }
        }
    }

    private suspend fun Dialogue.cannonInfo() {
        chatPlayer(quiz, "I want to know more about the cannon.")
        chatNpc(
            neutral,
            "It's the most destructive weapon the Black Guard has developed so far, and it fires " +
                "in short bursts. It automatically targets any monsters close by.",
        )
        chatNpc(neutral, "You'll need to make your own ammunition for it, mind.")
    }

    private suspend fun Dialogue.lostCannon() {
        chatPlayer(sad, "I've lost my cannon.")
        if (player.cannonStage == Multicannons.STAGE_NONE) {
            chatNpc(
                neutral,
                "I'm only allowed to replace cannons that were stolen in action, and you don't " +
                    "seem to have lost one.",
            )
            return
        }
        if (cannons.of(player) != null) {
            chatNpc(
                neutral,
                "Hmmm. I think you'll find it's still happily parked on the spot where you put it.",
            )
            chatPlayer(confused, "Oh, is it still there? I thought I'd lost it.")
            chatNpc(laugh, "Ha ha ha, what a muddle-headed numpty you are!")
            chatPlayer(neutral, "...")
            return
        }
        val parts = player.cannonStyle.parts
        if (player.inv.freeSpace() < parts.size) {
            chatNpc(
                neutral,
                "That's unfortunate! But don't worry, I can sort you out if you free up some " +
                    "inventory space...",
            )
            return
        }
        chatNpc(neutral, "That's unfortunate! But don't worry, I can sort you out...")
        for (part in parts) {
            access.invAdd(player.inv, part)
        }
        player.clearCannonVars()
        doubleobjbox(parts[2], parts[3], "The dwarf gives you a new cannon.")
        chatNpc(shifty, "Keep that quiet or I'll be in real trouble!")
        chatPlayer(happy, "Thanks a lot.")
    }

    private suspend fun Dialogue.sellCannon(npc: Npc) {
        chatPlayer(quiz, "I was hoping you might sell me a cannon.")
        chatNpc(
            neutral,
            "Hmmmmmm... I shouldn't really, but as you helped us so much, well, I could sort " +
                "something out. I'll warn you though, they don't come cheap!",
        )
        chatPlayer(quiz, "How much?")
        chatNpc(
            neutral,
            "For the full setup, 750,000 coins. Or I can sell you the separate parts... but it'll " +
                "cost extra!",
        )
        chatPlayer(shocked, "That's not cheap!")
        val choice =
            choice4(
                "Okay, I'll take a cannon please.",
                Purchase.Set,
                "Can I look at the separate parts please?",
                Purchase.Parts,
                "Have you any ammo or instructions to sell?",
                Purchase.Ammo,
                "Sorry, that's too much for me.",
                Purchase.None,
            )
        when (choice) {
            Purchase.Set -> buySet()
            Purchase.Parts -> {
                chatPlayer(quiz, "Can I look at the separate parts please?")
                chatNpc(happy, "Of course!")
                access.openShop(npc)
            }
            Purchase.Ammo -> {
                chatPlayer(quiz, "Have you any ammo or instructions to sell?")
                chatNpc(happy, "Of course!")
                access.openShop(npc)
            }
            Purchase.None -> {
                chatPlayer(sad, "Sorry, that's too much for me.")
                chatNpc(neutral, "Fair enough, it's too much for most of us.")
            }
        }
    }

    private suspend fun Dialogue.buySet() {
        chatPlayer(happy, "Okay, I'll take a cannon please.")
        if (player.inv.freeSpace() < SET_SLOTS) {
            chatNpc(
                neutral,
                "Okay. There are four pieces to carry, plus the mould and instruction book, so " +
                    "you'll need to free up some space.",
            )
            return
        }
        if (player.inv.count(COINS) < SET_PRICE) {
            chatNpc(neutral, "Okay, come back when you've got the 750,000 coins.")
            return
        }
        if (access.invDel(player.inv, COINS, SET_PRICE).failure) {
            return
        }
        for (part in SET_ITEMS) {
            access.invAdd(player.inv, part)
        }
        chatNpc(shifty, "Okay then, but keep it quiet... This thing's top secret!")
        doubleobjbox(
            COINS,
            CANNON_BASE,
            "You give the cannon engineer 750,000 coins. He gives you the four parts that make the " +
                "cannon, plus an ammo mould and an instruction manual.",
        )
        chatNpc(neutral, "There you go, you be careful with that thing.")
        chatPlayer(happy, "Will do. Take care, mate.")
        chatNpc(happy, "Take care, adventurer.")
    }

    private enum class Topic {
        Sell,
        Lost,
        Info,
        Leave,
    }

    private enum class Purchase {
        Set,
        Parts,
        Ammo,
        None,
    }

    private companion object {
        const val SHOP_TITLE = "Multicannon parts for sale"
        const val SHOP_INV = "inv.mcannonshop"

        const val SET_PRICE = 750_000
        const val SET_SLOTS = 6
        val SET_ITEMS =
            listOf(CANNON_BASE, CANNON_STAND, CANNON_BARRELS, CANNON_FURNACE, AMMO_MOULD, MANUAL)
    }
}
