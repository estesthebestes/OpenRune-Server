package org.rsmod.content.quest.area.ardougne.plaguecity

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.loc.MapLocListDefinition
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/** The real map squares Plague City plays across, decoded once from the cache. */
internal object PlagueCityMap {
    /** East Ardougne's garden, the Rehnison house, the civic office, the plague house and sewers. */
    val squares =
        listOf(
            MapSquareKey(40, 52),
            MapSquareKey(39, 52),
            MapSquareKey(39, 51),
            MapSquareKey(39, 151),
            MapSquareKey(39, 152),
        )

    data class Placed(val id: Int, val coords: CoordGrid, val shape: Int, val angle: Int)

    private val data =
        mutableListOf<Pair<MapSquareKey, Pair<MapTileSimpleDefinition, MapLocListDefinition>>>()

    fun load(cache: Cache) {
        data.clear()
        for (square in squares) {
            val group = (square.x shl 8) or square.z
            val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
            val locs =
                MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
            data += square to (tiles to locs)
        }
    }

    /** Loads collision and static locs into [collision] and [storage]; returns what was placed. */
    fun apply(collision: CollisionFlagMap, storage: LocZoneStorage?): List<Placed> {
        val placed = mutableListOf<Placed>()
        val builder = GameMapBuilder()
        for ((square, def) in data) {
            for (level in 0..3) {
                for (x in square.x * 64 until square.x * 64 + 64 step 8) {
                    for (z in square.z * 64 until square.z * 64 + 64 step 8) {
                        collision.allocateIfAbsent(x, z, level)
                    }
                }
            }
            GameMapDecoder.putMaps(collision, square, def.first)
            GameMapDecoder.putLocs(builder, collision, square, def.first, def.second)
        }
        for ((packed, zone) in builder.zoneBuilders) {
            val built = zone.build()
            if (storage != null) storage.mapLocs[packed] = built
            val base = ZoneKey(packed).toCoords()
            for (entry in built.byte2IntEntrySet()) {
                val key = LocZoneKey(entry.byteKey)
                val loc = LocEntity(entry.intValue)
                placed += Placed(loc.id, base.translate(key.x, key.z), loc.shape, loc.angle)
            }
        }
        return placed
    }
}
