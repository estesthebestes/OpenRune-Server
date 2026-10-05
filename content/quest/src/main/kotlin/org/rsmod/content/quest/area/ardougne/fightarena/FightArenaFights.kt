package org.rsmod.content.quest.area.ardougne.fightarena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Opponent
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The arena opponents fall like any other npc. A kill inside a player's private arena, at the
 * stage that opponent belongs to, moves the quest on; the same npc types standing in the open
 * world die as usual.
 */
class FightArenaFights
@Inject
constructor(
    private val quest: FightArenaQuest,
    private val scenes: FightArenaScenes,
    private val site: ArenaSite,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for ((name, opponent) in Opponents) {
            val type =
                ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")
            onNpcQueue(type, "queue.death") { slain(opponent) }
        }
    }

    private suspend fun StandardNpcAccess.slain(opponent: Opponent) {
        val hero = findHero(playerList)
        val inArena = hero != null && site.owns(npc)
        if (opponent == Opponent.General && inArena) {
            death.deathNoDrops(this)
        } else {
            death.deathWithDrops(this)
        }
        if (hero == null || !inArena) {
            return
        }
        if (quest.nextOpponent(quest.stage(hero)) != opponent) {
            return
        }
        launcher.launch(hero) { with(scenes) { opponentDefeated(opponent) } }
    }

    private companion object {
        val Opponents =
            listOf(
                FightArenaScenes.OgreType to Opponent.Ogre,
                FightArenaScenes.ScorpionType to Opponent.Scorpion,
                FightArenaScenes.BouncerType to Opponent.Bouncer,
                FightArenaScenes.GeneralType to Opponent.General,
            )
    }
}
