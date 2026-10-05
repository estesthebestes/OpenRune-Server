package org.rsmod.content.areas.city.grandexchange

internal data class DoseCount(val doses: Int, val count: Long)

internal object DoseMath {
    /** Pours [totalDoses] into as many full containers of [perContainer] as possible. */
    fun redistribute(totalDoses: Long, perContainer: Int): List<DoseCount> {
        require(perContainer > 0) { "perContainer=$perContainer" }
        if (totalDoses <= 0) {
            return emptyList()
        }
        val full = totalDoses / perContainer
        val remainder = (totalDoses % perContainer).toInt()
        return buildList {
            if (full > 0) {
                add(DoseCount(perContainer, full))
            }
            if (remainder > 0) {
                add(DoseCount(remainder, 1))
            }
        }
    }

    fun containers(plan: List<DoseCount>): Long = plan.sumOf { it.count }

    fun matches(plan: List<DoseCount>, current: Map<Int, Long>): Boolean {
        val planned = plan.associate { it.doses to it.count }
        val held = current.filterValues { it > 0 }
        return planned == held
    }
}
