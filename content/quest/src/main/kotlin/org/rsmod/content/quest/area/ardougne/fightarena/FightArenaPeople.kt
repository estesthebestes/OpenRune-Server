package org.rsmod.content.quest.area.ardougne.fightarena

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.ArmourTaken
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.BeerPrice
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.BouncerDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.CellKeys
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.Coins
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.Complete
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.HasKeys
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.HeadGuardBriefed
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.InPrison
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KeyPrice
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhaliBrew
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardBeaten
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.KhazardFight
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.OgreDefeated
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.RecommendedCombat
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.SammyFreed
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.ScorpionFight
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Companion.Started
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Everyone you can talk to around the fight arena: Lady Servil, Sammy, the Head Guard who holds
 * the cell keys, the guards, the barman, Hengrad, and the prisoners and onlookers.
 */
class FightArenaPeople
@Inject
constructor(
    private val quest: FightArenaQuest,
    private val scenes: FightArenaScenes,
    private val doors: FightArenaDoors,
    private val objRepo: ObjRepository,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(LadyServil) { startDialogue(it.npc) { ladyServil() } }
        onOpNpc1(Sammy) { startDialogue(it.npc) { sammy() } }
        onOpNpc1(HeadGuard) { startDialogue(it.npc) { headGuard() } }
        onOpNpc1(LazyGuard) { startDialogue(it.npc) { lazyGuard() } }
        for (guard in Guards) {
            onOpNpc1(guard) { startDialogue(it.npc) { guard(it.npc) } }
        }
        onOpNpc1(DoorGuardWest) { startDialogue(it.npc) { doorGuard(ArenaEntrance.West) } }
        onOpNpc1(DoorGuardEast) { startDialogue(it.npc) { doorGuard(ArenaEntrance.East) } }
        onOpNpc1(Barman) { startDialogue(it.npc) { barman() } }
        onOpNpc1(Hengrad) { startDialogue(it.npc) { hengrad() } }
        onOpNpc1(Spectator) { startDialogue(it.npc) { spectator() } }
        onOpNpc1(Joe) { startDialogue(it.npc) { prisoner("Joe") } }
        onOpNpc1(Kelvin) { startDialogue(it.npc) { prisoner("Kelvin") } }
        onOpNpc1(Slave) { startDialogue(it.npc) { slave() } }
        onOpNpc1(SlaveTwo) { startDialogue(it.npc) { slave() } }
    }

    private val Dialogue.stage: Int
        get() = quest.stage(player)

    private val Dialogue.disguised: Boolean
        get() = player.wearingKhazardArmour

    /* Lady Servil */

    private suspend fun Dialogue.ladyServil() {
        when (quest.quest.questState(player)) {
            QuestProgressState.NOT_STARTED -> ladyServilStart()
            QuestProgressState.FINISHED -> ladyServilAfter()
            QuestProgressState.IN_PROGRESS ->
                when (stage) {
                    Started -> ladyServilStarted()
                    ArmourTaken -> ladyServilArmour()
                    InPrison,
                    HeadGuardBriefed,
                    HasKeys -> ladyServilInside()
                    in SammyFreed..BouncerDefeated -> ladyServilFreedSammy()
                    KhazardFight -> ladyServilEscaped()
                    else -> ladyServilBeatKhazard()
                }
        }
    }

    private suspend fun Dialogue.ladyServilStart() {
        chatPlayer(neutral, "Hi there. You look troubled.")
        chatNpc(
            sad,
            "Oh, I am! My family has been taken from me, and I don't know where to turn.",
        )
        chatPlayer(quiz, "Your family? Can I help?")
        chatNpc(
            sad,
            "General Khazard's soldiers seized my husband Justin and my son Sammy on the road. " +
                "They have been dragged off to his fight arena, where he forces his prisoners " +
                "to battle for sport.",
        )
        chatNpc(
            sad,
            "The Khazard army has this whole region in its grip, and the guards are as cruel as " +
                "they are corrupt. Nobody dares to stand against them.",
        )
        chatNpc(
            sad,
            "Please, will you go into the arena and bring my husband and my son back to me?",
        )
        if (player.combatLevel < RecommendedCombat) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RecommendedCombat."
            )
        }
        if (!startQuestPrompt(quest.quest)) {
            chatPlayer(neutral, "I'm sorry, I can't help you right now.")
            chatNpc(sad, "Then I fear for them. Please, if you change your mind, come back.")
            return
        }
        chatPlayer(neutral, "Of course I'll help. Where is the arena?")
        quest.quest.setQuestStage(access, Started)
        chatNpc(
            happy,
            "Bless you! You'll find it south of here, past the Monastery. The guards will only " +
                "let their own kind in, so you will need a disguise.",
        )
        chatNpc(
            neutral,
            "I have heard that the off-duty guards keep a spare set of armour in a chest, in " +
                "the north-eastern house by the arena. Please hurry!",
        )
    }

    private suspend fun Dialogue.ladyServilStarted() {
        chatPlayer(neutral, "Hello again, Lady Servil.")
        chatNpc(
            sad,
            "Please hurry. Every hour my family spends in that awful place puts them in more " +
                "danger.",
        )
    }

    private suspend fun Dialogue.ladyServilArmour() {
        chatPlayer(happy, "I've got hold of some Khazard guard armour.")
        chatNpc(
            happy,
            "Wonderful! Wear it, and the guards should take you for one of their own. Do be " +
                "careful, and hurry!",
        )
    }

    private suspend fun Dialogue.ladyServilInside() {
        chatPlayer(neutral, "I've been inside the arena's prison, disguised as a guard.")
        chatNpc(sad, "Did you see my family? Are they alive?")
        chatPlayer(neutral, "I'm working on getting them out. Don't give up hope.")
        chatNpc(sad, "Please be quick, and be careful.")
    }

    private suspend fun Dialogue.ladyServilFreedSammy() {
        chatPlayer(neutral, "I've freed Sammy, but he ran off to look for his father.")
        chatNpc(
            shocked,
            "Oh no! Justin is still in there, and the arena is the most dangerous place of all! " +
                "You must go and help them at once!",
        )
    }

    private suspend fun Dialogue.ladyServilEscaped() {
        chatNpc(shocked, "You're alive! Thank goodness. What happened in there?")
        chatPlayer(
            neutral,
            "General Khazard let the Servils go, but he came after me. I managed to escape.",
        )
        chatNpc(
            happy,
            "Justin and Sammy are safe at home now, resting. I can never thank you enough for " +
                "what you have done.",
        )
        reward()
    }

    private suspend fun Dialogue.ladyServilBeatKhazard() {
        chatNpc(shocked, "I can't believe it. You are alive, and so are Justin and Sammy!")
        chatPlayer(neutral, "I fought General Khazard and beat him.")
        chatNpc(shocked, "You killed him?")
        chatPlayer(
            neutral,
            "No. He survived, but I beat him fair and square, and he let me go.",
        )
        chatNpc(
            happy,
            "Then that is more than enough. My family is safe at home, recovering, thanks to " +
                "you. Please, let me reward you for your courage.",
        )
        reward()
    }

    private suspend fun Dialogue.reward() {
        chatNpc(happy, "Here, take this. It's the least I can do.")
        quest.quest.setQuestStage(access, Complete)
    }

    private suspend fun Dialogue.ladyServilAfter() {
        chatPlayer(neutral, "Hello again, Lady Servil.")
        chatNpc(
            happy,
            "Oh, hello. My family is recovering at home. I'm just waiting for someone to mend " +
                "our cart.",
        )
        chatPlayer(neutral, "I hope they don't take too long.")
        chatNpc(happy, "Thank you again for everything. I'm sure it won't be long now.")
    }

    /* Sammy, in his cell */

    private suspend fun Dialogue.sammy() {
        if (stage >= SammyFreed) {
            return
        }
        if (stage == HasKeys && player.inv.count(CellKeys) > 0) {
            with(scenes) { access.freeSammyWithDialogue() }
            return
        }
        if (!player.arenaMetSammy) {
            chatPlayer(quiz, "Are you Sammy Servil?")
            chatNpc(
                worried,
                "Yes, but who are you? Are you one of the guards? Please don't hurt me!",
            )
            chatPlayer(
                neutral,
                "Don't worry, I'm a friend. Your mother sent me to get you out of here.",
            )
            chatNpc(
                happy,
                "My mother? Oh, thank goodness! But you must be careful. If the guards find out " +
                    "who you really are, they'll throw you in a cell with me.",
            )
            chatPlayer(quiz, "How do I get the cell open? Do you know where the keys are?")
            chatNpc(
                neutral,
                "The Head Guard keeps the keys. You'll find him in the south-eastern corner of " +
                    "the prison, near the stairs.",
            )
            player.arenaMetSammy = true
            return
        }
        chatPlayer(neutral, "Hello, Sammy.")
        chatNpc(quiz, "Did you manage to get the keys?")
        chatPlayer(sad, "Not yet.")
        chatNpc(
            neutral,
            "The Head Guard has them. Please, hurry! I don't know how much longer my father can " +
                "survive in the arena.",
        )
    }

    /* The guards */

    private suspend fun Dialogue.headGuard() {
        if (!disguised) {
            if (stage >= HasKeys) {
                chatNpc(
                    drunk,
                    "Hey... you... get out of here! I don't like strangers... leave me alone...",
                )
            } else {
                chatNpc(angry, "Hey! You're not supposed to be in here. Get out, now!")
            }
            return
        }
        when {
            stage >= HasKeys -> headGuardDrunk()
            stage >= HeadGuardBriefed && player.inv.count(KhaliBrew) > 0 -> headGuardBrew()
            stage >= HeadGuardBriefed -> {
                chatPlayer(neutral, "Hello again.")
                chatNpc(bored, "Oh, it's you again. What a dull, dull day this is.")
                chatPlayer(quiz, "Your break will be coming up soon, won't it?")
                chatNpc(
                    bored,
                    "Not soon enough. I'd love a Khali brew from the bar, but I can't leave my " +
                        "post.",
                )
            }
            else -> headGuardFirstTalk()
        }
    }

    private suspend fun Dialogue.headGuardFirstTalk() {
        chatPlayer(neutral, "Long live General Khazard!")
        chatNpc(quiz, "Er... yes, long live him. What do you want?")
        chatPlayer(neutral, "I was just checking whether you had any orders.")
        chatNpc(
            bored,
            "Orders? No. Guarding prisoners is the dullest job in the world. All I want is a " +
                "Khali brew from the bar, but I can't leave my post to fetch one.",
        )
        chatPlayer(quiz, "What's a Khali brew?")
        chatNpc(
            happy,
            "Only the finest ale in the land! Mind you, one mug and I'd be flat on my back and " +
                "fast asleep. Best not drink one while I'm on duty.",
        )
        quest.advanceTo(access, HeadGuardBriefed)
    }

    private suspend fun Dialogue.headGuardBrew() {
        chatPlayer(neutral, "Hello again.")
        chatNpc(bored, "Oh, it's you. Another dull day for the Head Guard.")
        chatPlayer(quiz, "Do you still fancy a drink?")
        chatNpc(
            quiz,
            "A drink? Well... I really shouldn't. I'm on duty. Oh, go on then, just the one.",
        )
        chatPlayer(quiz, "Are you sure?")
        chatNpc(happy, "Quite sure! Hand it over.")
        if (!exchangeBrewForKeys()) {
            return
        }
        objbox(KhaliBrew, "You give the Head Guard the Khali brew.")
        chatNpc(happy, "Down it goes! Ahh, that's... that's a strong one...")
        chatNpc(drunk, "I don't feel so good. The room... it's spinning... I need to lie down.")
        chatPlayer(
            neutral,
            "Why don't you take a rest? I'll keep an eye on the prisoners for you.",
        )
        chatNpc(
            drunk,
            "Yeah... that's very kind. Take... take the keys. Wake me... when my shift ends...",
        )
        objbox(CellKeys, "The Head Guard hands you the cell keys.")
    }

    /**
     * Swaps the brew for the keys in one step. The keys drop to the floor if the inventory is
     * full, and a player who no longer holds the brew gets nothing.
     */
    private fun Dialogue.exchangeBrewForKeys(): Boolean {
        if (access.invDel(access.inv, KhaliBrew, 1).failure) {
            access.mes("You no longer have the Khali brew.")
            return false
        }
        access.invAddOrDrop(objRepo, CellKeys)
        quest.advanceTo(access, HasKeys)
        return true
    }

    private suspend fun Dialogue.headGuardDrunk() {
        if (player.inv.count(CellKeys) == 0 && stage <= KhazardFight) {
            chatPlayer(shifty, "Hi, er... I seem to have lost the keys.")
            chatNpc(
                drunk,
                "You fool... can't be trusted with... anything. Here... I've a spare set. Don't " +
                    "lose... these ones.",
            )
            access.invAddOrDrop(objRepo, CellKeys)
            objbox(CellKeys, "The Head Guard hands you a spare set of keys.")
            return
        }
        chatPlayer(neutral, "Hello again.")
        chatNpc(
            drunk,
            "Leave me... alone. The room keeps... swaying about. I need to sleep this off...",
        )
    }

    private suspend fun Dialogue.lazyGuard() {
        val taken = stage >= ArmourTaken
        if (!taken) {
            chatNpc(quiz, "Can I help you?")
            chatPlayer(neutral, "Er, I was hoping you could help me.")
            chatNpc(
                neutral,
                "I'm off duty at the moment, so why don't you go and bother somebody else?",
            )
            return
        }
        if (disguised) {
            chatNpc(angry, "Someone broke in and stole my armour! The nerve of them!")
            chatPlayer(sad, "That's terrible. Who would do such a thing?")
            chatNpc(sad, "I don't know, but when I catch them... Ah, never mind. Thanks for listening.")
        } else {
            chatNpc(angry, "Despicable thieving scum!")
            chatPlayer(shocked, "Excuse me?")
            chatNpc(angry, "None of your business. Clear off!")
        }
    }

    private suspend fun Dialogue.guard(npc: Npc) {
        if (stage == OgreDefeated) {
            chatNpc(angry, "Where do you think you're going? Back to your cell, prisoner!")
            with(scenes) { access.sendToCell() }
            return
        }
        if (stage >= SammyFreed && stage < KhazardBeaten) {
            if (arenaReturn(npc)) {
                return
            }
        }
        if (stage >= Complete) {
            chatNpc(angry, "You! You're the one who killed Bouncer! You'll pay for that!")
            npc.opPlayer2(player, aiInteractions)
            return
        }
        if (!disguised) {
            chatNpc(angry, "You don't belong here. Get out, before I throw you out!")
            return
        }
        chatPlayer(neutral, "Hello.")
        when (access.random.of(4)) {
            0 -> chatNpc(neutral, "There's some good fighting scheduled for later. Can't wait.")
            1 ->
                chatNpc(
                    neutral,
                    "I'm planning to pop down to the bar when my break comes round.",
                )
            2 -> chatNpc(angry, "Keep your eyes open. There may be suspicious types about.")
            else -> chatNpc(bored, "Don't disturb me. I'm working.")
        }
    }

    /** A guard offers to send a player back into the arena while a fight is waiting. */
    private suspend fun Dialogue.arenaReturn(npc: Npc): Boolean {
        chatNpc(quiz, "Back for another round? The crowd is waiting.")
        val back = menu("Return to the arena." to true, "Not just yet." to false, title = "Return to the arena?")
        if (!back) {
            chatPlayer(neutral, "Not just yet.")
            chatNpc(neutral, "Suit yourself. Come back when you've got your nerve.")
            return true
        }
        chatPlayer(neutral, "Send me back in.")
        with(scenes) { access.returnToArena() }
        return true
    }

    private suspend fun Dialogue.doorGuard(door: ArenaEntrance) {
        if (!disguised) {
            chatNpc(
                angry,
                "Oi! You can't come in here. This place is for Khazard guards only. Clear off!",
            )
            return
        }
        if (stage <= ArmourTaken) {
            chatNpc(quiz, "Hold on. I don't recognise you. Are you new here?")
            chatPlayer(neutral, "Long live General Khazard!")
            chatNpc(
                neutral,
                "Er... yes. Quite. Well, you seem to know the right words. Carry on, then.",
            )
            quest.advanceTo(access, InPrison)
        } else {
            chatNpc(neutral, "Back again? Go on through.")
        }
        with(doors) { access.passDoorGuard(door) }
    }

    /* Barman, Hengrad and the rest */

    private suspend fun Dialogue.barman() {
        chatNpc(happy, "Welcome to the Fight Arena bar! What can I get you?")
        while (true) {
            when (
                menu(
                    "I'll have a beer, please." to Drink.Beer,
                    "I'd like a Khali brew, please." to Drink.Brew,
                    "Got any news?" to Drink.News,
                    "I'm fine, thanks." to Drink.Nothing,
                )
            ) {
                Drink.Beer -> {
                    chatPlayer(neutral, "I'll have a beer, please.")
                    buy("beer", Beer, BeerPrice)
                    return
                }
                Drink.Brew -> {
                    chatPlayer(neutral, "I'd like a Khali brew, please.")
                    chatNpc(neutral, "Certainly. That will be five gold coins.")
                    buy("Khali brew", KhaliBrew, KeyPrice)
                    return
                }
                Drink.News -> {
                    chatPlayer(quiz, "Got any news?")
                    chatNpc(
                        laugh,
                        "The arena's put on some real spectacles lately. Ogres, goblins, even " +
                            "dragons fighting for the crowd. Great entertainment!",
                    )
                    chatNpc(neutral, "Now, can I get you a drink?")
                }
                Drink.Nothing -> {
                    chatPlayer(neutral, "I'm fine, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.buy(what: String, item: String, price: Int) {
        if (price > 0 && player.inv.count(Coins) < price) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough coins.")
            return
        }
        if (!player.inv.hasFreeSpace()) {
            chatPlayer(sad, "I don't have enough room to carry that.")
            return
        }
        if (price > 0 && access.invDel(access.inv, Coins, price).failure) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough coins.")
            return
        }
        access.invAddOrDrop(objRepo, item)
        if (item == KhaliBrew) {
            objbox(item, "The barman hands you a Khali brew.")
            chatNpc(
                neutral,
                "Be careful with that stuff. Make sure you lie down before you drink it.",
            )
        } else {
            objbox(item, "The barman hands you a $what.")
        }
    }

    private suspend fun Dialogue.hengrad() {
        when {
            stage == OgreDefeated -> hengradInCell()
            stage >= BouncerDefeated -> {
                chatPlayer(neutral, "Hello again.")
                chatNpc(
                    shocked,
                    "I never thought you'd get this far. Get out of here while you still can!",
                )
            }
            stage >= ScorpionFight -> {
                chatPlayer(neutral, "Hello again.")
                chatNpc(
                    shocked,
                    "How did you get out of that arena? Your luck won't hold forever. Flee while " +
                        "you can!",
                )
            }
            disguised -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(
                    quiz,
                    "You don't look like a real guard to me. Who are you, really?",
                )
                chatPlayer(shifty, "Oh, I'm nobody important.")
                chatNpc(neutral, "Hmm. If you say so.")
            }
            else -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(quiz, "You're not a guard. What are you doing in here?")
                chatPlayer(shifty, "Oh, I'm just looking around.")
                chatNpc(
                    neutral,
                    "If you say so. But watch yourself. Khazard's guards lock up anyone who " +
                        "pokes about.",
                )
            }
        }
    }

    private suspend fun Dialogue.hengradInCell() {
        chatNpc(sad, "So, Khazard has caught you too.")
        chatPlayer(sad, "Yes. He threw me in here after I beat his ogre.")
        chatNpc(sad, "I'm sorry to hear it. I wouldn't wish this place on anyone.")
        chatPlayer(quiz, "How long have you been in here?")
        chatNpc(
            sad,
            "Since I was a boy. They kidnapped me, and I've spent my whole life fighting in that " +
                "arena, hoping for a chance to escape.",
        )
        chatPlayer(happy, "Don't lose hope. Everyone gets out in the end.")
        chatNpc(happy, "Thank you, friend. Those are kind words.")
        chatNpc(
            worried,
            "Quiet, now. A guard is coming. Good luck out there in the arena.",
        )
        with(scenes) { access.backToArena() }
    }

    private suspend fun Dialogue.spectator() {
        when {
            stage >= KhazardBeaten -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    happy,
                    "I know you! You're the one who beat Bouncer. What a fight that was!",
                )
            }
            stage > OgreDefeated -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    happy,
                    "Hey, I saw you fighting in the arena! That was a great show.",
                )
            }
            stage == OgreDefeated -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    happy,
                    "You're the one who beat the ogre! Brilliant stuff. Do it again, eh?",
                )
            }
            stage == Started -> {
                chatPlayer(neutral, "Hello. Can I go into the arena?")
                chatNpc(
                    neutral,
                    "Only Khazard guards are allowed in. You'd need their armour.",
                )
            }
            else -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(
                    happy,
                    "Have you heard? The Servil family is to fight soon. It should be very " +
                        "entertaining!",
                )
            }
        }
    }

    private suspend fun Dialogue.prisoner(name: String) {
        chatPlayer(neutral, "Hello there.")
        if (disguised) {
            chatNpc(
                angry,
                "I won't tell you anything! One day Khazard will get what's coming to him!",
            )
        } else {
            chatNpc(
                worried,
                "You're not a guard! Get out of here before they catch you and lock you up too!",
            )
        }
    }

    private suspend fun Dialogue.slave() {
        chatPlayer(quiz, "Do you know anything about the Servil family?")
        if (disguised) {
            chatNpc(sad, "Please, just leave me alone.")
            return
        }
        when (access.random.of(4)) {
            0 -> chatNpc(sad, "Never heard of them. Please leave me be.")
            1 -> chatNpc(sad, "I have been in here so long I know no one any more.")
            2 -> chatNpc(worried, "Shh! The guards will hear you. Go away!")
            else -> chatNpc(sad, "No. I know nothing. Leave me alone.")
        }
    }

    private enum class Drink {
        Beer,
        Brew,
        News,
        Nothing,
    }

    private companion object {
        const val LadyServil = "npc.lady_servil_vis"
        const val Sammy = "npc.sammy_servil_vis"
        const val HeadGuard = "npc.arena_guard2"
        const val LazyGuard = "npc.arena_guard3"
        const val DoorGuardWest = "npc.arena_guard_door_1"
        const val DoorGuardEast = "npc.arena_guard_door_2"
        const val Barman = "npc.khazard_barman"
        const val Hengrad = "npc.hengrad"
        const val Spectator = "npc.arena_spectator"
        const val Joe = "npc.fightslave_joe"
        const val Kelvin = "npc.fightslave_kelvin"
        const val Slave = "npc.fightslave"
        const val SlaveTwo = "npc.fightslave_2"
        const val Beer = "obj.beer"

        val Guards =
            listOf(
                "npc.arena_guard1",
                "npc.arena_guard4",
                "npc.arena_guard5",
                "npc.arena_guard6",
                "npc.arena_guard7",
            )
    }
}
