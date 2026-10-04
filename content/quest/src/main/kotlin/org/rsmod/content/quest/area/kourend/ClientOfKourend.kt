package org.rsmod.content.quest.area.kourend

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.chooseLampSkill
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class ClientOfKourend @Inject constructor(private val objRepo: ObjRepository) :
    QuestScript(
        QuestKey,
        "varp.veos_quest",
        rewards {
            extra("Two 500 XP lamps")
            extra("Kharedst's memoirs")
            extra("Kourend Castle Teleport")
        },
        ItemRewardDisplay(Memoirs, zoom = 400),
        questVarbit = "varbit.veos_progress",
    ) {

    private var Player.lampsRubbed by intVarBit("varbit.veos_housereward")

    override fun ScriptContext.init() {
        check(quest.maxSteps == CompleteStage) {
            "Client of Kourend end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $CompleteStage."
        }

        for (feather in Feathers) {
            onOpHeldU(feather, Scroll) { attuneQuill(feather) }
        }
        onOpHeldU(Quill, Scroll) {
            startDialogue {
                objbox(Quill, "You can't create another quill... by using the quill on the scroll.")
            }
        }
        onOpHeld1(Orb) { activateOrb() }
        onOpHeld1(Lamp) { rubLamp() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Veos</col> on the docks of <col=800000>Port Piscarilius</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "<red>Veos</red> has a client from the mainland who wants to learn more about " +
                    "<red>Great Kourend</red>. He asked me to gather information on the five " +
                    "cities of the kingdom for them."
            )
            objective(
                "I should use a <red>feather</red> on the <red>enchanted scroll</red> Veos gave " +
                    "me to attune an <red>enchanted quill</red>."
            ) {
                visibleWhen { stage(access.player) >= StartedStage }
                custom(
                        hasQuillMade(access.player),
                        "I attuned a feather to the enchanted scroll, creating a quill.",
                    )
                    .strike()
            }
            objective(
                "With the scroll and quill I should ask the <red>general store owners</red> of " +
                    "each city about their home:"
            ) {
                visibleWhen { stage(access.player) >= StartedStage }
                stageAtLeast(
                        InfoGatheredStage,
                        "I asked the general store owners of each city about their home.",
                    )
                    .strike()
            }
            for (store in KourendStore.entries) {
                objective("<red>${store.keeper}</red> in <red>${store.city}</red>") {
                    visibleWhen { stage(access.player) == StartedStage }
                    custom(talkedTo(access.player, store), "${store.keeper} in ${store.city}")
                        .strike()
                }
            }
            objective(
                "I heard a voice whisper that I should return to <red>Veos</red> on the docks of " +
                    "<red>Port Piscarilius</red>."
            ) {
                visibleWhen { stage(access.player) >= InfoGatheredStage }
                stageAtLeast(
                        ItemsReturnedStage,
                        "I heard a voice whisper that I should return to Veos.",
                    )
                    .strike()
            }
            objective(
                "Veos's client wants me to activate a <red>mysterious orb</red> beside the " +
                    "<red>Dark Altar</red>, north of <red>Arceuus</red>."
            ) {
                visibleWhen { stage(access.player) >= ItemsReturnedStage }
                stageAtLeast(
                        OrbActivatedStage,
                        "I activated the mysterious orb beside the Dark Altar.",
                    )
                    .strike()
            }
            objective(
                "The orb shattered and the voice thanked me. I should return to <red>Veos</red> " +
                    "in <red>Port Piscarilius</red>."
            ) {
                visibleWhen { stage(access.player) >= OrbActivatedStage }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Veos asked me to gather information on the five cities of Great Kourend for a " +
                    "mysterious client of his from the mainland."
            )
            line(
                "Using an enchanted scroll and quill, I questioned the general store owners of " +
                    "each city. The client could somehow read everything I wrote."
            )
            line(
                "The client then had me activate an orb beside the Dark Altar. When I returned, " +
                    "the client took control of Veos and thanked me for my help."
            )
            line("I still don't know who the client is.")
        }

    private fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun ProtectedAccess.setStage(stage: Int) {
        quest.setQuestStage(this, stage)
    }

    fun talkedTo(player: Player, store: KourendStore): Boolean =
        player.vars[store.talkedVarbit] != 0

    private fun hasQuillMade(player: Player): Boolean =
        stage(player) > StartedStage ||
            player.inv.count(Quill) > 0 ||
            KourendStore.entries.any { talkedTo(player, it) }

    /** The extra Veos topic at Port Piscarilius, or `null` when he has nothing quest-related. */
    fun veosTopic(player: Player): String? =
        when {
            quest.isQuestCompleted(player) -> LostSomething
            stage(player) > NotStartedStage -> TalkAboutClient
            QuestRequirements.hasCompleted(player, XMarksTheSpotKey) -> AnyQuests
            else -> null
        }

    suspend fun Dialogue.veosPiscariliusQuest() {
        when (stage(player)) {
            NotStartedStage -> offerQuest()
            StartedStage -> clientCheckIn()
            InfoGatheredStage -> {
                chatPlayer(neutral, TalkAboutClient)
                returnInformation()
            }
            ItemsReturnedStage,
            OrbStage -> orbCheckIn()
            OrbActivatedStage,
            RevealStage -> revealClient()
            else -> reclaimRewards()
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(quiz, AnyQuests)
        chatNpc(happy, "Funny you should ask, ${player.displayName}. I do...")
        chatNpc(
            neutral,
            "A client of mine from the mainland would like to learn a little more about " +
                "Kourend. They've asked me to collect some information on the five cities for them.",
        )
        chatNpc(
            neutral,
            "I was going to see to it myself, but you're still fairly new around here. It could " +
                "be a good chance for you to get to know the kingdom a bit better.",
        )
        chatNpc(quiz, "What do you think?")
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "I've got too much on my plate right now, sorry. Maybe later.")
            chatNpc(neutral, "Fair enough. Come back if you'd like to help my client out.")
            return
        }
        chatPlayer(happy, "That sounds interesting! What can I do to help?")
        access.invAddOrDrop(objRepo, Scroll)
        access.setStage(StartedStage)
        objbox(Scroll, "Veos hands you an enchanted scroll.")
        chatNpc(neutral, "Here, take this scroll.")
        describeTask()
        chatPlayer(
            worried,
            "Hold on a moment! I'm not spying for somebody, am I?",
        )
        chatNpc(
            shifty,
            "Not quite... My client would just rather their 'knowledge gathering' stayed quiet.",
        )
        chatPlayer(
            quiz,
            "I suppose that's fair. Who should I talk to if I want to learn about the cities?",
        )
        chatNpc(
            neutral,
            "Plenty of folk could tell you what you need, but your best bet is probably the " +
                "general store owner in each city.",
        )
        chatNpc(
            neutral,
            "They tend to know their cities well enough, and they're always happy to have a " +
                "chat.",
        )
        chatPlayer(quiz, "Makes sense. And what about this special quill you mentioned?")
        chatNpc(
            neutral,
            "Just touch an ordinary feather to the scroll and the two will attune their magic " +
                "to each other. There's no need for any ink.",
        )
        chatPlayer(
            neutral,
            "Got it. I'll come back to you once I have the information your client is after.",
        )
        chatNpc(happy, "See you soon.")
    }

    private suspend fun Dialogue.describeTask() {
        chatNpc(
            neutral,
            "My client wants to know about the hierarchy and the general social structure of " +
                "Great Kourend, especially where it differs from the mainland.",
        )
        chatNpc(
            neutral,
            "That scroll is special. Write on it with the right kind of quill and my client " +
                "will see every word, without anything showing up on the scroll itself.",
        )
    }

    private suspend fun Dialogue.clientCheckIn() {
        chatPlayer(neutral, TalkAboutClient)
        val lost =
            choice2(
                "What am I meant to be doing, again?",
                false,
                LostSomething,
                true,
            )
        if (lost) {
            chatPlayer(sad, LostSomething)
            replaceScroll()
            return
        }
        chatPlayer(confused, "What am I meant to be doing, again?")
        describeTask()
        chatNpc(
            neutral,
            "Talk to the general store owner in each city. They should know enough about their " +
                "own city for you to get what you need, and they always seem happy to chat.",
        )
        chatNpc(neutral, "And remember: just use a feather on the scroll to attune it as a quill.")
        chatPlayer(happy, "Thanks, Veos!")
    }

    private suspend fun Dialogue.replaceScroll() {
        if (player.inv.count(Scroll) > 0) {
            chatNpc(
                laugh,
                "You've already got your scroll, ${player.displayName}. I can see it poking out " +
                    "of your backpack...",
            )
            return
        }
        if (player.inv.freeSpace() == 0) {
            chatNpc(
                neutral,
                "I'm happy to give you another scroll, ${player.displayName}, but you don't have " +
                    "any room in your backpack! Come back once you do.",
            )
            return
        }
        access.invAdd(access.inv, Scroll)
        objbox(Scroll, "Veos hands you back an enchanted scroll.")
        chatNpc(
            happy,
            "Don't worry, ${player.displayName}, I have plenty of those. And because of the way " +
                "they work, none of the information you've gathered has been lost!",
        )
        chatNpc(
            neutral,
            "Remember, if you still need a quill, just attune a feather by using it on the " +
                "scroll.",
        )
        chatPlayer(happy, "Thanks, Veos!")
    }

    private suspend fun Dialogue.returnInformation() {
        chatPlayer(neutral, "I've gathered information from every one of the cities.")
        chatPlayer(
            worried,
            "I heard a voice, Veos... inside my head! It whispered in my ear and told me to " +
                "come back to you!",
        )
        chatNpc(neutral, "Yes, I heard it as well... they said that your task was finished.")
        val held = player.inv.count(Scroll) > 0 || player.inv.count(Quill) > 0
        if (held) {
            chatNpc(neutral, "I'll take those items off your hands now.")
        }
        access.removeEverywhere(Scroll)
        access.removeEverywhere(Quill)
        access.setStage(ItemsReturnedStage)
        if (held) {
            doubleobjbox(Scroll, Quill, "Veos removes the items he gave you from your backpack.")
        }
        chatPlayer(
            angry,
            "Which reminds me, Veos! I didn't agree to this! Nobody told me something would get " +
                "inside my head. And what about nearly wrecking my hand?",
        )
        chatNpc(
            neutral,
            "If it helps at all, my client sends their apologies. It wasn't on purpose. Clearly " +
                "something you wrote stirred up some feelings...",
        )
        chatNpc(neutral, "To be specific...")
        chatNpc(neutral, "The Dark Altar...")
        chatPlayer(
            angry,
            "If I'd known the Dark Altar would upset them that much, I'd never have written it " +
                "down!",
        )
        chatNpc(
            neutral,
            "Well, that's just it, ${player.displayName}. My client is pleased you found that " +
                "out. Tell me... would you be willing to do one last thing for them?",
        )
        chatPlayer(sad, "Well, it doesn't seem like I really have a choice!")
        chatPlayer(quiz, "What do I need to do?")
        chatNpc(happy, "I'm glad to hear you can help once more.")
        chatNpc(happy, "My client will be very pleased.")
        orbInstructions()
    }

    private suspend fun Dialogue.orbCheckIn() {
        chatPlayer(neutral, TalkAboutClient)
        val topic =
            choice3(
                "So what exactly is it your client now wants me to do?",
                OrbTopic.Task,
                LostSomething,
                OrbTopic.Lost,
                "Goodbye.",
                OrbTopic.Leave,
            )
        when (topic) {
            OrbTopic.Task -> {
                chatPlayer(quiz, "So what exactly does your client want me to do now?")
                if (player.inv.count(Orb) == 0) {
                    access.invAddOrDrop(objRepo, Orb)
                    access.setStage(OrbStage)
                    objbox(Orb, "Veos hands you an enchanted orb.")
                    chatNpc(neutral, "Take this orb.")
                }
                orbInstructions()
            }
            OrbTopic.Lost -> {
                chatPlayer(sad, LostSomething)
                replaceOrb()
            }
            OrbTopic.Leave -> chatPlayer(neutral, "Goodbye.")
        }
    }

    private suspend fun Dialogue.orbInstructions() {
        chatNpc(
            neutral,
            "My client needs... one more simple task. They promise it will be the last. Take " +
                "this orb. You must activate it next to the Dark Altar, north of Arceuus.",
        )
        if (stage(player) == ItemsReturnedStage) {
            access.invAddOrDrop(objRepo, Orb)
            access.setStage(OrbStage)
        }
        chatPlayer(quiz, "Can I ask why I'd be doing that?")
        chatNpc(shifty, "It's best that you don't.")
        chatPlayer(neutral, "Well, I can't argue with that! I'll come back when it's done!")
    }

    private suspend fun Dialogue.replaceOrb() {
        if (player.inv.count(Orb) > 0) {
            chatNpc(
                laugh,
                "You've already got the orb, ${player.displayName}. I can see it poking out of " +
                    "your backpack...",
            )
            return
        }
        if (player.inv.freeSpace() == 0) {
            chatNpc(
                neutral,
                "I'm happy to give you another orb, ${player.displayName}, but you don't have " +
                    "any room in your backpack! Come back once you've made some room.",
            )
            return
        }
        access.invAdd(access.inv, Orb)
        if (stage(player) == ItemsReturnedStage) {
            access.setStage(OrbStage)
        }
        objbox(Orb, "Veos hands you another mysterious orb.")
        chatNpc(
            neutral,
            "Do be more careful, ${player.displayName}. And remember to come back to me once " +
                "you've used the orb at the altar.",
        )
        chatPlayer(happy, "Thanks, Veos!")
    }

    private suspend fun Dialogue.revealClient() {
        chatPlayer(
            angry,
            "Veos, enough is enough! I've put up with plenty. Exploding orbs and burning " +
                "quills...",
        )
        chatNpc(confused, "${player.displayName}? What are you on about?")
        chatPlayer(angry, "You know exactly what I'm on about!")
        chatNpc(
            confused,
            "${player.displayName}, I haven't talked to you since I brought you to Kourend. I " +
                "have no idea what you're going on ab--",
        )
        access.setStage(RevealStage)
        chatNpc(shocked, "ARGH!")
        chatPlayer(
            worried,
            "Veos, this has stopped being funny... Are you alright? I did everything your " +
                "client asked me to...",
        )
        client("That you did.")
        chatPlayer(confused, "Uhm... Veos?")
        client("You have served me well. For that, you have my gratitude.")
        chatPlayer(shocked, "Who are you? What have you done to Veos?")
        client("You need not worry about him. I shall see that no harm comes to him.")
        client("As for who I am... that does not matter.")
        chatPlayer(angry, "I deserve to know!")
        client("Knowledge can be a curse. Sometimes it is better not to know.")
        client(
            "Now, it is time to return this mind to consciousness... Do not worry, I will see " +
                "that he rewards you. You have been very useful to me, after all..."
        )
        repeat(LampCount) { access.invAddOrDrop(objRepo, Lamp) }
        access.invAddOrDrop(objRepo, Memoirs)
        access.removeEverywhere(Orb)
        player.lampsRubbed = 0
        quest.completeQuest(access)
    }

    private suspend fun Dialogue.client(text: String) {
        chatNpcSpecific(ClientName, ClientChathead, neutral, "<col=000080>$text</col>")
    }

    private suspend fun Dialogue.reclaimRewards() {
        chatPlayer(sad, LostSomething)
        chatNpc(confused, "But I haven't given you anything lately.")
        chatPlayer(neutral, "Yes, you did. You gave me a reward for helping your client.")
        chatNpc(confused, "Client? I don't...")
        mesbox("Veos suddenly jumps.")
        chatNpc(
            neutral,
            "Ah, it's coming back to me now. Sorry, friend, my memory isn't what it used to be.",
        )
        val memoirs = !player.owns(Memoirs) && !player.owns(BookOfTheDead)
        val lamps = (LampCount - player.lampsRubbed - player.ownedCount(Lamp)).coerceAtLeast(0)
        if (!memoirs && lamps == 0) {
            chatNpc(
                neutral,
                "I do remember rewarding you. I'm afraid I don't have anything else to give you, " +
                    "though.",
            )
            return
        }
        chatNpc(happy, "I do have a few things for you. Enjoy!")
        if (memoirs) {
            access.invAddOrDrop(objRepo, Memoirs)
        }
        repeat(lamps) { access.invAddOrDrop(objRepo, Lamp) }
    }

    /** Talk-to topic for a Kourend general store owner, or `null` when there's nothing to ask. */
    fun storeTopic(player: Player, store: KourendStore): String? {
        if (stage(player) != StartedStage || talkedTo(player, store)) {
            return null
        }
        return "Can I ask you about ${store.city}?"
    }

    suspend fun Dialogue.storeInterview(store: KourendStore) {
        if (storeTopic(player, store) == null) {
            return
        }
        when (store) {
            KourendStore.Piscarilius -> leenzInterview()
            KourendStore.Arceuus -> regathInterview()
            KourendStore.Lovakengj -> muntyInterview()
            KourendStore.Shayzien -> jenniferInterview()
            KourendStore.Hosidius -> horaceInterview()
        }
    }

    private fun Dialogue.hasWritingKit(): Boolean {
        if (player.inv.count(Scroll) > 0 && player.inv.count(Quill) > 0) {
            return true
        }
        access.mes(
            "You require the Enchanted Scroll Veos gave you and an Enchanted Quill before " +
                "gathering information from the shopkeepers."
        )
        return false
    }

    private suspend fun Dialogue.interview(
        provides: String,
        toDo: String,
        onProvides: suspend Dialogue.() -> Unit,
        onToDo: suspend Dialogue.() -> Unit,
    ) {
        while (true) {
            if (choice2(provides, true, toDo, false)) {
                onProvides()
            } else {
                onToDo()
                return
            }
        }
    }

    private suspend fun Dialogue.jotDown(store: KourendStore, suffix: String = "") {
        val last = KourendStore.entries.all { it == store || talkedTo(player, it) }
        VarPlayerIntMapSetter.set(player, store.talkedVarbit, 1)
        if (last) {
            access.setStage(InfoGatheredStage)
            mesbox(
                "As you jot down the final information on ${store.city}, you hear a mysterious " +
                    "whisper in your ear. 'Return to Veos...'"
            )
        } else {
            doubleobjbox(
                Scroll,
                Quill,
                "You jot down all of the information ${store.keeper} had to say about " +
                    "${store.city}.$suffix",
            )
        }
    }

    private suspend fun Dialogue.leenzInterview() {
        chatPlayer(quiz, "Can I ask you about Port Piscarilius?")
        chatNpc(
            happy,
            "If it's Piscarilius knowledge you'd like to find, you'll have to take a peek " +
                "inside my mind!",
        )
        if (!hasWritingKit()) {
            chatPlayer(
                sad,
                "Sorry, I meant to write all of this down, but I haven't brought the right " +
                    "equipment! Could you save that rhyme for later?",
            )
            chatNpc(
                happy,
                "If there's one thing I do better than the rest, it's sitting here patiently " +
                    "awaiting my guests!",
            )
            return
        }
        interview(
            "What is it that Port Piscarilius provides for Kourend?",
            "What is there to do in Port Piscarilius?",
            onProvides = {
                chatPlayer(
                    happy,
                    "You're a cheerful one! So what does Port Piscarilius provide for Great " +
                        "Kourend?",
                )
                chatNpc(
                    happy,
                    "Here in Port Piscarilius you'll meet, fishermen, traders, pirates and me. " +
                        "Led by our ruthless leader, Shauna Piscarilius, none can best her, " +
                        "you'll agree.",
                )
                chatNpc(
                    happy,
                    "Should you meet her, don't be surprised, her methods are cruel. With her " +
                        "in charge, every pirate's as tame as a mule.",
                )
                chatNpc(
                    happy,
                    "Our great shore and our trade, hold treasures to see, providing exports and " +
                        "fish, in quantities aplenty.",
                )
            },
            onToDo = {
                chatPlayer(quiz, "What is there to do in Port Piscarilius?")
                chatNpc(
                    happy,
                    "Our shore overflows, with gold from the sea, and the most loyal of our " +
                        "people are given the key. You may stand on our shores and collect, the " +
                        "great anglerfish, that others only... covet?",
                )
                jotDown(KourendStore.Piscarilius, suffix = " With slightly less poetry.")
                chatPlayer(confused, "Hold on a moment... that last one didn't rhyme very well!")
                chatNpc(
                    angry,
                    "Listen here, pal, nobody messes with folk in Port Piscarilius. Do you want " +
                        "cheerful rhyming Leenz, or cross, gruff and painful Leenz?",
                )
                chatPlayer(happy, "Cheerful rhyming Leenz, please! Thanks for your help!")
                chatNpc(happy, "Any time, my adventuring friend, enjoy Great Kourend to the end!")
            },
        )
    }

    private suspend fun Dialogue.regathInterview() {
        chatPlayer(quiz, "Can I ask you about Arceuus?")
        chatNpc(
            neutral,
            "This city keeps knowledge that most would consider unsafe to share. Why should I " +
                "place such trust in you?",
        )
        chatPlayer(neutral, "Well, I'm a...")
        chatPlayer(confused, "Uhm...")
        chatPlayer(
            happy,
            "A journalist! Yes, a journalist from the mainland. I want to tell the rest of the " +
                "world how this amazing city works.",
        )
        chatNpc(quiz, "Somebody willing to spread word of our great city far and wide?")
        chatNpc(
            neutral,
            "Very well, then. The rest of the world could certainly stand to learn about our " +
                "great library.",
        )
        if (!hasWritingKit()) {
            chatPlayer(
                sad,
                "I meant to write all of this down, but I don't have the right equipment with " +
                    "me! I'll be right back!",
            )
            chatNpc(
                neutral,
                "Forgetting your equipment hardly makes you a good journalist. Very well. I " +
                    "shall await your return.",
            )
            return
        }
        interview(
            "So what exactly is it the people Arceuus bring to Great Kourend?",
            "What is there to do in Arceuus?",
            onProvides = {
                chatPlayer(quiz, "So what exactly do the people of Arceuus bring to Great Kourend?")
                chatNpc(
                    neutral,
                    "Arceuus is home to scholars of both literature and magic, led by the great " +
                        "Trobin Arceuus. He drives us to push the limits of what we know about " +
                        "magic, souls and the crafting of runic energy.",
                )
                chatNpc(
                    neutral,
                    "He has no interest in petty control. He focuses instead on growing the " +
                        "knowledge of Great Kourend.",
                )
            },
            onToDo = {
                chatPlayer(quiz, "What is there to do in Arceuus?")
                chatNpc(
                    neutral,
                    "Our library is a sight to behold, but its thousands of years of runic, " +
                        "magical and religious knowledge are trifles next to the power of the " +
                        "Dark Altar.",
                )
                chatNpc(
                    neutral,
                    "Through its power we have learnt a great deal about life and death. That " +
                        "knowledge could be yours too.",
                )
                access.say("ARGH!")
                doubleobjbox(
                    Scroll,
                    Quill,
                    "Your quill and scroll glow, causing a seething pain to run through your " +
                        "hand and arm.",
                )
                chatNpc(quiz, "What is the matter, journalist?")
                chatPlayer(confused, "I... I'm not sure.")
                chatPlayer(
                    neutral,
                    "I must have caught my finger on the quill. Sorry, please go on.",
                )
                chatNpc(
                    neutral,
                    "Very well. As I said, the rewards our city offers are priceless. Our " +
                        "magical research is child's play next to the secrets of Blood and Soul " +
                        "runecrafting.",
                )
                jotDown(KourendStore.Arceuus)
                chatPlayer(happy, "Thanks for your help!")
                chatNpc(
                    neutral,
                    "You are welcome. Spread word of this great city with what you have " +
                        "learnt, and perhaps take more care with how you hold your scroll in " +
                        "future...",
                )
            },
        )
    }

    private suspend fun Dialogue.muntyInterview() {
        chatPlayer(quiz, "Can I ask you about Lovakengj?")
        chatNpc(
            happy,
            "Why, I'd love a chat! I always enjoy talking about this great city of ours.",
        )
        if (!hasWritingKit()) {
            chatPlayer(
                sad,
                "Oh dear, sorry, I meant to write all of this down but I don't have the right " +
                    "equipment! I'll be right back!",
            )
            chatNpc(happy, "Very well, I look forward to our chat!")
            return
        }
        interview(
            "What is it that Lovakengj provides for Great Kourend?",
            "What is there to do in Lovakengj?",
            onProvides = {
                chatPlayer(quiz, "What does Lovakengj provide for Great Kourend?")
                chatNpc(
                    happy,
                    "Lovakengj is full of tremendously skilled blacksmiths. Under Vulcana " +
                        "Lovakengj's leadership, our clever ways of saving materials make us the " +
                        "best blacksmiths in the land.",
                )
                chatNpc(
                    happy,
                    "Armour, weapons, tools and structures - our city supplies all of Kourend " +
                        "with them, to the finest standards any dwarf or human has seen.",
                )
            },
            onToDo = {
                chatPlayer(quiz, "What is there to do in Lovakengj?")
                chatNpc(
                    neutral,
                    "Well, making dynamite and mining in the sulphur mine aren't pretty jobs, " +
                        "but someone has to do them!",
                )
                chatNpc(
                    neutral,
                    "We also make the armour the soldiers of the Shayzien Army wear, and that's " +
                        "before you even get to the Blast Mine.",
                )
                chatNpc(happy, "The rewards to be had in our city are not to be sniffed at!")
                jotDown(KourendStore.Lovakengj)
                chatPlayer(happy, "Thanks for your help!")
                chatNpc(happy, "Any time! Do come back soon!")
            },
        )
    }

    private suspend fun Dialogue.jenniferInterview() {
        chatPlayer(quiz, "Can I ask you about Shayzien?")
        chatNpc(
            happy,
            "Well, I could give yer a bit of info if that's all yer after. It gets lonely in " +
                "'ere while me mates are out fightin' the good fight!",
        )
        if (!hasWritingKit()) {
            chatPlayer(
                sad,
                "Sorry, I meant to write all of this down, but I don't have the right " +
                    "equipment!",
            )
            chatNpc(
                laugh,
                "Well yer can't remember everythin' I 'ave to say about this wee 'ouse without " +
                    "it, can yer? Come back when yer 'ead's not so frazzled!",
            )
            return
        }
        interview(
            "What is it that Shayzien provides for Great Kourend?",
            "What is there to do in Shayzien?",
            onProvides = {
                chatPlayer(quiz, "What does Shayzien provide for Great Kourend?")
                chatNpc(
                    happy,
                    "Cor, what don't we do? This place is packed with officers an' the like. " +
                        "Led by our strict leader Shiro Shayzien, 'e makes sure all of Great " +
                        "Kourend stays safe an' sees justice.",
                )
                chatNpc(
                    neutral,
                    "Our soldiers hold off the lizardmen, an' our well-trained medics patch 'em " +
                        "up after a battle. Strict combat trainin' an' discipline. Law, order " +
                        "an' defence. That's what we live by!",
                )
            },
            onToDo = {
                chatPlayer(quiz, "What is there to do in Shayzien?")
                chatNpc(
                    neutral,
                    "It ain't all pretty 'round 'ere, but if you 'elp out our soldiers and " +
                        "deal with a bit of organised crime... you'll find the spoils o' war " +
                        "are mighty fine.",
                )
                jotDown(KourendStore.Shayzien)
                chatPlayer(happy, "Thanks for your help!")
                chatNpc(happy, "No problem, petal, off yer go an' have some fun.")
            },
        )
    }

    private suspend fun Dialogue.horaceInterview() {
        chatPlayer(quiz, "Can I ask you about Hosidius?")
        chatNpc(
            happy,
            "Well, well, my friend! I've spent my whole life in this grand and glorious city. " +
                "I'd be glad to tell you anything you'd like to know!",
        )
        if (!hasWritingKit()) {
            chatPlayer(
                sad,
                "Oh dear, I'm sorry. I meant to write all of this down but I don't have the " +
                    "right equipment! Could you hold that thought?",
            )
            chatNpc(happy, "Of course. I'm always happy to chat with my new customers!")
            return
        }
        interview(
            "What is it that Hosidius provides for Great Kourend?",
            "What is there to do in Hosidius?",
            onProvides = {
                chatPlayer(quiz, "What does Hosidius provide for Great Kourend?")
                chatNpc(
                    happy,
                    "Hosidius is full of keen and skilled farmers! We're led by the remarkably " +
                        "efficient Kandur Hosidius, whose clever resourcefulness keeps our " +
                        "farming as productive as it can be.",
                )
                chatNpc(
                    happy,
                    "Our fields stretch further than the eye can see. With great teamwork, " +
                        "feeding Great Kourend and supplying its herbs is quite literally the " +
                        "fruit of our labour.",
                )
            },
            onToDo = {
                chatPlayer(quiz, "What is there to do in Hosidius?")
                chatNpc(
                    happy,
                    "Our communal kitchens and farming patches are lovely. Still, the real " +
                        "jewels of Hosidius are the vinery and the Tithe farm. Both are world " +
                        "class!",
                )
                jotDown(KourendStore.Hosidius)
                chatPlayer(happy, "Thanks for your help, Horace!")
                chatNpc(happy, "Any time, friend! Drop by whenever you need any wares!")
            },
        )
    }

    private suspend fun ProtectedAccess.attuneQuill(feather: String) {
        if (stage(player) != StartedStage) {
            mes("Nothing interesting happens.")
            return
        }
        if (inv.count(Quill) > 0) {
            startDialogue {
                objbox(
                    Quill,
                    "You already have an Enchanted quill in your backpack. There's no need to " +
                        "make another.",
                )
            }
            return
        }
        if (invDel(inv, feather).failure) {
            return
        }
        invAddOrDrop(objRepo, Quill)
        startDialogue {
            doubleobjbox(
                Scroll,
                Quill,
                "You attune the feather to the enchanted scroll, creating an enchanted quill.",
            )
        }
    }

    private suspend fun ProtectedAccess.activateOrb() {
        if (stage(player) != OrbStage) {
            mes("Nothing interesting happens.")
            return
        }
        if (!nearDarkAltar(player.coords)) {
            startDialogue {
                objbox(
                    Orb,
                    "The orb doesn't seem to respond here. Try moving closer to the Dark Altar.",
                )
            }
            return
        }
        if (invDel(inv, Orb).failure) {
            return
        }
        invAddOrDrop(objRepo, BrokenGlass)
        setStage(OrbActivatedStage)
        say("Argh!")
        startDialogue {
            objbox(BrokenGlass, "The orb shatters after being held in the air.")
            mesbox(
                "You hear a whisper in your ear. <col=000080>'Return... to Veos. My " +
                    "thanks...'</col>"
            )
            chatPlayer(sad, "I really wish someone would warn me about these things sooner...")
        }
    }

    private suspend fun ProtectedAccess.rubLamp() {
        val stat = chooseLampSkill("Choose the stat you wish to be advanced!") ?: return
        if (invDel(inv, Lamp).failure) {
            return
        }
        player.lampsRubbed = (player.lampsRubbed + 1).coerceAtMost(LampCount)
        statAdvance(stat, LampXp)
        mes("Your wish has been granted!")
    }

    private fun ProtectedAccess.removeEverywhere(obj: String) {
        val held = inv.count(obj)
        if (held > 0) {
            invDel(inv, obj, held, strict = false)
        }
        val bank = player.invMap[BankInv] ?: return
        val banked = bank.count(obj)
        if (banked > 0) {
            invDel(bank, obj, banked, strict = false)
        }
    }

    private fun Player.owns(obj: String): Boolean = ownedCount(obj) > 0

    private fun Player.ownedCount(obj: String): Int =
        inv.count(obj) + worn.count(obj) + (invMap[BankInv]?.count(obj) ?: 0)

    private fun nearDarkAltar(coords: CoordGrid): Boolean =
        coords.level == DarkAltar.level &&
            max(abs(coords.x - DarkAltar.x), abs(coords.z - DarkAltar.z)) <= DarkAltarRadius

    private enum class OrbTopic {
        Task,
        Lost,
        Leave,
    }

    private companion object {
        private const val QuestKey = "quest_clientofkourend"
        private const val XMarksTheSpotKey = "quest_xmarksthespot"

        private const val NotStartedStage = 0
        private const val StartedStage = 1
        private const val InfoGatheredStage = 2
        private const val ItemsReturnedStage = 3
        private const val OrbStage = 4
        private const val OrbActivatedStage = 5
        private const val RevealStage = 6
        private const val CompleteStage = 7

        private const val Scroll = "obj.veos_scroll"
        private const val Quill = "obj.veos_quill"
        private const val Orb = "obj.veos_orb"
        private const val Lamp = "obj.veos_lamp"
        private const val Memoirs = "obj.veos_kharedsts_memoirs"
        private const val BookOfTheDead = "obj.book_of_the_dead"
        private const val BrokenGlass = "obj.broken_glass"
        private const val BankInv = "inv.bank"

        private val Feathers =
            listOf(
                "obj.feather",
                "obj.hunting_stripy_bird_feather",
                "obj.hunting_jungle_feather",
                "obj.hunting_polar_feather",
                "obj.hunting_desert_feather",
                "obj.hunting_woodland_feather",
                "obj.hunting_eagle_feather",
            )

        private const val AnyQuests = "Have you got any quests for me?"
        private const val TalkAboutClient = "Let's talk about your client..."
        private const val LostSomething = "I've lost something you've given me."

        private const val ClientName = "Veos' Client"
        private const val ClientChathead = "npc.veos_controlled"

        private const val LampCount = 2
        private const val LampXp = 500.0

        private val DarkAltar = CoordGrid(1716, 3883, 0)
        private const val DarkAltarRadius = 10
    }
}
