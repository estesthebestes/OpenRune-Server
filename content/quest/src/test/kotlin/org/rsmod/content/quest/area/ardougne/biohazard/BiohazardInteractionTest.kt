package org.rsmod.content.quest.area.ardougne.biohazard

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.events.interact.WornObjEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_CROSSED_WALL
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_DISTRACTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_DISTILLATOR
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GUIDOR_TESTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_HQ_REFUSED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_MET_OMART
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_MOURNER_KILLED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_STEW_POISONED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_TOLD_ELENA
import org.rsmod.content.quest.area.ardougne.biohazard.npcs.Elena
import org.rsmod.content.quest.area.ardougne.plaguecity.EdmondsGarden
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.elenaHome
import org.rsmod.content.quest.area.ardougne.plaguecity.mudDug
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
import org.rsmod.game.interact.InteractionOp
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
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BiohazardInteractionTest {

    @Test
    fun `the whole quest from Elena's offer to the king's reward`() {
        val f = Fixture(0)

        f.at(ELENA_HOME)
        f.choose(1)
        f.talkTo(ELENA)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(STAGE_STARTED, f.player.vars["varp.biohazard"])
        assertFalse(f.player.mudDug, "the tunnel is filled in")
        assertTrue(f.said("Jerico is in communication with West Ardougne"))

        f.at(JERICO_HOME)
        f.talkTo(JERICO)
        assertTrue(f.player.metJerico)
        assertTrue(f.said("Omart is waiting for you at the southern end of the wall"))
        f.talkTo(JERICO)
        assertTrue(f.said("Have you spoken to Omart yet?"))
        assertEquals(STAGE_STARTED, f.stage())

        f.at(OMART_TILE)
        f.talkTo(OMART)
        assertEquals(STAGE_MET_OMART, f.stage())
        assertTrue(f.player.metOmart)
        assertTrue(f.said("Chuck some bird feed at the tower"))

        f.at(JERICO_HOME)
        f.talkTo(JERICO)
        assertTrue(f.said("Just grab one of the cages from behind my house"))

        f.openJericoCupboard()
        f.searchJericoCupboard()
        assertEquals(1, f.count("obj.birdfeed"))
        f.searchJericoCupboard()
        assertEquals(1, f.count("obj.birdfeed"), "only one handful is handed out")
        assertTrue(f.said("I don't need any more bird feed."))

        f.give("obj.pigeons")
        f.at(TOWER_SPOT)
        f.held("obj.pigeons")
        assertEquals(STAGE_MET_OMART, f.stage(), "the pigeons stay put until the tower has had its feed")
        assertEquals(1, f.count("obj.pigeons"))
        f.held("obj.birdfeed")
        assertEquals(0, f.count("obj.birdfeed"))
        assertTrue(f.player.birdFeedThrown)
        f.held("obj.pigeons")
        assertEquals(STAGE_DISTRACTED, f.stage())
        assertEquals(0, f.count("obj.pigeons"))
        assertEquals(1, f.count("obj.pigeoncage"))

        f.at(OMART_TILE)
        f.choose(1)
        f.talkTo(OMART)
        assertEquals(STAGE_DISTRACTED, f.stage(), "no gas mask, no crossing")
        assertEquals(OMART_TILE, f.player.coords)
        assertTrue(f.said("I'd recommend you wear a gas mask"))

        f.give("obj.gasmask")
        f.wear("obj.gasmask")
        f.choose(1)
        f.talkTo(OMART)
        assertEquals(STAGE_CROSSED_WALL, f.stage())
        assertEquals(WEST_LANDING, f.player.coords)
        assertTrue(f.said("Do you know where to find the Mourner Headquarters?"))

        f.at(HQ_FRONT_OUTSIDE)
        f.talkTo(HQ_GUARD)
        assertEquals(STAGE_HQ_REFUSED, f.stage())
        assertTrue(f.said("Only mourners are allowed inside the headquarters"))

        f.give("obj.rottenapples")
        f.at(CAULDRON_SIDE)
        f.appleOnCauldron()
        assertEquals(STAGE_STEW_POISONED, f.stage())
        assertEquals(0, f.count("obj.rottenapples"))

        f.at(NURSE_SPOT)
        f.openNurseCupboard()
        f.searchNurseCupboard()
        assertEquals(1, f.count("obj.doctor_gown"))
        f.searchNurseCupboard()
        assertEquals(1, f.count("obj.doctor_gown"), "one gown is enough")
        f.wear("obj.doctor_gown")

        f.at(HQ_FRONT_OUTSIDE)
        f.hqFrontDoor()
        assertTrue(f.hasLocNear("loc.mournerstewdooropen", HQ_FRONT_DOOR))
        f.at(HQ_FRONT_OUTSIDE.translateZ(2))
        f.choose(1)
        f.talkTo(SICK_MOURNER)
        assertEquals(STAGE_STEW_POISONED, f.stage())
        assertTrue(f.said("You're no doctor!"))
        f.killSickMourner()
        assertEquals(STAGE_MOURNER_KILLED, f.stage())
        assertEquals(1, f.count("obj.mournerkeytw"))

        f.at(CAGE_OUTSIDE)
        f.cageGate()
        assertTrue(f.hasLocNear("loc.mournerquaters_gatelopen", CAGE_GATE))
        f.at(CAGE_INSIDE)
        f.searchCrate()
        assertEquals(STAGE_GOT_DISTILLATOR, f.stage())
        assertEquals(1, f.count("obj.distillator"))

        f.at(ELENA_HOME)
        f.useOnNpc(ELENA, "obj.distillator")
        assertEquals(STAGE_GOT_SAMPLES, f.stage())
        assertEquals(0, f.count("obj.distillator"))
        for (kit in Elena.SAMPLE_KIT) assertEquals(1, f.count(kit), kit)

        f.choose(2)
        f.chemist()
        assertEquals(STAGE_GOT_TOUCH_PAPER, f.stage())
        assertEquals(1, f.count("obj.touch_paper"))

        f.choose(2)
        f.talkTo(CHANCY_RIMMINGTON)
        f.choose(1)
        f.talkTo(DA_VINCI_RIMMINGTON)
        f.choose(3)
        f.talkTo(HOPS_RIMMINGTON)
        for (vial in BiohazardQuest.VIALS) assertEquals(0, f.count(vial), vial)
        assertEquals(VIAL_LIQUID_HONEY, f.player.chancyVial)
        assertEquals(VIAL_ETHENEA, f.player.daVinciVial)
        assertEquals(VIAL_SULPHURIC_BROLINE, f.player.hopsVial)

        f.at(INN)
        f.talkTo(CHANCY_VARROCK)
        f.talkTo(DA_VINCI_VARROCK)
        f.talkTo(HOPS_VARROCK)
        for (vial in BiohazardQuest.VIALS) assertEquals(1, f.count(vial), vial)
        assertEquals(VIAL_NONE, f.player.chancyVial)
        assertEquals(VIAL_NONE, f.player.daVinciVial)
        assertEquals(VIAL_NONE, f.player.hopsVial)

        f.give("obj.priest_gown")
        f.give("obj.priest_robe")
        f.wear("obj.priest_gown")
        f.wear("obj.priest_robe")
        f.at(JULIE_TILE)
        f.talkTo(JULIE)
        assertTrue(f.said("A priest! thank goodness!"))
        assertTrue(f.player.metJulie)
        f.at(GUIDOR_DOOR_OUTSIDE)
        f.guidorBedroomDoor()
        assertTrue(f.hasLocNear("loc.guidordooropen", GUIDOR_DOOR))

        f.at(GUIDOR_ROOM)
        f.choose(1)
        f.talkTo(GUIDOR)
        assertEquals(STAGE_GUIDOR_TESTED, f.stage())
        for (item in BiohazardQuest.VIALS + "obj.touch_paper" + "obj.plaguesample") {
            assertEquals(0, f.count(item), item)
        }
        assertTrue(f.said("there is no plague"))

        f.at(ELENA_HOME)
        f.talkTo(ELENA)
        assertEquals(STAGE_TOLD_ELENA, f.stage())
        f.talkTo(ELENA)
        assertTrue(f.said("You must go and see King Lathas immediately!"))

        f.at(THRONE)
        f.talkTo(KING)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(STAGE_COMPLETE, f.player.vars["varp.biohazard"])
        assertEquals(3, f.player.vars["varp.qp"])
        assertEquals(1250, f.player.statMap.getXP("stat.thieving"))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.completedJournal().contains("QUEST COMPLETE"))

        f.choose(2)
        f.talkTo(KING)
        assertTrue(f.said("I assume you're here about King Tyras?"))
        assertEquals(3, f.player.vars["varp.qp"], "the quest points are paid once")
        assertEquals(1250, f.player.statMap.getXP("stat.thieving"), "the experience is paid once")
    }

    @Test
    fun `Elena only offers the quest once Plague City is done and declining changes nothing`() {
        val f = Fixture(0, plagueDone = false)
        f.talkTo(ELENA)
        assertEquals(0, f.stage())
        assertTrue(f.said("thanks for freeing me"))
        assertFalse(f.said("distillator"))

        val g = Fixture(0)
        g.choose(2)
        g.talkTo(ELENA)
        assertEquals(0, g.stage())
        assertEquals(0, g.player.vars["varp.biohazard"])
        assertTrue(g.said("Fair enough."))
    }

    @Test
    fun `starting Biohazard fills the mud patch back in and the garden says so`() {
        val f = Fixture(0)
        f.player.mudDug = true
        f.choose(1)
        f.talkTo(ELENA)
        assertEquals(STAGE_STARTED, f.stage())
        assertFalse(f.player.mudDug)
        f.at(CoordGrid(2565, 3332, 0))
        f.digInGarden()
        assertTrue(f.said("The ground's been filled in and packed hard."))
    }

    @Test
    fun `Jerico Omart and Kilron talk to everyone else`() {
        val f = Fixture(0)
        f.talkTo(JERICO)
        assertTrue(f.said("Just passing by."))
        f.talkTo(OMART)
        assertTrue(f.said("Fine thanks."))
        f.talkTo(KILRON)
        assertTrue(f.said("Busy."))
        assertEquals(0, f.stage())
    }

    @Test
    fun `Omart repeats himself until the guards are distracted and then lets the player cross`() {
        val f = Fixture(STAGE_MET_OMART)
        f.talkTo(OMART)
        assertTrue(f.said("Have you sorted that distraction yet?"))
        assertEquals(STAGE_MET_OMART, f.stage())

        val g = Fixture(STAGE_DISTRACTED)
        g.at(OMART_TILE)
        g.choose(2)
        g.talkTo(OMART)
        assertTrue(g.said("Don't take too long"))
        assertEquals(STAGE_DISTRACTED, g.stage())
        assertEquals(OMART_TILE, g.player.coords)
    }

    @Test
    fun `the ladder works in both directions until the samples are made`() {
        val f = Fixture(STAGE_CROSSED_WALL)
        f.at(WEST_LANDING)
        f.choose(1)
        f.talkTo(KILRON)
        assertEquals(EAST_LANDING, f.player.coords)

        f.choose(1)
        f.talkTo(OMART)
        assertEquals(WEST_LANDING, f.player.coords, "the second crossing needs no gas mask check")
        assertEquals(STAGE_CROSSED_WALL, f.stage())

        val g = Fixture(STAGE_GOT_SAMPLES)
        g.talkTo(OMART)
        assertTrue(g.said("too risky to use the ladder again"))
        g.talkTo(KILRON)
        assertTrue(g.said("Busy."))
    }

    @Test
    fun `a distracted tower needs the right place the right stage and the feed first`() {
        val f = Fixture(STAGE_MET_OMART)
        f.give("obj.birdfeed")
        f.give("obj.pigeons")
        f.at(ELENA_HOME)
        f.held("obj.birdfeed")
        assertTrue(f.said("I probably shouldn't waste it by throwing it here."))
        assertEquals(1, f.count("obj.birdfeed"))
        assertFalse(f.player.birdFeedThrown)
        f.held("obj.pigeons")
        assertTrue(f.said("The pigeons don't want to leave."))
        assertEquals(1, f.count("obj.pigeons"))

        f.at(TOWER_SPOT)
        f.held("obj.pigeons")
        assertEquals(STAGE_MET_OMART, f.stage())
        assertEquals(1, f.count("obj.pigeons"))

        val g = Fixture(STAGE_STARTED)
        g.give("obj.birdfeed")
        g.at(TOWER_SPOT)
        g.held("obj.birdfeed")
        assertEquals(1, g.count("obj.birdfeed"), "Omart has to ask first")
        assertFalse(g.player.birdFeedThrown)
    }

    @Test
    fun `a full pack still leaves the cage empty and the quest moving`() {
        val f = Fixture(STAGE_MET_OMART)
        f.player.birdFeedThrown = true
        f.give("obj.pigeons")
        f.fill()
        f.at(TOWER_SPOT)
        f.held("obj.pigeons")
        assertEquals(STAGE_DISTRACTED, f.stage())
        assertEquals(1, f.count("obj.pigeoncage"))
    }

    @Test
    fun `Jerico's cupboard with a full pack keeps the feed in the cupboard`() {
        val f = Fixture(STAGE_STARTED)
        f.fill()
        f.at(JERICO_HOME)
        f.openJericoCupboard()
        f.searchJericoCupboard()
        assertEquals(0, f.count("obj.birdfeed"))
        assertTrue(f.said("you don't have enough room to take it"))
    }

    @Test
    fun `Jerico follows the stage`() {
        val f = Fixture(STAGE_DISTRACTED)
        f.talkTo(JERICO)
        assertTrue(f.said("you must go now, quickly traveller"))
        val g = Fixture(STAGE_CROSSED_WALL)
        g.talkTo(JERICO)
        assertTrue(g.said("Omart will be waiting by the wall"))
    }

    @Test
    fun `the gas mask and the gown stay on where the quest needs them`() {
        val f = Fixture(STAGE_CROSSED_WALL)
        f.give("obj.gasmask")
        f.wear("obj.gasmask")
        f.at(WEST_LANDING)
        f.removeWorn("obj.gasmask")
        assertTrue(f.said("You should leave your gas mask on while you're in West Ardougne."))
        assertEquals(1, f.worn("obj.gasmask"))

        val g = Fixture(STAGE_STEW_POISONED)
        g.give("obj.doctor_gown")
        g.wear("obj.doctor_gown")
        g.at(HQ_FRONT_OUTSIDE.translateZ(2))
        g.removeWorn("obj.doctor_gown")
        assertTrue(g.said("You should leave your medical gown on while you're in the Mourner Headquarters."))
        assertEquals(1, g.worn("obj.doctor_gown"))
    }

    @Test
    fun `the headquarters turn everyone else away`() {
        val f = Fixture(STAGE_CROSSED_WALL)
        f.at(HQ_FRONT_OUTSIDE)
        f.hqFrontDoor()
        assertTrue(f.said("Only mourners are allowed inside the headquarters"))
        assertEquals(STAGE_HQ_REFUSED, f.stage())
        assertFalse(f.hasLocNear("loc.mournerstewdooropen", HQ_FRONT_DOOR))

        f.at(HQ_BACK_OUTSIDE)
        f.hqBackDoor()
        assertTrue(f.said("Eugh, locked."))
        f.at(HQ_BACK_OUTSIDE.translateZ(-2))
        f.hqBackDoor()
        assertTrue(f.said("The door is locked."))

        val g = Fixture(STAGE_STEW_POISONED)
        g.at(HQ_FRONT_OUTSIDE)
        g.talkTo(HQ_GUARD)
        assertTrue(g.said("Several mourners are ill with food poisoning"))
        assertFalse(g.hasLocNear("loc.mournerstewdooropen", HQ_FRONT_DOOR))
        g.talkTo("npc.mournerstew1")
        assertTrue(g.said("I think it was the stew."))

        val h = Fixture(STAGE_HQ_REFUSED)
        h.talkTo("npc.mournerstew1")
        assertTrue(h.said("Stand back citizen, do not approach me."))
    }

    @Test
    fun `the stew is only spoiled with an apple once the headquarters turned the player away`() {
        val f = Fixture(STAGE_STARTED)
        f.give("obj.rottenapples")
        f.at(CAULDRON_SIDE)
        f.appleOnCauldron()
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.count("obj.rottenapples"))
        assertTrue(f.said("I have no reason to do that."))

        val g = Fixture(STAGE_STEW_POISONED)
        g.give("obj.rottenapples")
        g.at(CAULDRON_SIDE)
        g.appleOnCauldron()
        assertEquals(STAGE_STEW_POISONED, g.stage())
        assertEquals(1, g.count("obj.rottenapples"))
        assertTrue(g.said("The stew has already been spoiled."))

        val h = Fixture(STAGE_HQ_REFUSED)
        h.at(CAULDRON_SIDE)
        h.cauldronOption()
        assertTrue(h.said("Looks like a nice stew is being prepared."))
    }

    @Test
    fun `the gown and the poisoned stew get a doctor through the door and the nurse's cupboard is picky`() {
        val f = Fixture(STAGE_HQ_REFUSED)
        f.at(NURSE_SPOT)
        f.openNurseCupboard()
        f.searchNurseCupboard()
        assertEquals(0, f.count("obj.doctor_gown"))
        assertTrue(f.said("You search the cupboard but you find nothing of interest."))

        val g = Fixture(STAGE_STEW_POISONED)
        g.fill()
        g.at(NURSE_SPOT)
        g.openNurseCupboard()
        g.searchNurseCupboard()
        assertEquals(0, g.count("obj.doctor_gown"))
        assertTrue(g.said("you don't have enough room to take it"))

        val h = Fixture(STAGE_STEW_POISONED)
        h.give("obj.doctor_gown")
        h.wear("obj.doctor_gown")
        h.at(HQ_FRONT_OUTSIDE)
        h.talkTo(HQ_GUARD)
        assertTrue(h.said("A doctor? I didn't think there were any left around here."))
        h.hqFrontDoor()
        assertTrue(h.hasLocNear("loc.mournerstewdooropen", HQ_FRONT_DOOR))
    }

    @Test
    fun `Nurse Sarah talks about the quest only while it runs`() {
        val f = Fixture(STAGE_CROSSED_WALL)
        f.talkTo("npc.bionurse")
        assertTrue(f.said("I don't know how much longer I can cope here."))
        val g = Fixture(0)
        g.talkTo("npc.bionurse")
        assertTrue(g.said("how are you feeling?"))
    }

    @Test
    fun `the sickest mourner only fights a fake doctor and the key can be won again if lost`() {
        val f = Fixture(STAGE_STEW_POISONED)
        f.at(HQ_FRONT_OUTSIDE.translateZ(2))
        f.choose(3)
        f.talkTo(SICK_MOURNER)
        assertTrue(f.said("I've never even had a girlfriend."))
        assertTrue(f.said("It's erm... at home."))
        f.killSickMourner()
        assertEquals(STAGE_MOURNER_KILLED, f.stage())
        assertEquals(1, f.count("obj.mournerkeytw"))
        f.killSickMourner()
        assertEquals(1, f.count("obj.mournerkeytw"), "no second key while the first is in the pack")

        f.talkTo(SICK_MOURNER)
        assertTrue(f.said("Sorry, I'd like to be left in peace."))

        f.take("obj.mournerkeytw")
        f.choose(2)
        f.talkTo(SICK_MOURNER)
        assertTrue(f.said("You're an imposter!"))
        f.killSickMourner()
        assertEquals(1, f.count("obj.mournerkeytw"))
        assertEquals(STAGE_MOURNER_KILLED, f.stage())
    }

    @Test
    fun `the key falls to the floor when the pack is full and early kills do nothing`() {
        val f = Fixture(STAGE_STEW_POISONED)
        f.fill()
        f.at(HQ_FRONT_OUTSIDE.translateZ(2))
        f.killSickMourner()
        assertEquals(STAGE_MOURNER_KILLED, f.stage())
        assertEquals(0, f.count("obj.mournerkeytw"))
        assertEquals(1, f.onFloor("obj.mournerkeytw"))

        val g = Fixture(STAGE_HQ_REFUSED)
        g.killSickMourner()
        assertEquals(STAGE_HQ_REFUSED, g.stage())
        assertEquals(0, g.count("obj.mournerkeytw"))
    }

    @Test
    fun `the cage gate wants the key and the crate only gives the distillator once`() {
        val f = Fixture(STAGE_STEW_POISONED)
        f.at(CAGE_OUTSIDE)
        f.cageGate()
        assertTrue(f.said("The gate is locked."))
        assertFalse(f.hasLocNear("loc.mournerquaters_gatelopen", CAGE_GATE))

        val g = Fixture(STAGE_MOURNER_KILLED)
        g.give("obj.mournerkeytw")
        g.at(CAGE_INSIDE)
        g.searchCrate()
        assertEquals(1, g.count("obj.distillator"))
        assertEquals(STAGE_GOT_DISTILLATOR, g.stage())
        g.searchCrate()
        assertEquals(1, g.count("obj.distillator"))
        assertTrue(g.said("You search the crate but find nothing of interest."))

        val h = Fixture(STAGE_HQ_REFUSED)
        h.at(CAGE_INSIDE)
        h.searchCrate()
        assertEquals(0, h.count("obj.distillator"))
        assertEquals(STAGE_HQ_REFUSED, h.stage())

        val i = Fixture(STAGE_MOURNER_KILLED)
        i.fill()
        i.at(CAGE_INSIDE)
        i.searchCrate()
        assertEquals(0, i.count("obj.distillator"))
        assertEquals(STAGE_MOURNER_KILLED, i.stage(), "no room, no progress")
        assertTrue(i.said("you don't have enough room to take it"))
    }

    @Test
    fun `Elena asks for the distillator until it is back and takes it in one step`() {
        val f = Fixture(STAGE_CROSSED_WALL)
        f.talkTo(ELENA)
        assertTrue(f.said("I'm afraid not."))
        assertEquals(STAGE_CROSSED_WALL, f.stage())

        val g = Fixture(STAGE_STARTED)
        g.talkTo(ELENA)
        assertTrue(g.said("Have you spoken to Jerico yet?"))

        val h = Fixture(STAGE_GOT_DISTILLATOR)
        h.give("obj.distillator")
        h.talkTo(ELENA)
        assertEquals(STAGE_GOT_SAMPLES, h.stage())
        assertEquals(0, h.count("obj.distillator"))
        for (kit in Elena.SAMPLE_KIT) assertEquals(1, h.count(kit), kit)
    }

    @Test
    fun `a full pack never loses the sample kit`() {
        val f = Fixture(STAGE_GOT_DISTILLATOR)
        f.give("obj.distillator")
        f.fill()
        f.talkTo(ELENA)
        assertEquals(STAGE_GOT_SAMPLES, f.stage())
        for (kit in Elena.SAMPLE_KIT) {
            assertEquals(1, f.count(kit) + f.onFloor(kit), kit)
        }
    }

    @Test
    fun `Elena replaces lost samples without doubling what the errand boys still hold`() {
        val f = Fixture(STAGE_GOT_SAMPLES)
        f.give("obj.plaguesample")
        f.give("obj.ethenea")
        f.player.hopsVial = VIAL_SULPHURIC_BROLINE
        f.choose(2)
        f.talkTo(ELENA)
        assertEquals(1, f.count("obj.plaguesample"))
        assertEquals(1, f.count("obj.ethenea"))
        assertEquals(1, f.count("obj.liquid_honey"), "the lost honey is replaced")
        assertEquals(0, f.count("obj.sulphuric_broline"), "Hops is still carrying his")

        val g = Fixture(STAGE_GOT_SAMPLES)
        for (kit in Elena.SAMPLE_KIT) g.give(kit)
        g.choose(2)
        g.talkTo(ELENA)
        assertTrue(g.said("Looks like you have everything to me."))
        for (kit in Elena.SAMPLE_KIT) assertEquals(1, g.count(kit), kit)

        val h = Fixture(STAGE_GOT_SAMPLES)
        h.fill()
        h.choose(2)
        h.talkTo(ELENA)
        assertTrue(h.said("you don't have enough room for them"))
        for (kit in Elena.SAMPLE_KIT) assertEquals(0, h.count(kit), kit)
    }

    @Test
    fun `Elena reminds the player what to do`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        f.choose(3)
        f.talkTo(ELENA)
        assertTrue(f.said("Go to Rimmington and get some touch paper from the chemist."))
        val g = Fixture(STAGE_GUIDOR_TESTED)
        g.talkTo(ELENA)
        assertEquals(STAGE_TOLD_ELENA, g.stage())
    }

    @Test
    fun `the chemist only has quest talk while the vials need smuggling`() {
        val f = Fixture(STAGE_DISTRACTED)
        assertFalse(f.chemistHandled())
        val g = Fixture(0)
        assertFalse(g.chemistHandled())
        val h = Fixture(STAGE_GOT_SAMPLES)
        h.choose(1)
        assertTrue(h.chemistHandled())
        assertTrue(h.said("LAMP-TALK"))
        assertEquals(STAGE_GOT_SAMPLES, h.stage())
    }

    @Test
    fun `the chemist hands out touch paper again to a player who lost it`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        f.choose(2)
        f.chemist()
        assertEquals(1, f.count("obj.touch_paper"))
        assertTrue(f.said("Okay, here you go."))
        f.choose(2)
        f.chemist()
        assertEquals(1, f.count("obj.touch_paper"), "no second sheet while the first is in the pack")
        assertTrue(f.said("Fair enough, don't forget to talk to my errand boys outside"))

        val g = Fixture(STAGE_GOT_TOUCH_PAPER)
        g.fill()
        g.choose(2)
        g.chemist()
        assertEquals(1, g.count("obj.touch_paper") + g.onFloor("obj.touch_paper"))

        val h = Fixture(STAGE_GOT_SAMPLES)
        h.fill()
        h.choose(2)
        h.chemist()
        assertEquals(STAGE_GOT_TOUCH_PAPER, h.stage())
        assertEquals(1, h.count("obj.touch_paper") + h.onFloor("obj.touch_paper"))
    }

    @Test
    fun `an errand boy takes exactly one vial and only the one the player really has`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        f.give("obj.liquid_honey")
        f.choose(1)
        f.talkTo(CHANCY_RIMMINGTON)
        assertTrue(f.said("You can't give him what you don't have."))
        assertEquals(1, f.count("obj.liquid_honey"))
        assertEquals(VIAL_NONE, f.player.chancyVial)

        f.choose(2)
        f.talkTo(CHANCY_RIMMINGTON)
        assertEquals(0, f.count("obj.liquid_honey"))
        assertEquals(VIAL_LIQUID_HONEY, f.player.chancyVial)

        f.give("obj.ethenea")
        f.talkTo(CHANCY_RIMMINGTON)
        assertTrue(f.said("I'm not taking two"))
        assertEquals(1, f.count("obj.ethenea"))
        assertEquals(VIAL_LIQUID_HONEY, f.player.chancyVial)
    }

    @Test
    fun `the errand boys have nothing to do before the chemist has spoken`() {
        val f = Fixture(STAGE_GOT_SAMPLES)
        f.give("obj.ethenea")
        f.talkTo(DA_VINCI_RIMMINGTON)
        assertTrue(f.said("HUMILIATION"))
        assertEquals(1, f.count("obj.ethenea"))
        f.talkTo(CHANCY_RIMMINGTON)
        assertTrue(f.said("Playing solitaire?"))
        f.talkTo(HOPS_RIMMINGTON)
        assertTrue(f.said("Hops don't wanna talk now."))
        f.talkTo(CHANCY_VARROCK)
        assertTrue(f.said("I'm trying to find my gambling buddies!"))
    }

    @Test
    fun `each errand boy keeps whatever he can use`() {
        val chancy = Fixture(STAGE_GOT_TOUCH_PAPER)
        chancy.player.chancyVial = VIAL_ETHENEA
        chancy.talkTo(CHANCY_VARROCK)
        assertTrue(chancy.said("Here's your cut anyway."))
        assertEquals(10, chancy.count("obj.coins"))
        assertEquals(0, chancy.count("obj.ethenea"))
        assertEquals(VIAL_NONE, chancy.player.chancyVial)

        val daVinci = Fixture(STAGE_GOT_TOUCH_PAPER)
        daVinci.player.daVinciVial = VIAL_LIQUID_HONEY
        daVinci.choose(1)
        daVinci.talkTo(DA_VINCI_VARROCK)
        assertTrue(daVinci.said("The Majesty of Varrock!"))
        assertEquals(0, daVinci.count("obj.liquid_honey"))
        assertEquals(VIAL_NONE, daVinci.player.daVinciVial)

        val hops = Fixture(STAGE_GOT_TOUCH_PAPER)
        hops.player.hopsVial = VIAL_ETHENEA
        hops.talkTo(HOPS_VARROCK)
        assertTrue(hops.said("But I'd be lying."))
        assertEquals(0, hops.count("obj.ethenea"))
        assertEquals(VIAL_NONE, hops.player.hopsVial)
    }

    @Test
    fun `a full pack leaves the vial in the errand boy's pocket and a stolen one can be replaced`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        f.player.daVinciVial = VIAL_ETHENEA
        f.fill()
        f.talkTo(DA_VINCI_VARROCK)
        assertEquals(0, f.count("obj.ethenea"))
        assertEquals(VIAL_ETHENEA, f.player.daVinciVial, "he still has it")
        assertTrue(f.said("you don't have enough room for it"))

        f.take("obj.bronze_dagger")
        f.talkTo(DA_VINCI_VARROCK)
        assertEquals(1, f.count("obj.ethenea"))
        assertEquals(VIAL_NONE, f.player.daVinciVial)

        val g = Fixture(STAGE_GOT_SAMPLES)
        g.give("obj.plaguesample")
        g.give("obj.liquid_honey")
        g.give("obj.sulphuric_broline")
        g.choose(2)
        g.talkTo(ELENA)
        assertEquals(1, g.count("obj.ethenea"), "Da Vinci's painting cost the player a vial, Elena has more")
    }

    @Test
    fun `the Varrock guard searches anyone bringing the vials in and lets the sample pass`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        for (vial in BiohazardQuest.VIALS) f.give(vial)
        f.give("obj.plaguesample")
        f.give("obj.touch_paper")
        f.at(GUIDOR_GATE_OUTSIDE)
        f.guidorGate()
        for (vial in BiohazardQuest.VIALS) assertEquals(0, f.count(vial), vial)
        assertEquals(1, f.count("obj.plaguesample"))
        assertEquals(1, f.count("obj.touch_paper"))
        assertTrue(f.said("He takes the vial of ethenea from you."))
        assertTrue(f.said("You may now pass."))
        assertTrue(f.hasLocNear("loc.guidorgatelopen", GUIDOR_GATE_LEFT))

        val g = Fixture(0)
        g.give("obj.ethenea")
        g.at(GUIDOR_GATE_OUTSIDE)
        g.guidorGate()
        assertEquals(1, g.count("obj.ethenea"), "the guard only cares during the quest")
        assertTrue(g.hasLocNear("loc.guidorgatelopen", GUIDOR_GATE_LEFT))
    }

    @Test
    fun `leaving Varrock's gate with vials asks first and staying keeps the gate shut`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        f.give("obj.ethenea")
        f.at(GUIDOR_GATE_OUTSIDE.translateX(2))
        f.choose(2)
        f.guidorGate()
        assertTrue(f.said("I should probably talk to Guidor before leaving the area."))
        assertFalse(f.hasLocNear("loc.guidorgatelopen", GUIDOR_GATE_LEFT))
        assertEquals(1, f.count("obj.ethenea"))

        val g = Fixture(STAGE_GOT_TOUCH_PAPER)
        g.give("obj.ethenea")
        g.at(GUIDOR_GATE_OUTSIDE.translateX(2))
        g.choose(1)
        g.guidorGate()
        assertTrue(g.hasLocNear("loc.guidorgatelopen", GUIDOR_GATE_LEFT))

        val h = Fixture(STAGE_GOT_TOUCH_PAPER)
        h.at(GUIDOR_GATE_OUTSIDE.translateX(2))
        h.guidorGate()
        assertFalse(h.said("I should probably talk to Guidor"))
        assertTrue(h.hasLocNear("loc.guidorgatelopen", GUIDOR_GATE_LEFT))
    }

    @Test
    fun `Julie only lets a priest see Guidor`() {
        val f = Fixture(STAGE_GOT_SAMPLES)
        f.talkTo(JULIE)
        assertTrue(f.said("I don't have time to chat!"))
        assertFalse(f.player.metJulie)
        f.at(GUIDOR_DOOR_OUTSIDE)
        f.guidorBedroomDoor()
        assertTrue(f.said("That's someone's bedroom."))
        assertFalse(f.hasLocNear("loc.guidordooropen", GUIDOR_DOOR))

        val g = Fixture(STAGE_GOT_TOUCH_PAPER)
        g.talkTo(JULIE)
        assertTrue(g.said("If only I could get him to see a priest!"))
        assertTrue(g.player.metJulie)
        g.at(GUIDOR_DOOR_OUTSIDE)
        g.guidorBedroomDoor()
        assertTrue(g.said("Thank you, but I just want him to see a priest."))
        assertFalse(g.hasLocNear("loc.guidordooropen", GUIDOR_DOOR))

        val h = Fixture(STAGE_GOT_TOUCH_PAPER)
        h.give("obj.priest_gown")
        h.give("obj.priest_robe")
        h.wear("obj.priest_gown")
        h.wear("obj.priest_robe")
        h.at(GUIDOR_DOOR_OUTSIDE)
        h.guidorBedroomDoor()
        assertTrue(h.hasLocNear("loc.guidordooropen", GUIDOR_DOOR))

        val i = Fixture(STAGE_GOT_TOUCH_PAPER)
        i.give("obj.priest_gown")
        i.wear("obj.priest_gown")
        i.at(GUIDOR_DOOR_OUTSIDE)
        i.guidorBedroomDoor()
        assertFalse(i.hasLocNear("loc.guidordooropen", GUIDOR_DOOR), "the robe is part of the outfit")
    }

    @Test
    fun `Guidor refuses to test anything without every ingredient and takes nothing`() {
        fun guidor(stage: Int, items: List<String>): Fixture {
            val f = Fixture(stage)
            for (item in items) f.give(item)
            f.give("obj.priest_gown")
            f.give("obj.priest_robe")
            f.wear("obj.priest_gown")
            f.wear("obj.priest_robe")
            f.at(GUIDOR_ROOM)
            f.choose(1)
            f.talkTo(GUIDOR)
            return f
        }
        val vials = BiohazardQuest.VIALS
        val noVial = guidor(STAGE_GOT_TOUCH_PAPER, vials.drop(1) + "obj.touch_paper" + "obj.plaguesample")
        assertTrue(noVial.said("I need all three reagents"))
        assertEquals(STAGE_GOT_TOUCH_PAPER, noVial.stage())
        assertEquals(2, vials.drop(1).sumOf { noVial.count(it) })
        assertEquals(1, noVial.count("obj.touch_paper"))
        assertEquals(1, noVial.count("obj.plaguesample"))

        val noPaper = guidor(STAGE_GOT_TOUCH_PAPER, vials + "obj.plaguesample")
        assertTrue(noPaper.said("You don't have any touch paper."))
        assertEquals(3, vials.sumOf { noPaper.count(it) })
        assertEquals(STAGE_GOT_TOUCH_PAPER, noPaper.stage())

        val noSample = guidor(STAGE_GOT_TOUCH_PAPER, vials + "obj.touch_paper")
        assertTrue(noSample.said("you don't actually have the plague sample"))
        assertEquals(1, noSample.count("obj.touch_paper"))
        assertEquals(STAGE_GOT_TOUCH_PAPER, noSample.stage())

        val bless = Fixture(STAGE_GOT_TOUCH_PAPER)
        for (item in vials + "obj.touch_paper" + "obj.plaguesample") bless.give(item)
        for (item in listOf("obj.priest_gown", "obj.priest_robe")) {
            bless.give(item)
            bless.wear(item)
        }
        bless.at(GUIDOR_ROOM)
        bless.choose(2)
        bless.talkTo(GUIDOR)
        assertTrue(bless.said("Oh. Goodbye then."))
        assertEquals(STAGE_GOT_TOUCH_PAPER, bless.stage())
        assertEquals(1, bless.count("obj.plaguesample"))
    }

    @Test
    fun `Guidor sees nobody who is not a priest and talks to everyone else afterwards`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        f.talkTo(GUIDOR)
        assertTrue(f.said("I don't really want any visitors just now."))
        val g = Fixture(STAGE_GUIDOR_TESTED)
        g.talkTo(GUIDOR)
        assertTrue(g.said("I still can't understand why they would lie about the plague."))
        val h = Fixture(STAGE_COMPLETE)
        h.talkTo(GUIDOR)
        assertTrue(h.said("I'm hanging in there."))
        h.talkTo(JULIE)
        assertTrue(h.said("I have to keep an eye on my husband."))
        val i = Fixture(STAGE_GUIDOR_TESTED)
        i.talkTo(JULIE)
        assertTrue(i.said("I fear Guidor may not be long for this world!"))
    }

    @Test
    fun `Asyff's free gown comes once and a full pack keeps it on the floor`() {
        val f = Fixture(STAGE_GOT_TOUCH_PAPER)
        assertTrue(f.guidors.asyffOffersGown(f.player))
        f.talkAsyff()
        assertEquals(1, f.count("obj.priest_gown"))
        assertEquals(1, f.count("obj.priest_robe"))
        assertTrue(f.player.freeClothes)
        assertFalse(f.guidors.asyffOffersGown(f.player))

        val g = Fixture(STAGE_GOT_TOUCH_PAPER)
        g.fill()
        g.talkAsyff()
        assertEquals(1, g.onFloor("obj.priest_gown"))
        assertEquals(1, g.onFloor("obj.priest_robe"))

        val h = Fixture(0)
        assertFalse(h.guidors.asyffOffersGown(h.player), "only during the quest")
        val i = Fixture(STAGE_COMPLETE)
        assertFalse(i.guidors.asyffOffersGown(i.player))
    }

    @Test
    fun `King Lathas only explains himself to the player Elena sent`() {
        val f = Fixture(STAGE_GUIDOR_TESTED)
        f.talkTo(KING)
        assertTrue(f.said("I'm far too busy to talk."))
        assertEquals(STAGE_GUIDOR_TESTED, f.stage())
        assertEquals(0, f.player.vars["varp.qp"])

        val g = Fixture(0)
        g.talkTo(KING)
        assertTrue(g.said("I'm far too busy to talk."))
    }

    @Test
    fun `the reward is paid exactly once and survives a second visit`() {
        val f = Fixture(STAGE_TOLD_ELENA)
        f.talkTo(KING)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(3, f.player.vars["varp.qp"])
        assertEquals(1250, f.player.statMap.getXP("stat.thieving"))
        for (i in 1..2) {
            f.choose(1)
            f.talkTo(KING)
            assertEquals(3, f.player.vars["varp.qp"])
            assertEquals(1250, f.player.statMap.getXP("stat.thieving"))
        }
        f.talkTo(ELENA)
        assertTrue(f.said("did you speak to the king?"))
        assertTrue(f.player.postQuestChat)
        f.talkTo(ELENA)
        assertTrue(f.said("I do wish you could tell me what's going on with the plague though."))
    }

    @Test
    fun `Elena's door swings out into the street`() {
        val f = Fixture(0)
        f.at(CoordGrid(2592, 3338, 0))
        f.elenaDoor()
        assertTrue(f.hasLocNear("loc.elenadoor2open", CoordGrid(2592, 3339, 0)))
    }

    @Test
    fun `the training camp is locked until the quest is done and pays six hits ever`() {
        val f = Fixture(STAGE_TOLD_ELENA)
        f.at(CAMP_OUTSIDE)
        f.campGate()
        assertTrue(f.said("The gates are locked."))
        assertFalse(f.hasLocNear("loc.lathastraining_gatelopen", CAMP_GATE_LEFT))

        val g = Fixture(STAGE_COMPLETE)
        g.at(CAMP_OUTSIDE)
        g.campGate()
        assertTrue(g.hasLocNear("loc.lathastraining_gatelopen", CAMP_GATE_LEFT))
        g.at(DUMMY_SIDE)
        repeat(6) { g.hitDummy() }
        assertEquals(300, g.player.statMap.getXP("stat.attack"))
        assertEquals(6, g.player.dummyHits)
        g.hitDummy()
        assertEquals(300, g.player.statMap.getXP("stat.attack"), "six hits ever")
        assertTrue(g.said("There is nothing more you can learn from hitting a dummy."))
    }

    @Test
    fun `the camp guards say what the transcripts give them`() {
        val f = Fixture(STAGE_COMPLETE)
        f.talkTo("npc.lathastrainer1")
        assertTrue(f.said("What do you want? Leave us be!"))
        f.talkTo("npc.lathastrainer2")
        assertTrue(f.said("they've eaten four children this week alone."))
        f.talkTo("npc.lathastrainer3")
        assertTrue(f.said("In this day and age we're all soldiers."))
    }

    @Test
    fun `the journal follows the stage`() {
        val expected =
            mapOf(
                STAGE_STARTED to "Jerico",
                STAGE_MET_OMART to "bird feed",
                STAGE_DISTRACTED to "guards are busy with the pigeons",
                STAGE_CROSSED_WALL to "I am over the wall",
                STAGE_HQ_REFUSED to "Only mourners are allowed inside",
                STAGE_STEW_POISONED to "poisoned the mourners' stew",
                STAGE_MOURNER_KILLED to "dropped a",
                STAGE_GOT_DISTILLATOR to "I found Elena's",
                STAGE_GOT_SAMPLES to "touch paper",
                STAGE_GOT_TOUCH_PAPER to "errand boys",
                STAGE_GUIDOR_TESTED to "no plague",
                STAGE_TOLD_ELENA to "King Lathas",
            )
        for ((stage, text) in expected) {
            val journal = Fixture(stage).journal()
            assertTrue(journal.contains(text), "stage $stage: $text in $journal")
        }
    }

    private class Fixture(stage: Int = 0, plagueDone: Boolean = true) {
        val events = EventBus()
        private val client = RecordingClient()
        val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("biohazard-test")
        private var result: Result<Unit>? = null
        private lateinit var regions: RegionRegistry
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getCollision = { collision },
                    getNpcList = { npcs },
                    getNpcInteractions = { NpcInteractions(events) },
                    getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
                    getAreaChecker = { AreaChecker(regions, AreaIndex()) },
                )
        private val clock = MapClock(100)
        private val npcRepo: NpcRepository
        private val locRepo: LocRepository
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        val objRepo = ObjRepository(clock, objRegistry)
        private val picks = ArrayDeque<Int>()
        private val locU =
            LocUInteractions::class
                .java
                .getDeclaredConstructor(EventBus::class.java)
                .apply { isAccessible = true }
                .newInstance(events)
        private var placed = emptyList<BiohazardMap.Placed>()

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 5511L
                observerUUID = 5511L
                slotId = 1
                assignUid()
                coords = ELENA_HOME
                currentMapClock = 100
                processedMapClock = 100
                pendingSequence = EntitySeq.NULL
                pendingFaceAngle = EntityFaceAngle.NULL
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

        val plague = PlagueCityQuest()
        val biohazard = BiohazardQuest()
        val doors: QuestDoors
        lateinit var guidors: GuidorsHouse
        private lateinit var smuggling: Smuggling
        private lateinit var headquarters: MournerHeadquarters
        private lateinit var garden: EdmondsGarden

        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            val playerList = PlayerList()
            val activity = ZonePlayerActivityBitSet()
            regions =
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
            val locRegistry =
                LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
            locRepo = LocRepository(clock, locRegistry, regions)
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            placed = BiohazardMap.apply(collision, storage)
            val worldRepo = WorldRepository(updates)
            doors = QuestDoors(locRepo)
            val search =
                NpcSearch(
                    Hunt(
                        RayCastValidator(collision),
                        PlayerRegistry(playerList, collision, activity, events),
                        npcRegistry,
                        objRegistry,
                        locRegistry,
                    )
                )
            val death = NpcDeath(npcRepo, playerList, objRepo, emptySet(), emptySet())
            val launcher = unlaunchable()
            val aiInteractions = AiPlayerInteractions(events, playerList)
            garden = EdmondsGarden(plague)
            guidors = GuidorsHouse(biohazard, doors, objRepo)
            smuggling = Smuggling(biohazard, objRepo)
            headquarters =
                MournerHeadquarters(
                    biohazard,
                    doors,
                    objRepo,
                    aiInteractions,
                    death,
                    playerList,
                    launcher,
                )
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(biohazard) { scripts.startup() }
            with(Elena(biohazard, plague, objRepo, doors)) { scripts.startup() }
            with(JericosHouse(biohazard, locRepo)) { scripts.startup() }
            with(RopeLadder(biohazard)) { scripts.startup() }
            with(WatchtowerDistraction(biohazard, worldRepo, search)) { scripts.startup() }
            with(NurseSarah(biohazard, locRepo)) { scripts.startup() }
            with(Disguises(biohazard)) { scripts.startup() }
            with(headquarters) { scripts.startup() }
            with(smuggling) { scripts.startup() }
            with(guidors) { scripts.startup() }
            with(KingLathas(biohazard)) { scripts.startup() }
            with(CombatTrainingCamp(biohazard, doors)) { scripts.startup() }
            with(garden) { scripts.startup() }
            if (plagueDone) {
                VarPlayerIntMapSetter.set(
                    player,
                    "varbit.plaguecity_progress",
                    PlagueCityQuest.STAGE_COMPLETE,
                )
                player.elenaHome = true
            }
            if (stage > 0) VarPlayerIntMapSetter.set(player, "varbit.biohazard_progress", stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = biohazard.stage(player)

        fun journal(): String = biohazard.questLog(access())

        fun completedJournal(): String = biohazard.completedLog(access())

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            repeat(count) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun take(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun wear(obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            if (player.inv.count(obj) == 0) give(obj)
            take(obj)
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

        fun worn(obj: String): Int = player.worn.count(obj)

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun onFloor(obj: String): Int =
            objRegistry
                .findAll(player.coords)
                .filter { it.type == obj.asRSCM() }
                .sumOf { it.count }

        fun said(text: String): Boolean = output().contains(text)

        fun at(coords: CoordGrid) {
            player.coords = coords
        }

        fun hasLocNear(type: String, coords: CoordGrid): Boolean =
            (-2..2).any { dx ->
                (-2..2).any { dz -> locRepo.findLoc(coords.translate(dx, dz), type) }
            }

        fun talkTo(type: String, at: CoordGrid = player.coords.translateX(1)): Npc {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
            return npc
        }

        fun useOnNpc(type: String, obj: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val npcType = checkNotNull(ServerCacheManager.getNpc(type.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            dispatch {
                assertTrue(events.publish(this, NpcUDefaultEvents.OpType(npc, slot, objType, npcType)))
            }
        }

        fun chemist() {
            val npc = Npc("npc.chemist", player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch {
                startDialogue(npc) {
                    if (!with(smuggling) { chemistQuest { chatPlayer(neutral, "LAMP-TALK") } }) {
                        chatPlayer(neutral, "NOT-QUEST")
                    }
                }
            }
        }

        fun chemistHandled(): Boolean {
            chemist()
            return !said("NOT-QUEST")
        }

        fun talkAsyff() {
            val npc = Npc("npc.tailorp", player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { startDialogue(npc) { with(guidors) { asyffPriestGown() } } }
        }

        fun killSickMourner() {
            val tile = player.coords
            dispatch { with(headquarters) { claimMournerKey(tile) } }
        }

        fun digInGarden() {
            dispatch { with(garden as SpadeDigHook) { dig() } }
        }

        fun held(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch {
                val event = HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv)
                assertTrue(events.publish(this, event))
            }
        }

        fun removeWorn(obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = type.wearpos1
            dispatch {
                assertTrue(events.publish(this, WornObjEvents.Op1(slot, checkNotNull(worn[slot]))))
            }
        }

        fun openJericoCupboard() =
            locOp("loc.jericoscupboardshut", JERICO_CUPBOARD, LocShape.CentrepieceStraight, LocAngle.West)

        fun searchJericoCupboard() =
            locOp("loc.jericoscupboardopen", JERICO_CUPBOARD, LocShape.CentrepieceStraight, LocAngle.West)

        fun openNurseCupboard() =
            locOp("loc.bionursescupboardshut", NURSE_CUPBOARD, LocShape.CentrepieceStraight, LocAngle.West)

        fun searchNurseCupboard() =
            locOp("loc.bionursescupboardopen", NURSE_CUPBOARD, LocShape.CentrepieceStraight, LocAngle.West)

        fun hqFrontDoor() = placedOp("loc.mournerstewdoor", HQ_FRONT_DOOR)

        fun hqBackDoor() = placedOp("loc.mournerstewdoor", HQ_BACK_DOOR)

        fun cageGate() = placedOp("loc.mournerquaters_gatel", CAGE_GATE)

        fun searchCrate() = placedOp("loc.mournercrateup", CRATE)

        fun guidorGate() = placedOp("loc.guidorgatelclosed", GUIDOR_GATE_LEFT)

        fun guidorBedroomDoor() = placedOp("loc.guidordoor", GUIDOR_DOOR)

        fun elenaDoor() = placedOp("loc.elenadoor2", CoordGrid(2592, 3339, 0))

        fun campGate() = placedOp("loc.lathastraining_gatel", CAMP_GATE_LEFT)

        fun hitDummy() = placedOp("loc.biohazarddummy", DUMMY_TILE)

        fun appleOnCauldron() =
            locU(
                "loc.mournercauldron",
                CAULDRON,
                LocShape.CentrepieceStraight,
                "obj.rottenapples",
                LocAngle.West,
            )

        fun cauldronOption() =
            locOp("loc.mournercauldron_op", CAULDRON, LocShape.CentrepieceStraight, LocAngle.West)

        private fun placedLoc(symbol: String, coords: CoordGrid): BoundLocInfo {
            val id = symbol.asRSCM(RSCMType.LOC)
            val at = checkNotNull(placed.firstOrNull { it.id == id && it.coords == coords }) {
                "$symbol is not placed at $coords"
            }
            val type = checkNotNull(ServerCacheManager.getObject(id))
            val info =
                LocInfo(LocLayerConstants.of(at.shape), coords, LocEntity(id, at.shape, at.angle))
            return BoundLocInfo(info, type)
        }

        private fun placedOp(symbol: String, coords: CoordGrid) {
            runLocOp(placedLoc(symbol, coords))
        }

        private fun locOp(symbol: String, coords: CoordGrid, shape: LocShape, angle: LocAngle) {
            runLocOp(bound(symbol, coords, shape, angle))
        }

        private fun runLocOp(loc: BoundLocInfo) {
            val event =
                LocInteractions(BoundValidator(collision), events)
                    .opTrigger(player, loc, InteractionOp.Op1)
            checkNotNull(event) { "No op handler for ${loc.id}" }
            dispatch { assertTrue(events.publish(this, event)) }
        }

        private fun locU(
            symbol: String,
            coords: CoordGrid,
            shape: LocShape,
            obj: String,
            angle: LocAngle,
        ) {
            val loc = bound(symbol, coords, shape, angle)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            val trigger = checkNotNull(event) { "No $obj handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        private fun bound(symbol: String, coords: CoordGrid, shape: LocShape, angle: LocAngle): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val info =
                LocInfo(LocLayerConstants.of(shape.id), coords, LocEntity(type.id, shape.id, angle.id))
            return BoundLocInfo(info, type)
        }

        private fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val access = access()
            val start: suspend () -> Unit = { access.block() }
            start.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            result?.getOrThrow()
            repeat(900) {
                if (coroutine.isIdle) {
                    picks.clear()
                    return
                }
                step()
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun step() {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent =
                    listOf(
                            "chat_left",
                            "chat_right",
                            "messagebox",
                            "chatmenu",
                            "objectbox",
                            "objectbox_double",
                        )
                        .firstOrNull { player.ui.containsModal("interface.$it") }
                        ?: error("Unknown dialogue: ${output()}")
                val input =
                    when (parent) {
                        "chatmenu" ->
                            ResumePauseButtonInput(
                                "component.chatmenu:options",
                                picks.removeFirstOrNull() ?: 1,
                            )
                        "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                        "objectbox_double" ->
                            ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                        else -> ResumePauseButtonInput("component.$parent:continue", -1)
                    }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                player.pendingSequence = EntitySeq.NULL
                player.pendingFaceAngle = EntityFaceAngle.NULL
                coroutine.advance()
            }
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
        const val ELENA = "npc.elena2"
        const val JERICO = "npc.jerico"
        const val OMART = "npc.omart"
        const val KILRON = "npc.kilron"
        const val HQ_GUARD = "npc.mourner_armed_guard"
        const val SICK_MOURNER = "npc.mournerstew2"
        const val CHANCY_RIMMINGTON = "npc.gambler1"
        const val DA_VINCI_RIMMINGTON = "npc.artist1"
        const val HOPS_RIMMINGTON = "npc.drunk1"
        const val CHANCY_VARROCK = "npc.gambler2"
        const val DA_VINCI_VARROCK = "npc.artist2"
        const val HOPS_VARROCK = "npc.drunk2"
        const val JULIE = "npc.guidors_wife"
        const val GUIDOR = "npc.guidor"
        const val KING = "npc.kinglathas"

        val ELENA_HOME = CoordGrid(2592, 3337, 0)
        val JERICO_HOME = CoordGrid(2612, 3325, 0)
        val JERICO_CUPBOARD = CoordGrid(2611, 3326, 0)
        val OMART_TILE = CoordGrid(2559, 3267, 0)
        val EAST_LANDING = CoordGrid(2559, 3267, 0)
        val WEST_LANDING = CoordGrid(2556, 3267, 0)
        val TOWER_SPOT = CoordGrid(2565, 3300, 0)
        val HQ_FRONT_DOOR = CoordGrid(2551, 3320, 0)
        val HQ_FRONT_OUTSIDE = CoordGrid(2551, 3319, 0)
        val HQ_BACK_DOOR = CoordGrid(2551, 3328, 0)
        val HQ_BACK_OUTSIDE = CoordGrid(2551, 3329, 0)
        val CAULDRON = CoordGrid(2543, 3332, 0)
        val CAULDRON_SIDE = CoordGrid(2544, 3332, 0)
        val NURSE_CUPBOARD = CoordGrid(2517, 3276, 0)
        val NURSE_SPOT = CoordGrid(2517, 3277, 0)
        val CAGE_GATE = CoordGrid(2551, 3326, 1)
        val CAGE_OUTSIDE = CoordGrid(2550, 3326, 1)
        val CAGE_INSIDE = CoordGrid(2553, 3327, 1)
        val CRATE = CoordGrid(2554, 3327, 1)
        val INN = CoordGrid(3270, 3390, 0)
        val JULIE_TILE = CoordGrid(3280, 3383, 0)
        val GUIDOR_DOOR = CoordGrid(3282, 3382, 0)
        val GUIDOR_DOOR_OUTSIDE = CoordGrid(3281, 3382, 0)
        val GUIDOR_ROOM = CoordGrid(3283, 3382, 0)
        val GUIDOR_GATE_LEFT = CoordGrid(3264, 3405, 0)
        val GUIDOR_GATE_OUTSIDE = CoordGrid(3263, 3405, 0)
        val THRONE = CoordGrid(2578, 3292, 1)
        val CAMP_GATE_LEFT = CoordGrid(2517, 3356, 0)
        val CAMP_OUTSIDE = CoordGrid(2517, 3355, 0)
        val DUMMY_TILE = CoordGrid(2511, 3373, 0)
        val DUMMY_SIDE = CoordGrid(2512, 3373, 0)

        private val restored = mutableListOf<() -> Unit>()

        /** The death hook that launches protected access is exercised through `claimMournerKey`. */
        private fun unlaunchable(): ProtectedAccessLauncher {
            val unsafe =
                Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").run {
                    isAccessible = true
                    get(null) as sun.misc.Unsafe
                }
            return unsafe.allocateInstance(ProtectedAccessLauncher::class.java)
                as ProtectedAccessLauncher
        }
        private lateinit var cache: dev.openrune.filesystem.Cache

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            cache = ServerCacheManager.init(240)
            BiohazardMap.load(cache)
            cache.close()
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
