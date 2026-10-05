package org.rsmod.api.grandexchange.rules

import dev.openrune.ServerCacheManager
import org.rsmod.game.type.uncert

public data class GeItemInfo(
    val id: Int,
    val name: String,
    val cost: Int,
    val taxExempt: Boolean = false,
)

/** Item facts the exchange needs, kept behind an interface so the domain is testable without a cache. */
public interface GeItemCatalog {
    /**
     * Resolves [itemId] (noted or unnoted) to the unnoted item that is traded on the exchange, or
     * `null` when it cannot be traded there.
     */
    public fun resolve(itemId: Int): GeItemInfo?

    /** The noted form of [itemId], or `null` when the item has none. */
    public fun notedId(itemId: Int): Int?

    public fun name(itemId: Int): String
}

public class CacheItemCatalog : GeItemCatalog {
    override fun resolve(itemId: Int): GeItemInfo? {
        val type = ServerCacheManager.getItem(itemId) ?: return null
        val base = uncert(type)
        if (!base.stockmarket) {
            return null
        }
        return GeItemInfo(
            id = base.id,
            name = base.name,
            cost = base.cost,
            taxExempt = TaxExemptItems.isExempt(base.name),
        )
    }

    override fun notedId(itemId: Int): Int? {
        val type = ServerCacheManager.getItem(itemId) ?: return null
        val base = uncert(type)
        return if (base.canCert) base.certlink else null
    }

    override fun name(itemId: Int): String = ServerCacheManager.getItem(itemId)?.name ?: "Item $itemId"
}
