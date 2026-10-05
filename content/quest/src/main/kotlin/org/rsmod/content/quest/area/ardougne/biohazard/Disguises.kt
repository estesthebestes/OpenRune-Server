package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.script.onOpWorn1
import org.rsmod.content.quest.area.ardougne.GAS_MASK
import org.rsmod.content.quest.area.ardougne.MEDICAL_GOWN
import org.rsmod.content.quest.area.ardougne.WestArdougne
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_CROSSED_WALL
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * While sneaking around West Ardougne during Biohazard the gas mask stays on, and the medical
 * gown stays on inside the Mourner Headquarters; anywhere else both come off as normal.
 */
class Disguises @Inject constructor(private val biohazard: BiohazardQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpWorn1(GAS_MASK) {
            val sneaking = biohazard.stage(player) in STAGE_CROSSED_WALL until STAGE_GOT_SAMPLES
            if (sneaking && WestArdougne.contains(player.coords)) {
                mes("You should leave your gas mask on while you're in West Ardougne.")
            } else {
                wornUnequip(it.slot)
            }
        }
        onOpWorn1(MEDICAL_GOWN) {
            if (
                WestArdougne.inMournerHeadquarters(player.coords) &&
                    biohazard.quest.isQuestInProgress(player)
            ) {
                mes(
                    "You should leave your medical gown on while you're in the Mourner " +
                        "Headquarters."
                )
            } else {
                wornUnequip(it.slot)
            }
        }
    }
}
