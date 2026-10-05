package org.rsmod.content.quest.area.ardougne.biohazard

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.BIRD_FEED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.PIGEON_CAGE_EMPTY
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.PIGEON_CAGE_FULL
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_DISTRACTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_MET_OMART
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The watchtower beside the wall gate and the mourners posted on top of it. Bird feed thrown
 * onto the tower and a cage of Jerico's pigeons opened beside it keep the guards busy for long
 * enough to get a rope ladder over the wall further south.
 */
class WatchtowerDistraction
@Inject
constructor(
    private val biohazard: BiohazardQuest,
    private val worldRepo: WorldRepository,
    private val search: NpcSearch,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(BIRD_FEED) { throwBirdFeed() }
        onOpHeld1(PIGEON_CAGE_FULL) { openCage() }
        for (tower in WATCHTOWERS) {
            onOpLoc1(tower) { investigate() }
        }
    }

    private suspend fun ProtectedAccess.investigate() {
        arriveDelay()
        faceSquare(TOWER_TOP)
        startDialogue {
            chatPlayer(
                quiz,
                "Two mourners on watch up there, with a good view of the whole wall. If I could " +
                    "keep them busy for a few minutes...",
            )
            chatPlayer(
                neutral,
                "Jerico's pigeons should do it, if I scatter some bird feed on the tower first.",
            )
        }
    }

    private suspend fun ProtectedAccess.throwBirdFeed() {
        if (!nearTower() || biohazard.stage(player) != STAGE_MET_OMART) {
            startDialogue { chatPlayer(
                neutral,
                "I probably shouldn't waste it by throwing it here.",
            ) }
            return
        }
        if (invDel(inv, BIRD_FEED).failure) {
            return
        }
        player.birdFeedThrown = true
        faceSquare(TOWER_TOP)
        anim(THROW_SEQ)
        soundSynth(THROW_SOUND)
        delay(1)
        mesbox(
            "You throw a handful of seeds onto the watchtower. The mourners do not seem to " +
                "notice."
        )
    }

    private suspend fun ProtectedAccess.openCage() {
        val ready =
            nearTower() && biohazard.stage(player) == STAGE_MET_OMART && player.birdFeedThrown
        if (!ready) {
            mes("The pigeons don't want to leave.")
            return
        }
        if (invReplace(inv, PIGEON_CAGE_FULL, 1, PIGEON_CAGE_EMPTY).failure) {
            return
        }
        biohazard.advanceTo(this, STAGE_DISTRACTED)
        faceSquare(TOWER_TOP)
        anim(OPEN_CAGE_SEQ)
        spotanim(PIGEON_LAUNCH)
        soundSynth(FLAPPING_SOUND)
        delay(1)
        pigeonsAtTheTower()
        mesbox(
            "The pigeons fly towards the watchtower. The mourners frantically try to scare them " +
                "away."
        )
    }

    private suspend fun ProtectedAccess.pigeonsAtTheTower() {
        try {
            camModeClose()
            camMoveTo(CAMERA_FROM, height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            camLookAt(TOWER_TOP, height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            delay(1)
            for (perch in PERCHES) {
                spotanimMap(worldRepo, PIGEON_LAUNCH, perch, height = PERCH_HEIGHT)
            }
            soundSynth(TWITTER_SOUND)
            val guards =
                search.findAll(TOWER_TOP, TOWER_MOURNER, TOWER_RADIUS, HuntVis.Off).toList()
            for ((index, guard) in guards.withIndex()) {
                guard.anim(SHOO_SEQ)
                guard.say(if (index % 2 == 0) "Shoo! Get off!" else "Filthy birds!")
            }
            delay(3)
            for (guard in guards) {
                guard.anim(SHOO_SEQ)
            }
            soundSynth(FLAPPING_SOUND)
            delay(3)
        } finally {
            camReset()
            camModeReset()
        }
    }

    private fun ProtectedAccess.nearTower(): Boolean =
        player.coords.level == 0 &&
            player.coords.x in THROW_MIN_X..THROW_MAX_X &&
            player.coords.z in THROW_MIN_Z..THROW_MAX_Z

    private companion object {
        /** The varp multiloc and the form with the Investigate option. */
        val WATCHTOWERS = listOf("loc.biowatchtower", "loc.biowatchtower_op")
        const val TOWER_MOURNER = "npc.mournerwatchtower"

        val TOWER_TOP = CoordGrid(2560, 3304, 0)
        val PERCHES =
            listOf(CoordGrid(2559, 3303, 0), CoordGrid(2561, 3305, 0), CoordGrid(2560, 3304, 0))
        val CAMERA_FROM = CoordGrid(2566, 3298, 0)
        const val TOWER_RADIUS = 4
        const val PERCH_HEIGHT = 250

        const val THROW_MIN_X = 2559
        const val THROW_MAX_X = 2568
        const val THROW_MIN_Z = 3297
        const val THROW_MAX_Z = 3309

        const val CAMERA_HEIGHT = 700
        const val LOOK_HEIGHT = 400
        const val CAMERA_RATE = 100

        const val THROW_SEQ = "seq.human_throw"
        const val OPEN_CAGE_SEQ = "seq.human_pickuptable"
        const val SHOO_SEQ = "seq.emote_wave"
        const val PIGEON_LAUNCH = "spotanim.biopigeon_launch"
        const val THROW_SOUND = "synth.throw"
        const val FLAPPING_SOUND = "synth.flapping"
        const val TWITTER_SOUND = "synth.bird_twitter_1"
    }
}
