package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.GnomeMaze
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.AgreedToGatherLogs
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Complete
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.GnomeAmulet
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.HasOrb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Orb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.OrbReturned
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Orbs
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.RecommendedCombat
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Started
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.WarlordSlain
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.bolrenGotOrbs
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.elkoyGuides
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.fadeFromBlack
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.fadeToBlack
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * King Bolren, the quest's start and end. He sits in the centre of the maze beside the spirit tree
 * with the Local Gnomes around him, who chant during the closing ceremony.
 */
class KingBolren
@Inject
constructor(
    private val quest: TreeGnomeVillageQuest,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val search: NpcSearch,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(Bolren) { startDialogue(it.npc) { bolren(it.npc) } }
    }

    private suspend fun Dialogue.bolren(npc: Npc) {
        when (quest.stage(player)) {
            0 -> beforeQuest()
            Started -> {
                chatPlayer(happy, "Hello Bolren.")
                chatNpc(
                    worried,
                    "Greetings, traveller. We have to get that orb back, and Khazard's " +
                    "troops are " +
                        "keeping it somewhere north of here.",
                )
                chatPlayer(neutral, "Ok, I'll try my best.")
            }
            in AgreedToGatherLogs until HasOrb -> {
                chatPlayer(happy, "Hello Bolren.")
                chatNpc(worried, "The orb is up at the battlefield, north of the maze.")
            }
            HasOrb -> {
                if (player.inv.contains(Orb)) {
                    returnFirstOrb()
                    return
                }
                chatPlayer(happy, "Hello Bolren.")
                chatNpc(quiz, "Have you got the orb?")
                chatPlayer(sad, "No, I'm afraid not.")
                chatNpc(worried, "Please hurry. Without the orb we can't hope to survive.")
            }
            OrbReturned,
            WarlordSlain -> {
                chatPlayer(happy, "Bolren, I have returned.")
                chatNpc(quiz, "You made it back! Do you have the orbs?")
                if (!player.inv.contains(Orbs)) {
                    chatPlayer(sad, "No, I'm afraid not.")
                    chatNpc(worried, "Please hurry. Without the orbs we can't hope to survive.")
                    return
                }
                returnOrbs(npc)
            }
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Well, hello stranger. I'm Bolren, king of the tree gnomes.")
        chatNpc(happy, "I'm surprised you found your way in. Perhaps my maze is too easy.")
        chatPlayer(happy, "Maybe.")
        chatNpc(worried, "Sadly I have much graver matters on my mind just now.")
        val help = choice2("Can I help at all?", true, "I'll leave you to it then.", false)
        if (!help) {
            chatPlayer(neutral, "I'll leave you to it then.")
            chatNpc(neutral, "Very well, take care.")
            return
        }
        chatPlayer(quiz, "Can I help at all?")
        chatNpc(happy, "I'm glad you asked.")
        chatNpc(
            sad,
            "My people are in grave danger. The Spirit Tree has always protected us, and no " +
                "creature of darkness can harm us while its three orbs stay in place.",
        )
        chatNpc(
            sad,
            "We are not a warlike race, but we fight when we have to. Many gnomes have fallen " +
                "resisting the dark forces of Khazard in the north.",
        )
        chatNpc(
            sad,
            "In desperation we carried one of the orbs of protection to the battlefield. It was " +
                "a foolish thing to do.",
        )
        chatNpc(worried, "Khazard's troops took it, and now we have no defence at all.")
        chatPlayer(quiz, "How can I help?")
        chatNpc(
            neutral,
            "A fighter like you would be a great asset on the battlefield. If you could win " +
                "the orb back, my people would be forever in your debt.",
        )
        if (player.combatLevel < RecommendedCombat) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RecommendedCombat."
            )
        }
        if (!startQuestPrompt(quest.quest)) {
            chatPlayer(neutral, "I'm sorry but I won't be involved.")
            chatNpc(neutral, "Then travel safely.")
            return
        }
        quest.advanceTo(access, Started)
        chatPlayer(happy, "I would be glad to help.")
        chatNpc(
            happy,
            "Thank you. The battlefield lies north of the maze, and Commander Montai will " +
                "tell you how things stand there.",
        )
        chatNpc(worried, "If he's still alive, that is.")
        chatNpc(neutral, "My assistant will lead you out. Good luck, friend. Bring back that orb.")
        access.elkoyGuides(
            GnomeMaze.Entrance,
            "We're out of the maze now. Please be quick, we need that orb to survive.",
            box = "Elkoy guides you out of the maze.",
        )
    }

    private suspend fun Dialogue.returnFirstOrb() {
        chatPlayer(happy, "I have the orb.")
        chatNpc(sad, "Oh no... the misery, the horror!")
        chatPlayer(quiz, "King Bolren, are you OK?")
        chatNpc(sad, "Thank you, traveller, but it's too late. We are all doomed.")
        chatPlayer(quiz, "What happened?")
        chatNpc(sad, "They came in the night. I don't know how many, but it was enough.")
        chatPlayer(quiz, "Who?")
        chatNpc(
            sad,
            "Khazard's troops. They cut down everyone in their path. Women, children... my own " +
                "wife.",
        )
        chatPlayer(sad, "I'm sorry.")
        chatNpc(sad, "They carried off the other two orbs. We have no protection left.")
        chatPlayer(quiz, "Where did they take them?")
        chatNpc(neutral, "North, beyond the stronghold. A warlord has them.")
        if (access.invDel(access.inv, Orb).failure) {
            return
        }
        quest.advanceTo(access, OrbReturned)
        val goOn =
            choice2(
                "I will find the warlord and bring back the orbs.",
                true,
                "I'm sorry but I can't help.",
                false,
            )
        if (!goOn) {
            chatPlayer(sad, "I'm sorry but I can't help.")
            chatNpc(sad, "I understand. This isn't your fight.")
            return
        }
        chatPlayer(neutral, "I will find the warlord and bring back the orbs.")
        chatNpc(
            happy,
            "You are brave, though even you will find this hard. I wish you luck. Once more, you " +
                "are our only hope.",
        )
        chatNpc(
            neutral,
            "I'll keep this orb safe and pray that you return. My assistant will see you out.",
        )
        access.elkoyGuides(
            GnomeMaze.Entrance,
            "Good luck, friend.",
            box = "Elkoy guides you out of the maze.",
        )
    }

    private suspend fun Dialogue.returnOrbs(npc: Npc) {
        chatPlayer(happy, "I have them here.")
        chatNpc(
            happy,
            "Hooray! You're amazing. I never thought it possible, but you have saved us.",
        )
        chatNpc(
            happy,
            "Once the orbs are back where they belong we will be safe again. The ceremony must " +
                "begin at once.",
        )
        chatPlayer(quiz, "What does the ceremony involve?")
        chatNpc(
            neutral,
            "The spirit tree has watched over us for centuries. Now we must honour it.",
        )
        access.ceremony(npc)
        chatNpc(happy, "At last my people are safe, and we can live in peace once more.")
        chatPlayer(happy, "I'm pleased I could help.")
        chatNpc(happy, "You are too modest, brave traveller.")
        chatNpc(
            happy,
            "Please accept this amulet for your trouble. It is carved from the same sacred " +
                "stone as the orbs of protection, and it will help keep you safe on your travels.",
        )
        chatPlayer(happy, "Thank you King Bolren.")
        chatNpc(
            neutral,
            "The tree has other powers too, some I may not tell. But as a friend of the gnomes " +
                "I can now let you use its magic to travel to other trees grown from related " +
                "seeds.",
        )
        if (access.invDel(access.inv, Orbs).failure) {
            return
        }
        access.invAddOrDrop(objRepo, GnomeAmulet)
        quest.quest.setQuestStage(access, Complete)
    }

    /**
     * The Local Gnomes chant while Bolren holds the orbs out, then the orbs settle into the spirit
     * tree, which is the multiloc `bolren_got_orbs` switching to its orb-bearing form. The orbs
     * stay in the player's inventory until the conversation ends.
     */
    private suspend fun ProtectedAccess.ceremony(bolren: Npc) {
        val chanters = npcFindAll(TreeCentre, Chanter, ChanterRadius, HuntVis.Off, search).toList()
        val cast = chanters + bolren
        val homes = cast.associateWith { it.coords }
        player.bolrenGotOrbs = OrbsHandedOver
        fadeToBlack()
        try {
            hideEntityOps()
            minimapHideMap()
            for (npc in cast) {
                npc.mode = NpcMode.None
            }
            bolren.teleport(collision, BolrenMark)
            bolren.lockFacingDirection(Direction.East)
            for (chanter in chanters) {
                chanter.faceSquare(TreeCentre)
            }
            telejump(PlayerMark)
            faceDirection(Direction.East)
            camMoveTo(CameraFrom, height = CameraHeight, rate = CameraRate, rate2 = CameraRate)
            camLookAt(CameraAt, height = CameraLookHeight, rate = CameraRate, rate2 = CameraRate)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()

            mesbox(
                "The gnomes begin to chant. Meanwhile, King Bolren holds the orbs of protection " +
                    "out in front of him."
            )
            bolren.anim(HoldOrbsSeq)
            bolren.spotanim(OrbsSpotanim, delay = 0, height = 0, slot = 0)
            val (north, south) = chanters.partition { it.coords.z > TreeCentre.z }
            repeat(ChantRounds) {
                north.forEach { it.say("Su tana.") }
                delay(ChantTicks)
                south.forEach { it.say("En tania.") }
                delay(ChantTicks)
            }
            soundSynth(RestoreSound)
            spotanimMap(worldRepo, OrbsSpotanim, TreeCentre, height = TreeSpotanimHeight)
            player.bolrenGotOrbs = OrbsInTree
            delay(2)
            mesbox(
                "The orbs of protection come to rest gently in the branches of the ancient " +
                    "spirit tree.",
            )
        } finally {
            camReset()
            showEntityOps()
            minimapReset()
            for (npc in cast) {
                npc.clearFacingLock()
                homes[npc]?.let { npc.teleport(collision, it) }
                npc.mode = npc.type.defaultMode
            }
            bolren.facePlayer(player)
        }
    }

    private suspend fun Dialogue.afterQuest() {
        if (!player.inv.contains(GnomeAmulet) && !player.worn.contains(GnomeAmulet)) {
            chatNpc(neutral, "Here, I think this belongs to you.")
            access.invAddOrDrop(objRepo, GnomeAmulet)
            objbox(GnomeAmulet, "King Bolren gives you an amulet.")
        }
        chatPlayer(happy, "Hello again, Bolren.")
        chatNpc(happy, "Hello there. It's good to see you again.")
        chatPlayer(quiz, "How are things here?")
        chatNpc(
            neutral,
            "Those Khazard troops continue to be a menace, but things are much better now. All " +
                "thanks to you, of course.",
        )
    }

    private companion object {
        const val Bolren = "npc.king_bolren"
        const val Chanter = "npc.chantergnome"

        /** The spirit tree (`loc.ent`) sits at 2543..2546 x 3168..3171; this is its middle. */
        val TreeCentre = CoordGrid(2544, 3169, 0)
        const val ChanterRadius = 6

        val PlayerMark = CoordGrid(2541, 3169, 0)
        val BolrenMark = CoordGrid(2542, 3170, 0)
        val CameraFrom = CoordGrid(2536, 3170, 0)
        val CameraAt = CoordGrid(2544, 3169, 0)
        const val CameraHeight = 700
        const val CameraLookHeight = 250
        const val CameraRate = 100

        const val HoldOrbsSeq = "seq.gnome_cast_globes"
        const val OrbsSpotanim = "spotanim.gnome_globes"
        const val TreeSpotanimHeight = 300
        const val RestoreSound = "synth.spirit_transform"

        const val OrbsHandedOver = 1
        const val OrbsInTree = 2
        const val ChantRounds = 2
        const val ChantTicks = 3
    }
}
