package org.rsmod.content.quest.area.ardougne.sheepherder

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import dev.openrune.types.NpcMode
import dev.openrune.types.varp.VarpLifetime
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.content.quest.area.ardougne.sheepherder.HerdingRules.Direction
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.BRUMTY
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.CATTLEPROD
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.FEED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.HALGRIVE
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.JACKET
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.ORBON
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_DISPOSED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.TROUSERS
import org.rsmod.content.quest.area.ardougne.sheepherder.npcs.CouncillorHalgrive
import org.rsmod.content.quest.area.ardougne.sheepherder.npcs.DoctorOrbon
import org.rsmod.content.quest.area.ardougne.sheepherder.npcs.FarmerBrumty
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
import org.rsmod.map.square.MapSquareKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Drives Sheep Herder's real scripts through the event bus on the real collision map around
 * Farmer Brumty's enclosure: the whole path, every refusal for coins, space, clothing and the
 * cattleprod, prods from every side and into fences, a real route driven prod by prod, the sheep
 * growing restless, heading home and being unstuck, the colours in different orders, feed outside
 * the pen, separate bones, lost-item recovery, save and reload, early reports and the payment.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SheepHerderInteractionTest {

    @Test fun `the whole quest from Halgrive's offer to the payment`() {
        val f = Fixture()
        f.start()
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.count(FEED))
        assertTrue(f.said("Doctor Orbon"))

        f.give(COINS, 100)
        f.buy()
        assertEquals(0, f.count(COINS))
        assertEquals(1, f.count(JACKET))
        assertEquals(1, f.count(TROUSERS))
        f.wear(JACKET, TROUSERS)
        f.give(CATTLEPROD)
        f.wear(CATTLEPROD)

        for (colour in SheepColour.entries) f.dispose(colour)
        assertEquals(STAGE_DISPOSED, f.stage())
        assertEquals(1, f.count(FEED), "the feed is reusable and never all used up")
        assertTrue(f.journal().contains("I should tell"))

        f.talk(HALGRIVE)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(3_100, f.count(COINS))
        assertEquals(4, f.player.vars["varp.qp"])
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.said("100 coins you paid"))
        assertTrue(f.sheep.completedLog(f.access()).contains("Halgrive paid me back"))
    }

    @Test fun `declining, a full pack and an earlier run all behave at the offer`() {
        val f = Fixture()
        f.choose(2)
        f.talk(HALGRIVE)
        assertEquals(0, f.stage())
        f.choose(1, 2)
        f.talk(HALGRIVE)
        assertEquals(0, f.stage())
        assertEquals(0, f.count(FEED))

        f.fill()
        f.start()
        assertEquals(0, f.stage(), "a full pack must not start the quest without the feed")
        assertTrue(f.said("Make some room"))
        f.player.inv[0] = null

        f.sheep.setState(f.player, SheepColour.RED, SheepState.BURNED)
        f.start()
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.count(FEED))
        assertEquals(SheepState.LOOSE, f.sheep.state(f.player, SheepColour.RED), "a new run starts clean")
    }

    @Test fun `Orbon sells the set for 100 coins after checking coins and space`() {
        val f = Fixture(STAGE_STARTED)
        f.give(COINS, 99)
        f.buy()
        assertEquals(99, f.count(COINS))
        assertEquals(0, f.count(JACKET))
        assertTrue(f.said("Come back when you have the 100 coins"))

        f.give(COINS, 1)
        f.fill()
        f.buy()
        assertEquals(100, f.count(COINS), "a full pack must not be charged")
        assertEquals(0, f.count(JACKET))
        assertTrue(f.said("more space in your pack"))

        f.choose(2)
        f.talk(ORBON)
        assertEquals(100, f.count(COINS), "turning the offer down costs nothing")

        val g = Fixture(STAGE_STARTED)
        g.give(COINS, 100)
        g.fill()
        g.player.inv[27] = null
        g.buy()
        assertEquals(0, g.count(COINS), "paying the whole stack frees a slot for the set")
        assertEquals(1, g.count(JACKET))
        assertEquals(1, g.count(TROUSERS))

        g.player.inv[5] = null
        g.give(COINS, 500)
        g.buy()
        assertEquals(500, g.count(COINS), "someone who still has the set is never charged again")
        assertEquals(1, g.count(JACKET))
        assertEquals(1, g.count(TROUSERS))
    }

    @Test fun `lost clothing is sold again for 100 coins, one missing piece at a time`() {
        val f = Fixture(STAGE_STARTED)
        f.give(COINS, 300)
        f.buy()
        assertEquals(200, f.count(COINS))
        f.wear(JACKET)
        f.drop(TROUSERS)

        f.buy()
        assertEquals(100, f.count(COINS))
        assertEquals(1, f.count(TROUSERS))
        assertEquals(0, f.count(JACKET), "only the missing piece is replaced")

        f.drop(TROUSERS)
        f.player.worn[checkNotNull(ServerCacheManager.getItem(JACKET.asRSCM())).wearpos1] = null
        f.buy()
        assertEquals(0, f.count(COINS))
        assertEquals(1, f.count(JACKET))
        assertEquals(1, f.count(TROUSERS))
    }

    @Test fun `protection and the wielded cattleprod gate every restricted interaction`() {
        val f = Fixture(STAGE_STARTED)
        f.give(FEED)
        val sheep = f.spawnSheep(SheepColour.RED, OPEN_FIELD)
        f.prod(sheep, from = OPEN_FIELD.translateZ(-1))
        assertTrue(f.said("without a full protective suit"))
        assertTrue(sheep.routeDestination.isEmpty())

        f.give(JACKET)
        f.give(TROUSERS)
        f.wear(JACKET)
        f.prod(sheep, from = OPEN_FIELD.translateZ(-1))
        assertTrue(f.said("without a full protective suit"), "one piece is not enough")
        f.wear(TROUSERS)

        f.prod(sheep, from = OPEN_FIELD.translateZ(-1))
        assertTrue(f.said("You need a cattleprod"))
        f.give(CATTLEPROD)
        f.prod(sheep, from = OPEN_FIELD.translateZ(-1))
        assertTrue(f.said("wield the cattleprod"))
        assertTrue(sheep.routeDestination.isEmpty())
        f.wear(CATTLEPROD)
        f.prod(sheep, from = OPEN_FIELD.translateZ(-1))
        assertEquals(OPEN_FIELD.translateZ(HerdingRules.PUSH_TILES), sheep.routeDestination.lastOrNull())

        f.unwear(TROUSERS)
        f.gate(from = HerdingRules.GATE_THRESHOLD[0])
        assertTrue(f.player.routeDestination.isEmpty(), "the gate opened without protection")
        assertTrue(f.said("I'm not going in without decent protective clothing"))
        f.wear(TROUSERS)
        f.gate(from = HerdingRules.GATE_THRESHOLD[0])
        assertEquals(PlagueEnclosure.INSIDE_X, f.player.routeDestination.lastOrNull()?.x)
        f.unwear(JACKET)
        f.gate(from = CoordGrid(PlagueEnclosure.INSIDE_X, 3361, 0))
        assertEquals(PlagueEnclosure.OUTSIDE_X, f.player.routeDestination.lastOrNull()?.x, "leaving is always allowed")

        f.sheep.setState(f.player, SheepColour.RED, SheepState.PENNED)
        val pen = f.spawnPenSheep(SheepColour.RED)
        f.feed(pen, SheepColour.RED)
        assertEquals(SheepState.PENNED, f.sheep.state(f.player, SheepColour.RED))
        f.sheep.setState(f.player, SheepColour.RED, SheepState.BONES)
        f.give(SheepColour.RED.bones)
        f.incinerate(SheepColour.RED)
        assertEquals(1, f.count(SheepColour.RED.bones), "unprotected burning must keep the bones")
        assertEquals(SheepState.BONES, f.sheep.state(f.player, SheepColour.RED))
    }

    @Test fun `sheep are only prodded during the quest`() {
        val f = Fixture()
        f.equip()
        val sheep = f.spawnSheep(SheepColour.RED, OPEN_FIELD)
        f.prod(sheep, from = OPEN_FIELD.translateZ(-1))
        assertTrue(f.said("no reason to go poking"))
        assertTrue(sheep.routeDestination.isEmpty())

        val g = Fixture(STAGE_DISPOSED)
        g.equip()
        val other = g.spawnSheep(SheepColour.RED, OPEN_FIELD)
        g.prod(other, from = OPEN_FIELD.translateZ(-1))
        assertTrue(g.said("done your part"))
        assertTrue(other.routeDestination.isEmpty())
    }

    @Test fun `a prod drives the sheep straight away from every side`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        for (dir in Direction.entries) {
            val sheep = f.spawnSheep(SheepColour.GREEN, OPEN_FIELD)
            f.prod(sheep, from = HerdingRules.standFor(OPEN_FIELD, dir))
            val expected = OPEN_FIELD.translate(dir.dx * HerdingRules.PUSH_TILES, dir.dz * HerdingRules.PUSH_TILES)
            assertEquals(expected, sheep.routeDestination.lastOrNull(), dir.name)
            assertEquals(NpcMode.None, sheep.mode, "a herded sheep stops grazing")
            f.remove(sheep)
        }
    }

    @Test fun `fences stop a sheep short or refuse the prod outright`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        val wall = f.spawnSheep(SheepColour.RED, CoordGrid(2594, 3358, 0))
        f.prod(wall, from = CoordGrid(2593, 3358, 0))
        assertTrue(wall.routeDestination.isEmpty())
        assertTrue(f.said("can't go any further east"))

        val north = f.spawnSheep(SheepColour.RED, CoordGrid(2600, 3367, 0))
        f.prod(north, from = CoordGrid(2600, 3368, 0))
        assertEquals(CoordGrid(2600, 3365, 0), north.routeDestination.lastOrNull(), "the north fence stops it")
        assertFalse(HerdingRules.isInPen(north.routeDestination.lastOrNull()!!))
    }

    @Test fun `the green sheep is driven home to the pen along a real route`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        val home = CoordGrid(2622, 3366, 0)
        val sheep = f.spawnSheep(SheepColour.GREEN, home)
        repeat(7) { f.drive(sheep, Direction.WEST) }
        assertEquals(SheepState.LOOSE, f.sheep.state(f.player, SheepColour.GREEN))
        f.drive(sheep, Direction.SOUTH)
        assertEquals(SheepState.PENNED, f.sheep.state(f.player, SheepColour.GREEN))
        assertEquals(home, sheep.coords, "the shared field sheep goes back to its pasture")
        assertTrue(f.said("jumps over the gate and into the enclosure"))
    }

    @Test fun `a penned colour can't be herded again and another sheep of it doesn't count`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        f.pen(SheepColour.BLUE)
        val second = f.spawnSheep(SheepColour.BLUE, OPEN_FIELD)
        f.prod(second, from = OPEN_FIELD.translateZ(-1))
        assertTrue(f.said("already a sheep like this in the pen"))
        assertTrue(second.routeDestination.isEmpty())
        assertEquals(SheepState.LOOSE, f.sheep.state(f.player, SheepColour.YELLOW))

        val pen = f.spawnPenSheep(SheepColour.BLUE)
        f.prod(pen, from = pen.coords.translateX(1))
        assertTrue(f.said("already in the enclosure"))
    }

    @Test fun `a sheep left alone heads home, and is put back if it gets stuck`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        val home = CoordGrid(2610, 3390, 0)
        val sheep = f.spawnSheep(SheepColour.YELLOW, home)
        f.prod(sheep, from = home.translateZ(1))
        sheep.teleport(f.collision, sheep.routeDestination.lastOrNull()!!)
        assertTrue(f.hasTimer(sheep, SheepHerding.RESTLESS_TIMER))

        f.herding.restless(sheep)
        assertFalse(f.hasTimer(sheep, SheepHerding.RESTLESS_TIMER))
        assertTrue(f.hasTimer(sheep, SheepHerding.STUCK_TIMER))
        assertTrue(sheep.routeDestination.isNotEmpty(), "the sheep walks a real route home")
        assertEquals(home, sheep.routeDestination.lastOrNull())

        f.prod(sheep, from = sheep.coords.translateZ(1))
        assertFalse(f.hasTimer(sheep, SheepHerding.STUCK_TIMER), "a prod cancels the trip home")
        assertTrue(f.hasTimer(sheep, SheepHerding.RESTLESS_TIMER))
        sheep.teleport(f.collision, sheep.routeDestination.lastOrNull()!!)

        f.herding.restless(sheep)
        f.herding.unstick(sheep)
        assertEquals(home, sheep.coords)
        assertEquals(NpcMode.Wander, sheep.mode)
        assertFalse(f.hasTimer(sheep, SheepHerding.STUCK_TIMER))
        assertEquals(SheepState.LOOSE, f.sheep.state(f.player, SheepColour.YELLOW))
    }

    @Test fun `the colours can be finished in any order, one at a time or all together`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        for (colour in listOf(SheepColour.YELLOW, SheepColour.RED, SheepColour.BLUE, SheepColour.GREEN)) {
            f.dispose(colour)
            assertEquals(SheepState.BURNED, f.sheep.state(f.player, colour))
        }
        assertEquals(STAGE_DISPOSED, f.stage())

        val g = Fixture(STAGE_STARTED)
        g.equip()
        for (colour in SheepColour.entries.reversed()) g.pen(colour)
        val pens = SheepColour.entries.associateWith { g.spawnPenSheep(it) }
        for (colour in listOf(SheepColour.GREEN, SheepColour.YELLOW, SheepColour.RED, SheepColour.BLUE)) {
            g.feed(pens.getValue(colour), colour)
            g.collect(colour)
        }
        assertEquals(STAGE_STARTED, g.stage())
        for (colour in listOf(SheepColour.BLUE, SheepColour.GREEN, SheepColour.RED)) g.incinerate(colour)
        assertEquals(STAGE_STARTED, g.stage())
        g.incinerate(SheepColour.YELLOW)
        assertEquals(STAGE_DISPOSED, g.stage())
    }

    @Test fun `the feed only works inside the enclosure and only once per colour`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        val field = f.spawnSheep(SheepColour.RED, OPEN_FIELD)
        f.feed(field, SheepColour.RED)
        assertTrue(f.said("kill the sheep outside the enclosure"))
        assertEquals(SheepState.LOOSE, f.sheep.state(f.player, SheepColour.RED))
        assertEquals(0, f.groundBones(SheepColour.RED))

        f.pen(SheepColour.RED)
        val pen = f.spawnPenSheep(SheepColour.RED)
        f.feed(pen, SheepColour.RED)
        assertTrue(f.said("It happily eats it"))
        assertTrue(f.said("collapses dead onto the floor"))
        assertEquals(SheepState.BONES, f.sheep.state(f.player, SheepColour.RED))
        assertEquals(1, f.groundBones(SheepColour.RED))
        f.feed(pen, SheepColour.RED)
        assertEquals(1, f.groundBones(SheepColour.RED), "a second feed must not drop more bones")
        assertEquals(1, f.count(FEED))

        val g = Fixture()
        g.give(FEED)
        g.equip()
        g.feed(g.spawnPenSheep(SheepColour.RED), SheepColour.RED)
        assertTrue(g.said("no need to poison"))
    }

    @Test fun `each colour's bones count only for that colour`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        f.dispose(SheepColour.RED)
        f.give(SheepColour.RED.bones, 3)
        repeat(3) { f.incinerate(SheepColour.RED) }
        assertEquals(0, f.count(SheepColour.RED.bones))
        assertEquals(listOf(SheepColour.GREEN, SheepColour.BLUE, SheepColour.YELLOW), f.sheep.remaining(f.player))
        assertEquals(STAGE_STARTED, f.stage())

        f.give(SheepColour.GREEN.bones)
        f.incinerate(SheepColour.GREEN)
        assertEquals(SheepState.LOOSE, f.sheep.state(f.player, SheepColour.GREEN), "loose bones don't stand in for herding")
    }

    @Test fun `lost feed and bones can be recovered without undoing a colour`() {
        val f = Fixture(STAGE_STARTED)
        f.talk(HALGRIVE)
        assertEquals(1, f.count(FEED))
        f.talk(HALGRIVE)
        assertEquals(1, f.count(FEED), "no second feed while one is carried")

        f.sheep.setState(f.player, SheepColour.RED, SheepState.BURNED)
        f.sheep.setState(f.player, SheepColour.BLUE, SheepState.BONES)
        f.sheep.setState(f.player, SheepColour.YELLOW, SheepState.BONES)
        f.give(SheepColour.YELLOW.bones)
        f.choose(1)
        f.talk(BRUMTY)
        assertEquals(1, f.count(SheepColour.BLUE.bones))
        assertEquals(1, f.count(SheepColour.YELLOW.bones), "bones still carried aren't handed out again")
        assertEquals(0, f.count(SheepColour.RED.bones))
        assertEquals(SheepState.BURNED, f.sheep.state(f.player, SheepColour.RED), "recovery never undoes a colour")
    }

    @Test fun `progress survives saving and reloading mid-herd`() {
        val f = Fixture(STAGE_STARTED)
        f.equip()
        f.pen(SheepColour.GREEN)
        f.dispose(SheepColour.RED)
        f.sheep.setState(f.player, SheepColour.BLUE, SheepState.BONES)
        val loose = f.spawnSheep(SheepColour.YELLOW, OPEN_FIELD)
        f.prod(loose, from = OPEN_FIELD.translateZ(-1))

        val loaded = f.saveAndReload()
        assertEquals(STAGE_STARTED, f.sheep.stage(loaded))
        assertEquals(SheepState.PENNED, f.sheep.state(loaded, SheepColour.GREEN))
        assertEquals(SheepState.BURNED, f.sheep.state(loaded, SheepColour.RED))
        assertEquals(SheepState.BONES, f.sheep.state(loaded, SheepColour.BLUE))
        assertEquals(SheepState.LOOSE, f.sheep.state(loaded, SheepColour.YELLOW))

        f.herding.unstick(loose)
        assertEquals(OPEN_FIELD, loose.coords, "an abandoned sheep is back in its pasture for the next try")
    }

    @Test fun `the journal follows the clothing, the cattleprod and each colour`() {
        val f = Fixture(STAGE_STARTED)
        var journal = f.journal()
        assertTrue(journal.contains("sells protective clothing"), journal)
        assertTrue(journal.contains("cattleprod"))
        assertTrue(journal.contains("Red sheep: not yet herded"))

        f.equip()
        f.sheep.setState(f.player, SheepColour.RED, SheepState.PENNED)
        f.sheep.setState(f.player, SheepColour.BLUE, SheepState.BONES)
        f.sheep.setState(f.player, SheepColour.GREEN, SheepState.BURNED)
        journal = f.journal()
        assertTrue(journal.contains("wearing Doctor Orbon's protective clothing"), journal)
        assertTrue(journal.contains("wielding the cattleprod"))
        assertTrue(journal.contains("Red sheep: in the enclosure"))
        assertTrue(journal.contains("Blue sheep: dead. Its bones are still in the enclosure"))
        assertTrue(journal.contains("Green sheep: dead and burned"))
        assertTrue(journal.contains("Yellow sheep: not yet herded"))
    }

    @Test fun `an unfinished report leaves everything as it was`() {
        val f = Fixture(STAGE_STARTED)
        f.give(FEED)
        f.sheep.setState(f.player, SheepColour.RED, SheepState.BURNED)
        f.sheep.setState(f.player, SheepColour.BLUE, SheepState.BURNED)
        f.talk(HALGRIVE)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.count(FEED))
        assertEquals(0, f.count(COINS))
    }

    @Test fun `the payment is 3,100 coins once, and waits for room`() {
        val f = Fixture(STAGE_DISPOSED)
        f.fill()
        f.talk(HALGRIVE)
        assertEquals(STAGE_DISPOSED, f.stage())
        assertTrue(f.said("no room for your payment"))

        f.player.inv[0] = null
        f.talk(HALGRIVE)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(3_100, f.count(COINS))
        assertEquals(4, f.player.vars["varp.qp"])

        f.talk(HALGRIVE)
        f.talk(HALGRIVE)
        assertEquals(3_100, f.count(COINS), "no second payment")
        assertEquals(4, f.player.vars["varp.qp"])
        f.sheep.quest.completeQuest(f.access())
        assertEquals(3_100, f.count(COINS))
    }

    @Test fun `everyone has something to say before and after the quest`() {
        val f = Fixture()
        f.choose(2)
        for (npc in listOf(HALGRIVE, ORBON, BRUMTY)) f.talk(npc)
        assertEquals(0, f.stage())
        assertEquals(0, f.count(FEED))
        assertEquals(0, f.count(JACKET))

        val g = Fixture(STAGE_COMPLETE)
        for (npc in listOf(HALGRIVE, ORBON, BRUMTY)) g.talk(npc)
        assertEquals(STAGE_COMPLETE, g.stage())
        assertEquals(0, g.count(COINS))
        assertEquals(0, g.count(FEED))
        assertEquals(0, g.count(JACKET))

        val h = Fixture(STAGE_DISPOSED)
        h.talk(ORBON)
        assertTrue(h.said("tell Councillor Halgrive"))
        assertEquals(STAGE_DISPOSED, h.stage())
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("sheep-herder-test")
        private var result: Result<Unit>? = null
        private val random = DefaultGameRandom(Random(7))
        private val clock = MapClock(100)
        private lateinit var regions: RegionRegistry
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { random },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
        )
        private val npcRepo: NpcRepository
        private val locRepo: LocRepository
        private val objRegistry: ObjRegistry
        private val picks = ArrayDeque<Int>()
        private val players = PlayerList()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        @OptIn(InternalApi::class)
        var player = newPlayer()

        val sheep = SheepHerderQuest()
        val herding: SheepHerding

        init {
            loadMap(collision)
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            regions = RegionRegistry(RegionListSmall(), RegionListLarge(), RegionListWorldEntity(),
                normal, collision, storage, npcRegistry,
                ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            locRepo = LocRepository(clock, LocRegistry(storage, normal,
                LocRegistryRegion(updates, collision, storage, regions)), regions)
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            objRegistry = ObjRegistry(updates)
            val world = WorldRepository(updates)
            herding = SheepHerding(sheep, collision, RouteFactory(collision), world)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(sheep) { scripts.startup() }
            with(herding) { scripts.startup() }
            with(PlagueEnclosure(sheep, locRepo, ObjRepository(clock, objRegistry), world)) { scripts.startup() }
            with(CouncillorHalgrive(sheep)) { scripts.startup() }
            with(DoctorOrbon(sheep)) { scripts.startup() }
            with(FarmerBrumty(sheep)) { scripts.startup() }
            players[player.slotId] = player
            if (stage > 0) VarPlayerIntMapSetter.set(player, "varbit.sheepherder_progress", stage)
        }

        @OptIn(InternalApi::class)
        private fun newPlayer() = Player().apply {
            this.client = this@Fixture.client
            uuid = 4343L
            observerUUID = 4343L
            slotId = 1
            assignUid()
            coords = BRUMTY_STAND
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = sheep.stage(player)

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            if (type.stackable) {
                val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
                if (slot >= 0) {
                    player.inv[slot] = InvObj(obj, checkNotNull(player.inv[slot]).count + count)
                    return
                }
                player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, count)
                return
            }
            repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun wear(vararg objs: String) {
            for (obj in objs) {
                drop(obj)
                val slot = checkNotNull(ServerCacheManager.getItem(obj.asRSCM())).wearpos1
                player.worn[slot] = InvObj(obj, 1)
            }
        }

        fun unwear(obj: String) {
            val slot = player.worn.indexOfFirst { it?.id == obj.asRSCM() }
            player.worn[slot] = null
            give(obj)
        }

        fun equip() {
            for (obj in listOf(JACKET, TROUSERS, CATTLEPROD)) {
                give(obj)
                wear(obj)
            }
            if (FEED !in player.inv) give(FEED)
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun journal(): String = sheep.questLog(access())

        fun start() {
            choose(1, 1)
            talk(HALGRIVE)
        }

        fun buy() {
            choose(1)
            talk(ORBON)
        }

        fun spawnSheep(colour: SheepColour, at: CoordGrid): Npc {
            val npc = Npc(colour.fieldNpc, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun spawnPenSheep(colour: SheepColour): Npc {
            val npc = Npc(colour.enclosureNpc, PEN_TILES.getValue(colour))
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun remove(npc: Npc) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun prod(npc: Npc, from: CoordGrid) {
            player.coords = from
            val trigger = checkNotNull(NpcInteractions(events).opTrigger(player, npc, InteractionOp.Op1)) {
                "No prod handler for ${npc.type}"
            }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        /** One prod from the side opposite [dir], with the sheep then moved to where it walked. */
        fun drive(npc: Npc, dir: Direction) {
            val stand = HerdingRules.standFor(npc.coords, dir)
            prod(npc, stand)
            val dest = npc.routeDestination.lastOrNull() ?: return
            npc.teleport(collision, dest)
        }

        /** Penning by the last prod onto the threshold, the way [drive] would arrive there. */
        fun pen(colour: SheepColour) {
            val npc = spawnSheep(colour, PEN_APPROACH)
            npc.spawnCoords = OPEN_FIELD
            drive(npc, Direction.SOUTH)
            assertEquals(SheepState.PENNED, sheep.state(player, colour), output())
            remove(npc)
        }

        fun feed(npc: Npc, colour: SheepColour) {
            player.coords = npc.coords.translateX(-1)
            val slot = player.inv.indexOfFirst { it?.id == FEED.asRSCM() }
            check(slot >= 0) { "no feed to use" }
            val feedType = checkNotNull(ServerCacheManager.getItem(FEED.asRSCM()))
            val shown = npc.type
            dispatch { assertTrue(events.publish(this, NpcUEvents.Op(npc, slot, feedType, shown))) }
        }

        fun groundBones(colour: SheepColour): Int =
            PEN_TILES.values.plus(OPEN_FIELD).sumOf { tile ->
                objRegistry.findAll(tile).count { it.type == colour.bones.asRSCM() }
            }

        fun collect(colour: SheepColour) {
            assertEquals(1, groundBones(colour), "no ${colour.label} bones dropped")
            give(colour.bones)
        }

        fun incinerate(colour: SheepColour) {
            player.coords = INCINERATOR_STAND
            locU(PlagueEnclosure.INCINERATOR, INCINERATOR, colour.bones)
        }

        fun dispose(colour: SheepColour) {
            pen(colour)
            feed(spawnPenSheep(colour), colour)
            assertEquals(SheepState.BONES, sheep.state(player, colour))
            collect(colour)
            incinerate(colour)
            assertEquals(SheepState.BURNED, sheep.state(player, colour), output())
        }

        fun gate(from: CoordGrid) {
            player.coords = from
            player.routeDestination.clear()
            val at = HerdingRules.GATE_THRESHOLD[0]
            val type = checkNotNull(ServerCacheManager.getObject(PlagueEnclosure.GATE_RIGHT.asRSCM()))
            locRepo.add(at, PlagueEnclosure.GATE_RIGHT, Int.MAX_VALUE, LocAngle.East, LocShape.WallStraight)
            val loc = BoundLocInfo(LocInfo(0, at, LocEntity(type.id, 0, 2)), type)
            val trigger = checkNotNull(LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, InteractionOp.Op1))
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        fun talk(type: String) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
            npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun hasTimer(npc: Npc, timer: String): Boolean {
            val id = timer.asRSCM(RSCMType.TIMER).toShort()
            return npc.timerMap.any { it.shortKey == id }
        }

        private fun locU(symbol: String, coords: CoordGrid, obj: String) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, 2)), type)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot < 0) return
            val event = with(locU) { access().opTrigger(loc, loc, type, objType, slot) }
            val trigger = checkNotNull(event) { "No $obj handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        /** What the account save keeps: the permanent varps and the persistent quest-stage attribute. */
        @OptIn(InternalApi::class)
        fun saveAndReload(): Player {
            val loaded = newPlayer()
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            return loaded
        }

        private fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val access = access()
            val start: suspend () -> Unit = { access.block() }
            start.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
            repeat(600) {
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
                val parent = listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox", "objectbox_double")
                    .firstOrNull { player.ui.containsModal("interface.$it") }
                    ?: error("Unknown dialogue: ${output()}")
                val input = when (parent) {
                    "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", picks.removeFirstOrNull() ?: 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    "objectbox_double" -> ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
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
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        val BRUMTY_STAND = CoordGrid(2592, 3358, 0)
        val OPEN_FIELD = CoordGrid(2600, 3388, 0)
        val PEN_APPROACH = CoordGrid(2594, 3364, 0)
        val INCINERATOR = CoordGrid(2606, 3360, 0)
        val INCINERATOR_STAND = CoordGrid(2604, 3360, 0)
        val PEN_TILES = mapOf(
            SheepColour.RED to CoordGrid(2597, 3362, 0),
            SheepColour.GREEN to CoordGrid(2598, 3361, 0),
            SheepColour.BLUE to CoordGrid(2597, 3360, 0),
            SheepColour.YELLOW to CoordGrid(2596, 3359, 0),
        )

        private val SQUARES = listOf(40 to 52, 40 to 53, 41 to 52, 41 to 53).map { (x, z) -> MapSquareKey(x, z) }
        private val restored = mutableListOf<() -> Unit>()
        private lateinit var cache: dev.openrune.filesystem.Cache

        fun loadMap(collision: CollisionFlagMap) {
            for (square in SQUARES) {
                val group = (square.x shl 8) or square.z
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in square.x * 64 until square.x * 64 + 64 step 8) {
                    for (z in square.z * 64 until square.z * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, spawns)
            }
        }

        @OptIn(InternalApi::class)
        @JvmStatic @BeforeAll fun cache() {
            cache = ServerCacheManager.init(240)
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
            assertNotNull(cache)
        }

        @JvmStatic @AfterAll fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
            cache.close()
        }
    }
}
