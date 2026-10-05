package org.rsmod.api.grandexchange.fill

import org.rsmod.api.grandexchange.offer.OfferType
import org.rsmod.api.grandexchange.price.MarketQuote

/** The part of an open offer a [FillModel] needs to see. */
public data class MarketOffer(
    val type: OfferType,
    val itemId: Int,
    val price: Long,
    val quantity: Int,
    val remaining: Int,
)

public data class MarketFill(val quantity: Int, val price: Long)

/**
 * Decides how an open offer trades against the simulated market once players have had first pick.
 * Selected with `gameplay.grand-exchange.fill-model`; a volume-paced `gradual` model can later be
 * added by implementing this interface and registering it in [FillModels].
 */
public interface FillModel {
    public val name: String

    /** The slice of [offer] that trades against the market now, or `null` to leave it resting. */
    public fun marketFill(offer: MarketOffer, quote: MarketQuote): MarketFill?

    /**
     * Whether resting offers should be re-checked against the market this cycle. [pricesChanged] is
     * true on the first cycle after new prices were installed.
     */
    public fun shouldSweep(pricesChanged: Boolean, tick: Long): Boolean
}

/**
 * An offer at or through the market price fills immediately and in full, at the market price (so
 * a buyer is refunded the difference and a seller receives more than asked). An offer priced away
 * from the market stays open until the market moves to it or a player takes the other side.
 */
public object InstantFillModel : FillModel {
    override val name: String = "instant"

    override fun marketFill(offer: MarketOffer, quote: MarketQuote): MarketFill? {
        if (offer.remaining <= 0) {
            return null
        }
        return when (offer.type) {
            OfferType.BUY ->
                if (offer.price >= quote.buyAt) MarketFill(offer.remaining, quote.buyAt) else null
            OfferType.SELL ->
                if (offer.price <= quote.sellAt) MarketFill(offer.remaining, quote.sellAt) else null
        }
    }

    override fun shouldSweep(pricesChanged: Boolean, tick: Long): Boolean = pricesChanged
}

public object FillModels {
    public val available: List<String> = listOf(InstantFillModel.name)

    /** @throws IllegalArgumentException for a name that has no implementation. */
    public fun create(name: String): FillModel =
        when (name.trim().lowercase()) {
            InstantFillModel.name -> InstantFillModel
            else ->
                throw IllegalArgumentException(
                    "Unknown grand-exchange fill-model '$name'. Available: ${available.joinToString()}"
                )
        }
}
