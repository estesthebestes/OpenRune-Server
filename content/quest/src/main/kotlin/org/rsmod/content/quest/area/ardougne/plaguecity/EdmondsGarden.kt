package org.rsmod.content.quest.area.ardougne.plaguecity

import jakarta.inject.Inject
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.BUCKETS_NEEDED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_DUG_TUNNEL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_HAS_GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SOIL_SOFTENED
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mud patch behind Edmond's house: four buckets of water soften it, a spade opens it into a
 * hole down to the sewers, and the hole stays open. The centre patch is a varbit multiloc
 * (`loc.plaguemudpatch2`: mud, then hole) with a plain "Mud patch" tile either side of it that
 * shares the same handling.
 */
class EdmondsGarden @Inject constructor(private val plagueCity: PlagueCityQuest) :
    PluginScript(), SpadeDigHook {

    override fun ScriptContext.startup() {
        for (patch in MUD_PATCHES) {
            onOpLocU(patch, BUCKET_WATER) { pourWater() }
            onOpLocU(patch, SPADE) { digOnPatch() }
        }
        onOpLoc1(DUG_HOLE) { climbDown() }
    }

    override fun claims(player: Player): Boolean =
        player.coords.level == 0 && player.coords.chebyshevDistance(MUD_PATCH_TILE) <= 1

    override suspend fun ProtectedAccess.dig() {
        faceSquare(MUD_PATCH_TILE)
        digPatch()
    }

    private suspend fun ProtectedAccess.digOnPatch() {
        arriveDelay()
        faceSquare(MUD_PATCH_TILE)
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(DIG_TICKS)
        resetAnim()
        digPatch()
    }

    private suspend fun ProtectedAccess.pourWater() {
        arriveDelay()
        faceSquare(MUD_PATCH_TILE)
        val stage = plagueCity.stage(player)
        if (stage != STAGE_HAS_GAS_MASK || !player.toldToDig) {
            mesbox("You see no reason to do that at the moment.")
            return
        }
        if (invReplace(inv, BUCKET_WATER, 1, BUCKET_EMPTY).failure) {
            return
        }
        val poured = player.bucketsPoured + 1
        player.bucketsPoured = poured
        anim(POUR_SEQ)
        if (poured >= BUCKETS_NEEDED) {
            plagueCity.advanceTo(this, STAGE_SOIL_SOFTENED)
            mesbox("You pour water onto the soil. The soil is now soft enough to dig into.")
        } else {
            mesbox("You pour water onto the soil. The soil softens slightly.")
        }
    }

    private suspend fun ProtectedAccess.digPatch() {
        when {
            player.mudDug -> climbDown()
            plagueCity.stage(player) != STAGE_SOIL_SOFTENED ->
                mesbox("You dig the soil... The ground is rather hard.")
            else -> tunnelThrough()
        }
    }

    private suspend fun ProtectedAccess.tunnelThrough() {
        player.mudDug = true
        player.edmondBelow = true
        plagueCity.advanceTo(this, STAGE_DUG_TUNNEL)
        soundSynth(LAND_SOUND)
        telejump(SEWER_ARRIVAL)
        mesbox(
            "You dig deep into the soft soil... Suddenly it crumbles away! You fall through " +
                "into the sewer. Edmond follows you down the hole."
        )
    }

    private suspend fun ProtectedAccess.climbDown() {
        arriveDelay()
        faceSquare(MUD_PATCH_TILE)
        anim(CLIMB_DOWN_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(2)
        telejump(SEWER_ARRIVAL)
        mes("You climb down into the hole.")
    }

    companion object {
        val MUD_PATCHES =
            listOf(
                "loc.plaguemudpatch1",
                "loc.plaguemudpatch2",
                "loc.plague_mud",
                "loc.plague_hole",
            )
        const val DUG_HOLE = "loc.plague_hole"
        const val SPADE = "obj.spade"
        const val BUCKET_WATER = "obj.bucket_water"
        const val BUCKET_EMPTY = "obj.bucket_empty"

        val MUD_PATCH_TILE = CoordGrid(2566, 3332, 0)

        /** Beside the mud pile at the north end of the sewer corridor. */
        val SEWER_ARRIVAL = CoordGrid(2518, 9759, 0)

        const val POUR_SEQ = "seq.human_pickuptable"
        const val DIG_SEQ = "seq.human_dig"
        const val DIG_SOUND = "synth.digspade"
        const val DIG_TICKS = 2
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val CLIMB_SOUND = "synth.climb_wall"
        const val LAND_SOUND = "synth.fall_land"
    }
}
