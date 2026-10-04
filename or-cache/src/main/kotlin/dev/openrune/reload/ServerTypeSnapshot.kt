package dev.openrune.reload

import dev.openrune.cache.DenseIntMap
import dev.openrune.codec.osrs.InventoryDecoder
import dev.openrune.codec.osrs.ItemDecoder
import dev.openrune.codec.osrs.NpcDecoder
import dev.openrune.codec.osrs.ObjectDecoder
import dev.openrune.filesystem.Cache
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import java.nio.file.Path

/** Freshly decoded server types, independent of the live tables in `ServerCacheManager`. */
public class ServerTypeSnapshot(
    public val npcs: Map<Int, NpcServerType>,
    public val items: Map<Int, ItemServerType>,
    public val locs: Map<Int, ObjectServerType>,
    public val invs: Map<Int, InventoryServerType>,
)

public object ServerTypeSnapshotLoader {
    public fun load(cacheDir: Path, rev: Int): ServerTypeSnapshot {
        val cache = Cache.load(cacheDir)
        try {
            val npcs = DenseIntMap<NpcServerType>()
            val items = DenseIntMap<ItemServerType>()
            val locs = DenseIntMap<ObjectServerType>()
            val invs = DenseIntMap<InventoryServerType>()
            NpcDecoder(rev).load(cache, npcs)
            ItemDecoder(rev).load(cache, items)
            ObjectDecoder(rev).load(cache, locs)
            InventoryDecoder().load(cache, invs)
            return ServerTypeSnapshot(npcs.readOnly(), items.readOnly(), locs.readOnly(), invs.readOnly())
        } finally {
            cache.close()
        }
    }
}
