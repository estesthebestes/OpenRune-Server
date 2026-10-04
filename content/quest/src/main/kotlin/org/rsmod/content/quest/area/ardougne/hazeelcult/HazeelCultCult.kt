package org.rsmod.content.quest.area.ardougne.hazeelcult

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.ChooseSide
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.FoodPoisoned
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.HazeelMark
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.HazeelScroll
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.InHideout
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.Poison
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.SideChosen
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.Started
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Clivet, Alomone and the rank and file of the Hazeel cult. */
class HazeelCultCult
@Inject
constructor(
    private val quest: HazeelCultQuest,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(Clivet) { startDialogue(it.npc) { clivet() } }
        onOpNpc1(ClivetHideout) {
            startDialogue(it.npc) {
                chatNpc(angry, "You! Leave this place at once, before we're forced to make you!")
            }
        }
        onOpNpc1(Alomone) { startDialogue(it.npc) { alomone(it.npc) } }
        for (cultist in listOf(Cultist, CultistTwo)) {
            onOpNpc1(cultist) { startDialogue(it.npc) { cultist() } }
        }
    }

    private suspend fun Dialogue.cultist() {
        chatPlayer(neutral, "Hello.")
        if (player.sidedWithCeril) {
            chatNpc(angry, "You have no place here! Leave now, before someone makes you!")
        } else {
            chatNpc(
                neutral,
                "I cannot talk right now. Lord Hazeel will soon be back with us and I have many " +
                    "preparations to make.",
            )
        }
    }

    private suspend fun Dialogue.clivet() {
        val stage = quest.stage(player)
        when {
            stage < Started -> {
                chatPlayer(quiz, "Hello there. Who are you?")
                chatNpc(neutral, "You can call me Clivet. What do you want, adventurer?")
                chatPlayer(neutral, "Nothing, sorry to bother you.")
            }
            stage == Started -> {
                confrontation()
                choosing()
            }
            stage == ChooseSide -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    neutral,
                    "So, you've returned. I hope you're here because you've recognised the folly " +
                        "in serving the Carnilleans. Think about what you could achieve if you " +
                        "join us instead.",
                )
                choosing()
            }
            stage == SideChosen -> poisonMission()
            stage == FoodPoisoned -> afterPoisoning()
            else -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(happy, "It is good to see you once more, adventurer. Glory to Hazeel!")
            }
        }
    }

    private suspend fun Dialogue.confrontation() {
        chatPlayer(quiz, "Hello there. Who are you?")
        chatNpc(neutral, "You can call me Clivet. What do you want, adventurer?")
        chatPlayer(quiz, "Do you know the Carnilleans?")
        chatNpc(shifty, "Carnilleans? Don't think I've ever heard of them.")
        chatPlayer(angry, "Look, I know you're lying. I know all about your cult.")
        chatNpc(angry, "Is that so? In that case, I'd say you'd be wise to leave now.")
        chatPlayer(angry, "Unfortunately, I have a job to do. I'm not leaving until it's done.")
        chatNpc(
            angry,
            "Ah, I see. That two-faced snob has made you fall for his propaganda, eh?",
        )
        chatPlayer(confused, "What's that supposed to mean?")
        chatNpc(
            angry,
            "The Carnillean home does not belong to them. It was built long ago by the great " +
                "Lord Hazeel, who once ruled over this entire region.",
        )
        chatNpc(
            angry,
            "Many years ago, the Carnilleans led a rebellion against Lord Hazeel and all who " +
                "followed him. Under the cover of darkness, they stormed his home in an angry " +
                "mob, torturing and butchering everyone they found.",
        )
        chatNpc(
            angry,
            "The following morning, the Carnillean forefathers moved into the empty household " +
                "and claimed it as their own. They have grown fat on the hard work of Lord " +
                "Hazeel ever since.",
        )
        chatPlayer(quiz, "Right... And what happened to this Hazeel?")
        chatNpc(
            neutral,
            "The foolish Carnilleans believed him dead. Unluckily for them, being an " +
                "all-powerful Mahjarrat gave him access to powers and enchantments they knew " +
                "nothing of.",
        )
        chatNpc(
            neutral,
            "As they stormed his home, Lord Hazeel made preparations for his eventual return. " +
                "We, his faithful followers, have been working to bring that about ever since.",
        )
        chatPlayer(
            quiz,
            "What makes you think I care about the politics and histories of Ardougne?",
        )
        quest.advanceTo(access, ChooseSide)
        chatNpc(
            neutral,
            "I could kill you right now and be done with it, but I don't think you're the fool " +
                "that the Carnilleans believe you to be. Instead, I offer you a chance to join us.",
        )
        chatNpc(
            neutral,
            "Help us bring about the return of Lord Hazeel, and you will be well rewarded. " +
                "However, should you continue to try and prevent his return, you will suffer the " +
                "wrath of Zamorak.",
        )
        chatPlayer(confused, "Hmm...")
    }

    private suspend fun Dialogue.choosing() {
        while (true) {
            when (
                choice3(
                    "Before I decide, I have some questions for you.",
                    Choice.Questions,
                    "Alright, I've made my decision.",
                    Choice.Decide,
                    "I need some time to think on this.",
                    Choice.Later,
                )
            ) {
                Choice.Questions -> questions()
                Choice.Decide -> {
                    decide()
                    return
                }
                Choice.Later -> {
                    chatPlayer(neutral, "I need some time to think on this.")
                    chatNpc(neutral, "Don't take too long.")
                    return
                }
            }
        }
    }

    private enum class Choice {
        Questions,
        Decide,
        Later,
    }

    private suspend fun Dialogue.questions() {
        chatPlayer(neutral, "Before I decide, I have some questions for you.")
        chatNpc(neutral, "Very well.")
        while (true) {
            when (
                menu(
                    "Tell me more about Hazeel." to 1,
                    "Why did the Carnilleans rebel against Hazeel?" to 2,
                    "Why do you keep breaking into the Carnillean Mansion?" to 3,
                    "What can you tell me about the Mahjarrat?" to 4,
                    "Actually, I have no questions." to 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Tell me more about Hazeel.")
                    chatNpc(
                        neutral,
                        "Lord Hazeel is one of the great Mahjarrat who once fought alongside " +
                            "Zamorak himself. He ruled this region for many years, until the " +
                            "cowardly Carnilleans rebelled against him.",
                    )
                    chatPlayer(quiz, "But he survived?")
                    chatNpc(
                        happy,
                        "Of course! The Mahjarrat know magic that others can only dream of. Lord " +
                            "Hazeel used his abilities to ensure that one day he'd eventually " +
                            "return. That day will soon be upon us.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Why did the Carnilleans rebel against Hazeel?")
                    chatNpc(
                        angry,
                        "The Carnilleans served the pathetic Saradomin. They considered it their " +
                            "duty to overthrow Hazeel due to his loyalties to Zamorak. They were " +
                            "fools, blinded by Saradominist propaganda!",
                    )
                    chatPlayer(confused, "Saradominist propaganda?")
                    chatNpc(
                        angry,
                        "Yes! Something even more prevalent today. Too many people now are " +
                            "fooled into believing that Saradomin is a god of good, while " +
                            "Zamorak is evil. Those lies couldn't be further from the truth!",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Why do you keep breaking into the Carnillean Mansion?")
                    chatNpc(
                        shifty,
                        "There is something we need inside. I will say no more unless you agree " +
                            "to join us.",
                    )
                }
                4 -> {
                    chatPlayer(quiz, "What can you tell me about the Mahjarrat?")
                    chatNpc(
                        happy,
                        "Surely you have heard of the Mahjarrat? Their race is the ultimate " +
                            "example of power! Each of them wields the strength of an entire " +
                            "army. To oppose any one of them, is to face your doom!",
                    )
                    chatPlayer(quiz, "And Hazeel is one of them?")
                    chatNpc(
                        happy,
                        "More than that! Hazeel is one of the greatest Mahjarrat to have ever " +
                            "lived!",
                    )
                    chatPlayer(neutral, "I see.")
                }
                else -> {
                    chatPlayer(neutral, "Actually, I have no questions.")
                    chatNpc(
                        neutral,
                        "Then we have nothing more to discuss until you've made your choice.",
                    )
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.decide() {
        chatPlayer(neutral, "Alright, I've made my decision.")
        chatNpc(quiz, "And?")
        when (
            choice3(
                "I'll help you.",
                1,
                "I won't help you.",
                2,
                "Actually, I need more time to think on this.",
                3,
            )
        ) {
            1 -> joinCult()
            2 -> refuseCult()
            else -> {
                chatPlayer(neutral, "Actually, I need more time to think on this.")
                chatNpc(neutral, "Don't take too long.")
            }
        }
    }

    private suspend fun Dialogue.joinCult() {
        chatPlayer(neutral, "I'll help you.")
        chatNpc(
            neutral,
            "Good. Now, we are not the monsters that the Carnilleans would claim we are, so we " +
                "were hoping to do this without violence. However, the fact they sent you here " +
                "makes it clear that won't work now.",
        )
        chatPlayer(quiz, "Meaning?")
        chatNpc(
            angry,
            "You must prove your loyalty to our cause, and Ceril Carnillean must die. Kill him " +
                "for us, and we will know you are truly dedicated to Lord Hazeel.",
        )
        val agree =
            choice2(
                "Alright, how do I do it?",
                true,
                "I need some time to think on this.",
                false,
            )
        if (!agree) {
            chatPlayer(neutral, "I need some time to think on this.")
            chatNpc(neutral, "Don't take too long.")
            return
        }
        chatPlayer(neutral, "Alright, how do I do it?")
        quest.quest.setQuestStage(access, SideChosen)
        chatNpc(
            happy,
            "I can already tell I was right about you. I can see you are of exactly the right " +
                "character to join the followers of Hazeel.",
        )
        chatNpc(
            neutral,
            "Here, take this poison and pour it into Ceril Carnillean's food. Once the deed is " +
                "done, return here and speak to me once more.",
        )
        givePoison(firstTime = true)
    }

    private suspend fun Dialogue.refuseCult() {
        chatPlayer(neutral, "I won't help you.")
        chatNpc(
            angry,
            "Then you are a fool. Go back to your small-minded little life. You will never know " +
                "the glories you could have tasted as one of us!",
        )
        chatPlayer(angry, "You know I'm going to stop you, right?")
        chatNpc(angry, "You'll try, but you'll never find us.")
        player.hazeelClivetLocation = 1
        quest.quest.setQuestStage(access, SideChosen)
        mesbox("Clivet jumps onto the nearby raft and pushes off into the sewer system.")
    }

    private suspend fun Dialogue.givePoison(firstTime: Boolean) {
        if (!player.inv.hasFreeSpace()) {
            objbox(
                Poison,
                "Clivet tries to give you some poison, but you don't have enough room to take it.",
            )
            return
        }
        access.invAdd(access.inv, Poison, 1)
        if (firstTime) {
            player.hazeelGivenPoison = true
        }
        objbox(Poison, "Clivet gives you some poison.")
    }

    private suspend fun Dialogue.poisonMission() {
        chatPlayer(neutral, "Hello there.")
        if (!player.hazeelGivenPoison) {
            chatNpc(
                neutral,
                "Here's that poison you need. Take it and pour it into Ceril Carnillean's food. " +
                    "Once the deed is done, return here and speak to me once more.",
            )
            givePoison(firstTime = true)
            return
        }
        chatNpc(
            neutral,
            "You have a mission for us, adventurer. Go to the Carnillean household and poison " +
                "Ceril Carnillean's meal to prove your loyalty.",
        )
        if (player.inv.contains(Poison)) {
            return
        }
        chatPlayer(sad, "I need some more poison.")
        chatNpc(angry, "Fool! Be more careful with it this time.")
        givePoison(firstTime = false)
    }

    private suspend fun Dialogue.afterPoisoning() {
        if (!player.hazeelPoisonSuccess) {
            chatPlayer(neutral, "Hello there.")
            chatNpc(
                neutral,
                "You have a mission for us, adventurer. Go to the Carnillean household and " +
                    "poison Ceril Carnillean's meal to prove your loyalty.",
            )
            return
        }
        if (!player.hazeelSewerChat) {
            reportPoisoning()
            return
        }
        if (!player.hazeelGivenAmulet) {
            chatNpc(neutral, "Here, you'll need this.")
            giveAmulet()
            return
        }
        val lost = !player.inv.contains(HazeelMark) && !player.worn.contains(HazeelMark)
        val topic =
            menu(
                buildList {
                    add("What am I meant to be doing?" to 1)
                    if (lost) add("I lost that amulet you gave me." to 2)
                }
            )
        if (topic == 2) {
            chatPlayer(sad, "I lost that amulet you gave me.")
            chatNpc(angry, "Careless! Look after this one!")
            handOverAmulet(announceDirections = false)
            return
        }
        chatPlayer(quiz, "What am I meant to be doing?")
        chatNpc(
            neutral,
            "Use the raft here to enter our hideout and meet with our leader, Alomone.",
        )
        chatNpc(
            neutral,
            "Be warned, you'll need to ensure the flow of water down here is set correctly for " +
                "you to be able to use the raft.",
        )
        chatNpc(
            neutral,
            "Going from left to right, turn each of the five sewer valves above so that they " +
                "follow the design of the amulet I gave you.",
        )
    }

    private suspend fun Dialogue.reportPoisoning() {
        chatPlayer(
            neutral,
            "I poisoned the food as requested, but it didn't quite go to plan.",
        )
        chatNpc(
            happy,
            "Yes, we heard all about it from one of our sources. It's a shame that Ceril " +
                "Carnillean survived, but that is no fault of yours. You have proven your " +
                "loyalty to Hazeel.",
        )
        chatPlayer(quiz, "So what now?")
        chatNpc(
            neutral,
            "I would like you to meet with our leader, Alomone. You'll find him in our hideout, " +
                "deeper in the sewers. You can use this raft to get there.",
        )
        chatNpc(
            neutral,
            "However, be warned that reaching the hideout is impossible unless the flow of " +
                "water down here is set correctly. You'll need to use the sewer valves above " +
                "to do that.",
        )
        player.hazeelSewerChat = true
        chatPlayer(quiz, "But how will I know how to set the valves?")
        chatNpc(neutral, "For that, you'll need this.")
        giveAmulet()
    }

    private suspend fun Dialogue.giveAmulet() = handOverAmulet(announceDirections = true)

    private suspend fun Dialogue.handOverAmulet(announceDirections: Boolean) {
        if (!player.inv.hasFreeSpace()) {
            objbox(
                HazeelMark,
                "Clivet tries to give you an amulet, but you don't have enough room to take it.",
            )
            return
        }
        access.invAdd(access.inv, HazeelMark, 1)
        player.hazeelGivenAmulet = true
        objbox(HazeelMark, "Clivet gives you an amulet.")
        if (announceDirections) {
            chatNpc(
                neutral,
                "Going from left to right, turn each of the five sewer valves above so that " +
                    "they follow the design of the amulet. Once you've done that, you'll be " +
                    "able to use the raft here to enter our hideout.",
            )
        }
    }

    private suspend fun Dialogue.alomone(npc: Npc) {
        val stage = quest.stage(player)
        when {
            player.sidedWithCeril && stage == SideChosen -> confrontAlomone(npc)
            player.sidedWithCeril -> return
            stage >= Complete -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(happy, "It is good to see you once more, adventurer. Glory to Hazeel!")
            }
            stage == FoodPoisoned -> recruit()
            else -> scrollHandIn()
        }
    }

    @OptIn(InternalApi::class)
    private suspend fun Dialogue.confrontAlomone(npc: Npc) {
        if (player.hazeelAlomoneState == 0) {
            chatNpc(angry, "How did you get in here?")
            chatPlayer(
                angry,
                "I've come for the Carnillean family armour. Hand it over, or face the " +
                    "consequences.",
            )
            chatNpc(
                angry,
                "I thought I made it clear to Jones that you could not be allowed to interfere " +
                    "with our mission. The incompetent fool must be going soft.",
            )
            chatPlayer(
                angry,
                "So the butler's part of your little cult? Why is it always the butler? I " +
                    "should have known...",
            )
            player.hazeelAlomoneMet = true
            player.hazeelAlomoneState = 1
        }
        chatNpc(angry, "Well you won't live long enough to tell anyone! Die!")
        npc.opPlayer2(player, aiInteractions)
    }

    private suspend fun Dialogue.recruit() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(
            happy,
            "Well, well, well... we have a new recruit. Clivet told me of your desire to join " +
                "us in our glorious task to resurrect the mighty Hazeel.",
        )
        chatPlayer(neutral, "That's right.")
        chatNpc(
            neutral,
            "Well if all goes well, you'll play a key role in just that. Before his defeat, " +
                "Hazeel secured within his home a powerful magical scroll of restoration. It " +
                "remains there still, hidden to even the foolish Carnilleans.",
        )
        chatNpc(
            neutral,
            "Up to now, our attempts to recover this scroll have been met with disappointment. " +
                "However, I know you will not fail us.",
        )
        chatPlayer(quiz, "So you want me to find this scroll?")
        chatNpc(
            neutral,
            "That is correct. You must return to the Carnillean Mansion and find where it is " +
                "hidden. We already have an agent in place to support you.",
        )
        chatPlayer(quiz, "You do?")
        chatNpc(
            neutral,
            "Indeed. Their Butler Jones is in fact a faithful follower of Hazeel. He has been " +
                "unsuccessful in locating the scroll himself, but together the two of you will " +
                "find it.",
        )
        quest.quest.setQuestStage(access, InHideout)
        player.hazeelDogState = 2
        chatNpc(
            happy,
            "Now, go with haste. Soon we will restore Hazeel to his true power and glory!",
        )
    }

    private suspend fun Dialogue.scrollHandIn() {
        chatPlayer(neutral, "Hello, Alomone.")
        if (!player.inv.contains(HazeelScroll)) {
            chatNpc(
                angry,
                "Hazeel has already waited far too long for his return. It is imperative that " +
                    "you find that scroll! It hides somewhere within the Carnillean Mansion.",
            )
            chatPlayer(neutral, "Don't worry, I'm on it.")
            return
        }
        chatNpc(quiz, "Have you brought the scroll of restoration?")
        chatPlayer(happy, "Yes, I have it right here.")
        chatNpc(
            happy,
            "At long last! With the words contained within this scroll, our lord shall finally " +
                "return to us!",
        )
        chatNpc(happy, "Watch adventurer, and witness the glorious rebirth of Hazeel!")
        mesbox("Alomone begins to revive Hazeel.")
        chatNpc(
            madlaugh,
            "Sentenne sillabri junque dithmenta! Ia! Ia! Dextrimon encanto! Termando... " +
                "Imcando... Solly enty rando... Sentenne! Ia! Indenti zaggarati g'thxa!",
        )
        chatNpc(
            madlaugh,
            "Dintenta! Sententa! Retenta! Q'exjta! Ia! Sottottot! Ia! Dysmenta junque " +
                "fammatio svelken! Sottey! Sentey! Soloment!",
        )
        mesbox("Hazeel awakens from the coffin.")
        hazeel(
            "My loyal followers, I have pride in you all. You have achieved a feat many would " +
                "have considered impossible and returned me to this land."
        )
        hazeel(
            "Much time has passed and I have much to attend to, but first, I believe there is " +
                "one here who deserves my gratitude."
        )
        hazeel(
            "Adventurer, I know that your efforts were essential in bringing about my return. " +
                "You have my thanks. Know that I consider you an ally."
        )
        hazeel(
            "Now, I must leave you, loyal subjects, for time is short. My Mahjarrat kin will " +
                "soon head northwards, and I have much to prepare."
        )
        hazeel(
            "Rest assured, when my work is done, I will return to you. Before long, the world " +
                "will know of Hazeel once more!"
        )
        if (access.invDel(access.inv, HazeelScroll, 1).failure) {
            access.mes("You no longer have the scroll.")
            return
        }
        player.hazeelGivenScroll = true
        player.hazeelRevived = true
        quest.quest.completeQuest(access)
    }

    private suspend fun Dialogue.hazeel(text: String) =
        chatNpcSpecific("Hazeel", HazeelHead, neutral, text)

    private companion object {
        const val Clivet = "npc.clivet_hazeel_cultist"
        const val ClivetHideout = "npc.clivet_hazeel_cultist_hideout"
        const val Alomone = "npc.alomone_hazeel_cultist"
        const val Cultist = "npc.hazeel_cultist"
        const val CultistTwo = "npc.hazeel_cultist_2"
        const val HazeelHead = "npc.hazeel"
        const val Complete = HazeelCultQuest.Complete
    }
}

/** Alomone falls for the Carnillean side: the chest is his, and the armour inside is the prize. */
class HazeelCultAlomoneDeath
@Inject
constructor(
    private val quest: HazeelCultQuest,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val death: NpcDeath,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(Alomone, AlomoneAttackable)) {
            val type =
                ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")
            onNpcQueue(type, "queue.death") { slain() }
        }
    }

    private suspend fun StandardNpcAccess.slain() {
        val hero = findHero(playerList)
        death.deathWithDrops(this, npc.coords)
        if (hero == null) {
            return
        }
        launcher.launch(hero) { alomoneFalls(quest) }
    }

    private companion object {
        const val Alomone = "npc.alomone_hazeel_cultist"
        const val AlomoneAttackable = "npc.alomone_hazeel_cultist_2op"
    }
}

internal fun ProtectedAccess.alomoneFalls(quest: HazeelCultQuest) {
    val hero = player
    if (!hero.sidedWithCeril || hero.hazeelAlomoneState != 1) {
        return
    }
    hero.hazeelAlomoneState = 2
    if (quest.stage(hero) == SideChosen) {
        quest.quest.setQuestStage(this, InHideout)
    }
}
