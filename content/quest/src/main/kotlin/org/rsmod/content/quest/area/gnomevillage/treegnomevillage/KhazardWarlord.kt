package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.HasOrb
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.OrbReturned
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.Orbs
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.WarlordSlain
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Khazard warlord who carries the last two orbs, north of the stronghold behind West Ardougne.
 * The map spawns the varbit-multi base type, which only ever resolves to its Talk-to form, so
 * talking to him transmogs him into the Attack form for the fight. He fights whoever spoke to him,
 * gives up after a while if no blow lands, and returns after each defeat: "warriors blessed by
 * Khazard don't die".
 */
class KhazardWarlord
@Inject
constructor(
    private val quest: TreeGnomeVillageQuest,
    private val playerList: PlayerList,
    private val aiInteractions: AiPlayerInteractions,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    private val combatType: NpcServerType by lazy {
        ServerCacheManager.getNpc(WarlordCombat.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $WarlordCombat")
    }

    override fun ScriptContext.startup() {
        onOpNpc1(Warlord) { startDialogue(it.npc) { talk(it.npc) } }
    }

    private suspend fun Dialogue.talk(npc: Npc) {
        when (quest.stage(player)) {
            in 0 until HasOrb -> {
                chatPlayer(happy, "Hello, how are you?")
                chatNpc(
                    angry,
                    "Don't speak to me you insignificant wretch! Die in the name of " +
                        "Khazard!",
                )
            }
            HasOrb -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(angry, "You think you're so clever. You know nothing!")
                chatPlayer(confused, "What?")
                chatNpc(angry, "I'll crush you and those pesky little green men!")
            }
            OrbReturned -> {
                chatPlayer(angry, "You there, stop!")
                chatNpc(angry, "Go back to your pesky little green friends.")
                chatPlayer(angry, "I've come for the orbs.")
                chatNpc(
                    neutral,
                    "You're out of your depth traveller. These orbs are part of a much larger " +
                        "picture.",
                )
                chatPlayer(angry, "They're stolen goods, now give them here!")
                chatNpc(laugh, "Ha, you really think you stand a chance? I'll crush you.")
            }
            else -> {
                chatPlayer(confused, "I thought I killed you?")
                chatNpc(
                    angry,
                    "Fool, warriors blessed by Khazard don't die. You can't kill that which is " +
                        "already dead. However I can kill you!",
                )
            }
        }
        access.provoke(npc)
    }

    @OptIn(InternalApi::class)
    private fun ProtectedAccess.provoke(npc: Npc) {
        if (npc.transmog == null) {
            npcChangeType(npc, combatType, Int.MAX_VALUE)
        }
        npc.opPlayer2(player, aiInteractions)
        val uid = player.uid
        worldQueues.add(PatienceTicks) { loseInterest(uid, npc) }
    }

    /** "Bah, enough of you!": an unfought warlord returns to his post and Talk-to form. */
    @OptIn(InternalApi::class)
    private fun loseInterest(uid: PlayerUid, npc: Npc) {
        val player = uid.resolve(playerList) ?: return
        if (npc.transmog == null || !npc.isSlotAssigned) {
            return
        }
        if (player.isInCombat() || npc.isInCombat()) {
            worldQueues.add(PatienceTicks) { loseInterest(uid, npc) }
            return
        }
        npc.say("Bah, enough of you!")
        npc.resetMode()
        npc.resetTransmog()
        npc.assignUid()
    }

    private companion object {
        const val Warlord = "npc.khazard_warlord"
        const val WarlordCombat = "npc.khazard_warlord_combat"

        /** Roughly thirty seconds without a blow before he gives up on the player. */
        const val PatienceTicks = 50
    }
}

/** Leaves the orbs behind for his killer and finishes the stage once he falls. */
class KhazardWarlordDeath
@Inject
constructor(
    private val quest: TreeGnomeVillageQuest,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val death: NpcDeath,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(Warlord, WarlordChat, WarlordCombat)) {
            val type =
                ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")
            onNpcQueue(type, "queue.death") { slain() }
        }
    }

    private suspend fun StandardNpcAccess.slain() {
        val hero = findHero(playerList)
        val dropCoords = npc.coords
        death.deathWithDrops(this, dropCoords)
        if (hero == null) {
            return
        }
        val dropsOrbs = warlordDropsOrbs(quest.stage(hero), hero)
        if (dropsOrbs) {
            objRepo.add(Orbs, dropCoords, OrbsLingerTicks, receiver = hero)
        }
        hero.soundSynth(ScreamSound)
        launcher.launch(hero) { warlordFalls(quest, dropsOrbs) }
    }

    private companion object {
        const val Warlord = "npc.khazard_warlord"
        const val WarlordChat = "npc.khazard_warlord_chat"
        const val WarlordCombat = "npc.khazard_warlord_combat"
        const val ScreamSound = "synth.scream"
        const val OrbsLingerTicks = 300
    }
}

/** The orbs are only left for a player Bolren has sent after them, and not one who holds them. */
internal fun warlordDropsOrbs(stage: Int, hero: Player): Boolean =
    stage in OrbReturned..WarlordSlain && !hero.inv.contains(Orbs)

internal suspend fun ProtectedAccess.warlordFalls(
    quest: TreeGnomeVillageQuest,
    dropsOrbs: Boolean,
) {
    if (!dropsOrbs) {
        mes(
            "As the warlord falls to the ground, a ghostly vapour floats upwards from his " +
                "battle-worn armour."
        )
        return
    }
    quest.advanceTo(this, WarlordSlain)
    objbox(
        Orbs,
        "As the warlord falls to the ground, a ghostly vapour floats upwards from his " +
            "battle-worn armour. Out of sight you hear a shrill scream in the still air. You " +
            "spot the orbs of protection among his remains.",
    )
}
