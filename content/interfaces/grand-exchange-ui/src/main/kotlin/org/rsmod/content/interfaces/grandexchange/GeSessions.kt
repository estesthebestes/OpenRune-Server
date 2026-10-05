package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.grandexchange.engine.CollectPart
import org.rsmod.api.grandexchange.engine.CollectSink
import org.rsmod.api.grandexchange.engine.GrandExchange
import org.rsmod.api.grandexchange.offer.SlotCodec
import org.rsmod.api.grandexchange.rules.GeItemCatalog
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

/** The online players the exchange knows about, keyed by user id. */
@Singleton
internal class GeSessions
@Inject
constructor(private val exchange: GrandExchange, private val catalog: GeItemCatalog) {
    private val sessions = HashMap<Long, GePlayer>()

    fun of(player: Player): GePlayer {
        val existing = sessions[player.userId]
        if (existing != null && existing.player === player) {
            return existing
        }
        val created = GePlayer(player, catalog)
        sessions[player.userId] = created
        return created
    }

    fun login(player: Player) {
        val session = GePlayer(player, catalog)
        sessions[player.userId] = session
        exchange.join(session)
        session.pushAllSlots()
    }

    fun logout(player: Player) {
        val session = sessions[player.userId] ?: return
        if (session.player === player) {
            exchange.leave(session)
            sessions.remove(player.userId)
        }
    }

    /** Makes the client receive the eight collection boxes. */
    fun transmitBoxes(player: Player) {
        for (name in GeIds.boxInvs) {
            player.startInvTransmit(player.invMap.getOrPut(name))
        }
    }
}

/** Puts collected goods into the inventory or the bank, taking only what fits. */
internal class InventorySink(
    private val player: Player,
    private val catalog: GeItemCatalog,
    private val toBank: Boolean,
) : CollectSink {
    private val target: Inventory
        get() = if (toBank) player.invMap.getOrPut("inv.bank") else player.inv

    override fun acceptItems(itemId: Int, count: Int, noted: Boolean): Int {
        val objId = if (noted && !toBank) catalog.notedId(itemId) ?: itemId else itemId
        return add(objId, count).toInt().coerceIn(0, count)
    }

    override fun acceptCoins(amount: Long): Long {
        val room = (Int.MAX_VALUE - target.totalOf(GeIds.coins)).coerceAtLeast(0)
        var accepted = 0L
        val asCoins = minOf(amount, room)
        if (asCoins > 0) {
            accepted += add(GeIds.coins, asCoins.toInt())
        }
        val tokens = minOf((amount - accepted) / TOKEN_VALUE, Int.MAX_VALUE.toLong())
        if (tokens > 0) {
            accepted += add(GeIds.platinum, tokens.toInt()) * TOKEN_VALUE
        }
        return accepted
    }

    private fun add(objId: Int, count: Int): Long {
        val inv = target
        val before = inv.totalOf(objId)
        player.invAdd(inv, objId, count, strict = false)
        return inv.totalOf(objId) - before
    }

    private companion object {
        const val TOKEN_VALUE = 1000L
    }
}

/** Collection rules shared by the offers window and the collection box interface. */
@Singleton
internal class GeCollector
@Inject
constructor(
    private val exchange: GrandExchange,
    private val sessions: GeSessions,
    private val catalog: GeItemCatalog,
) {
    /** What the client shows for the item stack of a box, and which ops it offers. */
    enum class ItemOp {
        COLLECT_ITEMS,
        COLLECT_NOTES,
        BANK,
    }

    /** Collects the item of [slot]; returns whether anything was left behind. */
    fun collectItem(player: Player, slot: Int, op: ItemOp): Boolean {
        val gp = sessions.of(player)
        val sink = InventorySink(player, catalog, toBank = op == ItemOp.BANK)
        exchange.collect(gp, slot, CollectPart.ITEMS, op == ItemOp.COLLECT_NOTES, sink)
        return !gp.box(slot).isEmpty()
    }

    fun collectCoins(player: Player, slot: Int, toBank: Boolean): Boolean {
        val gp = sessions.of(player)
        exchange.collect(gp, slot, CollectPart.COINS, false, InventorySink(player, catalog, toBank))
        return !gp.box(slot).isEmpty()
    }

    /** Hands back everything in one slot's box, as notes where that keeps it to one stack. */
    fun collectSlot(player: Player, slot: Int, toBank: Boolean): Boolean {
        val gp = sessions.of(player)
        val box = gp.box(slot)
        val noted = !toBank && box.itemCount > 1 && catalog.notedId(box.itemId) != null
        exchange.collect(gp, slot, CollectPart.ALL, noted, InventorySink(player, catalog, toBank))
        return !box.isEmpty()
    }

    /** The "Collect to inventory" / "Collect to bank" buttons: every finished slot at once. */
    fun collectAll(player: Player, toBank: Boolean): Boolean {
        val gp = sessions.of(player)
        var leftBehind = false
        for (slot in 0 until SlotCodec.SLOTS) {
            if (gp.box(slot).isEmpty()) {
                continue
            }
            leftBehind = collectSlot(player, slot, toBank) || leftBehind
        }
        return leftBehind
    }

    /**
     * Handles a click on one of the two stacks the client draws for a slot: the item (when
     * [isItem]) or the coins.
     */
    fun collectDisplayed(access: ProtectedAccess, slot: Int, isItem: Boolean, op: IfButtonOp) {
        val player = access.player
        val session = sessions.of(player)
        if (op == IfButtonOp.Op10) {
            val shown = if (isItem) session.box(slot).itemId else GeIds.coins
            ServerCacheManager.getItem(shown)?.let { access.mes(it.examine) }
            return
        }
        val opNumber = op.ordinal + 1
        val left =
            if (isItem) {
                val itemOp = itemOpFor(player, slot, opNumber) ?: return
                collectItem(player, slot, itemOp)
            } else {
                when (opNumber) {
                    2 -> collectCoins(player, slot, toBank = false)
                    3 -> collectCoins(player, slot, toBank = true)
                    else -> return
                }
            }
        if (left) {
            access.mes("You don't have enough inventory space to collect everything.")
        }
    }

    /**
     * The ops of the item shown for [slot] in the order the client lists them: for an item that
     * has a noted form op 1 is "notes" (or "item" for a single one) and op 2 the other way round;
     * otherwise only op 2, plain "Collect", exists.
     */
    fun itemOpFor(player: Player, slot: Int, op: Int): ItemOp? {
        val box = sessions.of(player).box(slot)
        if (box.itemCount == 0) {
            return null
        }
        val certable = catalog.notedId(box.itemId) != null
        return when (op) {
            1 -> if (!certable) null else if (box.itemCount == 1) ItemOp.COLLECT_ITEMS else ItemOp.COLLECT_NOTES
            2 ->
                if (!certable) ItemOp.COLLECT_ITEMS
                else if (box.itemCount == 1) ItemOp.COLLECT_NOTES
                else ItemOp.COLLECT_ITEMS
            3 -> ItemOp.BANK
            else -> null
        }
    }
}
