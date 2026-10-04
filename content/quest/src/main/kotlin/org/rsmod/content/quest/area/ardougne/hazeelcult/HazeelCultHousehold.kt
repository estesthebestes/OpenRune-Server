package org.rsmod.content.quest.area.ardougne.hazeelcult

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.ArmourReturned
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.CarnilleanArmour
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.ChestKey
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.Coins
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.FakeEndingCoins
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.FoodPoisoned
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.HazeelMark
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.HazeelScroll
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.InHideout
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.Poison
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.RecommendedCombat
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.SideChosen
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.Started
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Ceril Carnillean's household: the family, the cook, the guard and the butler. */
class HazeelCultHousehold
@Inject
constructor(private val quest: HazeelCultQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    private enum class Phase {
        Early,
        Joined,
        Poisoned,
        Scroll,
        Fake,
        CerilDone,
        HazeelDone,
    }

    override fun ScriptContext.startup() {
        onOpNpc1(Ceril) { startDialogue(it.npc) { ceril() } }
        onOpNpc1(Guard) { startDialogue(it.npc) { guard() } }
        onOpNpc1(Philipe) { startDialogue(it.npc) { philipe() } }
        onOpNpc1(Claus) { startDialogue(it.npc) { claus() } }
        onOpNpc1(Henryeta) { startDialogue(it.npc) { henryeta() } }
        onOpNpc1(Jones) { startDialogue(it.npc) { jones() } }
    }

    private fun Dialogue.phase(): Phase {
        val ceril = player.sidedWithCeril
        return when (quest.stage(player)) {
            in 0..3 -> Phase.Early
            SideChosen -> if (ceril) Phase.Early else Phase.Joined
            FoodPoisoned -> Phase.Poisoned
            InHideout -> if (ceril) Phase.Early else Phase.Scroll
            ArmourReturned -> Phase.Fake
            else -> if (ceril) Phase.CerilDone else Phase.HazeelDone
        }
    }

    private suspend fun Dialogue.ceril() {
        val stage = quest.stage(player)
        when (phase()) {
            Phase.Early ->
                when {
                    stage == 0 -> cerilBeforeQuest()
                    stage == InHideout && player.inv.contains(CarnilleanArmour) ->
                        returnArmour()
                    else -> cerilReminder()
                }
            Phase.Joined -> {
                chatNpc(quiz, "You're back. Have you had any luck yet?")
                chatPlayer(sad, "I'm afraid not, Ceril.")
                chatNpc(
                    angry,
                    "That's Sir Ceril you blooming scoundrel! Show respect when addressing " +
                        "someone of my rank! I must say, I am a bit disappointed you haven't " +
                        "made any progress. Maybe I was wrong about you.",
                )
                chatPlayer(neutral, "Yeah, well, just unlucky I guess.")
            }
            Phase.Poisoned -> if (player.hazeelPoisonSuccess) cerilGrieving() else cerilScruffy()
            Phase.Scroll -> cerilDogDead()
            Phase.Fake -> cerilAfterRefusal()
            Phase.CerilDone -> {
                chatPlayer(happy, "Hello, Ceril.")
                chatNpc(
                    happy,
                    "Hello again, adventurer! It's good to see you! If it wasn't for your quick " +
                        "thinking, the treacherous Jones would have probably poisoned my " +
                        "family and I by now! We are in your debt.",
                )
                chatPlayer(
                    happy,
                    "Don't worry about it! A good deed is its own reward. And that money didn't " +
                        "hurt either.",
                )
            }
            Phase.HazeelDone -> cerilMourning()
        }
    }

    private suspend fun Dialogue.cerilBeforeQuest() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(
            angry,
            "Blooming, thieving cultists! Why don't they leave me alone? String them all up, " +
                "that's what I say!",
        )
        when (
            choice3(
                "What's wrong?",
                1,
                "You probably deserve it.",
                2,
                "You seem uptight, I'll leave you alone.",
                3,
            )
        ) {
            1 -> cerilWhatsWrong()
            2 -> {
                chatPlayer(neutral, "You probably deserve it.")
                chatNpc(
                    angry,
                    "And who are you to judge me? You look like a peasant anyway. I'm wasting my " +
                        "time talking to you.",
                )
            }
            else -> {
                chatPlayer(neutral, "You seem uptight. I'll leave you alone.")
                chatNpc(neutral, "Yes, I doubt you could help anyway.")
            }
        }
    }

    private suspend fun Dialogue.cerilWhatsWrong() {
        chatPlayer(quiz, "What's wrong?")
        chatNpc(
            angry,
            "It's those blooming cultists from the south! They keep breaking into my house!",
        )
        chatPlayer(quiz, "Have they taken much?")
        chatNpc(
            neutral,
            "Well... no. They stole a suit of armour when they first broke in months ago, but " +
                "they've been back four more times since then and taken nothing.",
        )
        chatPlayer(neutral, "I see. And you are?")
        chatNpc(
            happy,
            "You don't know me? Why, I am Sir Ceril Carnillean! Mine is quite a famous " +
                "bloodline, you know. We've played a vital role in the politics of Ardougne " +
                "for many generations.",
        )
        chatPlayer(
            neutral,
            "Well I'm ${player.displayName}. Maybe I can help you with these cultists?",
        )
        chatNpc(
            neutral,
            "Interesting... I suppose the authorities haven't been much help so far. Yes, very " +
                "well. If you return the stolen armour, I will provide you with a modest cash " +
                "reward!",
        )
        if (player.combatLevel < RecommendedCombat) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RecommendedCombat."
            )
        }
        if (!startQuestPrompt(quest.quest)) {
            chatPlayer(neutral, "No thanks. I've got other plans.")
            chatNpc(
                angry,
                "Well no wonder I'm the one with the big house and you're the one on the " +
                    "streets.",
            )
            return
        }
        quest.quest.setQuestStage(access, Started)
        chatPlayer(happy, "Yes, of course, I'd be happy to help.")
        chatNpc(
            happy,
            "That's very noble of you! Keep that attitude up and one day you might have the " +
                "honour of working full-time for someone like me.",
        )
        chatNpc(
            neutral,
            "Now, I caught a glimpse of the thieves leaving, but due to... uh... my cold, I was " +
                "unable to give chase.",
        )
        chatNpc(
            neutral,
            "What I do know is that they belong to some sort of cult. They seem to be based in " +
                "a cave just south of the city, near the Clock Tower.",
        )
        chatPlayer(quiz, "How do you know that?")
        chatNpc(
            sad,
            "My old butler, Higson, once followed them there. Unfortunately, the next night he " +
                "died in his sleep.",
        )
        chatPlayer(shocked, "That's awful!")
        chatNpc(
            happy,
            "No, it's okay. Jones, a replacement from the Servants' Guild, arrived the next " +
                "day. He's been great - makes an excellent broth!",
        )
        chatPlayer(neutral, "Right... I'll see if I can find this cave.")
    }

    private suspend fun Dialogue.cerilReminder() {
        chatPlayer(neutral, "Hello, Ceril.")
        chatNpc(
            angry,
            "That's Sir Ceril to you, you impudent scamp. Show a bit of respect to your " +
                "betters. Now, shouldn't you be recovering my armour?",
        )
        chatPlayer(neutral, "Yeah yeah, I'm on it.")
        chatNpc(
            neutral,
            "Good. I suggest you start with that cave south of the city, near the Clock Tower. " +
                "That is where the cult is based.",
        )
    }

    private suspend fun Dialogue.returnArmour() {
        chatNpc(quiz, "You're back. Have you had any luck yet?")
        chatPlayer(happy, "Look! I've recovered your armour!")
        chatNpc(happy, "Well done! I must say I am very impressed! Come on, hand it over.")
        objbox(CarnilleanArmour, "You give the armour to Ceril.")
        chatNpc(
            happy,
            "Before we send you on your way with your payment, I'll just get Jones to whip you " +
                "up a batch of his special broth.",
        )
        chatPlayer(
            neutral,
            "I'd rather not if it's all the same to you. Apparently Jones is in league with the " +
                "cultists.",
        )
        chatNpc(
            shocked,
            "What? Right! You! We're going to blooming well sort this out right now, once and " +
                "for all!",
        )
        chatNpc(
            angry,
            "Jones! This commoner says you had something to do with the cultists that stole my " +
                "armour. What do you have to say for yourself?",
        )
        chatNpcSpecific(
            "Butler Jones",
            JonesHead,
            neutral,
            "Nothing to do with me, sir. I am, as you know, a loyal servant.",
        )
        chatNpc(
            angry,
            "Humph. Quite right too. I cannot fathom why this scoundrel would accuse you of " +
                "such a crime without evidence to back up the accusations.",
        )
        chatNpc(
            neutral,
            "Right, I have made a decision. I have given my word as a nobleman to reward you " +
                "for your efforts in retrieving my armour, but I must also compensate Jones for " +
                "this terrible slander.",
        )
        chatNpc(angry, "Now take this and leave! And don't darken my doorstep again!")
        if (access.invDel(access.inv, CarnilleanArmour, 1).failure) {
            access.mes("You no longer have the armour.")
            return
        }
        player.hazeelGivenArmour = true
        access.invAddOrDrop(objRepo, Coins, FakeEndingCoins)
        quest.quest.setQuestStage(access, ArmourReturned)
        chatNpcSpecific(
            "Butler Jones",
            JonesHead,
            happy,
            "Don't worry, sir, he won't be bothering us any more.",
        )
        mesbox("Jones smirks at you. You are going to need more than words to prove his treachery.")
        quest.showPartialCompletion(access)
    }

    private suspend fun Dialogue.cerilAfterRefusal() {
        if (player.hazeelEvidenceFound &&
            player.inv.contains(Poison) &&
            player.inv.contains(HazeelMark)
        ) {
            accuseJones()
            return
        }
        chatPlayer(angry, "Hey! You owe me money.")
        chatNpc(
            angry,
            "I owe you nothing you scoundrel! Now get out or I'll have Jones throw you out!",
        )
    }

    private suspend fun Dialogue.accuseJones() {
        chatPlayer(happy, "Ceril!")
        chatNpc(angry, "What do you want now you scoundrel?")
        chatPlayer(neutral, "Take a look at what I've found.")
        chatNpc(shocked, "What's this... Poison?")
        chatNpc(quiz, "Jones?")
        chatNpcSpecific("Butler Jones", JonesHead, shifty, "Just for killing rats, sir.")
        chatNpc(
            shocked,
            "Right... but this amulet... I recognise this! Those cultists wore amulets like " +
                "this!",
        )
        chatNpc(angry, "Jones! We trusted you! We took you into our home!")
        chatNpcSpecific(
            "Butler Jones",
            JonesHead,
            angry,
            "You senile old fool... it was all too easy! I should have killed you and your " +
                "pathetic family weeks ago!",
        )
        chatNpc(angry, "Guard!")
        chatNpc(angry, "This man is one of those cultists! Take him away!")
        chatNpcSpecific(
            "Butler Jones",
            JonesHead,
            angry,
            "Don't think this is over, Ceril! You will die at the hands of the Hazeel Cult! I " +
                "promise you that!",
        )
        chatNpc(sad, "Well... It looks like I am indebted to you!")
        chatPlayer(happy, "No problem. We all make mistakes.")
        chatNpc(worried, "But if it weren't for you... my whole family... we could have been...")
        chatNpc(
            neutral,
            "I apologise for my harshness before. The very least I can do is to reward you for " +
                "your noble efforts, and to offer my sincerest apologies as a gentleman.",
        )
        chatPlayer(happy, "Thank you.")
        chatNpc(happy, "No, no, thank you! Feel free to stop by anytime, adventurer!")
        player.hazeelJonesLocation = 1
        quest.quest.completeQuest(access)
    }

    private suspend fun Dialogue.cerilScruffy() {
        chatPlayer(neutral, "Hello again.")
        chatNpc(sad, "Oh the inhumanity... the cruelty... the misery... the pain...")
        chatNpc(
            sad,
            "My son is a good boy, really, but how could he give his dinner to Scruffy " +
                "without having the servants test it for poison first? How? How could he be so " +
                "thoughtless and careless? He knows we are all under threat!",
        )
        chatPlayer(quiz, "Scruffy?")
        chatNpc(
            sad,
            "He's been with our family for twenty years... that's 140 in dog years! The poor " +
                "dog... What did he ever do to deserve such a fate?",
        )
        chatPlayer(worried, "Your dog got poisoned? That's not right.")
        chatNpc(
            angry,
            "I agree! I hope whichever evildoer is responsible gets the full weight of the law " +
                "brought upon them!",
        )
        player.hazeelPoisonSuccess = true
        chatPlayer(shifty, "Uh... yeah... me too.")
    }

    private suspend fun Dialogue.cerilGrieving() {
        chatPlayer(neutral, "Hello again.")
        chatNpc(sad, "Oh the misery... How could someone do something like this?")
        chatPlayer(shifty, "Uh... I'm sure they won't get away with it...")
    }

    private suspend fun Dialogue.cerilDogDead() {
        chatPlayer(neutral, "Hello, Ceril. How's things?")
        chatNpc(
            angry,
            "Sir Ceril! Sir! And things are terrible! Our beloved dog is dead, and I fear it's " +
                "only a matter of time before those cultists strike again!",
        )
        chatPlayer(neutral, "Don't worry, I'm sure it will all be okay in the end.")
    }

    private suspend fun Dialogue.cerilMourning() {
        chatPlayer(neutral, "Ceril. How are you?")
        chatNpc(
            sad,
            "Oh cruel world! Scruffy... I knew you well... I... just... don't think I can... " +
                "go on without...",
        )
        mesbox("Sir Ceril breaks down into tears.")
        chatPlayer(bored, "Loosen up already. It was only a DOG.")
    }

    private suspend fun Dialogue.guard() {
        when (phase()) {
            Phase.Early,
            Phase.Joined -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    happy,
                    "Hello there. I hear you're after that cult who broke in the other night. It " +
                        "always gladdens me when civilians assist the law like this.",
                )
                chatPlayer(happy, "I'm just happy to be of help.")
            }
            Phase.Poisoned -> guardMurder()
            Phase.Scroll ->
                if (player.inv.contains(HazeelScroll)) guardBurglary() else guardMurder()
            Phase.HazeelDone -> guardBurglary()
            Phase.Fake -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(
                    quiz,
                    "Hello, adventurer. I hear you accused Jones of being part of that cult. Is " +
                        "that true?",
                )
                chatPlayer(sad, "It is, but nobody believes me.")
                chatNpc(
                    neutral,
                    "You have my utter faith, adventurer. I can't openly defy Sir Ceril, but I'd " +
                        "like you to know that I think you're on the right track with this.",
                )
                chatNpc(
                    neutral,
                    "I always found it suspicious that Jones would just show up on the doorstep " +
                        "the very day after the old butler died. Good luck in clearing your name.",
                )
            }
            Phase.CerilDone -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(
                    happy,
                    "Well look who it is! Good to see you managed to clear your name. I always " +
                        "had faith in you!",
                )
                chatPlayer(happy, "Thanks! So is everything all quiet around here now?")
                chatNpc(happy, "Indeed! For now at least, everything is back to normal.")
            }
        }
    }

    private suspend fun Dialogue.guardMurder() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(
            sad,
            "Today is a dark day. Those cultists have been back, and this time they've gone " +
                "further than ever before! Murder! We can't afford to keep letting them get " +
                "away with this.",
        )
    }

    private suspend fun Dialogue.guardBurglary() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(
            worried,
            "Hello, adventurer. I hope you find the cult soon. We have a horrible suspicion " +
                "that there's been another burglary.",
        )
        chatPlayer(worried, "That's worrying.")
        chatNpc(
            worried,
            "Yes, it is. The thing is, we can't even work out what they've taken! It's all " +
                "very odd.",
        )
    }

    private suspend fun Dialogue.philipe() {
        chatPlayer(neutral, "Hello there.")
        when (phase()) {
            Phase.Early,
            Phase.Joined -> {
                chatNpc(
                    happy,
                    "Mommy said you're here to kill all the nasty people that keep breaking in.",
                )
                chatPlayer(neutral, "Something like that.")
                chatNpc(quiz, "Can I watch?")
                chatPlayer(angry, "No!")
            }
            Phase.Poisoned,
            Phase.Scroll,
            Phase.HazeelDone -> {
                chatNpc(sad, "Someone killed Scruffy. I liked Scruffy. He never told me off.")
                chatPlayer(sad, "Yeah... it's a real shame.")
                chatNpc(sad, "I want my mommy.")
            }
            Phase.Fake -> {
                chatNpc(
                    quiz,
                    "Daddy says you don't like Mr Jones. Mr Jones is nice. He brings me toys and " +
                        "sweets.",
                )
                chatPlayer(neutral, "Jones is a bad man.")
                chatNpc(angry, "Well if you think that, I don't like you!")
                chatPlayer(
                    neutral,
                    "Well I'll do my best to console myself over your feelings towards me.",
                )
            }
            Phase.CerilDone -> {
                chatNpc(happy, "What have you brought me? I want some more toys!")
                chatPlayer(sad, "I'm afraid I don't have any toys.")
                chatNpc(angry, "Toys! I want toys!")
            }
        }
    }

    private suspend fun Dialogue.claus() {
        when (phase()) {
            Phase.Early -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(
                    worried,
                    "Sorry, can't stop to chat! You would be amazed at how many meals this " +
                        "family gets through daily!",
                )
            }
            Phase.Joined -> {
                chatPlayer(neutral, "Hey.")
                chatNpc(
                    quiz,
                    "You're that adventurer the family asked to help deal with those weird " +
                        "cultists, right?",
                )
                chatPlayer(neutral, "That's me.")
                chatNpc(happy, "Good luck with that!")
            }
            Phase.Poisoned -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(quiz, "Hello there. Caught any of those weird cultists yet?")
                chatPlayer(neutral, "Afraid not.")
                chatNpc(happy, "Well, keep at it!")
            }
            Phase.Scroll,
            Phase.HazeelDone -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(
                    confused,
                    "I just don't understand it! How? How could someone poison my delicious " +
                        "food without me even noticing? I... I just don't get it!",
                )
                chatNpc(
                    sad,
                    "Oh man... I hope I don't lose my job over this... but how? How did they do " +
                        "it? And why would they want to kill poor Scruffy anyway? He was such a " +
                        "good dog...",
                )
                chatPlayer(neutral, "Yeah... it's a real mystery.")
            }
            Phase.Fake -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(happy, "Hello. How are you today?")
                chatPlayer(neutral, "Not bad, thanks.")
                chatNpc(happy, "Good good.")
            }
            Phase.CerilDone -> {
                chatPlayer(neutral, "Hey.")
                chatNpc(happy, "Well hello there, adventurer! Are we fit and well?")
                chatPlayer(happy, "Absolutely.")
                chatNpc(happy, "Glad to hear it.")
            }
        }
    }

    private suspend fun Dialogue.henryeta() {
        chatPlayer(neutral, "Hello.")
        when (phase()) {
            Phase.Early -> {
                chatNpc(
                    worried,
                    "Oh, hello. I'm afraid I'm not very presentable at the moment. I've been " +
                        "under incredible stress of late! It's ruining my complexion!",
                )
                chatPlayer(quiz, "Why? What's wrong?")
                chatNpc(
                    angry,
                    "It's those awful cultists! I just don't feel safe here anymore!",
                )
            }
            Phase.Joined -> {
                chatNpc(
                    worried,
                    "Oh, hello. I hope you've found those awful cultists! I simply cannot sleep " +
                        "at night knowing they're loose!",
                )
                chatPlayer(neutral, "I'm afraid I haven't found them yet.")
                chatNpc(angry, "Well you really are useless, aren't you?")
                chatPlayer(neutral, "Yup, I guess I am.")
            }
            Phase.Poisoned -> {
                chatNpc(
                    sad,
                    "Those hooligans! They slaughtered my precious Scruffy! I shall never " +
                        "recover! I am emotionally scarred for life!",
                )
                chatPlayer(shifty, "Yeah... Hopefully someone finds them soon.")
            }
            Phase.Scroll,
            Phase.HazeelDone -> {
                chatNpc(
                    sad,
                    "I'm sorry. I am far too upset to talk to anybody right now. Oh... poor " +
                        "Scruffy...",
                )
                chatPlayer(sad, "Yeah... poor Scruffy.")
            }
            Phase.Fake -> {
                chatNpc(
                    angry,
                    "You! Don't think you can go around insulting our trusted staff and then " +
                        "waltz up to me as though nothing has happened!",
                )
                chatPlayer(angry, "Everything I said was the truth.")
                chatNpc(
                    laugh,
                    "Ha! Don't be so ridiculous! Next you'll be trying to tell me that Jones " +
                        "murdered our old butler!",
                )
            }
            Phase.CerilDone -> {
                chatNpc(
                    happy,
                    "Hello again, adventurer. Since you dealt with those horrible cultists, " +
                        "things have vastly improved for us around here!",
                )
                chatPlayer(happy, "I'm happy to have helped.")
            }
        }
    }

    private suspend fun Dialogue.jones() {
        val stage = quest.stage(player)
        chatPlayer(neutral, "Hello there.")
        when {
            player.sidedWithCeril && stage == ArmourReturned -> {
                chatNpc(quiz, "I believe you've been asked to leave, adventurer.")
                chatPlayer(
                    angry,
                    "You won't get away with this, Jones! I know who you really are!",
                )
                chatNpc(
                    angry,
                    "That's quite enough of that. All of us here are starting to tire of your " +
                        "theatrics.",
                )
                chatPlayer(angry, "This isn't over! I will find proof!")
            }
            player.sidedWithCeril && player.hazeelAlomoneMet && stage < ArmourReturned ->
                jonesAccused()
            !player.sidedWithCeril && stage in SideChosen..InHideout -> jonesCultist(stage)
            else -> {
                chatNpc(happy, "Hello. How are you today?")
                chatPlayer(happy, "Good thank you, and yourself?")
                chatNpc(happy, "Very well, thank you.")
            }
        }
    }

    private suspend fun Dialogue.jonesAccused() {
        chatNpc(quiz, "Hello, adventurer. How are you doing with your quest?")
        chatPlayer(angry, "Drop the act, Jones. I know you're in league with the cultists.")
        chatNpc(shocked, "Have you hit your head? That's a preposterous accusation!")
        chatPlayer(angry, "Really? So you won't mind me telling Ceril then?")
        chatNpc(laugh, "Oh, you think he'll take your word over mine? He trusts me completely!")
        chatPlayer(angry, "We'll see about that.")
    }

    private suspend fun Dialogue.jonesCultist(stage: Int) {
        if (stage == InHideout) {
            scrollHunt()
            return
        }
        if (stage == FoodPoisoned) {
            chatNpc(
                sad,
                "Hello, adventurer. Such a terrible shame about Scruffy. I wonder if the " +
                    "family will ever fully recover.",
            )
            chatNpc(neutral, "Anyway, I hear your quest is going well.")
        } else {
            chatNpc(neutral, "Hello, adventurer. I hear your quest is going well.")
        }
        chatPlayer(quiz, "Really?")
        chatNpc(happy, "Oh yes. Do keep up the good work.")
    }

    private suspend fun Dialogue.scrollHunt() {
        val hasScroll = player.inv.contains(HazeelScroll)
        if (!hasScroll && !player.inv.contains(ChestKey)) {
            chatNpc(neutral, "Hello, adventurer. I hear your quest is going well.")
            chatPlayer(
                neutral,
                "I'm making progress. On that note, I hear you might be able to help me with my " +
                    "next task.",
            )
            chatNpc(
                neutral,
                "Ah yes, the scroll. I'm afraid I've searched high and low, but have so far been " +
                    "unable to locate it. I'm sure it is somewhere within the house, hidden " +
                    "where even the family won't find it.",
            )
            chatPlayer(
                quiz,
                "Hmm... So it must be somewhere they'd have never stepped foot. I'll keep on " +
                    "looking.",
            )
            return
        }
        chatNpc(neutral, "Hello, adventurer. How is your quest going?")
        if (hasScroll) {
            chatPlayer(happy, "I have the scroll!")
            chatNpc(
                happy,
                "Then you should waste no time in returning to Alomone. The return of Hazeel " +
                    "will soon be upon us!",
            )
            return
        }
        chatPlayer(neutral, "I'm making progress.")
        chatNpc(
            neutral,
            "Good. Remember, the scroll must be somewhere within the house, hidden where even " +
                "the family won't find it.",
        )
        chatPlayer(quiz, "Hmm... I'll keep on looking.")
    }

    private companion object {
        const val Ceril = "npc.sir_ceril_carnillean"
        const val Guard = "npc.guard_carnillean"
        const val Philipe = "npc.philipe_carnillean"
        const val Claus = "npc.claus_carnillean"
        const val Henryeta = "npc.carnillean_wife"
        const val Jones = "npc.butler_jones_hazeel_cultist"
        const val JonesHead = "npc.butler_jones_hazeel_cultist_op"
    }
}
