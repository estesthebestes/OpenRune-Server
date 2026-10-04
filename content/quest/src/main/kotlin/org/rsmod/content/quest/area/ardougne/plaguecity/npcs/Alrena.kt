package org.rsmod.content.quest.area.ardougne.plaguecity.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.DWELLBERRIES
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.PICTURE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_DUG_TUNNEL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_FREED_ELENA
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_REMOVED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_HAS_GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_ROPE_TIED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SOIL_SOFTENED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.plaguecity.bucketsPoured
import org.rsmod.content.quest.area.ardougne.plaguecity.pictureAsked
import org.rsmod.content.quest.area.ardougne.wearingGasMask
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Alrena, Edmond's wife, who makes the dwellberry gas mask, and the cupboard in their house where
 * she hides the spare one.
 */
class Alrena
@Inject
constructor(
    private val plagueCity: PlagueCityQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val mixingType =
        ServerCacheManager.getNpc(ALRENA_MIXING.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $ALRENA_MIXING")
    private val maskType =
        ServerCacheManager.getNpc(ALRENA_MASK.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $ALRENA_MASK")

    override fun ScriptContext.startup() {
        onOpNpc1(ALRENA) { startDialogue(it.npc) { alrena(it.npc) } }
        onOpLoc1(CUPBOARD_SHUT) { openCupboard(it.loc) }
        onOpLoc1(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc2(CUPBOARD_OPEN) { closeCupboard(it.loc) }
    }

    private suspend fun Dialogue.alrena(npc: Npc) {
        when (plagueCity.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello Madam.")
                chatNpc(neutral, "Oh, hello there.")
                chatPlayer(quiz, "Are you ok?")
                chatNpc(worried, "Not too bad... I've just got some troubles on my mind...")
            }
            STAGE_STARTED -> makeMask(npc)
            STAGE_HAS_GAS_MASK -> {
                if (player.bucketsPoured == 0) {
                    chatNpc(
                        happy,
                        "Hello darling, I think Edmond had a good idea of how to get into West " +
                            "Ardougne, you should hear his idea.",
                    )
                    chatPlayer(happy, "Alright, I'll go and see him now.")
                    return
                }
                chatPlayer(happy, "Hello Alrena.")
                chatNpc(quiz, "Hello darling, how's that tunnel coming along?")
                chatPlayer(
                    neutral,
                    "I just need to soften the soil a little more and then we'll start digging.",
                )
            }
            STAGE_SOIL_SOFTENED -> {
                chatPlayer(happy, "Hello again Alrena.")
                chatNpc(quiz, "How's the tunnel going?")
                chatPlayer(neutral, "I'm getting there.")
                chatNpc(
                    worried,
                    "One of the mourners has been sniffing around asking questions about you " +
                        "and Edmond, you should keep an eye out for him.",
                )
                spareMaskReminder(
                    "Also, don't forget about that spare gas mask if you need it. It's hidden " +
                        "in the cupboard.",
                    "Okay, thanks for the warning.",
                )
            }
            in STAGE_DUG_TUNNEL..STAGE_ROPE_TIED -> {
                chatPlayer(happy, "Hello Alrena.")
                chatNpc(quiz, "Hi, have you managed to get through to West Ardougne?")
                chatPlayer(neutral, "Not yet, but I should be going soon.")
                chatNpc(
                    worried,
                    "Make sure you wear your mask while you're over there! I can't think of a " +
                        "worse way to die.",
                )
                spareMaskReminder(
                    "Don't forget, I've got a spare one hidden in the cupboard if you need it.",
                    "Okay, thanks for the warning.",
                )
            }
            in STAGE_GRILL_REMOVED until STAGE_FREED_ELENA -> {
                chatPlayer(happy, "Hello Alrena.")
                chatNpc(quiz, "Hello, any word on Elena?")
                chatPlayer(sad, "Not yet I'm afraid.")
                chatNpc(quiz, "Is there anything else I can do to help?")
                if (player.pictureAsked && !player.inv.contains(PICTURE)) {
                    chatPlayer(quiz, "Do you have a picture of Elena?")
                    chatNpc(
                        neutral,
                        "Yes. There should be one in the house somewhere. Let me know if you " +
                            "need anything else.",
                    )
                    return
                }
                chatPlayer(neutral, "It's alright, I'll get her back soon.")
                if (hasMask()) {
                    chatNpc(happy, "That's the spirit, dear.")
                } else {
                    chatNpc(
                        happy,
                        "That's the spirit, dear. Don't forget that there's a spare gas mask " +
                            "in the cupboard if you need one.",
                    )
                }
            }
            else ->
                chatNpc(
                    happy,
                    "Thank you for rescuing my daughter! Elena has told me of your bravery in " +
                        "entering a house that could have been plague infected. I can't thank " +
                        "you enough!",
                )
        }
    }

    private suspend fun Dialogue.makeMask(npc: Npc) {
        chatPlayer(happy, "Hello, Edmond has asked me to help find your daughter.")
        chatNpc(
            neutral,
            "Yes he told me. I've begun making your special gas mask, but I need some " +
                "dwellberries to finish it.",
        )
        if (!player.inv.contains(DWELLBERRIES)) {
            chatPlayer(neutral, "I'll try to get some.")
            chatNpc(
                neutral,
                "The best place to look is in McGrubor's Wood, just west of Seers' Village.",
            )
            return
        }
        chatPlayer(happy, "Yes I've got some here.")
        objbox(DWELLBERRIES, "You give the dwellberries to Alrena.")
        if (access.invDel(player.inv, DWELLBERRIES).failure) {
            return
        }
        access.invAddOrDrop(objRepo, GAS_MASK)
        plagueCity.advanceTo(access, STAGE_HAS_GAS_MASK)
        access.npcChangeType(npc, mixingType, MIXING_TICKS)
        access.soundSynth(MIX_SOUND)
        mesbox(
            "Alrena crushes the berries into a smooth paste. She then smears the paste over a " +
                "strange mask."
        )
        access.npcChangeType(npc, maskType, MASK_TICKS)
        chatNpc(
            happy,
            "There we go, all done. While in West Ardougne you must wear this at all times, " +
                "or you could catch the plague.",
        )
        objbox(GAS_MASK, "Alrena has given you a gas mask.")
        npc.resetTransmog()
        chatNpc(
            neutral,
            "I'll make a spare mask for you in case you lose that one. I'll hide it in the " +
                "cupboard in case the mourners come looking.",
        )
    }

    private suspend fun Dialogue.spareMaskReminder(reminder: String, thanks: String) {
        if (hasMask()) {
            chatPlayer(happy, thanks)
        } else {
            chatNpc(neutral, reminder)
            chatPlayer(happy, "Great, thanks Alrena!")
        }
    }

    private fun Dialogue.hasMask(): Boolean =
        player.inv.contains(GAS_MASK) || player.wearingGasMask()

    private suspend fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_SEQ)
        soundSynth(CUPBOARD_OPEN_SOUND)
        locRepo.change(cupboard, CUPBOARD_OPEN, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.closeCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(CLOSE_SEQ)
        soundSynth(CUPBOARD_CLOSE_SOUND)
        locRepo.change(cupboard, CUPBOARD_SHUT, CUPBOARD_TICKS)
    }

    /** The spare gas mask Alrena hides for the player once she has made the first one. */
    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        val hasMask = player.inv.contains(GAS_MASK) || player.wearingGasMask()
        if (plagueCity.stage(player) < STAGE_HAS_GAS_MASK || hasMask) {
            mes("You search the cupboard but you find nothing.")
            return
        }
        if (inv.isFull()) {
            objbox(
                GAS_MASK,
                "You find a protective mask but you don't have enough room to take it.",
            )
            return
        }
        invAdd(inv, GAS_MASK)
        objbox(GAS_MASK, "You find a protective mask.")
    }

    companion object {
        const val ALRENA = "npc.alrena"
        const val ALRENA_MIXING = "npc.alrena_gasmask_mix"
        const val ALRENA_MASK = "npc.alrena_gasmask_mask"

        const val CUPBOARD_SHUT = "loc.alrenascupboardshut"
        const val CUPBOARD_OPEN = "loc.alrenascupboardopen"

        const val OPEN_SEQ = "seq.human_opencupboard"
        const val CLOSE_SEQ = "seq.human_closecupboard"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
        const val MIX_SOUND = "synth.vial_mix"

        const val MIXING_TICKS = 6
        const val MASK_TICKS = 6
        const val CUPBOARD_TICKS = 100
    }
}
