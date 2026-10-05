package org.rsmod.content.quest.area.ardougne.sheepherder

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onNpcTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.CATTLEPROD
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.NO_PROTECTION
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Prodding the grazing sheep towards Farmer Brumty's enclosure, and the sheep wandering home again.
 *
 * The field sheep are shared by everyone; whether one has been penned is the prodding player's own
 * colour varbit. A prod stops the sheep grazing and starts [RESTLESS_TIMER]: when it runs out the
 * sheep says a quiet "Baa" and walks back to its spawn by a real route. If it still isn't home
 * after [STUCK_TIMER] it is put back there, so no sheep stays wedged anywhere. Any new prod cancels
 * the whole sequence.
 */
class SheepHerding
@Inject
constructor(
    private val sheep: SheepHerderQuest,
    private val collision: CollisionFlagMap,
    private val routeFactory: RouteFactory,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    private val steps = StepValidator(collision)

    override fun ScriptContext.startup() {
        for (colour in SheepColour.entries) {
            onOpNpc1(colour.fieldNpc) { prod(it.npc, colour) }
            onOpNpc1(colour.enclosureNpc) { prod(it.npc, colour) }
        }
        onNpcTimer(RESTLESS_TIMER) { restless(npc) }
        onNpcTimer(STUCK_TIMER) { unstick(npc) }
    }

    suspend fun ProtectedAccess.prod(npc: Npc, colour: SheepColour) {
        if (npc.isType(colour.enclosureNpc)) {
            mes("The sheep is already in the enclosure. You don't need to prod it.")
            return
        }
        val problem = prodProblem(player, colour)
        if (problem != null) {
            mes(problem)
            return
        }
        val dir = HerdingRules.direction(coords, npc.coords) ?: return
        anim(PROD_SEQ)
        holdForHerding(npc)
        val path = HerdingRules.push(steps, npc.coords, dir, BLOCKERS)
        if (path.isEmpty()) {
            npc.say("Baa!")
            mes("The ${colour.label} sheep can't go any further ${dir.label}: something's in the way.")
            return
        }
        bleat(npc)
        val dest = path.last()
        npc.walk(dest)
        if (!HerdingRules.isThreshold(dest)) {
            return
        }
        delay(path.size)
        pen(player, npc, colour)
    }

    /** Why [player] can't herd a [colour] sheep right now, or null when they can. */
    fun prodProblem(player: Player, colour: SheepColour): String? {
        val stage = sheep.stage(player)
        return when {
            !sheep.isProtected(player) -> NO_PROTECTION
            stage == 0 -> "You have no reason to go poking somebody else's sheep."
            stage > STAGE_STARTED -> "You've done your part. Leave the rest of the flock in peace."
            sheep.state(player, colour) != SheepState.LOOSE ->
                "There's already a sheep like this in the pen. You don't need to fetch another " +
                    "one now."
            CATTLEPROD in player.worn -> null
            CATTLEPROD in player.inv -> "You need to wield the cattleprod to herd sheep."
            else ->
                "You need a cattleprod to herd the sheep. There is one lying by the incinerator " +
                    "inside the enclosure."
        }
    }

    fun pen(player: Player, npc: Npc, colour: SheepColour) {
        sendHome(npc)
        if (sheep.state(player, colour) != SheepState.LOOSE) {
            return
        }
        sheep.setState(player, colour, SheepState.PENNED)
        player.mes("The sheep obligingly jumps over the gate and into the enclosure!")
    }

    private fun holdForHerding(npc: Npc) {
        npc.clearTimer(STUCK_TIMER)
        npc.moveRestrict = npc.type.moveRestrict
        npc.noneMode()
        npc.timer(RESTLESS_TIMER, RESTLESS_TICKS)
    }

    fun restless(npc: Npc) {
        npc.clearTimer(RESTLESS_TIMER)
        if (npc.coords == npc.spawnCoords) {
            npc.defaultMode()
            return
        }
        npc.say("Baa")
        npc.timer(STUCK_TIMER, STUCK_TICKS)
        npc.walkTo(routeFactory, npc.spawnCoords) {
            if (npc.coords.chebyshevDistance(npc.spawnCoords) > HOME_SLACK) {
                unstick(npc)
                return@walkTo
            }
            npc.clearTimer(STUCK_TIMER)
            npc.defaultMode()
        }
    }

    fun unstick(npc: Npc) {
        npc.clearTimer(STUCK_TIMER)
        if (npc.coords != npc.spawnCoords) {
            worldRepo.spotanimMap(SpotanimType(PUFF.asRSCM(RSCMType.SPOTANIM)), npc.coords)
            sendHome(npc)
            return
        }
        npc.defaultMode()
    }

    private fun sendHome(npc: Npc) {
        npc.clearTimer(RESTLESS_TIMER)
        npc.clearTimer(STUCK_TIMER)
        npc.moveRestrict = npc.type.moveRestrict
        npc.telejump(collision, npc.spawnCoords)
        npc.defaultMode()
    }

    private fun bleat(npc: Npc) {
        npc.say("BAAAAA!")
        worldRepo.soundArea(npc, BLEAT_SOUND)
    }

    companion object {
        const val PROD_SEQ = "seq.cattleprod"
        const val BLEAT_SOUND = "synth.sheep_atmospheric1"
        const val PUFF = "spotanim.smokepuff"

        const val RESTLESS_TIMER = "timer.sheepherder_restless"
        const val STUCK_TIMER = "timer.sheepherder_stuck"

        const val RESTLESS_TICKS = 40
        const val STUCK_TICKS = 60

        const val HOME_SLACK = 1

        const val BLOCKERS = CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS
    }
}
