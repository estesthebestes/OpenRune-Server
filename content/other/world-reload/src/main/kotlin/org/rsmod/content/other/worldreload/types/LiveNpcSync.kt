package org.rsmod.content.other.worldreload.types

import dev.openrune.types.NpcServerType
import org.rsmod.api.npc.isInCombat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.map.Direction

/** The values a live npc copied from its type before the type was edited in place. */
internal data class NpcTypeSnapshot(
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val hitpoints: Int,
    val ranged: Int,
    val magic: Int,
    val timer: Int,
    val respawnDir: Direction,
) {
    fun statsEqual(type: NpcServerType): Boolean =
        attack == type.attack &&
            strength == type.strength &&
            defence == type.defence &&
            hitpoints == type.hitpoints &&
            ranged == type.ranged &&
            magic == type.magic

    companion object {
        fun of(type: NpcServerType): NpcTypeSnapshot =
            NpcTypeSnapshot(
                type.attack,
                type.strength,
                type.defence,
                type.hitpoints,
                type.ranged,
                type.magic,
                type.timer,
                type.respawnDir,
            )
    }
}

/**
 * Pushes edited type values onto npcs already in the world. Npcs that are fighting or damaged
 * keep their current stats and pick the new ones up on their next respawn.
 */
internal object LiveNpcSync {
    data class Result(val refreshed: Int, val onRespawn: Int)

    fun apply(npcList: NpcList, before: Map<Int, NpcTypeSnapshot>): Result {
        var refreshed = 0
        var onRespawn = 0
        for (npc in npcList) {
            val old = before[npc.type.id] ?: continue
            val type = npc.type
            if (!old.statsEqual(type)) {
                if (npc.canRefreshStats(old)) {
                    npc.copyStats(type)
                    refreshed++
                } else {
                    onRespawn++
                }
            }
            if (npc.respawnDir == old.respawnDir) {
                npc.respawnDir = type.respawnDir
            }
            if (npc.aiTimerStart == old.timer && type.timer > 0) {
                npc.aiTimerStart = type.timer
            }
        }
        return Result(refreshed, onRespawn)
    }

    private fun Npc.canRefreshStats(old: NpcTypeSnapshot): Boolean =
        isSlotAssigned &&
            !isInCombat() &&
            baseAttackLvl == old.attack &&
            attackLvl == old.attack &&
            baseStrengthLvl == old.strength &&
            strengthLvl == old.strength &&
            baseDefenceLvl == old.defence &&
            defenceLvl == old.defence &&
            baseHitpointsLvl == old.hitpoints &&
            hitpoints == old.hitpoints &&
            baseRangedLvl == old.ranged &&
            rangedLvl == old.ranged &&
            baseMagicLvl == old.magic &&
            magicLvl == old.magic
}
