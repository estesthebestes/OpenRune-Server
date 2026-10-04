package org.rsmod.content.other.worldreload.spawns

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc

/** Npcs removed from the spawn files while fighting; they leave once combat ends. */
@Singleton
class DeferredNpcRemovals
@Inject
constructor(private val npcRepo: NpcRepository, private val mapClock: MapClock) {
    private val pending = mutableListOf<Pending>()

    val size: Int
        get() = pending.size

    fun add(npc: Npc) {
        pending += Pending(npc, deadline = mapClock.cycle + FORCE_REMOVE_CYCLES)
    }

    fun process() {
        if (pending.isEmpty()) {
            return
        }
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val (npc, deadline) = iterator.next()
            if (!npc.isSlotAssigned) {
                iterator.remove()
                continue
            }
            if (!npc.isInCombat() || mapClock.cycle >= deadline) {
                npcRepo.del(npc, Int.MAX_VALUE)
                iterator.remove()
            }
        }
    }

    private data class Pending(val npc: Npc, val deadline: Int)

    private companion object {
        private const val FORCE_REMOVE_CYCLES = 500
    }
}
