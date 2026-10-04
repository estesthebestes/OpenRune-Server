package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TreeGnomeVillageNativeMapTest {
    @Test
    fun `the quest's fixed coordinates match the native map`() {
        val cache = ServerCacheManager.init(240)
        try {
            val collision = CollisionFlagMap()
            val locs = mutableListOf<Pair<Int, CoordGrid>>()
            for ((x, z) in listOf(39 to 49, 39 to 50)) {
                val group = (x shl 8) or z
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..1) for (cx in (x * 64)..(x * 64 + 63) step 8) {
                    for (cz in (z * 64)..(z * 64 + 63) step 8) {
                        collision.allocateIfAbsent(cx, cz, level)
                    }
                }
                val builder = GameMapBuilder()
                val square = MapSquareKey(x, z)
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        locs += LocEntity(entry.intValue).id to base.translate(key.x, key.z)
                    }
                }
            }
            val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR
            for (tile in listOf(GnomeMaze.Entrance, GnomeMaze.RailingApproach, GnomeMaze.RailingTile)) {
                assertEquals(0, collision[tile.x, tile.z, tile.level] and blocked, "$tile")
            }
            fun at(loc: String) = locs.filter { it.first == loc.asRSCM() }.map { it.second }
            assertEquals(listOf(GnomeMaze.RailingTile), at("loc.treegnomelooserailing"))
            assertEquals(listOf(CoordGrid(2509, 3253, 0)), at("loc.khazzacklowwall"))
            assertEquals(listOf(CoordGrid(2502, 3250, 0)), at("loc.khazard_stronghold_door"))
            assertEquals(listOf(CoordGrid(2508, 3210, 0)), at("loc.catabow"))
            assertEquals(listOf(CoordGrid(2506, 3259, 1)), at("loc.chestclosed_khazard"))
            for (wall in listOf(CoordGrid(2509, 3252, 0), CoordGrid(2509, 3254, 0))) {
                assertEquals(0, collision[wall.x, wall.z, 0] and blocked, "$wall")
            }
        } finally {
            cache.close()
        }
    }
}
