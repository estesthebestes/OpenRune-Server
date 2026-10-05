package org.rsmod.content.areas.city.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.table.PotionRow

/** Items that differ only by how much is left in them, from one remaining up to [capacity]. */
internal class DoseFamily(
    val name: String,
    val doses: List<ItemServerType>,
    val empty: ItemServerType?,
) {
    val capacity: Int
        get() = doses.size

    fun item(doses: Int): ItemServerType = this.doses[doses - 1]
}

internal class DoseEntry(val family: DoseFamily, val doses: Int, val noted: Boolean)

internal class DoseFamilyIndex(val families: List<DoseFamily>) {
    private val byObj: Map<Int, DoseEntry> =
        buildMap {
            for (family in families) {
                for ((index, item) in family.doses.withIndex()) {
                    put(item.id, DoseEntry(family, index + 1, noted = false))
                    if (item.canCert) {
                        put(item.certlink, DoseEntry(family, index + 1, noted = true))
                    }
                }
            }
        }

    fun find(obj: Int): DoseEntry? = byObj[obj]
}

internal object DoseFamilies {
    private val POTION_CATEGORIES = setOf("potion", "divine_potion", "barbarian_mix", "tea")

    private val EXTRA =
        listOf(
            "Olive oil" to
                listOf("obj.oliveoil1", "obj.oliveoil2", "obj.oliveoil3", "obj.oliveoil4"),
            "Sacred oil" to
                listOf("obj.sacred_oil1", "obj.sacred_oil2", "obj.sacred_oil3", "obj.sacred_oil4"),
            "Sunlight moth mix" to
                listOf("obj.hunter_mix_sunmoth_1dose", "obj.hunter_mix_sunmoth_2dose"),
            "Moonlight moth mix" to
                listOf("obj.hunter_mix_moonmoth_1dose", "obj.hunter_mix_moonmoth_2dose"),
        )

    val decantable: DoseFamilyIndex by lazy {
        val vial = item("obj.vial_empty")
        val potions =
            PotionRow.all()
                .filter { it.category in POTION_CATEGORIES }
                .map { DoseFamily(it.name, it.items.reversed(), it.empty) }
        val extras = EXTRA.map { (name, items) -> DoseFamily(name, items.map(::item), vial) }
        DoseFamilyIndex(potions + extras)
    }

    val jewellery: DoseFamilyIndex by lazy {
        DoseFamilyIndex(
            listOf(
                charges("Ring of dueling", "obj.ring_of_dueling_", 8),
                charges("Games necklace", "obj.necklace_of_minigames_", 8),
                charges("Combat bracelet", "obj.jewl_bracelet_of_combat_", 4),
                charges("Skills necklace", "obj.jewl_necklace_of_skills_", 4),
                charges("Amulet of glory", "obj.amulet_of_glory_", 4),
                charges("Ring of wealth", "obj.ring_of_wealth_", 5),
                charges("Digsite pendant", "obj.necklace_of_digsite_", 5),
                charges("Necklace of passage", "obj.necklace_of_passage_", 5),
                charges("Burning amulet", "obj.burning_amulet_", 5),
                charges("Slayer ring", "obj.slayer_ring_", 8),
                charges("Ring of returning", "obj.ring_of_returning_", 5),
            )
        )
    }

    private fun charges(name: String, prefix: String, max: Int) =
        DoseFamily(name, (1..max).map { item("$prefix$it") }, empty = null)

    private fun item(symbol: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ))) { symbol }
}
