package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.ETHENEA
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.LIQUID_HONEY
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GUIDOR_TESTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.SULPHURIC_BROLINE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.vialCode
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.vialName
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.vialObj
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Getting Elena's vials past the Varrock guard: the Chemist in Rimmington hands over touch paper
 * and lends his three light-fingered errand boys, who each carry one vial to the Dancing Donkey
 * Inn in south-east Varrock. Each keeps anything he has a use for, so the painter must get the
 * ethenea, the gambler the worthless honey and the drunk the poisonous broline.
 *
 * The Chemist's lamp talk lives in the Rimmington area script, which hands over to
 * [chemistQuest] while the quest wants him.
 */
class Smuggling
@Inject
constructor(private val biohazard: BiohazardQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    private class ErrandBoy(
        val rimmington: String,
        val varrock: String,
        val safeVial: String,
        val held: (Player) -> Int,
        val hold: (Player, Int) -> Unit,
    )

    private val chancy =
        ErrandBoy("npc.gambler1", "npc.gambler2", LIQUID_HONEY, { it.chancyVial }) { p, v ->
            p.chancyVial = v
        }
    private val daVinci =
        ErrandBoy("npc.artist1", "npc.artist2", ETHENEA, { it.daVinciVial }) { p, v ->
            p.daVinciVial = v
        }
    private val hops =
        ErrandBoy("npc.drunk1", "npc.drunk2", SULPHURIC_BROLINE, { it.hopsVial }) { p, v ->
            p.hopsVial = v
        }

    override fun ScriptContext.startup() {
        for (boy in listOf(chancy, daVinci, hops)) {
            onOpNpc1(boy.rimmington) { startDialogue(it.npc) { inRimmington(boy) } }
            onOpNpc1(boy.varrock) { startDialogue(it.npc) { inVarrock(boy) } }
        }
    }

    /**
     * The chemist's quest dialogue, or false when the quest has nothing for him and the area
     * script should carry on with his usual talk. [lamps] is that usual lamp-oil chat.
     */
    suspend fun Dialogue.chemistQuest(lamps: suspend Dialogue.() -> Unit): Boolean {
        val stage = biohazard.stage(player)
        if (stage !in STAGE_GOT_SAMPLES..STAGE_GUIDOR_TESTED) {
            return false
        }
        when (choice2("Lamps.", 1, "Your quest.", 2, title = "What do you want to talk about?")) {
            1 -> lamps()
            2 -> if (stage == STAGE_GOT_SAMPLES) firstVisit() else laterVisit()
        }
        return true
    }

    private suspend fun Dialogue.firstVisit() {
        chatNpc(
            neutral,
            "Sorry, I'm afraid we're just closing now. You'll have to come back another time.",
        )
        chatPlayer(neutral, "But I was sent by Elena from Ardougne.")
        chatNpc(
            happy,
            "Oh, well that's different then. Must be pretty important for her to send you all " +
                "this way.",
        )
        chatPlayer(neutral, "She asked me to pick up some touch paper for a guy called Guidor.")
        chatNpc(happy, "Guidor? This one's on me then, I owe him more than a few favours.")
        chatNpc(
            worried,
            "I should warn you though, getting to him might not be easy if you're carrying any " +
                "chemicals. The guards are searching anyone heading to that part of Varrock.",
        )
        chatPlayer(quiz, "Oh right... so am I going to be okay carrying these three vials with me?")
        chatNpc(
            neutral,
            "With touch paper as well? You're asking for trouble. You'd better use my errand " +
                "boys outside. Give them a vial each. I must warn you though, they do have a " +
                "habit of stealing things.",
        )
        chatPlayer(quiz, "Well that's no good. How do I stop them stealing my vials?")
        chatNpc(
            neutral,
            "Just make sure you give them something they have no reason to steal. One's a " +
                "painter, one's a gambler, and one's a drunk. Not ideal I know, but if you pay " +
                "peanuts you'll get monkeys, right?",
        )
        access.invAddOrDrop(objRepo, TOUCH_PAPER)
        biohazard.advanceTo(access, STAGE_GOT_TOUCH_PAPER)
        objbox(TOUCH_PAPER, "The chemist gives you some touch paper.")
        chatNpc(
            neutral,
            "Now don't stand around here gassing. This is clearly important to Elena. You'll " +
                "find Guidor's home in the south east corner of Varrock.",
        )
        chatPlayer(happy, "Okay, thanks for your help. I know Elena appreciates it.")
    }

    private suspend fun Dialogue.laterVisit() {
        chatPlayer(happy, "Hello again.")
        chatNpc(quiz, "Oh hello, do you need more touch paper?")
        if (player.inv.contains(TOUCH_PAPER)) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(
                neutral,
                "Fair enough, don't forget to talk to my errand boys outside if you need help " +
                    "getting things to Guidor. I must warn you though, they do have a habit of " +
                    "stealing things.",
            )
        } else {
            chatPlayer(happy, "Yes please.")
            chatNpc(happy, "Okay, here you go.")
            access.invAddOrDrop(objRepo, TOUCH_PAPER)
            objbox(TOUCH_PAPER, "The chemist gives you some touch paper.")
            chatNpc(
                neutral,
                "Don't forget to talk to my errand boys outside if you need help getting " +
                    "things to Guidor. I must warn you though, they do have a habit of " +
                    "stealing things.",
            )
        }
        chatPlayer(quiz, "Well that's no good. What if they take my stuff?")
        chatNpc(
            neutral,
            "Just make sure you give them something they have no reason to steal. One's a " +
                "painter, one's a gambler, and one's a drunk. Not ideal I know, but if you pay " +
                "peanuts you'll get monkeys, right?",
        )
    }

    private suspend fun Dialogue.inRimmington(boy: ErrandBoy) {
        val onQuest = biohazard.stage(player) in STAGE_GOT_TOUCH_PAPER..STAGE_GUIDOR_TESTED
        if (!onQuest) {
            idleLine(boy, rimmington = true)
            return
        }
        if (boy.held(player) != VIAL_NONE) {
            when (boy) {
                chancy ->
                    chatNpc(
                        neutral,
                        "Look, I've got your vial but I'm not taking two. I always like to play " +
                            "the percentages.",
                    )
                daVinci ->
                    chatNpc(
                        neutral,
                        "Oh, it's you again. Please don't distract me now, I'm contemplating the " +
                            "sublime.",
                    )
                else ->
                    chatNpc(
                        neutral,
                        "I suppose I'd better get going. I'll meet you at the Dancing " +
                            "Donkey Inn.",
                    )
            }
            return
        }
        when (boy) {
            chancy -> {
                chatPlayer(neutral, "Hello, I've got a vial for you to take to Varrock.")
                chatNpc(neutral, "Tssch... that chemist asks for a lot for the wages he pays.")
                chatPlayer(neutral, "Maybe you should ask him for more money.")
                chatNpc(shifty, "Nah... I just use my initiative here and there.")
            }
            daVinci -> {
                chatPlayer(neutral, "Hello, I hear you're an errand boy for the chemist.")
                chatNpc(
                    neutral,
                    "Well that's my job yes. But I don't necessarily define my identity in such " +
                        "black and white terms.",
                )
                chatPlayer(neutral, "Good for you. Now can you take a vial to Varrock for me?")
                chatNpc(neutral, "Go on then.")
            }
            else -> {
                chatPlayer(neutral, "Hi, I've got something for you to take to Varrock.")
                chatNpc(neutral, "Sounds like pretty thirsty work.")
                chatPlayer(neutral, "Well, there's an Inn in Varrock if you're desperate.")
                chatNpc(happy, "Don't worry, I'm a pretty resourceful fellow you know.")
            }
        }
        val vial =
            choice3(
                "You give him the vial of ethenea...",
                ETHENEA,
                "You give him the vial of liquid honey...",
                LIQUID_HONEY,
                "You give him the vial of sulphuric broline...",
                SULPHURIC_BROLINE,
            )
        if (!player.inv.contains(vial)) {
            mesbox("You can't give him what you don't have.")
            return
        }
        if (access.invDel(player.inv, vial).failure) {
            return
        }
        boy.hold(player, vialCode(vial))
        objbox(vial, "You give him the vial of ${vialName(vial)}.")
        when (boy) {
            chancy ->
                chatNpc(
                    happy,
                    "Right. I'll meet you at the Dancing Donkey Inn in south east Varrock. Be " +
                        "lucky!",
                )
            daVinci -> chatNpc(neutral, "Okay, I'll meet you at the Dancing Donkey in Varrock.")
            else -> {
                chatPlayer(neutral, "Okay, I'll see you in Varrock.")
                chatNpc(happy, "Sure, I'm a regular at the Dancing Donkey Inn as it happens.")
            }
        }
    }

    private suspend fun Dialogue.inVarrock(boy: ErrandBoy) {
        val vial = vialObj(boy.held(player))
        if (vial == null) {
            idleLine(boy, rimmington = false)
            return
        }
        if (vial == boy.safeVial) {
            handBack(boy, vial)
        } else {
            boy.hold(player, VIAL_NONE)
            stolen(boy)
        }
    }

    private suspend fun Dialogue.handBack(boy: ErrandBoy, vial: String) {
        when (boy) {
            chancy -> {
                chatPlayer(happy, "Hi, thanks for doing that.")
                chatNpc(neutral, "No problem.")
            }
            daVinci -> {
                chatNpc(happy, "Hello again. I hope your journey was as pleasant as mine.")
                chatPlayer(happy, "Well, as they say, it's always sunny in Gielinor.")
                chatNpc(neutral, "Okay, here it is.")
            }
            else -> {
                chatPlayer(quiz, "Hello, how was your journey?")
                chatNpc(neutral, "Pretty thirst-inducing actually...")
                chatPlayer(worried, "Please tell me that you haven't drunk the contents...")
                chatNpc(shocked, "Oh the gods no! What do you take me for?")
                chatNpc(neutral, "Here's your vial anyway.")
            }
        }
        if (player.inv.isFull() || access.invAdd(player.inv, vial).failure) {
            objbox(
                vial,
                "He tries to give you the vial of ${vialName(vial)} but you don't have enough " +
                    "room for it.",
            )
            return
        }
        boy.hold(player, VIAL_NONE)
        objbox(vial, "He gives you the vial of ${vialName(vial)}.")
        when (boy) {
            chancy -> {
                chatNpc(
                    neutral,
                    "Next time give me something more valuable... I couldn't get anything for " +
                        "this on the blackmarket.",
                )
                chatPlayer(neutral, "That was the idea.")
            }
            daVinci -> chatPlayer(happy, "Thanks, you've been a big help.")
            else -> chatPlayer(happy, "Thanks, I'll let you get your drink now.")
        }
    }

    private suspend fun Dialogue.stolen(boy: ErrandBoy) {
        when (boy) {
            chancy -> {
                chatPlayer(happy, "Hi, thanks for doing that.")
                chatNpc(happy, "No problem. I've got some money for you actually.")
                chatPlayer(quiz, "What do you mean?")
                chatNpc(
                    neutral,
                    "Well, it turns out that potion you gave me, was quite valuable...",
                )
                chatPlayer(shocked, "What?")
                chatNpc(
                    shifty,
                    "I know that I probably shouldn't have sold it... but some friends and I " +
                        "were having a little wager, the odds were just too good!",
                )
                chatPlayer(angry, "You sold my vial and gambled with the money?!")
                chatNpc(
                    happy,
                    "Actually yes... but praise be to Saradomin because I won! So all's well " +
                        "that ends well right?",
                )
                when (
                    choice2(
                        "No! Nothing could be further from the truth!",
                        1,
                        "You have no idea what you have just done!",
                        2,
                    )
                ) {
                    1 -> {
                        chatPlayer(angry, "No! Nothing could be further from the truth!")
                        chatNpc(
                            neutral,
                            "Well, there's no pleasing some people. Here's your cut anyway.",
                        )
                    }
                    2 -> {
                        chatPlayer(angry, "You have no idea what you have just done!")
                        chatNpc(neutral, "Ignorance is bliss I'm afraid. Here's your cut anyway.")
                    }
                }
                access.invAddOrDrop(objRepo, COINS, CHANCY_CUT)
            }
            daVinci -> {
                chatNpc(happy, "Hello again. I hope your journey was as pleasant as mine.")
                chatPlayer(neutral, "Yep. Anyway, I'll take the package off you now.")
                chatNpc(
                    confused,
                    "Package? That's a funny way to describe a liquid of such exquisite beauty!",
                )
                when (
                    choice2(
                        "I'm getting a bad feeling about this.",
                        1,
                        "Just give me the stuff now please.",
                        2,
                    )
                ) {
                    1 -> chatPlayer(worried, "I'm getting a bad feeling about this.")
                    2 -> chatPlayer(neutral, "Just give me the stuff now please.")
                }
                chatPlayer(quiz, "You do still have it don't you?")
                chatNpc(happy, "Absolutely. It's just not stored in a vial anymore.")
                chatPlayer(shocked, "What?")
                chatNpc(
                    happy,
                    "Instead it has been liberated. It now gleams from the canvas of my latest " +
                        "epic: The Majesty of Varrock!",
                )
                chatPlayer(
                    angry,
                    "That's great. Thanks to you I'll have to walk back to East Ardougne to get " +
                        "another vial.",
                )
                chatNpc(neutral, "Well you can't put a price on art.")
            }
            else -> {
                chatPlayer(quiz, "Hello, how was your journey?")
                chatNpc(neutral, "Pretty thirst-inducing actually...")
                chatPlayer(worried, "Please tell me that you haven't drunk the contents...")
                chatNpc(neutral, "Of course I can tell you that I haven't drunk the contents...")
                chatNpc(
                    drunk,
                    "But I'd be lying. Sorry about that me old mucker, can I get you a drink?",
                )
                chatPlayer(angry, "No, I think you've done enough for now.")
            }
        }
    }

    private suspend fun Dialogue.idleLine(boy: ErrandBoy, rimmington: Boolean) {
        when (boy) {
            chancy ->
                if (rimmington) {
                    chatPlayer(happy, "Hello! Playing solitaire?")
                    chatNpc(
                        neutral,
                        "Hush - I'm trying to perfect the art of dealing off the bottom of the " +
                            "deck.",
                    )
                } else {
                    chatPlayer(happy, "Good morning.")
                    chatNpc(angry, "Leave me alone. I'm trying to find my gambling buddies!")
                }
            daVinci ->
                chatNpc(
                    angry,
                    "Bah! A great artist such as myself should not have to suffer the " +
                        "HUMILIATION of spending time where the likes of you wander everywhere!",
                )
            else -> chatNpc(drunk, "Hops don't wanna talk now.")
        }
    }

    private companion object {
        const val COINS = "obj.coins"
        const val CHANCY_CUT = 10
    }
}
