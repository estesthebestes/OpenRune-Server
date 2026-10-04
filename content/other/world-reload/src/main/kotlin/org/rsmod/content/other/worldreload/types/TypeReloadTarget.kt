package org.rsmod.content.other.worldreload.types

import dev.openrune.ServerCacheManager
import dev.openrune.reload.ServerTypeSnapshot
import dev.openrune.reload.ServerTypeSnapshotLoader
import dev.openrune.types.InvScope
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import org.rsmod.api.hotreload.ReloadContext
import org.rsmod.api.hotreload.ReloadException
import org.rsmod.api.hotreload.ReloadPlan
import org.rsmod.api.hotreload.ReloadSources
import org.rsmod.api.hotreload.ReloadSummary
import org.rsmod.api.hotreload.Reloadable
import org.rsmod.api.hotreload.types.InvTypeChange
import org.rsmod.api.hotreload.types.TypeReloadChanges
import org.rsmod.api.hotreload.types.TypeReloadListeners
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.NpcList

/**
 * Re-decodes `.data/cache/SERVER` and copies the server-only fields of every changed npc, obj,
 * loc and inv type onto the live instances, so stats, params, shop stock and similar data change
 * without a restart. Client-visible changes and brand new ids are reported as needing a restart.
 */
@Singleton
class TypeReloadTarget
@Inject
constructor(
    private val config: ServerConfig,
    private val npcList: NpcList,
    private val listeners: TypeReloadListeners,
) : Reloadable {
    override val id: String = "types"
    override val description: String = "Server cache types: npc/item/loc stats, params, shop stock"
    override val order: Int = 60
    override val watchDebounceMs: Long = 3_000

    private val cacheDir: Path
        get() = ReloadSources.workingDir.resolve(".data").resolve("cache").resolve("SERVER")

    override fun watchPaths(): List<Path> = listOf(cacheDir).filter(Files::isDirectory)

    override fun prepare(context: ReloadContext): ReloadPlan {
        if ("build" in context.options) {
            ServerCacheBuild.run(ReloadSources.workingDir)
        }
        val dir = cacheDir
        if (!Files.isDirectory(dir)) {
            throw ReloadException("Server cache not found at $dir")
        }
        val before = fileStamps(dir)
        val snapshot =
            try {
                ServerTypeSnapshotLoader.load(dir, config.revision)
            } catch (e: Exception) {
                throw ReloadException("Could not decode the server cache: ${e.message}", e)
            }
        if (fileStamps(dir) != before) {
            throw ReloadException("The cache changed while it was being read; run the reload again.")
        }
        val plan = TypePlan.build(snapshot)
        return ReloadPlan { apply(plan) }
    }

    private fun apply(plan: TypePlan): ReloadSummary {
        val stale = plan.staleIds()
        if (stale.isNotEmpty()) {
            throw ReloadException("Live types were replaced since the reload was prepared; try again.")
        }
        val npcSnapshots = plan.npcs.associate { it.live.id to NpcTypeSnapshot.of(it.live) }
        val invChanges =
            plan.invs.associate { delta ->
                delta.live.id to InvTypeChange(delta.live.size, delta.live.stock, delta.live.flags)
            }
        plan.npcs.forEach { it.apply() }
        plan.items.forEach { it.apply() }
        plan.locs.forEach { it.apply() }
        plan.invs.forEach { it.apply() }

        val sync = LiveNpcSync.apply(npcList, npcSnapshots)
        val changes =
            TypeReloadChanges(
                npcFields = plan.npcs.associate { it.live.id to it.changedNames },
                itemFields = plan.items.associate { it.live.id to it.changedNames },
                locFields = plan.locs.associate { it.live.id to it.changedNames },
                invs = invChanges.filterKeys { id -> plan.invs.any { it.live.id == id && it.changed.isNotEmpty() } },
            )
        val listenerLines = mutableListOf<String>()
        val warnings = plan.warnings.toMutableList()
        for ((name, listener) in listeners.all()) {
            try {
                listener.onTypesReloaded(changes)?.let(listenerLines::add)
            } catch (e: Exception) {
                warnings += "$name FAILED: ${e.message}"
            }
        }
        if (sync.onRespawn > 0) {
            warnings += "${sync.onRespawn} damaged or fighting npcs get their new stats on respawn"
        }
        val counts =
            "npcs ${plan.npcs.countCopied()}, items ${plan.items.countCopied()}, " +
                "locs ${plan.locs.countCopied()}, invs ${plan.invs.countCopied()}"
        val message =
            if (plan.isEmpty) {
                "no changes"
            } else {
                (listOf("updated $counts; ${sync.refreshed} live npcs refreshed") + listenerLines)
                    .joinToString("; ")
            }
        return ReloadSummary(message, warnings)
    }

    private fun fileStamps(dir: Path): Map<Path, Pair<Long, Long>> =
        Files.walk(dir).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .toList()
                .associateWith { it.fileSize() to it.getLastModifiedTime().toMillis() }
        }

    private class TypePlan(
        val npcs: List<TypeDelta<NpcServerType>>,
        val items: List<TypeDelta<ItemServerType>>,
        val locs: List<TypeDelta<ObjectServerType>>,
        val invs: List<TypeDelta<InventoryServerType>>,
        val warnings: List<String>,
    ) {
        val isEmpty: Boolean
            get() = npcs.isEmpty() && items.isEmpty() && locs.isEmpty() && invs.isEmpty()

        fun staleIds(): List<Int> =
            npcs.filter { ServerCacheManager.getNpc(it.live.id) !== it.live }.map { it.live.id } +
                items.filter { ServerCacheManager.getItem(it.live.id) !== it.live }.map { it.live.id } +
                locs.filter { ServerCacheManager.getObject(it.live.id) !== it.live }.map { it.live.id } +
                invs.filter { ServerCacheManager.getInventory(it.live.id) !== it.live }.map { it.live.id }

        companion object {
            fun build(snapshot: ServerTypeSnapshot): TypePlan {
                val warnings = mutableListOf<String>()
                val npcs = diffAll("npc", snapshot.npcs, ServerCacheManager::getNpc, NPC_POLICY, warnings)
                val items =
                    diffAll("item", snapshot.items, ServerCacheManager::getItem, ITEM_POLICY, warnings)
                val locs =
                    diffAll("loc", snapshot.locs, ServerCacheManager::getObject, LOC_POLICY, warnings)
                val invs = diffInvs(snapshot.invs, warnings)
                val weaponChanges =
                    items.count { delta ->
                        delta.changed.any {
                            it == ItemServerType::contentGroup || it == ItemServerType::weaponCategory
                        }
                    }
                if (weaponChanges > 0) {
                    warnings += "$weaponChanges items changed weapon category: needs a restart"
                }
                return TypePlan(npcs, items, locs, invs, warnings)
            }

            private fun <T : Any> diffAll(
                label: String,
                fresh: Map<Int, T>,
                live: (Int) -> T?,
                policy: TypeFieldPolicy<T>,
                warnings: MutableList<String>,
            ): List<TypeDelta<T>> {
                val deltas = mutableListOf<TypeDelta<T>>()
                var newIds = 0
                val restartFields = linkedSetOf<String>()
                var restartTypes = 0
                for ((id, freshType) in fresh) {
                    val liveType = live(id)
                    if (liveType == null) {
                        newIds++
                        continue
                    }
                    val delta = policy.diff(liveType, freshType) ?: continue
                    if (delta.restartOnly.isNotEmpty()) {
                        restartTypes++
                        restartFields += delta.restartOnly
                    }
                    deltas += delta
                }
                if (newIds > 0) {
                    warnings += "$newIds new ${label}s ignored: new ids need a restart"
                }
                if (restartTypes > 0) {
                    warnings +=
                        "$restartTypes ${label}s changed ${restartFields.joinToString()}: " +
                            "needs a restart (client-visible)"
                }
                return deltas
            }

            private fun diffInvs(
                fresh: Map<Int, InventoryServerType>,
                warnings: MutableList<String>,
            ): List<TypeDelta<InventoryServerType>> {
                val deltas = mutableListOf<TypeDelta<InventoryServerType>>()
                for ((id, freshType) in fresh) {
                    val liveType = ServerCacheManager.getInventory(id) ?: continue
                    val shared = liveType.scope == InvScope.Shared
                    val policy =
                        TypeFieldPolicy(
                            copy =
                                buildList {
                                    add(InventoryServerType::stock)
                                    add(InventoryServerType::flags)
                                    add(InventoryServerType::stack)
                                    if (shared) add(InventoryServerType::size)
                                },
                            restartOnly =
                                buildList {
                                    add("scope" to InventoryServerType::scope)
                                    if (!shared) add("size" to InventoryServerType::size)
                                },
                        )
                    val delta = policy.diff(liveType, freshType) ?: continue
                    if (delta.restartOnly.isNotEmpty()) {
                        warnings += "inv $id changed ${delta.restartOnly.joinToString()}: needs a restart"
                    }
                    deltas += delta
                }
                return deltas
            }
        }
    }

    private companion object {
        private fun List<TypeDelta<*>>.countCopied(): Int = count { it.changed.isNotEmpty() }
    }
}
