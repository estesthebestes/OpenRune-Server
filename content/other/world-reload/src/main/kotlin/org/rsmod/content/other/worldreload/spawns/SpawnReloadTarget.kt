package org.rsmod.content.other.worldreload.spawns

import dev.openrune.ServerCacheManager
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.GameMapSpawnSink
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.obj.MapObjDefinition
import dev.openrune.map.packing.MapNpcPacker
import dev.openrune.map.packing.MapObjPacker
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Path
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadException
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSources
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.hotreload.noChanges
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.map.MapSpawnRegistry
import org.rsmod.api.repo.map.NpcSpawnKey
import org.rsmod.api.repo.map.ObjSpawnKey
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.obj.ObjScope
import org.rsmod.map.CoordGrid

/**
 * Re-reads the map npc/obj spawn TOMLs and applies only the difference against what the files
 * spawned before, so npcs that were not touched keep their state. Script-spawned entities are
 * never affected because only entities recorded in [MapSpawnRegistry] are considered.
 */
@Singleton
class SpawnReloadTarget
@Inject
constructor(
    private val spawns: MapSpawnRegistry,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val npcList: NpcList,
    private val mapClock: MapClock,
    private val deferred: DeferredNpcRemovals,
    private val config: ServerConfig,
) : Reloadable {
    override val id: String = "spawns"
    override val description: String = "Map npc and ground item spawns"
    override val order: Int = 50

    private val npcDir: Path?
        get() = ReloadSources.resolve(config.hotReload.paths["spawns-npcs"], NPC_DIR)

    private val objDir: Path?
        get() = ReloadSources.resolve(config.hotReload.paths["spawns-objs"], OBJ_DIR)

    override fun watchPaths(): List<Path> = listOfNotNull(npcDir, objDir)

    override fun prepare(context: ReloadContext): ReloadPlan {
        val npcFolder = npcDir
        val objFolder = objDir
        if (npcFolder == null && objFolder == null) {
            return noChanges("spawn files not found ($NPC_DIR)")
        }
        val desired = DesiredSpawns()
        val loaded = spawns.loadedSquares
        try {
            npcFolder?.let { dir ->
                for ((square, list) in MapNpcPacker.collect(dir)) {
                    if (square.id in loaded) GameMapDecoder.putNpcs(square, list, desired)
                }
            }
            objFolder?.let { dir ->
                for ((square, list) in MapObjPacker.collect(dir)) {
                    if (square.id in loaded) GameMapDecoder.putObjs(square, list, desired)
                }
            }
        } catch (e: Exception) {
            throw ReloadException("Could not read spawn files: ${e.message}", e)
        }
        val errors = desired.validate()
        if (errors.isNotEmpty()) {
            val shown = errors.take(MAX_ERRORS)
            throw ReloadException("Rejected spawn files:\n" + shown.joinToString("\n"))
        }
        return ReloadPlan {
            apply(
                npcFolder != null,
                objFolder != null,
                desired.npcs,
                desired.objs,
            )
        }
    }

    private fun apply(
        npcsLoaded: Boolean,
        objsLoaded: Boolean,
        desiredNpcs: Map<NpcSpawnKey, Int>,
        desiredObjs: Map<ObjSpawnKey, Int>,
    ): ReloadSummary {
        val parts = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (npcsLoaded) {
            val diff = SpawnDiff.of(spawns.npcCounts(), desiredNpcs)
            val result = applyNpcs(diff)
            parts += "npcs +${result.added} -${result.removed}"
            if (result.deferred > 0) {
                warnings += "${result.deferred} npcs in combat will leave when their fight ends"
            }
            if (result.noSlot > 0) {
                warnings += "${result.noSlot} npcs not spawned: the npc list is full"
            }
        }
        if (objsLoaded) {
            val diff = SpawnDiff.of(spawns.objCounts(), desiredObjs)
            val (added, removed) = applyObjs(diff)
            parts += "ground items +$added -$removed"
        }
        return ReloadSummary(parts.joinToString(", "), warnings)
    }

    private fun applyNpcs(diff: SpawnDiff<NpcSpawnKey>): NpcResult {
        var removed = 0
        var deferredCount = 0
        for ((key, count) in diff.removed) {
            val candidates = spawns.npcs(key).sortedBy(::removalPriority).take(count)
            for (npc in candidates) {
                spawns.removeNpc(key, npc)
                removed++
                when {
                    !npc.isSlotAssigned -> Unit
                    npc.isInCombat() -> {
                        deferred.add(npc)
                        deferredCount++
                    }
                    else -> npcRepo.del(npc, Int.MAX_VALUE)
                }
            }
        }
        var added = 0
        var noSlot = 0
        for ((key, count) in diff.added) {
            val type = ServerCacheManager.getNpc(key.type) ?: continue
            repeat(count) {
                if (npcList.nextFreeSlot() == null) {
                    noSlot++
                    return@repeat
                }
                val npc = Npc(type, key.coords)
                npcRepo.add(npc, Int.MAX_VALUE)
                spawns.recordNpc(key, npc)
                added++
            }
        }
        return NpcResult(added, removed, deferredCount, noSlot)
    }

    private fun removalPriority(npc: Npc): Int =
        when {
            !npc.isSlotAssigned -> 0
            !npc.isInCombat() -> 1
            else -> 2
        }

    private fun applyObjs(diff: SpawnDiff<ObjSpawnKey>): Pair<Int, Int> {
        var removed = 0
        for ((key, count) in diff.removed) {
            var remaining = count
            val present =
                objRepo
                    .findAll(key.coords)
                    .filter { it.type == key.type && it.scope == ObjScope.Perm && it.isPublic }
                    .toList()
            for (obj in present) {
                if (remaining == 0) break
                if (objRepo.del(obj, Int.MAX_VALUE)) remaining--
            }
            if (remaining > 0) {
                remaining -= objRepo.cancelRespawns(key.coords, key.type, remaining)
            }
            spawns.adjustObj(key, -count)
            removed += count
        }
        var added = 0
        for ((key, count) in diff.added) {
            repeat(count) {
                val entity = ObjEntity(key.type, count = key.count, scope = ObjScope.Perm.id)
                val obj = Obj(key.coords, entity, mapClock.cycle, Obj.NULL_OBSERVER_ID)
                if (objRepo.add(obj, Int.MAX_VALUE)) added++
            }
            spawns.adjustObj(key, count)
        }
        return added to removed
    }

    private class DesiredSpawns : GameMapSpawnSink {
        val npcs = HashMap<NpcSpawnKey, Int>()
        val objs = HashMap<ObjSpawnKey, Int>()

        override fun onNpcSpawn(def: MapNpcDefinition, coords: CoordGrid) {
            npcs.merge(NpcSpawnKey(def.id, coords), 1, Int::plus)
        }

        override fun onObjSpawn(def: MapObjDefinition, coords: CoordGrid) {
            objs.merge(ObjSpawnKey(def.id, def.count, coords), 1, Int::plus)
        }

        fun validate(): List<String> = buildList {
            for (key in npcs.keys) {
                if (ServerCacheManager.getNpc(key.type) == null) add("unknown npc id ${key.type}")
                if (RegionRegistry.inWorkingArea(key.coords)) add("npc spawn in instance area")
            }
            for (key in objs.keys) {
                if (ServerCacheManager.getItem(key.type) == null) add("unknown obj id ${key.type}")
                if (key.count <= 0) add("obj spawn with count ${key.count} at ${key.coords}")
            }
        }
    }

    private data class NpcResult(val added: Int, val removed: Int, val deferred: Int, val noSlot: Int)

    private companion object {
        private const val NPC_DIR = ".data/raw-cache/map/npcs"
        private const val OBJ_DIR = ".data/raw-cache/map/objs"
        private const val MAX_ERRORS = 10
    }
}
