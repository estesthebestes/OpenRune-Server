package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_CROSSED_WALL
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_DISTRACTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_MET_OMART
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.ardougne.wearingGasMask
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Omart on the East Ardougne side of the wall and Kilron on the West, Jerico's two friends with
 * the rope ladder. Once the watchtower guards are distracted they will throw it over for the
 * player in either direction, until the distillator is back with Elena and it becomes too risky.
 * Both are multi-npcs, so the ops sit on the base types.
 */
class RopeLadder @Inject constructor(private val biohazard: BiohazardQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(OMART) { startDialogue(it.npc) { omart() } }
        onOpNpc1(KILRON) { startDialogue(it.npc) { kilron() } }
    }

    private suspend fun Dialogue.omart() {
        when (biohazard.stage(player)) {
            STAGE_STARTED -> {
                chatPlayer(neutral, "Omart, Jerico said you might be able to help me.")
                chatNpc(
                    neutral,
                    "He informed me of your problem traveller. I would be glad to help, I have " +
                        "a rope ladder and my associate, Kilron, is waiting on the other side.",
                )
                chatPlayer(happy, "Good stuff.")
                chatNpc(
                    worried,
                    "Unfortunately we can't risk it with the watchtower so close. So first we " +
                        "need to distract the guards in the tower.",
                )
                chatPlayer(quiz, "How?")
                chatNpc(
                    neutral,
                    "I reckon some of Jerico's pigeons might help us out here. Chuck some bird " +
                        "feed at the tower and then release some pigeons nearby. They should " +
                        "provide a nice distraction.",
                )
                chatPlayer(quiz, "Where do I get the bird feed and the pigeons?")
                chatNpc(
                    neutral,
                    "I'm sure Jerico can help you out with that if you ask him nicely.",
                )
                player.metOmart = true
                biohazard.advanceTo(access, STAGE_MET_OMART)
            }
            STAGE_MET_OMART -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(quiz, "Hello. Have you sorted that distraction yet?")
                chatPlayer(neutral, "Not yet.")
                chatNpc(
                    neutral,
                    "Try using some of Jerico's pigeons. Chuck some bird feed at the tower and " +
                        "then release some pigeons nearby. Jerico should be able to help you out.",
                )
            }
            STAGE_DISTRACTED -> {
                chatNpc(
                    happy,
                    "Well done, the guards are having real trouble with those birds. You must " +
                        "go now traveller, it's your only chance.",
                )
                crossingOptions(firstTime = true)
            }
            in STAGE_CROSSED_WALL until STAGE_GOT_SAMPLES -> {
                chatPlayer(happy, "Hello Omart.")
                chatNpc(quiz, "Hello traveller. Do you wish to cross the wall?")
                crossingOptions(firstTime = false)
            }
            in STAGE_GOT_SAMPLES until biohazard.quest.maxSteps -> {
                chatPlayer(happy, "Hello Omart.")
                chatNpc(
                    worried,
                    "Hello adventurer. I'm afraid it's too risky to use the ladder again. " +
                        "You'll have to find another way into the city.",
                )
            }
            else -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(neutral, "Hello.")
                chatPlayer(quiz, "How are you?")
                chatNpc(neutral, "Fine thanks.")
            }
        }
    }

    private suspend fun Dialogue.crossingOptions(firstTime: Boolean) {
        when (choice2("Okay, let's do it.", 1, "I'll be back soon.", 2)) {
            1 -> {
                chatPlayer(happy, "Okay, let's do it.")
                if (firstTime && !player.wearingGasMask()) {
                    chatNpc(
                        worried,
                        "I'd recommend you wear a gas mask before entering West Ardougne. You " +
                            "don't want to risk catching the plague.",
                    )
                    chatPlayer(neutral, "That would be wise. Thanks Omart.")
                    return
                }
                access.crossWall(thrower = "Omart", from = EAST_LANDING, to = WEST_LANDING)
                if (firstTime) {
                    biohazard.advanceTo(access, STAGE_CROSSED_WALL)
                    chatNpcSpecific(
                        "Kilron",
                        KILRON_HEAD,
                        happy,
                        "Welcome to West Ardougne friend. I'm Kilron. Let me know if you want to " +
                            "go back over the wall.",
                    )
                    chatPlayer(
                        quiz,
                        "Thanks Kilron. Do you know where to find the Mourner Headquarters?",
                    )
                    chatNpcSpecific(
                        "Kilron",
                        KILRON_HEAD,
                        neutral,
                        "It's the most north eastern building in the city. Be careful around " +
                            "there.",
                    )
                }
            }
            2 -> {
                chatPlayer(neutral, "I'll be back soon.")
                if (firstTime) {
                    chatNpc(
                        worried,
                        "Don't take too long, those mourners will soon be rid of those birds.",
                    )
                }
            }
        }
    }

    private suspend fun Dialogue.kilron() {
        if (!biohazard.ladderAvailable(player) || biohazard.stage(player) < STAGE_CROSSED_WALL) {
            chatPlayer(happy, "Hello there.")
            chatNpc(neutral, "Hello.")
            chatPlayer(quiz, "How are you?")
            chatNpc(neutral, "Busy.")
            return
        }
        chatPlayer(happy, "Hello Kilron.")
        chatNpc(quiz, "Hello traveller. Do you need to go back over?")
        when (
            choice3(
                "Yes I do.",
                1,
                "Do you know where to find the Mourner Headquarters?",
                2,
                "Not yet Kilron.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Yes I do.")
                chatNpc(neutral, "Okay, quickly now!")
                access.crossWall(thrower = "Kilron", from = WEST_LANDING, to = EAST_LANDING)
            }
            2 -> {
                chatPlayer(quiz, "Do you know where to find the Mourner Headquarters?")
                chatNpc(
                    neutral,
                    "It's the most north eastern building in the city. Be careful around there.",
                )
            }
            3 -> {
                chatPlayer(neutral, "Not yet Kilron.")
                chatNpc(neutral, "Okay, just give me the word.")
            }
        }
    }

    private suspend fun ProtectedAccess.crossWall(thrower: String, from: CoordGrid, to: CoordGrid) {
        mesbox("$thrower throws a rope ladder over the wall.")
        teleport(from)
        faceSquare(if (to.x > from.x) from.translateX(1) else from.translateX(-1))
        anim(CLIMB_SEQ)
        soundSynth(CLIMB_SOUND)
        mesbox("You climb up the rope ladder...")
        fadeToBlack()
        telejump(to)
        soundSynth(LAND_SOUND)
        fadeFromBlack()
        mesbox("...and drop down on the other side.")
    }

    private companion object {
        const val OMART = "npc.omart"
        const val KILRON = "npc.kilron"
        const val KILRON_HEAD = "npc.kilron_vis"

        val EAST_LANDING = CoordGrid(2559, 3267, 0)
        val WEST_LANDING = CoordGrid(2556, 3267, 0)

        const val CLIMB_SEQ = "seq.human_climbing"
        const val CLIMB_SOUND = "synth.ropeclimb"
        const val LAND_SOUND = "synth.jump_and_fall"
    }
}
