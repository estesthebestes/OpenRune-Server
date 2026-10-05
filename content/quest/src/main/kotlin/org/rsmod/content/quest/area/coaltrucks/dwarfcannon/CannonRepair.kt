package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifSetAnim
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.LAWGOF
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.SAFETY_ON
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.SPRING_SET
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_CANNON_FIXED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_REPAIR_CANNON
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.TOOLKIT
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.TOOL_HOOK
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.TOOL_PLIERS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.TOOL_TOOTHED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.TOOL_VARBITS
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Black Guard's sabotaged multicannon and the toolkit interface used to mend it.
 *
 * Interface 409 is an old if1 layout: three tool buttons whose models highlight from
 * `varbit.mcannonmulti_tool1..3`, three invisible hot spots over the safety switch, spring and
 * gear, and the firing mechanism model the server animates. The pliers go on the safety switch and
 * the hooked tool on the spring, in either order; the toothed tool on the gear then tests the
 * mechanism, which only runs true once both are done.
 *
 * Scripts cannot delay while the interface is open, so each transition animation is followed by a
 * world-queued switch back to the mechanism's resting animation.
 */
class CannonRepair
@Inject
constructor(
    private val dwarfCannon: DwarfCannonQuest,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val eventBus: EventBus,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CANNON) { inspectOrFire() }
        onOpLoc2(CANNON) { pickUp() }
        onOpLocU(CANNON, TOOLKIT) { useToolkit() }
        onIfModalButton(TOOTHED_BUTTON) { selectTool(TOOL_TOOTHED) }
        onIfModalButton(PLIERS_BUTTON) { selectTool(TOOL_PLIERS) }
        onIfModalButton(HOOK_BUTTON) { selectTool(TOOL_HOOK) }
        onIfModalButton(SAFETY_SPOT) { fixSafety() }
        onIfModalButton(SPRING_SPOT) { fixSpring() }
        onIfModalButton(GEAR_SPOT) { testGear() }
        onIfClose(INTERFACE) { clearTools(player) }
    }

    private suspend fun ProtectedAccess.inspectOrFire() {
        arriveDelay()
        val stage = dwarfCannon.stage(player)
        when {
            stage >= STAGE_CANNON_FIXED ->
                startDialogue { chatPlayer(happy, "This should work nicely now that I've fixed it.") }
            stage == STAGE_REPAIR_CANNON && TOOLKIT in inv ->
                startDialogue { chatPlayer(neutral, "I guess I'd better fix it with the toolkit I was given.") }
            else -> mesbox("The Black Guard sent Lawgof this cannon to help defend the mines against the goblins.")
        }
    }

    private suspend fun ProtectedAccess.pickUp() {
        arriveDelay()
        startDialogue {
            chatNpcSpecific(
                "Captain Lawgof",
                LAWGOF,
                angry,
                "What do you think you're doing, trooper! Did I give you orders to move that cannon? " +
                    "No! Well then don't!",
            )
        }
    }

    private suspend fun ProtectedAccess.useToolkit() {
        arriveDelay()
        when (dwarfCannon.stage(player)) {
            STAGE_REPAIR_CANNON -> openRepair()
            in STAGE_CANNON_FIXED..Int.MAX_VALUE -> mes("The cannon is already working.")
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.openRepair() {
        clearTools(player)
        ifOpenMainModal(INTERFACE)
        for (button in BUTTONS) {
            ifSetEvents(button, 0..0, IfEvent.Op1)
        }
        showMechanism(player, restingAnim(player))
    }

    private fun ProtectedAccess.selectTool(tool: String) {
        if (dwarfCannon.stage(player) != STAGE_REPAIR_CANNON) {
            return
        }
        for (varbit in TOOL_VARBITS) {
            VarPlayerIntMapSetter.set(player, varbit, if (varbit == tool) 1 else 0)
        }
        soundSynth(SELECT_SOUND)
    }

    private fun ProtectedAccess.selectedTool(): String? = TOOL_VARBITS.firstOrNull { vars[it] != 0 }

    private fun ProtectedAccess.fixSafety() {
        val tool = selectedTool() ?: return noTool()
        if (tool != TOOL_PLIERS) {
            return wrongTool()
        }
        clearTools(player)
        if (vars[SAFETY_ON] != 0) {
            mes("The safety switch is already fixed.")
            return
        }
        VarPlayerIntMapSetter.set(player, SAFETY_ON, 1)
        val spring = vars[SPRING_SET] != 0
        soundSynth(SAFETY_SOUND)
        playThenRest(if (spring) SAFETY_ON_WITH_SPRING_ANIM else SAFETY_ON_ANIM, TRANSITION_TICKS)
        mes("You use the pliers to fix the safety switch.")
    }

    private fun ProtectedAccess.fixSpring() {
        val tool = selectedTool() ?: return noTool()
        if (tool != TOOL_HOOK) {
            return wrongTool()
        }
        clearTools(player)
        if (vars[SPRING_SET] != 0) {
            mes("The spring is already in place.")
            return
        }
        VarPlayerIntMapSetter.set(player, SPRING_SET, 1)
        val safety = vars[SAFETY_ON] != 0
        soundSynth(SPRING_SOUND)
        playThenRest(if (safety) ATTACH_SPRING_ANIM else SAFETY_OFF_ATTACH_SPRING_ANIM, TRANSITION_TICKS)
        mes("You use the hooked tool to put the spring back in place.")
    }

    private fun ProtectedAccess.testGear() {
        val tool = selectedTool() ?: return noTool()
        if (tool != TOOL_TOOTHED) {
            return wrongTool()
        }
        clearTools(player)
        soundSynth(GEAR_SOUND)
        if (vars[SAFETY_ON] == 0 || vars[SPRING_SET] == 0) {
            playThenRest(WORK_WITHOUT_SPRING_ANIM, WORK_TICKS)
            mes("The gear turns, but the firing mechanism still isn't working properly.")
            return
        }
        dwarfCannon.advanceTo(this, STAGE_CANNON_FIXED)
        showMechanism(player, WORK_WITH_SPRING_ANIM)
        val uid = player.uid
        worldQueues.add(WORK_TICKS) {
            val repairer = uid.resolve(playerList) ?: return@add
            repairer.ifClose(eventBus)
            repairer.soundSynth(FIXED_SOUND)
            repairer.mes("Well done! You've fixed the cannon! Better go and tell Captain Lawgof.")
        }
    }

    private fun ProtectedAccess.noTool() {
        mes("You need to choose a tool to use first.")
    }

    private fun ProtectedAccess.wrongTool() {
        clearTools(player)
        mes("That tool doesn't fit this part.")
    }

    private fun ProtectedAccess.playThenRest(seq: String, ticks: Int) {
        showMechanism(player, seq)
        val uid = player.uid
        worldQueues.add(ticks) {
            val repairer = uid.resolve(playerList) ?: return@add
            if (dwarfCannon.stage(repairer) == STAGE_REPAIR_CANNON) {
                showMechanism(repairer, restingAnim(repairer))
            }
        }
    }

    private fun restingAnim(player: Player): String =
        if (player.vars[SPRING_SET] != 0) IDLE_WITH_SPRING_ANIM else IDLE_ANIM

    private fun showMechanism(player: Player, seq: String) {
        player.ifSetAnim(MECHANISM, ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)))
    }

    private fun clearTools(player: Player) {
        for (varbit in TOOL_VARBITS) {
            if (player.vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(player, varbit, 0)
            }
        }
    }

    private companion object {
        const val CANNON = "loc.mcannon_cannon_multiloc"
        const val INTERFACE = "interface.mcannon_interface"

        const val TOOTHED_BUTTON = "component.mcannon_interface:mcannon_tool1"
        const val PLIERS_BUTTON = "component.mcannon_interface:mcannon_tool2"
        const val HOOK_BUTTON = "component.mcannon_interface:mcannon_tool3"
        const val SAFETY_SPOT = "component.mcannon_interface:mcannon_safety"
        const val SPRING_SPOT = "component.mcannon_interface:mcannon_spring"
        const val GEAR_SPOT = "component.mcannon_interface:mcannon_gear"
        const val MECHANISM = "component.mcannon_interface:mcannon_firing_mechanism"

        val BUTTONS =
            listOf(TOOTHED_BUTTON, PLIERS_BUTTON, HOOK_BUTTON, SAFETY_SPOT, SPRING_SPOT, GEAR_SPOT)

        const val IDLE_ANIM = "seq.mcannon_interface_gun_idle"
        const val IDLE_WITH_SPRING_ANIM = "seq.mcannon_interface_gun_idle_with_spring"
        const val SAFETY_ON_ANIM = "seq.mcannon_interface_gun_safety_on"
        const val SAFETY_ON_WITH_SPRING_ANIM = "seq.mcannon_interface_gun_safety_on_with_spring"
        const val ATTACH_SPRING_ANIM = "seq.mcannon_interface_gun_attach_spring"
        const val SAFETY_OFF_ATTACH_SPRING_ANIM = "seq.mcannon_interface_safety_off_attaching_spring"
        const val WORK_WITHOUT_SPRING_ANIM = "seq.mcannon_interface_gun_work_without_spring"
        const val WORK_WITH_SPRING_ANIM = "seq.mcannon_interface_gun_work_with_spring"

        const val SELECT_SOUND = "synth.rogue_gear_select"
        const val SAFETY_SOUND = "synth.unlock"
        const val SPRING_SOUND = "synth.mousetrap_spring"
        const val GEAR_SOUND = "synth.rogue_placegear"
        const val FIXED_SOUND = "synth.mcannon_turn"

        /** The switch and spring animations run for about a second. */
        const val TRANSITION_TICKS = 2

        /** One full turn of the test-fire animation. */
        const val WORK_TICKS = 5
    }
}
