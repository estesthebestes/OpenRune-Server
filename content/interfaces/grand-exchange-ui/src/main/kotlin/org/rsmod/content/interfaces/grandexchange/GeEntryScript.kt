package org.rsmod.content.interfaces.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onApNpc5
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Everything that gets a player into the exchange (the clerks and the booths), plus the session
 * plumbing: players join the order book on login and leave it on logout, and resting offers are
 * re-checked against the market when new prices arrive.
 */
internal class GeEntryScript
@Inject
constructor(
    private val exchange: GrandExchange,
    private val sessions: GeSessions,
    private val windows: GeWindows,
    private val prices: GePrices,
) : PluginScript() {
    private val itemSets = GeItemSets()
    private val clerks = GeClerkDialogue(windows, itemSets, sessions)
    private var tick = 0L
    private var pricesSeen = -1L

    override fun ScriptContext.startup() {
        onPlayerLogin {
            sessions.login(player)
            clerks.remind(player)
        }
        onPlayerLogout { sessions.logout(player) }
        onEvent<GameLifecycle.LateCycle> { sweep() }

        for (clerk in CLERKS) {
            onApNpc1(clerk) { approach(it.npc) { talk(it.npc) } }
            onOpNpc1(clerk) { talk(it.npc) }
            onApNpc3(clerk) { approach(it.npc) { windows.openExchange(this) } }
            onOpNpc3(clerk) { windows.openExchange(this) }
            onApNpc4(clerk) { approach(it.npc) { windows.openHistory(this) } }
            onOpNpc4(clerk) { windows.openHistory(this) }
            onApNpc5(clerk) { approach(it.npc) { itemSets.open(this) } }
            onOpNpc5(clerk) { itemSets.open(this) }
        }

        onOpLoc1(BOOTH_EXCHANGE) { windows.openExchange(this) }
        for (booth in BOOTHS_WITH_COLLECT) {
            onOpLoc3(booth) { windows.openCollect(this) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) { clerks.greet(this) }
    }

    private fun sweep() {
        tick++
        val version = prices.version
        val changed = version != pricesSeen
        pricesSeen = version
        exchange.sweep(tick, changed)
    }

    private inline fun ProtectedAccess.approach(npc: Npc, action: ProtectedAccess.() -> Unit) {
        if (isWithinApRange(npc, distance = 2)) {
            action()
        }
    }

    private companion object {
        val CLERKS = listOf("npc.ge_clerk_1", "npc.ge_clerk_2", "npc.ge_clerk_3", "npc.ge_clerk_4")
        const val BOOTH_EXCHANGE = "loc.exchange_bank_wall_exchange"
        val BOOTHS_WITH_COLLECT =
            listOf(
                "loc.exchange_bank_wall_exchange",
                "loc.exchange_bank_wall_bank",
                "loc.exchange_bank_wall_bank_3ops",
            )
    }
}
