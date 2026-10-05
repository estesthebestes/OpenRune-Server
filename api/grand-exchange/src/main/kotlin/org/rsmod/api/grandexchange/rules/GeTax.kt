package org.rsmod.api.grandexchange.rules

/**
 * The Grand Exchange convenience fee, charged to the seller per item and rounded down.
 *
 * The OSRS Wiki (Grand Exchange, "Convenience fee and item sink") currently lists 2% with a cap of
 * 5,000,000 coins per item. Items sold for 49 coins or less pay nothing because 2% rounds down to
 * zero. Rate and cap come from `gameplay.grand-exchange.tax`.
 */
public class GeTax(ratePermille: Int, public val capPerItem: Long) {
    public val ratePermille: Int = ratePermille.coerceIn(0, MAX_RATE_PERMILLE)

    /** Tax on a single item sold at [price]. */
    public fun perItem(price: Long, exempt: Boolean = false): Long {
        if (exempt || price <= 0) {
            return 0
        }
        return minOf(capPerItem, price * ratePermille / 1000)
    }

    public fun total(price: Long, quantity: Int, exempt: Boolean = false): Long =
        perItem(price, exempt) * quantity

    public companion object {
        /** The client reads the rate from a 9-bit varbit (`ge_transmit_taxrate`). */
        public const val MAX_RATE_PERMILLE: Int = 511

        public fun fromRate(rate: Double, capPerItem: Long): GeTax =
            GeTax(Math.round(rate * 1000).toInt(), capPerItem.coerceAtLeast(0))
    }
}

/** Items the wiki lists as exempt from the convenience fee, matched on the item name. */
public object TaxExemptItems {
    private val names: Set<String> =
        setOf(
            "old school bond",
            "bronze arrow",
            "bronze dart",
            "iron arrow",
            "iron dart",
            "mind rune",
            "steel arrow",
            "steel dart",
            "bass",
            "bread",
            "cake",
            "cooked chicken",
            "cooked meat",
            "herring",
            "lobster",
            "mackerel",
            "meat pie",
            "pike",
            "salmon",
            "shrimps",
            "tuna",
            "ardougne teleport",
            "camelot teleport",
            "civitas illa fortis teleport",
            "falador teleport",
            "games necklace(8)",
            "kourend castle teleport",
            "lumbridge teleport",
            "ring of dueling(8)",
            "teleport to house",
            "varrock teleport",
            "chisel",
            "gardening trowel",
            "glassblowing pipe",
            "hammer",
            "needle",
            "pestle and mortar",
            "rake",
            "saw",
            "secateurs",
            "seed dibber",
            "shears",
            "spade",
            "watering can",
            "watering can(0)",
        )

    private const val ENERGY_POTION = "energy potion"

    public fun isExempt(itemName: String): Boolean {
        val name = itemName.trim().lowercase()
        return name in names || name == ENERGY_POTION || name.startsWith("$ENERGY_POTION(")
    }
}
