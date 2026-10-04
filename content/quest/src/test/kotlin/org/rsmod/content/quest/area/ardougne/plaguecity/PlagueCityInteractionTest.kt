package org.rsmod.content.quest.area.ardougne.plaguecity

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
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
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.WestArdougneGate
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_BOOK_RETURNED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_CLERK_PERMISSION
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_CURED_BRAVEK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_DUG_TUNNEL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_FREED_ELENA
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GOT_BOOK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GOT_WARRANT
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_CHECKED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_REMOVED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_HAS_GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_MOURNER_REFUSED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_ROPE_TIED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SNEAKED_IN
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SOIL_SOFTENED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_TALKED_BRAVEK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_TALKED_MILLI
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_UNLOCKED_CELL
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Alrena
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Citizens
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Edmond
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Jethick
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Mourners
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
class PlagueCityInteractionTest {

    @Test
    fun `the whole quest from Edmond's offer to the reward`() {
        val f = Fixture()

        f.at(GARDEN)
        f.choose(1, 1)
        f.talkTo(EDMOND_TOP)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(STAGE_STARTED, f.player.vars["varp.elenaquest"])
        assertTrue(f.said("McGrubor's Wood"))

        f.at(ALRENA_TILE)
        f.give("obj.dwellberries")
        f.talkTo("npc.alrena")
        assertEquals(STAGE_HAS_GAS_MASK, f.stage())
        assertEquals(0, f.count("obj.dwellberries"))
        assertEquals(1, f.count("obj.gasmask"))

        f.at(GARDEN)
        f.talkTo(EDMOND_TOP)
        assertTrue(f.player.toldToDig)
        f.give("obj.bucket_water", 4)
        repeat(3) { f.pourOnPatch() }
        assertEquals(STAGE_HAS_GAS_MASK, f.stage())
        assertEquals(3, f.count("obj.bucket_empty"))
        f.pourOnPatch()
        assertEquals(STAGE_SOIL_SOFTENED, f.stage())
        assertEquals(4, f.count("obj.bucket_empty"))
        assertEquals(0, f.count("obj.bucket_water"))

        f.at(PATCH_SIDE)
        f.dig()
        assertEquals(STAGE_DUG_TUNNEL, f.stage())
        assertTrue(f.player.mudDug)
        assertTrue(f.player.edmondBelow)
        assertEquals(EdmondsGarden.SEWER_ARRIVAL, f.player.coords)

        f.give("obj.rope")
        f.at(ArdougneSewer.GRILL_TILE.translateZ(2))
        f.useRopeOnGrill()
        assertTrue(f.said("Maybe I should try opening it first."))
        assertEquals(1, f.count("obj.rope"))
        f.pullGrill()
        assertEquals(STAGE_GRILL_CHECKED, f.stage())
        f.useRopeOnGrill()
        assertEquals(STAGE_ROPE_TIED, f.stage())
        assertEquals(PIPE_ROPE_TIED, f.player.pipeState)
        assertEquals(0, f.count("obj.rope"))

        f.talkTo(EDMOND_BOTTOM, EDMOND_BELOW_TILE)
        assertEquals(STAGE_GRILL_REMOVED, f.stage())
        assertEquals(PIPE_OPEN, f.player.pipeState)

        f.at(ArdougneSewer.PIPE_ARRIVAL)
        f.climbPipe()
        assertTrue(f.said("Edmond") || f.said("gas mask"))
        assertEquals(ArdougneSewer.PIPE_ARRIVAL, f.player.coords)
        f.wear("obj.gasmask")
        f.climbPipe()
        assertEquals(ArdougneSewer.SQUARE_ARRIVAL, f.player.coords)

        f.talkTo("npc.jethick")
        assertTrue(f.player.pictureAsked)
        assertEquals(STAGE_GRILL_REMOVED, f.stage())
        f.give("obj.elena_picture")
        f.choose(1)
        f.talkTo("npc.jethick")
        assertEquals(STAGE_GOT_BOOK, f.stage())
        assertEquals(1, f.count("obj.turnip_book"))
        assertEquals(1, f.count("obj.elena_picture"), "the picture is only shown")

        f.at(REHNISON_OUTSIDE)
        f.rehnisonDoor()
        assertEquals(STAGE_BOOK_RETURNED, f.stage())
        assertEquals(0, f.count("obj.turnip_book"))
        f.talkTo("npc.ted_rehnison")
        assertTrue(f.said("Milli is upstairs"))
        f.talkTo("npc.milli")
        assertEquals(STAGE_TALKED_MILLI, f.stage())

        f.at(PLAGUE_DOOR_OUTSIDE)
        f.choose(1, 2)
        f.plagueHouseDoor()
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())

