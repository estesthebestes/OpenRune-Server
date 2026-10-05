package org.rsmod.content.quest.area.ardougne.tribaltotem

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUDefaultEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfModalButton
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
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Complete
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Delivered
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.LabelPlaced
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Started
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.TrapFound
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
class TribalTotemInteractionTest {

    @Test
    fun `the whole quest from Kangai Mau to the swordfish`() {
        val f = Fixture()
        f.talk(Kangai)
        f.finish(listOf(1, 1))
        assertEquals(Started, f.stage())
        assertEquals(Started, f.player.vars["varp.totemquest"])
        assertTrue(f.output().contains("Ardougne is a big place"), f.output())
        assertTrue(f.journal().contains("front door"))

        f.loc(LabelCrate, LabelCrateCoords, 2)
        f.finish()
        assertEquals(1, f.count(Label))
        assertTrue(f.output().contains("peel it off"), f.output())
        assertEquals(Started, f.stage())
        assertTrue(f.journal().contains("I have taken the address label"))

        f.useOnLoc(BlockCrate, BlockCrateCoords, Label)
        f.finish()
        assertEquals(LabelPlaced, f.stage())
        assertEquals(0, f.count(Label))
        assertTrue(f.output().contains("covering it completely"), f.output())
        assertTrue(f.output().contains("someone to deliver it"), f.output())
        assertTrue(f.journal().contains("GPDT employee"))

        f.loc(BlockCrate, BlockCrateCoords, 2)
        f.finish()
        assertTrue(f.output().contains("To Lord Handelmort, Handelmort Mansion"), f.output())

        f.talk(Employee)
        f.finish(listOf(1))
        assertEquals(Delivered, f.stage())
        assertTrue(f.output().contains("we could do it now"), f.output())
        assertTrue(f.journal().contains("Wizard Cromperty"))

        f.player.coords = CrompertyCoords
        f.talk(Cromperty)
        f.finish(listOf(2, 2, 1))
        assertEquals(TribalTotemQuest.MansionLanding, f.player.coords)
        assertTrue(f.output().contains("Okey dokey"), f.output())

        f.loc(Front, FrontCoords)
        f.finish()
        assertTrue(f.output().contains("This door is securely locked."), f.output())

        f.loc(Combo, ComboCoords, shape = LocShape.WallStraight)
        f.finish()
        assertTrue(f.player.ui.containsModal("interface.rd_combolock"))
        f.dial("a", 10)
        f.dial("b", -6)
        f.dial("c", -9)
        f.dial("d", -7)
        f.press("rdenter")
        assertTrue(f.output().contains("The combination seems correct!"), f.output())
        assertFalse(f.player.ui.containsModal("interface.rd_combolock"))

        f.player.statMap.setBaseLevel("stat.thieving", 21)
        f.player.statMap.setCurrentLevel("stat.thieving", 21)
        f.loc(Stairs, StairsCoords, 2)
        f.finish()
        assertEquals(TrapFound, f.stage())
        assertTrue(f.output().contains("trained senses as a thief"), f.output())
        assertTrue(f.journal().contains("spotted the trap"))

        f.loc(Stairs, StairsCoords)
        f.finish()
        assertEquals(TribalTotemQuest.StairsTop, f.player.coords)
        assertTrue(f.output().contains("You climb the stairs."), f.output())

        f.loc(ShutChest, ChestCoords)
        f.finish()
        assertTrue(f.output().contains("You open the chest."), f.output())
        f.loc(OpenChest, ChestCoords)
        f.finish()
        assertEquals(1, f.count(Totem))
        assertTrue(f.output().contains("you find the tribal totem"), f.output())
        assertTrue(f.journal().contains("I have the totem"))

        f.player.coords = CoordGrid(2791, 3181, 0)
        f.talk(Kangai)
        f.finish()
        f.assertRewards()
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.output().contains("freshly cooked"), f.output())

