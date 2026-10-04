package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.GnomeMaze
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.HasOrb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Orb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.OrbReturned
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Started
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.StrongholdBreached
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.WarlordSlain
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.elkoyGuides
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Elkoy guides the player through the maze. There are two of him: `npc.elkoy` outside the hedges at
 * the north-west entrance, and `npc.elkoy_village` at the far end of the maze beside the loose
 * railing. Both are varp-multi npcs that gain a Follow option as the quest goes on; the handlers
 * are bound to the base types, which is what the interaction event carries.
 */
class Elkoy @Inject constructor(private val quest: TreeGnomeVillageQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(AtEntrance, AtVillage)) {
            onOpNpc1(type) { startDialogue(it.npc) { elkoy(it.npc) } }
            onOpNpc3(type) { follow(it.npc) }
        }
    }

    private val Npc.atVillage: Boolean
        get() = id == AtVillage.asRSCM(RSCMType.NPC)

    private suspend fun ProtectedAccess.follow(npc: Npc) {
        if (quest.stage(player) == 0) {
            startDialogue(npc) { elkoy(npc) }
            return
        }
        guide(npc)
    }

    private suspend fun ProtectedAccess.guide(npc: Npc, box: String? = null) {
        val dest = if (npc.atVillage) GnomeMaze.Entrance else GnomeMaze.RailingApproach
        elkoyGuides(dest, parting(npc), box)
    }

    private fun ProtectedAccess.parting(npc: Npc): String {
        val atVillage = npc.atVillage
        return when (quest.stage(player)) {
            in Started until HasOrb -> "Please help us get our orb back."
            HasOrb ->
                when {
                    !player.inv.contains(Orb) -> "Please help us get our orb back."
                    atVillage -> "Please come back with our orb soon."
                    else -> "Here we are. Take the orb to King Bolren, I'm sure he'll be pleased."
                }
            OrbReturned ->
                if (atVillage) "Please help us find the orbs."
                else "Here we are. Despite what has happened here, I hope you feel welcome."
            else ->
                if (atVillage) "Here we are. Have a safe journey."
                else "Here we are. Feel free to have a look around."
        }
    }

    private suspend fun Dialogue.elkoy(npc: Npc) {
        val stage = quest.stage(player)
        when {
            stage == 0 -> introduction(npc)
            stage == Started -> {
                chatPlayer(happy, "Hello Elkoy.")
                chatNpc(worried, "Oh my! Oh my!")
                chatPlayer(quiz, "What's wrong?")
                chatNpc(
                    worried,
                    "The orb, they have the orb. We're doomed. Shall I show you " +
                        "${wayTo(npc)}?",
                )
                offerGuide(npc)
            }
            stage < StrongholdBreached -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    worried,
                    "You must retrieve the orb, or the gnome village is doomed. Shall I show you " +
                        "${wayTo(npc)}?",
                )
                offerGuide(npc)
            }
            stage <= HasOrb -> orbQuestion(npc)
            stage == OrbReturned -> pillaged(npc)
            stage == WarlordSlain -> hero(npc)
            else -> afterQuest(npc)
        }
    }

    private fun wayTo(npc: Npc): String =
        if (npc.atVillage) "through the maze" else "back to the village"

    private suspend fun Dialogue.offerGuide(npc: Npc) {
        if (!choice2("Yes please.", true, "Not now, thanks.", false)) {
            chatPlayer(neutral, "Not now, thanks.")
            return
        }
        chatPlayer(happy, "Yes please.")
        access.guide(npc)
    }

    private suspend fun Dialogue.introduction(npc: Npc) {
        chatPlayer(happy, "Hello there.")
        val place = if (npc.atVillage) "village" else "maze"
        chatNpc(happy, "Hello, welcome to our $place. I'm Elkoy the tree gnome.")
        chatPlayer(quiz, "I haven't heard of your sort before.")
        chatNpc(
            sad,
            "There aren't many of us left. Once tree gnomes lived all over the world, but now we " +
                "hide in small groups so we're not captured.",
        )
        chatPlayer(quiz, "Captured by whom?")
        chatNpc(
            sad,
            "Tree gnomes have been hunted for so-called 'fun' for as long as I can " +
                "remember.",
        )
        chatNpc(
            neutral,
            "These days our biggest threat is General Khazard's troops. They show no mercy, but " +
                "they're very dense. They'll never find their way through our maze.",
        )
        chatNpc(happy, "Have fun.")
    }

    private suspend fun Dialogue.orbQuestion(npc: Npc) {
        if (!player.inv.contains(Orb)) {
            chatPlayer(happy, "Hello Elkoy.")
            chatNpc(quiz, "You're back! And the orb?")
            chatPlayer(sad, "No, I'm afraid not.")
            chatNpc(
                worried,
                "Please, we must have the orb if we are to survive. Shall I show you " +
                    "${wayTo(npc)}?",
            )
            offerGuide(npc)
            return
        }
        if (npc.atVillage) {
            chatPlayer(happy, "Hello Elkoy. I have the orb.")
            chatNpc(happy, "Then take it to King Bolren. I'm sure he'll be pleased to see you.")
            if (!choice2("Can you show me out of the village?", true, "Okay.", false)) {
                chatPlayer(happy, "Okay.")
                return
            }
            chatPlayer(quiz, "Can you show me out of the village?")
            access.guide(npc)
            return
        }
        chatPlayer(happy, "Hello Elkoy.")
        chatNpc(quiz, "You're back! And the orb?")
        chatPlayer(happy, "I have it here.")
        chatNpc(
            happy,
            "You're our saviour! Bring it to the village and we are all saved. Would you like me " +
                "to show you the way to the village?",
        )
        if (!choice2("Yes please.", true, "No thanks Elkoy.", false)) {
            chatPlayer(neutral, "No thanks Elkoy.")
            chatNpc(worried, "Please, we must have the orb if we are to survive.")
            return
        }
        chatPlayer(happy, "Yes please.")
        access.guide(npc, box = "Elkoy guides you through the maze.")
    }

    private suspend fun Dialogue.pillaged(npc: Npc) {
        chatPlayer(happy, "Hello Elkoy.")
        chatNpc(sad, "Did you hear?")
        chatNpc(
            sad,
            "Khazard's men have pillaged the village! They killed many of us, and took the other " +
                "orbs to draw us out of the maze. When will the misery end?",
        )
        if (npc.atVillage) {
            if (!choice2("Can you show me out of the village?", true, "I'm very sorry.", false)) {
                chatPlayer(sad, "I'm very sorry.")
                return
            }
            chatPlayer(quiz, "Can you show me out of the village?")
            access.guide(npc)
            return
        }
        chatNpc(neutral, "Would you like me to show you the way to the village?")
        guideOrDecline(npc)
    }

    private suspend fun Dialogue.hero(npc: Npc) {
        chatPlayer(happy, "Hello Elkoy.")
        chatNpc(happy, "You truly are a hero.")
        chatPlayer(happy, "Thanks.")
        chatNpc(
            happy,
            "You saved us by bringing back the orbs of protection. I'm humbled, and I wish " +
            "you well.",
        )
        offerWayAfterQuest(npc)
    }

    private suspend fun Dialogue.afterQuest(npc: Npc) {
        chatPlayer(happy, "Hello Elkoy.")
        chatNpc(happy, "Hi there, I hope life is treating you well.")
        offerWayAfterQuest(npc)
    }

    private suspend fun Dialogue.offerWayAfterQuest(npc: Npc) {
        if (npc.atVillage) {
            chatNpc(neutral, "Would you like me to show you the way out of the village?")
            if (!choice2("Yes please.", true, "Not now, thanks.", false)) {
                chatPlayer(neutral, "Not now, thanks.")
                return
            }
            chatPlayer(happy, "Yes please.")
            access.guide(npc)
            return
        }
        chatNpc(neutral, "Would you like me to show you the way to the village?")
        guideOrDecline(npc)
    }

    private suspend fun Dialogue.guideOrDecline(npc: Npc) {
        if (!choice2("Yes please.", true, "No thanks Elkoy.", false)) {
            chatPlayer(neutral, "No thanks Elkoy.")
            chatNpc(neutral, "Ok then, take care.")
            return
        }
        chatPlayer(happy, "Yes please.")
        access.guide(npc)
    }

    private companion object {
        const val AtEntrance = "npc.elkoy"
        const val AtVillage = "npc.elkoy_village"
    }
}
