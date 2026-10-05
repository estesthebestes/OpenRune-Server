package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon

import dev.openrune.rscm.RSCM
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.mapMultiway
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Cannon
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.ROTATE_TIMER
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.STAGE_FULL
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The multicannon's firing loop, run as a soft timer on its owner every tick.
 *
 * The barrel steps clockwise one of eight directions per tick, starting north. Facing each
 * direction it looks for a target near three points along that line (3, 7 and 14 squares out,
 * searching 1, 2 and 5 squares around each), takes the first one it can see from its centre, and
 * fires one ball at it. Only an npc's south-west square counts, as in the original.
 *
 * Cannonballs hit for up to 30 (35 for granite) regardless of the player's gear, but the accuracy
 * roll is the player's own: ranged accuracy with a ranged weapon, melee accuracy otherwise, against
 * the target's heavy ranged defence. Damage gives 2 Ranged xp per point. In single-way combat the
 * cannon only fires at the npc attacking its owner, if any, and never at another player's fight.
 */
class MulticannonFiring
@Inject
constructor(
    private val cannons: Multicannons,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val npcList: NpcList,
    private val worldRepo: WorldRepository,
    private val accuracy: AccuracyFormulae,
    private val attackTypes: AttackTypes,
    private val attackStyles: AttackStyles,
    private val npcHitModifier: NpcHitModifier,
    private val areaChecker: AreaChecker,
    private val random: GameRandom,
    collision: CollisionFlagMap,
) : PluginScript() {

    private val rayCast = RayCastValidator(collision)
    private val cannonIds by lazy { CannonStyle.entries.associateWith { RSCM.getRSCM(it.cannonLoc) } }
    private val destroyerIds by lazy { CANNON_DESTROYERS.map(RSCM::getRSCM).toSet() }

    override fun ScriptContext.startup() {
        onPlayerSoftTimer(ROTATE_TIMER) { rotate(player) }
    }

    private fun rotate(player: Player) {
        val cannon = cannons.of(player)
        if (cannon == null || cannon.stage != STAGE_FULL || cannon.broken || !cannon.firing) {
            stop(player, cannon)
            return
        }
        val loc = locRepo.findExact(cannon.origin, LocShape.CentrepieceStraight)
        if (loc == null || loc.id != cannonIds.getValue(cannon.style)) {
            stop(player, cannon)
            return
        }
        if (player.coords.level != cannon.origin.level || !player.isWithinDistance(cannon.centre, OWNER_RANGE)) {
            stop(player, cannon)
            return
        }
        if (player.cannonBalls < 1) {
            player.mes("Your cannon is out of ammo!")
            stop(player, cannon)
            return
        }
        val direction = cannon.direction
        cannon.direction = (direction + 1) % DIRECTIONS.size
        worldRepo.locAnim(loc, DIRECTIONS[direction].turnSeq)
        val target = findTarget(player, cannon, DIRECTIONS[direction]) ?: return
        fireAt(player, cannon, target)
    }

    private fun stop(player: Player, cannon: Cannon?) {
        cannon?.firing = false
        player.clearSoftTimer(ROTATE_TIMER)
    }

    private fun findTarget(player: Player, cannon: Cannon, direction: Direction): Npc? {
        val centre = cannon.centre
        val candidates =
            npcRepo.findAll(ZoneKey.from(centre), SEARCH_ZONE_RADIUS)
                .filter { canTarget(player, it) }
                .toList()
        if (candidates.isEmpty()) {
            return null
        }
        val lockedOn = singleWayOpponent(player)
        for ((distance, radius) in RANGES) {
            val point = centre.translate(direction.dx * distance.scale(direction), direction.dz * distance.scale(direction))
            val target =
                candidates
                    .filter { lockedOn == null || it === lockedOn }
                    .filter { it.coords.chebyshev(point) <= radius }
                    .filter { rayCast.hasLineOfSight(centre, it.coords) }
                    .minByOrNull { it.coords.chebyshev(point) }
            if (target != null) {
                return target
            }
        }
        return null
    }

    private fun canTarget(player: Player, npc: Npc): Boolean {
        if (!npc.isValidTarget() || npc.coords.level != player.coords.level) {
            return false
        }
        if (npc.visType.actions.getOpOrNull(1) != "Attack") {
            return false
        }
        if (npc.visType.paramOrNull(params.cannon_immunity) == 1) {
            return false
        }
        if (npc.mapMultiway(areaChecker)) {
            return true
        }
        val fighting = npc.aggressivePlayer
        val recent = npc.lastCombat + constants.combat_activecombat_delay > npc.currentMapClock
        return !recent || fighting == null || fighting == player.uid
    }

    /** In single-way combat the npc currently attacking the owner, when there is one. */
    private fun singleWayOpponent(player: Player): Npc? {
        val npc = player.aggressiveNpc?.resolve(npcList) ?: return null
        if (npc.mapMultiway(areaChecker) || npc.aggressivePlayer != player.uid) {
            return null
        }
        val recent = npc.lastCombat + constants.combat_activecombat_delay > npc.currentMapClock
        return npc.takeIf { recent && it.isValidTarget() }
    }

    private fun fireAt(player: Player, cannon: Cannon, target: Npc) {
        val granite = player.cannonBallType == BALL_GRANITE
        player.cannonBalls -= 1
        val centre = cannon.centre
        val distance = centre.chebyshev(target.coords)
        val proj =
            ProjAnim(
                spotanim = RSCM.getRSCM(if (granite) cannon.style.graniteSpotanim else cannon.style.steelSpotanim),
                startHeight = PROJ_START_HEIGHT,
                endHeight = PROJ_END_HEIGHT,
                startTime = 0,
                endTime = PROJ_LENGTH + PROJ_STEP * distance,
                angle = PROJ_ANGLE,
                progress = PROJ_PROGRESS,
                sourceIndex = 0,
                targetIndex = target.slotId + 1,
                startCoord = centre,
                endCoord = target.coords,
            )
        worldRepo.projAnim(proj)
        worldRepo.soundArea(centre, FIRE_SOUND, radius = SOUND_RADIUS)
        val hitDelay = maxOf(1, proj.endTime / CLIENT_CYCLES_PER_TICK)
        val damage =
            if (rollAccuracy(player, target)) random.of(0..(if (granite) GRANITE_MAX_HIT else STEEL_MAX_HIT)) else 0
        val dealt = minOf(damage, target.hitpoints)
        target.queueHit(player, hitDelay, HitType.Ranged, damage, npcHitModifier)
        target.queueCombatRetaliate(player, hitDelay)
        if (dealt > 0) {
            player.statAdvance("stat.ranged", dealt * XP_PER_DAMAGE)
        }
        if (destroysCannons(target) && random.of(DESTROY_ODDS) == 0) {
            destroy(player, cannon)
        }
    }

    /** Bosses that smash a cannon firing at them, as the King Black Dragon and Kalphite Queen do. */
    private fun destroysCannons(npc: Npc): Boolean =
        npc.type.id in destroyerIds || areaChecker.inArea(GOD_WARS, npc.coords)

    private fun destroy(player: Player, cannon: Cannon) {
        val loc = locRepo.findExact(cannon.origin, LocShape.CentrepieceStraight) ?: return
        worldRepo.spotanimMap(SpotanimType(RSCM.getRSCM(DESTROY_SPOTANIM)), cannon.centre, DESTROY_SPOTANIM_HEIGHT)
        locRepo.del(loc, Int.MAX_VALUE)
        stop(player, cannon)
        cannons.remove(cannon)
        player.markCannonLost("Your cannon has been destroyed!")
    }

    private fun rollAccuracy(player: Player, target: Npc): Boolean {
        val type = attackTypes.get(player)
        val style = attackStyles.get(player)
        return if (type?.isRanged == true) {
            accuracy.rollRangedAccuracy(
                player = player,
                target = target,
                attackType = RangedAttackType.from(type),
                attackStyle = RangedAttackStyle.from(style),
                blockType = RangedAttackType.Heavy,
                specMultiplier = 1.0,
                random = random,
            )
        } else {
            val meleeType = MeleeAttackType.from(type)
            accuracy.rollMeleeAccuracy(
                player = player,
                target = target,
                attackType = meleeType,
                attackStyle = MeleeAttackStyle.from(style),
                blockType = meleeType,
                specMultiplier = 1.0,
                random = random,
            )
        }
    }

    private fun CoordGrid.chebyshev(other: CoordGrid): Int = maxOf(abs(x - other.x), abs(z - other.z))

    /** Diagonal search points sit a little closer in (2, 5 and 12 squares along each axis). */
    private fun Int.scale(direction: Direction): Int =
        if (direction.dx == 0 || direction.dz == 0) this else DIAGONAL_DISTANCES.getValue(this)

    private class Direction(val dx: Int, val dz: Int, val turnSeq: String)

    private companion object {
        private val Npc.lastCombat: Int by intVarn("varn.lastcombat")
        private val Npc.aggressivePlayer by typePlayerUidVarn("varn.aggressive_player")
        private val Player.aggressiveNpc by typeNpcUidVarp("varp.aggressive_npc")

        /** Facing north first, clockwise; each step plays the turn into the next direction. */
        val DIRECTIONS =
            listOf(
                Direction(0, 1, "seq.mcannon_ne_turn"),
                Direction(1, 1, "seq.mcannon_e_turn"),
                Direction(1, 0, "seq.mcannon_se_turn"),
                Direction(1, -1, "seq.mcannon_s_turn"),
                Direction(0, -1, "seq.mcannon_sw_turn"),
                Direction(-1, -1, "seq.mcannon_w_turn"),
                Direction(-1, 0, "seq.mcannon_nw_turn"),
                Direction(-1, 1, "seq.mcannon_n_turn"),
            )

        /** Search points along the barrel and the radius searched around each. */
        val RANGES = listOf(3 to 1, 7 to 2, 14 to 5)
        val DIAGONAL_DISTANCES = mapOf(3 to 2, 7 to 5, 14 to 12)

        const val SEARCH_ZONE_RADIUS = 3
        const val OWNER_RANGE = 32

        const val STEEL_MAX_HIT = 30
        const val GRANITE_MAX_HIT = 35
        const val XP_PER_DAMAGE = 2.0

        const val FIRE_SOUND = "synth.mcannon_fire"
        const val SOUND_RADIUS = 10

        const val PROJ_START_HEIGHT = 144
        const val PROJ_END_HEIGHT = 140
        const val PROJ_ANGLE = 2
        const val PROJ_PROGRESS = 11
        const val PROJ_LENGTH = 35
        const val PROJ_STEP = 5
        const val CLIENT_CYCLES_PER_TICK = 30

        const val DESTROY_ODDS = 4
        const val DESTROY_SPOTANIM = "spotanim.smokepuff_huge"
        const val DESTROY_SPOTANIM_HEIGHT = 200
        const val GOD_WARS = "area.godwars_dungeon"
        val CANNON_DESTROYERS =
            listOf(
                "npc.king_dragon",
                "npc.kalphite_queen",
                "npc.kalphite_flyingqueen",
                "npc.smoke_devil_boss",
                "npc.myarm_giant_roc",
            )
    }
}
