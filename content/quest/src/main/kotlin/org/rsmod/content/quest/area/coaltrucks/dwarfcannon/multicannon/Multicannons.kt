package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon

import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Every dwarf multicannon standing in the world, keyed by its owner's account.
 *
 * The client reads the owner's side from varps: `varp.dropcannon` is the assembly stage (4 once
 * the furnace is on), `varp.ownedmcannon` and `varp.ownedmcannon_temp` the cannon's south-west
 * tile, `varp.rockthrower` the balls loaded and `varbit.mcannon_decayed` whether it has been lost.
 * Those are saved with the player; this registry is not, so after a restart or a decay the varps
 * outlive the cannon and the player has to reclaim it from Nulodion.
 */
@Singleton
class Multicannons {
    private val cannons = HashMap<Long, Cannon>()
    private var nextSerial = 0

    fun of(player: Player): Cannon? = cannons[player.accountHash]

    fun all(): Collection<Cannon> = cannons.values

    fun place(player: Player, origin: CoordGrid, style: CannonStyle): Cannon {
        val cannon = Cannon(player.accountHash, origin, style, ++nextSerial)
        cannons[player.accountHash] = cannon
        return cannon
    }

    fun ownedBy(player: Player, origin: CoordGrid): Cannon? =
        of(player)?.takeIf { it.origin == origin }

    fun remove(cannon: Cannon) {
        if (cannons[cannon.owner] === cannon) {
            cannons.remove(cannon.owner)
        }
    }

    fun isCurrent(cannon: Cannon): Boolean = cannons[cannon.owner] === cannon

    class Cannon(val owner: Long, val origin: CoordGrid, val style: CannonStyle, val serial: Int) {
        var stage: Int = STAGE_NONE
        var broken: Boolean = false
        var direction: Int = 0
        var firing: Boolean = false

        val centre: CoordGrid
            get() = origin.translate(1, 1)
    }

    companion object {
        const val STAGE_NONE = 0
        const val STAGE_BASE = 1
        const val STAGE_STAND = 2
        const val STAGE_BARRELS = 3
        const val STAGE_FULL = 4

        const val STEEL_BALL = "obj.mcannonball"
        const val GRANITE_BALL = "obj.granite_cannonball"

        /** A cannon runs for 25 minutes before it breaks down. */
        const val BREAK_TICKS = 2500

        /** An unrepaired cannon falls apart ten minutes after breaking. */
        const val DECAY_TICKS = 1000

        const val ROTATE_TIMER = "timer.dwarf_cannon_rotate"
    }
}

/**
 * The plain dwarf multicannon and its Shattered Relics ornamented twin. Each part and each stage
 * of the standing cannon has its own item and loc; the two sets cannot be mixed.
 */
enum class CannonStyle(
    val parts: List<String>,
    val stageLocs: List<String>,
    val brokenLoc: String,
    val steelSpotanim: String,
    val graniteSpotanim: String,
) {
    Normal(
        parts = listOf("obj.twpart1", "obj.twpart2", "obj.twpart3", "obj.twpart4"),
        stageLocs =
            listOf(
                "loc.multicannon_base",
                "loc.multicannon_stand",
                "loc.multicannon_barrels",
                "loc.dwarf_multicannon1",
            ),
        brokenLoc = "loc.dwarf_multicannon1_broken",
        steelSpotanim = "spotanim.cannonball_travel",
        graniteSpotanim = "spotanim.cannonball_travel_granite",
    ),
    Ornate(
        parts =
            listOf(
                "obj.league_3_multicannon_base",
                "obj.league_3_multicannon_stand",
                "obj.league_3_multicannon_barrels",
                "obj.league_3_multicannon_furnace",
            ),
        stageLocs =
            listOf(
                "loc.league03_multicannon_base",
                "loc.league03_multicannon_stand",
                "loc.league03_multicannon_barrels",
                "loc.league03_multicannon_active",
            ),
        brokenLoc = "loc.league03_multicannon_broken",
        steelSpotanim = "spotanim.cannonball_travel_league",
        graniteSpotanim = "spotanim.cannonball_travel_granite_league",
    );

    val base: String
        get() = parts[0]

    val cannonLoc: String
        get() = stageLocs.last()
}

internal var Player.cannonStage by intVarp("varp.dropcannon")
internal var Player.cannonBalls by intVarp("varp.rockthrower")
internal var Player.cannonBallType by intVarBit("varbit.mcannon_balltype")
internal var Player.cannonDecayed by boolVarBit("varbit.mcannon_decayed")
private var Player.cannonOrnate by boolVarBit("varbit.dwarf_cannon_ornate")
private var Player.ownedCannonCoord by intVarp("varp.ownedmcannon")
private var Player.ownedCannonHud by intVarp("varp.ownedmcannon_temp")

internal const val BALL_STEEL = 1
internal const val BALL_GRANITE = 2

internal var Player.cannonOrigin: CoordGrid?
    get() = ownedCannonCoord.takeIf { it > 0 }?.let(::CoordGrid)
    set(value) {
        val packed = value?.packed ?: 0
        ownedCannonCoord = packed
        ownedCannonHud = packed
    }

/** The style of the cannon the player owns, kept so Nulodion returns the right parts. */
internal var Player.cannonStyle: CannonStyle
    get() = if (cannonOrnate) CannonStyle.Ornate else CannonStyle.Normal
    set(value) {
        cannonOrnate = value == CannonStyle.Ornate
    }

/** 30 balls, raised to 35, 45 and 60 by the medium, hard and elite Combat Achievement tiers. */
internal val Player.cannonCapacity: Int
    get() =
        when {
            vars["varbit.ca_tier_status_elite"] != 0 -> 60
            vars["varbit.ca_tier_status_hard"] != 0 -> 45
            vars["varbit.ca_tier_status_medium"] != 0 -> 35
            else -> 30
        }

internal fun Player.clearCannonVars() {
    cannonOrigin = null
    cannonStage = Multicannons.STAGE_NONE
    cannonBalls = 0
    cannonDecayed = false
}

/** The cannon fell apart or was destroyed: leave the vars so Nulodion can replace it. */
internal fun Player.markCannonLost(
    message: String = "Your cannon has decayed. Speak to Nulodion to obtain a new one.",
) {
    if (cannonStage == Multicannons.STAGE_NONE || cannonDecayed) {
        return
    }
    cannonDecayed = true
    cannonBalls = 0
    ownedCannonHud = 0
    mes(message)
}

internal fun ballObj(type: Int): String =
    if (type == BALL_GRANITE) Multicannons.GRANITE_BALL else Multicannons.STEEL_BALL
