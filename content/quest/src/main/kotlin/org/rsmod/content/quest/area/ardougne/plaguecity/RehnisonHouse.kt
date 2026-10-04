package org.rsmod.content.quest.area.ardougne.plaguecity

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.BOOK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_BOOK_RETURNED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_FREED_ELENA
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GOT_BOOK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_TALKED_MILLI
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Rehnison family's timbered house against the north wall of West Ardougne. Ted only opens
 * the door to someone returning Jethick's book; their daughter Milli upstairs saw Elena being
 * taken.
 */
class RehnisonHouse
@Inject
constructor(private val plagueCity: PlagueCityQuest, private val doors: QuestDoors) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(DOOR) { frontDoor(it.loc) }
        onOpLoc1(STAIRS_UP) { climbStairs(UPSTAIRS) }
        onOpLoc1(STAIRS_DOWN) { climbStairs(DOWNSTAIRS) }
        onOpNpc1(TED) { startDialogue(it.npc) { parent() } }
        onOpNpc1(MARTHA) { startDialogue(it.npc) { parent() } }
        onOpNpc1(BILLY) { mes("Billy isn't interested in talking.") }
        onOpNpc1(MILLI) { startDialogue(it.npc) { milli() } }
    }

    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        val inside = player.coords.z > door.coords.z
        val stage = plagueCity.stage(player)
        when {
            inside || stage >= STAGE_BOOK_RETURNED -> doors.open(this, door, DOOR_OPEN)
            stage == STAGE_GOT_BOOK && inv.contains(BOOK) -> returnBook(door)
            else ->
                startDialogue {
                    chatNpcSpecific("Ted Rehnison", TED, angry, "Go away. We don't want any.")
                }
        }
    }

    private suspend fun ProtectedAccess.returnBook(door: BoundLocInfo) {
        startDialogue {
            chatNpcSpecific("Ted Rehnison", TED, angry, "Go away. We don't want any.")
            chatPlayer(
                neutral,
                "I'm a friend of Jethick's, I have come to return a book he borrowed.",
            )
            chatNpcSpecific(
                "Ted Rehnison",
                TED,
                happy,
                "Oh... Why didn't you say, come in then.",
            )
            if (access.invDel(player.inv, BOOK).failure) {
                return@startDialogue
            }
            plagueCity.advanceTo(access, STAGE_BOOK_RETURNED)
            doors.open(access, door, DOOR_OPEN)
            objbox(BOOK, "You hand the book to Ted as you enter.")
            chatNpcSpecific("Ted Rehnison", TED, happy, "Thanks, I've been missing that.")
        }
    }

    private suspend fun ProtectedAccess.climbStairs(dest: CoordGrid) {
        arriveDelay()
        delay(1)
        telejump(dest)
    }

    private suspend fun Dialogue.parent() {
        val stage = plagueCity.stage(player)
        when {
            stage < STAGE_BOOK_RETURNED -> chatNpc(angry, "Go away. We don't want any.")
            stage == STAGE_BOOK_RETURNED -> {
                chatPlayer(neutral, "Hi, I hear a woman called Elena is staying here.")
                chatNpc(
                    neutral,
                    "Yes she was staying here, but slightly over a week ago she was getting " +
                        "ready to go back. However she never managed to leave.",
                )
                chatNpc(
                    worried,
                    "My daughter Milli was playing near the west wall when she saw some shadowy " +
                        "figures jump out and grab her. Milli is upstairs if you wish to speak " +
                        "to her.",
                )
            }
            stage < STAGE_FREED_ELENA -> {
                chatNpc(quiz, "Any luck finding Elena yet?")
                chatPlayer(sad, "Not yet...")
                chatNpc(neutral, "I wish you luck, she did a lot for us.")
            }
            else -> {
                chatNpc(quiz, "Any luck finding Elena yet?")
                chatPlayer(happy, "Yes, she is safe at home now.")
                chatNpc(happy, "That's good to hear, she helped us a lot.")
            }
        }
    }

    private suspend fun Dialogue.milli() {
        val stage = plagueCity.stage(player)
        when {
            stage < STAGE_BOOK_RETURNED ->
                chatNpc(sad, "*sniff* Go away, I'm not supposed to talk to strangers.")
            stage == STAGE_BOOK_RETURNED -> {
                chatPlayer(neutral, "Hello. Your parents say you saw what happened to Elena...")
                chatNpc(
                    sad,
                    "*sniff* Yes I was near the south east corner when I saw Elena walking by. " +
                        "I was about to run to greet her when some men jumped out. They shoved " +
                        "a sack over her head and dragged her into a building.",
                )
                chatPlayer(quiz, "Which building?")
                chatNpc(
                    sad,
                    "It was the boarded up building with no windows in the south east corner " +
                        "of West Ardougne.",
                )
                plagueCity.advanceTo(access, STAGE_TALKED_MILLI)
            }
            stage < STAGE_FREED_ELENA -> {
                chatNpc(quiz, "Have you found Elena yet?")
                chatPlayer(neutral, "No, I'm still looking.")
                chatNpc(sad, "I hope you find her. She was nice.")
            }
            else -> {
                chatNpc(quiz, "Have you found Elena yet?")
                chatPlayer(happy, "Yes, she's safe at home.")
                chatNpc(happy, "I hope she comes and visits sometime.")
                chatPlayer(neutral, "Maybe.")
            }
        }
    }

    companion object {
        const val DOOR = "loc.rehnisondoorshut"
        const val DOOR_OPEN = "loc.rehnisondooropen"
        const val STAIRS_UP = "loc.rehnisonstairs"
        const val STAIRS_DOWN = "loc.rehnisonstairstop"
        const val TED = "npc.ted_rehnison"
        const val MARTHA = "npc.martha_rehnison"
        const val BILLY = "npc.billy_rehnison"
        const val MILLI = "npc.milli"

        val UPSTAIRS = CoordGrid(2529, 3332, 1)
        val DOWNSTAIRS = CoordGrid(2529, 3332, 0)
    }
}
