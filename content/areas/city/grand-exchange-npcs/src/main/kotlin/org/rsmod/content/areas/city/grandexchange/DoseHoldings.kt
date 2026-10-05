package org.rsmod.content.areas.city.grandexchange

import org.rsmod.game.inv.Inventory

internal class DoseHolding(val family: DoseFamily) {
    private val unnoted = LongArray(family.capacity + 1)
    private val noted = LongArray(family.capacity + 1)

    fun add(entry: DoseEntry, count: Int) {
        val counts = if (entry.noted) noted else unnoted
        counts[entry.doses] += count.toLong()
    }

    val hasNoted: Boolean
        get() = noted.any { it > 0 }

    val totalDoses: Long
        get() = (1..family.capacity).sumOf { it * (unnoted[it] + noted[it]) }

    val containers: Long
        get() = (1..family.capacity).sumOf { unnoted[it] + noted[it] }

    fun held(): Map<Int, Long> = (1..family.capacity).associateWith { unnoted[it] + noted[it] }

    fun unnotedCount(doses: Int): Long = unnoted[doses]

    fun notedCount(doses: Int): Long = noted[doses]
}

internal fun Inventory.holdings(index: DoseFamilyIndex): Map<DoseFamily, DoseHolding> {
    val result = LinkedHashMap<DoseFamily, DoseHolding>()
    for (obj in objs) {
        obj ?: continue
        val entry = index.find(obj.id) ?: continue
        result.getOrPut(entry.family) { DoseHolding(entry.family) }.add(entry, obj.count)
    }
    return result
}
