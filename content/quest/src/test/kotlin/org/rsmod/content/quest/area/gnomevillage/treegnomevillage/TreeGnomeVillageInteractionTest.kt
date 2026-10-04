package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs.CommanderMontai
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs.Elkoy
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs.KingBolren
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs.TrackerGnomes
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs.Villagers
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
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TreeGnomeVillageInteractionTest {
    @Test
    fun `accepting King Bolren's request starts the quest and Elkoy leads the player out`() {
        val f = Fixture()
        f.talkBolren()
        f.finish(listOf(1, 1))
        assertEquals(1, f.stage())
        assertEquals(1, f.player.vars["varp.treequest"])
        assertEquals(GnomeMaze.Entrance, f.player.coords)
        assertTrue(f.output().contains("Elkoy guides you out of the maze."), f.output())
    }

    @Test
    fun `declining or leaving keeps the quest unstarted`() {
        for (options in listOf(listOf(1, 2), listOf(2))) {
            val f = Fixture()
            f.talkBolren()
            f.finish(options)
            assertEquals(0, f.stage(), "options $options")
        }
    }

    @Test
    fun `agreeing to fetch logs advances once and declining does not`() {
        val declined = Fixture(1)
        declined.talkMontai()
        declined.finish(listOf(1))
        assertEquals(1, declined.stage())

        val f = Fixture(1)
        f.talkMontai()
        f.finish(listOf(2))
        assertEquals(2, f.stage())
    }

    @Test
    fun `fewer than six logs are refused and nothing is taken`() {
        val f = Fixture(2)
        f.give(Logs, 5)
        f.talkMontai()
        f.finish()
        assertEquals(2, f.stage())
        assertEquals(5, f.player.inv.count(Logs))
    }

    @Test
    fun `six logs are taken and the stage advances together`() {
        val f = Fixture(2)
        f.give(Logs, 6)
        f.give("obj.oak_logs", 1)
        f.talkMontai()
        f.until { f.player.inv.count(Logs) == 0 }
        assertEquals(3, f.stage())
        f.finish()
        assertEquals(3, f.stage())
        assertEquals(1, f.player.inv.count("obj.oak_logs"))
    }

    @Test
    fun `cancelling before the hand-in leaves the logs and the stage alone`() {
        val f = Fixture(2)
        f.give(Logs, 6)
        f.talkMontai()
        f.until { f.output().contains("I have some here.") }
        f.cancel()
        assertEquals(2, f.stage())
        assertEquals(6, f.player.inv.count(Logs))
    }

    @Test
    fun `Montai sends the player for the trackers and rolls the ballista answer`() {
        val f = Fixture(3)
        f.talkMontai()
        f.finish(listOf(2))
        assertEquals(4, f.stage())
        assertTrue(f.player.ballistaAnswer in 0..3)
        val declined = Fixture(3)
        declined.talkMontai()
        declined.finish(listOf(1))
        assertEquals(3, declined.stage())
    }

    @Test
    fun `each tracker records its coordinate and the mad one hints at the roll`() {
        val f = Fixture(4)
        f.player.ballistaAnswer = 2
        f.talk("npc.tracker1")
        f.finish()
        assertTrue(f.player.knowsHeight)
        assertFalse(f.player.knowsY)
        f.talk("npc.tracker2")
        f.finish()
        assertTrue(f.player.knowsY)
        f.talk("npc.tracker3")
        f.finish()
        assertTrue(f.player.knowsX)
        assertTrue(f.output().contains("More than we, less than our feet."), f.output())
        assertEquals(4, f.stage())
    }

    @Test
    fun `every riddle matches its coordinate`() {
        val riddles =
            listOf(
                "Less than my hands.",
                "More than my head, less than my fingers.",
                "More than we, less than our feet.",
                "My legs and your legs, ha ha ha!",
            )
        for ((answer, riddle) in riddles.withIndex()) {
            val f = Fixture(4)
            f.player.ballistaAnswer = answer
            f.talk("npc.tracker3")
            f.finish()
            assertTrue(f.output().contains(riddle), "answer ${answer + 1}: ${f.output()}")
        }
    }

    @Test
    fun `trackers say nothing about coordinates before they are sent for`() {
        val f = Fixture(2)
        f.talk("npc.tracker1")
        f.finish()
        f.talk("npc.tracker2")
        f.finish()
        f.talk("npc.tracker3")
        f.finish()
        assertFalse(f.player.knowsHeight || f.player.knowsY || f.player.knowsX)
    }

    @Test
    fun `the ballista is damaged until the trackers are sent for`() {
        val f = Fixture(3)
        f.fire()
        f.finish()
        assertTrue(f.output().contains("The ballista is damaged."), f.output())
        assertEquals(3, f.stage())
    }

    @Test
    fun `the ballista needs every coordinate`() {
        val f = Fixture(4)
        f.player.knowsHeight = true
        f.fire()
        f.finish()
        assertTrue(f.output().contains("coordinates yet"), f.output())
        assertEquals(4, f.stage())
    }

    @Test
    fun `a wrong x coordinate misses and the right one breaches the stronghold`() {
        val f = Fixture(4)
        f.knowAll(answer = 2)
        f.fire()
        f.finish(listOf(1))
        assertTrue(f.output().contains("completely misses the Khazard stronghold"), f.output())
        assertEquals(4, f.stage())
        f.fire()
        f.finish(listOf(3))
        assertEquals(5, f.stage())
        assertTrue(f.output().contains("reduced to rubble"), f.output())
        f.fire()
        f.finish()
        assertTrue(f.output().contains("has already been breached"), f.output())
        assertEquals(5, f.stage())
    }

    @Test
    fun `the stronghold wall cannot be climbed before it is breached`() {
        val f = Fixture(4)
        f.climbWall(CoordGrid(2509, 3252, 0))
        f.finish()
        assertTrue(f.output().contains("far too high"), f.output())
        assertEquals(CoordGrid(2509, 3252, 0), f.player.coords)
    }

    @Test
    fun `the breached wall can be climbed into the stronghold`() {
        val f = Fixture(5)
        f.climbWall(CoordGrid(2509, 3252, 0))
        f.finish()
        assertEquals(CoordGrid(2509, 3254, 0), f.player.coords)
    }

    @Test
    fun `the stronghold door is locked from the outside`() {
        val f = Fixture(5)
        f.player.coords = CoordGrid(2502, 3249, 0)
        f.openDoor()
        f.finish()
        assertTrue(f.output().contains("locked from the inside"), f.output())
        assertEquals(CoordGrid(2502, 3249, 0), f.player.coords)
    }

    @Test
    fun `the chest gives the orb once and a lost orb can be fetched again`() {
        val f = Fixture(5)
        f.searchChest()
        f.finish()
        assertEquals(1, f.player.inv.count(Orb))
        assertEquals(6, f.stage())
        f.searchChest()
        f.finish()
        assertEquals(1, f.player.inv.count(Orb))
        assertTrue(f.output().contains("You find nothing of interest."), f.output())
        f.player.inv[0] = null
        f.player.inv[1] = null
        assertEquals(0, f.player.inv.count(Orb))
        f.searchChest()
        f.finish()
        assertEquals(1, f.player.inv.count(Orb))
        assertEquals(6, f.stage())
    }

    @Test
    fun `the chest is empty before the stronghold is breached`() {
        val f = Fixture(4)
        f.searchChest()
        f.finish()
        assertEquals(0, f.player.inv.count(Orb))
        assertEquals(4, f.stage())
    }

    @Test
    fun `Montai congratulates the player who has the orb`() {
        val f = Fixture(6)
        f.give(Orb, 1)
        f.talkMontai()
        f.finish()
        assertTrue(f.output().contains("you really are something"), f.output())
    }

    @Test
    fun `returning the first orb takes it and sends the player after the warlord`() {
        val f = Fixture(6)
        f.give(Orb, 1)
        f.talkBolren()
        f.finish(listOf(1))
        assertEquals(7, f.stage())
        assertEquals(0, f.player.inv.count(Orb))
        assertEquals(GnomeMaze.Entrance, f.player.coords)
    }

    @Test
    fun `the first orb is taken even if the player turns the new task down`() {
        val f = Fixture(6)
        f.give(Orb, 1)
        f.talkBolren()
        f.finish(listOf(2))
        assertEquals(7, f.stage())
        assertEquals(0, f.player.inv.count(Orb))
    }

    @Test
    fun `Bolren without the orb does not advance anything`() {
        val f = Fixture(6)
        f.talkBolren()
        f.finish()
        assertEquals(6, f.stage())
        assertTrue(f.output().contains("Without the orb"), f.output())
    }

    @Test
    fun `the warlord leaves orbs only for a player Bolren sent after them`() {
        val f = Fixture(7)
        assertTrue(warlordDropsOrbs(7, f.player))
        assertTrue(warlordDropsOrbs(8, f.player))
        assertFalse(warlordDropsOrbs(6, f.player))
        assertFalse(warlordDropsOrbs(9, f.player))
        assertFalse(warlordDropsOrbs(0, f.player))
        f.give(Orbs, 1)
        assertFalse(warlordDropsOrbs(8, f.player))
    }

    @Test
    fun `killing the warlord advances the stage once and a repeat kill keeps it`() {
        val f = Fixture(7)
        f.run { warlordFalls(f.quest, warlordDropsOrbs(f.stage(), f.player)) }
        f.finish()
        assertEquals(8, f.stage())
        assertTrue(f.output().contains("You spot the orbs of protection"), f.output())
        f.run { warlordFalls(f.quest, warlordDropsOrbs(f.stage(), f.player)) }
        f.finish()
        assertEquals(8, f.stage())
    }

    @Test
    fun `a warlord kill outside the quest only shows the vapour`() {
        val f = Fixture(2)
        f.run { warlordFalls(f.quest, warlordDropsOrbs(f.stage(), f.player)) }
        f.finish()
        assertEquals(2, f.stage())
        assertTrue(f.output().contains("ghostly vapour"), f.output())
        assertFalse(f.output().contains("You spot the orbs"), f.output())
    }

    @Test
    fun `talking to the warlord at each stage uses the matching lines`() {
        val lines =
            mapOf(
                0 to "Die in the name of Khazard!",
                5 to "Die in the name of Khazard!",
                6 to "You think you're so clever.",
                7 to "Go back to your pesky little green friends.",
                8 to "warriors blessed by Khazard don't die",
                9 to "warriors blessed by Khazard don't die",
            )
        for ((stage, line) in lines) {
            val f = Fixture(stage)
            f.talkWarlord()
            f.finish()
            assertTrue(f.output().contains(line), "stage $stage: ${f.output()}")
        }
    }

    @Test
    fun `without the orbs Bolren only repeats his plea`() {
        val f = Fixture(8)
        f.talkBolren()
        f.finish()
        assertEquals(8, f.stage())
        assertTrue(f.output().contains("Without the orbs"), f.output())
    }

    @Test
    fun `returning the orbs completes the quest once with every reward`() {
        val f = Fixture(8)
        f.give(Orbs, 1)
        f.talkBolren()
        f.finish()
        f.assertComplete()
        assertEquals(2, f.player.bolrenGotOrbs)
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        f.talkBolren()
        f.finish()
        f.assertComplete()
        assertEquals(1, f.player.inv.count(GnomeAmulet))
    }

    @Test
    fun `the orbs can be handed over straight after the first orb`() {
        val f = Fixture(7)
        f.give(Orbs, 1)
        f.talkBolren()
        f.finish()
        f.assertComplete()
    }

    @Test
    fun `cancelling the ceremony keeps the orbs and the stage`() {
        val f = Fixture(8)
        f.give(Orbs, 1)
        f.talkBolren()
        f.until { f.output().contains("The gnomes begin to chant") }
        f.cancel()
        assertEquals(8, f.stage())
        assertEquals(1, f.player.inv.count(Orbs))
        assertEquals(0, f.player.inv.count(GnomeAmulet))
        assertEquals(0, f.player.statMap.getXP("stat.attack"))
        f.talkBolren()
        f.finish()
        f.assertComplete()
    }

    @Test
    fun `after the quest Bolren replaces a lost amulet`() {
        val f = Fixture(9)
        f.talkBolren()
        f.finish()
        assertEquals(1, f.player.inv.count(GnomeAmulet))
        assertTrue(f.output().contains("Here, I think this belongs to you."), f.output())
        val kept = Fixture(9)
        kept.give(GnomeAmulet, 1)
        kept.talkBolren()
        kept.finish()
        assertEquals(1, kept.player.inv.count(GnomeAmulet))
        assertFalse(kept.output().contains("belongs to you"), kept.output())
        assertTrue(kept.output().contains("It's good to see you again."), kept.output())
    }

    @Test
    fun `a worn amulet is not replaced`() {
        val f = Fixture(9)
        f.player.worn[2] = InvObj(GnomeAmulet, 1)
        f.talkBolren()
        f.finish()
        assertEquals(0, f.player.inv.count(GnomeAmulet))
    }

    @Test
    fun `Elkoy at the entrance leads the player to the railing`() {
        val f = Fixture(1)
        f.talk("npc.elkoy")
        f.finish(listOf(1))
        assertEquals(GnomeMaze.RailingApproach, f.player.coords)
        assertTrue(f.output().contains("Please help us get our orb back."), f.output())
    }

    @Test
    fun `Elkoy at the railing leads the player out of the maze`() {
        val f = Fixture(1)
        f.talk("npc.elkoy_village")
        f.finish(listOf(1))
        assertEquals(GnomeMaze.Entrance, f.player.coords)
    }

    @Test
    fun `declining Elkoy's offer leaves the player where they are`() {
        val f = Fixture(1)
        val start = f.player.coords
        f.talk("npc.elkoy")
        f.finish(listOf(2))
        assertEquals(start, f.player.coords)
    }

    @Test
    fun `Elkoy's Follow option skips the conversation`() {
        val f = Fixture(2)
        f.talk("npc.elkoy", op = 3)
        f.finish()
        assertEquals(GnomeMaze.RailingApproach, f.player.coords)
        assertFalse(f.output().contains("You must retrieve the orb"), f.output())
    }

    @Test
    fun `Elkoy talks about the maze before the quest and never guides`() {
        val f = Fixture()
        val start = f.player.coords
        f.talk("npc.elkoy")
        f.finish()
        assertTrue(f.output().contains("welcome to our maze"), f.output())
        assertEquals(start, f.player.coords)
    }

    @Test
    fun `Elkoy greets a hero differently from the player who finished`() {
        val hero = Fixture(8)
        hero.talk("npc.elkoy")
        hero.finish(listOf(2))
        assertTrue(hero.output().contains("You truly are a hero."), hero.output())
        val done = Fixture(9)
        done.talk("npc.elkoy")
        done.finish(listOf(2))
        assertTrue(done.output().contains("I hope life is treating you well."), done.output())
        assertFalse(done.output().contains("You truly are a hero."), done.output())
    }

    @Test
    fun `the loose railing squeezes through both ways`() {
        val f = Fixture(1)
        f.player.coords = GnomeMaze.RailingApproach
        f.squeeze()
        f.finish()
        assertEquals(GnomeMaze.RailingTile, f.player.coords)
        f.squeeze()
        f.finish()
        assertEquals(GnomeMaze.RailingApproach, f.player.coords)
    }

    @Test
    fun `villagers have their own lines before and after the quest`() {
        val before = Fixture()
        before.talk("npc.remsai")
        before.finish()
        assertTrue(before.output().contains("Not many find their way in here."), before.output())
        val after = Fixture(9)
        after.talk("npc.remsai")
        after.finish()
        assertTrue(after.output().contains("You're a legend around these parts."), after.output())
        val kalron = Fixture(9)
        kalron.talk("npc.lostgnome")
        kalron.finish()
        assertTrue(kalron.output().contains("Are you trying to be funny?"), kalron.output())
        val shop = Fixture()
        shop.talk("npc.treevillage_shopkeeper1")
        shop.finish(listOf(2))
        assertTrue(shop.output().contains("Ok, maybe later."), shop.output())
    }

    @Test
    fun `the journal follows the stage and the tracker flags`() {
        val f = Fixture(4)
        assertTrue(f.quest.questLog(f.access()).contains("tracker gnomes"))
        f.player.knowsHeight = true
        f.player.knowsY = true
        f.player.knowsX = true
        val log = f.quest.questLog(f.access())
        assertTrue(log.contains("gave me the height"), log)
        assertTrue(log.contains("fire the <red>ballista</red>"), log)
        val complete = Fixture(9)
        assertTrue(complete.quest.completedLog(complete.access()).contains("QUEST COMPLETE"))
    }

    @Test
    fun `the quest ends at the stage the cache expects`() {
        val f = Fixture()
        assertEquals(TreeGnomeVillageQuest.Complete, f.quest.quest.maxSteps)
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("tree-gnome-village-test")
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
        private val npcs = NpcRepository(clock, npcRegistry, npcList)
        private val objs = ObjRepository(clock, objRegistry)
        private val world = WorldRepository(updates)
        private val search =
            NpcSearch(
                Hunt(RayCastValidator(collision), playerRegistry, npcRegistry, objRegistry, locRegistry)
            )
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
                uuid = 793L
                slotId = 1
                assignUid()
                coords = CoordGrid(2523, 3207, 0)
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

        val quest = TreeGnomeVillageQuest()
        private var nextSlot = 0

        init {
            for (x in 2496..2560 step 8) for (z in 3144..3264 step 8) {
                collision.allocateIfAbsent(x, z, 0)
                collision.allocateIfAbsent(x, z, 1)
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
            with(KingBolren(quest, objs, world, search, collision)) { scripts.startup() }
            with(CommanderMontai(quest)) { scripts.startup() }
            with(TrackerGnomes(quest)) { scripts.startup() }
            with(Elkoy(quest)) { scripts.startup() }
            with(Villagers(quest, Shops(events))) { scripts.startup() }
            with(KhazardWarlord(quest, playerList, aiInteractions, WorldQueueList())) {
                scripts.startup()
            }
            with(Ballista(quest, world)) { scripts.startup() }
            with(KhazardStronghold(quest, locs, objs, search, aiInteractions)) { scripts.startup() }
            with(LooseRailing()) { scripts.startup() }
            VarPlayerIntMapSetter.set(player, "varbit.tree_gnome_village_progress", stage)
        }

        fun stage() = quest.quest.getQuestStage(player)

        fun access() = ProtectedAccess(player, coroutine, context)

        fun give(obj: String, count: Int) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            if (type.stackable) {
                player.inv[nextSlot++] = InvObj(obj, count)
            } else {
                repeat(count) { player.inv[nextSlot++] = InvObj(obj, 1) }
            }
        }

        fun knowAll(answer: Int) {
            player.knowsHeight = true
            player.knowsY = true
            player.knowsX = true
            player.ballistaAnswer = answer
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

        fun talkBolren() = talk("npc.king_bolren")

        fun talkMontai() = talk("npc.commander_montai")

        fun talkWarlord() = talk("npc.khazard_warlord", register = true)

        private fun op(
            symbol: String,
            coords: CoordGrid,
            shape: LocShape,
            angle: LocAngle,
            slot: Int = 1,
        ) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val loc =
                BoundLocInfo(LocInfo(shape.id, coords, LocEntity(type.id, shape.id, angle.id)), type)
            run {
                val event =
                    if (slot == 2) LocEvents.Op2(loc, loc, type) else LocEvents.Op1(loc, loc, type)
                assertTrue(events.publish(this, event))
            }
        }

        fun fire() =
            op("loc.catabow", CoordGrid(2508, 3210, 0), LocShape.CentrepieceStraight, LocAngle.West, 2)

        fun climbWall(from: CoordGrid) {
            player.coords = from
            op("loc.khazzacklowwall", CoordGrid(2509, 3253, 0), LocShape.CentrepieceStraight, LocAngle.North)
        }

        fun openDoor() =
            op("loc.khazard_stronghold_door", CoordGrid(2502, 3250, 0), LocShape.WallStraight, LocAngle.North)

        fun searchChest() =
            op("loc.chestopen_khazard", CoordGrid(2506, 3259, 1), LocShape.CentrepieceStraight, LocAngle.East)

        fun squeeze() =
            op("loc.treegnomelooserailing", GnomeMaze.RailingTile, LocShape.WallStraight, LocAngle.South)

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

        fun assertComplete() {
            assertEquals(9, stage())
            assertEquals(9, player.vars["varp.treequest"])
            assertEquals(2, player.vars["varp.qp"])
            assertEquals(11450, player.statMap.getXP("stat.attack"))
            assertEquals(1, player.inv.count(GnomeAmulet))
            assertEquals(0, player.inv.count(Orbs))
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
        private const val Logs = "obj.logs"
        private const val Orb = "obj.orb_of_protection"
        private const val Orbs = "obj.orbs_of_protection"
        private const val GnomeAmulet = "obj.gnome_amulet"
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
