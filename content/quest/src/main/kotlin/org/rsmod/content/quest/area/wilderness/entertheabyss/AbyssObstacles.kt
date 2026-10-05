package org.rsmod.content.quest.area.wilderness.entertheabyss

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * One of the twelve gaps between the Abyss's outer and inner rings. Every gap is a multiloc on
 * `varbit.rcu_abyssal_generator`: values 0-11 pick the layout (the blockage sits at gap `n` for
 * layout `n`), 12-19 are the shared "being cleared" frames that every obstacle briefly shows, and
 * the ground behind each gap is unwalkable, so passing one moves the player to [inside].
 */
internal enum class AbyssGate(val loc: String, val front: CoordGrid, val inside: CoordGrid) {
    South("loc.rcu_outer_multi1", CoordGrid(3042, 4810), CoordGrid(3040, 4817)),
    SouthWest("loc.rcu_outer_multi2", CoordGrid(3027, 4812), CoordGrid(3029, 4821)),
    WestSouth("loc.rcu_outer_multi3", CoordGrid(3017, 4822), CoordGrid(3029, 4821)),
    West("loc.rcu_outer_multi4", CoordGrid(3017, 4834), CoordGrid(3024, 4834)),
    WestNorth("loc.rcu_outer_multi5", CoordGrid(3020, 4843), CoordGrid(3027, 4840)),
    NorthWest("loc.rcu_outer_multi6", CoordGrid(3029, 4851), CoordGrid(3030, 4843)),
    North("loc.rcu_outer_multi7", CoordGrid(3039, 4855), CoordGrid(3038, 4846)),
    NorthEast("loc.rcu_outer_multi8", CoordGrid(3050, 4851), CoordGrid(3047, 4844)),
    EastNorth("loc.rcu_outer_multi9", CoordGrid(3060, 4840), CoordGrid(3050, 4840)),
    East("loc.rcu_outer_multi10", CoordGrid(3062, 4831), CoordGrid(3054, 4831)),
    EastSouth("loc.rcu_outer_multi11", CoordGrid(3059, 4822), CoordGrid(3050, 4822)),
    SouthEast("loc.rcu_outer_multi12", CoordGrid(3050, 4812), CoordGrid(3050, 4822)),
    ;

    /** What this gap shows in [layout]; null for the blockage and the in-progress frames. */
    fun obstacle(layout: Int): AbyssObstacle? {
        val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)) ?: return null
        val shown = type.transforms?.getOrNull(layout)?.takeIf { it != -1 } ?: return null
        return AbyssObstacle.byLoc[RSCM.getReverseMapping(RSCMType.LOC, shown)]
    }

    companion object {
        const val LAYOUT_VARBIT = "varbit.rcu_abyssal_generator"
        val LAYOUTS = entries.indices
    }
}

internal enum class AbyssObstacle(
    val shown: String,
    val stat: String?,
    val clearing: IntArray,
    val attempt: String?,
    val success: String?,
    val failure: String?,
) {
    Rock(
        shown = "loc.rcu_abyssal_barrier_teeth1",
        stat = "stat.mining",
        clearing = intArrayOf(12, 13),
        attempt = "You attempt to mine your way through...",
        success = "...and manage to break through the rock.",
        failure = "...but fail to break-up the rock.",
    ),
    Tendrils(
        shown = "loc.rcu_abyssal_barrier_tendrils1",
        stat = "stat.woodcutting",
        clearing = intArrayOf(14, 15),
        attempt = "You attempt to chop your way through...",
        success = "...and manage to cut your way through the tendrils.",
        failure = "...but fail to cut through the tendrils.",
    ),
    Boil(
        shown = "loc.rcu_abyssal_barrier_boil1",
        stat = "stat.firemaking",
        clearing = intArrayOf(16, 17),
        attempt = "You attempt to burn your way through...",
        success = "...and manage to burn it down and get past.",
        failure = "...but fail to set it on fire.",
    ),
    Eyes(
        shown = "loc.rcu_abyssal_barrier_eyes1",
        stat = "stat.thieving",
        clearing = intArrayOf(18, 19),
        attempt = "You use your thieving skills to misdirect the eyes...",
        success = "...and sneak past while they're not looking.",
        failure = "...but fail to distract the eyes.",
    ),
    Gap(
        shown = "loc.rcu_abyssal_barrier_agility",
        stat = "stat.agility",
        clearing = intArrayOf(),
        attempt = "You attempt to squeeze through the narrow gap...",
        success = "...and you manage to crawl through.",
        failure = "...but you are not agile enough to get through the gap.",
    ),
    Passage(
        shown = "loc.rcu_blankmodel",
        stat = null,
        clearing = intArrayOf(),
        attempt = null,
        success = null,
        failure = null,
    ),
    ;

    companion object {
        val byLoc = entries.associateBy { it.shown }
    }
}

