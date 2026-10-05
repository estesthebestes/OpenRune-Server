package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.BIRD_FEED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_CROSSED_WALL
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_DISTRACTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_MET_OMART
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_STARTED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Jerico's house south of the northern bank: Jerico himself, who keeps messenger pigeons, and
 * the cupboard full of bird feed. The pigeon cages themselves are ground spawns behind the
 * house.
 */
class JericosHouse
@Inject
constructor(private val biohazard: BiohazardQuest, private val locRepo: LocRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(JERICO) { startDialogue(it.npc) { jerico() } }
        onOpLoc1(CUPBOARD_SHUT) { openCupboard(it.loc) }
        onOpLoc1(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc2(CUPBOARD_OPEN) { closeCupboard(it.loc) }
    }

    private suspend fun Dialogue.jerico() {
        when (biohazard.stage(player)) {
            STAGE_STARTED -> {
                if (player.metJerico) {
                    chatNpc(quiz, "Hello again. Have you spoken to Omart yet?")
                    chatPlayer(neutral, "Not yet.")
                    chatNpc(
                        neutral,
                        "You'll find him at the southern end of the wall. Keep an eye out for " +
                            "them mourners.",
                    )
                    return
                }
                player.metJerico = true
                chatPlayer(happy, "Hello Jerico.")
                chatNpc(
                    happy,
                    "Hello, I've been expecting you. Elena tells me you need to cross the wall.",
                )
                chatPlayer(neutral, "That's right.")
                chatNpc(
                    neutral,
                    "My messenger pigeons help me communicate with friends over the wall.",
                )
                chatNpc(
                    neutral,
                    "I have arranged for two of them to aid you with a rope ladder. Omart is " +
                        "waiting for you at the southern end of the wall.",
                )
                chatNpc(
                    worried,
                    "But be careful, if the mourners catch you, the punishment will be severe.",
                )
                chatPlayer(happy, "Thanks Jerico.")
            }
            STAGE_MET_OMART -> {
                chatPlayer(happy, "Hello Jerico.")
                chatNpc(quiz, "Hello again. Have you spoken to Omart yet?")
                chatPlayer(neutral, "I have. He told me to ask you about some pigeons.")
                chatNpc(quiz, "Pigeons? What do you need my pigeons for?")
                chatPlayer(neutral, "Omart thought they'd make a good distraction for the guards.")
                chatNpc(
                    happy,
                    "Ahh, very clever. Well I'm sure I can let you borrow some, as long as you " +
                        "look after them. Just grab one of the cages from behind my house. " +
                        "You'll want some bird feed as well. I should have some in my cupboard.",
                )
            }
            STAGE_DISTRACTED -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(
                    worried,
                    "The guards are distracted by the birds, you must go now, quickly " +
                        "traveller.",
                )
            }
            in STAGE_CROSSED_WALL until STAGE_GOT_SAMPLES -> {
                chatPlayer(happy, "Hello again Jerico.")
                chatNpc(quiz, "So you've returned traveller. Did you get what you wanted?")
                chatPlayer(neutral, "Not yet.")
                chatNpc(
                    neutral,
                    "Omart will be waiting by the wall, in case you need to cross again.",
                )
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "How's it going?")
                chatPlayer(happy, "Good thanks. Just passing by.")
            }
        }
    }

    private suspend fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_SEQ)
        soundSynth(CUPBOARD_OPEN_SOUND)
        locRepo.change(cupboard, CUPBOARD_OPEN, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.closeCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(CLOSE_SEQ)
        soundSynth(CUPBOARD_CLOSE_SOUND)
        locRepo.change(cupboard, CUPBOARD_SHUT, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        objbox(BIRD_FEED, "The cupboard is full of bird feed.")
        when {
            inv.isFull() ->
                objbox(
                    BIRD_FEED,
                    "You find some bird feed in the cupboard but you don't have enough room to " +
                        "take it.",
                )
            inv.contains(BIRD_FEED) ->
                startDialogue { chatPlayer(neutral, "I don't need any more bird feed.") }
            else -> {
                invAdd(inv, BIRD_FEED)
                startDialogue { chatPlayer(quiz, "Mmm, bird feed! Now what could I do with that?") }
            }
        }
    }

    private companion object {
        const val JERICO = "npc.jerico"
        const val CUPBOARD_SHUT = "loc.jericoscupboardshut"
        const val CUPBOARD_OPEN = "loc.jericoscupboardopen"

        const val OPEN_SEQ = "seq.human_opencupboard"
        const val CLOSE_SEQ = "seq.human_closecupboard"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
        const val CUPBOARD_TICKS = 100
    }
}
