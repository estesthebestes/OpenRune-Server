package org.rsmod.content.skills.runecrafting.altar

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Kourend's blood and soul altars. Crafting at one marks it as crafted, which the Abyss's blood and
 * soul rifts need before dark essence can redirect them there; the redirect itself is the cache's
 * `zeah_*_altar_unlocked` varbit.
 */
enum class KourendAltar(
    val altarLoc: String,
    private val craftedVarbit: String,
    private val riftVarbit: String,
) {
    Blood(
        "loc.archeus_altar_blood",
        "varbit.rc_kourend_blood_crafted",
        "varbit.zeah_blood_altar_unlocked",
    ),
    Soul(
        "loc.archeus_altar_soul",
        "varbit.rc_kourend_soul_crafted",
        "varbit.zeah_soul_altar_unlocked",
    ),
    ;

    fun hasCrafted(player: Player): Boolean = player.vars[craftedVarbit] != 0

    fun markCrafted(player: Player) = VarPlayerIntMapSetter.set(player, craftedVarbit, 1)

    fun riftRedirected(player: Player): Boolean = player.vars[riftVarbit] != 0

    fun redirectRift(player: Player) = VarPlayerIntMapSetter.set(player, riftVarbit, 1)

    companion object {
        fun byAltar(loc: String): KourendAltar? = entries.firstOrNull { it.altarLoc == loc }
    }
}
