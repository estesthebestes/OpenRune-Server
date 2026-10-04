package org.rsmod.api.repo.map

import jakarta.inject.Singleton
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

public data class NpcSpawnKey(val type: Int, val coords: CoordGrid)

public data class ObjSpawnKey(val type: Int, val count: Int, val coords: CoordGrid)

/**
 * Remembers which npcs and ground objs came from the map spawn files, so `::reload spawns` can
 * diff the files against what is in the world without touching script-spawned entities.
 * Recording happens on the boot map-decoding threads, so writes are synchronized; everything
 * after boot runs on the game thread.
 */
@Singleton
public class MapSpawnRegistry {
    private val npcs = HashMap<NpcSpawnKey, MutableList<Npc>>()
    private val objs = HashMap<ObjSpawnKey, Int>()

    /** Map square ids the server decoded at boot; spawns elsewhere are never placed. */
    @Volatile
    public var loadedSquares: Set<Int> = emptySet()

    @Synchronized
    public fun recordNpc(key: NpcSpawnKey, npc: Npc) {
        npcs.getOrPut(key) { mutableListOf() } += npc
    }

    @Synchronized
    public fun recordObj(key: ObjSpawnKey) {
        objs.merge(key, 1, Int::plus)
    }

    @Synchronized
    public fun npcCounts(): Map<NpcSpawnKey, Int> = npcs.mapValues { it.value.size }

    @Synchronized
    public fun objCounts(): Map<ObjSpawnKey, Int> = HashMap(objs)

    @Synchronized
    public fun npcs(key: NpcSpawnKey): List<Npc> = npcs[key]?.toList().orEmpty()

    @Synchronized
    public fun removeNpc(key: NpcSpawnKey, npc: Npc) {
        val list = npcs[key] ?: return
        list.remove(npc)
        if (list.isEmpty()) {
            npcs.remove(key)
        }
    }

    @Synchronized
    public fun adjustObj(key: ObjSpawnKey, delta: Int) {
        val next = (objs[key] ?: 0) + delta
        if (next <= 0) objs.remove(key) else objs[key] = next
    }
}
