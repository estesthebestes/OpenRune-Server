package org.rsmod.content.other.worldreload.types

import dev.openrune.definition.EntityOpsDefinition
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import kotlin.reflect.KMutableProperty1

/**
 * Which fields of a server type may be copied onto the live instance, and which only matter to
 * the client (or to boot-time collision) and therefore need a cache rebuild plus restart.
 */
internal class TypeFieldPolicy<T : Any>(
    val copy: List<KMutableProperty1<T, *>>,
    val restartOnly: List<Pair<String, (T) -> Any?>>,
) {
    fun diff(live: T, fresh: T): TypeDelta<T>? {
        val changed = copy.filter { it.get(live) != it.get(fresh) }
        val visible = restartOnly.filter { (_, getter) -> getter(live) != getter(fresh) }
        if (changed.isEmpty() && visible.isEmpty()) {
            return null
        }
        return TypeDelta(live, fresh, changed, visible.map { it.first })
    }
}

internal class TypeDelta<T : Any>(
    val live: T,
    val fresh: T,
    val changed: List<KMutableProperty1<T, *>>,
    val restartOnly: List<String>,
) {
    val changedNames: Set<String>
        get() = changed.mapTo(linkedSetOf()) { it.name }

    @Suppress("UNCHECKED_CAST")
    fun apply() {
        for (property in changed) {
            (property as KMutableProperty1<T, Any?>).set(live, property.get(fresh))
        }
    }
}

private fun EntityOpsDefinition.texts(): List<String?> = (0 until 5).map { getOpOrNull(it) }

internal val NPC_POLICY =
    TypeFieldPolicy<NpcServerType>(
        copy =
            listOf(
                NpcServerType::attack,
                NpcServerType::defence,
                NpcServerType::strength,
                NpcServerType::hitpoints,
                NpcServerType::ranged,
                NpcServerType::magic,
                NpcServerType::timer,
                NpcServerType::respawnDir,
                NpcServerType::waypoints,
                NpcServerType::patrol,
                NpcServerType::contentGroup,
                NpcServerType::heroCount,
                NpcServerType::regenRate,
                NpcServerType::moveRestrict,
                NpcServerType::defaultMode,
                NpcServerType::respawnRate,
                NpcServerType::examine,
                NpcServerType::maxRange,
                NpcServerType::wanderRange,
                NpcServerType::attackRange,
                NpcServerType::huntRange,
                NpcServerType::huntMode,
                NpcServerType::giveChase,
                NpcServerType::category,
                NpcServerType::paramsRaw,
                NpcServerType::paramMap,
            ),
        restartOnly =
            listOf(
                "name" to NpcServerType::name,
                "size" to NpcServerType::size,
                "combat level" to NpcServerType::combatLevel,
                "options" to { it.actions.texts() },
                "transforms" to NpcServerType::transforms,
                "multi npc" to { listOf(it.multiVarBit, it.multiVarp, it.multiDefault) },
                "block walk" to NpcServerType::blockWalk,
                "interactable" to NpcServerType::isInteractable,
                "animations" to { listOf(it.standAnim, it.walkAnim, it.runSequence) },
            ),
    )

internal val ITEM_POLICY =
    TypeFieldPolicy<ItemServerType>(
        copy =
            listOf(
                ItemServerType::cost,
                ItemServerType::weight,
                ItemServerType::stockmarket,
                ItemServerType::tradeable,
                ItemServerType::objvar,
                ItemServerType::playerCost,
                ItemServerType::playerCostDerived,
                ItemServerType::playerCostDerivedConst,
                ItemServerType::stockMarketBuyLimit,
                ItemServerType::stockMarketRecalcUsers,
                ItemServerType::respawnRate,
                ItemServerType::dummyitem,
                ItemServerType::examine,
                ItemServerType::category,
                ItemServerType::contentGroup,
                ItemServerType::weaponCategory,
                ItemServerType::paramsRaw,
                ItemServerType::paramMap,
            ),
        restartOnly =
            listOf(
                "name" to ItemServerType::name,
                "options" to { it.options.texts() },
                "inventory options" to ItemServerType::interfaceOptions,
                "cert/placeholder links" to {
                    listOf(it.certlink, it.certtemplate, it.placeholderLink, it.placeholderTemplate)
                },
                "stacking" to ItemServerType::stacks,
                "wear positions" to { listOf(it.wearpos1, it.wearpos2, it.wearpos3) },
                "transform links" to { listOf(it.transformlink, it.transformtemplate) },
                "count variants" to { listOf(it.countCo, it.countObj) },
            ),
    )

internal val LOC_POLICY =
    TypeFieldPolicy<ObjectServerType>(
        copy =
            listOf(
                ObjectServerType::paramsRaw,
                ObjectServerType::paramMap,
                ObjectServerType::desc,
                ObjectServerType::category,
                ObjectServerType::contentGroup,
            ),
        restartOnly =
            listOf(
                "name" to ObjectServerType::name,
                "size" to { listOf(it.width, it.length) },
                "collision" to {
                    listOf(it.blockWalk, it.blockRange, it.breakRouteFinding, it.forceApproachFlags)
                },
                "options" to { it.actions.texts() },
                "transforms" to { listOf(it.transforms, it.multiVarBit, it.multiVarp, it.multiDefault) },
            ),
    )
