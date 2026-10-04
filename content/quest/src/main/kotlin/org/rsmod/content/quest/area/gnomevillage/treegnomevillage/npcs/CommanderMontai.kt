package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.AgreedToGatherLogs
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.HasOrb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.LogsGiven
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.LogsNeeded
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Logs
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.MaxTrackerAnswer
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Orb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Started
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.StrongholdBreached
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.TrackersSent
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.ballistaAnswer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Commander Montai, who runs the gnome side of the battlefield north of the maze. */
class CommanderMontai @Inject constructor(private val quest: TreeGnomeVillageQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(Montai) { startDialogue(it.npc) { montai() } }
    }

    private suspend fun Dialogue.montai() {
        when (quest.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    angry,
                    "I can't talk now. Can't you see we're in the middle of a battle? If we " +
                    "don't " +
                        "hold back Khazard's men, we're all doomed.",
                )
            }
            Started -> firstMeeting()
            AgreedToGatherLogs -> logs()
            LogsGiven -> nextPhase()
            TrackersSent -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    neutral,
                    "Hello warrior. We still need the coordinates for a direct hit from the " +
                        "ballista. Once it lands, you'll be able to get into the stronghold and " +
                        "retrieve the orb.",
                )
            }
            StrongholdBreached -> {
                chatPlayer(happy, "I've breached the stronghold.")
                chatNpc(
                    happy,
                    "I saw it, a beautiful shot. The Khazard troops never knew what hit them.",
                )
                chatNpc(
                    neutral,
                    "Now is the time to take the orb. It's all down to you. I'll be praying for " +
                        "you.",
                )
            }
            HasOrb -> withOrb()
            else -> {
                chatPlayer(happy, "Hello Montai, how are you?")
                chatNpc(
                    neutral,
                    "I'm all right. This battle is going to take longer to win than I expected. " +
                        "Khazard's troops won't give up even without the orb.",
                )
                chatPlayer(neutral, "Hang in there.")
            }
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(happy, "Hello.")
        chatNpc(quiz, "Hello traveller. Have you come to help, or just to watch?")
        chatPlayer(neutral, "I've been sent by King Bolren to retrieve the orb of protection.")
        chatNpc(happy, "Excellent, we need all the help we can get.")
        chatNpc(
            neutral,
            "I'm Commander Montai. The orb is in the Khazard stronghold to the north, but we " +
                "can't get near it until their defences are weakened.",
        )
        chatPlayer(quiz, "What can I do?")
        chatNpc(
            neutral,
            "First we must reinforce our own lines. We badly need wood for more battlements, " +
                "because once they fall it's all over. Six loads of normal logs should do it.",
        )
        val gather =
            choice2(
                "Sorry, I no longer want to be involved.",
                false,
                "Ok, I'll gather some wood.",
                true,
            )
        if (!gather) {
            chatPlayer(neutral, "Sorry I no longer want to be involved.")
            chatNpc(sad, "That's a shame. We could have used your help.")
            return
        }
        quest.advanceTo(access, AgreedToGatherLogs)
        chatPlayer(happy, "Ok, I'll gather some wood.")
        chatNpc(worried, "Be as quick as you can. I don't know how much longer we can hold out.")
    }

    private suspend fun Dialogue.logs() {
        chatPlayer(happy, "Hello.")
        if (access.invTotal(access.inv, Logs) < LogsNeeded) {
            chatNpc(
                worried,
                "Hello again. We're still desperate for wood, soldier. We need six loads of " +
                    "normal logs.",
            )
            chatPlayer(neutral, "I'll see what I can do.")
            chatNpc(neutral, "Thank you.")
            return
        }
        chatNpc(worried, "Hello again. We're still desperate for wood, soldier.")
        chatPlayer(
            happy,
            "I have some here. <col=ffffff>(You give six loads of logs to the " +
                "commander.)</col>",
        )
        if (access.invDel(access.inv, Logs, LogsNeeded).failure) {
            return
        }
        quest.advanceTo(access, LogsGiven)
        chatNpc(
            happy,
            "Excellent, now we can build more defensive battlements. Give me a moment to " +
                "organise the troops, then come and see me. I'll tell you about the next phase " +
                "of our attack.",
        )
    }

    private suspend fun Dialogue.nextPhase() {
        chatPlayer(quiz, "How are you doing Montai?")
        chatNpc(
            neutral,
            "We're hanging in there, soldier. For the next phase of our attack we have to breach " +
                "their stronghold.",
        )
        chatNpc(
            neutral,
            "The ballista can break through the stronghold wall, and then we can advance and " +
                "seize back the orb.",
        )
        chatPlayer(quiz, "So what's the problem?")
        chatNpc(
            neutral,
            "From this distance we can't line up an accurate shot. We need the exact coordinates " +
                "of the stronghold for a direct hit, so I sent out three tracker gnomes to get " +
                "them.",
        )
        chatPlayer(quiz, "Have they returned?")
        chatNpc(
            worried,
            "I'm afraid not, and time is running out. I need you to go into the heart of the " +
                "battlefield, find the trackers and bring back the coordinates.",
        )
        chatNpc(quiz, "Do you think you can do it?")
        val accept =
            choice2("No, I've had enough of your battle.", false, "I'll try my best.", true)
        if (!accept) {
            chatPlayer(neutral, "No, I've had enough of your battle.")
            chatNpc(sad, "I understand. This isn't your fight.")
            return
        }
        access.player.ballistaAnswer = (0 until MaxTrackerAnswer).random()
        quest.advanceTo(access, TrackersSent)
        chatPlayer(happy, "I'll try my best.")
        chatNpc(happy, "Thank you, you're braver than most.")
        chatNpc(
            neutral,
            "I don't know how long I can hold out. Once you have the coordinates, come back and " +
                "fire the ballista right into those monsters.",
        )
        chatNpc(
            neutral,
            "If you can get the orb back and bring safety to my people, none of the blood " +
            "spilled " +
                "on this field will have been in vain.",
        )
    }

    private suspend fun Dialogue.withOrb() {
        if (!player.inv.contains(Orb)) {
            chatPlayer(happy, "Hello.")
            chatNpc(
                worried,
                "Where's the orb, soldier? You must take it back to the gnome village before " +
                    "Khazard's men find it again.",
            )
            return
        }
        chatPlayer(happy, "I have the orb of protection.")
        chatNpc(happy, "Incredible. For a human, you really are something.")
        chatPlayer(quiz, "Thanks... I think!")
        chatNpc(
            neutral,
            "I'll stay here with my troops and keep Khazard's men back. You take the orb to the " +
                "gnome village, and be quick about it. The village is still unprotected.",
        )
    }

    private companion object {
        const val Montai = "npc.commander_montai"
    }
}
