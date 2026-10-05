package org.rsmod.content.quest.area.entrana

import dev.openrune.ServerCacheManager
import dev.openrune.util.Wearpos
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/** Saradomin's edict for Entrana, shared by the monks' ferry and the Abyss's law rift. */
public object EntranaRules {
    private val allowedSlots = setOf(Wearpos.Front.slot, Wearpos.Ring.slot)

    private val combatBonuses =
        listOf(
            params.attack_stab,
            params.attack_slash,
            params.attack_crush,
            params.attack_magic,
            params.attack_ranged,
            params.defence_stab,
            params.defence_slash,
            params.defence_crush,
            params.defence_magic,
            params.defence_ranged,
            params.melee_strength,
            params.ranged_strength,
            params.magic_damage,
        )

    public fun isForbidden(obj: InvObj): Boolean {
        val type = ServerCacheManager.getItem(obj.id) ?: return false
        if (type.wearpos1 == -1 || type.wearpos1 in allowedSlots) {
            return false
        }
        return combatBonuses.any { (type.paramOrNull(it) ?: 0) != 0 }
    }

    public fun carriesForbidden(player: Player): Boolean =
        player.inv.any { it != null && isForbidden(it) } ||
            player.worn.any { it != null && isForbidden(it) }
}
