package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUDefaultEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_CANNON_FIXED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_FIND_GILOB
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_FIND_LOLLK
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_HAVE_MOULD
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_LOLLK_RESCUED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_RAILINGS_FIXED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_REPAIR_CANNON
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_SEE_NULODION
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.CannonOrnamentKit
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.CannonRestrictions
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.CannonStyle
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.MulticannonScript
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon.Multicannons
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.npcs.BlackGuardSentries
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.npcs.CaptainLawgof
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.npcs.Nulodion
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
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
import org.rsmod.game.entity.util.EntityFaceAngle
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
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DwarfCannonInteractionTest {

    /* Captain Lawgof */

    @Test
    fun `accepting Lawgof's offer starts the quest and hands out six railings and a hammer`() {
        val f = Fixture()
        f.talk(Lawgof)
        f.finish(listOf(1))
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(STAGE_STARTED, f.player.vars["varp.mcannon"])
        assertEquals(6, f.count(Railing))
        assertEquals(1, f.count(Hammer))
    }

    @Test
    fun `a player who already owns a hammer is not given another`() {
        val f = Fixture()
        f.give(Hammer)
        f.talk(Lawgof)
        f.finish(listOf(1))
        assertEquals(1, f.count(Hammer))
        assertEquals(6, f.count(Railing))
    }

    @Test
    fun `declining Lawgof's offer leaves the quest unstarted`() {
        val f = Fixture()
        f.talk(Lawgof)
        f.finish(listOf(2))
        assertEquals(0, f.stage())
        assertEquals(0, f.player.vars["varp.mcannon"])
        assertEquals(0, f.count(Railing))
        assertEquals(0, f.count(Hammer))
    }

    @Test
    fun `starting the quest clears railing flags left behind by an earlier reset`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.mcannon_railing3_fixed", 1)
        f.talk(Lawgof)
        f.finish(listOf(1))
        assertEquals(0, f.player.vars["varbit.mcannon_railing3_fixed"])
    }

    @Test
    fun `Lawgof replaces lost railings while the stockade is broken`() {
        val f = Fixture(STAGE_STARTED)
        f.talk(Lawgof)
        f.finish()
        assertEquals(1, f.count(Railing))
        f.talk(Lawgof)
        f.finish()
        assertEquals(1, f.count(Railing))
    }

    @Test
    fun `Lawgof will not hand out a railing to a full inventory`() {
        val f = Fixture(STAGE_STARTED)
        f.fill()
        f.talk(Lawgof)
        f.finish()
        assertEquals(0, f.count(Railing))
        assertTrue(f.output().contains("no room"), f.output())
    }

    @Test
    fun `the watchtower mission follows the repaired stockade`() {
        val f = Fixture(STAGE_RAILINGS_FIXED)
        f.talk(Lawgof)
        f.finish()
        assertEquals(STAGE_FIND_GILOB, f.stage())
    }

    @Test
    fun `Lawgof takes Gilob's remains and moves the quest on once`() {
        val f = Fixture(STAGE_FIND_GILOB)
        f.give(Remains)
        f.talk(Lawgof)
        f.finish()
        assertEquals(STAGE_FIND_LOLLK, f.stage())
        assertEquals(0, f.count(Remains))
        f.talk(Lawgof)
        f.finish()
        assertEquals(STAGE_FIND_LOLLK, f.stage())
    }

    @Test
    fun `without the remains Lawgof just asks for news`() {
        val f = Fixture(STAGE_FIND_GILOB)
        f.talk(Lawgof)
        f.finish()
        assertEquals(STAGE_FIND_GILOB, f.stage())
    }

    @Test
    fun `accepting the cannon favour hands over the toolkit`() {
        val f = Fixture(STAGE_LOLLK_RESCUED)
        f.talk(Lawgof)
        f.finish(listOf(1))
        assertEquals(STAGE_REPAIR_CANNON, f.stage())
        assertEquals(1, f.count(Toolkit))
    }

    @Test
    fun `declining the cannon favour keeps the stage and gives nothing`() {
        val f = Fixture(STAGE_LOLLK_RESCUED)
        f.talk(Lawgof)
        f.finish(listOf(2))
        assertEquals(STAGE_LOLLK_RESCUED, f.stage())
        assertEquals(0, f.count(Toolkit))
    }

    @Test
    fun `a full inventory delays the toolkit without losing the stage`() {
        val f = Fixture(STAGE_LOLLK_RESCUED)
        f.fill()
        f.talk(Lawgof)
        f.finish(listOf(1))
        assertEquals(STAGE_LOLLK_RESCUED, f.stage())
        assertEquals(0, f.count(Toolkit))
    }

    @Test
    fun `a lost toolkit is replaced`() {
        val f = Fixture(STAGE_REPAIR_CANNON)
        f.talk(Lawgof)
        f.finish()
        assertEquals(1, f.count(Toolkit))
        f.talk(Lawgof)
        f.finish()
        assertEquals(1, f.count(Toolkit))
    }

    @Test
    fun `the fixed cannon sends the player to Nulodion and takes the toolkit back`() {
        val f = Fixture(STAGE_CANNON_FIXED)
        f.give(Toolkit)
        f.talk(Lawgof)
        f.finish(listOf(2))
        assertEquals(STAGE_SEE_NULODION, f.stage())
        assertEquals(0, f.count(Toolkit))
    }

    @Test
    fun `refusing the errand keeps the fixed cannon stage`() {
        val f = Fixture(STAGE_CANNON_FIXED)
        f.talk(Lawgof)
        f.finish(listOf(1))
        assertEquals(STAGE_CANNON_FIXED, f.stage())
    }

    @Test
    fun `Lawgof asks about Nulodion until the engineer has been seen`() {
        val f = Fixture(STAGE_SEE_NULODION)
        f.talk(Lawgof)
        f.finish()
        assertEquals(STAGE_SEE_NULODION, f.stage())
        assertEquals(0, f.player.vars["varp.qp"])
    }

    /* The railings */

    @Test
    fun `the first railing needs a railing in the pack`() {
        val f = Fixture(STAGE_STARTED)
        f.loc(Railings[0], RailingTiles[0])
        f.finish()
        assertEquals(0, f.player.vars["varbit.mcannon_railing1_fixed"])
        assertEquals(STAGE_STARTED, f.stage())
    }

    @Test
    fun `a railing needs a hammer`() {
        val f = Fixture(STAGE_STARTED)
        f.give(Railing)
        f.loc(Railings[0], RailingTiles[0])
        f.finish()
        assertEquals(0, f.player.vars["varbit.mcannon_railing1_fixed"])
        assertEquals(1, f.count(Railing))
        assertTrue(f.output().contains("hammer"), f.output())
    }

    @Test
    fun `each railing is mended on a crafting roll and only costs a railing when it holds`() {
        val f = Fixture(STAGE_STARTED)
        f.give(Hammer)
        f.give(Railing, 6)
        f.player.statMap.setBaseLevel("stat.crafting", 99)
        f.player.statMap.setCurrentLevel("stat.crafting", 99)
        for ((index, tile) in RailingTiles.withIndex()) {
            var attempts = 0
            while (f.player.vars["varbit.mcannon_railing${index + 1}_fixed"] == 0) {
                f.player.statMap.setCurrentLevel("stat.hitpoints", 10)
                f.player.statMap.setCurrentLevel("stat.crafting", 99)
                f.player.statMap.setCurrentLevel("stat.strength", 99)
                f.loc(Railings[index], tile)
                f.finish()
                assertTrue(++attempts < 200, "railing ${index + 1} never held")
            }
            assertEquals(5 - index, f.count(Railing), "railing ${index + 1} was not consumed once")
        }
        assertEquals(STAGE_RAILINGS_FIXED, f.stage())
        assertEquals(0, f.count(Railing))
        assertEquals(1, f.count(Hammer))
    }

    @Test
    fun `using a hammer on a broken railing only looks like a repair`() {
        val f = Fixture(STAGE_STARTED)
        f.give(Hammer)
        f.useOnLoc(Railings[0], RailingTiles[0], Hammer)
        f.finish()
        assertEquals(0, f.player.vars["varbit.mcannon_railing1_fixed"])
        assertEquals(STAGE_STARTED, f.stage())
    }

    /* Gilob's remains and the goblin cave */

    @Test
    fun `the remains can only be taken at the watchtower stage and only once`() {
        val early = Fixture(STAGE_REPAIR_CANNON)
        early.loc(RemainsLoc, CoordGrid(2567, 3444, 2), LocShape.CentrepieceStraight)
        early.finish()
        assertEquals(0, early.count(Remains))

        val f = Fixture(STAGE_FIND_GILOB)
        f.loc(RemainsLoc, CoordGrid(2567, 3444, 2), LocShape.CentrepieceStraight)
        f.finish()
        assertEquals(1, f.count(Remains))
        f.loc(RemainsLoc, CoordGrid(2567, 3444, 2), LocShape.CentrepieceStraight)
        f.finish()
        assertEquals(1, f.count(Remains))
    }

    @Test
    fun `a full pack still keeps the remains on the ground`() {
        val f = Fixture(STAGE_FIND_GILOB)
        f.fill()
        f.loc(RemainsLoc, CoordGrid(2567, 3444, 2), LocShape.CentrepieceStraight)
        f.finish()
        assertEquals(STAGE_FIND_GILOB, f.stage())
        assertEquals(0, f.count(Remains))
    }

    @Test
    fun `the crate holds nothing until the remains are delivered`() {
        for (stage in listOf(0, STAGE_STARTED, STAGE_FIND_GILOB)) {
            val f = Fixture(stage)
            f.loc(LollkCrate, CoordGrid(2571, 9850, 0), LocShape.CentrepieceStraight)
            f.finish()
            assertEquals(stage, f.stage(), "stage $stage")
            assertTrue(f.output().contains("find nothing"), f.output())
        }
    }

    @Test
    fun `searching the right crate frees Lollk once`() {
        val f = Fixture(STAGE_FIND_LOLLK)
        f.loc(LollkCrate, CoordGrid(2571, 9850, 0), LocShape.CentrepieceStraight)
        f.finish()
        assertEquals(STAGE_LOLLK_RESCUED, f.stage())
        assertTrue(f.output().contains("tied up"), f.output())
        f.loc(LollkCrate, CoordGrid(2571, 9850, 0), LocShape.CentrepieceStraight)
        f.finish()
        assertEquals(STAGE_LOLLK_RESCUED, f.stage())
    }

    @Test
    fun `any other crate in the store is empty`() {
        val f = Fixture(STAGE_FIND_LOLLK)
        f.loc("loc.mcannon_crates", CoordGrid(2570, 9848, 0), LocShape.CentrepieceStraight)
        f.finish()
        assertEquals(STAGE_FIND_LOLLK, f.stage())
        assertTrue(f.output().contains("find nothing"), f.output())
    }

    /* The repair puzzle */

    @Test
    fun `the toolkit does nothing on the cannon before it is handed over`() {
        val f = Fixture(STAGE_LOLLK_RESCUED)
        f.give(Toolkit)
        f.useOnLoc(CampCannon, CoordGrid(2562, 3461, 0), Toolkit, LocShape.CentrepieceStraight)
        f.finish()
        assertFalse(f.player.ui.containsModal("interface.mcannon_interface"))
    }

    @Test
    fun `the toolkit opens the repair interface at the repair stage`() {
        val f = Fixture(STAGE_REPAIR_CANNON)
        f.give(Toolkit)
        f.openRepair()
        assertTrue(f.player.ui.containsModal("interface.mcannon_interface"))
    }

    @Test
    fun `the wrong tool on a part is refused and deselected`() {
        val f = Fixture(STAGE_REPAIR_CANNON)
        f.give(Toolkit)
        f.openRepair()
        f.press("mcannon_tool1")
        assertEquals(1, f.player.vars["varbit.mcannonmulti_tool1"])
        f.press("mcannon_safety")
        assertEquals(0, f.player.vars["varbit.mcannon_safety_on"])
        assertEquals(0, f.player.vars["varbit.mcannonmulti_tool1"])
        assertEquals(STAGE_REPAIR_CANNON, f.stage())
    }

    @Test
    fun `testing the gear early does not repair the cannon`() {
        val f = Fixture(STAGE_REPAIR_CANNON)
        f.give(Toolkit)
        f.openRepair()
        f.press("mcannon_tool1")
        f.press("mcannon_gear")
        assertEquals(STAGE_REPAIR_CANNON, f.stage())
    }

    @Test
    fun `the safety switch and the spring in either order then the gear repair the cannon`() {
        for (pliersFirst in listOf(true, false)) {
            val f = Fixture(STAGE_REPAIR_CANNON)
            f.give(Toolkit)
            f.openRepair()
            val steps =
                listOf(
                    "mcannon_tool2" to "mcannon_safety",
                    "mcannon_tool3" to "mcannon_spring",
                )
            for ((tool, part) in if (pliersFirst) steps else steps.reversed()) {
                f.press(tool)
                f.press(part)
            }
            assertEquals(1, f.player.vars["varbit.mcannon_safety_on"])
            assertEquals(1, f.player.vars["varbit.mcannon_spring_set"])
            assertEquals(STAGE_REPAIR_CANNON, f.stage())
            f.press("mcannon_tool1")
            f.press("mcannon_gear")
            assertEquals(STAGE_CANNON_FIXED, f.stage())
        }
    }

    @Test
    fun `the camp cannon cannot be moved`() {
        val f = Fixture(STAGE_CANNON_FIXED)
        f.loc(CampCannon, CoordGrid(2562, 3461, 0), LocShape.CentrepieceStraight, op = 2)
        f.finish()
        assertEquals(STAGE_CANNON_FIXED, f.stage())
        assertTrue(f.output().contains("move that cannon"), f.output())
    }

    /* Nulodion */

    @Test
    fun `Nulodion hands over the notes and the mould`() {
        val f = Fixture(STAGE_SEE_NULODION)
        f.talk(Nulodion)
        f.finish()
        assertEquals(STAGE_HAVE_MOULD, f.stage())
        assertEquals(1, f.count(Notes))
        assertEquals(1, f.count(Mould))
    }

    @Test
    fun `Nulodion needs room for both items before giving anything`() {
        val f = Fixture(STAGE_SEE_NULODION)
        f.fill()
        f.talk(Nulodion)
        f.finish()
        assertEquals(STAGE_SEE_NULODION, f.stage())
        assertEquals(0, f.count(Notes))
        assertEquals(0, f.count(Mould))
    }

    @Test
    fun `Nulodion replaces a lost mould or lost notes`() {
        val f = Fixture(STAGE_HAVE_MOULD)
        f.give(Mould)
        f.talk(Nulodion)
        f.finish()
        assertEquals(1, f.count(Notes))
        assertEquals(1, f.count(Mould))
        val g = Fixture(STAGE_HAVE_MOULD)
        g.give(Notes)
        g.talk(Nulodion)
        g.finish()
        assertEquals(1, g.count(Notes))
        assertEquals(1, g.count(Mould))
    }

    @Test
    fun `Nulodion does not hand out duplicates`() {
        val f = Fixture(STAGE_HAVE_MOULD)
        f.give(Notes)
        f.give(Mould)
        f.talk(Nulodion)
        f.finish()
        assertEquals(1, f.count(Notes))
        assertEquals(1, f.count(Mould))
    }

    @Test
    fun `the parts shop is closed to players who have not finished the quest`() {
        val f = Fixture(STAGE_HAVE_MOULD)
        f.talk(Nulodion, op = 3)
        f.finish()
        assertTrue(f.output().contains("top secret"), f.output())
    }

    /* Completion */

    @Test
    fun `handing over the mould and the notes completes the quest once`() {
        val f = Fixture(STAGE_HAVE_MOULD)
        f.give(Mould)
        f.give(Notes)
        f.talk(Lawgof)
        f.finish()
        f.assertComplete()
        f.talk(Lawgof)
        f.finish()
        f.assertComplete(scroll = false)
    }

    @Test
    fun `Lawgof will not complete the quest without both items`() {
        for (missing in listOf(Mould, Notes)) {
            val f = Fixture(STAGE_HAVE_MOULD)
            f.give(if (missing == Mould) Notes else Mould)
            f.talk(Lawgof)
            f.finish()
            assertEquals(STAGE_HAVE_MOULD, f.stage(), missing)
            assertEquals(0, f.player.vars["varp.qp"], missing)
            assertEquals(1, f.count(if (missing == Mould) Notes else Mould), missing)
        }
    }

    @Test
    fun `the reward is not repeated after the quest is done`() {
        val f = Fixture(STAGE_COMPLETE)
        f.talk(Lawgof)
        f.finish()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.player.vars["varp.qp"])
        assertEquals(0, f.player.statMap.getXP("stat.crafting"))
        assertTrue(f.output().contains("blasting"), f.output())
    }

    @Test
    fun `the journal follows the stage`() {
        val f = Fixture(STAGE_STARTED)
        assertTrue(f.journal().contains("railings"), f.journal())
        f.stage(STAGE_REPAIR_CANNON)
        assertTrue(f.journal().contains("toolkit"), f.journal())
        f.stage(STAGE_COMPLETE)
        val done = f.quest.completedLog(f.access())
        assertTrue(done.contains("honorary member"), done)
    }

    /* Guards */

    @Test
    fun `the dwarf guards talk before and after the quest`() {
        for (stage in listOf(0, STAGE_COMPLETE)) {
            val f = Fixture(stage)
            f.talk("npc.mcannonguard1")
            f.finish()
            assertEquals(stage, f.stage())
            assertTrue(f.output().contains("Goblins"), f.output())
        }
    }

    /* The multicannon */

    @Test
    fun `the cannon cannot be set up before the quest is done`() {
        val f = Fixture(STAGE_HAVE_MOULD)
        f.give(CannonBase)
        f.heldOp1(CannonBase)
        f.finish()
        assertEquals(0, f.player.vars["varp.dropcannon"])
        assertEquals(1, f.count(CannonBase))
        assertTrue(f.output().contains("complete the Dwarf Cannon quest"), f.output())
    }

    @Test
    fun `setting up the base alone places a base and consumes the base`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(CannonBase)
        f.heldOp1(CannonBase)
        f.finish()
        assertEquals(Multicannons.STAGE_BASE, f.player.vars["varp.dropcannon"])
        assertEquals(0, f.count(CannonBase))
        assertNotNull(f.cannons.of(f.player))
        assertTrue(f.player.vars["varp.ownedmcannon"] > 0)
    }

    @Test
    fun `a full set assembles in order and a part can be added to a half-built cannon`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(CannonBase)
        f.give(CannonStand)
        f.heldOp1(CannonBase)
        f.finish()
        assertEquals(Multicannons.STAGE_STAND, f.player.vars["varp.dropcannon"])
        f.give(CannonBarrels)
        f.give(CannonFurnace)
        val cannon = checkNotNull(f.cannons.of(f.player))
        f.useOnCannonStage(cannon, CannonStyle.Normal.stageLocs[1], CannonBarrels)
        f.finish()
        assertEquals(Multicannons.STAGE_BARRELS, f.player.vars["varp.dropcannon"])
        f.useOnCannonStage(cannon, CannonStyle.Normal.stageLocs[2], CannonFurnace)
        f.finish()
        assertEquals(Multicannons.STAGE_FULL, f.player.vars["varp.dropcannon"])
        assertEquals(0, f.count(CannonFurnace))
    }

    @Test
    fun `only one cannon can be out at a time`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(CannonBase)
        f.heldOp1(CannonBase)
        f.finish()
        f.give(CannonBase)
        f.heldOp1(CannonBase)
        f.finish()
        assertEquals(1, f.count(CannonBase))
        assertTrue(f.output().contains("more than one Cannon"), f.output())
    }

    @Test
    fun `a full cannon is loaded fired up and picked up with its balls`() {
        val f = Fixture(STAGE_COMPLETE)
        f.giveSet()
        f.heldOp1(CannonBase)
        f.finish()
        assertEquals(Multicannons.STAGE_FULL, f.player.vars["varp.dropcannon"])
        f.give(CannonBall, 40)
        val cannon = checkNotNull(f.cannons.of(f.player))
        f.cannonLoc(cannon, op = 4, amount = 100)
        f.finish()
        assertEquals(30, f.player.vars["varp.rockthrower"])
        assertEquals(10, f.count(CannonBall))
        f.cannonLoc(cannon, op = 3)
        f.finish()
        assertEquals(0, f.player.vars["varp.rockthrower"])
        assertEquals(40, f.count(CannonBall))
        f.cannonLoc(cannon, op = 4, amount = 5)
        f.finish()
        assertEquals(5, f.player.vars["varp.rockthrower"])
        f.cannonLoc(cannon, op = 2)
        f.finish()
        assertEquals(0, f.player.vars["varp.dropcannon"])
        assertEquals(0, f.player.vars["varp.rockthrower"])
        assertNull(f.cannons.of(f.player))
        assertEquals(1, f.count(CannonBase))
        assertEquals(1, f.count(CannonStand))
        assertEquals(1, f.count(CannonBarrels))
        assertEquals(1, f.count(CannonFurnace))
        assertEquals(40, f.count(CannonBall))
    }

    @Test
    fun `a granite ball is never mixed with steel`() {
        val f = Fixture(STAGE_COMPLETE)
        f.giveSet()
        f.heldOp1(CannonBase)
        f.finish()
        val cannon = checkNotNull(f.cannons.of(f.player))
        f.give(GraniteBall, 10)
        f.give(CannonBall, 10)
        f.cannonLoc(cannon, op = 4, amount = 10)
        f.finish()
        assertEquals(10, f.player.vars["varp.rockthrower"])
        assertEquals(10, f.count(CannonBall))
        assertEquals(0, f.count(GraniteBall))
        f.cannonLoc(cannon, op = 4, amount = 10)
        f.finish()
        assertEquals(10, f.player.vars["varp.rockthrower"])
        assertTrue(f.output().contains("mix"), f.output())
    }

    @Test
    fun `the combat achievement tiers raise the cannon's capacity`() {
        for ((varbit, capacity) in
            mapOf(
                null to 30,
                "varbit.ca_tier_status_medium" to 35,
                "varbit.ca_tier_status_hard" to 45,
                "varbit.ca_tier_status_elite" to 60,
            )) {
            val f = Fixture(STAGE_COMPLETE)
            if (varbit != null) VarPlayerIntMapSetter.set(f.player, varbit, 1)
            f.giveSet()
            f.heldOp1(CannonBase)
            f.finish()
            f.give(CannonBall, 100)
            val cannon = checkNotNull(f.cannons.of(f.player))
            f.cannonLoc(cannon, op = 4, amount = 100)
            f.finish()
            assertEquals(capacity, f.player.vars["varp.rockthrower"], "$varbit")
        }
    }

    @Test
    fun `another player's cannon cannot be picked up or loaded`() {
        val f = Fixture(STAGE_COMPLETE)
        f.giveSet()
        f.heldOp1(CannonBase)
        f.finish()
        val cannon = checkNotNull(f.cannons.of(f.player))
        val thief = Fixture(STAGE_COMPLETE, uuid = 77L)
        thief.give(CannonBall, 5)
        thief.cannonLoc(cannon, op = 4, amount = 5, shared = f)
        thief.finish()
        assertEquals(0, f.player.vars["varp.rockthrower"])
        assertEquals(5, thief.count(CannonBall))
        thief.cannonLoc(cannon, op = 2, shared = f)
        thief.finish()
        assertEquals(Multicannons.STAGE_FULL, f.player.vars["varp.dropcannon"])
        assertNotNull(f.cannons.of(f.player))
    }

    @Test
    fun `picking up needs room for every part`() {
        val f = Fixture(STAGE_COMPLETE)
        f.giveSet()
        f.heldOp1(CannonBase)
        f.finish()
        val cannon = checkNotNull(f.cannons.of(f.player))
        f.fill()
        f.cannonLoc(cannon, op = 2)
        f.finish()
        assertEquals(Multicannons.STAGE_FULL, f.player.vars["varp.dropcannon"])
        assertNotNull(f.cannons.of(f.player))
        assertTrue(f.output().contains("free inventory"), f.output())
    }

    @Test
    fun `no cannon may be set up in a prohibited area`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(CannonBase)
        f.player.coords = CoordGrid(3165, 3480, 0)
        f.heldOp1(CannonBase)
        f.finish()
        assertEquals(0, f.player.vars["varp.dropcannon"])
        assertEquals(1, f.count(CannonBase))
        assertTrue(f.output().contains("Grand Exchange staff"), f.output())
    }

    @Test
    fun `restrictions name the place the cannon was refused in`() {
        val restrictions = CannonRestrictions(AreaChecker(Fixture.regionsFor(CollisionFlagMap()), AreaIndex()))
        assertEquals(
            "The dwarves won't be happy if you set up a cannon here.",
            restrictions.message(CoordGrid(3010, 3470, 0)),
        )
        assertEquals(
            "The TzHaar won't be happy if you set up a cannon here.",
            restrictions.message(CoordGrid(2400, 5100, 0)),
        )
        assertNull(restrictions.message(CoordGrid(2580, 3420, 0)))
    }

    @Test
    fun `Nulodion replaces a lost cannon in kind and rejects a false claim`() {
        val f = Fixture(STAGE_COMPLETE)
        f.talk(Nulodion)
        f.finish(listOf(2))
        assertEquals(0, f.count(CannonBase))
        assertTrue(f.output().contains("stolen in action"), f.output())

        VarPlayerIntMapSetter.set(f.player, "varp.dropcannon", Multicannons.STAGE_FULL)
        VarPlayerIntMapSetter.set(f.player, "varbit.mcannon_decayed", 1)
        VarPlayerIntMapSetter.set(f.player, "varp.rockthrower", 12)
        f.talk(Nulodion)
        f.finish(listOf(2))
        assertEquals(1, f.count(CannonBase))
        assertEquals(1, f.count(CannonStand))
        assertEquals(1, f.count(CannonBarrels))
        assertEquals(1, f.count(CannonFurnace))
        assertEquals(0, f.player.vars["varp.dropcannon"])
        assertEquals(0, f.player.vars["varp.rockthrower"])
        assertEquals(0, f.player.vars["varbit.mcannon_decayed"])
    }

    @Test
    fun `Nulodion sells the full set for 750000 coins`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give("obj.coins", 750_000)
        f.talk(Nulodion)
        f.finish(listOf(1, 1))
        assertEquals(0, f.count("obj.coins"))
        for (part in listOf(CannonBase, CannonStand, CannonBarrels, CannonFurnace, Mould, Manual)) {
            assertEquals(1, f.count(part), part)
        }
    }

    @Test
    fun `Nulodion will not sell the set without the coins or the room`() {
        val poor = Fixture(STAGE_COMPLETE)
        poor.give("obj.coins", 749_999)
        poor.talk(Nulodion)
        poor.finish(listOf(1, 1))
        assertEquals(0, poor.count(CannonBase))
        assertEquals(749_999, poor.count("obj.coins"))

        val full = Fixture(STAGE_COMPLETE)
        full.give("obj.coins", 750_000)
        full.fill()
        full.talk(Nulodion)
        full.finish(listOf(1, 1))
        assertEquals(0, full.count(CannonBase))
        assertEquals(750_000, full.count("obj.coins"))
    }

    @Test
    fun `the ornament kit swaps a part for its Shattered twin and back`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(CannonBase)
        f.give(Kit)
        f.useHeldOn(Kit, CannonBase)
        f.finish(listOf(1))
        assertEquals(0, f.count(CannonBase))
        assertEquals(0, f.count(Kit))
        assertEquals(1, f.count(OrnateBase))
        f.heldOp(OrnateBase, 4)
        assertEquals(1, f.count(CannonBase))
        assertEquals(1, f.count(Kit))
        assertEquals(0, f.count(OrnateBase))
    }

    @Test
    fun `an ornamented set is remembered so a lost cannon comes back ornamented`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(OrnateBase)
        f.heldOp1(OrnateBase)
        f.finish()
        assertEquals(1, f.player.vars["varbit.dwarf_cannon_ornate"])
        assertEquals(CannonStyle.Ornate, f.cannons.of(f.player)!!.style)
    }

    /* Fixture */

    private class Fixture(
        stage: Int = 0,
        uuid: Long = 906L,
        shared: Fixture? = null,
    ) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("dwarf-cannon-test")
        private var result: Result<Unit>? = null
        private val collision: CollisionFlagMap = shared?.collision ?: CollisionFlagMap()
        private val clock = MapClock(100)
        private val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val npcList = NpcList()
        private val playerList: PlayerList = shared?.playerList ?: PlayerList()
        private val npcRegistry = NpcRegistry(npcList, collision, events)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions: RegionRegistry = regionsFor(collision, npcRegistry, normal, storage, clock)
        private val locRegistry =
            LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
        private val locs: LocRepository = shared?.locs ?: LocRepository(clock, locRegistry, regions)
        private val npcRepo = NpcRepository(clock, npcRegistry, npcList)
        private val objs = ObjRepository(clock, ObjRegistry(updates))
        private val worldQueues = WorldQueueList()
        val cannons: Multicannons = shared?.cannons ?: Multicannons()
        private val movement =
            PlayerMovementProcessor(collision, RouteFactory(collision), StepFactory(collision), events)
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                    getCollision = { collision },
                    getNpcList = { npcList },
                    getPlayerList = { playerList },
                    getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
                    getAreaChecker = { AreaChecker(regions, AreaIndex()) },
                    getRandom = { DefaultGameRandom(7) },
                    getHitModifier = { PlayerHitModifier { _ -> } },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                this.uuid = uuid
                observerUUID = uuid
                accountHash = uuid
                slotId = if (shared == null) 1 else 2
                assignUid()
                coords = Start
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

        val quest = DwarfCannonQuest()
        private var nextSlot = 0

        init {
            for ((x0, z0) in
                listOf(
                    2552 to 3392,
                    2552 to 3440,
                    2552 to 3488,
                    2560 to 9792,
                    2560 to 9840,
                    3000 to 3440,
                    3136 to 3456,
                    2368 to 5056,
                )) {
                for (level in 0..2) for (x in x0..x0 + 64 step 8) for (z in z0..z0 + 56 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            val random = DefaultGameRandom(7)
            val restrictions = CannonRestrictions(AreaChecker(regions, AreaIndex()))
            with(quest) { scripts.startup() }
            with(CaptainLawgof(quest, objs, random)) { scripts.startup() }
            with(BlackGuardSentries(quest)) { scripts.startup() }
            with(Nulodion(quest, Shops(events), objs, cannons)) { scripts.startup() }
            with(BlackGuardRailings(quest)) { scripts.startup() }
            with(GilobsWatchtower(quest, objs)) { scripts.startup() }
            with(GoblinCave(quest, npcRepo, RouteFactory(collision))) { scripts.startup() }
            with(CannonRepair(quest, worldQueues, playerList, events)) { scripts.startup() }
            with(MulticannonScript(cannons, locs, collision, playerList, restrictions)) {
                scripts.startup()
            }
            with(CannonOrnamentKit()) { scripts.startup() }
            if (shared == null) playerList[player.slotId] = player else playerList[player.slotId] = player
            stage(stage)
        }

        fun stage() = quest.stage(player)

        fun stage(value: Int) {
            VarPlayerIntMapSetter.set(player, "varbit.dwarf_cannon_progress", value)
        }

        fun journal(): String = quest.questLog(access())

        fun access() = ProtectedAccess(player, coroutine, context)

        fun count(obj: String): Int = player.inv.count(obj)

        fun give(obj: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            if (type.stackable) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, count)
            } else {
                repeat(count) {
                    val slot = player.inv.indexOfFirst { it == null }
                    player.inv[slot] = InvObj(obj, 1)
                }
            }
        }

        fun giveSet() {
            for (part in listOf(CannonBase, CannonStand, CannonBarrels, CannonFurnace)) give(part)
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.logs")
        }

        fun talk(symbol: String, op: Int = 1) {
            val npc = Npc(symbol, player.coords.translateZ(1))
            run {
                val event = if (op == 3) NpcEvents.Op3(npc) else NpcEvents.Op1(npc)
                assertTrue(events.publish(this, event))
            }
        }

        private fun bound(symbol: String, at: CoordGrid, shape: LocShape, angle: LocAngle): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val layer = if (shape == LocShape.WallStraight) 0 else 2
            return BoundLocInfo(LocInfo(layer, at, LocEntity(type.id, shape.id, angle.id)), type)
        }

        fun loc(
            symbol: String,
            at: CoordGrid,
            shape: LocShape = LocShape.WallStraight,
            op: Int = 1,
        ) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val loc = bound(symbol, at, shape, LocAngle.West)
            player.coords = at.translateZ(-1)
            run {
                val event =
                    when (op) {
                        2 -> LocEvents.Op2(loc, loc, type)
                        3 -> LocEvents.Op3(loc, loc, type)
                        4 -> LocEvents.Op4(loc, loc, type)
                        else -> LocEvents.Op1(loc, loc, type)
                    }
                assertTrue(events.publish(this, event))
            }
        }

        fun useOnLoc(
            symbol: String,
            at: CoordGrid,
            obj: String,
            shape: LocShape = LocShape.WallStraight,
        ) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val item = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            val loc = bound(symbol, at, shape, LocAngle.West)
            val slot = (0 until 28).first { player.inv[it]?.id == item.id }
            player.coords = at.translateZ(-1)
            run {
                val specific = LocUEvents.Op(loc, loc, type, item, slot)
                if (!events.publish(this, specific)) {
                    val default = LocUDefaultEvents.OpType(loc, loc, type, item, slot)
                    assertTrue(events.publish(this, default))
                }
            }
        }

        fun openRepair() {
            useOnLoc(CampCannon, CoordGrid(2562, 3461, 0), Toolkit, LocShape.CentrepieceStraight)
            finish()
        }

        fun press(component: String) {
            val id = "component.mcannon_interface:$component".asRSCM(RSCMType.COMPONENT)
            val type = ServerCacheManager.fromComponent(id)
            run(clear = false) {
                val event = IfModalButton(type, comsub = -1, obj = null, op = IfButtonOp.Op1)
                assertTrue(events.publish(this, event))
            }
        }

        fun heldOp1(obj: String) = heldOp(obj, 1)

        fun heldOp(obj: String, op: Int) {
            val id = obj.asRSCM(RSCMType.OBJ)
            val slot = (0 until 28).first { player.inv[it]?.id == id }
            val type = checkNotNull(ServerCacheManager.getItem(id))
            run {
                val event =
                    when (op) {
                        4 -> HeldObjEvents.Op4(slot, checkNotNull(inv[slot]), type, inv)
                        else -> HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv)
                    }
                assertTrue(events.publish(this, event))
            }
            if (op == 4) finish()
        }

        fun useHeldOn(used: String, target: String) {
            val usedId = used.asRSCM(RSCMType.OBJ)
            val targetId = target.asRSCM(RSCMType.OBJ)
            val usedSlot = (0 until 28).first { player.inv[it]?.id == usedId }
            val targetSlot = (0 until 28).first { player.inv[it]?.id == targetId }
            val usedType = checkNotNull(ServerCacheManager.getItem(usedId))
            val targetType = checkNotNull(ServerCacheManager.getItem(targetId))
            run {
                val event = HeldUEvents.Type(usedType, usedSlot, targetType, targetSlot)
                if (!events.publish(this, event)) fail<Unit>("no handler for $used on $target")
            }
        }

        private fun cannonBound(cannon: Multicannons.Cannon, symbol: String): BoundLocInfo =
            bound(symbol, cannon.origin, LocShape.CentrepieceStraight, LocAngle.West)

        fun cannonLoc(
            cannon: Multicannons.Cannon,
            op: Int,
            amount: Int = 0,
            shared: Fixture? = null,
        ) {
            val symbol = cannon.style.cannonLoc
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val loc = cannonBound(cannon, symbol)
            player.coords = cannon.origin.translate(1, -3)
            countAnswer = amount
            run {
                val event =
                    when (op) {
                        2 -> LocEvents.Op2(loc, loc, type)
                        3 -> LocEvents.Op3(loc, loc, type)
                        4 -> LocEvents.Op4(loc, loc, type)
                        else -> LocEvents.Op1(loc, loc, type)
                    }
                assertTrue(events.publish(this, event))
            }
        }

        fun useOnCannonStage(cannon: Multicannons.Cannon, symbol: String, obj: String) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val item = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            val loc = cannonBound(cannon, symbol)
            val slot = (0 until 28).first { player.inv[it]?.id == item.id }
            player.coords = cannon.origin.translate(1, -3)
            run {
                val event = LocUEvents.Op(loc, loc, type, item, slot)
                assertTrue(events.publish(this, event))
            }
        }

        private var countAnswer = 0

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
                if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                    val input =
                        when {
                            player.ui.containsModal("interface.chatmenu") ->
                                ResumePauseButtonInput(
                                    "component.chatmenu:options",
                                    if (selections.hasNext()) selections.next() else 1,
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
                } else if (coroutine.isAwaiting(org.rsmod.api.player.input.ResumePCountDialogInput::class)) {
                    coroutine.resumeWith(org.rsmod.api.player.input.ResumePCountDialogInput(countAnswer))
                } else {
                    player.currentMapClock++
                    player.processedMapClock = player.currentMapClock
                    player.pendingSequence = org.rsmod.game.seq.EntitySeq.NULL
                    player.pendingFaceAngle = EntityFaceAngle.NULL
                    movement.process(player)
                    coroutine.advance()
                }
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        fun assertComplete(scroll: Boolean = true) {
            assertEquals(STAGE_COMPLETE, stage())
            assertEquals(STAGE_COMPLETE, player.vars["varp.mcannon"])
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(750, player.statMap.getXP("stat.crafting"))
            assertEquals(0, count(Mould))
            assertEquals(0, count(Notes))
            if (scroll) assertTrue(player.ui.containsModal("interface.questscroll"))
        }

        fun output() = client.messages.joinToString("\n").replace("<br>", " ")

        companion object {
            val Start = CoordGrid(2580, 3420, 0)

            fun regionsFor(
                collision: CollisionFlagMap,
                npcRegistry: NpcRegistry = NpcRegistry(NpcList(), collision, EventBus()),
                normal: LocRegistryNormal =
                    LocRegistryNormal(ZoneUpdateMap(), collision, LocZoneStorage()),
                storage: LocZoneStorage = LocZoneStorage(),
                clock: MapClock = MapClock(100),
            ): RegionRegistry =
                RegionRegistry(
                    RegionListSmall(),
                    RegionListLarge(),
                    RegionListWorldEntity(),
                    normal,
                    collision,
                    storage,
                    npcRegistry,
                    ControllerRegistry(clock, ControllerList()),
                    ZonePlayerActivityBitSet(),
                )
        }
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
        private const val Lawgof = "npc.lawgof2"
        private const val Nulodion = "npc.nulodion"

        private const val Railing = "obj.mcannonrailing1_obj"
        private const val Hammer = "obj.hammer"
        private const val Remains = "obj.mcannonremains"
        private const val Toolkit = "obj.mcannontoolkit"
        private const val Notes = "obj.nulodions_notes"
        private const val Mould = "obj.ammo_mould"
        private const val Manual = "obj.mcannonbook"
        private const val CannonBase = "obj.twpart1"
        private const val CannonStand = "obj.twpart2"
        private const val CannonBarrels = "obj.twpart3"
        private const val CannonFurnace = "obj.twpart4"
        private const val CannonBall = "obj.mcannonball"
        private const val GraniteBall = "obj.granite_cannonball"
        private const val Kit = "obj.league_3_multicannon_pack"
        private const val OrnateBase = "obj.league_3_multicannon_base"

        private const val RemainsLoc = "loc.mcannonremains_multiloc"
        private const val LollkCrate = "loc.mcannoncrateboy"
        private const val CampCannon = "loc.mcannon_cannon_multiloc"

        private val Railings = (1..6).map { "loc.mcannon_railing${it}_multiloc" }
        private val RailingTiles =
            listOf(
                CoordGrid(2555, 3479, 0),
                CoordGrid(2557, 3468, 0),
                CoordGrid(2559, 3458, 0),
                CoordGrid(2563, 3457, 0),
                CoordGrid(2573, 3457, 0),
                CoordGrid(2577, 3457, 0),
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
            val oldPolicy = QuestRequirements.activePolicy()
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
            restored += { QuestRequirements.install(oldPolicy) }
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
