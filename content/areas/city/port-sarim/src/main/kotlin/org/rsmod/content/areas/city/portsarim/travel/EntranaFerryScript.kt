package org.rsmod.content.areas.city.portsarim.travel

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.entrana.EntranaRules
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class EntranaFerryScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (monk in SARIM_MONKS) {
            onOpNpc1(monk) { startDialogue(it.npc) { offerPassage() } }
            onOpNpc3(monk) { startDialogue(it.npc) { searchAndBoard() } }
        }
        for (monk in ENTRANA_MONKS) {
            onOpNpc1(monk) { startDialogue(it.npc) { offerReturn() } }
            onOpNpc3(monk) { sailFerry(PORT_SARIM, "Port Sarim", fare = 0) }
        }
    }

    private suspend fun Dialogue.offerPassage() {
        chatNpc(
            quiz,
            "Do you seek passage to holy Entrana? If so, you must leave your weaponry and armour " +
                "behind. This is Saradomin's will.",
        )
        val ready =
            choice2(
                "No, not right now.",
                false,
                "Yes, okay, I'm ready to go.",
                true,
                title = "What would you like to say?",
            )
        if (!ready) {
            chatPlayer(neutral, "No, not right now.")
            chatNpc(neutral, "Very well.")
            return
        }
        chatPlayer(happy, "Yes, okay, I'm ready to go.")
        chatNpc(neutral, "Very well. One moment please.")
        searchAndBoard()
    }

    private suspend fun Dialogue.searchAndBoard() {
        mesbox("The monk quickly searches you.")
        if (access.carriesWeaponsOrArmour()) {
            chatNpc(
                angry,
                "NO WEAPONS OR ARMOUR are permitted on holy Entrana AT ALL. We will not allow you " +
                    "to travel there in breach of mighty Saradomin's edict.",
            )
            chatNpc(
                neutral,
                "Do not try and deceive us again. Come back when you have laid down your " +
                    "Zamorakian instruments of death.",
            )
            return
        }
        chatNpc(neutral, "All is satisfactory. You may board the boat now.")
        access.sailFerry(ENTRANA, "Entrana", fare = 0)
    }

    private suspend fun Dialogue.offerReturn() {
        chatNpc(quiz, "Do you wish to leave holy Entrana?")
        if (!choice2("Yes, I'm ready to go.", true, "Not just yet.", false)) {
            chatPlayer(neutral, "Not just yet.")
            return
        }
        chatPlayer(happy, "Yes, I'm ready to go.")
        chatNpc(neutral, "Okay, let's board...")
        access.sailFerry(PORT_SARIM, "Port Sarim", fare = 0)
    }

    private fun ProtectedAccess.carriesWeaponsOrArmour(): Boolean =
        EntranaRules.carriesForbidden(player)

    private companion object {
        val SARIM_MONKS = listOf("npc.shipmonk", "npc.shipmonk1_b", "npc.shipmonk1_c")
        val ENTRANA_MONKS = listOf("npc.shipmonk2", "npc.shipmonk2_b", "npc.shipmonk2_c")
        val ENTRANA = CoordGrid(2834, 3335, 0)
        val PORT_SARIM = CoordGrid(3048, 3234, 0)
    }
}
