package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The dwarven guards posted at the Coal Trucks stockade and the Black Guard camp. */
class BlackGuardSentries @Inject constructor(private val dwarfCannon: DwarfCannonQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (guard in GUARDS) {
            onOpNpc1(guard) { startDialogue(it.npc) { guard() } }
        }
    }

    private suspend fun Dialogue.guard() {
        chatPlayer(happy, "Hello.")
        chatNpc(angry, "Don't distract me while I'm on duty! This mine has to be protected!")
        chatPlayer(quiz, "What's going to attack a mine?")
        chatNpc(
            angry,
            "Goblins! They wander everywhere, attacking anyone they think is small enough to be an " +
                "easy victim. We need more cannons to fight them off properly.",
        )
        if (dwarfCannon.isComplete(player)) {
            chatPlayer(happy, "Well, I've done my bit to help with that.")
            chatNpc(neutral, "Yes, I heard. Now please let me get on with my guard duties.")
        } else {
            chatPlayer(shocked, "Cannons? Those sound expensive.")
            chatNpc(
                neutral,
                "A new cannon can cost 750,000 coins, and the ammo isn't easy to get, but they do " +
                    "up to 30 hitpoints of damage with each shot. When you've got an important mine " +
                    "like this one to protect, it's worth the expense.",
            )
            chatPlayer(happy, "Thanks for the information.")
            chatNpc(neutral, "You're welcome. Now please let me get on with my guard duties.")
        }
        chatPlayer(neutral, "Alright, I'll leave you alone now.")
    }

    private companion object {
        val GUARDS =
            listOf(
                "npc.mcannonguard",
                "npc.mcannonguard1",
                "npc.mcannonguard2",
                "npc.mcannonguard3",
                "npc.mcannonguard4",
            )
    }
}
