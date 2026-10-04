package org.rsmod.content.quest.area.ardougne.hazeelcult

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUDefaultEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HazeelCultInteractionTest {

    @Test
    fun `accepting Ceril's request starts the quest`() {
        val f = Fixture()
        f.talk(Ceril)
        f.finish(listOf(1, 1))
        assertEquals(2, f.stage())
        assertEquals(2, f.player.vars["varp.hazeelcultquest"])
        assertTrue(f.output().contains("Higson"), f.output())
    }

    @Test
    fun `declining or leaving keeps the quest unstarted`() {
        for (options in listOf(listOf(1, 2), listOf(2), listOf(3))) {
            val f = Fixture()
            f.talk(Ceril)
            f.finish(options)
            assertEquals(0, f.stage(), "options $options")
        }
    }

    @Test
    fun `a low combat level is warned about before the prompt`() {
        val f = Fixture()
        f.talk(Ceril)
        f.finish(listOf(1, 2))
        assertTrue(f.output().contains("lower than the recommended level"), f.output())
    }

    @Test
    fun `Clivet's first talk leads to the offer and the questions loop back to the menu`() {
        val f = Fixture(2)
        f.talk(ClivetEntrance)
        f.finish(listOf(1, 1, 4, 5, 3))
        assertEquals(3, f.stage())
        assertTrue(f.output().contains("Lord Hazeel is one of the great Mahjarrat"), f.output())
        assertTrue(f.output().contains("Hazeel is one of the greatest Mahjarrat"), f.output())
        assertTrue(f.output().contains("Don't take too long."), f.output())
        assertFalse(f.player.sidedWithCeril)
    }

    @Test
    fun `taking time to think leaves the choice open`() {
        val f = Fixture(2)
        f.talk(ClivetEntrance)
        f.finish(listOf(2, 3))
        assertEquals(3, f.stage())
        f.talk(ClivetEntrance)
        f.finish(listOf(3))
        assertEquals(3, f.stage())
        assertEquals(0, f.player.hazeelClivetLocation)
        assertEquals(0, f.player.inv.count(Poison))
    }

    @Test
    fun `refusing Clivet sides with Ceril and sends Clivet off on the raft`() {
        val f = Fixture(3)
        f.talk(ClivetEntrance)
        f.finish(listOf(2, 2))
        assertEquals(4, f.stage())
        assertTrue(f.player.sidedWithCeril)
        assertEquals(0, f.player.inv.count(Poison))
        assertTrue(f.output().contains("pushes off into the sewer system"), f.output())
    }

    @Test
    fun `agreeing to help Clivet gives poison once the player asks how`() {
        val f = Fixture(3)
        f.talk(ClivetEntrance)
        f.finish(listOf(2, 1, 1))
        assertEquals(4, f.stage())
        assertFalse(f.player.sidedWithCeril)
        assertEquals(1, f.player.inv.count(Poison))
        assertTrue(f.player.hazeelGivenPoison)
    }

    @Test
    fun `needing time after agreeing gives no poison and no stage`() {
        val f = Fixture(3)
        f.talk(ClivetEntrance)
        f.finish(listOf(2, 1, 2))
        assertEquals(3, f.stage())
        assertEquals(0, f.player.inv.count(Poison))
    }

    @Test
    fun `a full inventory keeps the poison from Clivet until the player returns`() {
        val f = Fixture(3)
        f.fillInventory()
        f.talk(ClivetEntrance)
        f.finish(listOf(2, 1, 1))
        assertEquals(4, f.stage())
        assertEquals(0, f.player.inv.count(Poison))
        assertFalse(f.player.hazeelGivenPoison)
        assertTrue(f.output().contains("don't have enough room"), f.output())
        f.player.inv[0] = null
        f.talk(ClivetEntrance)
        f.finish()
        assertEquals(1, f.player.inv.count(Poison))
        assertTrue(f.player.hazeelGivenPoison)
    }

    @Test
    fun `Clivet replaces lost poison but not poison the player still holds`() {
        val f = Fixture(4)
        f.player.hazeelGivenPoison = true
        f.give(Poison, 1)
        f.talk(ClivetEntrance)
        f.finish()
        assertEquals(1, f.player.inv.count(Poison))
        f.player.inv[0] = null
        f.talk(ClivetEntrance)
        f.finish()
        assertEquals(1, f.player.inv.count(Poison))
        assertTrue(f.output().contains("Be more careful"), f.output())
    }

    @Test
    fun `pouring the poison advances once and kills the dog`() {
        val f = Fixture(4)
        f.give(Poison, 2)
        f.useOnLoc(Range, RangeCoords, Poison)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(1, f.player.inv.count(Poison))
        assertEquals(1, f.player.hazeelDogState)
        f.useOnLoc(Range, RangeCoords, Poison)
        f.finish()
        assertEquals(1, f.player.inv.count(Poison))
        assertTrue(f.output().contains("already done enough poisoning"), f.output())
    }

    @Test
    fun `inspecting the range offers to pour the poison and declining keeps it`() {
        val f = Fixture(4)
        f.give(Poison, 1)
        f.loc(Range, RangeCoords)
        f.finish(listOf(2))
        assertEquals(4, f.stage())
        assertEquals(1, f.player.inv.count(Poison))
        f.loc(Range, RangeCoords)
        f.finish(listOf(1))
        assertEquals(5, f.stage())
        assertEquals(0, f.player.inv.count(Poison))
    }

    @Test
    fun `the range only takes poison at the right time and shoos other items away`() {
        val early = Fixture(2)
        early.give(Poison, 1)
        early.useOnLoc(Range, RangeCoords, Poison)
        early.finish()
        assertEquals(2, early.stage())
        assertEquals(1, early.player.inv.count(Poison))
        assertTrue(early.output().contains("messing around with my range"), early.output())

        val ceril = Fixture(4)
        ceril.player.hazeelClivetLocation = 1
        ceril.give(Poison, 1)
        ceril.useOnLoc(Range, RangeCoords, Poison)
        ceril.finish()
        assertEquals(4, ceril.stage())
        assertEquals(1, ceril.player.inv.count(Poison))

        val other = Fixture(4)
        other.give("obj.logs", 1)
        other.useOnLoc(Range, RangeCoords, "obj.logs")
        other.finish()
        assertTrue(other.output().contains("messing around with my range"), other.output())
    }

    @Test
    fun `cancelling before the soup is poisoned keeps the poison`() {
        val f = Fixture(4)
        f.give(Poison, 1)
        f.loc(Range, RangeCoords)
        f.until { f.output().contains("A pot of soup is bubbling") }
        f.cancel()
        assertEquals(4, f.stage())
        assertEquals(1, f.player.inv.count(Poison))
    }

    @Test
    fun `Ceril reports the dog's death once and Clivet then rewards the player with the mark`() {
        val f = Fixture(5)
        f.player.hazeelDogState = 1
        f.talk(Clivet)
        f.finish()
        assertFalse(f.player.hazeelSewerChat)
        assertEquals(0, f.player.inv.count(HazeelMark))
        f.talk(Ceril)
        f.finish()
        assertTrue(f.player.hazeelPoisonSuccess)
        assertTrue(f.output().contains("Scruffy?"), f.output())
        f.talk(Ceril)
        f.finish()
        assertTrue(f.output().contains("Oh the misery"), f.output())
        f.talk(Clivet)
        f.finish()
        assertTrue(f.player.hazeelSewerChat)
        assertTrue(f.player.hazeelGivenAmulet)
        assertEquals(1, f.player.inv.count(HazeelMark))
        assertEquals(5, f.stage())
    }

    @Test
    fun `a full inventory delays the mark and Clivet hands it over on the next visit`() {
        val f = Fixture(5)
        f.player.hazeelPoisonSuccess = true
        f.fillInventory()
        f.talk(Clivet)
        f.finish()
        assertTrue(f.player.hazeelSewerChat)
        assertFalse(f.player.hazeelGivenAmulet)
        assertEquals(0, f.player.inv.count(HazeelMark))
        f.player.inv[0] = null
        f.talk(Clivet)
        f.finish()
        assertEquals(1, f.player.inv.count(HazeelMark))
        assertTrue(f.player.hazeelGivenAmulet)
        assertTrue(f.output().contains("Going from left to right"), f.output())
    }

    @Test
    fun `Clivet gives a lost mark again and only to a player without one`() {
        val f = Fixture(5)
        f.player.hazeelPoisonSuccess = true
        f.player.hazeelSewerChat = true
        f.player.hazeelGivenAmulet = true
        f.talk(Clivet)
        f.finish(listOf(2))
        assertEquals(1, f.player.inv.count(HazeelMark))
        f.talk(Clivet)
        f.finish()
        assertEquals(1, f.player.inv.count(HazeelMark))
        assertTrue(f.output().contains("Use the raft here"), f.output())
    }

    @Test
    fun `the mark shows the valve pattern`() {
        val f = Fixture(5)
        f.give(HazeelMark, 1)
        f.run {
            val type = checkNotNull(ServerCacheManager.getItem(HazeelMark.asRSCM(RSCMType.OBJ)))
            val event = HeldObjEvents.Op1(0, checkNotNull(inv[0]), type, inv)
            assertTrue(f.events.publish(this, event))
        }
        f.finish()
        assertTrue(f.output().contains("right, right, left, right, right"), f.output())
    }

    @Test
    fun `every valve starts wrong and turning it to the amulet side sets it right`() {
        val f = Fixture(5)
        assertEquals(0, SewerValve.correctCount(f.player))
        for ((index, valve) in SewerValve.entries.withIndex()) {
            val startsRight = valve == SewerValve.Clocktower
            f.loc(valve.loc, ValveCoords[index])
            f.finish(listOf(1))
            assertEquals(index + 1, SewerValve.correctCount(f.player), valve.name)
            assertEquals(!startsRight, valve.isTurnedRight(f.player), valve.name)
            assertTrue(f.output().contains("sudden sound of rushing water"), f.output())
        }
        assertEquals(5, SewerValve.correctCount(f.player))
    }

    @Test
    fun `doing nothing at a valve leaves it alone and a second turn undoes the first`() {
        val f = Fixture(5)
        f.loc(SewerValve.West.loc, ValveCoords[0])
        f.finish(listOf(2))
        assertEquals(0, SewerValve.correctCount(f.player))
        assertTrue(f.output().contains("You leave the valve alone."), f.output())
        f.loc(SewerValve.West.loc, ValveCoords[0])
        f.finish(listOf(1))
        assertEquals(1, SewerValve.correctCount(f.player))
        f.loc(SewerValve.West.loc, ValveCoords[0])
        f.finish(listOf(1))
        assertEquals(0, SewerValve.correctCount(f.player))
    }

    @Test
    fun `the raft will not move with no valve set correctly`() {
        val f = Fixture(5)
        f.player.hazeelSewerChat = true
        f.player.coords = HazeelSewers.RaftLanding
        f.loc(Raft, HazeelSewers.RaftStart)
        f.finish()
        assertEquals(HazeelSewers.RaftLanding, f.player.coords)
        assertTrue(f.output().contains("current is flowing against the raft"), f.output())
    }

    @Test
    fun `the raft stops at an island chosen by how many valves are right`() {
        val islands =
            mapOf(
                1 to ("first" to HazeelSewers.FirstIsland),
                2 to ("second" to HazeelSewers.SecondIsland),
                3 to ("fourth" to HazeelSewers.FourthIsland),
                4 to ("third" to HazeelSewers.ThirdIsland),
            )
        for ((count, expected) in islands) {
            val f = Fixture(5)
            f.player.hazeelSewerChat = true
            f.setValves(count)
            f.loc(Raft, HazeelSewers.RaftStart)
            f.finish()
            assertEquals(expected.second, f.player.coords, "count $count")
            assertTrue(f.output().contains("stops at the ${expected.first} island"), f.output())
        }
    }

    @Test
    fun `the right valves carry the raft to the hideout and any raft flows back`() {
        val f = Fixture(5)
        f.player.hazeelSewerChat = true
        f.setValves(5)
        f.loc(Raft, HazeelSewers.RaftStart)
        f.finish()
        assertEquals(HazeelSewers.HideoutLanding, f.player.coords)
        assertTrue(f.output().contains("reaches the end of the passage"), f.output())
        f.loc(Raft, CoordGrid(2606, 9693, 0))
        f.finish()
        assertEquals(HazeelSewers.RaftLanding, f.player.coords)
        assertTrue(f.output().contains("flows back to the cave entrance"), f.output())
    }

    @Test
    fun `Clivet stops anyone who has not been told about the raft`() {
        val f = Fixture(3)
        f.setValves(5)
        f.player.coords = HazeelSewers.RaftLanding
        f.loc(Raft, HazeelSewers.RaftStart)
        f.finish()
        assertEquals(HazeelSewers.RaftLanding, f.player.coords)
        assertTrue(f.output().contains("I don't remember saying you could use that raft"))
    }

    @Test
    fun `the cave leads down to the sewer and the stairs lead back up`() {
        val f = Fixture(2)
        f.loc(CaveEntrance, CoordGrid(2585, 3233, 0))
        f.finish()
        assertEquals(HazeelSewers.CaveEntry, f.player.coords)
        f.loc(CaveStairs, CoordGrid(2570, 9683, 0))
        f.finish()
        assertEquals(HazeelSewers.CaveExit, f.player.coords)
    }

    @Test
    fun `Alomone hears the player out and then fights`() {
        val f = Fixture(4)
        f.player.hazeelClivetLocation = 1
        f.talk(Alomone, register = true)
        f.finish()
        assertEquals(1, f.player.hazeelAlomoneState)
        assertTrue(f.player.hazeelAlomoneMet)
        assertTrue(f.output().contains("Why is it always the butler?"), f.output())
        assertEquals(4, f.stage())
    }

    @Test
    fun `slaying Alomone ends his part once and moves the Carnillean side on`() {
        val f = Fixture(4)
        f.player.hazeelClivetLocation = 1
        f.player.hazeelAlomoneState = 1
        f.run { alomoneFalls(f.quest) }
        assertEquals(2, f.player.hazeelAlomoneState)
        assertEquals(6, f.stage())
        f.run { alomoneFalls(f.quest) }
        assertEquals(6, f.stage())
    }

    @Test
    fun `an unprovoked Alomone cannot be slain for credit and the cult side is unaffected`() {
        val calm = Fixture(4)
        calm.player.hazeelClivetLocation = 1
        calm.run { alomoneFalls(calm.quest) }
        assertEquals(0, calm.player.hazeelAlomoneState)
        assertEquals(4, calm.stage())

        val cult = Fixture(5)
        cult.player.hazeelAlomoneState = 1
        cult.run { alomoneFalls(cult.quest) }
        assertEquals(1, cult.player.hazeelAlomoneState)
        assertEquals(5, cult.stage())
    }

    @Test
    fun `the hideout chest hides the armour until Alomone is dead`() {
        val f = Fixture(4)
        f.player.hazeelClivetLocation = 1
        f.loc(HideoutChest, HideoutChestCoords)
        f.finish()
        assertTrue(f.output().contains("Get away from that chest!"), f.output())
        assertEquals(0, f.player.inv.count(Armour))
    }

    @Test
    fun `the chest gives armour each time it is searched and honours a full inventory`() {
        val f = Fixture(6)
        f.player.hazeelClivetLocation = 1
        f.player.hazeelAlomoneState = 2
        f.loc(HideoutChest, HideoutChestCoords)
        f.finish()
        f.loc(HideoutChest, HideoutChestCoords)
        f.finish()
        assertEquals(2, f.player.inv.count(Armour))
        assertTrue(f.player.hazeelFoundArmour)
        f.fillInventory()
        f.loc(HideoutChest, HideoutChestCoords)
        f.finish()
        assertEquals(2, f.player.inv.count(Armour))
        assertTrue(f.output().contains("don't have enough room"), f.output())
    }

    @Test
    fun `the chest is empty outside the armour hunt`() {
        val cultist = Fixture(6)
        cultist.player.hazeelAlomoneState = 2
        cultist.loc(HideoutChest, HideoutChestCoords)
        cultist.finish()
        assertEquals(0, cultist.player.inv.count(Armour))
        val done = Fixture(9)
        done.player.hazeelClivetLocation = 1
        done.player.hazeelAlomoneState = 2
        done.loc(HideoutChest, HideoutChestCoords)
        done.finish()
        assertEquals(0, done.player.inv.count(Armour))
    }

    @Test
    fun `Ceril refuses a player without the armour and does not take other items`() {
        val f = Fixture(6)
        f.player.hazeelClivetLocation = 1
        f.give("obj.logs", 1)
        f.talk(Ceril)
        f.finish()
        assertEquals(6, f.stage())
        assertEquals(1, f.player.inv.count("obj.logs"))
        assertTrue(f.output().contains("shouldn't you be recovering my armour"), f.output())
    }

    @Test
    fun `returning the armour takes it once pays five coins and shows the partial scroll`() {
        val f = Fixture(6)
        f.player.hazeelClivetLocation = 1
        f.give(Armour, 2)
        f.talk(Ceril)
        f.finish()
        assertEquals(7, f.stage())
        assertEquals(1, f.player.inv.count(Armour))
        assertEquals(5, f.player.inv.count(Coins))
        assertTrue(f.player.hazeelGivenArmour)
        assertEquals(0, f.player.vars["varp.qp"])
        assertEquals(0, f.player.statMap.getXP("stat.thieving"))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.output().contains("Jones smirks at you"), f.output())
    }

    @Test
    fun `cancelling the hand-in before the payment keeps the armour and the stage`() {
        val f = Fixture(6)
        f.player.hazeelClivetLocation = 1
        f.give(Armour, 1)
        f.talk(Ceril)
        f.until { f.output().contains("Now take this and leave") }
        f.cancel()
        assertEquals(6, f.stage())
        assertEquals(1, f.player.inv.count(Armour))
        assertEquals(0, f.player.inv.count(Coins))
        f.talk(Ceril)
        f.finish()
        assertEquals(7, f.stage())
        assertEquals(5, f.player.inv.count(Coins))
    }

    @Test
    fun `a full inventory still pays the five coins after the armour is taken`() {
        val f = Fixture(6)
        f.player.hazeelClivetLocation = 1
        f.fillInventory()
        f.player.inv[0] = InvObj(Armour, 1)
        f.talk(Ceril)
        f.finish()
        assertEquals(7, f.stage())
        assertEquals(5, f.player.inv.count(Coins))
    }

    @Test
    fun `nobody believes the player after the first refusal`() {
        val f = Fixture(7)
        f.player.hazeelClivetLocation = 1
        f.player.hazeelAlomoneMet = true
        f.talk(Ceril)
        f.finish()
        assertTrue(f.output().contains("I owe you nothing"), f.output())
        assertEquals(7, f.stage())
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("asked to leave"), f.output())
        f.talk(Henryeta)
        f.finish()
        assertTrue(f.output().contains("insulting our trusted staff"), f.output())
        f.talk(Philipe)
        f.finish()
        assertTrue(f.output().contains("Mr Jones is nice"), f.output())
        f.talk(Guard)
        f.finish()
        assertTrue(f.output().contains("utter faith"), f.output())
        f.talk(Claus)
        f.finish()
        assertTrue(f.output().contains("How are you today?"), f.output())
    }

    @Test
    fun `the cupboard only holds the evidence once the armour is returned`() {
        val early = Fixture(6)
        early.player.hazeelClivetLocation = 1
        early.loc(CupboardOpen, CupboardCoords)
        early.finish()
        assertEquals(0, early.player.inv.count(Poison))
        assertFalse(early.player.hazeelEvidenceFound)

        val f = Fixture(7)
        f.player.hazeelClivetLocation = 1
        f.loc(CupboardOpen, CupboardCoords)
        f.finish()
        assertEquals(1, f.player.inv.count(Poison))
        assertEquals(1, f.player.inv.count(HazeelMark))
        assertTrue(f.player.hazeelEvidenceFound)
        f.loc(CupboardOpen, CupboardCoords)
        f.finish()
        assertEquals(1, f.player.inv.count(Poison))
        assertEquals(1, f.player.inv.count(HazeelMark))
    }

    @Test
    fun `the cupboard opens and shuts and a lost item can be searched for again`() {
        val f = Fixture(7)
        f.player.hazeelClivetLocation = 1
        f.place(CupboardShut, CupboardCoords)
        f.loc(CupboardShut, CupboardCoords)
        f.finish()
        f.loc(CupboardOpen, CupboardCoords, slot = 2)
        f.finish()
        f.loc(CupboardOpen, CupboardCoords)
        f.finish()
        assertEquals(1, f.player.inv.count(HazeelMark))
        f.player.inv[0] = null
        f.player.inv[1] = null
        assertEquals(0, f.player.inv.count(HazeelMark))
        f.loc(CupboardOpen, CupboardCoords)
        f.finish()
        assertEquals(1, f.player.inv.count(HazeelMark))
        assertEquals(1, f.player.inv.count(Poison))
    }

    @Test
    fun `a full inventory leaves the evidence in the cupboard`() {
        val f = Fixture(7)
        f.player.hazeelClivetLocation = 1
        f.fillInventory()
        f.loc(CupboardOpen, CupboardCoords)
        f.finish()
        assertEquals(0, f.player.inv.count(Poison))
        assertFalse(f.player.hazeelEvidenceFound)
        assertTrue(f.output().contains("don't have enough room"), f.output())
    }

    @Test
    fun `Ceril needs both pieces of evidence before he listens`() {
        val f = Fixture(7)
        f.player.hazeelClivetLocation = 1
        f.player.hazeelEvidenceFound = true
        f.give(Poison, 1)
        f.talk(Ceril)
        f.finish()
        assertEquals(7, f.stage())
        assertTrue(f.output().contains("I owe you nothing"), f.output())
    }

    @Test
    fun `showing Ceril the evidence finishes the Carnillean side exactly once`() {
        val f = Fixture(7)
        f.player.hazeelClivetLocation = 1
        f.player.hazeelEvidenceFound = true
        f.give(Poison, 1)
        f.give(HazeelMark, 1)
        f.give(Coins, 5)
        f.talk(Ceril)
        f.finish()
        f.assertCerilComplete()
        f.talk(Ceril)
        f.finish()
        f.assertCerilComplete(scroll = false)
        assertTrue(f.output().contains("We are in your debt"), f.output())
    }

    @Test
    fun `the whole Carnillean side plays through to the same rewards`() {
        val f = Fixture(2)
        f.talk(ClivetEntrance)
        f.finish(listOf(2, 2))
        f.player.hazeelAlomoneState = 1
        f.run { alomoneFalls(f.quest) }
        assertEquals(6, f.stage())
        f.loc(HideoutChest, HideoutChestCoords)
        f.finish()
        f.talk(Ceril)
        f.finish()
        assertEquals(7, f.stage())
        f.loc(CupboardOpen, CupboardCoords)
        f.finish()
        f.talk(Ceril)
        f.finish()
        f.assertCerilComplete(coins = 2005)
    }

    @Test
    fun `cancelling the accusation keeps the quest open until it is finished`() {
        val f = Fixture(7)
        f.player.hazeelClivetLocation = 1
        f.player.hazeelEvidenceFound = true
        f.give(Poison, 1)
        f.give(HazeelMark, 1)
        f.talk(Ceril)
        f.until { f.output().contains("Take a look at what I've found") }
        f.cancel()
        assertEquals(7, f.stage())
        assertEquals(0, f.player.hazeelJonesLocation)
        f.talk(Ceril)
        f.finish()
        f.assertCerilComplete(coins = 2000)
    }

    @Test
    fun `after the Carnillean side the household talks like friends`() {
        val f = Fixture(9)
        f.player.hazeelClivetLocation = 1
        f.talk(Ceril)
        f.finish()
        assertTrue(f.output().contains("We are in your debt"), f.output())
        f.talk(Guard)
        f.finish()
        assertTrue(f.output().contains("clear your name"), f.output())
        f.talk(Philipe)
        f.finish()
        assertTrue(f.output().contains("I want some more toys"), f.output())
        f.talk(Henryeta)
        f.finish()
        assertTrue(f.output().contains("vastly improved"), f.output())
        f.talk(Claus)
        f.finish()
        assertTrue(f.output().contains("Are we fit and well?"), f.output())
        f.talk(ClivetHideout)
        f.finish()
        assertTrue(f.output().contains("Leave this place at once"), f.output())
    }

    @Test
    fun `the cult's hideout is unwelcoming to the Carnillean side and friendly to the other`() {
        val ceril = Fixture(4)
        ceril.player.hazeelClivetLocation = 1
        ceril.talk(Cultist)
        ceril.finish()
        assertTrue(ceril.output().contains("You have no place here"), ceril.output())
        val cult = Fixture(5)
        cult.talk(CultistTwo)
        cult.finish()
        assertTrue(cult.output().contains("many preparations to make"), cult.output())
    }

    @Test
    fun `Alomone recruits the cult side once and the dog is buried`() {
        val f = Fixture(5)
        f.player.hazeelDogState = 1
        f.talk(Alomone)
        f.finish()
        assertEquals(6, f.stage())
        assertEquals(2, f.player.hazeelDogState)
        assertTrue(f.output().contains("we have a new recruit"), f.output())
        f.talk(Alomone)
        f.finish()
        assertEquals(6, f.stage())
        assertTrue(f.output().contains("imperative that you find that scroll"), f.output())
    }

    @Test
    fun `Butler Jones helps the scroll hunt along`() {
        val f = Fixture(6)
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("hidden where even the family won't find it"), f.output())
        f.give(ChestKey, 1)
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("Remember, the scroll must be"), f.output())
        f.give(Scroll, 1)
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("returning to Alomone"), f.output())
    }

    @Test
    fun `the crate gives a chest key only to the scroll hunter and only one at a time`() {
        val early = Fixture(5)
        early.loc(Crate, CrateCoords)
        early.finish()
        assertEquals(0, early.player.inv.count(ChestKey))

        val ceril = Fixture(6)
        ceril.player.hazeelClivetLocation = 1
        ceril.loc(Crate, CrateCoords)
        ceril.finish()
        assertEquals(0, ceril.player.inv.count(ChestKey))

        val f = Fixture(6)
        f.loc(Crate, CrateCoords)
        f.finish()
        assertEquals(1, f.player.inv.count(ChestKey))
        f.loc(Crate, CrateCoords)
        f.finish()
        assertEquals(1, f.player.inv.count(ChestKey))
        assertTrue(f.output().contains("find nothing of interest"), f.output())
    }

    @Test
    fun `a full inventory leaves the key in the crate`() {
        val f = Fixture(6)
        f.fillInventory()
        f.loc(Crate, CrateCoords)
        f.finish()
        assertEquals(0, f.player.inv.count(ChestKey))
        assertTrue(f.output().contains("don't have enough room"), f.output())
    }

    @Test
    fun `the secret wall can be pushed through from either side`() {
        val f = Fixture(6)
        f.player.coords = CoordGrid(2572, 3270, 1)
        f.loc(WallKnock, WallCoords)
        f.finish(listOf(1))
        assertEquals(CoordGrid(2572, 3271, 1), f.player.coords)
        f.loc(WallKnock, WallCoords)
        f.finish(listOf(1))
        assertEquals(CoordGrid(2572, 3270, 1), f.player.coords)
        f.loc(WallKnock, WallCoords)
        f.finish(listOf(2))
        assertEquals(CoordGrid(2572, 3270, 1), f.player.coords)
        assertTrue(f.output().contains("hollow space"), f.output())
    }

    @Test
    fun `the attic chest needs the key and gives the scroll once`() {
        val locked = Fixture(6)
        locked.loc(AtticChest, AtticChestCoords)
        locked.finish()
        assertEquals(0, locked.player.inv.count(Scroll))
        assertTrue(locked.output().contains("locked"), locked.output())

        val f = Fixture(6)
        f.give(ChestKey, 1)
        f.useOnLoc(AtticChest, AtticChestCoords, ChestKey)
        f.finish()
        assertEquals(1, f.player.inv.count(Scroll))
        assertEquals(1, f.player.inv.count(ChestKey))
        f.loc(AtticChest, AtticChestCoords)
        f.finish()
        assertEquals(1, f.player.inv.count(Scroll))
        assertTrue(f.output().contains("already have the scroll"), f.output())
    }

    @Test
    fun `the attic chest respects a full inventory and the quest stage`() {
        val full = Fixture(6)
        full.give(ChestKey, 1)
        full.fillInventory()
        full.loc(AtticChest, AtticChestCoords)
        full.finish()
        assertEquals(0, full.player.inv.count(Scroll))
        assertTrue(full.output().contains("don't have enough room"), full.output())

        val ceril = Fixture(6)
        ceril.player.hazeelClivetLocation = 1
        ceril.give(ChestKey, 1)
        ceril.loc(AtticChest, AtticChestCoords)
        ceril.finish()
        assertEquals(0, ceril.player.inv.count(Scroll))
    }

    @Test
    fun `Alomone refuses the cult side without the scroll and takes nothing else`() {
        val f = Fixture(6)
        f.give("obj.logs", 1)
        f.give(ChestKey, 1)
        f.talk(Alomone)
        f.finish()
        assertEquals(6, f.stage())
        assertEquals(1, f.player.inv.count(ChestKey))
        assertTrue(f.output().contains("imperative that you find that scroll"), f.output())
    }

    @Test
    fun `handing over the scroll completes the cult side once with every reward`() {
        val f = Fixture(6)
        f.give(Scroll, 1)
        f.give(HazeelMark, 1)
        f.talk(Alomone)
        f.finish()
        f.assertHazeelComplete()
        assertTrue(f.output().contains("Hazeel awakens from the coffin."), f.output())
        f.talk(Alomone)
        f.finish()
        f.assertHazeelComplete(scroll = false)
    }

    @Test
    fun `cancelling the ritual keeps the scroll and the stage`() {
        val f = Fixture(6)
        f.give(Scroll, 1)
        f.talk(Alomone)
        f.until { f.output().contains("Hazeel awakens from the coffin.") }
        f.cancel()
        assertEquals(6, f.stage())
        assertEquals(1, f.player.inv.count(Scroll))
        assertFalse(f.player.hazeelGivenScroll)
        assertEquals(0, f.player.statMap.getXP("stat.thieving"))
        f.talk(Alomone)
        f.finish()
        f.assertHazeelComplete(mark = 0)
    }

    @Test
    fun `the whole cult side plays through to the rewards`() {
        val f = Fixture(2)
        f.talk(ClivetEntrance)
        f.finish(listOf(2, 1, 1))
        f.useOnLoc(Range, RangeCoords, Poison)
        f.finish()
        assertEquals(5, f.stage())
        f.talk(Ceril)
        f.finish()
        f.talk(Clivet)
        f.finish()
        f.setValves(5)
        f.player.coords = HazeelSewers.RaftLanding
        f.loc(Raft, HazeelSewers.RaftStart)
        f.finish()
        assertEquals(HazeelSewers.HideoutLanding, f.player.coords)
        f.talk(Alomone)
        f.finish()
        assertEquals(6, f.stage())
        f.loc(Crate, CrateCoords)
        f.finish()
        f.loc(AtticChest, AtticChestCoords)
        f.finish()
        f.talk(Alomone)
        f.finish()
        f.assertHazeelComplete()
    }

    @Test
    fun `after the cult side the household mourns Scruffy`() {
        val f = Fixture(9)
        f.talk(Ceril)
        f.finish()
        assertTrue(f.output().contains("It was only a DOG"), f.output())
        f.talk(Henryeta)
        f.finish()
        assertTrue(f.output().contains("poor Scruffy"), f.output())
        f.talk(Clivet)
        f.finish()
        assertTrue(f.output().contains("Glory to Hazeel"), f.output())
        f.talk(Alomone)
        f.finish()
        assertTrue(f.output().contains("Glory to Hazeel"), f.output())
    }

    @Test
    fun `the household reacts to the poisoned dog`() {
        val f = Fixture(5)
        f.player.hazeelDogState = 1
        f.talk(Henryeta)
        f.finish()
        assertTrue(f.output().contains("They slaughtered my precious Scruffy"), f.output())
        f.talk(Philipe)
        f.finish()
        assertTrue(f.output().contains("Someone killed Scruffy"), f.output())
        f.talk(Claus)
        f.finish()
        assertTrue(f.output().contains("Caught any of those weird cultists yet?"), f.output())
        f.talk(Guard)
        f.finish()
        assertTrue(f.output().contains("Today is a dark day"), f.output())
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("terrible shame about Scruffy"), f.output())
    }

    @Test
    fun `the guard notices a burglary once the player holds the scroll`() {
        val f = Fixture(6)
        f.talk(Guard)
        f.finish()
        assertTrue(f.output().contains("Today is a dark day"), f.output())
        f.give(Scroll, 1)
        f.talk(Guard)
        f.finish()
        assertTrue(f.output().contains("another burglary"), f.output())
    }

    @Test
    fun `before the choice the household has only its everyday lines`() {
        val f = Fixture(2)
        f.talk(Ceril)
        f.finish()
        assertTrue(f.output().contains("shouldn't you be recovering my armour"), f.output())
        f.talk(Henryeta)
        f.finish()
        assertTrue(f.output().contains("not very presentable"), f.output())
        f.talk(Claus)
        f.finish()
        assertTrue(f.output().contains("how many meals this family gets"), f.output())
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("Very well, thank you."), f.output())
    }

    @Test
    fun `Butler Jones is accused only after Alomone has said so`() {
        val f = Fixture(4)
        f.player.hazeelClivetLocation = 1
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("Very well, thank you."), f.output())
        f.player.hazeelAlomoneMet = true
        f.talk(Jones)
        f.finish()
        assertTrue(f.output().contains("preposterous accusation"), f.output())
    }

    @Test
    fun `the journal follows the side and the stage`() {
        val f = Fixture(2)
        assertTrue(f.quest.questLog(f.access()).contains("cave"))
        f.stage(3)
        assertTrue(f.quest.questLog(f.access()).contains("Clivet"))
        f.stage(4)
        f.player.hazeelClivetLocation = 1
        assertTrue(f.quest.questLog(f.access()).contains("sewer valves"))
        f.stage(6)
        assertTrue(f.quest.questLog(f.access()).contains("Carnillean armour"))
        f.stage(7)
        assertTrue(f.quest.questLog(f.access()).contains("cupboard"))
        f.player.hazeelEvidenceFound = true
        assertTrue(f.quest.questLog(f.access()).contains("show them to"))
        val cult = Fixture(4)
        assertTrue(cult.quest.questLog(cult.access()).contains("poison Ceril's food"))
        cult.stage(6)
        assertTrue(cult.quest.questLog(cult.access()).contains("scroll of restoration"))
        val ceril = Fixture(9)
        ceril.player.hazeelClivetLocation = 1
        assertTrue(ceril.quest.completedLog(ceril.access()).contains("QUEST COMPLETE"))
        assertTrue(ceril.quest.completedLog(ceril.access()).contains("Jones was arrested"))
        val done = Fixture(9)
        assertTrue(done.quest.completedLog(done.access()).contains("bring the Mahjarrat"))
    }

    @Test
    fun `the quest ends at the stage the cache expects`() {
        val f = Fixture()
        assertEquals(HazeelCultQuest.Complete, f.quest.quest.maxSteps)
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("hazeel-cult-test")
        private var result: Result<Unit>? = null
        private val collision = CollisionFlagMap()
        private val clock = MapClock(100)
        private val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val activity = ZonePlayerActivityBitSet()
        private val npcList = NpcList()
        private val playerList = PlayerList()
        private val npcRegistry = NpcRegistry(npcList, collision, events)
        private val playerRegistry = PlayerRegistry(playerList, collision, activity, events)
        private val objRegistry = ObjRegistry(updates)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions =
            RegionRegistry(
                RegionListSmall(),
                RegionListLarge(),
                RegionListWorldEntity(),
                normal,
                collision,
                storage,
                npcRegistry,
                ControllerRegistry(clock, ControllerList()),
                activity,
            )
        private val locRegistry =
            LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
        private val locs = LocRepository(clock, locRegistry, regions)
        private val objs = ObjRepository(clock, objRegistry)
        private val npcs = NpcRepository(clock, npcRegistry, npcList)
        private val aiInteractions = AiPlayerInteractions(events, playerList)
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                    getCollision = { collision },
                    getNpcList = { npcList },
                    getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
                    getAreaChecker = { AreaChecker(regions, AreaIndex()) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 811L
                slotId = 1
                assignUid()
                coords = CoordGrid(2569, 3272, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
                worn =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())),
                        arrayOfNulls(14),
                    )
            }

        val quest = HazeelCultQuest()
        private var nextSlot = 0

        init {
            for (x in 2528..2624 step 8) {
                for (z in listOf(3224, 3232, 3240, 3248, 3256, 3264, 3272, 3280)) {
                    allocate(x, z)
                }
                for (z in 9664..9728 step 8) {
                    allocate(x, z)
                }
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
            with(HazeelCultHousehold(quest, objs)) { scripts.startup() }
            with(HazeelCultCult(quest, aiInteractions)) { scripts.startup() }
            with(HazeelCultLocs(quest, locs)) { scripts.startup() }
            stage(stage)
        }

        private fun allocate(x: Int, z: Int) {
            for (level in 0..2) {
                collision.allocateIfAbsent(x, z, level)
            }
        }

        fun stage() = quest.quest.getQuestStage(player)

        fun stage(value: Int) {
            VarPlayerIntMapSetter.set(player, "varbit.hazeel_cult_progress", value)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun give(obj: String, count: Int) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            if (type.stackable) {
                player.inv[nextSlot++] = InvObj(obj, count)
            } else {
                repeat(count) { player.inv[nextSlot++] = InvObj(obj, 1) }
            }
        }

        fun fillInventory() {
            for (slot in 0 until 28) {
                if (player.inv[slot] == null) {
                    player.inv[slot] = InvObj("obj.logs", 1)
                }
            }
        }

        fun setValves(correct: Int) {
            for ((index, valve) in SewerValve.entries.withIndex()) {
                VarPlayerIntMapSetter.set(player, valve.varbit, if (index < correct) 1 else 0)
            }
        }

        fun talk(symbol: String, op: Int = 1, register: Boolean = false) {
            val npc = Npc(symbol, player.coords.translateZ(1))
            if (register) {
                npcs.add(npc, 100)
            }
            run {
                val event = if (op == 3) NpcEvents.Op3(npc) else NpcEvents.Op1(npc)
                assertTrue(events.publish(this, event))
            }
        }

        private fun boundLoc(symbol: String, coords: CoordGrid): Pair<BoundLocInfo, Any> {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val shape = LocShape.CentrepieceStraight
            val loc =
                BoundLocInfo(
                    LocInfo(
                        CentrepieceLayer,
                        coords,
                        LocEntity(type.id, shape.id, LocAngle.West.id),
                    ),
                    type,
                )
            return loc to type
        }

        fun loc(symbol: String, coords: CoordGrid, slot: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val (loc, _) = boundLoc(symbol, coords)
            run {
                val event =
                    if (slot == 2) LocEvents.Op2(loc, loc, type) else LocEvents.Op1(loc, loc, type)
                assertTrue(events.publish(this, event))
            }
        }

        fun place(symbol: String, coords: CoordGrid) {
            locs.add(
                coords,
                symbol,
                1000,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        }

        fun useOnLoc(symbol: String, coords: CoordGrid, obj: String) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val item = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            val (loc, _) = boundLoc(symbol, coords)
            val slot = (0 until 28).first { player.inv[it]?.id == item.id }
            run {
                val specific = LocUEvents.Op(loc, loc, type, item, slot)
                if (!events.publish(this, specific)) {
                    val default = LocUDefaultEvents.OpType(loc, loc, type, item, slot)
                    assertTrue(events.publish(this, default))
                }
            }
        }

        fun run(block: suspend ProtectedAccess.() -> Unit) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { access().block() }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            result?.getOrThrow()
        }

        fun until(predicate: () -> Boolean) {
            repeat(300) {
                if (predicate()) return
                advance(emptyList<Int>().iterator())
            }
            fail<Unit>("Condition was not reached: ${output()}")
        }

        fun finish(options: List<Int> = emptyList()) {
            val selections = options.iterator()
            repeat(300) {
                if (coroutine.isIdle) return
                advance(selections)
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun advance(options: Iterator<Int>) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val input =
                    when {
                        player.ui.containsModal("interface.chatmenu") ->
                            ResumePauseButtonInput(
                                "component.chatmenu:options",
                                if (options.hasNext()) options.next() else 1,
                            )
                        player.ui.containsModal("interface.objectbox") ->
                            ResumePauseButtonInput("component.objectbox:universe", -1)
                        player.ui.containsModal("interface.objectbox_double") ->
                            ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                        else -> {
                            val parent =
                                listOf("chat_left", "chat_right", "messagebox").firstOrNull {
                                    player.ui.containsModal("interface.$it")
                                } ?: error("Unknown dialogue: ${output()}")
                            ResumePauseButtonInput("component.$parent:continue", -1)
                        }
                    }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            result?.getOrThrow()
        }

        fun cancel() {
            coroutine.cancel()
            assertInstanceOf(CancellationException::class.java, result?.exceptionOrNull())
            result = null
            player.activeCoroutine = null
        }

        fun assertCerilComplete(coins: Int = 2005, scroll: Boolean = true) {
            assertEquals(9, stage())
            assertEquals(9, player.vars["varp.hazeelcultquest"])
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(1500, player.statMap.getXP("stat.thieving"))
            assertEquals(coins, player.inv.count(Coins))
            assertEquals(1, player.hazeelJonesLocation)
            if (scroll) assertTrue(player.ui.containsModal("interface.questscroll"))
        }

        fun assertHazeelComplete(mark: Int = 1, scroll: Boolean = true) {
            assertEquals(9, stage())
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(1500, player.statMap.getXP("stat.thieving"))
            assertEquals(2000, player.inv.count(Coins))
            assertEquals(0, player.inv.count(Scroll))
            assertEquals(mark, player.inv.count(HazeelMark))
            assertTrue(player.hazeelGivenScroll)
            assertTrue(player.hazeelRevived)
            if (scroll) assertTrue(player.ui.containsModal("interface.questscroll"))
        }

        fun output() = client.messages.joinToString("\n").replace("<br>", " ")
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()

        override fun write(message: Any) {
            messages += message
        }

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val Ceril = "npc.sir_ceril_carnillean"
        private const val Guard = "npc.guard_carnillean"
        private const val Philipe = "npc.philipe_carnillean"
        private const val Claus = "npc.claus_carnillean"
        private const val Henryeta = "npc.carnillean_wife"
        private const val Jones = "npc.butler_jones_hazeel_cultist"
        private const val ClivetEntrance = "npc.clivet_hazeel_cultist"
        private const val Clivet = ClivetEntrance
        private const val ClivetHideout = "npc.clivet_hazeel_cultist_hideout"
        private const val Alomone = "npc.alomone_hazeel_cultist"
        private const val Cultist = "npc.hazeel_cultist"
        private const val CultistTwo = "npc.hazeel_cultist_2"

        private const val CentrepieceLayer = 2

        private const val Poison = "obj.poison"
        private const val HazeelMark = "obj.mark_of_hazeel"
        private const val Armour = "obj.carnillean_armour"
        private const val ChestKey = "obj.carnilleanchestkey"
        private const val Scroll = "obj.hazeel_scroll"
        private const val Coins = "obj.coins"

        private const val Range = "loc.carnilleanrange"
        private const val Crate = "loc.carnilleancrate"
        private const val Raft = "loc.hazeelsewerraft"
        private const val CaveEntrance = "loc.hazeelcultcave"
        private const val CaveStairs = "loc.hazeelcultstairs"
        private const val WallKnock = "loc.carnilleanbookcase_knock"
        private const val AtticChest = "loc.carnilleanshutchest_normal"
        private const val HideoutChest = "loc.hazeel_chest_closed"
        private const val CupboardShut = "loc.hazeelcbshut"
        private const val CupboardOpen = "loc.hazeelcbopen"

        private val RangeCoords = CoordGrid(2538, 9699, 0)
        private val CrateCoords = CoordGrid(2545, 9696, 0)
        private val WallCoords = CoordGrid(2572, 3270, 1)
        private val AtticChestCoords = CoordGrid(2571, 3269, 2)
        private val HideoutChestCoords = CoordGrid(2611, 9674, 0)
        private val CupboardCoords = CoordGrid(2573, 3267, 1)
        private val ValveCoords =
            listOf(
                CoordGrid(2562, 3247, 0),
                CoordGrid(2572, 3263, 0),
                CoordGrid(2585, 3245, 0),
                CoordGrid(2597, 3263, 0),
                CoordGrid(2611, 3242, 0),
            )

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in
                listOf(
                    "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                    "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
                )) {
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
