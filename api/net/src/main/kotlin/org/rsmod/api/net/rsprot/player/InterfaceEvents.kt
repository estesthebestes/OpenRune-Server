package org.rsmod.api.net.rsprot.player

import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.definition.type.widget.IfEvent
import org.rsmod.game.ui.UserInterfaceMap

internal object InterfaceEvents {
    fun isEnabled(
        ui: UserInterfaceMap,
        component: ComponentType,
        comsub: Int,
        event: IfEvent,
    ): Boolean {
        val verifyStaticEvents = comsub == -1
        return if (verifyStaticEvents) {
            // Static components can also be enabled at runtime with if_setevents(-1, -1, ...),
            // which ComponentEventMap stores as a range starting at slot 0.
            component.hasEvent(event) || ui.hasEvent(component, 0, event)
        } else {
            ui.hasEvent(component, comsub, event)
        }
    }
}
