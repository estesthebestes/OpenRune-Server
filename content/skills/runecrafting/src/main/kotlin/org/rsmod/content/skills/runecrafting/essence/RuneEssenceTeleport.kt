package org.rsmod.content.skills.runecrafting.essence

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The npcs who know the incantation. [portal] is the `varbit.essencemine_portal` value the mine's
 * exit portals transform on, and also tells the portal where to send the player back to; every
 * value used here shows a portal (values 2, 5 and 6 hide it).
 */
enum class EssenceMineTeleporter(val portal: Int, val returnCoord: CoordGrid) {
    Sedridor(portal = 0, returnCoord = CoordGrid(3105, 9572)),
    Aubury(portal = 1, returnCoord = CoordGrid(3253, 3401)),
    Cromperty(portal = 3, returnCoord = CoordGrid(2685, 3324)),
    Brimstail(portal = 4, returnCoord = CoordGrid(2409, 9816)),
    Distentor(portal = 7, returnCoord = CoordGrid(2593, 3088)),
    ;

    companion object {
        fun fromPortal(value: Int): EssenceMineTeleporter =
            entries.firstOrNull { it.portal == value } ?: Sedridor
    }
}

/** Published once a teleporter has put the player in the mine; never for a blocked cast. */
data class EssenceMineArrival(val access: ProtectedAccess, val teleporter: EssenceMineTeleporter) :
    UnboundEvent

class EssencePortals : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.blankrunestone_exit_portal") { teleportToMainLand() }
    }

    companion object {

        const val PORTAL_VARBIT = "varbit.essencemine_portal"

        private val RUNE_ESSENCE_MINE = CoordGrid(2910, 4832)
        private const val LANDING_RADIUS = 20

        fun randomEssenceMineDest(access: ProtectedAccess): CoordGrid =
            access.mapFindSquareNone(RUNE_ESSENCE_MINE, 0, LANDING_RADIUS) ?: RUNE_ESSENCE_MINE

        fun randomizeLocation(base: CoordGrid): CoordGrid {
            if ((0..1).random() == 0) {
                return base
            }
            return CoordGrid(
                x = base.x + (-1..1).random(),
                z = base.z + (-1..1).random(),
            )
        }

        fun returnDest(teleporter: EssenceMineTeleporter): CoordGrid =
            randomizeLocation(teleporter.returnCoord)
    }
}

suspend fun ProtectedAccess.teleportToRuneEssenceMine(
    npc: Npc,
    teleporter: EssenceMineTeleporter,
) {
    startDialogue(npc) { teleportToRuneEssenceMine(teleporter) }
}

suspend fun ProtectedAccess.teleportToMainLand() {
    startDialogue { teleportToMainLand() }
}

suspend fun Dialogue.teleportToRuneEssenceMine(teleporter: EssenceMineTeleporter) {
    val npc = checkNotNull(npc) { "Rune essence mine teleport requires an npc dialogue context." }
    val dest = EssencePortals.randomEssenceMineDest(access)

    access.castTeleport(npc, "Seventior Disthine Molenko!")
    access.telejump(dest)
    if (player.coords != dest) {
        return
    }
    access.vars[EssencePortals.PORTAL_VARBIT] = teleporter.portal
    access.publish(EssenceMineArrival(access, teleporter))
}

/** The incantation and curse casting every npc teleporter performs, up to the player's move. */
suspend fun ProtectedAccess.castTeleport(npc: Npc, incantation: String) {
    npc.say(incantation)
    npc.spotanim("spotanim.curse_casting", height = 92)
    player.soundSynth("synth.curse_cast_and_fire")
    delay(1)
    npc.facePlayer(player)
    delay(1)
    npc.resetFaceEntity()
    spotanim("spotanim.curse_impact", delay = 15, height = 124)
    player.soundSynth("synth.curse_hit", delay = 15)
    delay(1)
}

suspend fun Dialogue.teleportToMainLand() {
    access.spotanim("spotanim.curse_impact", delay = 15, height = 124)
    player.soundSynth("synth.teleport_all", delay = 15)
    delay(1)
    val teleporter = EssenceMineTeleporter.fromPortal(player.vars[EssencePortals.PORTAL_VARBIT])
    access.telejump(EssencePortals.returnDest(teleporter))
}
