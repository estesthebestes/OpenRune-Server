package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.map.CoordGrid

/**
 * Where a multicannon may not be set up, and what the player is told. Places with a named map
 * area use it; the rest of the wiki's list is covered by plain rectangles.
 */
class CannonRestrictions @Inject constructor(private val areas: AreaChecker) {

    fun message(coords: CoordGrid): String? {
        for ((area, message) in AREA_RULES) {
            if (areas.inArea(area, coords)) {
                return message
            }
        }
        return ZONE_RULES.firstOrNull { it.contains(coords) }?.message
    }

    private class Zone(
        val minX: Int,
        val minZ: Int,
        val maxX: Int,
        val maxZ: Int,
        val message: String,
    ) {
        fun contains(coords: CoordGrid): Boolean = coords.x in minX..maxX && coords.z in minZ..maxZ
    }

    private companion object {
        const val CANT = "You can't set up a cannon here."
        const val DWARVES = "The dwarves won't be happy if you set up a cannon here."
        const val ANCIENT_POWER = "An ancient power won't let you set up a cannon here."
        const val ANCIENT_MAGIC = "An ancient magical force won't let you set up a cannon here."
        const val TZHAAR = "The TzHaar won't be happy if you set up a cannon here."
        const val DANK = "The air is too dank for you to set up a cannon here."

        val AREA_RULES =
            listOf(
                "area.abyss" to CANT,
                "area.ancient_cavern" to "It's far too damp for you to set up a cannon here.",
                "area.catacombs_of_kourend" to ANCIENT_POWER,
                "area.forthos_dungeon" to
                    "A mysterious presence won't let you set up a cannon here.",
                "area.fremennik_slayer_dungeon" to DANK,
                "area.lighthouse_dungeon" to DANK,
                "area.jormungandprison" to "The ground is too soft for you to set up a cannon here.",
                "area.kraken_cove" to CANT,
                "area.molch_and_lizardman_temple" to
                    "This ancient structure is too unstable to handle the idea of a cannon " +
                        "firing at its walls.",
                "area.revenant_caves" to CANT,
                "area.tapoyauik" to ANCIENT_MAGIC,
                "area.cryptoftonali" to ANCIENT_MAGIC,
                "area.slayer_tower" to "A strange magical force won't let you set up a cannon here.",
                "area.smoke_dungeon" to "Dusty Aliv won't be happy if you set up a cannon here.",
            )

        val ZONE_RULES =
            listOf(
                Zone(
                    3005,
                    3504,
                    3035,
                    3527,
                    "It is not permitted to set up a cannon this close to the Dwarf Black Guard.",
                ),
                Zone(2979, 3417, 3071, 3519, DWARVES),
                Zone(2944, 9740, 3071, 9855, DWARVES),
                Zone(2800, 3325, 2870, 3395, CANT),
                Zone(2368, 5056, 2559, 5183, TZHAAR),
                Zone(2240, 5312, 2303, 5375, TZHAAR),
                Zone(2624, 2560, 2687, 2687, CANT),
                Zone(2520, 3560, 2545, 3585, CANT),
                Zone(2368, 3072, 2447, 3135, CANT),
                Zone(2368, 9472, 2431, 9535, CANT),
                Zone(1856, 5440, 1919, 5503, CANT),
                Zone(2828, 3534, 2876, 3556, CANT),
                Zone(2816, 5184, 2908, 5247, ANCIENT_POWER),
                Zone(1344, 4480, 1407, 4543, ANCIENT_MAGIC),
                Zone(3638, 3200, 3698, 3238, "The vampyres won't be happy if you set up a cannon here."),
                Zone(
                    3143,
                    3462,
                    3187,
                    3510,
                    "The Grand Exchange staff prefer not to have heavy artillery operated " +
                        "around their premises.",
                ),
            )
    }
}
