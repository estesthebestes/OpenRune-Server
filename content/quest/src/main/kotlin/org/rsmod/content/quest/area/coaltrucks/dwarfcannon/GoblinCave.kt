package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CAVE_ARRIVAL
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.CAVE_EXIT
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.LOLLK
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_FIND_LOLLK
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_LOLLK_RESCUED
import org.rsmod.game.entity.Npc
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The goblin cave (the goblins' "Plain of Mud") south-east of the Fishing Guild. Lollk is tied up
 * inside the one crate that shakes, in the crate store at the cave's north-western end.
 */
class GoblinCave
@Inject
constructor(
    private val dwarfCannon: DwarfCannonQuest,
    private val npcRepo: NpcRepository,
    private val routeFactory: RouteFactory,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CAVE_ENTRANCE) { enterCave() }
        onOpLoc1(MUD_PILE) { leaveCave() }
        onOpLoc1(LOLLK_CRATE) { searchLollkCrate() }
        for (crate in STORE_CRATES) {
            onOpLoc1(crate) { searchCrate() }
        }
    }

    private suspend fun ProtectedAccess.enterCave() {
        arriveDelay()
        anim(CRAWL_ANIM)
        delay(1)
        telejump(CAVE_ARRIVAL, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.leaveCave() {
        arriveDelay()
        anim(CLIMB_ANIM)
        delay(1)
        telejump(CAVE_EXIT, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()
        anim(SEARCH_ANIM)
        mes("You search the crate...")
        delay(1)
        mes("You search the crate but find nothing.")
    }

    private suspend fun ProtectedAccess.searchLollkCrate() {
        arriveDelay()
        val stage = dwarfCannon.stage(player)
        if (stage !in STAGE_FIND_LOLLK until STAGE_LOLLK_RESCUED) {
            searchCrate()
            return
        }
        anim(SEARCH_ANIM)
        mes("You search the crate..")
        delay(1)
        mes("Inside you see a dwarf child, tied up!")
        delay(1)
        mes("You untie the child.")
        dwarfCannon.advanceTo(this, STAGE_LOLLK_RESCUED)
        val lollk = Npc(LOLLK, lollkTile(player.coords))
        lollk.respawns = false
        npcRepo.add(lollk, LOLLK_TICKS)
        lollk.facePlayer(player)
        faceSquare(lollk.coords)
        startDialogue(lollk) {
            chatNpc(happy, "Thank the heavens, you saved me! I thought I'd be goblin lunch for sure!")
            chatPlayer(quiz, "Are you okay?")
            chatNpc(neutral, "I think so, I'd better run off home.")
            chatPlayer(happy, "That's right, you get going, I'll catch up.")
            chatNpc(happy, "Thanks again, brave adventurer.")
        }
        mes("The dwarf child runs off into the caverns.")
        if (!lollk.isSlotAssigned) {
            return
        }
        lollk.walkTo(routeFactory, LOLLK_ESCAPE, speed = MoveSpeed.Run) {
            if (lollk.isSlotAssigned) {
                npcRepo.del(lollk, Int.MAX_VALUE)
            }
        }
    }

    private fun lollkTile(player: CoordGrid): CoordGrid = LOLLK_SPAWNS.firstOrNull { it != player } ?: LOLLK_SPAWNS.last()

    internal companion object {
        const val CAVE_ENTRANCE = "loc.mcannoncave"
        const val MUD_PILE = "loc.mcanmudpile"
        const val LOLLK_CRATE = "loc.mcannoncrateboy"
        val STORE_CRATES = listOf("loc.mcannon_crates", "loc.mcannon_crates2", "loc.mcannon_crates3")

        const val SEARCH_ANIM = "seq.human_pickuptable"
        const val CRAWL_ANIM = "seq.human_pickupfloor"
        const val CLIMB_ANIM = "seq.human_reachforladder"

        /** Free tiles beside the crate, west of it, where Lollk climbs out. */
        val LOLLK_SPAWNS =
            listOf(CoordGrid(2570, 9850, 0), CoordGrid(2570, 9851, 0), CoordGrid(2570, 9849, 0))

        /** The crate store's eastern way out, towards the rest of the cave. */
        val LOLLK_ESCAPE = CoordGrid(2577, 9846, 0)

        /** A fallback despawn in case the player walks off mid-conversation. */
        const val LOLLK_TICKS = 100
    }
}
