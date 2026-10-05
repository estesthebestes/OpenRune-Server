package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.MEDICAL_GOWN
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_STEW_POISONED
import org.rsmod.content.quest.area.ardougne.wearingMedicalGown
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nurse Sarah's clinic south-west of the West Ardougne chapel, and the cupboard holding the
 * medical gown that gets a "doctor" into the Mourner Headquarters.
 */
class NurseSarah
@Inject
constructor(private val biohazard: BiohazardQuest, private val locRepo: LocRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(NURSE) { startDialogue(it.npc) { nurse() } }
        onOpLoc1(CUPBOARD_SHUT) { openCupboard(it.loc) }
        onOpLoc1(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc2(CUPBOARD_OPEN) { closeCupboard(it.loc) }
    }

    private suspend fun Dialogue.nurse() {
        if (biohazard.quest.isQuestInProgress(player)) {
            chatPlayer(happy, "Hello nurse.")
            chatNpc(sad, "I don't know how much longer I can cope here.")
            chatPlayer(quiz, "What? Is the plague getting to you?")
            chatNpc(
                neutral,
                "No, strangely enough the people here don't seem to be affected. It's just the " +
                    "awful living conditions that is making people ill.",
            )
            chatPlayer(confused, "I was under the impression that everyone here was affected.")
            chatNpc(neutral, "Me too, but that doesn't seem to be the case.")
            return
        }
        chatPlayer(happy, "Hello there.")
        chatNpc(happy, "Hello my dear, how are you feeling?")
        chatPlayer(happy, "I'm okay thanks.")
        chatNpc(neutral, "Well in that case I'd better get back to work. Take care.")
        chatPlayer(happy, "You too.")
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
        val hasGown = inv.contains(MEDICAL_GOWN) || player.wearingMedicalGown()
        if (biohazard.stage(player) < STAGE_STEW_POISONED || hasGown) {
            mes("You search the cupboard but you find nothing of interest.")
            return
        }
        if (inv.isFull()) {
            objbox(
                MEDICAL_GOWN,
                "You find a medical gown in the cupboard but you don't have enough room to take " +
                    "it.",
            )
            return
        }
        invAdd(inv, MEDICAL_GOWN)
        objbox(MEDICAL_GOWN, "You find a medical gown in the cupboard.")
    }

    private companion object {
        const val NURSE = "npc.bionurse"
        const val CUPBOARD_SHUT = "loc.bionursescupboardshut"
        const val CUPBOARD_OPEN = "loc.bionursescupboardopen"

        const val OPEN_SEQ = "seq.human_opencupboard"
        const val CLOSE_SEQ = "seq.human_closecupboard"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
        const val CUPBOARD_TICKS = 100
    }
}
