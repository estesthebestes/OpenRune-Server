package org.rsmod.content.interfaces.grandexchange

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The standalone collection box (`ge_collect`), reached from bankers, clerks and booths. */
internal class GeCollectScript
@Inject
constructor(
    private val sessions: GeSessions,
    private val collector: GeCollector,
    private val setup: GeSetup,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onIfOpen("interface.ge_collect") {
            logger.debug { "GE collection box opened: player=${player.username}" }
            player.prepareCollect()
        }
        for (slot in 0 until SlotCodec.SLOTS) {
            onIfModalButton("component.ge_collect:collect_$slot") {
                logger.debug { "GE collect button: slot=$slot comsub=${it.comsub} op=${it.op}" }
                collectSlot(slot, it.comsub, it.op)
            }
        }
        onIfModalButton("component.ge_collect:collect_inv") { collectAll(toBank = false) }
        onIfModalButton("component.ge_collect:collect_bank") { collectAll(toBank = true) }
    }

    private fun Player.prepareCollect() {
        sessions.transmitBoxes(this)
        setup.sendItemSinkDefaults(this)
        sessions.of(this).pushAllSlots()
        for (slot in 0 until SlotCodec.SLOTS) {
            ifSetEvents(
                "component.ge_collect:collect_$slot",
                0..CHILD_LAST,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op10,
            )
        }
    }

    private fun ProtectedAccess.collectSlot(slot: Int, child: Int, op: IfButtonOp) {
        when (child) {
            ITEM_CHILD -> collector.collectDisplayed(this, slot, isItem = true, op = op)
            COINS_CHILD -> collector.collectDisplayed(this, slot, isItem = false, op = op)
        }
    }

    private fun ProtectedAccess.collectAll(toBank: Boolean) {
        if (collector.collectAll(player, toBank)) {
            mes("You don't have enough inventory space to collect everything.")
        }
    }

    private companion object {
        const val ITEM_CHILD = 3
        const val COINS_CHILD = 4
        const val CHILD_LAST = 8
    }
}
