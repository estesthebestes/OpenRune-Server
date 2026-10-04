package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_ROVIN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.STAGE_KEY_HUNT
import org.rsmod.content.quest.area.varrock.demonslayer.silverlightCaseEmpty
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CaptainRovin
@Inject
constructor(private val demonSlayer: DemonSlayerQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.captain_rovin") { startDialogue(it.npc) { rovin() } }
    }

    private suspend fun Dialogue.rovin() {
        chatNpc(
            angry,
            "What are you doing up here? Only the palace guards are allowed up here.",
        )
        when (
            menu(
                "I am one of the palace guards." to Opening.Guard,
                "What about the King?" to Opening.King,
                "Yes I know, but this is important." to Opening.Important,
            )
        ) {
            Opening.Guard -> oneOfTheGuards()
            Opening.King -> theKing()
            Opening.Important -> important()
        }
    }

    private suspend fun Dialogue.oneOfTheGuards() {
        chatPlayer(shifty, "I am one of the palace guards.")
        chatNpc(angry, "No, you're not! I know all the palace guards.")
        if (menu("I'm a new recruit." to true, "I've had extensive plastic surgery." to false)) {
            chatPlayer(shifty, "I'm a new recruit.")
            chatNpc(angry, "I interview all the new recruits. I'd know if you were one of them.")
            chatPlayer(sad, "That blows that story out of the water then.")
            chatNpc(angry, "Get out of my sight.")
        } else {
            chatPlayer(shifty, "I've had extensive plastic surgery.")
            chatNpc(
                confused,
                "What sort of surgery is that? I've never heard of it. Besides, you look " +
                    "reasonably healthy.",
            )
            chatNpc(angry, "Why is this relevant anyway? You still shouldn't be here.")
        }
    }

    private suspend fun Dialogue.theKing() {
        chatPlayer(quiz, "What about the King? Surely you'd let him up here.")
        chatNpc(
            neutral,
            "Well, yes, I suppose we'd let him up. He doesn't generally want to come up here, " +
                "but if he did want to, he could.",
        )
        chatNpc(angry, "Anyway, you're not the King either. So get out of my sight.")
    }

    private suspend fun Dialogue.important() {
        chatPlayer(neutral, "Yes, I know, but this is important.")
        chatNpc(neutral, "Ok, I'm listening. Tell me what's so important.")
        val seekingKey =
            demonSlayer.stage(player) == STAGE_KEY_HUNT && !player.silverlightCaseEmpty
        val options =
            buildList {
                if (seekingKey) {
                    add("There's a demon who wants to invade this city." to Reason.Demon)
                }
                add("Erm I forgot." to Reason.Forgot)
                add("The castle has just received its ale delivery." to Reason.Ale)
            }
        when (menu(options)) {
            Reason.Demon -> demonWarning()
            Reason.Forgot -> {
                chatPlayer(confused, "Erm I forgot.")
                chatNpc(neutral, "Well it can't be that important then.")
                chatPlayer(quiz, "How do you know?")
                chatNpc(angry, "Just go away.")
            }
            Reason.Ale -> {
                chatPlayer(happy, "The castle has just received its ale delivery.")
                chatNpc(
                    happy,
                    "Now that is important. However I'm the wrong person to speak to about it. " +
                        "Go talk to the kitchen staff.",
                )
            }
        }
    }

    private suspend fun Dialogue.demonWarning() {
        chatPlayer(worried, "There's a demon who wants to invade the city.")
        if (demonSlayer.holdsKey(access, KEY_ROVIN)) {
            keyAlreadyGiven()
            return
        }
        chatNpc(quiz, "Is it a powerful demon?")
        if (!menu("Not really." to false, "Yes, very." to true)) {
            chatPlayer(neutral, "Not really.")
            chatNpc(
                neutral,
                "Well, I'm sure the palace guards can deal with it, then. Thanks for the " +
                    "information.",
            )
            return
        }
        chatPlayer(neutral, "Yes, very.")
        chatNpc(
            worried,
            "As good as the palace guards are, I don't know if they're up to taking on a very " +
                "powerful demon.",
        )
        if (
            menu(
                "Yeah, the palace guards are rubbish!" to true,
                "It's not them who are going to fight the demon, it's me." to false,
            )
        ) {
            chatPlayer(laugh, "Yeah, the palace guards are rubbish!")
            chatNpc(laugh, "Yeah, they're--")
            chatNpc(verymad, "Wait! How dare you insult the palace guards? Get out of my sight!")
            return
        }
        chatPlayer(neutral, "It's not them who are going to fight the demon, it's me.")
        chatNpc(quiz, "What, all by yourself? How are you going to do that?")
        chatPlayer(
            neutral,
            "I'm going to use the powerful sword Silverlight, which I believe you have one of " +
                "the keys for?",
        )
        chatNpc(neutral, "Yes, I do. But why should I give it to you?")
        persuade()
    }

    private suspend fun Dialogue.persuade() {
        while (true) {
            when (
                menu(
                    "Fortune-teller Aris said I was destined to kill the demon." to Plea.Aris,
                    "Otherwise the demon will destroy the city!" to Plea.City,
                    "Sir Prysin said you would give me the key." to Plea.Prysin,
                )
            ) {
                Plea.Aris -> arisPlea("Fortune-teller Aris said I was destined to kill the demon.")
                Plea.City -> cityPlea()
                Plea.Prysin -> return prysinPlea()
                Plea.Why -> Unit
            }
        }
    }

    private suspend fun Dialogue.arisPlea(label: String) {
        chatPlayer(neutral, label)
        chatNpc(
            angry,
            "A fortune-teller? Destiny? I don't believe in that stuff. I got where I am today " +
                "by hard work, not by destiny! Why should I care what that mad old " +
                "fortune-teller says?",
        )
    }

    private suspend fun Dialogue.cityPlea() {
        chatPlayer(worried, "Otherwise the demon will destroy the city!")
        chatNpc(
            angry,
            "You can't fool me! How do I know you haven't just made that story up to get my " +
                "key?",
        )
    }

    private suspend fun Dialogue.prysinPlea() {
        chatPlayer(neutral, "Sir Prysin said you would give me the key.")
        chatNpc(
            angry,
            "Oh, he did, did he? Well I don't report to Sir Prysin, I report directly to the " +
                "king!",
        )
        chatNpc(
            verymad,
            "I didn't work my way up through the ranks of the palace guards so I could take " +
                "orders from an ill-bred moron who only has his job because his great-" +
                "grandfather was a hero with a silly name!",
        )
        while (true) {
            when (
                menu(
                    "Why did he give you one of the keys then?" to Plea.Why,
                    "Aris said I was destined to kill the demon." to Plea.Aris,
                    "Otherwise the demon will destroy the city!" to Plea.City,
                )
            ) {
                Plea.Why -> return whyTheKey()
                Plea.Aris -> arisPlea("Fortune-teller Aris said I was destined to kill the demon.")
                Plea.City -> cityPlea()
                Plea.Prysin -> Unit
            }
        }
    }

    private suspend fun Dialogue.whyTheKey() {
        chatPlayer(quiz, "Why did he give you one of the keys then?")
        chatNpc(
            neutral,
            "Only because the king ordered him to! The king couldn't get Sir Prysin to part " +
                "with his precious ancestral sword, but he made him lock it up so he couldn't " +
                "lose it.",
        )
        chatNpc(
            neutral,
            "I got one key and I think some wizard got another. Now what happened to the third " +
                "one?",
        )
        chatPlayer(laugh, "Sir Prysin dropped it down a drain!")
        chatNpc(laugh, "Ha ha ha! The idiot!")
        chatNpc(
            happy,
            "Okay, I'll give you the key, just so that it's you that kills the demon and not " +
                "Sir Prysin!",
        )
        access.invAddOrDrop(objRepo, KEY_ROVIN)
        objbox(KEY_ROVIN, "Captain Rovin hands you a key.")
    }

    private suspend fun Dialogue.keyAlreadyGiven() {
        chatNpc(quiz, "Yes, you said before, haven't you killed it yet?")
        chatPlayer(
            neutral,
            "Well I'm going to use the powerful sword Silverlight, which I believe you have one " +
                "of the keys for?",
        )
        if (player.inv.count(KEY_ROVIN) > 0) {
            chatNpc(neutral, "I already gave you my key. Check your pockets.")
        } else {
            chatNpc(
                neutral,
                "I already gave you my key. Maybe you left it somewhere. Have you checked your " +
                    "bank account?",
            )
        }
    }

    private enum class Opening {
        Guard,
        King,
        Important,
    }

    private enum class Reason {
        Demon,
        Forgot,
        Ale,
    }

    private enum class Plea {
        Aris,
        City,
        Prysin,
        Why,
    }
}
