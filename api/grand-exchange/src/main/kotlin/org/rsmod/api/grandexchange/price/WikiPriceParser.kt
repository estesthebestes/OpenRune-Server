package org.rsmod.api.grandexchange.price

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Parses the payloads of the OSRS Wiki real-time prices API. */
public object WikiPriceParser {
    private val mapper = ObjectMapper()

    /** `/latest`: `{"data": {"<id>": {"high": n, "highTime": n, "low": n, "lowTime": n}}}`. */
    public fun parseLatest(json: String): Map<Int, PricePoint> {
        val data = mapper.readTree(json).path("data")
        val out = HashMap<Int, PricePoint>(data.size() * 2)
        for ((key, node) in data.fields()) {
            val id = key.toIntOrNull() ?: continue
            val high = node.positiveLong("high")
            val low = node.positiveLong("low")
            if (high != null || low != null) {
                out[id] = PricePoint(high, low)
            }
        }
        return out
    }

    /** `/mapping`: `[{"id": n, "name": "...", "limit": n, "value": n, ...}]`. */
    public fun parseBuyLimits(json: String): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (node in mapper.readTree(json)) {
            val id = node.path("id").asInt(-1)
            val limit = node.path("limit").asInt(0)
            if (id >= 0 && limit > 0) {
                out[id] = limit
            }
        }
        return out
    }

    private fun JsonNode.positiveLong(field: String): Long? {
        val value = path(field)
        if (!value.isNumber) {
            return null
        }
        return value.asLong().takeIf { it > 0 }
    }
}
