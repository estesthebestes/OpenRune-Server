package org.rsmod.api.grandexchange

import java.nio.file.Path
import java.nio.file.Paths
import org.rsmod.api.server.config.GrandExchangeYaml

/** `gameplay.grand-exchange` from `game.yml`, in the shapes the exchange wants. */
public data class GrandExchangeSettings(
    val enabled: Boolean,
    val fillModel: String,
    val liveFetch: Boolean,
    val refreshMinutes: Long,
    val snapshotPath: Path,
    val userAgent: String,
    val taxRate: Double,
    val taxCap: Long,
) {
    public companion object {
        public fun from(yaml: GrandExchangeYaml): GrandExchangeSettings =
            GrandExchangeSettings(
                enabled = yaml.enabled,
                fillModel = yaml.fillModel,
                liveFetch = yaml.prices.live,
                refreshMinutes = yaml.prices.refreshMinutes.toLong().coerceAtLeast(1),
                snapshotPath = Paths.get(yaml.prices.snapshotPath),
                userAgent = yaml.prices.userAgent,
                taxRate = yaml.tax.rate,
                taxCap = yaml.tax.cap,
            )
    }
}
