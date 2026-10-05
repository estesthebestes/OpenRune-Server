package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.EnumType
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The clerks' item set window. The client builds the list of sets and greys out the ones the
 * backpack can't fill from the cache enums, so the server only has to do the swap when a card or
 * a set in the backpack is clicked.
 */
@Singleton
internal class GeItemSets @Inject constructor() {
    private val catalog: EnumType
        get() = enum(SETS)

    private val bySet: EnumType
        get() = enum(SETS_BY_OBJ)

    val setCount: Int
        get() = catalog.values.size

    fun open(access: ProtectedAccess) {
        access.invTransmit(access.inv)
        access.ifOpenMainSidePair(main = "interface.itemsets", side = "interface.itemsets_side")
        access.ifSetEvents(LIST, 0 until setCount, IfEvent.Op1, IfEvent.Op10)
        access.ifSetEvents(SIDE_ITEMS, access.inv.indices, IfEvent.Op1, IfEvent.Op10)
    }

    fun pack(access: ProtectedAccess, index: Int, op: IfButtonOp) {
        val setId = catalog.values[index] as? Int ?: return
        val set = enum(setId)
        val setType = ServerCacheManager.getItem(set.values[SET_KEY] as? Int ?: return) ?: return
        if (op == IfButtonOp.Op10) {
            access.mes(setType.examine)
            return
        }
        if (access.player.isUltimateIronman) {
            access.mes("Ultimate ironmen cannot assemble item sets.")
            return
        }
        val parts = parts(set)
        val inv = access.inv
        val held = parts.keys.all { inv.totalOf(it) > 0 || inv.totalOf(notedId(it)) > 0 }
        if (!held) {
            access.mes("${setType.name} contains: ${contents(parts)}.")
            return
        }
        val results =
            access.player.invTransaction(inv, autoCommit = false) {
                val target = select(inv)
                for ((part, count) in parts) {
                    val plain = minOf(inv.totalOf(part), count.toLong()).toInt()
                    if (plain > 0) {
                        delete(target, part, plain)
                    }
                    if (plain < count) {
                        delete(target, notedId(part), count - plain)
                    }
                }
                add(target, setType.id, 1)
            }
        if (results.success) {
            results.commitAll()
        } else {
            access.mes("You don't have enough inventory space.")
        }
    }

    fun unpack(access: ProtectedAccess, slot: Int, op: IfButtonOp) {
        val obj = access.inv[slot] ?: return
        val type = ServerCacheManager.getItem(obj.id) ?: return
        if (op == IfButtonOp.Op10) {
            access.mes(type.examine)
            return
        }
        val noted = type.isCert
        val base = if (noted) type.certlink else type.id
        val setId = bySet.values[base] as? Int ?: return
        val parts = parts(enum(setId))
        val inv = access.inv
        val results =
            access.player.invTransaction(inv, autoCommit = false) {
                val target = select(inv)
                delete(target, obj.id, 1, slot = slot)
                for ((part, count) in parts) {
                    add(target, part, count, cert = noted)
                }
            }
        if (results.success) {
            results.commitAll()
        } else {
            access.mes("You don't have enough inventory space.")
        }
    }

    private fun parts(set: EnumType): Map<Int, Int> {
        val counts = LinkedHashMap<Int, Int>()
        for (key in set.values.keys.filter { it >= 0 }.sorted()) {
            val part = set.values[key] as? Int ?: continue
            counts.merge(part, 1, Int::plus)
        }
        return counts
    }

    private fun contents(parts: Map<Int, Int>): String =
        parts.entries.joinToString(", ") { (part, count) ->
            val name = ServerCacheManager.getItem(part)?.name ?: "?"
            if (count > 1) "$count x $name" else name
        }

    private fun notedId(obj: Int): Int {
        val type = ServerCacheManager.getItem(obj) ?: return -1
        return if (type.canCert) type.certlink else -1
    }

    private fun enum(id: Int): EnumType =
        checkNotNull(ServerCacheManager.getEnum(id)) { "Enum $id not found" }

    private fun enum(internal: String): EnumType = enum(internal.asRSCM(RSCMType.ENUM))

    companion object {
        const val SET_KEY = -1
        const val SETS = "enum.ge_item_sets"
        const val SETS_BY_OBJ = "enum.ge_item_sets_by_obj"
        const val LIST = "component.itemsets:itemlist"
        const val SIDE_ITEMS = "component.itemsets_side:items"
    }
}

internal class GeItemSetsScript @Inject constructor(private val itemSets: GeItemSets) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfModalButton(GeItemSets.LIST) { itemSets.pack(this, it.comsub, it.op) }
        onIfModalButton(GeItemSets.SIDE_ITEMS) { itemSets.unpack(this, it.comsub, it.op) }
    }
}
