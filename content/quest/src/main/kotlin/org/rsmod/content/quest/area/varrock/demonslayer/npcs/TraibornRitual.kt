package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/**
 * The drazier ritual: Traiborn casts facing the player while the bones gather at his feet, the
 * wardrobe rises beside him, he reaches into it, it sinks back into the floor and he turns back.
 * Purely visual - the key is handed over before this plays.
 */
interface TraibornRitual {
    suspend fun Dialogue.perform(npc: Npc)
}

@Singleton
class WardrobeRitual
@Inject
constructor(private val locRepo: LocRepository, private val worldRepo: WorldRepository) :
    TraibornRitual {

    override suspend fun Dialogue.perform(npc: Npc) {
        npc.facePlayer(player)
        npc.anim(CAST_SEQ)
        npc.spotanim(BONE_SPOT)
        worldRepo.soundArea(npc, "synth.bones_to_bananas_all", radius = SOUND_RADIUS)
        delay(ticks(CAST_SEQ))

        val wardrobeTile = access.wardrobeTile(npc) ?: return
        val appear = ticks(APPEAR_SEQ)
        val reach = ticks(REACH_SEQ)
        val disappear = ticks(DISAPPEAR_SEQ)
        val wardrobe =
            locRepo.add(
                wardrobeTile,
                WARDROBE_LOC,
                appear + reach + disappear + 1,
                wardrobeAngle(npc.coords, wardrobeTile),
                LocShape.CentrepieceStraight,
            )
        worldRepo.locAnim(wardrobe, APPEAR_SEQ)
        worldRepo.soundArea(wardrobeTile, "synth.summon_npc", radius = SOUND_RADIUS)
        access.mes("A strange-looking wardrobe rises out of the floor.")
        delay(appear)

        npc.lockFacing(wardrobeTile)
        npc.anim(REACH_SEQ)
        worldRepo.soundArea(npc, "synth.pick2", radius = SOUND_RADIUS)
        delay(reach)

        worldRepo.locAnim(wardrobe, DISAPPEAR_SEQ)
        worldRepo.soundArea(wardrobeTile, "synth.teleport_reverse", radius = SOUND_RADIUS)
        delay(disappear)
        npc.clearFacingLock()
        npc.facePlayer(player)
    }

    private fun ticks(seq: String): Int =
        ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))?.tickDuration?.coerceAtLeast(1) ?: 2

    private fun wardrobeAngle(npc: CoordGrid, wardrobe: CoordGrid): LocAngle =
        when {
            wardrobe.x > npc.x -> FRONT_FACES_WEST
            wardrobe.z > npc.z -> FRONT_FACES_WEST.turn(3)
            wardrobe.x < npc.x -> FRONT_FACES_WEST.turn(2)
            else -> FRONT_FACES_WEST.turn(1)
        }

    private fun ProtectedAccess.wardrobeTile(npc: Npc): CoordGrid? {
        val candidates =
            listOf(
                npc.coords.translate(1, 0),
                npc.coords.translate(-1, 0),
                npc.coords.translate(0, 1),
                npc.coords.translate(0, -1),
            )
        return candidates.firstOrNull { it != player.coords && !mapBlocked(it) }
    }

    private companion object {
        const val WARDROBE_LOC = "loc.qip_ds_wizards_key_wardrobe_magic"
        const val CAST_SEQ = "seq.qip_ds_bones_wizard_anim"
        const val BONE_SPOT = "spotanim.qip_ds_bone_spotanim"
        const val APPEAR_SEQ = "seq.qip_ds_wardrobe_appear"
        const val DISAPPEAR_SEQ = "seq.qip_ds_wardrobe_disappear"
        const val REACH_SEQ = "seq.human_pickuptable"
        const val SOUND_RADIUS = 10

        val FRONT_FACES_WEST = LocAngle.North
    }
}
