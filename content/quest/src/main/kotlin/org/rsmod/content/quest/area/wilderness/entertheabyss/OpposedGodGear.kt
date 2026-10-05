package org.rsmod.content.quest.area.wilderness.entertheabyss

import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

internal enum class OpposedGod(val keyword: String) {
    Saradomin("saradomin"),
    Guthix("guthix"),
}

/** The god whose equipment the player wears, which the Mage of Zamorak refuses to talk past. */
internal fun Player.wornOpposedGod(): OpposedGod? {
    val names = worn.filterNotNull { true }.map { getInvObj(it).internalName }
    return OpposedGod.entries.firstOrNull { god -> names.any { god.keyword in it } }
}
