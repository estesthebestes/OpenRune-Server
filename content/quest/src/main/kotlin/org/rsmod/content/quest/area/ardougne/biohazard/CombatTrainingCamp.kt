package org.rsmod.content.quest.area.ardougne.biohazard

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * King Lathas's Combat Training Camp north-west of Ardougne, opened to the player by finishing
 * Biohazard: the guarded gate, the practice dummies that pay Attack experience for the first six
 * hits ever, the loose railing into the ogre pen and the guards.
 */
class CombatTrainingCamp
@Inject
constructor(private val biohazard: BiohazardQuest, private val doors: QuestDoors) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GATE_LEFT) { gate(it.loc, left = true) }
        onOpLoc1(GATE_RIGHT) { gate(it.loc, left = false) }
        onOpLoc1(DUMMY) { hitDummy(it.loc) }
        onOpLoc1(LOOSE_RAILING) { squeezeRailing(it.loc) }
        onOpNpc1(MACE_GUARD) {
            startDialogue(it.npc) {
                chatPlayer(happy, "Hello there.")
                chatNpc(angry, "What do you want? Leave us be!")
            }
        }
        onOpNpc1(OGRE_GUARD) {
            startDialogue(it.npc) {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    neutral,
                    "Well hello brave warrior. These ogres have been terrorising the area, " +
                        "they've eaten four children this week alone.",
                )
                chatPlayer(angry, "Brutes!")
                chatNpc(
                    neutral,
                    "So we decided to use them for target practice. A fair punishment.",
                )
                chatPlayer(neutral, "Indeed.")
            }
        }
        onOpNpc1(CROSSBOW_GUARD) {
            startDialogue(it.npc) {
                chatPlayer(happy, "Hello.")
                chatNpc(neutral, "Hello soldier.")
                chatPlayer(neutral, "I'm more of an adventurer really.")
                chatNpc(
                    angry,
                    "In this day and age we're all soldiers. No time to waste gassing, Fight! " +
                        "Fight! Fight!",
                )
            }
        }
    }

    private suspend fun ProtectedAccess.gate(gate: BoundLocInfo, left: Boolean) {
        arriveDelay()
        faceLoc(gate)
        val inside = player.coords.z > gate.coords.z
        if (!inside && !biohazard.quest.isQuestCompleted(player)) {
            startDialogue {
                chatNpcSpecific(
                    "Guard",
                    MACE_GUARD,
                    angry,
                    "This is a restricted area. You can only enter under the authority of King " +
                        "Lathas.",
                )
            }
            mes("The gates are locked.")
            return
        }
        val leftGate = if (left) doors.asInfo(gate) else doors.leftOfGate(gate, GATE_LEFT)
        val rightGate = if (left) doors.rightOfGate(gate, GATE_RIGHT) else doors.asInfo(gate)
        doors.openGate(this, leftGate, GATE_LEFT_OPEN, rightGate, GATE_RIGHT_OPEN)
    }

    private suspend fun ProtectedAccess.hitDummy(dummy: BoundLocInfo) {
        arriveDelay()
        faceLoc(dummy)
        val weapon = player.righthand?.let { getInvObj(it) }
        val attackAnim = weapon?.paramOrNull(params.attack_anim_stance1)
        val attackSound = weapon?.paramOrNull(params.attack_sound_stance1)
        if (attackAnim != null) {
            anim(RSCM.getReverseMapping(RSCMType.SEQ, attackAnim.id))
        } else {
            anim(PUNCH_SEQ)
        }
        if (attackSound != null) {
            soundSynth(attackSound.id)
        } else {
            soundSynth(PUNCH_SOUND)
        }
        delay(1)
        val hits = player.dummyHits
        if (hits >= MAX_REWARDED_HITS) {
            mes("There is nothing more you can learn from hitting a dummy.")
            return
        }
        player.dummyHits = hits + 1
        statAdvance("stat.attack", DUMMY_XP)
        mes("You hit the dummy.")
    }

    private suspend fun ProtectedAccess.squeezeRailing(railing: BoundLocInfo) {
        arriveDelay()
        faceLoc(railing)
        val dest =
            if (player.coords.x > railing.coords.x) railing.coords
            else railing.coords.translateX(1)
        anim(SQUEEZE_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(1)
        teleport(dest)
        mes("You squeeze through the loose railing.")
    }

    private companion object {
        const val GATE_LEFT = "loc.lathastraining_gatel"
        const val GATE_RIGHT = "loc.lathastraining_gater"
        const val GATE_LEFT_OPEN = "loc.lathastraining_gatelopen"
        const val GATE_RIGHT_OPEN = "loc.lathastraining_gateropen"
        const val DUMMY = "loc.biohazarddummy"
        const val LOOSE_RAILING = "loc.biohazardlooserailing"

        const val MACE_GUARD = "npc.lathastrainer1"
        const val OGRE_GUARD = "npc.lathastrainer2"
        const val CROSSBOW_GUARD = "npc.lathastrainer3"

        const val MAX_REWARDED_HITS = 6
        const val DUMMY_XP = 50.0

        const val PUNCH_SEQ = "seq.human_unarmedpunch"
        const val SQUEEZE_SEQ = "seq.human_walk_fence_north"
        const val PUNCH_SOUND = "synth.human_unarmedpunch"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
    }
}
