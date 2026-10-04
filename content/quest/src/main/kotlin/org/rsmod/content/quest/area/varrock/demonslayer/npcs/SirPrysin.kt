package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEYS
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_DRAIN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_ROVIN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_TRAIBORN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.SILVERLIGHT
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.SILVERLIGHT_FEE
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_KEY_HUNT
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.varrock.demonslayer.silverlightCaseEmpty
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SirPrysin
@Inject
constructor(private val demonSlayer: DemonSlayerQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    private val presentingType by lazy {
        ServerCacheManager.getNpc(PRESENTING_NPC.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $PRESENTING_NPC")
    }

    override fun ScriptContext.startup() {
        onOpNpc1("npc.sir_prysin") { startDialogue(it.npc) { prysin(it.npc) } }
        onOpNpc1(PRESENTING_NPC) { startDialogue(it.npc) { prysin(it.npc) } }
    }

    private suspend fun Dialogue.prysin(npc: Npc) {
        when (demonSlayer.stage(player)) {
            STAGE_STARTED -> introduction(askedByAris = true)
            STAGE_KEY_HUNT ->
                if (player.silverlightCaseEmpty) beforeFight() else keyHunt(npc)
            STAGE_COMPLETE -> afterQuest()
            else -> introduction(askedByAris = false)
        }
    }

    private suspend fun Dialogue.introduction(askedByAris: Boolean) {
        chatNpc(quiz, "Hello, who are you?")
        val options =
            buildList {
                add("I am a mighty adventurer. Who are you?" to Intro.Adventurer)
                add("I'm not sure, I was hoping you could tell me." to Intro.NotSure)
                if (askedByAris) add("Aris said I should come and talk to you." to Intro.Aris)
            }
        when (menu(options)) {
            Intro.Adventurer -> {
                chatPlayer(happy, "I am a mighty adventurer, who are you?")
                chatNpc(happy, "I am Sir Prysin. A bold and famous knight of the realm.")
            }
            Intro.NotSure -> {
                chatPlayer(confused, "I was hoping you could tell me.")
                chatNpc(confused, "Well I've never met you before.")
            }
            Intro.Aris -> sentByAris()
        }
    }

    private suspend fun Dialogue.sentByAris() {
        chatPlayer(neutral, "Aris said I should come and talk to you.")
        chatNpc(
            quiz,
            "Aris? Is she still alive? I remember her from when I was pretty young. Well what " +
                "do you need to talk to me about?",
        )
        val stillAlive =
            menu("I need to find Silverlight." to false, "Yes, she is still alive." to true)
        if (stillAlive) {
            chatPlayer(happy, "Yes she is still alive. She lives right outside the castle!")
            chatNpc(
                confused,
                "Oh, is that the same Aris? I would have thought she would have died by now. " +
                    "She was pretty old when I was a lad.",
            )
            chatNpc(quiz, "Anyway, what can I do for you?")
        }
        chatPlayer(neutral, "I need to find Silverlight.")
        chatNpc(quiz, "What do you need to find that for?")
        chatPlayer(neutral, "I need it to fight Delrith.")
        chatNpc(
            shocked,
            "Delrith? I thought the world was rid of him, thanks to my great-grandfather.",
        )
        if (
            menu(
                "Well, Aris' crystal ball seems to think otherwise." to true,
                "He's back and unfortunately I've got to deal with him." to false,
            )
        ) {
            chatPlayer(neutral, "Well Aris' crystal ball seems to think otherwise.")
            chatNpc(neutral, "Well if the ball says so, I'd better help you.")
        } else {
            chatPlayer(neutral, "He's back and unfortunately I've got to deal with him.")
            chatNpc(
                neutral,
                "You don't look up to much. I suppose Silverlight may be good enough to carry " +
                    "you through though.",
            )
        }
        chatNpc(neutral, "The problem is getting Silverlight.")
        chatPlayer(quiz, "You mean you don't have it?")
        chatNpc(
            neutral,
            "Oh I do have it, but it is so powerful that the king made me put it in a special " +
                "box which needs three different keys to open it. That way it won't fall into " +
                "the wrong hands.",
        )
        if (
            menu(
                "So give me the keys!" to true,
                "And why is this a problem?" to false,
            )
        ) {
            chatPlayer(quiz, "So give me the keys!")
            chatNpc(worried, "Um, well, it's not so easy.")
        } else {
            chatPlayer(quiz, "And why is this a problem?")
        }
        chatNpc(
            worried,
            "I kept one of the keys. I gave the other two to other people for safe keeping.",
        )
        chatNpc(neutral, "One I gave to Rovin, the captain of the palace guard.")
        chatNpc(neutral, "I gave the other to the wizard Traiborn.")
        if (demonSlayer.stage(player) == STAGE_STARTED) {
            demonSlayer.quest.advanceQuestStage(access)
        }
        keyQuestions(listOf(KeyTopic.Key, KeyTopic.Rovin, KeyTopic.Wizard))
    }

    private suspend fun Dialogue.keyQuestions(start: List<KeyTopic>) {
        var options = start
        while (true) {
            when (menu(options.map { it.label to it })) {
                KeyTopic.Key -> {
                    chatPlayer(quiz, "Can you give me your key?")
                    chatNpc(worried, "Um.... ah....")
                    chatNpc(worried, "Well there's a problem there as well.")
                    chatNpc(
                        sad,
                        "I managed to drop the key in the drain just outside the palace kitchen. " +
                            "It is just inside and I can't reach it.",
                    )
                    options = listOf(KeyTopic.Drain, KeyTopic.Rovin, KeyTopic.Wizard)
                }
                KeyTopic.Drain -> {
                    chatPlayer(quiz, "So what does the drain connect to?")
                    chatNpc(
                        neutral,
                        "It is the drain for the drainpipe running from the sink in the kitchen " +
                            "down to the palace sewers.",
                    )
                    options = listOf(KeyTopic.Rovin, KeyTopic.Wizard, KeyTopic.Leave)
                }
                KeyTopic.Rovin -> {
                    chatPlayer(quiz, "Where can I find Captain Rovin?")
                    chatNpc(
                        neutral,
                        "Captain Rovin lives at the top of the guards' quarters in the " +
                            "north-west wing of this palace.",
                    )
                    options = listOf(KeyTopic.Key, KeyTopic.Wizard, KeyTopic.Leave)
                }
                KeyTopic.Wizard -> {
                    chatPlayer(quiz, "Where does the wizard live?")
                    chatNpc(neutral, "Wizard Traiborn?")
                    chatNpc(
                        neutral,
                        "He is one of the wizards who lives in the tower on the little island " +
                            "just off the south coast. I believe his quarters are on the first " +
                            "floor of the tower.",
                    )
                    options = listOf(KeyTopic.Key, KeyTopic.Rovin, KeyTopic.Leave)
                }
                KeyTopic.Leave -> {
                    chatPlayer(neutral, "Well I'd better go key hunting.")
                    chatNpc(neutral, "Ok, goodbye.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.keyHunt(npc: Npc) {
        chatNpc(quiz, "So how are you doing with getting the keys?")
        val held = demonSlayer.keysCarried(player)
        if (held.size == KEYS.size) {
            handOverSilverlight(npc)
            return
        }
        if (held.isEmpty()) {
            chatPlayer(sad, "I haven't found any of them yet.")
        } else {
            chatPlayer(happy, describeKeys(held))
        }
        if (
            menu(
                "Can you remind me where all the keys were again?" to true,
                "I'm still looking." to false,
            )
        ) {
            chatPlayer(quiz, "Can you remind me where all the keys were again?")
            chatNpc(
                neutral,
                "I kept one of the keys. I gave the other two to other people for safe keeping.",
            )
            chatNpc(neutral, "One I gave to Rovin, the captain of the palace guard.")
            chatNpc(neutral, "I gave the other to the wizard Traiborn.")
            keyQuestions(listOf(KeyTopic.Key, KeyTopic.Rovin, KeyTopic.Wizard))
        } else {
            chatPlayer(neutral, "I'm still looking.")
            chatNpc(neutral, "Ok, tell me when you've got them all.")
        }
    }

    private fun describeKeys(held: List<String>): String =
        when (held.toSet()) {
            setOf(KEY_ROVIN) -> "I've got the key from Captain Rovin."
            setOf(KEY_TRAIBORN) -> "I've got the key from Wizard Traiborn."
            setOf(KEY_DRAIN) -> "I've got the key which you dropped down the drain."
            setOf(KEY_TRAIBORN, KEY_ROVIN) ->
                "I've got the keys from Wizard Traiborn and Captain Rovin."
            setOf(KEY_ROVIN, KEY_DRAIN) ->
                "I've got the key from Captain Rovin and the one that you dropped down the drain."
            else ->
                "I've got the key from Wizard Traiborn and the one that you dropped down the drain."
        }

    private suspend fun Dialogue.handOverSilverlight(npc: Npc) {
        chatPlayer(happy, "I've got all three keys!")
        chatNpc(happy, "Excellent! Now I can give you Silverlight.")
        val swapped =
            player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                for (key in KEYS) {
                    delete {
                        from = inventory
                        obj = key.asRSCM(RSCMType.OBJ)
                        strictCount = 1
                    }
                }
                insert {
                    into = inventory
                    obj = SILVERLIGHT.asRSCM(RSCMType.OBJ)
                    strictCount = 1
                }
            }
        if (swapped.failure) {
            return
        }
        player.silverlightCaseEmpty = true
        presentSilverlight(npc)
        objbox(SILVERLIGHT, "Sir Prysin hands you a very shiny sword.")
        chatNpc(
            neutral,
            "That sword belonged to my great-grandfather. Make sure you treat it with respect!",
        )
        chatNpc(happy, "Now go kill that demon!")
    }

    private suspend fun Dialogue.presentSilverlight(npc: Npc) {
        access.npcChangeType(npc, presentingType, PRESENTING_TICKS)
        npc.anim("seq.qip_ds_presenting_silverlight_start")
        access.soundSynth("synth.pillory_unlock")
        delay(2)
        npc.anim("seq.qip_ds_presenting_sword_middle")
        delay(2)
        npc.anim("seq.qip_ds_presenting_sword_end")
        access.anim("seq.qip_ds_recieving_silverlight")
        access.soundSynth("synth.found_gem")
        delay(1)
    }

    private suspend fun Dialogue.beforeFight() {
        chatNpc(quiz, "Have you sorted that demon out yet?")
        if (demonSlayer.holdsSilverlight(player)) {
            chatPlayer(neutral, "No, not yet.")
            chatNpc(
                neutral,
                "Well get on with it. He'll be pretty powerful when he gets to full strength.",
            )
            return
        }
        chatPlayer(sad, "Not yet. And I, um, lost Silverlight.")
        access.invAddOrDrop(objRepo, SILVERLIGHT)
        chatNpc(
            angry,
            "Yes, I know, someone returned it to me. Take better care of it this time.",
        )
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Hello. I've heard you stopped the demon, well done.")
        val options =
            buildList {
                add("Yes, that's right." to true)
                if (!demonSlayer.holdsSilverlight(player)) {
                    add("Yes, although I'm afraid I've lost Silverlight." to false)
                }
            }
        if (menu(options)) {
            chatPlayer(happy, "Yes, that's right.")
            chatNpc(happy, "A good job well done then.")
            chatPlayer(happy, "Thank you.")
            return
        }
        chatPlayer(sad, "Yes, although I'm afraid I've lost Silverlight.")
        chatNpc(
            neutral,
            "Yes, news of your carelessness is almost as widespread as knowledge of your " +
                "victory. Fortunately for you, Silverlight has come back into my possession.",
        )
        if (
            menu(
                "Phew, that's a relief." to true,
                "Is there any chance of me borrowing it again?" to false,
            )
        ) {
            chatPlayer(happy, "Phew, that's a relief.")
            return
        }
        chatPlayer(quiz, "Is there any chance of me borrowing it again?")
        chatNpc(
            angry,
            "I'm not going to give it away that easily again, it's far too important to be " +
                "treated so disrespectfully.",
        )
        chatNpc(
            neutral,
            "If you wish to make use of Silverlight again, it will cost you $SILVERLIGHT_FEE " +
                "gold pieces. Maybe that will encourage you to look after it.",
        )
        if (
            !menu(
                "No way, it's not worth that much." to false,
                "Ok, I'll pay." to true,
            )
        ) {
            chatPlayer(angry, "No way, it's not worth that much.")
            return
        }
        chatPlayer(neutral, "Ok, I'll pay.")
        if (access.invCoinTotal() < SILVERLIGHT_FEE) {
            chatPlayer(sad, "But I don't have that much money.")
            return
        }
        val bought =
            player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                delete {
                    from = inventory
                    obj = "obj.coins".asRSCM(RSCMType.OBJ)
                    strictCount = SILVERLIGHT_FEE
                }
                insert {
                    into = inventory
                    obj = SILVERLIGHT.asRSCM(RSCMType.OBJ)
                    strictCount = 1
                }
            }
        if (bought.failure) {
            chatNpc(
                angry,
                "You don't even have enough free space to carry it. Stop wasting my time!",
            )
            return
        }
        chatNpc(happy, "May you make good use of it.")
    }

    private enum class Intro {
        Adventurer,
        NotSure,
        Aris,
    }

    private enum class KeyTopic(val label: String) {
        Key("Can you give me your key?"),
        Drain("So what does the drain lead to?"),
        Rovin("Where can I find Captain Rovin?"),
        Wizard("Where does the wizard live?"),
        Leave("Well I'd better go key hunting."),
    }

    private companion object {
        const val PRESENTING_NPC = "npc.sir_prysin_silverlight"
        const val PRESENTING_TICKS = 8
    }
}
