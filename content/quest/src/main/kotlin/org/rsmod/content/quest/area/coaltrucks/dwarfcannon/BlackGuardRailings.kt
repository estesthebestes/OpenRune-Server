package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.HAMMER
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.RAILING
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.RAILING_VARBITS
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_RAILINGS_FIXED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_STARTED
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The stockade around Captain Lawgof's camp. Six of its railings are multilocs that stay bent
 * until their `mcannon_railingN_fixed` varbit is set; every other section is the fixed railing.
 *
 * Replacing one needs a railing and a hammer and succeeds on a Crafting roll (24% at level 1, 59%
 * at 99). A failed attempt costs 1 or 2 hitpoints, or drains Crafting or Strength by one.
 */
class BlackGuardRailings @Inject constructor(private val dwarfCannon: DwarfCannonQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        RAILING_MULTILOCS.forEachIndexed { index, loc ->
            onOpLoc1(loc) { inspect(index) }
            onOpLocU(loc, RAILING) { inspect(index) }
            onOpLocU(loc, HAMMER) { hammerAt(index) }
        }
        onOpLoc1(FIXED_RAILING) { inspectFixed() }
        onOpLocU(FIXED_RAILING, RAILING) { inspectFixed() }
        onOpLocU(FIXED_RAILING) { hammerFixed() }
    }

    private fun ProtectedAccess.isFixed(index: Int): Boolean = vars[RAILING_VARBITS[index]] != 0

    private suspend fun ProtectedAccess.inspect(index: Int) {
        arriveDelay()
        if (isFixed(index)) {
            inspectFixed()
            return
        }
        mesbox("This railing is broken and needs to be replaced.")
        if (RAILING !in inv) {
            startDialogue {
                chatPlayer(
                    neutral,
                    "I'm not going to be able to fix this without a new railing. Lawgof should " +
                        "have some spare ones.",
                )
            }
            return
        }
        if (dwarfCannon.stage(player) != STAGE_STARTED) {
            return
        }
        mes("You attempt to replace the broken railing...")
        if (HAMMER !in inv) {
            mes("You will need a hammer to fix the railings.")
            return
        }
        anim(REPAIR_ANIM)
        soundSynth(REPAIR_SOUND)
        delay(REPAIR_TICKS)
        if (!statRandom("stat.crafting", SUCCESS_LOW, SUCCESS_HIGH, 0)) {
            fail()
            return
        }
        if (invDel(inv, RAILING).failure) {
            return
        }
        VarPlayerIntMapSetter.set(player, RAILING_VARBITS[index], 1)
        soundSynth(FIXED_SOUND)
        mes("This railing is now fixed.")
        if (RAILING_VARBITS.indices.all { isFixed(it) }) {
            dwarfCannon.advanceTo(this, STAGE_RAILINGS_FIXED)
            startDialogue { chatPlayer(happy, "I've fixed all these railings now.") }
        }
    }

    private fun ProtectedAccess.fail() {
        when (random.of(0..3)) {
            0 -> {
                mes("You cut yourself on the rusty old railing.")
                queueHit(delay = 1, type = HitType.Typeless, damage = 1)
            }
            1 -> {
                mes("You accidentally crush your hand in the railing.")
                queueHit(delay = 1, type = HitType.Typeless, damage = 2)
            }
            2 -> {
                mes("Your arm is getting tired.")
                statSub("stat.crafting", constant = 1, percent = 0)
            }
            else -> {
                mes("You wrench your back trying to handle the railings.")
                statSub("stat.strength", constant = 1, percent = 0)
            }
        }
        say(OUCHES.random())
    }

    private suspend fun ProtectedAccess.inspectFixed() {
        arriveDelay()
        if (dwarfCannon.stage(player) >= STAGE_RAILINGS_FIXED) {
            startDialogue { chatPlayer(happy, "I've fixed all these railings now.") }
        } else {
            mes("This railing looks sturdy enough.")
        }
    }

    /** Knocking a railing with a hammer, or anything else, only looks like a repair. */
    private suspend fun ProtectedAccess.hammerAt(index: Int) {
        if (isFixed(index)) {
            hammerFixed()
            return
        }
        arriveDelay()
        anim(REPAIR_ANIM)
        soundSynth(REPAIR_SOUND)
        delay(REPAIR_TICKS)
        startDialogue {
            chatPlayer(happy, "That's better, the goblins won't get past this piece of fencing now.")
        }
    }

    private suspend fun ProtectedAccess.hammerFixed() {
        arriveDelay()
        startDialogue {
            chatPlayer(happy, "That's better, the goblins won't get past this piece of fencing now.")
        }
    }

    private companion object {
        val RAILING_MULTILOCS = (1..DwarfCannonQuest.RAILING_COUNT).map { "loc.mcannon_railing${it}_multiloc" }
        const val FIXED_RAILING = "loc.mcannon_dwarf_railing_fixed"

        const val REPAIR_ANIM = "seq.mcannon_hammer_anim"
        const val REPAIR_SOUND = "synth.tbcu_repair_fence"
        const val FIXED_SOUND = "synth.hammering_1"
        const val REPAIR_TICKS = 3

        const val SUCCESS_LOW = 60
        const val SUCCESS_HIGH = 150

        val OUCHES = listOf("Oooch!", "Ow!", "Urrrgh!", "Gah!")
    }
}
