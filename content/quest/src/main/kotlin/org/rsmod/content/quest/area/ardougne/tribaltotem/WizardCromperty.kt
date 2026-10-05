package org.rsmod.content.quest.area.ardougne.tribaltotem

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.CrompertyPost
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.CrompertyPre
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.DepotLanding
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Started
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.TrapFound
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.content.skills.runecrafting.essence.teleportToRuneEssenceMine
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wizard Cromperty's teleport block is the quest's way into the mansion: the teleport lands beside
 * the crate that carries the other block, so the destination follows the crate's delivery.
 */
class WizardCromperty @Inject constructor(private val tribalTotem: TribalTotemQuest) :
    PluginScript() {

    private val quest
        get() = tribalTotem.quest

    override fun ScriptContext.startup() {
        for (cromperty in listOf(CrompertyPre, CrompertyPost)) {
            onOpNpc1(cromperty) { startDialogue(it.npc) { crompertyDialogue() } }
            onOpNpc3(cromperty) { teleportToRuneEssenceMine(it.npc, EssenceMineTeleporter.Cromperty) }
        }
    }

    private suspend fun Dialogue.crompertyDialogue() {
        chatNpc(happy, "Hello there. My name is Cromperty. I am a Wizard, and an inventor.")
        chatNpc(
            happy,
            "You must be ${player.displayName}. My good friend Sedridor has told me about " +
                "you. As both wizard and inventor, he has aided me in my great invention!",
        )
        when (
            choice3(
                "Two jobs? That's got to be tough.",
                0,
                "So what have you invented?",
                1,
                "Can you teleport me to the Rune Essence?",
                2,
            )
        ) {
            0 -> twoJobs()
            1 -> {
                chatPlayer(quiz, "So what have you invented?")
                invention()
            }
            else -> {
                chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
                teleportToRuneEssenceMine(EssenceMineTeleporter.Cromperty)
            }
        }
    }

    private suspend fun Dialogue.twoJobs() {
        chatPlayer(quiz, "Two jobs? That's got to be tough.")
        chatNpc(happy, "Not when you combine them it isn't! I invent MAGIC things!")
        val invented =
            choice2(
                "So what have you invented?",
                true,
                "Well, I shall leave you to your inventing.",
                false,
            )
        if (invented) {
            chatPlayer(quiz, "So what have you invented?")
            invention()
        } else {
            chatPlayer(neutral, "Well, I shall leave you to your inventing.")
            chatNpc(happy, "Thanks for dropping by! Stop again anytime!")
        }
    }

    private suspend fun Dialogue.invention() {
        chatNpc(
            happy,
            "Ah! My latest invention is my patent pending teleportation block! It emits a low " +
                "level magical signal, that will allow me to locate it anywhere in the world, " +
                "and teleport anything",
        )
        chatNpc(
            happy,
            "directly to it! I hope to revolutionise the entire teleportation system! Don't " +
                "you think I'm great? Uh, I mean it's great?",
        )
        when (
            choice3(
                "So where is the other block?",
                0,
                "Can I be teleported please?",
                1,
                "Well done, that's very clever.",
                2,
            )
        ) {
            0 -> otherBlock()
            1 -> {
                chatPlayer(quiz, "Can I be teleported please?")
                teleportOffer()
            }
            else -> {
                chatPlayer(happy, "Well done, that's very clever.")
                chatNpc(
                    laugh,
                    "Yes it is isn't it? Forgive me for feeling a little smug, this is a major " +
                        "breakthrough in the field of teleportation!",
                )
            }
        }
    }

    private suspend fun Dialogue.otherBlock() {
        chatPlayer(quiz, "So where is the other block?")
        chatNpc(
            confused,
            "Well... Hmm. I would guess somewhere between here and the Wizards' Tower in " +
                "Misthalin. All I know is that it hasn't got there yet as the wizards there " +
                "would have contacted me.",
        )
        chatNpc(
            neutral,
            "I'm using the GPDT for delivery. They assured me it would be delivered promptly.",
        )
        if (choice2("Can I be teleported please?", true, "Who are the GPDT?", false)) {
            chatPlayer(quiz, "Can I be teleported please?")
            teleportOffer()
        } else {
            chatPlayer(quiz, "Who are the GPDT?")
            chatNpc(
                happy,
                "The Gielinor Parcel Delivery Team. They come very highly recommended. Their " +
                    "motto is: 'We aim to deliver your stuff at some point after you have " +
                    "paid us!'",
            )
        }
    }

    private suspend fun Dialogue.teleportOffer() {
        chatNpc(
            happy,
            "By all means! I'm afraid I can't give you any specifics as to where you will " +
                "come out however. Presumably wherever the other block is located.",
        )
        if (
            !choice2(
                "Yes, that sounds good. Teleport me!",
                true,
                "That sounds dangerous. Leave me here.",
                false,
            )
        ) {
            chatPlayer(worried, "That sounds dangerous. Leave me here.")
            chatNpc(neutral, "As you wish.")
            return
        }
        chatPlayer(happy, "Yes, that sounds good. Teleport me!")
        chatNpc(happy, "Okey dokey! Ready?")
        val destination = blockLocation()
        if (destination == null) {
            chatNpc(confused, "Hmmm.... that's odd... I can't seem to get a signal...")
            chatPlayer(neutral, "Oh well, never mind.")
            return
        }
        blockTeleport(destination)
    }

    private fun Dialogue.blockLocation(): CoordGrid? {
        val stage = tribalTotem.stage(player)
        return when {
            stage !in Started..TrapFound -> null
            stage >= TribalTotemQuest.Delivered -> TribalTotemQuest.MansionLanding
            else -> DepotLanding
        }
    }

    private suspend fun Dialogue.blockTeleport(destination: CoordGrid) {
        val npc = checkNotNull(npc) { "Cromperty's teleport requires an npc dialogue context." }
        npc.say("Dipsolum sententa sententi!")
        npc.spotanim("spotanim.curse_casting", height = 92)
        player.soundSynth("synth.curse_cast_and_fire")
        delay(1)
        npc.facePlayer(player)
        delay(1)
        npc.resetFaceEntity()
        access.spotanim("spotanim.curse_impact", delay = 15, height = 124)
        player.soundSynth("synth.curse_hit", delay = 15)
        delay(1)
        access.telejump(destination)
    }
}
