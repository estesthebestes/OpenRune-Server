package org.rsmod.content.quest.area.varrock.demonslayer

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The end of Demon Slayer: walking into the stone circle with Silverlight moves the player into
 * a private copy of it, where Denath and his dark wizards summon Delrith. Reducing Delrith to
 * zero hitpoints leaves him weakened and the player is asked for the incantation. The right words
 * banish him and finish the quest, the wrong ones restore him to full strength, and waiting too
 * long lets him slip away until the player re-enters.
 */
class DelrithFight
@Inject
constructor(
    private val quest: DemonSlayerQuest,
    private val banishment: DelrithBanishment,
    private val circle: StoneCircle,
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val musicPlayer: MusicPlayer,
) : PluginScript() {

    private val fightTrack: MusicRow by lazy { MusicRow.getRow(FIGHT_TRACK) }

    private val delrithType =
        ServerCacheManager.getNpc(DELRITH.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $DELRITH")
    private val weakenedType =
        ServerCacheManager.getNpc(WEAKENED.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $WEAKENED")
    private val denathType =
        ServerCacheManager.getNpc(DENATH.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $DENATH")

    override fun ScriptContext.startup() {
        onArea(CIRCLE_AREA) { tryEnterFight() }
        onNpcQueue(delrithType, "queue.death") { weaken() }
        onNpcQueue(weakenedType, TIMEOUT_QUEUE) { timeout() }
        onOpNpc1(WEAKENED) { banish(it.npc) }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KEY)) { musicPlayer.playNext(player) }
    }

    private suspend fun ProtectedAccess.tryEnterFight() {
        if (quest.stage(player) != DemonSlayerQuest.STAGE_KEY_HUNT) {
            return
        }
        if (!player.silverlightCaseEmpty || !quest.holdsSilverlight(player)) {
            return
        }
        if (manager.sessionForPlayer(player) != null) {
            return
        }
        enterFight()
    }

    private suspend fun ProtectedAccess.enterFight() {
        fadeToBlack()
        val visit =
            try {
                with(circle) { enterCircle(KEY, WIZARD_SPAWNS) }
            } catch (e: Exception) {
                logger.error(e) { "Demon Slayer fight entry failed for ${player.displayName}." }
                with(circle) { exitToRoad() }
                null
            }
        if (visit == null) {
            fadeFromBlack()
            closeFadeOverlay()
            return
        }
        // Let the client rebuild the instance before the camera or overlay are touched.
        delay(1)
        if (player.seenSummoning) {
            fadeFromBlack()
            closeFadeOverlay()
            musicPlay(fightTrack)
            val delrith = spawnDelrith(visit, summoned = false)
            engage(delrith, circle.npcsIn(visit))
            return
        }
        var delrith: Npc? = null
        try {
            delrith = summoningCutscene(visit)
        } catch (e: Exception) {
            logger.error(e) { "Demon Slayer summoning cutscene failed for ${player.displayName}." }
        } finally {
            endCutscene()
            fadeFromBlack()
            closeFadeOverlay()
        }
        player.seenSummoning = true
        val wizards =
            circle.npcsIn(visit).filter { it.id != delrithType.id && it.id != denathType.id }
        for (wizard in wizards) {
            wizard.clearFacingLock()
            wizard.mode = wizard.type.defaultMode
        }
        val striker = wizards.firstOrNull { it.type.combatLevel < 20 }
        engage(delrith ?: spawnDelrith(visit, summoned = false), wizards, striker)
    }

    private suspend fun ProtectedAccess.summoningCutscene(visit: StoneCircle.Visit): Npc {
        beginCutscene()
        camMoveTo(
            visit.at(CAMERA_FROM),
            height = CAMERA_HEIGHT,
            rate = CAMERA_RATE,
            rate2 = CAMERA_RATE,
        )
        camLookAt(
            visit.at(CAMERA_AT),
            height = LOOK_HEIGHT,
            rate = CAMERA_RATE,
            rate2 = CAMERA_RATE,
        )

        val wizards = circle.npcsIn(visit).filter { it.id != delrithType.id }
        val denath = circle.spawn(visit, DENATH, DENATH_TILE, Direction.North)
        for (wizard in wizards) {
            wizard.mode = NpcMode.None
            wizard.lockFacing(visit.at(StoneCircle.TABLE), targetWidth = 2, targetLength = 2)
        }
        delay(1)

        fadeFromBlack()
        musicPlay(fightTrack)
        denath.say("Arise, O mighty Delrith! Bring destruction to this soft, weak city!")
        denath.anim(CHANT_SEQ)
        soundSynth("synth.curse_cast_and_fire")
        delay(4)
        for (wizard in wizards) {
            wizard.say("Arise, Delrith!")
            wizard.anim(CHANT_SEQ)
        }
        soundSynth("synth.curse_cast_and_fire")
        delay(3)
        mes("The wizards cast an evil spell...")
        circle.animateTable(visit, "seq.qip_ds_table_explosion")
        soundSynth("synth.crumble_hit")
        delay(2)
        val delrith = spawnDelrith(visit, summoned = true)
        soundSynth("synth.summon_npc")
        delay(4)
        denath.say("Ha ha ha! At last you are free, my demonic brother!")
        delay(3)
        denath.say("Rest now, and then have your revenge on this pitiful city!")
        delay(4)
        wizards.firstOrNull()?.say("Who's that?")
        delay(3)
        denath.say("Noo! Not Silverlight! Delrith is not ready yet!")
        delay(3)
        denath.say("I've got to get out of here...")
        denath.clearFacingLock()
        denath.walk(visit.at(DENATH_ESCAPE))
        delay(3)
        circle.remove(denath)
        fadeToBlack()
        return delrith
    }

    private fun spawnDelrith(visit: StoneCircle.Visit, summoned: Boolean): Npc {
        val delrith = circle.spawn(visit, DELRITH, DELRITH_TILE, Direction.South)
        delrith.mode = delrithType.defaultMode
        if (summoned) {
            delrith.anim("seq.qip_ds_delrith_summoned")
        }
        return delrith
    }

    /**
     * Delrith always comes for the player. The dark wizards only bother with players they would
     * be aggressive towards in the open world: the level 7 wizards below combat 15, the level
     * 20 wizards below combat 41. After the summoning cutscene one level 7 wizard attacks
     * whatever the player's level, [striker].
     */
    private fun ProtectedAccess.engage(delrith: Npc, wizards: List<Npc>, striker: Npc? = null) {
        delrith.opPlayer2(player, aiInteractions)
        for (wizard in wizards) {
            if (wizard.id == delrithType.id || !wizard.isSlotAssigned) {
                continue
            }
            val threshold = if (wizard.type.combatLevel >= 20) 41 else 15
            if (wizard === striker || player.combatLevel < threshold) {
                wizard.opPlayer2(player, aiInteractions)
            }
        }
    }

    /** Delrith's death queue: he does not die, he weakens and waits for the incantation. */
    private fun StandardNpcAccess.weaken() {
        val hero = findHero(playerList)
        val instanceId = manager.instanceForNpc(npc)
        val coords = npc.coords
        npcRepo.del(npc, Int.MAX_VALUE)

        val weakened = Npc(weakenedType, coords)
        weakened.mode = NpcMode.None
        npcRepo.add(weakened, Int.MAX_VALUE)
        if (instanceId != null) {
            manager.attachNpc(instanceId, weakened)
        }
        weakened.queue(TIMEOUT_QUEUE, TIMEOUT_TICKS)

        if (hero != null) {
            hero.soundSynth("synth.weaken_all")
            launcher.launch(hero) { banish(weakened) }
        }
    }

    private fun StandardNpcAccess.timeout() {
        if (!npc.isSlotAssigned) {
            return
        }
        val instanceId = manager.instanceForNpc(npc)
        val session = instanceId?.let { manager.sessionForId(it) }
        if (session != null) {
            for (occupant in session.occupants) {
                val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
                player.mes(
                    "Delrith recovers his strength and slips away. Leave the circle and " +
                        "return to face him again."
                )
            }
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private suspend fun ProtectedAccess.banish(weakened: Npc) {
        with(banishment) { banish(WeakenedDelrith(weakened)) }
    }

    private inner class WeakenedDelrith(private val weakened: Npc) : DelrithBanishment.Arena {
        override fun present(): Boolean = weakened.isSlotAssigned

        override fun playBanishEffects(access: ProtectedAccess) {
            if (!weakened.isSlotAssigned) {
                return
            }
            weakened.clearQueue(TIMEOUT_QUEUE)
            weakened.anim("seq.qip_ds_delrith_banished")
            access.soundSynth("synth.aide_teleport_portal")
        }

        override fun leave(access: ProtectedAccess) {
            circle.remove(weakened)
            with(circle) { access.exitToRoad() }
            musicPlayer.playNext(access.player)
        }

        override fun restore(access: ProtectedAccess) {
            weakened.clearQueue(TIMEOUT_QUEUE)
            val instanceId = manager.instanceForNpc(weakened)
            val coords = weakened.coords
            circle.remove(weakened)

            val delrith = Npc(delrithType, coords)
            npcRepo.add(delrith, Int.MAX_VALUE)
            if (instanceId != null) {
                manager.attachNpc(instanceId, delrith)
            }
            delrith.opPlayer2(access.player, aiInteractions)
        }
    }

    private companion object {
        private val logger = InlineLogger()

        const val KEY = "demonslayer_fight"
        const val CIRCLE_AREA = "area.demon_slayer_stone_circle"
        const val TIMEOUT_QUEUE = "queue.demonslayer_delrith_timeout"

        /** Ticks a weakened Delrith waits for the incantation before slipping away. */
        const val TIMEOUT_TICKS = 100

        const val DELRITH = "npc.delrith"
        const val WEAKENED = "npc.delrith_weakened"
        const val DENATH = "npc.qip_ds_dark_wizard_denath"
        const val CHANT_SEQ = "seq.qip_ds_dark_wizard_chanting"
        const val FIGHT_TRACK = "dbrow.music_delrith_summoning"

        const val CAMERA_HEIGHT = 700
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100

        val CAMERA_FROM = CoordGrid(3227, 3355, 0)
        val CAMERA_AT = CoordGrid(3228, 3370, 0)

        /** Delrith is 2x2; this puts him just north of the stone table. */
        val DELRITH_TILE = CoordGrid(3227, 3371, 0)

        /** South of the table, facing it. */
        val DENATH_TILE = CoordGrid(3227, 3367, 0)
        val DENATH_ESCAPE = CoordGrid(3235, 3362, 0)

        /** Two level 7 and one level 20 dark wizard, on the world spawn tiles around the table. */
        val WIZARD_SPAWNS =
            listOf(
                InstanceNpc("npc.qip_ds_young_dark_wizard1", CoordGrid(3225, 3365, 0)),
                InstanceNpc("npc.qip_ds_young_dark_wizard2", CoordGrid(3230, 3374, 0)),
                InstanceNpc("npc.qip_ds_young_dark_wizard3", CoordGrid(3232, 3367, 0)),
            )
    }
}
