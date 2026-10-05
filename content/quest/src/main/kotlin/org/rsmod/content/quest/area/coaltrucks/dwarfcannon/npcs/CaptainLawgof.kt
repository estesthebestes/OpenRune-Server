package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.AMMO_MOULD
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.HAMMER
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.LAWGOF
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.NOTES
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.RAILING
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.RAILING_COUNT
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.REMAINS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_CANNON_FIXED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_FIND_GILOB
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_FIND_LOLLK
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_HAVE_MOULD
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_LOLLK_RESCUED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_RAILINGS_FIXED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_REPAIR_CANNON
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_SEE_NULODION
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.TOOLKIT
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Captain Lawgof, commander of the Black Guard outpost by the Coal Trucks. He gives out the
 * railings, the toolkit and every errand of the quest, and shouts at his troops between visitors.
 */
class CaptainLawgof
@Inject
constructor(
    private val dwarfCannon: DwarfCannonQuest,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(LAWGOF) { startDialogue(it.npc) { lawgof() } }
        onAiTimer(LAWGOF) { npc.shout() }
    }

    private fun Npc.shout() {
        aiTimer(random.of(SHOUT_MIN_TICKS..SHOUT_MAX_TICKS))
        say(SHOUTS.random())
    }

    private suspend fun Dialogue.lawgof() {
        when (dwarfCannon.stage(player)) {
            0 -> offerQuest()
            STAGE_STARTED -> railingsReport()
            STAGE_RAILINGS_FIXED -> watchtowerOrders()
            STAGE_FIND_GILOB -> watchtowerReport()
            STAGE_FIND_LOLLK, STAGE_FIND_LOLLK + 1 -> lollkMissing()
            STAGE_LOLLK_RESCUED -> cannonFavour()
            STAGE_REPAIR_CANNON -> repairReport()
            STAGE_CANNON_FIXED -> cannonFixed()
            STAGE_SEE_NULODION -> engineerReport()
            STAGE_HAVE_MOULD -> deliverMould()
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(happy, "Hello.")
        chatNpc(
            happy,
            "Guthix be praised, the cavalry has arrived! Hero, how would you like to be made an " +
                "honorary member of the Black Guard?",
        )
        chatPlayer(confused, "The Black Guard? What's that?")
        chatNpc(
            laugh,
            "Hawhaw! 'What's that' ${pronoun()} asks, what a sense of humour! The Black Guard " +
                "is the finest regiment in the dwarven army. Only the best of the best are allowed " +
                "to join it and then they receive months of rigorous training.",
        )
        chatNpc(
            neutral,
            "However, we are currently in need of a hero, so for a limited time only I'm offering " +
                "you, a human, a chance to join this prestigious regiment. What do you say?",
        )
        if (!startQuestPrompt(dwarfCannon.quest)) {
            chatPlayer(neutral, "I'm sorry, I'm too busy mining.")
            chatNpc(
                worried,
                "Well best make the most of it while you can, if the goblins over-run us they'll " +
                    "descend on the mines like a plague of locusts!",
            )
            return
        }
        chatPlayer(happy, "Sure, I'd be honoured to join.")
        chatNpc(
            happy,
            "That's the spirit! Now trooper, we have no time to waste - the goblins are attacking " +
                "from the forests to the South. There are so many of them, they are overwhelming my " +
                "men and breaking through our",
        )
        chatNpc(
            neutral,
            "perimeter defences; could you please try to fix the stockade by replacing the broken " +
                "rails with these new ones?",
        )
        chatPlayer(neutral, "Sure, sounds easy enough...")
        val needsHammer = HAMMER !in player.inv
        dwarfCannon.syncVars(player)
        dwarfCannon.advanceTo(access, STAGE_STARTED)
        access.invAddOrDrop(objRepo, RAILING, RAILING_COUNT)
        if (needsHammer) {
            access.invAddOrDrop(objRepo, HAMMER)
        }
        objbox(RAILING, "The Dwarf Captain gives you six railings.")
        if (needsHammer) {
            chatNpc(neutral, "You'll need this hammer too.")
            objbox(HAMMER, "The Dwarf Captain gives you a hammer.")
        }
        chatNpc(neutral, "Report back to me once you've fixed the railings.")
        chatPlayer(happy, "Yes Sir, Captain!")
    }

    private suspend fun Dialogue.railingsReport() {
        chatPlayer(happy, "Hello.")
        chatNpc(quiz, "Hello, trooper, how are you doing with those railings?")
        chatPlayer(neutral, "I'm getting there.")
        chatNpc(worried, "The goblins are still getting in, so there must still be some broken railings.")
        if (RAILING in player.inv) {
            chatPlayer(neutral, "Don't worry, I'll find them soon enough.")
            return
        }
        chatPlayer(sad, "But I'm out of railings...")
        chatNpc(happy, "That's okay, we've got plenty, here you go.")
        if (player.inv.isFull()) {
            chatNpc(neutral, "There's no room in your backpack, so I can't give you anymore.")
            return
        }
        access.invAdd(player.inv, RAILING)
        objbox(RAILING, "The Dwarf Captain gives you another railing.")
    }

    private suspend fun Dialogue.watchtowerOrders() {
        chatNpc(
            happy,
            "Well done, trooper! The goblins seems to have stopped getting in. I think you've done " +
                "the job!",
        )
        player.inv.count(RAILING).takeIf { it > 0 }?.let { access.invDel(player.inv, RAILING, it) }
        chatPlayer(happy, "Great, I'll be getting on then.")
        chatNpc(angry, "What? I'll have you jailed for desertion!")
        chatNpc(
            neutral,
            "Besides, I have another commission for you. Just before the goblins over-ran us we " +
                "lost contact with our watch tower to the South, that's why the goblins managed to " +
                "catch us unawares. I'd like you to perform",
        )
        chatNpc(
            neutral,
            "a covert operation into enemy territory, to check up on the guards we have stationed " +
                "there.",
        )
        chatNpc(worried, "They should have reported in by now ...")
        chatPlayer(neutral, "Okay, I'll see what I can find out.")
        dwarfCannon.advanceTo(access, STAGE_FIND_GILOB)
        chatNpc(
            happy,
            "Excellent! I have two men there, the dwarf-in-charge is called Gilob, find him and " +
                "tell him that I'll send him a relief guard just as soon as we mop up these " +
                "remaining goblins.",
        )
    }

    private suspend fun Dialogue.watchtowerReport() {
        chatPlayer(happy, "Hello.")
        if (REMAINS !in player.inv) {
            chatNpc(quiz, "Hello, any news from the watchman?")
            chatPlayer(neutral, "Not yet.")
            chatNpc(neutral, "Well, as quick as you can then.")
            return
        }
        chatNpc(quiz, "Have you been to the watch tower yet?")
        chatPlayer(
            sad,
            "I have some terrible news for you Captain, the goblins over ran the tower, your guards " +
                "fought well but were overwhelmed.",
        )
        if (access.invDel(player.inv, REMAINS).failure) {
            return
        }
        dwarfCannon.advanceTo(access, STAGE_FIND_LOLLK)
        objbox(REMAINS, "You give the Dwarf Captain his subordinate's remains...")
        chatNpc(
            sad,
            "I can't believe it, Gilob was the finest lieutenant I had! We'll give him a fitting " +
                "funeral, but what of his command? His son, Lollk, was with him. Did you find his " +
                "body too?",
        )
        chatPlayer(neutral, "No, there was only one body there, I searched pretty well.")
        chatNpc(
            worried,
            "The goblins must have taken him. Please traveler, seek out the goblins' hideout and " +
                "return the lad to us. They always attack from the South-east, so they must be " +
                "based down there.",
        )
        chatPlayer(neutral, "Okay, I'll see if I can find their hideout.")
    }

    private suspend fun Dialogue.lollkMissing() {
        chatPlayer(happy, "Hello.")
        chatNpc(worried, "Have you found Lollk yet? The goblins' hideout must be to the South-east.")
        chatPlayer(neutral, "Not yet, I'm still looking.")
    }

    private suspend fun Dialogue.cannonFavour() {
        chatPlayer(quiz, "Hello, has Lollk returned yet?")
        chatNpc(
            happy,
            "He has, and I thank you from the bottom of my heart - without you he'd be a goblin " +
                "barbecue!",
        )
        chatPlayer(happy, "Always a pleasure to help.")
        chatNpc(neutral, "In that case could I ask one more favour of you...")
        chatNpc(
            worried,
            "When the goblins attacked us some of them managed to slip past my guards and sabotage " +
                "our cannon. I don't have anybody who understands how it works, could you have a " +
                "look at it and see if you could get it working for",
        )
        chatNpc(worried, "us, please?")
        val accepted =
            choice2(
                "Okay, I'll see what I can do.",
                true,
                "Sorry, I've done enough for today.",
                false,
            )
        if (!accepted) {
            chatPlayer(neutral, "Sorry, I've done enough for today.")
            chatNpc(
                sad,
                "Fair enough, didn't think you'd be able to help anyway, not much chance for a human " +
                    "to be able to fix it when the best dwarven engineers have failed.",
            )
            return
        }
        chatPlayer(neutral, "Okay, I'll see what I can do.")
        chatNpc(happy, "Thank you, take this toolkit, you'll need it...")
        if (player.inv.isFull()) {
            chatNpc(neutral, "You don't have room to take this, report back to me when you do.")
            return
        }
        access.invAdd(player.inv, TOOLKIT)
        dwarfCannon.advanceTo(access, STAGE_REPAIR_CANNON)
        objbox(TOOLKIT, "The Dwarf Captain gives you a tool kit.")
        chatNpc(neutral, "Report back to me if you manage to fix it.")
    }

    private suspend fun Dialogue.repairReport() {
        chatNpc(
            quiz,
            "How are you doing in there, trooper? We've been trying our best with that thing, but " +
                "I just haven't got the patience.",
        )
        chatPlayer(neutral, "It's not an easy job, but I'm getting there.")
        chatNpc(
            happy,
            "Good stuff, let me know if you have any luck. If we manage to get that thing working, " +
                "those goblins will be no trouble at all.",
        )
        if (dwarfCannon.ownsItem(access, TOOLKIT)) {
            return
        }
        chatPlayer(sad, "I'm afraid I lost the toolkit...")
        if (player.inv.isFull()) {
            chatNpc(
                neutral,
                "That was silly... never mind, I can give you another, but you'll have to free up " +
                    "some space in your backpack first.",
            )
            return
        }
        chatNpc(neutral, "That was silly... never mind, here you go.")
        access.invAdd(player.inv, TOOLKIT)
        objbox(TOOLKIT, "The Dwarf Captain gives you another toolkit.")
    }

    private suspend fun Dialogue.cannonFixed() {
        chatPlayer(happy, "Hello again.")
        chatNpc(quiz, "Hello there trooper, how's things?")
        chatPlayer(happy, "Well, I think I've done it, take a look...")
        chatNpc(happy, "That's fantastic, well done!")
        chatNpc(
            shocked,
            "Well I don't believe it, it seems to be working perfectly! I seem to have " +
                "underestimated you, trooper!",
        )
        chatPlayer(happy, "Not bad for an adventurer eh?")
        player.inv.count(TOOLKIT).takeIf { it > 0 }?.let { access.invDel(player.inv, TOOLKIT, it) }
        objbox(TOOLKIT, "You give the toolkit back to Captain Lawgof.")
        chatNpc(
            happy,
            "Not bad at all, your effort is appreciated, my friend. Now, if I could figure what " +
                "the thing uses as ammo...",
        )
        chatNpc(
            worried,
            "The Black Guard forgot to send instructions. I know I said that was the last favour, " +
                "but...",
        )
        chatPlayer(bored, "What now?")
        chatNpc(
            neutral,
            "I can't leave this post, could you go to the Black Guard base and find out what this " +
                "thing actually shoots?",
        )
        val accepted =
            choice2("Sorry, I've really done enough.", false, "Okay then, just for you!", true)
        if (!accepted) {
            chatPlayer(neutral, "Sorry, I've really done enough.")
            chatNpc(sad, "Fair enough, fair enough.")
            return
        }
        chatPlayer(happy, "Okay then, just for you!")
        dwarfCannon.advanceTo(access, STAGE_SEE_NULODION)
        chatNpc(
            happy,
            "That's great, we were lucky you came along when you did. The base is located just " +
                "South of the Ice Mountain.",
        )
        chatNpc(
            neutral,
            "You'll need to speak to Nulodion, the Dwarf Cannon engineer. He's the Weapons " +
                "Development Chief for the Black Guard, so if anyone knows how to fire this thing, " +
                "it'll be him.",
        )
        chatPlayer(neutral, "Okay, I'll see what I can do.")
    }

    private suspend fun Dialogue.engineerReport() {
        chatPlayer(happy, "Hi again.")
        chatNpc(quiz, "Hello trooper, any word from the Cannon Engineer?")
        chatPlayer(neutral, "Not yet.")
        chatNpc(
            neutral,
            "The Black Guard camp is South of the Ice Mountain, the quicker we can get some ammo " +
                "for this thing the quicker those Goblins will leave us be.",
        )
        chatPlayer(neutral, "I'll get to it.")
    }

    private suspend fun Dialogue.deliverMould() {
        chatPlayer(happy, "Hi.")
        chatNpc(quiz, "Hello trooper, any word from the Cannon Engineer?")
        chatPlayer(happy, "Yes, I have spoken to him.")
        if (AMMO_MOULD !in player.inv || NOTES !in player.inv) {
            chatPlayer(
                worried,
                "He gave me some items to give you... but I seem to have lost something.",
            )
            chatNpc(neutral, "If you could go back and get another, I'd appreciate it.")
            chatPlayer(neutral, "Oh, okay then.")
            return
        }
        chatPlayer(happy, "He gave me an ammo mould and some notes to give to you...")
        chatNpc(
            happy,
            "Aah, of course, we make the ammo! This is great, now we will be able to defend " +
                "ourselves. I don't know how to thank you...",
        )
        chatPlayer(happy, "You could give me a cannon...")
        chatNpc(laugh, "Hah! You'd be lucky, those things are worth a fortune.")
        chatNpc(
            neutral,
            "I'll tell you what though, I'll write to the Cannon Engineer requesting him to sell " +
                "you one. He controls production of the cannons.",
        )
        chatNpc(
            neutral,
            "He won't be able to give you one, but for the right price, I'm sure he'll sell one to " +
                "you.",
        )
        chatPlayer(happy, "Hmmm... sounds interesting, I might take you up on that.")
        if (AMMO_MOULD !in player.inv || NOTES !in player.inv) {
            return
        }
        if (access.invDel(player.inv, AMMO_MOULD).failure) {
            return
        }
        if (access.invDel(player.inv, NOTES).failure) {
            return
        }
        dwarfCannon.complete(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Well hello there, how you doing?")
        chatPlayer(happy, "Not bad, yourself?")
        chatNpc(
            happy,
            "I'm great now, those goblins can't get close with this cannon blasting at them!",
        )
    }

    private fun Dialogue.pronoun(): String =
        if (player.appearance.bodyType == Constants.bodytype_a) "he" else "she"

    private companion object {
        const val SHOUT_MIN_TICKS = 25
        const val SHOUT_MAX_TICKS = 60

        val SHOUTS =
            listOf(
                "Don't let any goblins through the breach in our fence!",
                "Keep an eye open for goblins!",
                "Don't just do something! Stand there!",
                "Stop dawdling soldier! You're in the army now!",
                "Hurry up on that patrol route, trooper!",
            )
    }
}
