package org.rsmod.content.quest.area.ardougne.biohazard

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.DISTILLATOR
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.MOURNER_KEY
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.ROTTEN_APPLE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_CROSSED_WALL
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_DISTILLATOR
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_HQ_REFUSED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_MOURNER_KILLED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_STEW_POISONED
import org.rsmod.content.quest.area.ardougne.wearingMedicalGown
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Mourner Headquarters in the north-east corner of West Ardougne. A guard turns citizens
 * away at the front door; the back yard, reached through a gap in the fence, has the stew pot.
 * Poisoning the stew gets a "doctor" let in, and the sickest mourner upstairs is the one with
 * the key to the caged storeroom where Elena's distillator is kept.
 */
class MournerHeadquarters
@Inject
constructor(
    private val biohazard: BiohazardQuest,
    private val doors: QuestDoors,
    private val objRepo: ObjRepository,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val sickMournerType =
        ServerCacheManager.getNpc(SICK_MOURNER.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $SICK_MOURNER")

    override fun ScriptContext.startup() {
        onOpNpc1(DOOR_GUARD) { startDialogue(it.npc) { doorGuard() } }
        onOpLoc1(DOOR) { groundDoor(it.loc) }
        onOpLoc1(FENCE) { squeezeThroughFence(it.loc) }
        for (cauldron in CAULDRONS) {
            onOpLoc1(cauldron) { inspectCauldron() }
            onOpLocU(cauldron, ROTTEN_APPLE) { poisonStew() }
        }
        onOpLoc1(TRAPDOOR) { mes("The trapdoor is bolted on the other side.") }
        for (mourner in SICK_MOURNERS) {
            onOpNpc1(mourner) { startDialogue(it.npc) { insideMourner() } }
        }
        onOpLoc1(UPSTAIRS_DOOR) { upstairsDoor(it.loc) }
        onOpNpc1(SICK_MOURNER) { startDialogue(it.npc) { sickestMourner(it.npc) } }
        onNpcQueue(sickMournerType, "queue.death") { sickestMournerDied() }
        onOpLoc1(GATE_LEFT) { openGate(it.loc, left = true) }
        onOpLoc1(GATE_RIGHT) { openGate(it.loc, left = false) }
        onOpLoc1(CRATE) { searchCrate() }
    }

    private suspend fun Dialogue.doorGuard() {
        val stage = biohazard.stage(player)
        when {
            stage in STAGE_STEW_POISONED..STAGE_GOT_DISTILLATOR && player.wearingMedicalGown() -> {
                guardSays(quiz, "A doctor? I didn't think there were any left around here.")
                chatPlayer(neutral, "I heard there was some trouble with food poisoning here.")
                guardSays(neutral, "You heard right. Head on in and see what you can do.")
            }
            stage in STAGE_STEW_POISONED..STAGE_GOT_DISTILLATOR -> {
                guardSays(
                    worried,
                    "Back away citizen. Several mourners are ill with food poisoning.",
                )
                chatPlayer(quiz, "Is there a doctor nearby? Maybe I can go fetch them.")
                guardSays(
                    neutral,
                    "There are no doctors in West Ardougne, just that nurse south west of the " +
                        "chapel. You don't need to worry though, we'll soon have it sorted.",
                )
            }
            stage in STAGE_CROSSED_WALL..STAGE_HQ_REFUSED -> {
                guardSays(
                    angry,
                    "Back away citizen. Only mourners are allowed inside the headquarters.",
                )
                chatPlayer(quiz, "Hmm. I wonder if there's another way in.")
                biohazard.advanceTo(access, STAGE_HQ_REFUSED)
            }
            else ->
                guardSays(
                    angry,
                    "Back away citizen. Only mourners are allowed inside the headquarters.",
                )
        }
    }

    private suspend fun Dialogue.guardSays(mesanim: MesAnimType, text: String) {
        if (npc != null) {
            chatNpc(mesanim, text)
        } else {
            chatNpcSpecific("Mourner", DOOR_GUARD_HEAD, mesanim, text)
        }
    }

    private suspend fun ProtectedAccess.groundDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        if (door.coords == BACK_DOOR) {
            val outside = player.coords.z >= door.coords.z
            if (outside && biohazard.stage(player) in STAGE_CROSSED_WALL..STAGE_HQ_REFUSED) {
                startDialogue {
                    chatPlayer(
                        confused,
                        "Eugh, locked. There must be a way for me to get in. Maybe there's " +
                            "something around here that will help me.",
                    )
                }
            } else {
                mesbox("The door is locked.")
            }
            return
        }
        val inside = player.coords.z > door.coords.z
        val stage = biohazard.stage(player)
        val doctor =
            stage in STAGE_STEW_POISONED..STAGE_GOT_DISTILLATOR && player.wearingMedicalGown()
        if (inside || doctor) {
            doors.open(this, door, DOOR_OPEN)
            return
        }
        startDialogue { doorGuard() }
    }

    private suspend fun ProtectedAccess.squeezeThroughFence(fence: BoundLocInfo) {
        arriveDelay()
        faceLoc(fence)
        val dest =
            if (player.coords.x <= fence.coords.x) fence.coords.translateX(1) else fence.coords
        anim(SQUEEZE_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(1)
        teleport(dest)
    }

    private suspend fun ProtectedAccess.inspectCauldron() {
        arriveDelay()
        faceSquare(CAULDRON_TILE)
        startDialogue {
            chatPlayer(
                neutral,
                "Looks like a nice stew is being prepared. This must be to feed all of the " +
                    "mourners.",
            )
        }
    }

    private suspend fun ProtectedAccess.poisonStew() {
        arriveDelay()
        faceSquare(CAULDRON_TILE)
        val stage = biohazard.stage(player)
        when {
            stage >= STAGE_STEW_POISONED -> mes("The stew has already been spoiled.")
            stage < STAGE_CROSSED_WALL -> mes("I have no reason to do that.")
            else -> {
                if (invDel(inv, ROTTEN_APPLE).failure) {
                    return
                }
                biohazard.advanceTo(this, STAGE_STEW_POISONED)
                anim(DROP_SEQ)
                soundSynth(STEW_SOUND)
                mesbox("You place the rotten apple in the pot. It quickly dissolves into the stew.")
                startDialogue {
                    chatPlayer(happy, "Anyone who eats this stew is going to have a bad time now.")
                }
            }
        }
    }

    private suspend fun Dialogue.insideMourner() {
        val stage = biohazard.stage(player)
        if (stage !in STAGE_STEW_POISONED until STAGE_GOT_SAMPLES) {
            chatNpc(angry, "Stand back citizen, do not approach me.")
            return
        }
        chatPlayer(happy, "Hello there.")
        chatNpc(sad, "Oh dear oh dear. I feel terrible, I think it was the stew.")
        chatPlayer(neutral, "You should be more careful with your ingredients.")
        if (stage < STAGE_GOT_DISTILLATOR) {
            chatNpc(
                sad,
                "There is one mourner who's really sick. They're resting upstairs. You should " +
                    "see to them first.",
            )
            chatPlayer(neutral, "Okay, I'll see what I can do.")
        }
    }

    private suspend fun ProtectedAccess.upstairsDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        doors.open(this, door, UPSTAIRS_DOOR_OPEN)
    }

    private suspend fun Dialogue.sickestMourner(npc: Npc) {
        val stage = biohazard.stage(player)
        val needsKey = stage in STAGE_STEW_POISONED until STAGE_GOT_DISTILLATOR
        if (!needsKey || player.inv.contains(MOURNER_KEY)) {
            chatPlayer(happy, "Hello there.")
            chatNpc(sad, "Sorry, I'd like to be left in peace.")
            return
        }
        chatPlayer(happy, "Hello there.")
        chatNpc(
            sad,
            "A doctor? At last! I don't know what I've eaten but I feel like I'm on death's door.",
        )
        chatPlayer(quiz, "Hmm... interesting, sounds like food poisoning.")
        chatNpc(sad, "Yes, I'd figured that out already. What can you give me to help.")
        when (
            choice3(
                "Just hold your breath and count to ten.",
                1,
                "The best I can do is pray for you.",
                2,
                "There's nothing I can do, it's fatal.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Just hold your breath and count to ten.")
                chatNpc(confused, "What? How will that help? What kind of doctor are you?")
                chatPlayer(worried, "Erm... I'm new, I just started.")
                chatNpc(angry, "You're no doctor!")
            }
            2 -> {
                chatPlayer(neutral, "The best I can do is pray for you.")
                chatNpc(angry, "Pray for me? You're no doctor... You're an imposter!")
            }
            3 -> {
                chatPlayer(neutral, "There's nothing I can do, it's fatal.")
                chatNpc(shocked, "No, I'm too young to die! I've never even had a girlfriend.")
                chatPlayer(neutral, "That's life for you.")
                chatNpc(quiz, "Wait a minute, where's your equipment?")
                chatPlayer(worried, "It's erm... at home.")
                chatNpc(angry, "You're no doctor!")
            }
        }
        npc.facePlayer(player)
        npc.opPlayer2(player, aiInteractions)
    }

    private suspend fun StandardNpcAccess.sickestMournerDied() {
        val hero = findHero(playerList)
        val dropAt = npc.coords
        death.deathNoDrops(this)
        if (hero == null) {
            return
        }
        launcher.launch(hero) { claimMournerKey(dropAt) }
    }

    internal suspend fun ProtectedAccess.claimMournerKey(dropAt: CoordGrid) {
        val stage = biohazard.stage(player)
        val needsKey = stage in STAGE_STEW_POISONED until STAGE_GOT_DISTILLATOR
        if (!needsKey || inv.contains(MOURNER_KEY)) {
            return
        }
        invAddOrDrop(objRepo, MOURNER_KEY, coords = dropAt)
        biohazard.advanceTo(this, STAGE_MOURNER_KILLED)
        objbox(MOURNER_KEY, "The mourner drops a key as he dies.")
    }

    private suspend fun ProtectedAccess.openGate(gate: BoundLocInfo, left: Boolean) {
        arriveDelay()
        faceLoc(gate)
        val inside = player.coords.x > gate.coords.x
        if (!inside) {
            if (!inv.contains(MOURNER_KEY)) {
                mesbox("The gate is locked.")
                return
            }
            mesbox("You use the key to open the gate.")
        }
        val leftGate = if (left) doors.asInfo(gate) else doors.leftOfGate(gate, GATE_LEFT)
        val rightGate = if (left) doors.rightOfGate(gate, GATE_RIGHT) else doors.asInfo(gate)
        doors.openGate(this, leftGate, GATE_LEFT_OPEN, rightGate, GATE_RIGHT_OPEN)
    }

    private suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()
        anim(SEARCH_SEQ)
        val stage = biohazard.stage(player)
        if (stage !in STAGE_MOURNER_KILLED..STAGE_GOT_DISTILLATOR || inv.contains(DISTILLATOR)) {
            mes("You search the crate but find nothing of interest.")
            return
        }
        if (inv.isFull()) {
            objbox(
                DISTILLATOR,
                "You search the crate and find Elena's distillator but you don't have enough " +
                    "room to take it.",
            )
            return
        }
        if (invAdd(inv, DISTILLATOR).failure) {
            return
        }
        biohazard.advanceTo(this, STAGE_GOT_DISTILLATOR)
        objbox(DISTILLATOR, "You search the crate and find Elena's distillator.")
    }

    private companion object {
        const val DOOR_GUARD = "npc.mourner_armed_guard"
        const val DOOR_GUARD_HEAD = "npc.mourner_armed_guard_vis"
        const val DOOR = "loc.mournerstewdoor"
        const val DOOR_OPEN = "loc.mournerstewdooropen"
        val BACK_DOOR = CoordGrid(2551, 3328, 0)
        const val FENCE = "loc.mournerstewfence"
        val CAULDRONS =
            listOf("loc.mournercauldron", "loc.mournercauldron_op", "loc.mournercauldron_noop")
        val CAULDRON_TILE = CoordGrid(2543, 3332, 0)
        const val TRAPDOOR = "loc.mourning_hideout_trap_door"
        val SICK_MOURNERS = listOf("npc.mournerstew1", "npc.mournerstew3")
        const val UPSTAIRS_DOOR = "loc.mournerstewdoorup"
        const val UPSTAIRS_DOOR_OPEN = "loc.mournerstewdoorupopen"

        /** The level 13 mourner upstairs; a multi-npc whose visible form has the Attack option. */
        const val SICK_MOURNER = "npc.mournerstew2"
        const val GATE_LEFT = "loc.mournerquaters_gatel"
        const val GATE_RIGHT = "loc.mournerquaters_gater"
        const val GATE_LEFT_OPEN = "loc.mournerquaters_gatelopen"
        const val GATE_RIGHT_OPEN = "loc.mournerquaters_gateropen"
        const val CRATE = "loc.mournercrateup"

        const val SQUEEZE_SEQ = "seq.human_walk_fence_north"
        const val DROP_SEQ = "seq.human_pickuptable"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val STEW_SOUND = "synth.bubbling_soup"
    }
}
