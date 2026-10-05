package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.grandexchange.GrandExchangeSettings
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.grandexchange.rules.CacheItemCatalog
import org.rsmod.api.grandexchange.rules.GeTax
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.banks.BankBooth
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GeBoothTest {
    private class Placed(val coords: CoordGrid, val entity: LocEntity)

    private fun deskLocs(): List<Placed> {
        val cache = ServerCacheManager.init(240)
        try {
            val collision = CollisionFlagMap()
            val x = 49
            val z = 54
            val group = (x shl 8) or z
            val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
            val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
            for (cx in (x * 64)..(x * 64 + 63) step 8) {
                for (cz in (z * 64)..(z * 64 + 63) step 8) {
                    collision.allocateIfAbsent(cx, cz, 0)
                }
            }
            val builder = GameMapBuilder()
            val square = MapSquareKey(x, z)
            GameMapDecoder.putMaps(collision, square, tiles)
            GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
            val found = ArrayList<Placed>()
            for ((packed, zone) in builder.zoneBuilders) {
                val base = ZoneKey(packed).toCoords()
                for (entry in zone.build().byte2IntEntrySet()) {
                    val key = LocZoneKey(entry.byteKey)
                    val coords = base.translate(key.x, key.z)
                    if (coords.level == 0 && coords.x in 3160..3169 && coords.z in 3486..3493) {
                        found += Placed(coords, LocEntity(entry.intValue))
                    }
                }
            }
            return found
        } finally {
            cache.close()
        }
    }

    private fun interactions(): LocInteractions {
        val events = EventBus()
        val catalog = CacheItemCatalog()
        val exchange = GrandExchange(catalog, GePrices(null, null, { null }), GeTax(20, 5_000_000))
        val sessions = GeSessions(exchange, catalog)
        val windows =
            GeWindows(
                exchange,
                sessions,
                GrandExchangeSettings(true, "instant", false, 5, Paths.get("unused.json"), "test", 0.02, 5_000_000),
                GeSetup(GePrices(null, null, { null }), catalog, sessions),
            )
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(GeEntryScript(exchange, sessions, windows, GePrices(null, null, { null }))) { context.startup() }
        with(BankBooth()) { context.startup() }
        return LocInteractions(BoundValidator(CollisionFlagMap()), events)
    }

    @Test
    fun `every booth around the desk resolves its Bank, Exchange and Collect options server-side`() {
        val booths =
            deskLocs().filter { ServerCacheManager.getObject(it.entity.id)?.name == "Grand Exchange booth" ||
                ServerCacheManager.getObject(it.entity.id)?.multiLoc?.isNotEmpty() == true }
        val interactions = interactions()
        val player = Player()
        val seen = HashMap<String, Int>()
        for (placed in booths) {
            val type = checkNotNull(ServerCacheManager.getObject(placed.entity.id))
            val bound = BoundLocInfo(LocInfo(LocLayerConstants.of(placed.entity.shape), placed.coords, placed.entity), type)
            val shown =
                interactions.multiLoc(bound, type, player.vars)?.let { ServerCacheManager.getObject(it.id) } ?: type
            for (op in 1..5) {
                val text = shown.actions.getOpOrNull(op - 1)?.takeIf { it.isNotBlank() } ?: continue
                val interactionOp = InteractionOp.entries[op - 1]
                if (text == "Deposit-box") continue
                assertTrue(text in setOf("Bank", "Exchange", "Collect"), "$text at ${placed.coords}")
                assertNotNull(
                    interactions.opTrigger(player, bound, interactionOp, type),
                    "$text (op$op) at ${placed.coords} on ${RSCM.getReverseMapping(RSCMType.LOC, placed.entity.id)}",
                )
                seen.merge(text, 1, Int::plus)
            }
        }
        assertEquals(8, seen["Collect"], "four exchange and four bank booths each collect")
        assertEquals(4, seen["Exchange"])
        assertEquals(4, seen["Bank"])
    }
}