        f.talk(Kangai)
        f.finish()
        assertTrue(f.output().contains("esteemed thief"), f.output())
        f.assertRewards()
    }

    @Test
    fun `declining, other options and a quest already started do not restart anything`() {
        val declined = Fixture()
        declined.talk(Kangai)
        declined.finish(listOf(1, 2))
        assertEquals(0, declined.stage())
        assertTrue(declined.output().contains("help soon"), declined.output())

        val adventure = Fixture()
        adventure.talk(Kangai)
        adventure.finish(listOf(2, 1))
        assertEquals(Started, adventure.stage())
        assertTrue(adventure.output().contains("Adventure is something"), adventure.output())

        val tribe = Fixture()
        tribe.talk(Kangai)
        tribe.finish(listOf(3, 1))
        assertEquals(Started, tribe.stage())
        assertTrue(tribe.output().contains("proud and noble tribe"), tribe.output())

        val noTribe = Fixture()
        noTribe.talk(Kangai)
        noTribe.finish(listOf(3, 2))
        assertEquals(0, noTribe.stage())
    }

    @Test
    fun `Kangai refuses to be paid without the totem and ignores wrong items`() {
        val f = Fixture(TrapFound)
        f.give(Swordfish)
        f.talk(Kangai)
        f.finish()
        assertEquals(TrapFound, f.stage())
        assertTrue(f.output().contains("it's not that easy"), f.output())
        assertTrue(f.output().contains("you no good"), f.output())
        assertEquals(0, f.player.vars["varp.qp"])

        f.use(Kangai, Swordfish)
        f.finish()
        assertEquals(TrapFound, f.stage())
        assertEquals(1, f.count(Swordfish))
        assertTrue(f.output().contains("Nothing interesting happens"), f.output())

        val unstarted = Fixture()
        unstarted.give(Totem)
        unstarted.use(Kangai, Totem)
        unstarted.finish()
        assertEquals(0, unstarted.stage())
        assertEquals(1, unstarted.count(Totem))
    }

    @Test
    fun `using the totem on Kangai hands it in and rewards exactly once`() {
        val f = Fixture(TrapFound)
        f.give(Totem)
        f.use(Kangai, Totem)
        f.finish()
        f.assertRewards()

        f.give(Totem)
        f.use(Kangai, Totem)
        f.finish()
        assertEquals(1, f.count(Totem))
        assertEquals(5, f.count(Swordfish))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(1775, f.player.statMap.getXP("stat.thieving"))
    }

    @Test
    fun `the swordfish land on the floor when the inventory is full and nothing is lost`() {
        val f = Fixture(TrapFound)
        f.give(Totem)
        f.fillInventory()
        f.talk(Kangai)
        f.finish()
        assertEquals(Complete, f.stage())
        assertEquals(0, f.count(Totem))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(1775, f.player.statMap.getXP("stat.thieving"))
        assertEquals(5, f.count(Swordfish) + f.onFloor(Swordfish))
    }

    @Test
    fun `the label crate only gives a label while the quest wants one`() {
        val before = Fixture()
        before.loc(LabelCrate, LabelCrateCoords, 2)
        before.finish()
        assertEquals(0, before.count(Label))
        assertTrue(before.output().contains("Lord Handelmort, Handelmort Mansion"), before.output())

        val started = Fixture(Started)
        started.loc(LabelCrate, LabelCrateCoords, 2)
        started.finish()
        assertEquals(1, started.count(Label))
        started.loc(LabelCrate, LabelCrateCoords, 2)
        started.finish()
        assertEquals(1, started.count(Label))
        assertTrue(started.output().contains("gluey outline"), started.output())

        started.player.inv[started.player.inv.indexOfFirst { it?.id == Label.asRSCM() }] = null
        started.loc(LabelCrate, LabelCrateCoords, 2)
        started.finish()
        assertEquals(1, started.count(Label))

        val placed = Fixture(LabelPlaced)
        placed.loc(LabelCrate, LabelCrateCoords, 2)
        placed.finish()
        assertEquals(0, placed.count(Label))
        assertTrue(placed.output().contains("gluey outline"), placed.output())
    }

    @Test
    fun `the block crate is sealed until the label goes on and takes only the label`() {
        val f = Fixture(Started)
        f.loc(BlockCrate, BlockCrateCoords, 2)
        f.finish()
        assertTrue(f.output().contains("Senior Patents Clerk"), f.output())
        assertTrue(f.output().contains("securely fastened shut"), f.output())

        f.give(Totem)
        f.useOnLoc(BlockCrate, BlockCrateCoords, Totem)
        f.finish()
        assertEquals(Started, f.stage())
        assertEquals(1, f.count(Totem))
        assertTrue(f.output().contains("Nothing interesting happens"), f.output())

        f.give(Label)
        f.useOnLoc(BlockCrate, BlockCrateCoords, Label)
        f.finish()
        assertEquals(LabelPlaced, f.stage())
        assertEquals(0, f.count(Label))

        f.give(Label)
        f.useOnLoc(BlockCrate, BlockCrateCoords, Label)
        f.finish()
        assertEquals(LabelPlaced, f.stage())
        assertEquals(1, f.count(Label))
        assertTrue(f.output().contains("already replaced the delivery address label"), f.output())
    }

    @Test
    fun `the GPDT employee only agrees to deliver once the label is on`() {
        val plain = Fixture(Started)
        plain.talk(Employee)
        plain.finish()
        assertEquals(Started, plain.stage())
        assertTrue(plain.output().contains("Welcome to GPDT!"), plain.output())
        assertTrue(plain.output().contains("Thank you very much."), plain.output())

        val chat = Fixture(LabelPlaced)
        chat.talk(Employee)
        chat.finish(listOf(2))
        assertEquals(LabelPlaced, chat.stage())
        assertTrue(chat.output().contains("premier delivery service"), chat.output())

        val asked = Fixture(LabelPlaced)
        asked.talk(Employee)
        asked.finish(listOf(1))
        assertEquals(Delivered, asked.stage())
        asked.talk(Employee)
        asked.finish(listOf(1))
        assertEquals(Delivered, asked.stage())
    }

    @Test
    fun `Cromperty teleports to wherever the block is and has no signal outside the quest`() {
        val depot = Fixture(LabelPlaced)
        depot.player.coords = CrompertyCoords
        depot.talk(Cromperty)
        depot.finish(listOf(2, 2, 1))
        assertEquals(TribalTotemQuest.DepotLanding, depot.player.coords)

        val mansion = Fixture(Delivered)
        mansion.player.coords = CrompertyCoords
        mansion.talk(Cromperty)
        mansion.finish(listOf(2, 1, 1, 1))
        assertEquals(TribalTotemQuest.MansionLanding, mansion.player.coords)

        val trapped = Fixture(TrapFound)
        trapped.player.coords = CrompertyCoords
        trapped.talk(Cromperty)
        trapped.finish(listOf(2, 2, 1))
        assertEquals(TribalTotemQuest.MansionLanding, trapped.player.coords)

        for (stage in listOf(0, Complete)) {
            val none = Fixture(stage)
            none.player.coords = CrompertyCoords
            none.talk(Cromperty)
            none.finish(listOf(2, 2, 1))
            assertEquals(CrompertyCoords, none.player.coords, "stage $stage")
            assertTrue(none.output().contains("can't seem to get a signal"), none.output())
            assertTrue(none.output().contains("Oh well, never mind"), none.output())
        }
    }

    @Test
    fun `Cromperty's other options leave the player where they are`() {
        val leave = Fixture(Delivered)
        leave.player.coords = CrompertyCoords
        leave.talk(Cromperty)
        leave.finish(listOf(2, 2, 2))
        assertEquals(CrompertyCoords, leave.player.coords)
        assertTrue(leave.output().contains("As you wish."), leave.output())

        val jobs = Fixture(Delivered)
        jobs.talk(Cromperty)
        jobs.finish(listOf(1, 2))
        assertTrue(jobs.output().contains("Thanks for dropping by"), jobs.output())

        val clever = Fixture(Delivered)
        clever.talk(Cromperty)
        clever.finish(listOf(2, 3))
        assertTrue(clever.output().contains("feeling a little smug"), clever.output())

        val gpdt = Fixture(Delivered)
        gpdt.talk(Cromperty)
        gpdt.finish(listOf(2, 1, 2))
        assertTrue(gpdt.output().contains("Gielinor Parcel Delivery Team"), gpdt.output())
        assertEquals(Delivered, gpdt.stage())

        val essence = Fixture(Delivered)
        essence.player.coords = CrompertyCoords
        essence.talk(Cromperty)
        essence.finish(listOf(3))
        assertTrue(essence.player.coords.x in 2890..2930, essence.player.coords.toString())
        assertEquals(Delivered, essence.stage())

        val rightClick = Fixture()
        rightClick.player.coords = CrompertyCoords
        rightClick.talk(Cromperty, op = 3)
        rightClick.finish()
        assertTrue(rightClick.player.coords.x in 2890..2930, rightClick.player.coords.toString())
    }

    @Test
    fun `the combination lock needs the letters K U R T and keeps the dials`() {
        val f = Fixture(Delivered)
        f.loc(Combo, ComboCoords, shape = LocShape.WallStraight)
        f.finish()
        assertEquals(listOf(0, 0, 0, 0), f.dials())

        f.press("rdenter")
        assertTrue(f.output().contains("This combination is incorrect."), f.output())
        assertTrue(f.player.ui.containsModal("interface.rd_combolock"))

        f.dial("a", 10)
        f.dial("b", -6)
        f.dial("c", -9)
        assertEquals(listOf(10, 20, 17, 0), f.dials())
        f.press("rdenter")
        assertEquals(2, f.output().split("This combination is incorrect.").size - 1)

        f.dial("d", -7)
        assertEquals(listOf(10, 20, 17, 19), f.dials())
        f.press("rdenter")
        assertTrue(f.output().contains("The combination seems correct!"), f.output())
        assertFalse(f.player.ui.containsModal("interface.rd_combolock"))
    }

    @Test
    fun `the dials wrap around the alphabet and the wrong code stays locked`() {
        val f = Fixture(Delivered)
        f.loc(Combo, ComboCoords, shape = LocShape.WallStraight)
        f.finish()
        f.dial("a", -1)
        assertEquals(25, f.dials()[0])
        f.dial("a", 1)
        assertEquals(0, f.dials()[0])
        f.dial("b", 26)
        assertEquals(0, f.dials()[1])
        for ((letter, steps) in listOf("a" to 10, "b" to 20, "c" to 17, "d" to 18)) {
            f.dial(letter, steps)
        }
        f.press("rdenter")
        assertFalse(f.output().contains("seems correct"), f.output())

        f.loc(Combo, ComboCoords, shape = LocShape.WallStraight)
        f.finish()
        assertTrue(f.player.ui.containsModal("interface.rd_combolock"))
    }

    @Test
    fun `once unlocked the door opens without asking for the code again`() {
        val f = Fixture(Delivered)
        f.place(Combo, ComboCoords, LocShape.WallStraight, LocAngle.West)
        f.setDials(listOf(10, 20, 17, 19))
        f.loc(Combo, ComboCoords, shape = LocShape.WallStraight)
        f.finish()
        assertFalse(f.player.ui.containsModal("interface.rd_combolock"))
        val opened = "loc.poshdooropen".asRSCM(RSCMType.LOC)
        val translated = ComboCoords.translateX(-1)
        assertTrue(f.locs.findAll(translated).any { it.id == opened }, "the door did not open")
    }

    @Test
    fun `the front door is always locked`() {
        for (stage in listOf(0, Started, Delivered, Complete)) {
            val f = Fixture(stage)
            f.loc(Front, FrontCoords)
            f.finish()
            assertTrue(f.output().contains("This door is securely locked."), "stage $stage")
        }
    }

    @Test
    fun `climbing the stairs unwarned drops the player into the sewer`() {
        val f = Fixture(Delivered)
        f.loc(Stairs, StairsCoords)
        f.finish()
        assertEquals(TribalTotemQuest.SewerLanding, f.player.coords)
        assertTrue(f.output().contains("you hear a click..."), f.output())
        assertTrue(f.output().contains("You have fallen through a trap!"), f.output())
        assertEquals(Delivered, f.stage())
    }

    @Test
    fun `investigating the stairs needs thieving 21 and the trap stays known`() {
        val low = Fixture(Delivered)
        low.player.statMap.setCurrentLevel("stat.thieving", 20)
        low.loc(Stairs, StairsCoords, 2)
        low.finish()
        assertEquals(Delivered, low.stage())
        assertTrue(low.output().contains("You don't find anything interesting."), low.output())
        low.loc(Stairs, StairsCoords)
        low.finish()
        assertEquals(TribalTotemQuest.SewerLanding, low.player.coords)

        val boosted = Fixture(Delivered)
        boosted.player.statMap.setBaseLevel("stat.thieving", 19)
        boosted.player.statMap.setCurrentLevel("stat.thieving", 21)
        boosted.loc(Stairs, StairsCoords, 2)
        boosted.finish()
        assertEquals(TrapFound, boosted.stage())

        boosted.loc(Stairs, StairsCoords, 2)
        boosted.finish()
        assertEquals(TrapFound, boosted.stage())
        boosted.loc(Stairs, StairsCoords)
        boosted.finish()
        assertEquals(TribalTotemQuest.StairsTop, boosted.player.coords)
    }

    @Test
    fun `falling through the trap does not lose the code or the quest`() {
        val f = Fixture(Delivered)
        f.setDials(listOf(10, 20, 17, 19))
        f.loc(Stairs, StairsCoords)
        f.finish()
        assertEquals(listOf(10, 20, 17, 19), f.dials())
        assertEquals(Delivered, f.stage())
    }

    @Test
    fun `the chest gives one totem at a time during the quest`() {
        val f = Fixture(TrapFound)
        f.place(OpenChest, ChestCoords, LocShape.CentrepieceStraight, LocAngle.South)
        f.loc(OpenChest, ChestCoords)
        f.finish()
        assertEquals(1, f.count(Totem))
        f.loc(OpenChest, ChestCoords)
        f.finish()
        assertEquals(1, f.count(Totem))
        assertTrue(f.output().contains("The chest is empty."), f.output())

        f.player.inv[f.player.inv.indexOfFirst { it?.id == Totem.asRSCM() }] = null
        f.loc(OpenChest, ChestCoords)
        f.finish()
        assertEquals(1, f.count(Totem))
    }

    @Test
    fun `the chest is empty outside the quest and can be shut again`() {
        for (stage in listOf(0, Complete)) {
            val f = Fixture(stage)
            f.loc(OpenChest, ChestCoords)
            f.finish()
            assertEquals(0, f.count(Totem), "stage $stage")
            assertTrue(f.output().contains("The chest is empty."), "stage $stage")
        }
        val f = Fixture(TrapFound)
        f.place(ShutChest, ChestCoords, LocShape.CentrepieceStraight, LocAngle.South)
        f.loc(ShutChest, ChestCoords)
        f.finish()
        val open = "loc.totemopenchest".asRSCM(RSCMType.LOC)
        assertTrue(f.locs.findAll(ChestCoords).any { it.id == open })
        f.loc(OpenChest, ChestCoords, 2)
        f.finish()
        val shut = "loc.totemshutchest".asRSCM(RSCMType.LOC)
        assertTrue(f.locs.findAll(ChestCoords).any { it.id == shut })
    }

    @Test
    fun `Horacio chats about the garden and lets the combination slip during the quest`() {
        val f = Fixture(Started)
        f.talk(Horacio)
        f.finish(listOf(2, 1))
        assertTrue(f.output().contains("it's his middle name"), f.output())
        assertTrue(f.output().contains("Forget I mentioned it"), f.output())
        assertEquals(Started, f.stage())

        val help = Fixture()
        help.talk(Horacio)
        help.finish(listOf(2, 2))
        assertTrue(help.output().contains("muscle in on my job"), help.output())

        val nice = Fixture()
        nice.talk(Horacio)
        nice.finish(listOf(1))
        assertTrue(nice.output().contains("glad to be alive"), nice.output())

        val done = Fixture(Complete)
        done.talk(Horacio)
        done.finish(listOf(2))
        assertTrue(done.output().contains("Horacio Dobson"), done.output())
        assertFalse(done.output().contains("middle name"), done.output())
    }

    @Test
    fun `the journal follows every stage`() {
        val f = Fixture()
        val expected =
            mapOf(
                Started to "front door",
                LabelPlaced to "GPDT employee",
                Delivered to "Thieving level 21",
                TrapFound to "spotted the trap",
            )
        for ((stage, text) in expected) {
            f.stageTo(stage)
            assertTrue(f.journal().contains(text), "stage $stage: ${f.journal()}")
        }
        f.stageTo(Complete)
        assertTrue(f.quest.completedLog(f.access()).contains("five swordfish"))
    }

    @Test
    fun `the quest ends at the stage the cache expects`() {
        assertEquals(TribalTotemQuest.Complete, Fixture().quest.quest.maxSteps)
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("tribal-totem-test")
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
        val locs = LocRepository(clock, locRegistry, regions)
        private val objs = ObjRepository(clock, objRegistry)
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
                uuid = 861L
                observerUUID = 861L
                slotId = 1
                assignUid()
                coords = CoordGrid(2650, 3272, 0)
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

        val quest = TribalTotemQuest()

        private fun freeSlot(): Int = (0 until 28).first { player.inv[it] == null }

        init {
            for (level in 0..1) {
                for (x in 2624..2688 step 8) {
                    for (z in 3264..3328 step 8) collision.allocateIfAbsent(x, z, level)
                    for (z in 9664..9728 step 8) collision.allocateIfAbsent(x, z, level)
                }
                for (x in 2880..2944 step 8) {
                    for (z in 4800..4864 step 8) collision.allocateIfAbsent(x, z, level)
                }
                for (x in 2784..2800 step 8) {
                    for (z in 3176..3184 step 8) collision.allocateIfAbsent(x, z, level)
                }
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
            with(TribalTotemNpcs(quest, objs)) { scripts.startup() }
            with(WizardCromperty(quest)) { scripts.startup() }
            with(TribalTotemLocs(quest, locs, objs)) { scripts.startup() }
            with(HandelmortDoor(QuestDoors(locs))) { scripts.startup() }
            stageTo(stage)
            player.statMap.setBaseLevel("stat.thieving", 1)
        }

        fun stageTo(value: Int) {
            VarPlayerIntMapSetter.set(player, "varbit.tribal_totem_progress", value)
        }

        fun stage() = quest.stage(player)

        fun access() = ProtectedAccess(player, coroutine, context)

        fun journal() = quest.questLog(access())

        fun count(obj: String) = player.inv.count(obj)

        fun onFloor(obj: String): Int =
            objRegistry.findAll(player.coords).filter { it.type == obj.asRSCM() }.sumOf { it.count }

        fun give(obj: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            if (type.stackable) {
                player.inv[freeSlot()] = InvObj(obj, count)
            } else {
                repeat(count) { player.inv[freeSlot()] = InvObj(obj, 1) }
            }
        }

        fun fillInventory() {
            for (slot in 0 until 28) {
                if (player.inv[slot] == null) {
                    player.inv[slot] = InvObj("obj.logs", 1)
                }
            }
        }

        fun dials(): List<Int> =
            (1..4).map { player.vars["varbit.totemquest_combodoor_code$it"] }

        fun setDials(values: List<Int>) {
            for ((index, value) in values.withIndex()) {
                VarPlayerIntMapSetter.set(
                    player,
                    "varbit.totemquest_combodoor_code${index + 1}",
                    value,
                )
            }
        }

        fun dial(letter: String, steps: Int) {
            val arrow = if (steps < 0) "rd${letter}_left" else "rd${letter}_right"
            repeat(Math.abs(steps)) { press(arrow) }
        }

        fun press(component: String) {
            val id = "component.rd_combolock:$component".asRSCM(RSCMType.COMPONENT)
            val type = ServerCacheManager.fromComponent(id)
            run(clear = false) {
                val event = IfModalButton(type, comsub = -1, obj = null, op = IfButtonOp.Op1)
                assertTrue(events.publish(this, event))
            }
        }

        fun talk(symbol: String, op: Int = 1) {
            val npc = Npc(symbol, player.coords.translateZ(1))
            run {
                val event = if (op == 3) NpcEvents.Op3(npc) else NpcEvents.Op1(npc)
                assertTrue(events.publish(this, event))
            }
        }

        fun use(npc: String, obj: String) {
            val target = Npc(npc, player.coords.translateZ(1))
            val id = obj.asRSCM()
            val slot = (0 until 28).first { player.inv[it]?.id == id }
            val type = checkNotNull(ServerCacheManager.getItem(id))
            val npcType = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM()))
            run {
                val event = NpcUDefaultEvents.OpType(target, slot, type, npcType)
                assertTrue(events.publish(this, event))
            }
        }

        private fun boundLoc(
            symbol: String,
            coords: CoordGrid,
            shape: LocShape,
            angle: LocAngle,
        ): Pair<BoundLocInfo, Any> {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val loc =
                BoundLocInfo(
                    LocInfo(
                        if (shape == LocShape.WallStraight) 0 else 2,
                        coords,
                        LocEntity(type.id, shape.id, angle.id),
                    ),
                    type,
                )
            return loc to type
        }

        fun place(symbol: String, coords: CoordGrid, shape: LocShape, angle: LocAngle) {
            locs.add(coords, symbol, 1000, angle, shape)
        }

        fun loc(
            symbol: String,
            coords: CoordGrid,
            slot: Int = 1,
            shape: LocShape = LocShape.CentrepieceStraight,
        ) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val (loc, _) = boundLoc(symbol, coords, shape, LocAngle.West)
            run {
                val event =
                    if (slot == 2) LocEvents.Op2(loc, loc, type) else LocEvents.Op1(loc, loc, type)
                assertTrue(events.publish(this, event))
            }
        }

        fun useOnLoc(symbol: String, coords: CoordGrid, obj: String) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val item = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            val (loc, _) =
                boundLoc(symbol, coords, LocShape.CentrepieceStraight, LocAngle.West)
            val slot = (0 until 28).first { player.inv[it]?.id == item.id }
            run {
                val specific = LocUEvents.Op(loc, loc, type, item, slot)
                if (!events.publish(this, specific)) {
                    val default = LocUDefaultEvents.OpType(loc, loc, type, item, slot)
                    assertTrue(events.publish(this, default))
                }
            }
        }

        fun run(clear: Boolean = true, block: suspend ProtectedAccess.() -> Unit) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            if (clear) player.clearPendingAction(events)
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

        fun finish(options: List<Int> = emptyList()) {
            val selections = options.iterator()
            repeat(400) {
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

        fun assertRewards() {
            assertEquals(Complete, stage())
            assertEquals(Complete, player.vars["varp.totemquest"])
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(1775, player.statMap.getXP("stat.thieving"))
            assertEquals(5, count(Swordfish))
            assertEquals(0, count(Totem))
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
        private const val Kangai = "npc.kangai_mau"
        private const val Horacio = "npc.horacio"
        private const val Employee = "npc.rpdt_employee"
        private const val Cromperty = "npc.cromperty_pre_diary"

        private const val Totem = "obj.tribal_totem"
        private const val Label = "obj.tribal_totem_label"
        private const val Swordfish = "obj.swordfish"

        private const val LabelCrate = "loc.horncrate"
        private const val BlockCrate = "loc.teleportcrate"
        private const val Front = "loc.tribaltotemdoor"
        private const val Combo = "loc.combodoor"
        private const val Stairs = "loc.totemtrapstairs"
        private const val ShutChest = "loc.totemshutchest"
        private const val OpenChest = "loc.totemopenchest"

        private val LabelCrateCoords = CoordGrid(2650, 3273, 0)
        private val BlockCrateCoords = CoordGrid(2650, 3271, 0)
        private val FrontCoords = CoordGrid(2635, 3321, 0)
        private val ComboCoords = CoordGrid(2634, 3323, 0)
        private val StairsCoords = CoordGrid(2631, 3322, 0)
        private val ChestCoords = CoordGrid(2638, 3324, 1)
        private val CrompertyCoords = CoordGrid(2685, 3324, 0)

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
