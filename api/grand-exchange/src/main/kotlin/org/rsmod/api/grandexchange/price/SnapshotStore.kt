package org.rsmod.api.grandexchange.price

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Persists the last good [PriceSnapshot] so the exchange has prices at boot and while offline. */
public class SnapshotStore(private val path: Path) {
    private val mapper = ObjectMapper()

    /** Returns `null` when there is no usable snapshot (missing, unreadable or corrupt). */
    public fun load(): PriceSnapshot? {
        if (!Files.isRegularFile(path)) {
            return null
        }
        return try {
            decode(Files.readString(path))
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    /** Writes next to the target and then moves into place so a crash never leaves half a file. */
    public fun save(snapshot: PriceSnapshot) {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, encode(snapshot))
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
    }

    public fun encode(snapshot: PriceSnapshot): String {
        val root = mapper.createObjectNode()
        root.put("fetchedAtMillis", snapshot.fetchedAtMillis)
        root.put("limitsFetchedAtMillis", snapshot.limitsFetchedAtMillis)
        val prices = root.putObject("prices")
        for ((id, point) in snapshot.prices) {
            val node = prices.putObject(id.toString())
            point.high?.let { node.put("high", it) }
            point.low?.let { node.put("low", it) }
        }
        val limits = root.putObject("buyLimits")
        for ((id, limit) in snapshot.buyLimits) {
            limits.put(id.toString(), limit)
        }
        return mapper.writeValueAsString(root)
    }

    public fun decode(json: String): PriceSnapshot {
        val root = mapper.readTree(json)
        val prices = HashMap<Int, PricePoint>()
        for ((key, node) in root.path("prices").fields()) {
            val id = key.toIntOrNull() ?: continue
            val high = node.path("high").takeIf { it.isNumber }?.asLong()
            val low = node.path("low").takeIf { it.isNumber }?.asLong()
            if (high != null || low != null) {
                prices[id] = PricePoint(high, low)
            }
        }
        val limits = HashMap<Int, Int>()
        for ((key, node) in root.path("buyLimits").fields()) {
            val id = key.toIntOrNull() ?: continue
            limits[id] = node.asInt()
        }
        return PriceSnapshot(
            fetchedAtMillis = root.path("fetchedAtMillis").asLong(0L),
            prices = prices,
            buyLimits = limits,
            limitsFetchedAtMillis = root.path("limitsFetchedAtMillis").asLong(0L),
        )
    }
}
