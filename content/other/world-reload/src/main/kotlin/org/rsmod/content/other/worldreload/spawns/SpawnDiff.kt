package org.rsmod.content.other.worldreload.spawns

internal data class SpawnDiff<K>(val added: Map<K, Int>, val removed: Map<K, Int>) {
    val isEmpty: Boolean
        get() = added.isEmpty() && removed.isEmpty()

    companion object {
        fun <K> of(current: Map<K, Int>, desired: Map<K, Int>): SpawnDiff<K> {
            val added = HashMap<K, Int>()
            val removed = HashMap<K, Int>()
            for (key in current.keys + desired.keys) {
                val delta = (desired[key] ?: 0) - (current[key] ?: 0)
                when {
                    delta > 0 -> added[key] = delta
                    delta < 0 -> removed[key] = -delta
                }
            }
            return SpawnDiff(added, removed)
        }
    }
}
