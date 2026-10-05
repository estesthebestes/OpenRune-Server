package org.rsmod.content.interfaces.grandexchange

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.price.GePrices
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
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
    private var tick = 0L
    private var pricesSeen = -1L

    override fun ScriptContext.startup() {
        onPlayerLogin { sessions.login(player) }
        onPlayerLogout { sessions.logout(player) }
        onEvent<GameLifecycle.LateCycle> { sweep() }

        for (clerk in CLERKS) {
            onOpNpc1(clerk) { startDialogue(it.npc) { greeting() } }
            onApNpc3(clerk) { approach(it.npc) { windows.openExchange(this) } }
            onOpNpc3(clerk) { windows.openExchange(this) }
            onApNpc4(clerk) { approach(it.npc) { windows.openHistory(this) } }
            onOpNpc4(clerk) { windows.openHistory(this) }
            onOpNpc5(clerk) { itemSets() }
        }

        onOpLoc1(BOOTH_EXCHANGE) { windows.openExchange(this) }
        for (booth in BOOTHS_WITH_COLLECT) {
            onOpLoc3(booth) { windows.openCollect(this) }
        }
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

    private fun ProtectedAccess.itemSets() {
        mes("Item sets are not available on this server yet.")
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(neutral, "Welcome to the Grand Exchange. How can I help you?")
        val option =
            choice4(
                "I'd like to set up trade offers please.",
                1,
                "How do I use the Grand Exchange?",
                2,
                "I'd like to collect my items.",
                3,
                "Actually, nothing now.",
                4,
            )
        when (option) {
            1 -> windows.openExchange(access)
            2 -> howItWorks()
            3 -> windows.openCollect(access)
            4 -> chatPlayer(neutral, "Actually, nothing now.")
        }
    }

    private suspend fun Dialogue.howItWorks() {
        chatPlayer(quiz, "How do I use the Grand Exchange?")
        chatNpc(
            neutral,
            "Set up a buy or sell offer in one of your slots. Offers at the market price trade " +
                "straight away, and other players' offers are matched first.",
        )
        chatNpc(
            neutral,
            "Whatever you buy or get back waits in the offer's collection box until you " +
                "collect it. Selling is charged a small fee.",
        )
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
