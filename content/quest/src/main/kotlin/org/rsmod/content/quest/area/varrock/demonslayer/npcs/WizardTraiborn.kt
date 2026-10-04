package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.BONES
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.BONES_REQUIRED
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_TRAIBORN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_KEY_HUNT
import org.rsmod.content.quest.area.varrock.demonslayer.silverlightCaseEmpty
import org.rsmod.content.quest.area.varrock.demonslayer.traibornAsked
import org.rsmod.content.quest.area.varrock.demonslayer.traibornBonesGiven
import org.rsmod.content.quest.area.varrock.demonslayer.traibornKeyGiven
import org.rsmod.content.quest.manager.menu

/**
 * Wizard Traiborn's part of Demon Slayer. His dialogue is owned by the Wizards' Tower area; it
 * calls [traibornDialogue] straight after his greeting and carries on with its own conversation
 * when this returns `false`.
 */
@Singleton
class WizardTraiborn
@Inject
constructor(
    private val demonSlayer: DemonSlayerQuest,
    private val objRepo: ObjRepository,
    private val ritual: TraibornRitual,
) {

    suspend fun Dialogue.traibornDialogue(): Boolean {
        if (demonSlayer.stage(player) != STAGE_KEY_HUNT) {
            return false
        }
        if (!menu("Talk about Demon Slayer." to true, "Talk about something else." to false)) {
            return false
        }
        when {
            player.silverlightCaseEmpty -> keyInUse()
            player.traibornKeyGiven -> afterKey()
            !player.traibornAsked -> askForKey()
            else -> bones()
        }
        return true
    }

    private suspend fun Dialogue.askForKey() {
        chatPlayer(neutral, "I need to get a key given to you by Sir Prysin.")
        chatNpc(confused, "Sir Prysin? Who's that? What would I want his key for?")
        when (
            menu(
                "He told me you were looking after it for him." to Excuse.LookingAfter,
                "He's one of the King's knights." to Excuse.Knight,
                "Well, have you got any keys knocking around?" to Excuse.Keys,
            )
        ) {
            Excuse.LookingAfter -> lookingAfter()
            Excuse.Knight -> knight()
            Excuse.Keys -> keysAroundTheTower()
        }
    }

    private suspend fun Dialogue.lookingAfter() {
        chatPlayer(neutral, "He told me you were looking after it for him.")
        chatNpc(
            neutral,
            "That wasn't very clever of him. I'd lose my head if it wasn't screwed on. Go and " +
                "tell him to find someone else to look after his valuables in future.",
        )
        if (
            menu(
                "Okay, I'll go and tell him that." to true,
                "Well, have you got any keys knocking around?" to false,
            )
        ) {
            chatPlayer(neutral, "Okay, I'll go and tell him that.")
            chatNpc(happy, "Oh that's great, if it wouldn't be too much trouble.")
            if (
                menu(
                    "Err I'd better be off really." to true,
                    "Well, have you got any keys knocking around?" to false,
                )
            ) {
                return beOff()
            }
        }
        keysAroundTheTower()
    }

    private suspend fun Dialogue.knight() {
        chatPlayer(neutral, "He's one of the King's knights.")
        chatNpc(confused, "Say, I remember one of the King's knights. He had nice shoes...")
        chatNpc(
            happy,
            "...and didn't like my homemade spinach rolls. Would you like a spinach roll?",
        )
        if (
            menu(
                "Yes please." to true,
                "Just tell me if you have the key." to false,
            )
        ) {
            chatPlayer(happy, "Yes please.")
            access.invAddOrDrop(objRepo, SPINACH_ROLL)
            objbox(
                SPINACH_ROLL,
                "Traiborn digs around in the pockets of his robes. After a few moments he " +
                    "triumphantly presents you with a spinach roll.",
            )
            chatPlayer(happy, "Thank you very much.")
            if (
                menu(
                    "Err I'd better be off really." to true,
                    "Well, have you got any keys knocking around?" to false,
                )
            ) {
                return beOff()
            }
            return keysAroundTheTower()
        }
        chatPlayer(neutral, "Just tell me if you have the key.")
        chatNpc(confused, "The key? The key to what?")
        chatNpc(
            confused,
            "There's more than one key in the world don't you know? Would be a bit odd if " +
                "there was only the one.",
        )
        if (
            menu(
                "It's the key to get a sword called Silverlight." to true,
                "You've lost it, haven't you?" to false,
            )
        ) {
            chatPlayer(neutral, "It's the key to get a sword called Silverlight.")
            chatNpc(
                confused,
                "Silverlight? Never heard of that. Sounds a good name for a ship. Are you sure " +
                    "it's not the name of a ship rather than a sword?",
            )
            if (
                menu(
                    "Yeah, pretty sure." to true,
                    "Well, have you got any keys knocking around?" to false,
                )
            ) {
                chatPlayer(neutral, "Yeah, pretty sure.")
                chatNpc(sad, "That's a pity, waste of a name.")
                if (
                    menu(
                        "Err I'd better be off really." to true,
                        "Well, have you got any keys knocking around?" to false,
                    )
                ) {
                    return beOff()
                }
            }
        } else {
            chatPlayer(quiz, "You've lost it, haven't you?")
            chatNpc(angry, "Me? Lose things? That's a nasty accusation.")
        }
        keysAroundTheTower()
    }

    private suspend fun Dialogue.beOff() {
        chatPlayer(neutral, "Err I'd better be off really.")
        chatNpc(
            happy,
            "Oh ok, have a good time, and watch out for sheep! They're more cunning than they " +
                "look.",
        )
    }

    private suspend fun Dialogue.keysAroundTheTower() {
        chatPlayer(quiz, "Well, have you got any keys knocking around?")
        chatNpc(
            happy,
            "Now you come to mention it, yes I do have a key. It's in my special closet of " +
                "valuable stuff. Now how do I get into that?",
        )
        chatNpc(
            confused,
            "I sealed it using one of my magic rituals. So it would make sense that another " +
                "ritual would open it again.",
        )
        chatPlayer(quiz, "So do you know what ritual to use?")
        chatNpc(neutral, "Let me think a second.")
        chatNpc(
            confused,
            "Yes a simple drazier style ritual should suffice. Hmm, main problem with that is " +
                "I'll need $BONES_REQUIRED sets of bones. Now where am I going to get hold of " +
                "something like that?",
        )
        player.traibornAsked = true
        if (
            menu(
                "Hmm, that's too bad. I really need that key." to true,
                "I'll get the bones for you." to false,
            )
        ) {
            chatPlayer(sad, "Hmm, that's too bad. I really need that key.")
            chatNpc(sad, "Ah well, sorry I couldn't be any more help.")
        } else {
            chatPlayer(happy, "I'll help get the bones for you.")
            chatNpc(happy, "Ooh that would be very good of you.")
            chatPlayer(neutral, "Okay, I'll speak to you when I've got some bones.")
        }
    }

    private suspend fun Dialogue.bones() {
        chatNpc(quiz, "How are you doing finding bones?")
        val carried = access.inv.count(BONES)
        if (carried < 1) {
            chatPlayer(sad, "I haven't got any at the moment.")
            chatNpc(neutral, "Nevermind, keep working on it.")
            return
        }
        chatPlayer(happy, "I have some bones.")
        chatNpc(happy, "Give 'em here then.")
        val handed = minOf(carried, BONES_REQUIRED - player.traibornBonesGiven)
        val result = handIn(handed)
        if (result == HandIn.Failed) {
            return
        }
        mesbox("You give Traiborn $handed ${if (handed == 1) "set" else "sets"} of bones.")
        if (result == HandIn.Partial) {
            chatPlayer(neutral, "That's all of them.")
            chatNpc(
                neutral,
                "I still need ${BONES_REQUIRED - player.traibornBonesGiven} more.",
            )
            chatPlayer(neutral, "Ok, I'll keep looking.")
            return
        }
        chatNpc(happy, "Hurrah! That's all $BONES_REQUIRED sets of bones.")
        chatNpc(
            happy,
            "Wings of dark and colour too, Spreading in the morning dew; Locked away I have a " +
                "key; Return it now, please, unto me.",
        )
        with(ritual) { perform(checkNotNull(npc)) }
        objbox(KEY_TRAIBORN, "Traiborn hands you a key.")
        chatPlayer(happy, "Thank you very much.")
        chatNpc(happy, "Not a problem for a friend of Sir What's-his-face.")
    }

    /**
     * Takes [count] bones and records them in one step, handing over the key in that same step
     * once the last set arrives.
     */
    private fun Dialogue.handIn(count: Int): HandIn {
        if (count <= 0) {
            return HandIn.Failed
        }
        val total = player.traibornBonesGiven + count
        val complete = total >= BONES_REQUIRED
        val moved =
            player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                delete {
                    from = inventory
                    obj = BONES.asRSCM(RSCMType.OBJ)
                    strictCount = count
                }
                if (complete) {
                    insert {
                        into = inventory
                        obj = KEY_TRAIBORN.asRSCM(RSCMType.OBJ)
                        strictCount = 1
                    }
                }
            }
        if (moved.failure) {
            return HandIn.Failed
        }
        player.traibornBonesGiven = total
        if (!complete) {
            return HandIn.Partial
        }
        player.traibornKeyGiven = true
        return HandIn.Complete
    }

    private suspend fun Dialogue.afterKey() {
        if (demonSlayer.holdsKey(access, KEY_TRAIBORN)) {
            chatNpc(
                confused,
                "Don't you have somewhere to be, young thingummywut? You still have that key " +
                    "you asked me for.",
            )
            chatPlayer(neutral, "You're right. I've got a demon to slay.")
            return
        }
        chatPlayer(sad, "I've lost the key you gave to me.")
        chatNpc(
            neutral,
            "Yes I know, it was returned to me. If you want it back you're going to have to " +
                "collect another $BONES_REQUIRED sets of bones.",
        )
        player.traibornKeyGiven = false
        player.traibornBonesGiven = 0
    }

    private suspend fun Dialogue.keyInUse() {
        chatNpc(
            confused,
            "Don't you have somewhere to be, young thingummywut? You still have that key you " +
                "asked me for.",
        )
        chatPlayer(neutral, "You're right. I've got a demon to slay.")
    }

    private enum class HandIn {
        Failed,
        Partial,
        Complete,
    }

    private enum class Excuse {
        LookingAfter,
        Knight,
        Keys,
    }

    private companion object {
        const val SPINACH_ROLL = "obj.spinach_roll"
    }
}
