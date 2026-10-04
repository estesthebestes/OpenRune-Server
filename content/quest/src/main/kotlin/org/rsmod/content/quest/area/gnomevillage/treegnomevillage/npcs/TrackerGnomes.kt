package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.HasOrb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Started
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.StrongholdBreached
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.TrackersSent
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.ballistaAnswer
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.knowsHeight
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.knowsX
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.knowsY
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three tracker gnomes Montai sent to scout the stronghold: one hiding north-west of it, one
 * locked in the Khazard cell, and one who has lost his mind and only hints at the x coordinate.
 */
class TrackerGnomes @Inject constructor(private val quest: TreeGnomeVillageQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(Tracker1) { startDialogue(it.npc) { tracker1() } }
        onOpNpc1(Tracker2) { startDialogue(it.npc) { tracker2() } }
        onOpNpc1(Tracker3) { startDialogue(it.npc) { tracker3() } }
    }

    private suspend fun Dialogue.tracker1() {
        when (quest.stage(player)) {
            in 0 until TrackersSent -> {
                chatPlayer(happy, "Hello.")
                chatNpc(angry, "I can't talk now. Can't you see we're trying to win a battle here?")
            }
            TrackersSent -> {
                chatPlayer(quiz, "Do you know the coordinates of the Khazard stronghold?")
                chatNpc(neutral, "I managed to get one, although it wasn't easy.")
                player.knowsHeight = true
                mesbox("The gnome tells you the <col=000080>height</col> coordinate.")
                chatPlayer(happy, "Well done.")
                chatNpc(
                    neutral,
                    "The other two tracker gnomes should have the rest, if they're still alive.",
                )
                chatPlayer(neutral, "OK, take care.")
            }
            StrongholdBreached -> afterBreach()
            HasOrb -> {
                chatPlayer(quiz, "How are you tracker?")
                chatNpc(
                    happy,
                    "Now that we have the orb I'm much better. They won't stand a chance " +
                    "without it.",
                )
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    sad,
                    "When will this battle end? I feel like I've been fighting forever.",
                )
            }
        }
    }

    private suspend fun Dialogue.tracker2() {
        when (quest.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    worried,
                    "I can't talk now. If the guards catch me I'll be dead gnome meat.",
                )
            }
            in Started until TrackersSent -> {
                chatPlayer(happy, "Hi there.")
                chatNpc(
                    neutral,
                    "The battle is far from over. If your heart is pure, you will help us win.",
                )
            }
            TrackersSent -> {
                chatPlayer(quiz, "Are you OK?")
                chatNpc(sad, "They caught me spying on the stronghold. They beat and tortured me.")
                chatNpc(angry, "But I didn't crack. I told them nothing. They can't break me!")
                chatPlayer(sad, "I'm sorry little man.")
                chatNpc(happy, "Don't be. I know where the stronghold is!")
                player.knowsY = true
                mesbox("The gnome tells you the <col=000080>y coordinate.</col>")
                chatPlayer(happy, "Well done.")
                chatNpc(worried, "Now leave, before they find you and all is lost.")
                chatPlayer(neutral, "Hang in there.")
                chatNpc(worried, "Go!")
            }
            StrongholdBreached -> afterBreach()
            HasOrb -> {
                chatPlayer(quiz, "How are you tracker?")
                chatNpc(
                    happy,
                    "Now that we have the orb I'm much better. Soon my comrades will come and " +
                        "free me.",
                )
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    sad,
                    "When will this battle end? I feel like I've been locked up my whole life.",
                )
            }
        }
    }

    private suspend fun Dialogue.tracker3() {
        when (quest.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(angry, "I can't talk now. Can't you see we're trying to win a battle here?")
            }
            in Started until TrackersSent -> {
                chatPlayer(happy, "Hi there.")
                chatNpc(
                    worried,
                    "I can't stand this war. The misery, the pain, it's driving me crazy! When " +
                        "will it end?",
                )
            }
            TrackersSent -> {
                chatPlayer(quiz, "Are you OK?")
                chatNpc(laugh, "OK? Who's OK? Not me! Hee hee!")
                chatPlayer(quiz, "What's wrong?")
                chatNpc(
                    worried,
                    "You can't see me, no one can. Monsters, demons, they're all around me!",
                )
                chatPlayer(quiz, "What do you mean?")
                chatNpc(laugh, "They're dancing, all of them, hee hee.")
                mesbox("He's clearly lost the plot.")
                chatPlayer(quiz, "Do you have the coordinate for the Khazard stronghold?")
                chatNpc(quiz, "Who holds the stronghold?")
                chatPlayer(confused, "What?")
                chatNpc(laugh, riddle(player.ballistaAnswer + 1))
                player.knowsX = true
                chatPlayer(neutral, "You're mad.")
                chatNpc(laugh, "Dance with me, and Khazard's men are beat.")
                mesbox("The toll of war has affected his mind.")
                chatPlayer(sad, "I'll pray for you little man.")
                chatNpc(laugh, "All day we pray in the hay, hee hee.")
            }
            StrongholdBreached,
            HasOrb -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(
                    worried,
                    "Don't talk to me, you can't see me. No one can, just the demons.",
                )
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(confused, "I feel dizzy, where am I? Oh dear, oh dear I need some rest.")
                chatPlayer(neutral, "I think you do.")
            }
        }
    }

    private suspend fun Dialogue.afterBreach() {
        chatPlayer(happy, "Hello again.")
        chatNpc(
            happy,
            "Well done, you've broken down their defences. This battle must be ours.",
        )
    }

    private fun riddle(x: Int): String =
        when (x) {
            1 -> "Less than my hands."
            2 -> "More than my head, less than my fingers."
            3 -> "More than we, less than our feet."
            else -> "My legs and your legs, ha ha ha!"
        }

    private companion object {
        const val Tracker1 = "npc.tracker1"
        const val Tracker2 = "npc.tracker2"
        const val Tracker3 = "npc.tracker3"
    }
}
