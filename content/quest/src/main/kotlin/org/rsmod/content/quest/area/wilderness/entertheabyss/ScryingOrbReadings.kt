package org.rsmod.content.quest.area.wilderness.entertheabyss

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.EMPTY_ORB
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.FULL_ORB
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.READINGS_NEEDED
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_READINGS_TAKEN
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_RESEARCHING
import org.rsmod.content.skills.runecrafting.essence.EssenceMineArrival
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ScryingOrbReadings @Inject constructor(private val quest: EnterTheAbyssQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onEvent<EssenceMineArrival> { onArrival(access, teleporter) }
    }

    private fun onArrival(access: ProtectedAccess, teleporter: EssenceMineTeleporter) {
        val player = access.player
        when (quest.stage(player)) {
            STAGE_RESEARCHING -> if (EMPTY_ORB in player.inv) access.takeReading(teleporter)
            STAGE_READINGS_TAKEN -> if (EMPTY_ORB in player.inv) access.refillOrb()
        }
    }

    private fun ProtectedAccess.takeReading(teleporter: EssenceMineTeleporter) {
        val repeated = quest.hasReading(player, teleporter)
        if (!repeated) {
            quest.recordReading(player, teleporter)
        }
        val count = quest.readingCount(player)
        if (count < READINGS_NEEDED) {
            if (repeated) {
                mes("Your scrying orb already holds a reading of this teleport.")
                return
            }
            val remaining = READINGS_NEEDED - count
            val more = if (remaining == 1) "one more location" else "$remaining more locations"
            mes("Your scrying orb absorbs a reading of the teleport. It needs readings from $more.")
            return
        }
        if (invReplace(inv, EMPTY_ORB, 1, FULL_ORB).failure) {
            return
        }
        quest.setStage(this, STAGE_READINGS_TAKEN)
        mes("Your scrying orb absorbs a reading of the teleport and is now full.")
        mes("You should take it back to the Mage of Zamorak in Varrock.")
    }

    private fun ProtectedAccess.refillOrb() {
        if (invReplace(inv, EMPTY_ORB, 1, FULL_ORB).failure) {
            return
        }
        mes("Your scrying orb absorbs the teleport and recovers its readings.")
        mes("You should take it back to the Mage of Zamorak in Varrock.")
    }
}
