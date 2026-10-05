package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon

import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Cannon
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.BREAK_TICKS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.DECAY_TICKS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.GRANITE_BALL
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.ROTATE_TIMER
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.STAGE_BARRELS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.STAGE_BASE
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.STAGE_FULL
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.STAGE_NONE
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.STAGE_STAND
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons.Companion.STEEL_BALL
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Setting up, loading, emptying, repairing and picking up the dwarf multicannon.
 *
 * Set-up on the base stands the player back two squares and assembles as many parts as they
 * carry, in order; a missing part can be added later by using it on the half-built cannon. Once
 * the furnace is on, the cannon runs for [BREAK_TICKS] before breaking down, and falls apart
 * [DECAY_TICKS] after that unless repaired, after which Nulodion hands out a replacement.
 */
class MulticannonScript
@Inject
constructor(
    private val cannons: Multicannons,
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
    private val playerList: PlayerList,
    private val restrictions: CannonRestrictions,
) : PluginScript() {

    private val rayCast by lazy { RayCastValidator(collision) }
    private val cannonIds by lazy { CannonStyle.entries.map { RSCM.getRSCM(it.cannonLoc) }.toSet() }

    override fun ScriptContext.startup() {
        for (style in CannonStyle.entries) {
            register(style)
        }

        onPlayerLogin { checkCannonOnLogin(player) }
        onPlayerLogout { cannons.of(player)?.firing = false }
    }

    private fun ScriptContext.register(style: CannonStyle) {
        val (base, stand, barrels) = style.stageLocs
        val cannon = style.cannonLoc
        onOpHeld1(style.base) { setUp(style) }

        onOpLoc1(base) { pickUp(it.loc) }
        onOpLoc1(stand) { pickUp(it.loc) }
        onOpLoc1(barrels) { pickUp(it.loc) }
        onOpLocU(base, style.parts[STAGE_STAND - 1]) { addPart(it.loc, STAGE_STAND) }
        onOpLocU(stand, style.parts[STAGE_BARRELS - 1]) { addPart(it.loc, STAGE_BARRELS) }
        onOpLocU(barrels, style.parts[STAGE_FULL - 1]) { addPart(it.loc, STAGE_FULL) }

        onOpLoc1(cannon) { fire(it.loc) }
        onOpLoc2(cannon) { pickUp(it.loc) }
        onOpLoc3(cannon) { empty(it.loc) }
        onOpLoc4(cannon) { loadX(it.loc) }
        onOpLocU(cannon, STEEL_BALL) { loadFrom(it.loc, BALL_STEEL) }
        onOpLocU(cannon, GRANITE_BALL) { loadFrom(it.loc, BALL_GRANITE) }

        onOpLoc1(style.brokenLoc) { repair(it.loc) }
        onOpLoc2(style.brokenLoc) { pickUp(it.loc) }
    }

    /* Setting up */

    private suspend fun ProtectedAccess.setUp(style: CannonStyle) {
        if (!QuestRequirements.hasCompleted(player, DwarfCannonQuest.QUEST_KEY)) {
            mes("You can't set up this cannon.")
            mes("You need to complete the Dwarf Cannon quest.")
            return
        }
        if (player.cannonStage != STAGE_NONE) {
            mes("You cannot construct more than one Cannon at a time.")
            mes("If you have lost your Cannon, go and see the Dwarf Cannon engineer.")
            return
        }
        val centre = player.coords
        restrictionMessage(centre)?.let {
            mes(it)
            return
        }
        if (!hasRoom(centre)) {
            mes("There isn't enough space to set up here.")
            return
        }
        val standOff = standOffSquare(centre)
        if (standOff == null) {
            mes("There isn't enough space to set up here.")
            return
        }
        playerWalk(standOff)
        faceSquare(centre)
        delay(1)
        if (player.cannonStage != STAGE_NONE || style.base !in inv || !hasRoom(centre)) {
            mes("There isn't enough space to set up here.")
            return
        }
        val origin = centre.translate(-1, -1)
        val cannon = cannons.place(player, origin, style)
        player.cannonOrigin = origin
        player.cannonStyle = style
        player.cannonDecayed = false
        if (!assemble(cannon, STAGE_BASE)) {
            return
        }
        for (stage in STAGE_STAND..STAGE_FULL) {
            if (style.parts[stage - 1] !in inv) {
                return
            }
            delay(ASSEMBLE_TICKS)
            if (!cannons.isCurrent(cannon) || cannon.stage != stage - 1) {
                return
            }
            assemble(cannon, stage)
        }
    }

    private fun ProtectedAccess.assemble(cannon: Cannon, stage: Int): Boolean {
        if (invDel(inv, cannon.style.parts[stage - 1]).failure) {
            return false
        }
        anim(ASSEMBLE_ANIM)
        soundSynth(SETUP_SOUND)
        cannon.stage = stage
        player.cannonStage = stage
        spawn(cannon, cannon.style.stageLocs[stage - 1])
        mes(ASSEMBLE_MESSAGES[stage - 1])
        return true
    }

    private suspend fun ProtectedAccess.addPart(loc: BoundLocInfo, stage: Int) {
        arriveDelay()
        val cannon = cannons.ownedBy(player, loc.coords)
        if (cannon == null) {
            mes("That isn't your cannon!")
            return
        }
        assemble(cannon, stage)
    }

    private fun spawn(cannon: Cannon, locType: String) {
        locRepo.add(cannon.origin, locType, BREAK_TICKS, LocAngle.West, LocShape.CentrepieceStraight) {
            breakDown(cannon)
        }
    }

    private fun restrictionMessage(centre: CoordGrid): String? = restrictions.message(centre)

    private fun hasRoom(centre: CoordGrid): Boolean {
        val origin = centre.translate(-1, -1)
        for (dx in 0..2) for (dz in 0..2) {
            val tile = origin.translate(dx, dz)
            if (collision[tile.x, tile.z, tile.level] and BLOCKING_FLAGS != 0) {
                return false
            }
        }
        if (locRepo.findExact(origin, LocShape.CentrepieceStraight) != null) {
            return false
        }
        val overlaps =
            cannons.all().any {
                it.origin.level == origin.level &&
                    abs(it.origin.x - origin.x) < 3 &&
                    abs(it.origin.z - origin.z) < 3
            }
        if (overlaps) {
            return false
        }
        return ACROSS.all { (a, b) -> rayCast.hasLineOfWalk(centre.translate(a.first, a.second), centre.translate(b.first, b.second)) }
    }

    /** A random square two back from the cannon that the player can walk to and from. */
    private fun ProtectedAccess.standOffSquare(centre: CoordGrid): CoordGrid? {
        repeat(STAND_OFF_ATTEMPTS) {
            val (dx, dz) = STAND_OFFSETS[random.of(STAND_OFFSETS.size)]
            val square = centre.translate(dx, dz)
            if (rayCast.hasLineOfWalk(square, centre) && rayCast.hasLineOfWalk(centre, square)) {
                return square
            }
        }
        return null
    }

    /* Ammunition */

    private suspend fun ProtectedAccess.fire(loc: BoundLocInfo) {
        arriveDelay()
        val cannon = cannons.ownedBy(player, loc.coords)
        if (cannon == null || cannon.stage != STAGE_FULL) {
            mes("That isn't your cannon!")
            return
        }
        val loaded = load(player.cannonCapacity)
        if (player.cannonBalls < 1) {
            mes("Your cannon is out of ammo!")
            return
        }
        if (cannon.firing) {
            if (loaded == 0) {
                mes("Your cannon is already firing.")
            }
            return
        }
        cannon.firing = true
        player.softTimer(ROTATE_TIMER, 1)
    }

    private suspend fun ProtectedAccess.loadFrom(loc: BoundLocInfo, ball: Int) {
        arriveDelay()
        val cannon = cannons.ownedBy(player, loc.coords)
        if (cannon == null) {
            mes("This is not your cannon.")
            return
        }
        if (player.cannonBalls >= player.cannonCapacity) {
            mes("Your cannon is already full.")
            return
        }
        load(player.cannonCapacity, ball)
    }

    private suspend fun ProtectedAccess.loadX(loc: BoundLocInfo) {
        arriveDelay()
        val cannon = cannons.ownedBy(player, loc.coords)
        if (cannon == null) {
            mes("This is not your cannon.")
            return
        }
        val amount = countDialog()
        if (amount > 0) {
            load(amount)
        }
    }

    /**
     * Loads up to [limit] balls from the inventory: the [preferred] kind when a ball was used on the
     * cannon, otherwise granite first. Steel and granite are never mixed: a cannon still holding one
     * kind only takes more of the same.
     * @return the number of balls loaded.
     */
    private fun ProtectedAccess.load(limit: Int, preferred: Int? = null): Int {
        val current = player.cannonBalls
        val space = minOf(player.cannonCapacity - current, limit)
        if (space <= 0) {
            return 0
        }
        val type =
            when {
                current > 0 -> player.cannonBallType
                preferred != null -> preferred
                GRANITE_BALL in inv -> BALL_GRANITE
                else -> BALL_STEEL
            }
        val obj = ballObj(type)
        val count = minOf(space, inv.count(obj))
        if (count <= 0) {
            if (current > 0 && (STEEL_BALL in inv || GRANITE_BALL in inv)) {
                mes("You can't mix different types of cannonball in your cannon.")
            }
            return 0
        }
        if (invDel(inv, obj, count).failure) {
            return 0
        }
        player.cannonBallType = type
        player.cannonBalls = current + count
        mes("You load the cannon with $count cannonballs.")
        return count
    }

    private suspend fun ProtectedAccess.empty(loc: BoundLocInfo) {
        arriveDelay()
        val cannon = cannons.ownedBy(player, loc.coords)
        if (cannon == null) {
            mes("This is not your cannon.")
            return
        }
        val count = player.cannonBalls
        if (count <= 0) {
            mes("Your cannon has no ammo in it.")
            return
        }
        val obj = ballObj(player.cannonBallType)
        if (invAdd(inv, obj, count).failure) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        player.cannonBalls = 0
        mes("You unload your cannon and receive $count cannonballs.")
    }

    /* Pick-up, repair and decay */

    private suspend fun ProtectedAccess.pickUp(loc: BoundLocInfo) {
        arriveDelay()
        val cannon = cannons.ownedBy(player, loc.coords)
        if (cannon == null) {
            mes(if (loc.id in cannonIds) "This is not your cannon." else "That isn't your cannon!")
            return
        }
        val parts = cannon.stage
        val ammo = ballObj(player.cannonBallType)
        val slots = parts + if (player.cannonBalls > 0 && ammo !in inv) 1 else 0
        if (inv.freeSpace() < slots) {
            mes(freeSpaceMessage(slots))
            return
        }
        cannon.firing = false
        player.clearSoftTimer(ROTATE_TIMER)
        cannons.remove(cannon)
        locRepo.del(loc, Int.MAX_VALUE)
        anim(ASSEMBLE_ANIM)
        for (part in cannon.style.parts.take(parts)) {
            invAdd(inv, part)
        }
        returnBalls()
        player.clearCannonVars()
        soundSynth(PICKUP_SOUND)
        mes("You pick up the cannon. It's really heavy.")
    }

    private fun ProtectedAccess.returnBalls() {
        val count = player.cannonBalls
        if (count <= 0) {
            return
        }
        if (invAdd(inv, ballObj(player.cannonBallType), count).success) {
            player.cannonBalls = 0
        }
    }

    private suspend fun ProtectedAccess.repair(loc: BoundLocInfo) {
        arriveDelay()
        val cannon = cannons.ownedBy(player, loc.coords)
        if (cannon == null || !cannon.broken) {
            mes("That isn't your cannon!")
            return
        }
        anim(ASSEMBLE_ANIM)
        soundSynth(SETUP_SOUND)
        cannon.broken = false
        spawn(cannon, cannon.style.cannonLoc)
        mes("You repair your cannon, restoring it to working order.")
    }

    private fun breakDown(cannon: Cannon) {
        if (!cannons.isCurrent(cannon)) {
            return
        }
        if (cannon.stage != STAGE_FULL || cannon.broken) {
            lose(cannon)
            return
        }
        cannon.broken = true
        cannon.firing = false
        locRepo.add(cannon.origin, cannon.style.brokenLoc, DECAY_TICKS, LocAngle.West, LocShape.CentrepieceStraight) {
            if (cannons.isCurrent(cannon) && cannon.broken) {
                lose(cannon)
            }
        }
        owner(cannon)?.mes("Your cannon has broken!")
    }

    private fun lose(cannon: Cannon) {
        cannons.remove(cannon)
        val owner = owner(cannon) ?: return
        owner.clearSoftTimer(ROTATE_TIMER)
        owner.markCannonLost()
    }

    private fun owner(cannon: Cannon): Player? = playerList.firstOrNull { it.accountHash == cannon.owner }

    private fun checkCannonOnLogin(player: Player) {
        if (player.cannonStage != STAGE_NONE && cannons.of(player) == null) {
            player.markCannonLost()
        }
    }

    private fun freeSpaceMessage(parts: Int): String =
        when (parts) {
            1 -> "You need one free inventory space to pick that up."
            2 -> "You need two free inventory spaces to pick that up."
            3 -> "You need three free inventory spaces to pick that up."
            else -> "You need $parts free inventory spaces to pick that up."
        }

    private companion object {
        const val ASSEMBLE_ANIM = "seq.human_pickupfloor"
        const val SETUP_SOUND = "synth.mcannon_setup"
        const val PICKUP_SOUND = "synth.pick"
        const val ASSEMBLE_TICKS = 2
        const val STAND_OFF_ATTEMPTS = 50

        val ASSEMBLE_MESSAGES =
            listOf(
                "You place the cannon base on the ground.",
                "You add the stand.",
                "You add the barrels.",
                "You add the furnace.",
            )

        const val BLOCKING_FLAGS = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** Opposite edges of the 3x3 footprint that must see each other across the centre. */
        val ACROSS =
            listOf(
                (0 to 1) to (0 to -1),
                (1 to 0) to (-1 to 0),
                (1 to 1) to (-1 to -1),
                (-1 to 1) to (1 to -1),
            )

        val STAND_OFFSETS =
            listOf(
                -2 to 2, -1 to 2, 0 to 2, 1 to 2, 2 to 2,
                2 to -2, 1 to -2, 0 to -2, -1 to -2, -2 to -2,
                -2 to -1, -2 to 0, -2 to 1, 2 to 1, 2 to 0, 2 to -1,
            )
    }
}
