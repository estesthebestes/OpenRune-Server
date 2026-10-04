package org.rsmod.content.quest.area.ardougne.hazeelcult

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.ArmourReturned
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.CarnilleanArmour
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.ChestKey
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.FoodPoisoned
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.HazeelMark
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.HazeelScroll
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.InHideout
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.Poison
import org.rsmod.content.quest.area.ardougne.hazeelcult.HazeelCultQuest.Companion.SideChosen
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The scenery of the quest: the five sewer valves and the raft they steer, the cave and the
 * Carnilleans' stairs, ladders, range, crate, cupboard, hidden wall and chests. The generic ladder
 * script already owns the plain ladders to and from the secret attic room.
 */
class HazeelCultLocs
@Inject
constructor(private val quest: HazeelCultQuest, private val locRepo: LocRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (valve in SewerValve.entries) {
            onOpLoc1(valve.loc) { turnValve(valve) }
        }
        onOpLoc1(Raft) { boardRaft(it.loc) }
        onOpLoc1(CaveEntrance) {
            arriveDelay()
            telejump(HazeelSewers.CaveEntry)
        }
        onOpLoc1(CaveStairs) {
            arriveDelay()
            telejump(HazeelSewers.CaveExit)
        }

        onOpLoc1(HouseStairsUp) {
            arriveDelay()
            telejump(HouseUpstairs)
        }
        onOpLoc1(HouseStairsDown) {
            arriveDelay()
            telejump(HouseGroundFloor)
        }
        onOpLoc1(BasementLadderDown) {
            arriveDelay()
            anim(ClimbSeq)
            delay(1)
            telejump(Basement)
        }
        onOpLoc1(BasementLadderUp) {
            arriveDelay()
            anim(ClimbSeq)
            delay(1)
            telejump(HouseLadderLanding)
        }

        onOpLoc1(Range) { inspectRange() }
        onOpLocU(Range, Poison) { pourPoison() }
        onOpLocU(Range) { warnAwayFromRange() }
        onOpLoc1(Crate) { searchCrate() }

        onOpLoc1(WallKnock) { knockOnWall() }
        onOpLoc1(AtticChest) { searchAtticChest() }
        onOpLocU(AtticChest, ChestKey) { searchAtticChest() }
        onOpLoc1(HideoutChest) { searchHideoutChest() }

        onOpLoc1(CupboardShut) { toggleCupboard(it.loc, CupboardOpen) }
        onOpLoc1(CupboardOpen) { searchCupboard() }
        onOpLoc2(CupboardOpen) { toggleCupboard(it.loc, CupboardShut) }

        onOpHeld1(HazeelMark) {
            mesbox(
                "The amulet is engraved with five sewer valves. From left to right they are " +
                    "turned: ${SewerValve.amuletPattern()}."
            )
        }
    }

    private suspend fun ProtectedAccess.turnValve(valve: SewerValve) {
        arriveDelay()
        val right = valve.isTurnedRight(player)
        val current = if (right) "right" else "left"
        val other = if (right) "left" else "right"
        startDialogue {
            val turn =
                choice2(
                    "Turn it to the $other.",
                    true,
                    "Do nothing.",
                    false,
                    title = "The valve is currently turned to the $current.",
                )
            if (!turn) {
                mesbox("You leave the valve alone.")
                return@startDialogue
            }
            val flipped = player.vars[valve.varbit] == 1
            VarPlayerIntMapSetter.set(player, valve.varbit, if (flipped) 0 else 1)
            mesbox(
                "You turn the valve to the $other. Beneath your feet you hear the sudden " +
                    "sound of rushing water."
            )
        }
    }

    private suspend fun ProtectedAccess.boardRaft(raft: BoundLocInfo) {
        arriveDelay()
        if (raft.coords != HazeelSewers.RaftStart) {
            telejump(HazeelSewers.RaftLanding)
            mesbox("The raft flows back to the cave entrance.")
            return
        }
        if (player.hazeelClivetLocation == 0 && !player.hazeelSewerChat) {
            startDialogue {
                chatNpcSpecific(
                    "Clivet",
                    ClivetHead,
                    angry,
                    "Hey! I don't remember saying you could use that raft!",
                )
            }
            return
        }
        val correct = SewerValve.correctCount(player)
        val destination = HazeelSewers.destination(correct)
        if (destination == null) {
            mesbox("The current is flowing against the raft. It won't move.")
            return
        }
        telejump(destination)
        val island = HazeelSewers.islandName(correct)
        if (island == null) {
            mesbox("The raft washes up the sewer until it reaches the end of the passage.")
            return
        }
        mesbox(
            "The raft washes up the sewer and stops at the $island island. You'll need to find " +
                "the right combination of the five sewer valves above if you want to go further."
        )
    }

    private fun ProtectedAccess.canPoisonSoup(): Boolean =
        !player.sidedWithCeril &&
            quest.stage(player) == SideChosen &&
            player.inv.contains(Poison)

    private suspend fun ProtectedAccess.inspectRange() {
        arriveDelay()
        startDialogue {
            mesbox("A pot of soup is bubbling away on the range.")
            if (!access.canPoisonSoup()) {
                return@startDialogue
            }
            val pour =
                choice2("Yes.", true, "No.", false, title = "Pour the poison into the pot?")
            if (pour) {
                access.poisonSoup()
            }
        }
    }

    private suspend fun ProtectedAccess.pourPoison() {
        arriveDelay()
        when {
            canPoisonSoup() -> poisonSoup()
            !player.sidedWithCeril && quest.stage(player) >= FoodPoisoned ->
                mesbox("You've already done enough poisoning.")
            else -> warnAwayFromRange()
        }
    }

    private suspend fun ProtectedAccess.poisonSoup() {
        if (!canPoisonSoup() || invDel(inv, Poison, 1).failure) {
            return
        }
        player.hazeelDogState = 1
        quest.quest.setQuestStage(this, FoodPoisoned)
        objbox(Poison, "You pour the poison into the pot and it dissolves into the soup.")
    }

    private suspend fun ProtectedAccess.warnAwayFromRange() {
        startDialogue {
            chatNpcSpecific(
                "Claus the Chef",
                ClausHead,
                angry,
                "Oi - I don't want people messing around with my range!",
            )
        }
    }

    private suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()
        val hunting = !player.sidedWithCeril && quest.stage(player) == InHideout
        if (!hunting || inv.contains(ChestKey)) {
            mes("You search the crate but find nothing of interest.")
            return
        }
        if (!inv.hasFreeSpace()) {
            objbox(
                ChestKey,
                "You search the crate and find an old key hidden at the bottom, but you don't " +
                    "have enough room to take it.",
            )
            return
        }
        invAdd(inv, ChestKey, 1)
        objbox(ChestKey, "You search the crate and find an old key hidden at the bottom.")
    }

    private suspend fun ProtectedAccess.knockOnWall() {
        arriveDelay()
        startDialogue {
            mesbox(
                "You knock on the wall. It sounds like there's a hollow space on the other side."
            )
            val push = choice2("Yes.", true, "No.", false, title = "Push against the wall?")
            if (!push) {
                return@startDialogue
            }
            val inside = player.coords.z >= HiddenRoom.z
            access.telejump(if (inside) HiddenRoomDoor else HiddenRoom)
        }
    }

    private suspend fun ProtectedAccess.searchAtticChest() {
        arriveDelay()
        val hunting = !player.sidedWithCeril && quest.stage(player) == InHideout
        if (!inv.contains(ChestKey) || !hunting) {
            mes("The chest is locked. It looks like it needs a key.")
            return
        }
        if (inv.contains(HazeelScroll)) {
            mes("You already have the scroll from this chest.")
            return
        }
        if (!inv.hasFreeSpace()) {
            objbox(
                HazeelScroll,
                "You unlock the chest and find a scroll inside, but you don't have enough room " +
                    "to take it.",
            )
            return
        }
        invAdd(inv, HazeelScroll, 1)
        objbox(HazeelScroll, "You unlock the chest and find a scroll inside.")
    }

    private suspend fun ProtectedAccess.searchHideoutChest() {
        arriveDelay()
        if (!player.sidedWithCeril) {
            mes("You search the chest but find nothing of interest.")
            return
        }
        if (player.hazeelAlomoneState != 2) {
            startDialogue {
                chatNpcSpecific("Alomone", AlomoneHead, angry, "Get away from that chest!")
            }
            return
        }
        if (quest.stage(player) != InHideout) {
            mes("You search the chest but find nothing of interest.")
            return
        }
        if (!inv.hasFreeSpace()) {
            objbox(
                CarnilleanArmour,
                "You find some armour in the chest, but you don't have enough room to take it.",
            )
            return
        }
        invAdd(inv, CarnilleanArmour, 1)
        player.hazeelFoundArmour = true
        objbox(CarnilleanArmour, "You find some armour in the chest.")
    }

    private fun ProtectedAccess.toggleCupboard(cupboard: BoundLocInfo, to: String) {
        soundSynth(CupboardSound)
        locRepo.del(cupboard, CupboardOpenTicks)
        locRepo.add(cupboard.coords, to, CupboardOpenTicks, cupboard.angle, cupboard.shape)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        val searching = player.sidedWithCeril && quest.stage(player) == ArmourReturned
        val hasPoison = inv.contains(Poison)
        val hasMark = inv.contains(HazeelMark)
        if (!searching || (player.hazeelEvidenceFound && hasPoison && hasMark)) {
            mes("You search the cupboard but find nothing of interest.")
            return
        }
        val missing = listOf(hasPoison, hasMark).count { !it }
        if (inv.freeSpace() < missing) {
            mesbox(
                "You search the cupboard and find a bottle of poison along with a mysterious " +
                    "amulet, but you don't have enough room to take them."
            )
            return
        }
        if (!hasPoison) invAdd(inv, Poison, 1)
        if (!hasMark) invAdd(inv, HazeelMark, 1)
        player.hazeelEvidenceFound = true
        startDialogue {
            doubleobjbox(
                Poison,
                HazeelMark,
                "You search the cupboard and find a bottle of poison along with a mysterious " +
                    "amulet.",
            )
            chatPlayer(neutral, "And what do we have here...")
        }
    }

    private companion object {
        const val Raft = "loc.hazeelsewerraft"
        const val CaveEntrance = "loc.hazeelcultcave"
        const val CaveStairs = "loc.hazeelcultstairs"

        const val HouseStairsUp = "loc.carnillean_stairs"
        const val HouseStairsDown = "loc.carnillean_stairstop"
        const val BasementLadderDown = "loc.carnillean_ladder_down"
        const val BasementLadderUp = "loc.carnillean_ladder_up"
        const val Range = "loc.carnilleanrange"
        const val Crate = "loc.carnilleancrate"
        const val WallKnock = "loc.carnilleanbookcase_knock"
        const val AtticChest = "loc.carnilleanshutchest_normal"
        const val HideoutChest = "loc.hazeel_chest_closed"
        const val CupboardShut = "loc.hazeelcbshut"
        const val CupboardOpen = "loc.hazeelcbopen"

        const val ClivetHead = "npc.clivet_hazeel_cultist_vis"
        const val ClausHead = "npc.claus_carnillean"
        const val AlomoneHead = "npc.alomone_hazeel_cultist_1op"

        const val ClimbSeq = "seq.human_reachforladder"
        const val CupboardSound = "synth.cupboard_open"
        const val CupboardOpenTicks = 100

        val HouseUpstairs = CoordGrid(2568, 3267, 1)
        val HouseGroundFloor = CoordGrid(2568, 3271, 0)
        val Basement = CoordGrid(2544, 9695, 0)
        val HouseLadderLanding = CoordGrid(2570, 3268, 0)
        val HiddenRoom = CoordGrid(2572, 3271, 1)
        val HiddenRoomDoor = CoordGrid(2572, 3270, 1)
    }
}
