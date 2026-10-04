package org.rsmod.content.quest.area.lumbridge

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.min
import kotlin.math.sign
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class SheepShearer @Inject constructor(private val objRepo: ObjRepository) :
    QuestScript(
        "quest_sheepshearer",
        "varp.sheep",
        rewards {
            xp("stat.crafting", CraftingXpReward)
            item("obj.coins", CoinReward)
        },
        ItemRewardDisplay(BallOfWool),
        questVarbit = "varbit.sheep_shearer_progress",
    ) {

    private var Player.seenTheThing by boolVarBit("varbit.sheep_shearer_thing_seen")

    override fun ScriptContext.init() {
        check(quest.maxSteps == CompleteStage) {
            "Sheep Shearer end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the hand-in completes at $CompleteStage."
        }

        onOpNpc1("npc.fred_the_farmer") { startDialogue(it.npc) { fredDialogue() } }

        onOpNpc1(TheThing) { shearTheThing(it.npc) }
        onOpNpc3(TheThing) { talkToTheThing(it.npc) }
        onOpNpcU(TheThing) {
            if (it.objType.id == Shears.asRSCM(RSCMType.OBJ)) {
                shearTheThing(it.npc)
            } else {
                mes("Nothing interesting happens.")
            }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Fred the Farmer</col> at his farm just north-west of " +
            "<col=800000>Lumbridge</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "<red>Fred the Farmer</red> has asked me to shear his sheep and bring him " +
                    "<red>$WoolRequired balls of wool</red>. He has promised to pay me for my trouble."
            )

            objective(
                "I can shear Fred's sheep with some <red>shears</red> and spin the wool into balls " +
                    "on the <red>spinning wheel</red> on the first floor of " +
                    "<red>Lumbridge Castle</red>."
            ) {
                hasItem(
                    "ball_of_wool",
                    "I have some <red>balls of wool</red> I can give to Fred.",
                )
                    .preserveObjective(strikeObjective = false)
                hasItem(
                    "wool",
                    "I have some <red>wool</red> that needs to be spun on a " +
                        "<red>spinning wheel</red>.",
                )
                    .preserveObjective(strikeObjective = false)
            }

            val remaining = remaining(access.player)
            objective("I need to bring Fred <red>$remaining</red> more ${balls(remaining)} of wool.") {
                visibleWhen { delivered(access.player) > 0 }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Fred the Farmer's sheep were getting far too woolly, so he asked me to shear them " +
                    "for him."
            )
            line(
                "I sheared the sheep, spun the wool into $WoolRequired balls of wool and brought " +
                    "them back to Fred, who paid me for my help."
            )
        }

    private fun delivered(player: Player): Int =
        (quest.getQuestStage(player) - StartedStage).coerceIn(0, WoolRequired)

    private fun remaining(player: Player): Int = WoolRequired - delivered(player)

    private suspend fun Dialogue.fredDialogue() {
        when (quest.questState(player)) {
            QuestProgressState.NOT_STARTED -> beforeQuest()
            QuestProgressState.IN_PROGRESS -> duringQuest()
            QuestProgressState.FINISHED -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(
            angry,
            "What are you doing on my land? Are you the one who keeps leaving all my gates open " +
                "and letting my sheep wander off?",
        )
        val options = buildList {
            add("I'm looking for a quest." to FredOption.Quest)
            add("I'm looking for something to kill." to FredOption.Kill)
            add("I'm lost." to FredOption.Lost)
            if (player.seenTheThing) add(SeenTheThing to FredOption.TheThing)
        }
        when (menu(options)) {
            FredOption.Quest -> offerQuest()
            FredOption.Kill -> lookingToKill()
            FredOption.Lost -> lost()
            FredOption.TheThing -> theThingSighting()
            FredOption.Shearing -> Unit
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(neutral, "I'm looking for a quest.")
        chatNpc(quiz, "A quest, is it? As it happens, I could use a hand.")
        chatNpc(
            neutral,
            "My sheep are getting awfully woolly. I'd be grateful if you could shear them for me, " +
                "and spin the wool while you're at it.",
        )
        chatNpc(
            neutral,
            "That's right, bring me $WoolRequired balls of wool and I'm sure I can find some way " +
                "to pay you. Mind you, there's the small matter of The Thing.",
        )

        if (player.inv.count(BallOfWool) >= WoolRequired) {
            earlyHandIn()
            return
        }

        chatPlayer(quiz, "The Thing? What do you mean?")
        chatNpc(
            worried,
            "Nobody has ever seen The Thing properly. That's why we call it The Thing - we don't " +
                "know what it is.",
        )
        chatNpc(
            worried,
            "Some folk say it's a black-hearted shapeshifter, hungry for the souls of decent " +
                "hard-working people like me. Others reckon it's just a sheep.",
        )
        chatNpc(
            angry,
            "Anyway, I haven't got all day to stand here gossiping. Are you going to shear my " +
                "sheep or not?",
        )

        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "No, I think I'll give it a miss.")
            chatNpc(neutral, "Suit yourself.")
            return
        }

        chatPlayer(happy, "Yes, alright. I can do that.")
        quest.setQuestStage(access, StartedStage)
        chatNpc(happy, "Good! One more thing - do you actually know how to shear a sheep?")
        chatPlayer(confused, "Err. No, actually, I don't.")
        explainShearing()
        chatNpc(quiz, "Do you know how to spin wool?")
        chatPlayer(sad, "Sorry, I don't know how to spin wool.")
        explainSpinning()
    }

    private suspend fun Dialogue.earlyHandIn() {
        chatPlayer(
            happy,
            "Funny you should say that, Fred. I actually have $WoolRequired balls of wool on me " +
                "already.",
        )
        chatNpc(angry, "Have you been shearing my sheep without asking!?")
        chatPlayer(
            shifty,
            "No! Well... maybe. They just looked a bit woolly! Don't you fancy a shave now and " +
                "then?",
        )
        chatNpc(
            angry,
            "It's rude to shave someone without asking - and don't you come at me with those " +
                "shears!",
        )
        chatPlayer(sad, "Sorry. I'll ask first next time.")
        chatNpc(
            neutral,
            "Well, I suppose no real harm's been done. Hand them over and we'll say no more " +
                "about it.",
        )
        deliver(WoolRequired)
    }

    private suspend fun Dialogue.duringQuest() {
        chatNpc(angry, "What are you doing on my land?")
        if (player.seenTheThing) {
            val choice =
                menu(
                    ShearingTalk to FredOption.Shearing,
                    SeenTheThing to FredOption.TheThing,
                )
            if (choice == FredOption.TheThing) {
                theThingSighting()
                return
            }
        }
        woolProgress()
    }

    private suspend fun Dialogue.woolProgress() {
        chatPlayer(neutral, ShearingTalk)
        chatNpc(quiz, "Oh. How are you getting on with those balls of wool?")

        if (player.inv.count(BallOfWool) > 0) {
            handInWool()
            return
        }

        chatPlayer(quiz, "How many more do I need to bring you?")
        val remaining = remaining(player)
        chatNpc(neutral, "You need to collect $remaining more ${balls(remaining)} of wool.")

        if (player.inv.count(Wool) > 0) {
            chatPlayer(
                neutral,
                "I've got some wool, but I haven't managed to turn it into balls yet.",
            )
            chatNpc(
                neutral,
                "Then go and find a spinning wheel. There's one on the first floor of Lumbridge " +
                    "Castle - just follow the road east from my house and you'll reach Lumbridge.",
            )
            return
        }

        chatPlayer(sad, "I haven't got any right now.")
        chatNpc(
            neutral,
            "Ah well, at least you haven't been eaten. You do know what you're doing, don't you?",
        )
        when (
            choice3(
                "How do I shear sheep, again?",
                1,
                "Remind me how to spin wool.",
                2,
                "Yeah, I think so.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "How do I shear sheep, again?")
                explainShearing()
                chatNpc(quiz, "Do you know how to spin wool?")
                val knowsHow =
                    choice2(
                        "Yes, I know how to spin wool.",
                        true,
                        "I don't know how to spin wool, sorry.",
                        false,
                    )
                if (knowsHow) {
                    chatPlayer(happy, "Yes, I know how to spin wool.")
                    chatNpc(happy, "Great!")
                } else {
                    chatPlayer(sad, "I don't know how to spin wool, sorry.")
                    explainSpinning()
                }
            }
            2 -> {
                chatPlayer(quiz, "Remind me how to spin wool.")
                explainSpinning()
            }
            3 -> {
                chatPlayer(happy, "Yeah, I think so.")
                chatNpc(neutral, "Off you go then!")
            }
        }
    }

    private suspend fun Dialogue.handInWool() {
        chatPlayer(happy, "I have some.")
        chatNpc(neutral, "Hand them over, then.")

        val owed = remaining(player)
        val held = min(player.inv.count(BallOfWool), owed)
        if (held >= owed) {
            chatPlayer(happy, "That's the last of them.")
            chatNpc(happy, "I suppose I'd better pay you, then.")
            deliver(owed)
            return
        }

        if (!deliver(held)) {
            return
        }
        objbox(BallOfWool, "You give Fred $held ${balls(held)} of wool.")
        chatPlayer(neutral, "That's all I've got for now.")
        chatNpc(neutral, "I need ${remaining(player)} more before I can pay you.")
        chatPlayer(neutral, "Ok, I'll work on it.")
    }

    private fun Dialogue.deliver(count: Int): Boolean {
        if (count <= 0 || player.inv.count(BallOfWool) < count) {
            access.mes("You don't have enough balls of wool with you.")
            return false
        }
        if (access.invDel(access.inv, BallOfWool, count).failure) {
            return false
        }
        quest.setQuestStage(access, StartedStage + delivered(player) + count)
        return true
    }

    private suspend fun Dialogue.explainShearing() {
        if (player.inv.count(Shears) > 0) {
            chatNpc(
                happy,
                "Well, you're halfway there already! You've got some shears with you. Just use " +
                    "them on a sheep to shear it.",
            )
            chatPlayer(quiz, "Is that all there is to it?")
            chatNpc(
                neutral,
                "Well, once you've gathered some wool you'll need to spin it into balls.",
            )
            return
        }

        chatNpc(
            neutral,
            "First things first, you'll need some shears. I've got a pair here you can borrow.",
        )
        access.invAddOrDrop(objRepo, Shears)
        objbox(Shears, "Fred gives you a set of sharp shears.")
        chatNpc(neutral, "Now just go and use them on the sheep out in my field.")
        chatPlayer(happy, "Sounds easy!")
        chatNpc(laugh, "That's what they all say!")
        chatNpc(
            neutral,
            "Some of the sheep aren't too keen on it... keep at it and you'll get there.",
        )
        chatNpc(neutral, "Once you've gathered some wool you can spin it into balls.")
    }

    private suspend fun Dialogue.explainSpinning() {
        chatNpc(happy, "Don't worry, it's quite simple!")
        chatNpc(
            neutral,
            "The nearest spinning wheel is on the first floor of Lumbridge Castle.",
        )
        chatNpc(neutral, "To get to Lumbridge Castle, just follow the road east.")
        chatPlayer(happy, "Thank you!")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(angry, "What are you doing on my land?")
        val options = buildList {
            add("I'm looking for something to kill." to FredOption.Kill)
            add("I'm lost." to FredOption.Lost)
            if (player.seenTheThing) add(SeenTheThing to FredOption.TheThing)
        }
        when (menu(options)) {
            FredOption.Kill -> lookingToKill()
            FredOption.Lost -> lost()
            FredOption.TheThing -> theThingSighting()
            FredOption.Quest,
            FredOption.Shearing -> Unit
        }
    }

    private suspend fun Dialogue.lookingToKill() {
        chatPlayer(neutral, "I'm looking for something to kill.")
        chatNpc(angry, "What, on my land? You leave my livestock alone, you scoundrel!")
    }

    private suspend fun Dialogue.lost() {
        chatPlayer(confused, "I'm lost.")
        chatNpc(
            confused,
            "How can you be lost? Follow the road east and then south and you'll be in " +
                "Lumbridge in no time.",
        )
    }

    private suspend fun Dialogue.theThingSighting() {
        chatPlayer(shocked, SeenTheThing)
        chatNpc(shocked, "You... you actually saw it?")
        chatNpc(
            shocked,
            "Run for the hills! ${player.displayName}, grab as many chickens as you can carry! " +
                "We have to...",
        )
        chatPlayer(angry, "Fred!")
        chatNpc(sad, "...flee! Oh, woe is me! The shapeshifter is coming! We're all...")
        chatPlayer(verymad, "FRED!")
        chatNpc(shocked, "...doomed. What!")
        chatPlayer(neutral, "It's not a shapeshifter, or any other kind of monster!")
        chatNpc(quiz, "Well then, what is it?")
        chatPlayer(neutral, "Well... it's just two penguins. Penguins dressed up as a sheep.")
        chatNpc(confused, "...")
        chatNpc(confused, "Have you been out in the sun too long?")
    }

    private suspend fun ProtectedAccess.shearTheThing(npc: Npc) {
        if (Shears !in inv) {
            mes("You need a set of shears to do this.")
            return
        }
        faceEntitySquare(npc)
        anim("seq.human_shearing")
        delay(1)
        player.seenTheThing = true
        npc.walk(fleeDestination(npc))
        startDialogue { mesbox("The... whatever it is... scuttles away from you!") }
    }

    private suspend fun ProtectedAccess.talkToTheThing(npc: Npc) {
        player.seenTheThing = true
        startDialogue(npc) { chatPlayer(confused, "That's a sheep... I think. I can't talk to sheep.") }
    }

    private fun ProtectedAccess.fleeDestination(npc: Npc) =
        npc.coords.translate(
            (npc.coords.x - coords.x).sign * FleeDistance,
            (npc.coords.z - coords.z).sign * FleeDistance,
        )

    private fun balls(count: Int): String = if (count == 1) "ball" else "balls"

    private enum class FredOption {
        Quest,
        Kill,
        Lost,
        TheThing,
        Shearing,
    }

    private companion object {
        private const val WoolRequired = 20
        private const val StartedStage = 1
        private const val CompleteStage = StartedStage + WoolRequired

        private const val CraftingXpReward = 150.0
        private const val CoinReward = 60

        private const val BallOfWool = "obj.ball_of_wool"
        private const val Wool = "obj.wool"
        private const val Shears = "obj.shears"
        private const val TheThing = "npc.sheep_shearer_the_thing"

        private const val FleeDistance = 3

        private const val ShearingTalk = "I need to talk to you about shearing these sheep!"
        private const val SeenTheThing = "Fred! Fred! I've seen The Thing!"
    }
}
