package org.rsmod.content.quest.area.varrock.demonslayer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_DRAIN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_KEY_HUNT
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Prysin's key: stuck in the drain outside the palace kitchen until water is poured down it,
 * then lying in the mud of the Varrock sewers. The drain and mud locs are cache multilocs driven
 * by `varbit.delrith_drain_key`, so each player sees the state matching their own progress.
 */
class DemonSlayerDrain @Inject constructor(private val demonSlayer: DemonSlayerQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (drain in DRAINS) {
            onOpLoc1(drain) { searchDrain() }
            for ((full, empty) in WATER_CONTAINERS) {
                onOpLocU(drain, full) { pourWater(full, empty) }
            }
        }
        onOpLoc1(SEWER_KEY) { takeKey() }
    }

    private fun ProtectedAccess.seekingKey(): Boolean =
        demonSlayer.stage(player) == STAGE_KEY_HUNT && !player.silverlightCaseEmpty

    private suspend fun ProtectedAccess.searchDrain() {
        if (seekingKey() && player.drainKeyState == 0) {
            startDialogue {
                chatPlayer(quiz, "That must be the key Sir Prysin dropped.")
                chatPlayer(
                    neutral,
                    "I don't seem to be able to reach it. I wonder if I can dislodge it " +
                        "somehow. That way it may go down into the sewers.",
                )
            }
            return
        }
        mes("Nothing interesting seems to have been dropped down here today.")
    }

    private suspend fun ProtectedAccess.pourWater(full: String, empty: String) {
        anim("seq.human_pickuptable")
        soundSynth("synth.waterstrike_hit")
        val keyLost = player.drainKeyState == 2 && !demonSlayer.holdsKey(this, KEY_DRAIN)
        val washKey = seekingKey() && (player.drainKeyState == 0 || keyLost)
        if (invReplace(inv, full, 1, empty).failure) {
            return
        }
        if (!washKey) {
            mes("You pour the liquid down the drain.")
            return
        }
        player.drainKeyState = 1
        soundSynth("synth.coins_jingle_1", delay = 20)
        startDialogue {
            chatPlayer(
                happy,
                "OK, I think I've washed the key down into the sewer. I'd better go down and " +
                    "get it!",
            )
        }
    }

    private suspend fun ProtectedAccess.takeKey() {
        if (player.drainKeyState != 1) {
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You don't have enough inventory space.")
            return
        }
        anim("seq.human_pickupfloor")
        soundSynth("synth.pick2")
        delay(1)
        if (player.drainKeyState != 1 || invAdd(inv, KEY_DRAIN).failure) {
            return
        }
        player.drainKeyState = 2
        objbox(KEY_DRAIN, "You pick up an old rusty key.")
    }

    private companion object {
        const val SEWER_KEY = "loc.qip_ds_rustykey_mud"
        val DRAINS = listOf("loc.qip_ds_questdrain_key", "loc.qip_ds_questdrain_nokey")

        val WATER_CONTAINERS =
            listOf(
                "obj.bucket_water" to "obj.bucket_empty",
                "obj.jug_water" to "obj.jug_empty",
                "obj.bowl_water" to "obj.bowl_empty",
                "obj.vial_water" to "obj.vial_empty",
            )
    }
}
