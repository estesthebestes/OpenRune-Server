package org.rsmod.content.areas.zeah.doors

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KourendSceneryDoors @Inject constructor(private val locRepo: LocRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(OUTHOUSE_DOOR) { mes(OUTHOUSE_LOCKED) }
        onOpLoc1(VINERY_TOILET_DOOR) { occupied() }
        for (flap in TENT_FLAPS) {
            onOpLoc1(flap.closed) { toggleTentFlaps(it.loc.toLocInfo()) }
            onOpLoc1(flap.open) { toggleTentFlaps(it.loc.toLocInfo()) }
        }
        for (variant in CATTLE_GATE_VARIANTS) {
            onOpLoc1(variant) { openCattleGate(it.loc.toLocInfo()) }
        }
    }

    private suspend fun ProtectedAccess.occupied() {
        startDialogue { chatNpcSpecific(TOILET_MAN_TITLE, TOILET_MAN, angry, "Occupied!") }
    }

    private fun toggleTentFlaps(clicked: LocInfo) {
        swapTentFlap(clicked)
        val along = clicked.angle == LocAngle.West || clicked.angle == LocAngle.East
        val neighbours =
            if (along) {
                listOf(clicked.coords.translateZ(1), clicked.coords.translateZ(-1))
            } else {
                listOf(clicked.coords.translateX(1), clicked.coords.translateX(-1))
            }
        val partnerId = tentPartnerOf(clicked.id) ?: return
        val partner =
            neighbours.firstNotNullOfOrNull { tile ->
                locRepo.findExact(tile, clicked.shape)?.takeIf {
                    it.id == partnerId && it.angle == clicked.angle
                }
            } ?: return
        swapTentFlap(partner)
    }

    private fun swapTentFlap(flap: LocInfo) {
        val other = tentOtherStage(flap.id) ?: return
        locRepo.swap(flap, other, flap.coords, flap.angle, TENT_FLAP_TICKS)
    }

    private fun ProtectedAccess.openCattleGate(clicked: LocInfo) {
        locRepo.openGate(CATTLE_GATE, clicked, CATTLE_GATE_TICKS)
        soundSynth(GATE_OPEN_SOUND)
    }

    internal companion object {
        const val OUTHOUSE_DOOR = "loc.kore2_hos_door_inactive"
        const val OUTHOUSE_LOCKED = "I'm sure this door is locked for a reason. I'll leave it be."

        const val VINERY_TOILET_DOOR = "loc.hos_grape_odddoor"
        const val TOILET_MAN = "npc.hosidius_man_on_toilet"
        const val TOILET_MAN_TITLE = "Man"

        val TENT_FLAPS =
            listOf(
                Leaf("loc.mdaughter_tent_door", "loc.mdaughter_tent_door_open"),
                Leaf("loc.mdaughter_tent_doorl", "loc.mdaughter_tent_door_openl"),
            )
        const val TENT_FLAP_TICKS = 500

        /** The pen gate shows flour during Getting Ahead; both looks open into the plain gate. */
        val CATTLE_GATE =
            DoubleDoor(
                left = Leaf("loc.ga_fencegate_l", "loc.openfencegate_l"),
                right = Leaf("loc.ga_fencegate_r", "loc.openfencegate_r"),
            )
        val CATTLE_GATE_VARIANTS =
            listOf(
                "loc.ga_fencegate_l_normal",
                "loc.ga_fencegate_r_normal",
                "loc.ga_fencegate_l_flour",
                "loc.ga_fencegate_r_flour",
            )
        const val CATTLE_GATE_TICKS = 500
        const val GATE_OPEN_SOUND = "synth.picketgate_open"

        fun tentOtherStage(id: Int): String? =
            TENT_FLAPS.firstNotNullOfOrNull { flap ->
                when (id) {
                    flap.closed.asRSCM(RSCMType.LOC) -> flap.open
                    flap.open.asRSCM(RSCMType.LOC) -> flap.closed
                    else -> null
                }
            }

        fun tentPartnerOf(id: Int): Int? {
            val (right, left) = TENT_FLAPS
            return when (id) {
                right.closed.asRSCM(RSCMType.LOC) -> left.closed.asRSCM(RSCMType.LOC)
                right.open.asRSCM(RSCMType.LOC) -> left.open.asRSCM(RSCMType.LOC)
                left.closed.asRSCM(RSCMType.LOC) -> right.closed.asRSCM(RSCMType.LOC)
                left.open.asRSCM(RSCMType.LOC) -> right.open.asRSCM(RSCMType.LOC)
                else -> null
            }
        }
    }
}
