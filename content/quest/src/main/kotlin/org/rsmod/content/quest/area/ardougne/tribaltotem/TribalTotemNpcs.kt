package org.rsmod.content.quest.area.ardougne.tribaltotem

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Delivered
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.GpdtEmployee
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Horacio
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.KangaiMau
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.LabelPlaced
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Started
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Swordfish
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.SwordfishReward
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Totem
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TribalTotemNpcs
@Inject
constructor(private val tribalTotem: TribalTotemQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    private val quest
        get() = tribalTotem.quest

    override fun ScriptContext.startup() {
        onOpNpc1(KangaiMau) { startDialogue(it.npc) { kangaiDialogue() } }
        onOpNpcU(KangaiMau) {
            if (it.objType.internalName == Totem && tribalTotem.stage(player) in Started..4) {
                startDialogue(it.npc) { returnTotem() }
            } else {
                mes("Nothing interesting happens.")
            }
        }
        onOpNpc1(Horacio) { startDialogue(it.npc) { horacioDialogue() } }
        onOpNpc1(GpdtEmployee) { startDialogue(it.npc) { employeeDialogue() } }
    }

    private suspend fun Dialogue.kangaiDialogue() {
        when (quest.questState(player)) {
            QuestProgressState.NOT_STARTED -> kangaiBeforeQuest()
            QuestProgressState.IN_PROGRESS -> returnTotem()
            QuestProgressState.FINISHED -> {
                chatNpc(happy, "Many greetings esteemed thief.")
                chatPlayer(happy, "Hey.")
            }
        }
    }

    private suspend fun Dialogue.kangaiBeforeQuest() {
        chatNpc(neutral, "Hello. I Kangai Mau of the Rantuki tribe.")
        when (
            menu3(
                "And what are you doing in Brimhaven?",
                "I'm in search of adventure!",
                "Who are the Rantuki tribe?",
            )
        ) {
            0 -> {
                chatPlayer(quiz, "And what are you doing in Brimhaven?")
                kangaiMission()
            }
            1 -> {
                chatPlayer(happy, "I'm in search of adventure!")
                chatNpc(happy, "Adventure is something I may be able to give.")
                kangaiTotem()
            }
            else -> {
                chatPlayer(quiz, "Who are the Rantuki tribe?")
                chatNpc(
                    sad,
                    "A proud and noble tribe of Karamja. But now we are few, as men come from " +
                        "across sea, steal our land, and settle on our hunting grounds.",
                )
                chatPlayer(quiz, "And what are you doing in Brimhaven?")
                kangaiMission()
            }
        }
    }

    private suspend fun Dialogue.kangaiMission() {
        chatNpc(
            neutral,
            "I looking for someone brave to go on important mission for me. Someone skilled in " +
                "thievery and sneaking about. I am told I can find such people in Brimhaven.",
        )
        chatPlayer(quiz, "Tell me of this mission.")
        kangaiTotem()
    }

    private suspend fun Dialogue.kangaiTotem() {
        chatNpc(
            neutral,
            "I need someone to go on a mission to the city of Ardougne. There you will find " +
                "the house of Lord Handelmort. In his house he has our tribal totem.",
        )
        chatNpc(angry, "We need it back.")
        chatPlayer(quiz, "Why does he have it?")
        chatNpc(
            angry,
            "Lord Handelmort is an Ardougnese explorer which means he think he have the right " +
                "to come to my tribal home, steal our stuff and put in his private museum.",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "Well good luck with that.")
            chatNpc(sad, "Let's hope I find some help soon.")
            return
        }
        if (tribalTotem.stage(player) != 0) return
        quest.setQuestStage(access, Started)
        chatPlayer(happy, "Ok, I will get it back.")
        chatNpc(happy, "Best of luck with that adventurer.")
        chatPlayer(quiz, "How can I find Handelmort's house? Ardougne is a big place...")
        chatNpc(neutral, "I don't know Ardougne. You tell me.")
    }

    private suspend fun Dialogue.returnTotem() {
        chatNpc(quiz, "Have you got our totem back?")
        if (player.inv.count(Totem) == 0) {
            chatPlayer(sad, "No, it's not that easy.")
            chatNpc(angry, "Bah, you no good.")
            return
        }
        chatPlayer(happy, "Yes I have.")
        chatNpc(
            shocked,
            "You have??? Many thanks brave adventurer! Here, have some freshly cooked " +
                "Karamjan fish, caught specially by my tribe.",
        )
        objbox(Totem, "You hand over the Tribal Totem.")
        val stage = tribalTotem.stage(player)
        if (stage !in Started..4) return
        if (access.invDel(access.inv, Totem, 1).failure) return
        access.invAddOrDrop(objRepo, Swordfish, SwordfishReward)
        quest.completeQuest(access)
    }

    private suspend fun Dialogue.horacioDialogue() {
        chatNpc(happy, "It's a fine day to be out in the garden isn't it?")
        when (menu2("Yes, it's very nice.", "So... who are you?")) {
            0 -> {
                chatPlayer(happy, "Yes, it's very nice.")
                chatNpc(happy, "Days like these make me glad to be alive!")
            }
            else -> {
                chatPlayer(quiz, "So... who are you?")
                chatNpc(
                    happy,
                    "My name is Horacio Dobson. I'm the gardener to Lord Handelmort. Take a " +
                        "look around this beautiful garden, all of this is my handiwork.",
                )
                if (quest.isQuestCompleted(player)) return
                horacioGarden()
            }
        }
    }

    private suspend fun Dialogue.horacioGarden() {
        when (menu2("So... do you garden round the back too?", "Do you need any help?")) {
            0 -> {
                chatPlayer(quiz, "So... do you garden round the back too?")
                chatNpc(happy, "That I do!")
                chatPlayer(
                    quiz,
                    "Doesn't all of the security around the house get in your way then?",
                )
                chatNpc(
                    neutral,
                    "Ah, I'm used to all that. I have my keys, the guard dogs know me, and I " +
                        "know the combination to the door lock. It's rather easy, it's his " +
                        "middle name.",
                )
                chatPlayer(quiz, "Whose middle name?")
                chatNpc(
                    worried,
                    "Hum. I probably shouldn't have said that. Forget I mentioned it.",
                )
            }
            else -> {
                chatPlayer(quiz, "Do you need any help?")
                chatNpc(
                    angry,
                    "Trying to muscle in on my job eh? I'm more than happy to do this all by " +
                        "myself!",
                )
            }
        }
    }

    private suspend fun Dialogue.employeeDialogue() {
        chatNpc(happy, "Welcome to GPDT!")
        val stage = tribalTotem.stage(player)
        if (stage !in LabelPlaced..4) {
            chatPlayer(happy, "Thank you very much.")
            return
        }
        when (
            menu2(
                "So, when are you going to deliver this crate?",
                "Thank you, it's interesting in here.",
            )
        ) {
            0 -> {
                chatPlayer(quiz, "So, when are you going to deliver this crate?")
                chatNpc(neutral, "Well... I guess we could do it now...")
                if (tribalTotem.stage(player) == LabelPlaced) {
                    quest.setQuestStage(access, Delivered)
                }
            }
            else -> {
                chatPlayer(happy, "Thank you, it's interesting in here.")
                chatNpc(happy, "We're the premier delivery service in ALL of Gielinor!")
            }
        }
    }

    private suspend fun Dialogue.menu2(first: String, second: String): Int =
        choice2(first, 0, second, 1)

    private suspend fun Dialogue.menu3(first: String, second: String, third: String): Int =
        choice3(first, 0, second, 1, third, 2)
}