/**
 * The outer-ring obstacles. Each attempt succeeds with a (level + 1) percent chance, so certain at 99,
 * with any usable tool working equally well, and grants 25 xp; the passage always lets
 * the player through.
 */
class AbyssObstacles : PluginScript() {

    override fun ScriptContext.startup() {
        for (gate in AbyssGate.entries) {
            onOpLoc1(gate.loc) { attempt(gate) }
        }
    }

    internal suspend fun ProtectedAccess.attempt(gate: AbyssGate) {
        val layout = vars[AbyssGate.LAYOUT_VARBIT]
        if (layout !in AbyssGate.LAYOUTS) {
            return
        }
        val obstacle = gate.obstacle(layout) ?: return
        if (obstacle == AbyssObstacle.Passage) {
            telejump(gate.inside)
            return
        }
        val seq = startAttempt(obstacle) ?: return
        anim(seq)
        obstacle.attempt?.let { mes(it) }
        delay(ATTEMPT_TICKS)
        val stat = checkNotNull(obstacle.stat)
        if (random.of(0 until PERCENT) >= player.stat(stat) + 1) {
            resetAnim()
            obstacle.failure?.let { mes(it) }
            return
        }
        try {
            for (frame in obstacle.clearing) {
                vars[AbyssGate.LAYOUT_VARBIT] = frame
                delay(1)
            }
            telejump(gate.inside)
        } finally {
            vars[AbyssGate.LAYOUT_VARBIT] = layout
        }
        resetAnim()
        obstacle.success?.let { mes(it) }
        statAdvance(stat, XP)
    }

    /** The animation for this attempt, or null (with the reason) when the tool is missing. */
    private fun ProtectedAccess.startAttempt(obstacle: AbyssObstacle): String? =
        when (obstacle) {
            AbyssObstacle.Rock -> {
                val pickaxe = bestPickaxe()
                if (pickaxe == null) {
                    mes("You need a pickaxe to mine this rock.")
                    null
                } else {
                    toolAnim(pickaxe, MINE_SEQ)
                }
            }
            AbyssObstacle.Tendrils -> {
                val axe = bestAxe()
                if (axe == null) {
                    mes("You need an axe to chop through these tendrils.")
                    null
                } else {
                    toolAnim(axe, CHOP_SEQ)
                }
            }
            AbyssObstacle.Boil -> {
                if (TINDERBOX !in inv) {
                    mes("You need a tinderbox to burn this boil.")
                    null
                } else {
                    BURN_SEQ
                }
            }
            AbyssObstacle.Eyes -> DISTRACT_SEQ
            AbyssObstacle.Gap -> SQUEEZE_SEQ
            AbyssObstacle.Passage -> null
        }

    private companion object {
        const val ATTEMPT_TICKS = 3
        const val XP = 25.0
        const val PERCENT = 100
        const val TINDERBOX = "obj.tinderbox"

        const val MINE_SEQ = "seq.human_mining_bronze_pickaxe"
        const val CHOP_SEQ = "seq.human_woodcutting_bronze_axe"
        const val BURN_SEQ = "seq.human_createfire"
        const val DISTRACT_SEQ = "seq.sanctuary"
        const val SQUEEZE_SEQ = "seq.human_crawling"
    }
}

private fun ProtectedAccess.bestAxe(): ItemServerType? =
    bestTool("content.woodcutting_axe", player.woodcuttingLvl)

private fun ProtectedAccess.bestPickaxe(): ItemServerType? =
    bestTool("content.mining_pickaxe", player.miningLvl)

private fun ProtectedAccess.bestTool(content: String, level: Int): ItemServerType? {
    val carried = inv.filterNotNull { true } + listOfNotNull(player.righthand)
    return carried
        .map { getInvObj(it) }
        .filter { it.isContentType(content) && level >= (it.paramOrNull(params.levelrequire) ?: 1) }
        .maxByOrNull { it.paramOrNull(params.levelrequire) ?: 1 }
}

private fun toolAnim(tool: ItemServerType, fallback: String): String {
    val seq = tool.paramOrNull(params.skill_anim) ?: return fallback
    return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
}
