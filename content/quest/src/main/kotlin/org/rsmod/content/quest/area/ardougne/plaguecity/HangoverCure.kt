package org.rsmod.content.quest.area.ardougne.plaguecity

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.HANGOVER_CURE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.SCRUFFY_NOTE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.TELEPORT_SCROLL
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bravek's hangover cure (chocolate dust into a bucket of milk, then snape grass), the scruffy
 * recipe note he scribbled, and the Ardougne teleport scroll Edmond hands over at the end.
 */
class HangoverCure @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(CHOCOLATE_DUST, BUCKET_MILK) { mix(CHOCOLATE_DUST, BUCKET_MILK, CHOCOLATY_MILK) }
        onOpHeldU(SNAPE_GRASS, CHOCOLATY_MILK) { mix(SNAPE_GRASS, CHOCOLATY_MILK, HANGOVER_CURE) }
        onOpHeld1(SCRUFFY_NOTE) { readNote() }
        onOpHeld1(TELEPORT_SCROLL) { readScroll() }
    }

    /** Swaps [ingredient] and [base] for [result] in one transaction. */
    private suspend fun ProtectedAccess.mix(ingredient: String, base: String, result: String) {
        val mixed =
            player.invTransaction(inv) {
                val from = select(inv)
                delete {
                    this.from = from
                    this.obj = ingredient.asRSCM(RSCMType.OBJ)
                    this.strictCount = 1
                }
                delete {
                    this.from = from
                    this.obj = base.asRSCM(RSCMType.OBJ)
                    this.strictCount = 1
                }
                insert {
                    this.into = from
                    this.obj = result.asRSCM(RSCMType.OBJ)
                    this.strictCount = 1
                }
            }
        if (mixed.failure) {
            return
        }
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        val what = if (result == HANGOVER_CURE) "snape grass" else "chocolate"
        objbox(result, "You mix the $what into the bucket.")
    }

    private suspend fun ProtectedAccess.readNote() {
        mesbox("Got a bncket of nnilk. Tlen qrind sorne lcoculate vnith a pestal and rnortar.")
        mesbox("Ald the grourd dlocolate to tho milt. Fnales add 5cme snape gras5.")
    }

    /**
     * The first scroll teaches the spell; any further copies go up in smoke, the way the real
     * ones do once the spell is known.
     */
    private suspend fun ProtectedAccess.readScroll() {
        if (!player.readScroll) {
            if (invDel(inv, TELEPORT_SCROLL).failure) {
                return
            }
            player.readScroll = true
            objbox(
                TELEPORT_SCROLL,
                "You memorise what is written on the scroll. You can now use the Ardougne " +
                    "Teleport spell.",
            )
            return
        }
        if (invReplace(inv, TELEPORT_SCROLL, 1, ASHES).failure) {
            return
        }
        spotanim(PUFF)
        soundSynth(EXPLODE_SOUND)
        mes("The scroll bursts into flame as you unroll it, leaving only ashes.")
    }

    private companion object {
        const val CHOCOLATE_DUST = "obj.chocolate_dust"
        const val BUCKET_MILK = "obj.bucket_milk"
        const val CHOCOLATY_MILK = "obj.chocolaty_milk"
        const val SNAPE_GRASS = "obj.snape_grass"
        const val ASHES = "obj.ashes"

        const val MIX_SEQ = "seq.human_pickuptable"
        const val MIX_SOUND = "synth.vial_mix"
        const val PUFF = "spotanim.smokepuff"
        const val EXPLODE_SOUND = "synth.exploding_vial"
    }
}