        f.talkTo("npc.clerk")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())
        f.choose(1, 2, 3)
        f.talkTo("npc.clerk")
        assertEquals(STAGE_CLERK_PERMISSION, f.stage())

        f.choose(1, 3)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_TALKED_BRAVEK, f.stage())
        assertEquals(1, f.count("obj.scruffy_note"))

        f.give("obj.chocolate_dust")
        f.give("obj.bucket_milk")
        f.give("obj.snape_grass")
        f.useOn("obj.chocolate_dust", "obj.bucket_milk")
        assertEquals(1, f.count("obj.chocolaty_milk"))
        assertEquals(0, f.count("obj.chocolate_dust"))
        assertEquals(0, f.count("obj.bucket_milk"))
        f.useOn("obj.snape_grass", "obj.chocolaty_milk")
        assertEquals(1, f.count("obj.hangover_cure"))
        assertEquals(0, f.count("obj.snape_grass"))
        assertEquals(0, f.count("obj.chocolaty_milk"))

        f.choose(3)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_GOT_WARRANT, f.stage())
        assertEquals(0, f.count("obj.hangover_cure"))
        assertEquals(1, f.count("obj.warrant"))

        f.plagueHouseDoor()
        assertEquals(STAGE_SNEAKED_IN, f.stage())
        assertEquals(PLAGUE_DOOR.translateZ(-1), f.player.coords)

        f.searchBarrel()
        assertEquals(1, f.count("obj.elenakey"))
        f.cellDoor()
        assertEquals(STAGE_UNLOCKED_CELL, f.stage())
        f.talkTo("npc.elenap", ELENA_CELL_TILE)
        assertEquals(STAGE_FREED_ELENA, f.stage())
        assertTrue(f.player.elenaHome)
        assertFalse(f.player.edmondBelow)

        f.at(GARDEN)
        f.talkTo(EDMOND_TOP)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(STAGE_COMPLETE, f.player.vars["varp.elenaquest"])
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(2425, f.player.statMap.getXP("stat.mining"))
        assertEquals(1, f.count("obj.ardougnescroll"))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.completedJournal().contains("QUEST COMPLETE"))

        f.choose(2)
        f.talkTo(EDMOND_TOP)
        assertTrue(f.said("Thank you again for rescuing my daughter"))
        assertEquals(1, f.player.vars["varp.qp"], "the quest point is paid once")
        assertEquals(2425, f.player.statMap.getXP("stat.mining"), "the experience is paid once")
        assertEquals(1, f.count("obj.ardougnescroll"))
    }

    @Test
    fun `declining Edmond leaves the quest unstarted`() {
        val f = Fixture()
        f.choose(1, 2)
        f.talkTo(EDMOND_TOP)
        assertEquals(0, f.stage())
        assertEquals(0, f.player.vars["varp.elenaquest"])
        assertTrue(f.said("anything about Elena"))

        val g = Fixture()
        g.choose(2)
        g.talkTo(EDMOND_TOP)
        assertEquals(0, g.stage())

        val h = Fixture()
        h.talkTo("npc.alrena")
        assertEquals(0, h.stage())
        assertTrue(h.said("troubles on my mind"))
    }

    @Test
    fun `starting clears flags left from an earlier attempt`() {
        val f = Fixture()
        f.player.mudDug = true
        f.player.edmondBelow = true
        f.player.bucketsPoured = 3
        f.player.toldToDig = true
        f.choose(1, 1)
        f.talkTo(EDMOND_TOP)
        assertEquals(STAGE_STARTED, f.stage())
        assertFalse(f.player.mudDug)
        assertFalse(f.player.edmondBelow)
        assertEquals(0, f.player.bucketsPoured)
        assertFalse(f.player.toldToDig)
    }

    @Test
    fun `Edmond reminds a player without berries where to find them`() {
        val f = Fixture(STAGE_STARTED)
        f.talkTo(EDMOND_TOP)
        assertTrue(f.said("Have you got the dwellberries yet?"))
        assertTrue(f.said("McGrubor's Wood"))
        f.give("obj.dwellberries")
        f.talkTo(EDMOND_TOP)
        assertTrue(f.said("Take them to my wife Alrena"))
    }

    @Test
    fun `Alrena takes the berries and makes the mask in one step`() {
        val f = Fixture(STAGE_STARTED)
        f.talkTo("npc.alrena")
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(0, f.count("obj.gasmask"))
        assertTrue(f.said("I'll try to get some"))

        f.give("obj.dwellberries", 2)
        f.talkTo("npc.alrena")
        assertEquals(STAGE_HAS_GAS_MASK, f.stage())
        assertEquals(1, f.count("obj.dwellberries"), "only one bunch is taken")
        assertEquals(1, f.count("obj.gasmask"))
    }

    @Test
    fun `a full pack never loses the gas mask`() {
        val f = Fixture(STAGE_STARTED)
        f.give("obj.dwellberries")
        f.fill()
        f.talkTo("npc.alrena")
        assertEquals(STAGE_HAS_GAS_MASK, f.stage())
        assertEquals(1, f.count("obj.gasmask") + f.onFloor("obj.gasmask"))
    }

    @Test
    fun `the spare mask lives in the cupboard and is only handed to someone without one`() {
        val f = Fixture(STAGE_HAS_GAS_MASK)
        f.openCupboard()
        assertTrue(f.hasLoc("loc.alrenascupboardopen", CUPBOARD))
        assertEquals(0, f.count("obj.gasmask"), "opening the cupboard gives nothing")
        f.searchCupboard()
        assertEquals(1, f.count("obj.gasmask"))
        f.searchCupboard()
        assertEquals(1, f.count("obj.gasmask"), "a second mask is not handed over")
        assertTrue(f.said("find nothing"))

        val wearing = Fixture(STAGE_HAS_GAS_MASK)
        wearing.give("obj.gasmask")
        wearing.wear("obj.gasmask")
        wearing.openCupboard()
        wearing.searchCupboard()
        assertEquals(0, wearing.count("obj.gasmask"))

        val early = Fixture(STAGE_STARTED)
        early.openCupboard()
        early.searchCupboard()
        assertEquals(0, early.count("obj.gasmask"))
    }

    @Test
    fun `water needs Edmond's plan first and only buckets soften the soil`() {
        val f = Fixture(STAGE_HAS_GAS_MASK)
        f.give("obj.bucket_water")
        f.pourOnPatch()
        assertTrue(f.said("see no reason"))
        assertEquals(1, f.count("obj.bucket_water"))
        assertEquals(0, f.player.bucketsPoured)

        f.player.toldToDig = true
        f.pourOnPatch()
        assertEquals(0, f.count("obj.bucket_water"))
        assertEquals(1, f.count("obj.bucket_empty"))
        assertEquals(1, f.player.bucketsPoured)
        assertTrue(f.said("softens slightly"))

        val early = Fixture(STAGE_STARTED)
        early.player.toldToDig = true
        early.give("obj.bucket_water")
        early.pourOnPatch()
        assertEquals(1, early.count("obj.bucket_water"))
        assertEquals(0, early.player.bucketsPoured)
    }

    @Test
    fun `Edmond counts the buckets still needed`() {
        val f = Fixture(STAGE_HAS_GAS_MASK)
        f.talkTo(EDMOND_TOP)
        assertTrue(f.player.toldToDig)
        assertTrue(f.said("four buckets"))
        f.player.bucketsPoured = 3
        f.talkTo(EDMOND_TOP)
        assertTrue(f.said("one more bucket"))
    }

    @Test
    fun `the soil stays hard until it is softened`() {
        val f = Fixture(STAGE_HAS_GAS_MASK)
        f.at(PATCH_SIDE)
        assertTrue(f.garden.claims(f.player))
        f.dig()
        assertTrue(f.said("The ground is rather hard"))
        assertEquals(STAGE_HAS_GAS_MASK, f.stage())
        assertFalse(f.player.mudDug)
        assertEquals(PATCH_SIDE, f.player.coords)
    }

    @Test
    fun `only the ground beside the patch is claimed by the spade`() {
        val f = Fixture(STAGE_SOIL_SOFTENED)
        f.at(PATCH_SIDE.translateX(5))
        assertFalse(f.garden.claims(f.player))
        f.at(PATCH_SIDE)
        assertTrue(f.garden.claims(f.player))
        f.at(CoordGrid(PATCH_SIDE.x, PATCH_SIDE.z, 1))
        assertFalse(f.garden.claims(f.player))
    }

    @Test
    fun `a spade used on the patch digs the same way`() {
        val f = Fixture(STAGE_SOIL_SOFTENED)
        f.give("obj.spade")
        f.spadeOnPatch("loc.plaguemudpatch1", CoordGrid(2566, 3331, 0))
        assertEquals(STAGE_DUG_TUNNEL, f.stage())
        assertTrue(f.player.mudDug)
        assertEquals(EdmondsGarden.SEWER_ARRIVAL, f.player.coords)
    }

    @Test
    fun `an open hole is climbed rather than dug again`() {
        val f = Fixture(STAGE_DUG_TUNNEL)
        f.player.mudDug = true
        f.at(PATCH_SIDE)
        f.dig()
        assertEquals(STAGE_DUG_TUNNEL, f.stage())
        assertEquals(EdmondsGarden.SEWER_ARRIVAL, f.player.coords)
        assertTrue(f.said("You climb down into the hole."))
    }

    @Test
    fun `the mud pile leads back up to the garden`() {
        val f = Fixture(STAGE_DUG_TUNNEL)
        f.at(EdmondsGarden.SEWER_ARRIVAL)
        f.mudPile()
        assertEquals(ArdougneSewer.GARDEN_ARRIVAL, f.player.coords)
    }

    @Test
    fun `the grill cannot be pulled alone and rope only helps after trying`() {
        val f = Fixture(STAGE_DUG_TUNNEL)
        f.give("obj.rope")
        f.useRopeOnGrill()
        assertEquals(STAGE_DUG_TUNNEL, f.stage())
        assertEquals(1, f.count("obj.rope"))
        assertEquals(PIPE_BLOCKED, f.player.pipeState)

        f.pullGrill()
        assertTrue(f.said("too secure"))
        assertEquals(STAGE_GRILL_CHECKED, f.stage())
        assertTrue(f.player.grillChecked)

        f.useRopeOnGrill()
        assertEquals(STAGE_ROPE_TIED, f.stage())
        assertEquals(0, f.count("obj.rope"))
        f.give("obj.rope")
        f.useRopeOnGrill()
        assertEquals(1, f.count("obj.rope"), "a second rope is not used")
        assertTrue(f.said("already a rope"))
    }

    @Test
    fun `Edmond pulls the grill with the player and the pipe opens`() {
        val f = Fixture(STAGE_ROPE_TIED)
        f.player.pipeState = PIPE_ROPE_TIED
        f.at(ArdougneSewer.GRILL_TILE.translateZ(3))
        f.talkTo(EDMOND_BOTTOM, EDMOND_BELOW_TILE)
        assertEquals(STAGE_GRILL_REMOVED, f.stage())
        assertEquals(PIPE_OPEN, f.player.pipeState)
        assertEquals(Edmond.PLAYER_PULL_TILE, f.player.coords)
        assertTrue(f.said("Jethick"))
    }

    @Test
    fun `Edmond asks for rope before he will pull`() {
        val f = Fixture(STAGE_GRILL_CHECKED)
        f.at(ArdougneSewer.GRILL_TILE.translateZ(3))
        f.talkTo(EDMOND_BOTTOM, EDMOND_BELOW_TILE)
        assertEquals(STAGE_GRILL_CHECKED, f.stage())
        assertTrue(f.said("get some rope"))
    }

    @Test
    fun `the pipe stays shut until the grill is off and needs the gas mask`() {
        val f = Fixture(STAGE_GRILL_CHECKED)
        f.at(ArdougneSewer.PIPE_ARRIVAL)
        f.climbPipe()
        assertTrue(f.said("grill blocking your way"))
        assertEquals(ArdougneSewer.PIPE_ARRIVAL, f.player.coords)

        f.player.pipeState = PIPE_OPEN
        f.climbPipe()
        assertEquals(ArdougneSewer.PIPE_ARRIVAL, f.player.coords)
        f.give("obj.gasmask")
        f.climbPipe()
        assertEquals(ArdougneSewer.PIPE_ARRIVAL, f.player.coords, "a mask in the pack is not worn")
        f.wear("obj.gasmask")
        f.climbPipe()
        assertEquals(ArdougneSewer.SQUARE_ARRIVAL, f.player.coords)
    }

    @Test
    fun `the manhole in the square leads back down`() {
        val f = Fixture(STAGE_FREED_ELENA)
        f.at(ArdougneSewer.SQUARE_ARRIVAL)
        f.manhole("loc.plaguemanholeclosed")
        assertTrue(f.hasLoc("loc.plaguemanholeopen", MANHOLE))
        f.manhole("loc.plaguemanholeopen")
        assertEquals(ArdougneSewer.PIPE_ARRIVAL, f.player.coords)
    }

    @Test
    fun `Jethick helps once he has seen the picture and re-offers a lost book`() {
        val f = Fixture(STAGE_GRILL_REMOVED)
        f.talkTo("npc.jethick")
        assertTrue(f.player.metJethick)
        assertTrue(f.player.pictureAsked)
        assertEquals(STAGE_GRILL_REMOVED, f.stage())
        f.talkTo("npc.jethick")
        assertTrue(f.said("see a picture?"))

        f.give("obj.elena_picture")
        f.fill()
        f.talkTo("npc.jethick")
        assertEquals(STAGE_GOT_BOOK, f.stage())
        assertEquals(0, f.count("obj.turnip_book"), "no room, no book")
        assertTrue(f.said("you don't have room"))

        f.take("obj.bronze_dagger")
        f.talkTo("npc.jethick")
        assertEquals(1, f.count("obj.turnip_book"))

        f.take("obj.turnip_book")
        f.talkTo("npc.jethick")
        assertEquals(1, f.count("obj.turnip_book"), "a lost book is handed over again")
    }

    @Test
    fun `declining the book does not stop Jethick pointing the way`() {
        val f = Fixture(STAGE_GRILL_REMOVED)
        f.give("obj.elena_picture")
        f.choose(2)
        f.talkTo("npc.jethick")
        assertEquals(STAGE_GOT_BOOK, f.stage())
        assertEquals(0, f.count("obj.turnip_book"))
    }

    @Test
    fun `Ted only opens the door to the book`() {
        val f = Fixture(STAGE_GOT_BOOK)
        f.at(REHNISON_OUTSIDE)
        f.rehnisonDoor()
        assertTrue(f.said("Go away"))
        assertEquals(STAGE_GOT_BOOK, f.stage())

        f.give("obj.turnip_book")
        f.rehnisonDoor()
        assertEquals(STAGE_BOOK_RETURNED, f.stage())
        assertEquals(0, f.count("obj.turnip_book"))
        assertTrue(f.hasLoc("loc.rehnisondooropen", CoordGrid(2531, 3329, 0)) || f.hasOpenRehnisonDoor())

        f.give("obj.turnip_book")
        f.rehnisonDoor()
        assertEquals(STAGE_BOOK_RETURNED, f.stage())
        assertEquals(1, f.count("obj.turnip_book"), "a second book is not taken")
    }

    @Test
    fun `the family stairs link both floors`() {
        val f = Fixture(STAGE_BOOK_RETURNED)
        f.at(CoordGrid(2528, 3332, 0))
        f.stairs("loc.rehnisonstairs", CoordGrid(2527, 3332, 0))
        assertEquals(RehnisonHouse.UPSTAIRS, f.player.coords)
        f.stairs("loc.rehnisonstairstop", CoordGrid(2527, 3332, 1))
        assertEquals(RehnisonHouse.DOWNSTAIRS, f.player.coords)
    }

    @Test
    fun `Milli only talks to someone the Rehnisons have let in`() {
        val f = Fixture(STAGE_GOT_BOOK)
        f.talkTo("npc.milli")
        assertEquals(STAGE_GOT_BOOK, f.stage())
        assertTrue(f.said("not supposed to talk to strangers"))

        val g = Fixture(STAGE_BOOK_RETURNED)
        g.talkTo("npc.milli")
        assertEquals(STAGE_TALKED_MILLI, g.stage())
        g.talkTo("npc.milli")
        assertEquals(STAGE_TALKED_MILLI, g.stage())
        assertTrue(g.said("Have you found Elena yet?"))
    }

    @Test
    fun `the plague house will not open without clearance`() {
        val f = Fixture(STAGE_TALKED_MILLI)
        f.at(PLAGUE_DOOR_OUTSIDE)
        f.choose(3)
        f.plagueHouseDoor()
        assertEquals(STAGE_TALKED_MILLI, f.stage())
        assertTrue(f.said("black cross"))

        f.choose(2)
        f.plagueHouseDoor()
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())
        assertEquals(PLAGUE_DOOR_OUTSIDE, f.player.coords)

        val early = Fixture(STAGE_GOT_BOOK)
        early.at(PLAGUE_DOOR_OUTSIDE)
        early.plagueHouseDoor()
        assertEquals(STAGE_GOT_BOOK, early.stage())
        assertEquals(PLAGUE_DOOR_OUTSIDE, early.player.coords)
    }

    @Test
    fun `the clerk lets someone with a kidnapping through to Bravek`() {
        val f = Fixture(STAGE_MOURNER_REFUSED)
        f.choose(3)
        f.talkTo("npc.clerk")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())

        f.choose(2, 2)
        f.talkTo("npc.clerk")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage(), "leaving him alone changes nothing")

        f.choose(2, 3)
        f.talkTo("npc.clerk")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())
        assertTrue(f.said("hour or so"))

        f.choose(1, 2, 2, 2)
        f.talkTo("npc.clerk")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())

        f.choose(1, 1)
        f.talkTo("npc.clerk")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())

        f.choose(1, 2, 3)
        f.talkTo("npc.clerk")
        assertEquals(STAGE_CLERK_PERMISSION, f.stage())
        f.talkTo("npc.clerk")
        assertTrue(f.said("Bravek will see you now"))

        val early = Fixture(STAGE_BOOK_RETURNED)
        early.choose(2)
        early.talkTo("npc.clerk")
        assertEquals(STAGE_BOOK_RETURNED, early.stage())
        assertFalse(early.said("permission"))
    }

    @Test
    fun `Bravek's door only opens for someone the clerk has let in`() {
        val f = Fixture(STAGE_MOURNER_REFUSED)
        f.at(CoordGrid(2529, 3314, 0))
        f.bravekDoor()
        assertTrue(f.said("In a meeting"))
        assertFalse(f.hasLoc("loc.bravekdooropen", CoordGrid(2531, 3314, 0)))

        val g = Fixture(STAGE_CLERK_PERMISSION)
        g.at(CoordGrid(2529, 3314, 0))
        g.bravekDoor()
        assertTrue(g.hasOpenBravekDoor())
    }

    @Test
    fun `Bravek hands over the recipe and takes a second one back only if lost`() {
        val f = Fixture(STAGE_CLERK_PERMISSION)
        f.choose(1, 3)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_TALKED_BRAVEK, f.stage())
        assertEquals(1, f.count("obj.scruffy_note"))
        assertTrue(f.player.gotNote)

        f.talkTo("npc.bravek")
        assertEquals(1, f.count("obj.scruffy_note"), "he does not hand over a second note")

        f.take("obj.scruffy_note")
        f.talkTo("npc.bravek")
        assertEquals(1, f.count("obj.scruffy_note"))
    }

    @Test
    fun `a full pack gets no note but the stage waits`() {
        val f = Fixture(STAGE_CLERK_PERMISSION)
        f.fill()
        f.choose(1, 3)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_CLERK_PERMISSION, f.stage())
        assertEquals(0, f.count("obj.scruffy_note"))
    }

    @Test
    fun `Bravek is deaf to everything before the clerk lets the player in`() {
        val f = Fixture(STAGE_MOURNER_REFUSED)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())
        assertTrue(f.said("those mourners"))
    }

    @Test
    fun `the note reads in Trudi's handwriting`() {
        val f = Fixture(STAGE_TALKED_BRAVEK)
        f.give("obj.scruffy_note")
        f.held("obj.scruffy_note")
        assertTrue(f.said("bncket of nnilk"))
        assertEquals(1, f.count("obj.scruffy_note"))
    }

    @Test
    fun `the cure is mixed in two exact steps and nothing is lost without both parts`() {
        val f = Fixture(STAGE_TALKED_BRAVEK)
        f.give("obj.chocolate_dust")
        f.give("obj.snape_grass")
        f.give("obj.bucket_milk")
        f.useOn("obj.snape_grass", "obj.bucket_milk")
        assertEquals(1, f.count("obj.snape_grass"), "snape grass goes into chocolatey milk only")
        assertEquals(1, f.count("obj.bucket_milk"))
        f.useOn("obj.snape_grass", "obj.chocolate_dust")
        assertEquals(1, f.count("obj.snape_grass"))
        f.useOn("obj.chocolate_dust", "obj.bucket_milk")
        assertEquals(1, f.count("obj.chocolaty_milk"))
        assertEquals(0, f.count("obj.chocolate_dust"))
        assertEquals(0, f.count("obj.bucket_milk"))
    }

    @Test
    fun `Bravek takes the cure and rewards it with the warrant in one step`() {
        val f = Fixture(STAGE_TALKED_BRAVEK)
        f.give("obj.hangover_cure")
        f.choose(3)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_GOT_WARRANT, f.stage(), f.output())
        assertEquals(0, f.count("obj.hangover_cure"))
        assertEquals(1, f.count("obj.warrant"))

        val g = Fixture(STAGE_TALKED_BRAVEK)
        g.give("obj.hangover_cure")
        g.useOnNpc("npc.bravek", "obj.hangover_cure")
        assertEquals(STAGE_CURED_BRAVEK, g.stage())
        assertEquals(0, g.count("obj.hangover_cure"))
        g.choose(3)
        g.talkTo("npc.bravek")
        assertEquals(STAGE_GOT_WARRANT, g.stage())
        assertEquals(1, g.count("obj.warrant"))
    }

    @Test
    fun `a full pack leaves the warrant until there is room`() {
        val f = Fixture(STAGE_CURED_BRAVEK)
        f.fill()
        f.choose(3)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_CURED_BRAVEK, f.stage())
        assertEquals(0, f.count("obj.warrant"))
        f.take("obj.bronze_dagger")
        f.choose(3)
        f.talkTo("npc.bravek")
        assertEquals(STAGE_GOT_WARRANT, f.stage())
        assertEquals(1, f.count("obj.warrant"))
    }

    @Test
    fun `a cure is useless on Bravek before the clerk, and other items on him do nothing`() {
        val f = Fixture(STAGE_MOURNER_REFUSED)
        f.give("obj.hangover_cure")
        f.useOnNpc("npc.bravek", "obj.hangover_cure")
        assertEquals(STAGE_MOURNER_REFUSED, f.stage())
        assertEquals(1, f.count("obj.hangover_cure"))

        val g = Fixture(STAGE_TALKED_BRAVEK)
        g.give("obj.bucket_milk")
        g.useOnNpc("npc.bravek", "obj.bucket_milk")
        assertEquals(1, g.count("obj.bucket_milk"))
        assertTrue(g.said("doesn't seem interested"))
    }

    @Test
    fun `Bravek is friendly once cured`() {
        val f = Fixture(STAGE_SNEAKED_IN)
        f.talkTo("npc.bravek")
        assertTrue(f.said("little drop of whisky"))
        assertEquals(STAGE_SNEAKED_IN, f.stage())
    }

    @Test
    fun `the guards only let the warrant through`() {
        val f = Fixture(STAGE_GOT_WARRANT)
        f.at(PLAGUE_DOOR_OUTSIDE)
        f.plagueHouseDoor()
        assertEquals(STAGE_GOT_WARRANT, f.stage(), "the warrant must be in the pack")
        assertEquals(PLAGUE_DOOR_OUTSIDE, f.player.coords)

        f.give("obj.warrant")
        f.plagueHouseDoor()
        assertEquals(STAGE_SNEAKED_IN, f.stage())
        assertEquals(1, f.count("obj.warrant"), "the warrant can be shown again")
        assertEquals(PLAGUE_DOOR.translateZ(-1), f.player.coords)
    }

    @Test
    fun `showing the warrant to a guard works the same as using the door`() {
        val f = Fixture(STAGE_GOT_WARRANT)
        f.at(PLAGUE_DOOR_OUTSIDE)
        f.give("obj.warrant")
        f.useOnNpc("npc.mourner_elena_guard", "obj.warrant", PLAGUE_DOOR_OUTSIDE.translateX(1))
        assertEquals(STAGE_SNEAKED_IN, f.stage())

        val g = Fixture(STAGE_TALKED_MILLI)
        g.give("obj.warrant")
        g.useOnNpc("npc.mourner_elena_guard", "obj.warrant", PLAGUE_DOOR_OUTSIDE.translateX(1))
        assertEquals(STAGE_TALKED_MILLI, g.stage(), "no warrant has been earned yet")

        val h = Fixture(STAGE_GOT_WARRANT)
        h.give("obj.bucket_milk")
        h.useOnNpc("npc.mourner_elena_guard", "obj.bucket_milk", PLAGUE_DOOR_OUTSIDE.translateX(1))
        assertEquals(STAGE_GOT_WARRANT, h.stage())
        assertEquals(1, h.count("obj.bucket_milk"))
    }

    @Test
    fun `the key is in the barrel only while it is needed`() {
        val f = Fixture(STAGE_GOT_WARRANT)
        f.searchBarrel()
        assertEquals(0, f.count("obj.elenakey"), "not before getting in")

        val g = Fixture(STAGE_SNEAKED_IN)
        g.searchBarrel()
        assertEquals(1, g.count("obj.elenakey"))
        g.searchBarrel()
        assertEquals(1, g.count("obj.elenakey"), "one key at a time")
        g.take("obj.elenakey")
        g.searchBarrel()
        assertEquals(1, g.count("obj.elenakey"), "a lost key is found again")

        val h = Fixture(STAGE_SNEAKED_IN)
        h.fill()
        h.searchBarrel()
        assertEquals(0, h.count("obj.elenakey"))
        assertTrue(h.said("don't have room"))

        val done = Fixture(STAGE_UNLOCKED_CELL)
        done.searchBarrel()
        assertEquals(0, done.count("obj.elenakey"))
    }

    @Test
    fun `the cell stays locked without the key and Elena says where it is`() {
        val f = Fixture(STAGE_SNEAKED_IN)
        f.cellDoor()
        assertEquals(STAGE_SNEAKED_IN, f.stage())
        assertTrue(f.said("The door is locked"))
        assertTrue(f.said("heard them stashing it"))
        assertTrue(f.player.keyAsked)

        f.give("obj.elenakey")
        f.cellDoor()
        assertEquals(STAGE_UNLOCKED_CELL, f.stage())
        assertEquals(1, f.count("obj.elenakey"))
        f.cellDoor()
        assertEquals(STAGE_UNLOCKED_CELL, f.stage())

        val early = Fixture(STAGE_GOT_WARRANT)
        early.give("obj.elenakey")
        early.cellDoor()
        assertEquals(STAGE_GOT_WARRANT, early.stage(), "the key is useless outside the quest path")
    }

    @Test
    fun `the plague house stairs link both floors`() {
        val f = Fixture(STAGE_SNEAKED_IN)
        f.stairs("loc.plaguehousestairsdown", CoordGrid(2536, 3268, 0))
        assertEquals(PlagueHouse.BASEMENT_ARRIVAL, f.player.coords)
        f.stairs("loc.plaguehousestairsup", CoordGrid(2536, 9671, 0))
        assertEquals(PlagueHouse.GROUND_ARRIVAL, f.player.coords)
    }

    @Test
    fun `Elena frees herself and the cache hides the cell`() {
        val f = Fixture(STAGE_UNLOCKED_CELL)
        f.player.edmondBelow = true
        f.talkTo("npc.elenap", ELENA_CELL_TILE)
        assertEquals(STAGE_FREED_ELENA, f.stage())
        assertTrue(f.player.elenaHome)
        assertFalse(f.player.edmondBelow)
        assertEquals(0, f.player.vars["varp.qp"], "the quest is not complete yet")
        val cell = checkNotNull(ServerCacheManager.getNpc("npc.elenap".asRSCM()))
        assertNull(NpcInteractions(f.events).multiNpc(cell, f.player.vars))

        val locked = Fixture(STAGE_SNEAKED_IN)
        locked.talkTo("npc.elenap", ELENA_CELL_TILE)
        assertEquals(STAGE_SNEAKED_IN, locked.stage())
        assertTrue(locked.said("get me out of here"))
    }

    @Test
    fun `Edmond pays once and the scroll comes with the quest point`() {
        val f = Fixture(STAGE_FREED_ELENA)
        f.talkTo(EDMOND_TOP)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.count("obj.ardougnescroll"))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(2425, f.player.statMap.getXP("stat.mining"))

        f.talkTo(EDMOND_TOP)
        f.talkTo(EDMOND_TOP)
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(2425, f.player.statMap.getXP("stat.mining"))
    }

    @Test
    fun `a full pack drops the scroll instead of losing it`() {
        val f = Fixture(STAGE_FREED_ELENA)
        f.fill()
        f.talkTo(EDMOND_TOP)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count("obj.ardougnescroll"))
        assertEquals(1, f.onFloor("obj.ardougnescroll"))
        assertEquals(1, f.player.vars["varp.qp"])
    }

    @Test
    fun `the first scroll teaches the spell and later ones burn`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give("obj.ardougnescroll", 2)
        f.held("obj.ardougnescroll")
        assertTrue(f.player.readScroll)
        assertEquals(1, f.count("obj.ardougnescroll"))
        assertTrue(f.said("Ardougne Teleport spell"))

        f.held("obj.ardougnescroll")
        assertEquals(0, f.count("obj.ardougnescroll"))
        assertEquals(1, f.count("obj.ashes"))
        assertTrue(f.said("bursts into flame"))
    }

    @Test
    fun `Edmond hands out spare scrolls until the spell is known`() {
        val f = Fixture(STAGE_COMPLETE)
        f.choose(1)
        f.talkTo(EDMOND_TOP)
        assertEquals(1, f.count("obj.ardougnescroll"))

        f.choose(2)
        f.talkTo(EDMOND_TOP)
        assertEquals(1, f.count("obj.ardougnescroll"))

        f.player.readScroll = true
        f.talkTo(EDMOND_TOP)
        assertEquals(1, f.count("obj.ardougnescroll"), "no option once the spell is known")
    }

    @Test
    fun `the journal follows the stages`() {
        val expected =
            mapOf(
                STAGE_STARTED to "dwellberries",
                STAGE_HAS_GAS_MASK to "four buckets of water",
                STAGE_SOIL_SOFTENED to "spade",
                STAGE_DUG_TUNNEL to "pipe to the south",
                STAGE_GRILL_CHECKED to "rope",
                STAGE_ROPE_TIED to "tied the rope",
                STAGE_GRILL_REMOVED to "look for",
                STAGE_GOT_BOOK to "Rehnison",
                STAGE_BOOK_RETURNED to "Milli",
                STAGE_TALKED_MILLI to "plague house",
                STAGE_MOURNER_REFUSED to "head mourner",
                STAGE_CLERK_PERMISSION to "head mourner",
                STAGE_TALKED_BRAVEK to "hangover cure",
                STAGE_CURED_BRAVEK to "feeling much better",
                STAGE_GOT_WARRANT to "warrant",
                STAGE_SNEAKED_IN to "key",
                STAGE_UNLOCKED_CELL to "unlocked the cell door",
                STAGE_FREED_ELENA to "manhole",
            )
        for ((stage, text) in expected) {
            val journal = Fixture(stage).journal()
            assertTrue(journal.contains(text), "stage $stage: $journal")
        }
        val removed = Fixture(STAGE_GRILL_REMOVED)
        removed.player.pictureAsked = true
        assertTrue(removed.journal().contains("picture of Elena"))
        assertTrue(Fixture(STAGE_COMPLETE).completedJournal().contains("QUEST COMPLETE"))
        val carrying = Fixture(STAGE_STARTED)
        carrying.give("obj.dwellberries")
        assertTrue(carrying.journal().contains("I have some dwellberries."))
    }

    @Test
    fun `the great doors stay shut until the quest is done`() {
        val f = Fixture(STAGE_FREED_ELENA)
        f.at(CoordGrid(2556, 3299, 0))
        f.gate("loc.ardougnedoor_r", CoordGrid(2557, 3299, 0))
        assertTrue(f.said("will not open"))
        assertEquals(CoordGrid(2556, 3299, 0), f.player.coords)

        val g = Fixture(STAGE_COMPLETE)
        g.at(CoordGrid(2556, 3299, 0))
        g.gate("loc.ardougnedoor_r", CoordGrid(2557, 3299, 0))
        assertEquals(CoordGrid(2559, 3299, 0), g.player.coords)
        g.gate("loc.ardougnedoor_r", CoordGrid(2557, 3299, 0))
        assertEquals(CoordGrid(2556, 3299, 0), g.player.coords)
    }

    @Test
    fun `the plain city folk and mourners have their say`() {
        val f = Fixture(STAGE_GRILL_REMOVED)
        f.talkTo("npc.mourner1")
        assertTrue(f.said("how did you get over here"))
        f.talkTo("npc.w_ardougnecitizen1")
        assertTrue(f.said("Curse King Tyras"))
        f.choose(3)
        f.talkTo("npc.headmourner")
        assertTrue(f.said("How did you get into West Ardougne?"))

        val done = Fixture(STAGE_COMPLETE)
        done.talkTo("npc.mourner1")
        assertTrue(done.said("Stand back citizen"))
        done.talkTo("npc.headmourner")
        assertTrue(done.said("Stand back citizen"))
        done.talkTo("npc.jethick")
        assertTrue(done.said("surprised you're still here"))
    }

    @Test
    fun `the mourner near Edmond's follows what the player has been doing`() {
        val f = Fixture(STAGE_DUG_TUNNEL)
        f.talkTo("npc.mournertwa")
        assertTrue(f.said("Been digging have we?"))
        val g = Fixture(STAGE_COMPLETE)
        g.talkTo("npc.mournertwa")
        assertTrue(g.said("fancy dress"))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("plague-city-test")
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

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 4411L
                observerUUID = 4411L
                slotId = 1
                assignUid()
                coords = GARDEN
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
        lateinit var garden: EdmondsGarden

        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
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
                    ZonePlayerActivityBitSet(),
                )
            locRepo =
                LocRepository(
                    clock,
                    LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)),
                    regions,
                )
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            PlagueCityMap.apply(collision, storage)
            val worldRepo = WorldRepository(updates)
            val doors = QuestDoors(locRepo)
            garden = EdmondsGarden(plague)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(plague) { scripts.startup() }
            with(garden) { scripts.startup() }
            with(ArdougneSewer(plague, locRepo)) { scripts.startup() }
            with(HangoverCure()) { scripts.startup() }
            with(Edmond(plague, objRepo, locRepo, worldRepo, collision)) { scripts.startup() }
            with(Alrena(plague, locRepo, objRepo)) { scripts.startup() }
            with(Jethick(plague)) { scripts.startup() }
            with(Citizens()) { scripts.startup() }
            with(Mourners(plague)) { scripts.startup() }
            with(CivicOffice(plague, doors)) { scripts.startup() }
            with(RehnisonHouse(plague, doors)) { scripts.startup() }
            with(PlagueHouse(plague, doors)) { scripts.startup() }
            with(WestArdougneGate(plague, locRepo)) { scripts.startup() }
            if (stage > 0) VarPlayerIntMapSetter.set(player, "varbit.plaguecity_progress", stage)
            if (stage >= STAGE_HAS_GAS_MASK) player.toldToDig = stage > STAGE_HAS_GAS_MASK
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = plague.stage(player)

        fun journal(): String = plague.questLog(access())

        fun completedJournal(): String = plague.completedLog(access())

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
            take(obj)
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

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

        fun hasLoc(type: String, coords: CoordGrid): Boolean = locRepo.findLoc(coords, type)

        fun hasOpenRehnisonDoor(): Boolean =
            listOf(
                    CoordGrid(2531, 3328, 0),
                    CoordGrid(2531, 3329, 0),
                    CoordGrid(2530, 3328, 0),
                    CoordGrid(2532, 3328, 0),
                )
                .any { hasLoc("loc.rehnisondooropen", it) }

        fun hasOpenBravekDoor(): Boolean =
            listOf(
                    CoordGrid(2530, 3314, 0),
                    CoordGrid(2530, 3315, 0),
                    CoordGrid(2530, 3313, 0),
                    CoordGrid(2531, 3314, 0),
                    CoordGrid(2529, 3314, 0),
                )
                .any { hasLoc("loc.bravekdooropen", it) }

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

        fun dig() {
            dispatch { with(garden as SpadeDigHook) { dig() } }
        }

        fun pourOnPatch() = locU("loc.plaguemudpatch2", MUD_PATCH, LocShape.GroundDecor, "obj.bucket_water")

        fun spadeOnPatch(symbol: String, at: CoordGrid) =
            locU(symbol, at, LocShape.GroundDecor, "obj.spade")

        fun pullGrill() = locOp("loc.plague_grill", GRILL, LocShape.WallDecorStraightNoOffset, LocAngle.South)

        fun useRopeOnGrill() =
            locU("loc.plague_grill", GRILL, LocShape.WallDecorStraightNoOffset, "obj.rope", LocAngle.South)

        fun climbPipe() =
            locOp("loc.plaguesewerpipe_open", PIPE, LocShape.CentrepieceStraight, LocAngle.South)

        fun mudPile() =
            locOp("loc.plaguemudpile", ArdougneSewer.MUD_PILE_TILE, LocShape.CentrepieceStraight, LocAngle.East)

        fun manhole(symbol: String) =
            locOp(symbol, MANHOLE, LocShape.CentrepieceStraight, LocAngle.West)

        fun rehnisonDoor() =
            locOp("loc.rehnisondoorshut", CoordGrid(2531, 3328, 0), LocShape.WallStraight, LocAngle.North)

        fun stairs(symbol: String, at: CoordGrid) =
            locOp(symbol, at, LocShape.CentrepieceStraight, LocAngle.North)

        fun plagueHouseDoor() =
            locOp(
                "loc.plagueelenadoorshut",
                PLAGUE_DOOR,
                LocShape.WallStraight,
                LocAngle.South,
            )

        fun bravekDoor() =
            locOp("loc.bravekdoorshut", CoordGrid(2530, 3314, 0), LocShape.WallStraight, LocAngle.West)

        fun searchBarrel() =
            locOp("loc.plaguekeybarrel", CoordGrid(2534, 3268, 0), LocShape.CentrepieceStraight, LocAngle.West)

        fun cellDoor() =
            locOp("loc.elenagateshut", CoordGrid(2539, 9672, 0), LocShape.WallStraight, LocAngle.East)

        fun openCupboard() =
            locOp("loc.alrenascupboardshut", CUPBOARD, LocShape.CentrepieceStraight, LocAngle.West)

        fun searchCupboard() =
            locOp("loc.alrenascupboardopen", CUPBOARD, LocShape.CentrepieceStraight, LocAngle.West)

        fun gate(symbol: String, at: CoordGrid) =
            locOp(symbol, at, LocShape.CentrepieceStraight, LocAngle.East)

        fun held(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch {
                val event = HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv)
                assertTrue(events.publish(this, event))
            }
        }

        fun useOn(first: String, second: String) {
            val firstSlot = player.inv.indexOfFirst { it?.id == first.asRSCM() }
            val secondSlot = player.inv.indexOfFirst { it?.id == second.asRSCM() }
            val firstType = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val secondType = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            val handled = events.contains(HeldUEvents.Type::class.java, key(firstType.id, secondType.id))
            if (!handled) return
            dispatch {
                val event = HeldUEvents.Type(firstType, firstSlot, secondType, secondSlot)
                assertTrue(events.publish(this, event))
            }
        }

        private fun key(first: Int, second: Int): Long = EventBus.composeLongKey(first, second)

        private fun locOp(symbol: String, coords: CoordGrid, shape: LocShape, angle: LocAngle) {
            val loc = bound(symbol, coords, shape, angle)
            val event =
                LocInteractions(BoundValidator(collision), events)
                    .opTrigger(player, loc, InteractionOp.Op1)
            if (event == null) {
                return
            }
            dispatch { assertTrue(events.publish(this, event)) }
        }

        private fun locU(
            symbol: String,
            coords: CoordGrid,
            shape: LocShape,
            obj: String,
            angle: LocAngle = LocAngle.West,
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
            val info = LocInfo(LocLayerConstants.of(shape.id), coords, LocEntity(type.id, shape.id, angle.id))
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
                    listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox")
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
        const val EDMOND_TOP = "npc.edmond_top"
        const val EDMOND_BOTTOM = "npc.edmond_bottom"

        val GARDEN = CoordGrid(2567, 3334, 0)
        val ALRENA_TILE = CoordGrid(2570, 3334, 0)
        val PATCH_SIDE = CoordGrid(2565, 3332, 0)
        val MUD_PATCH = EdmondsGarden.MUD_PATCH_TILE
        val GRILL = ArdougneSewer.GRILL_TILE
        val PIPE = CoordGrid(2514, 9737, 0)
        val EDMOND_BELOW_TILE = CoordGrid(2514, 9743, 0)
        val MANHOLE = CoordGrid(2529, 3303, 0)
        val CUPBOARD = CoordGrid(2574, 3334, 0)
        val REHNISON_OUTSIDE = CoordGrid(2531, 3327, 0)
        val PLAGUE_DOOR = CoordGrid(2533, 3272, 0)
        val PLAGUE_DOOR_OUTSIDE = CoordGrid(2533, 3273, 0)
        val ELENA_CELL_TILE = CoordGrid(2541, 9672, 0)

        private val restored = mutableListOf<() -> Unit>()
        private lateinit var cache: dev.openrune.filesystem.Cache

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            cache = ServerCacheManager.init(240)
            PlagueCityMap.load(cache)
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
