package org.rsmod.content.quest.area.ardougne.tribaltotem

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The combination door to the stairs. Jagex's `rd_combolock` interface has no clientscript, so the
 * server drives the four letter wheels. Their positions persist in `totemquest_combodoor_code*`,
 * and the door is unlocked for as long as they spell [Code].
 */
class HandelmortDoor @Inject constructor(private val doors: QuestDoors) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(Door) {
            if (player.unlocked()) {
                doors.open(this, it.loc, OpenedDoor)
            } else {
                openLock()
            }
        }
        for (dial in Dials) {
            onIfModalButton(dial.left) { turn(dial, -1) }
            onIfModalButton(dial.right) { turn(dial, 1) }
        }
        onIfModalButton(Enter) { enter() }
    }

    private suspend fun ProtectedAccess.openLock() {
        arriveDelay()
        ifOpenMainModal(Interface)
        for (dial in Dials) {
            showDial(dial)
        }
    }

    private fun ProtectedAccess.turn(dial: Dial, step: Int) {
        val position = Math.floorMod(player.vars[dial.varbit] + step, Letters)
        vars[dial.varbit] = position
        showDial(dial)
    }

    private fun ProtectedAccess.showDial(dial: Dial) {
        ifSetText(dial.text, letter(player.vars[dial.varbit]).toString())
    }

    private fun ProtectedAccess.enter() {
        if (!player.unlocked()) {
            mes("This combination is incorrect.")
            return
        }
        mes("The combination seems correct!")
        ifCloseSub(Interface)
    }

    private fun Player.unlocked(): Boolean =
        Dials.withIndex().all { (index, dial) -> letter(vars[dial.varbit]) == Code[index] }

    private fun letter(position: Int): Char = 'A' + Math.floorMod(position, Letters)

    private class Dial(letter: String, index: Int) {
        val text = "component.rd_combolock:rd$letter"
        val left = "component.rd_combolock:rd${letter}_left"
        val right = "component.rd_combolock:rd${letter}_right"
        val varbit = "varbit.totemquest_combodoor_code$index"
    }

    companion object {
        const val Door = "loc.combodoor"
        const val OpenedDoor = "loc.poshdooropen"
        const val Interface = "interface.rd_combolock"
        const val Enter = "component.rd_combolock:rdenter"
        const val Code = "KURT"

        private const val Letters = 26
        private val Dials = listOf(Dial("a", 1), Dial("b", 2), Dial("c", 3), Dial("d", 4))
    }
}
