package org.rsmod.content.quest.area.ardougne.fightarena

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
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
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
import org.rsmod.api.random.DefaultGameRandom
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
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaQuest.Opponent
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
import org.rsmod.game.map.Direction
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
class FightArenaInteractionTest {

    /* Lady Servil */

    @Test
    fun `accepting Lady Servil's request starts the quest`() {
        val f = Fixture()
        f.talk(Lady)
        f.finish(listOf(1))
        assertEquals(1, f.stage())
        assertEquals(1, f.player.vars["varp.arenaquest"])
        assertTrue(f.output().contains("disguise"), f.output())
    }

    @Test
    fun `declining or leaving keeps the quest unstarted`() {
        val f = Fixture()
        f.talk(Lady)
        f.finish(listOf(2))
        assertEquals(0, f.stage())
        assertEquals(0, f.player.vars["varp.arenaquest"])
    }

    @Test
    fun `a low combat level is warned about before the prompt`() {
        val f = Fixture()
        f.talk(Lady)
        f.finish(listOf(2))
        assertTrue(f.output().contains("lower than the recommended level"), f.output())
    }

    @Test
    fun `Lady Servil follows the stage`() {
        val lines =
            mapOf(
                1 to "hurry",
                2 to "Wear it",
                3 to "disguised as a guard",
                4 to "disguised as a guard",
                5 to "disguised as a guard",
                6 to "Justin is still in there",
                8 to "Justin is still in there",
                11 to "Justin is still in there",
            )
        for ((stage, text) in lines) {
            val f = Fixture(stage)
            f.talk(Lady)
            f.finish()
            assertTrue(f.output().contains(text), "stage $stage: ${f.output()}")
            assertEquals(stage, f.stage())
            assertEquals(0, f.player.inv.count(Coins))
        }
    }

    @Test
    fun `escaping Khazard and reporting to Lady Servil completes the quest once`() {
        val f = Fixture(12)
        f.talk(Lady)
        f.finish()
        f.assertComplete()
        f.talk(Lady)
        f.finish()
        f.assertComplete(scroll = false)
        assertTrue(f.output().contains("mend our cart"), f.output())
    }

    @Test
    fun `beating Khazard and reporting to Lady Servil completes the quest once`() {
        val f = Fixture(13)
        f.talk(Lady)
        f.finish()
        f.assertComplete()
        assertTrue(f.output().contains("You killed him"), f.output())
        f.talk(Lady)
        f.finish()
        f.assertComplete(scroll = false)
    }

    @Test
    fun `the reward is not repeated after the quest is done`() {
        val f = Fixture(14)
        f.talk(Lady)
        f.finish()
        assertEquals(14, f.stage())
        assertEquals(0, f.player.inv.count(Coins))
        assertEquals(0, f.player.statMap.getXP("stat.attack"))
        assertEquals(0, f.player.vars["varp.qp"])
        assertTrue(f.output().contains("recovering at home"), f.output())
    }

    @Test
    fun `Lady Servil does not pay out before the arena is done`() {
        for (stage in listOf(1, 5, 6, 9, 10, 11)) {
            val f = Fixture(stage)
            f.talk(Lady)
            f.finish()
            assertEquals(stage, f.stage())
            assertEquals(0, f.player.vars["varp.qp"], "stage $stage")
        }
    }

    /* The armour chest */

    @Test
    fun `the chest stays locked until the quest is started`() {
        val f = Fixture(0)
        f.loc(Chest, ChestCoords)
        assertEquals(0, f.player.inv.count(Helmet))
        assertEquals(0, f.stage())
        assertTrue(f.output().contains("securely locked"), f.output())
    }

    @Test
    fun `searching the chest gives the armour and moves the quest on`() {
        val f = Fixture(1)
        f.loc(Chest, ChestCoords)
        assertEquals(1, f.player.inv.count(Helmet))
        assertEquals(1, f.player.inv.count(Plate))
        assertEquals(2, f.stage())
    }

    @Test
    fun `the chest gives nothing while the armour is held or worn`() {
        val f = Fixture(2)
        f.wear()
        f.loc(Chest, ChestCoords)
        assertEquals(0, f.player.inv.count(Helmet))
        assertEquals(0, f.player.inv.count(Plate))
        f.player.worn[0] = null
        f.player.worn[4] = null
        f.give(Helmet, 1)
        f.give(Plate, 1)
        f.loc(Chest, ChestCoords)
        assertEquals(1, f.player.inv.count(Helmet))
        assertEquals(1, f.player.inv.count(Plate))
    }

    @Test
    fun `the chest replaces only the piece that is missing`() {
        val f = Fixture(2)
        f.give(Helmet, 1)
        f.loc(Chest, ChestCoords)
        assertEquals(1, f.player.inv.count(Helmet))
        assertEquals(1, f.player.inv.count(Plate))
    }

    @Test
    fun `a full inventory leaves the armour in the chest and the stage alone`() {
        val f = Fixture(1)
        f.fillInventory()
        f.loc(Chest, ChestCoords)
        assertEquals(0, f.player.inv.count(Helmet))
        assertEquals(0, f.player.inv.count(Plate))
        assertEquals(1, f.stage())
        assertTrue(f.output().contains("don't have enough room"), f.output())
        f.player.inv[0] = null
        f.loc(Chest, ChestCoords)
        assertEquals(0, f.player.inv.count(Helmet))
        f.player.inv[1] = null
        f.loc(Chest, ChestCoords)
        assertEquals(1, f.player.inv.count(Helmet))
        assertEquals(1, f.player.inv.count(Plate))
        assertEquals(2, f.stage())
    }

    /* The doors into the prison */

    @Test
    fun `a door guard turns away a player without the armour`() {
        val f = Fixture(2)
        f.talk(DoorGuard)
        f.finish()
        assertEquals(2, f.stage())
        assertTrue(f.output().contains("Khazard guards only"), f.output())
    }

    @Test
    fun `the door guard lets a disguised newcomer in and the door lets them through`() {
        val f = Fixture(2)
        f.wear()
        f.player.coords = OutsideWest
        f.place(Door1, ArenaEntrance.West.coords, LocAngle.West)
        f.talk(DoorGuard)
        f.finish()
        assertEquals(3, f.stage())
        assertEquals(InsideWest, f.player.coords)
        assertTrue(f.output().contains("Long live General Khazard"), f.output())
    }

    @Test
    fun `clicking the door without being let in only gets a sarcastic guard`() {
        val f = Fixture(2)
        f.wear()
        f.player.coords = OutsideWest
        f.place(Door1, ArenaEntrance.West.coords, LocAngle.West)
        f.door(Door1, ArenaEntrance.West.coords, LocAngle.West)
        f.finish()
        assertEquals(OutsideWest, f.player.coords)
        assertEquals(2, f.stage())
        assertTrue(f.output().contains("asking me nicely"), f.output())
    }

    @Test
    fun `a disguised player who has been let in once can use the door both ways`() {
        val f = Fixture(3)
        f.wear()
        f.player.coords = OutsideWest
        f.place(Door1, ArenaEntrance.West.coords, LocAngle.West)
        f.door(Door1, ArenaEntrance.West.coords, LocAngle.West)
        f.finish()
        assertEquals(InsideWest, f.player.coords)
        f.player.currentMapClock += 10
        f.door(Door1, ArenaEntrance.West.coords, LocAngle.West)
        f.finish()
        assertEquals(OutsideWest, f.player.coords)
    }

    @Test
    fun `the east door guard works the same way`() {
        val f = Fixture(2)
        f.wear()
        f.player.coords = OutsideEast
        f.place(Door1, ArenaEntrance.East.coords, LocAngle.North)
        f.talk(DoorGuardEast)
        f.finish()
        assertEquals(3, f.stage())
        assertEquals(InsideEast, f.player.coords)
    }

    /* Prisoners and onlookers */

    @Test
    fun `the prisoners and the lazy guard answer the player by what they wear`() {
        val bare = Fixture(2)
        bare.talk(Joe)
        bare.finish()
        assertTrue(bare.output().contains("not a guard"), bare.output())
        val worn = Fixture(2)
        worn.wear()
        worn.talk(Kelvin)
        worn.finish()
        assertTrue(worn.output().contains("won't tell you anything"), worn.output())
        val slave = Fixture(2)
        slave.wear()
        slave.talk(Slave)
        slave.finish()
        assertTrue(slave.output().contains("leave me alone"), slave.output())
        val lazyBefore = Fixture(1)
        lazyBefore.talk(Lazy)
        lazyBefore.finish()
        assertTrue(lazyBefore.output().contains("off duty"), lazyBefore.output())
        val lazyThief = Fixture(2)
        lazyThief.talk(Lazy)
        lazyThief.finish()
        assertTrue(lazyThief.output().contains("Despicable thieving scum"), lazyThief.output())
        val lazyWorn = Fixture(2)
        lazyWorn.wear()
        lazyWorn.talk(Lazy)
        lazyWorn.finish()
        assertTrue(lazyWorn.output().contains("stole my armour"), lazyWorn.output())
    }

    @Test
    fun `the spectator follows the quest`() {
        val lines = mapOf(0 to "Servil family", 1 to "Only Khazard guards", 8 to "beat the ogre")
        for ((stage, text) in lines) {
            val f = Fixture(stage)
            f.talk(Spectator)
            f.finish()
            assertTrue(f.output().contains(text), "stage $stage: ${f.output()}")
        }
    }

    @Test
    fun `a guard without the armour chases the player away and one in it chats`() {
        val bare = Fixture(3)
        bare.talk(Guard)
        bare.finish()
        assertTrue(bare.output().contains("don't belong here"), bare.output())
        val worn = Fixture(3)
        worn.wear()
        worn.talk(Guard)
        worn.finish()
        assertTrue(worn.output().contains("Hello."), worn.output())
        assertEquals(3, worn.stage())
    }

    /* Sammy and the Head Guard */

    @Test
    fun `Sammy is met once and points to the Head Guard`() {
        val f = Fixture(3)
        f.talk(Sammy)
        f.finish()
        assertTrue(f.player.arenaMetSammy)
        assertTrue(f.output().contains("The Head Guard keeps the keys"), f.output())
        f.talk(Sammy)
        f.finish()
        assertTrue(f.output().contains("Did you manage to get the keys"), f.output())
        assertEquals(3, f.stage())
    }

    @Test
    fun `the Head Guard throws out a player who is not in the armour`() {
        val f = Fixture(3)
        f.talk(HeadGuard)
        f.finish()
        assertTrue(f.output().contains("Get out, now"), f.output())
        assertEquals(3, f.stage())
    }

    @Test
    fun `the Head Guard mentions the Khali brew on a first visit and again on the second`() {
        val f = Fixture(3)
        f.wear()
        f.talk(HeadGuard)
        f.finish()
        assertEquals(4, f.stage())
        assertTrue(f.output().contains("What's a Khali brew"), f.output())
        f.talk(HeadGuard)
        f.finish()
        assertEquals(4, f.stage())
        assertTrue(f.output().contains("dull, dull day"), f.output())
        assertEquals(0, f.player.inv.count(Keys))
    }

    @Test
    fun `a Khali brew carried before the Head Guard's first talk is kept`() {
        val f = Fixture(3)
        f.wear()
        f.give(Brew, 1)
        f.talk(HeadGuard)
        f.finish()
        assertEquals(4, f.stage())
        assertEquals(1, f.player.inv.count(Brew))
        assertEquals(0, f.player.inv.count(Keys))
    }

    @Test
    fun `the brew buys the keys and the swap is atomic`() {
        val f = Fixture(4)
        f.wear()
        f.give(Brew, 1)
        f.talk(HeadGuard)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(0, f.player.inv.count(Brew))
        assertEquals(1, f.player.inv.count(Keys))
        assertTrue(f.output().contains("hands you the cell keys"), f.output())
    }

    @Test
    fun `the swap is complete even if the player walks away straight after it`() {
        val f = Fixture(4)
        f.wear()
        f.give(Brew, 1)
        f.talk(HeadGuard)
        f.until { f.stage() == 5 }
        f.cancel()
        assertEquals(5, f.stage())
        assertEquals(0, f.player.inv.count(Brew))
        assertEquals(1, f.player.inv.count(Keys))
    }

    @Test
    fun `the swap works with a full inventory since the brew's slot is freed`() {
        val f = Fixture(4)
        f.wear()
        f.give(Brew, 1)
        f.fillInventory()
        f.talk(HeadGuard)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(1, f.player.inv.count(Keys))
        assertEquals(0, f.player.inv.count(Brew))
    }

    @Test
    fun `the Head Guard refuses a beer and anything else offered for the keys`() {
        val f = Fixture(4)
        f.wear()
        f.give(Beer, 1)
        f.give("obj.logs", 1)
        f.talk(HeadGuard)
        f.finish()
        assertEquals(4, f.stage())
        assertEquals(1, f.player.inv.count(Beer))
        assertEquals(1, f.player.inv.count("obj.logs"))
        assertEquals(0, f.player.inv.count(Keys))
    }

    @Test
    fun `a Head Guard who has handed over the keys takes no more brews`() {
        val f = Fixture(5)
        f.wear()
        f.give(Keys, 1)
        f.give(Brew, 1)
        f.talk(HeadGuard)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(1, f.player.inv.count(Brew))
        assertEquals(1, f.player.inv.count(Keys))
        assertTrue(f.output().contains("room keeps"), f.output())
    }

    @Test
    fun `lost keys are replaced once and held keys are not duplicated`() {
        val f = Fixture(5)
        f.wear()
        f.talk(HeadGuard)
        f.finish()
        assertEquals(1, f.player.inv.count(Keys))
        assertTrue(f.output().contains("spare set"), f.output())
        f.talk(HeadGuard)
        f.finish()
        assertEquals(1, f.player.inv.count(Keys))
    }

    @Test
    fun `a drunk Head Guard still sends away a player in no armour`() {
        val f = Fixture(5)
        f.talk(HeadGuard)
        f.finish()
        assertEquals(0, f.player.inv.count(Keys))
        assertTrue(f.output().contains("don't like strangers"), f.output())
    }

    /* The barman */

    @Test
    fun `the barman sells a Khali brew for five coins`() {
        val f = Fixture(3)
        f.give(Coins, 12)
        f.talk(Barman)
        f.finish(listOf(2))
        assertEquals(1, f.player.inv.count(Brew))
        assertEquals(7, f.player.inv.count(Coins))
    }

    @Test
    fun `the barman sells a beer for two coins`() {
        val f = Fixture(0)
        f.give(Coins, 12)
        f.talk(Barman)
        f.finish(listOf(1))
        assertEquals(1, f.player.inv.count(Beer))
        assertEquals(10, f.player.inv.count(Coins))
    }

    @Test
    fun `without the coins the barman sells nothing`() {
        val f = Fixture(3)
        f.give(Coins, 4)
        f.talk(Barman)
        f.finish(listOf(2))
        assertEquals(0, f.player.inv.count(Brew))
        assertEquals(4, f.player.inv.count(Coins))
        assertTrue(f.output().contains("enough coins"), f.output())
        val none = Fixture(3)
        none.talk(Barman)
        none.finish(listOf(1))
        assertEquals(0, none.player.inv.count(Beer))
    }

    @Test
    fun `a full inventory cannot take the brew and keeps the coins`() {
        val f = Fixture(3)
        f.give(Coins, 12)
        f.fillInventory()
        f.talk(Barman)
        f.finish(listOf(2))
        assertEquals(0, f.player.inv.count(Brew))
        assertEquals(12, f.player.inv.count(Coins))
        assertTrue(f.output().contains("enough room"), f.output())
    }

    @Test
    fun `the barman's news loops back to the menu and refusing ends it`() {
        val f = Fixture(3)
        f.give(Coins, 12)
        f.talk(Barman)
        f.finish(listOf(3, 4))
        assertTrue(f.output().lowercase().contains("great entertainment"), f.output())
        assertEquals(12, f.player.inv.count(Coins))
        assertEquals(0, f.player.inv.count(Brew))
    }

    /* Sammy's cell */

    @Test
    fun `the keys on the right door free Sammy and begin the ogre fight`() {
        val f = Fixture(5)
        f.give(Keys, 1)
        f.player.coords = SammyCorridor
        f.useKeysOn(SammyGate, SammyGateCoords, LocAngle.West)
        f.finish()
        assertEquals(7, f.stage())
        assertEquals(1, f.site.entered)
        assertTrue(f.site.inside)
        assertEquals(1, f.player.inv.count(Keys))
        f.site.assertSpawned(FightArenaScenes.SammyType)
        f.site.assertSpawned(FightArenaScenes.JustinCutsceneType)
        f.site.assertSpawned(FightArenaScenes.OgreType, engaged = true)
        assertTrue(f.site.removed.any { it.type.isType(FightArenaScenes.OgreCutsceneType) })
    }

    @Test
    fun `talking to Sammy with the keys does the same`() {
        val f = Fixture(5)
        f.give(Keys, 1)
        f.talk(Sammy)
        f.finish()
        assertEquals(7, f.stage())
        assertTrue(f.site.inside)
        assertTrue(f.output().contains("I have the keys"), f.output())
    }

    @Test
    fun `the keys on any other cell open nothing and cost nothing`() {
        val f = Fixture(5)
        f.give(Keys, 1)
        f.useKeysOn(PrisonGate, CoordGrid(2617, 3163, 0), LocAngle.West)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(0, f.site.entered)
        assertEquals(1, f.player.inv.count(Keys))
        assertTrue(f.output().contains("Sammy isn't in this cell"), f.output())
    }

    @Test
    fun `a wrong item on the gate does nothing`() {
        val f = Fixture(5)
        f.give(Beer, 1)
        f.useOnLoc(SammyGate, SammyGateCoords, LocAngle.West, Beer)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(0, f.site.entered)
        assertEquals(1, f.player.inv.count(Beer))
        assertTrue(f.output().contains("Nothing interesting happens"), f.output())
    }

    @Test
    fun `clicking a cell gate only says that it is locked`() {
        val f = Fixture(5)
        f.give(Keys, 1)
        f.door(SammyGate, SammyGateCoords, LocAngle.West)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(0, f.site.entered)
        assertTrue(f.output().contains("securely locked"), f.output())
    }

    @Test
    fun `without the keys Sammy is not freed`() {
        val f = Fixture(5)
        f.talk(Sammy)
        f.finish()
        assertEquals(5, f.stage())
        assertEquals(0, f.site.entered)
        assertTrue(f.output().contains("Did you manage") || f.output().contains("Are you Sammy"))
    }

    @Test
    fun `an arena that cannot be made leaves the player at the freed stage to retry`() {
        val f = Fixture(5)
        f.give(Keys, 1)
        f.site.available = false
        f.talk(Sammy)
        f.finish()
        assertEquals(6, f.stage())
        assertFalse(f.site.inside)
        f.site.available = true
        f.player.coords = FightArenaPlaces.ArenaExit
        f.door(ArenaDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish(listOf(1))
        assertTrue(f.site.inside)
        assertEquals(7, f.stage())
        f.site.assertSpawned(FightArenaScenes.OgreType, engaged = true)
    }

    /* The arena door and the guards' offer to go back */

    @Test
    fun `the arena door is locked until Sammy is free and records the attempt`() {
        val f = Fixture(3)
        assertFalse(f.player.arenaTriedDoor)
        f.door(ArenaDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish()
        assertTrue(f.player.arenaTriedDoor)
        assertEquals(0, f.site.entered)
        assertTrue(f.output().contains("securely locked"), f.output())
    }

    @Test
    fun `the arena door asks before sending the player back into the fights`() {
        val f = Fixture(9)
        f.door(ArenaDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish(listOf(2))
        assertEquals(0, f.site.entered)
        f.door(ArenaDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish(listOf(1))
        assertEquals(1, f.site.entered)
        f.site.assertSpawned(FightArenaScenes.ScorpionType, engaged = true)
    }

    @Test
    fun `the arena door is locked again once Khazard has been beaten`() {
        val f = Fixture(13)
        f.door(ArenaDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish()
        assertEquals(0, f.site.entered)
        assertTrue(f.output().contains("securely locked"), f.output())
    }

    @Test
    fun `the escape door asks first, and its quick option does not`() {
        val f = Fixture(9)
        f.site.inside = true
        f.door(EscapeDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish(listOf(2))
        assertTrue(f.site.inside)
        f.door(EscapeDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish(listOf(1))
        assertFalse(f.site.inside)
        assertEquals(FightArenaPlaces.ArenaExit, f.player.coords)
        f.site.inside = true
        f.door(EscapeDoor, FightArenaPlaces.ArenaDoor, LocAngle.West, slot = 2)
        f.finish()
        assertFalse(f.site.inside)
        assertEquals(9, f.stage())
    }

    @Test
    fun `the ordinary arena door also leaves safely from inside`() {
        val f = Fixture(9)
        f.site.inside = true
        f.door(ArenaDoor, FightArenaPlaces.ArenaDoor, LocAngle.West)
        f.finish(listOf(1))
        assertFalse(f.site.inside)
    }

    @Test
    fun `a guard sends the player back in when a fight is waiting`() {
        for ((stage, opponent) in
            mapOf(
                6 to FightArenaScenes.OgreType,
                7 to FightArenaScenes.OgreType,
                9 to FightArenaScenes.ScorpionType,
                10 to FightArenaScenes.BouncerType,
                12 to FightArenaScenes.GeneralType,
            )) {
            val f = Fixture(stage)
            f.wear()
            f.talk(Guard)
            f.finish(listOf(1))
            assertEquals(1, f.site.entered, "stage $stage")
            f.site.assertSpawned(opponent, engaged = true)
        }
    }

    @Test
    fun `a guard lets the player think it over`() {
        val f = Fixture(9)
        f.wear()
        f.talk(Guard)
        f.finish(listOf(2))
        assertEquals(0, f.site.entered)
        assertEquals(9, f.stage())
    }

    @Test
    fun `a prisoner who left the jail scene is sent back to their cell`() {
        val f = Fixture(8)
        f.player.coords = FightArenaPlaces.ArenaExit
        f.talk(Guard)
        f.finish()
        assertEquals(FightArenaPlaces.HengradCell, f.player.coords)
        assertEquals(8, f.stage())
        assertEquals(0, f.site.entered)
    }

    @Test
    fun `an arena guard recognises the player once the quest is done`() {
        val f = Fixture(14)
        f.wear()
        f.talk(Guard)
        f.finish()
        assertTrue(f.output().contains("killed Bouncer"), f.output())
        assertEquals(14, f.stage())
    }

    /* The fights */

    @Test
    fun `returning at the ogre stage brings the onlookers and the ogre`() {
        val f = Fixture(7)
        f.run { with(f.scenes) { returnToArena() } }
        f.finish()
        f.site.assertSpawned(FightArenaScenes.SammyType)
        f.site.assertSpawned(FightArenaScenes.JustinType)
        f.site.assertSpawned(FightArenaScenes.OgreType, engaged = true)
        assertEquals(7, f.stage())
    }

    @Test
    fun `beating the ogre is followed by Khazard, a cell and no reward yet`() {
        val f = Fixture(7)
        f.site.inside = true
        f.run { with(f.scenes) { opponentDefeated(Opponent.Ogre) } }
        f.finish()
        assertEquals(8, f.stage())
        assertFalse(f.site.inside)
        assertEquals(FightArenaPlaces.HengradCell, f.player.coords)
        assertTrue(f.output().contains("General Khazard, I presume"), f.output())
        assertTrue(f.output().contains("Take"), f.output())
        assertEquals(0, f.player.vars["varp.qp"])
        assertEquals(0, f.player.inv.count(Coins))
    }

    @Test
    fun `the quest stage is already written if the jail scene is interrupted`() {
        val f = Fixture(7)
        f.site.inside = true
        f.run { with(f.scenes) { opponentDefeated(Opponent.Ogre) } }
        f.until { f.stage() == 8 }
        f.cancel()
        assertEquals(8, f.stage())
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test
    fun `Hengrad's cell talk leads back to the arena and the scorpion`() {
        val f = Fixture(8)
        f.talk(Hengrad)
        f.finish()
        assertEquals(9, f.stage())
        assertTrue(f.site.inside)
        assertTrue(f.output().contains("Good luck out there"), f.output())
        assertTrue(f.player.arenaScorpionIntro)
        f.site.assertSpawned(FightArenaScenes.GeneralPropType)
        f.site.assertSpawned(FightArenaScenes.ScorpionType, engaged = true)
    }

    @Test
    fun `the scorpion introduction is not repeated on a second visit`() {
        val f = Fixture(9)
        f.player.arenaScorpionIntro = true
        f.run { with(f.scenes) { returnToArena() } }
        f.finish()
        assertEquals(1, f.site.spawned.count { it.type.isType(FightArenaScenes.ScorpionType) })
    }

    @Test
    fun `beating the scorpion releases Bouncer straight away`() {
        val f = Fixture(9)
        f.site.inside = true
        f.run { with(f.scenes) { opponentDefeated(Opponent.Scorpion) } }
        f.finish()
        assertEquals(10, f.stage())
        assertTrue(f.player.arenaBouncerIntro)
        f.site.assertSpawned(FightArenaScenes.BouncerType, engaged = true)
        assertTrue(f.site.inside)
    }

    @Test
    fun `beating Bouncer ends the bargain and sets Khazard on the player`() {
        val f = Fixture(10)
        f.run { with(f.scenes) { returnToArena() } }
        f.finish()
        assertEquals(10, f.stage())
        f.run { with(f.scenes) { opponentDefeated(Opponent.Bouncer) } }
        f.finish()
        assertEquals(12, f.stage())
        assertTrue(f.player.arenaKhazardIntro)
        assertTrue(f.output().contains("Bouncer... dead"), f.output())
        f.site.assertSpawned(FightArenaScenes.GeneralType, engaged = true)
        assertTrue(f.site.removed.any { it.type.isType(FightArenaScenes.SammyType) })
        assertTrue(f.site.removed.any { it.type.isType(FightArenaScenes.JustinType) })
        assertTrue(f.site.removed.any { it.type.isType(FightArenaScenes.GeneralPropType) })
        assertTrue(f.site.inside)
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test
    fun `Khazard's speech after Bouncer is skipped once it has been heard`() {
        val f = Fixture(11)
        f.player.arenaKhazardIntro = true
        f.run { with(f.scenes) { returnToArena() } }
        f.finish()
        assertEquals(12, f.stage())
        assertFalse(f.output().contains("Bouncer... dead"), f.output())
        f.site.assertSpawned(FightArenaScenes.GeneralType, engaged = true)
    }

    @Test
    fun `beating Khazard lets the player out with the fights behind them`() {
        val f = Fixture(12)
        f.site.inside = true
        f.run { with(f.scenes) { opponentDefeated(Opponent.General) } }
        f.finish()
        assertEquals(13, f.stage())
        assertFalse(f.site.inside)
        assertEquals(FightArenaPlaces.ArenaExit, f.player.coords)
        assertTrue(f.output().contains("freedom"), f.output())
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test
    fun `each stage names the next opponent`() {
        val f = Fixture(0)
        val expected =
            mapOf(
                5 to null,
                6 to Opponent.Ogre,
                7 to Opponent.Ogre,
                8 to null,
                9 to Opponent.Scorpion,
                10 to Opponent.Bouncer,
                11 to Opponent.General,
                12 to Opponent.General,
                13 to null,
                14 to null,
            )
        for ((stage, opponent) in expected) {
            assertEquals(opponent, f.quest.nextOpponent(stage), "stage $stage")
        }
    }

    @Test
    fun `no fight stage is entered in the wrong order`() {
        val f = Fixture(3)
        f.run { with(f.scenes) { returnToArena() } }
        f.finish()
        assertEquals(0, f.site.entered)
        val jailed = Fixture(8)
        jailed.run { with(jailed.scenes) { returnToArena() } }
        jailed.finish()
        assertEquals(0, jailed.site.entered)
        val done = Fixture(13)
        done.run { with(done.scenes) { returnToArena() } }
        done.finish()
        assertEquals(0, done.site.entered)
    }

    /* Hengrad's other talks */

    @Test
    fun `Hengrad greets visitors by what they wear and warns the survivors`() {
        val bare = Fixture(3)
        bare.talk(Hengrad)
        bare.finish()
        assertTrue(bare.output().contains("not a guard"), bare.output())
        val worn = Fixture(3)
        worn.wear()
        worn.talk(Hengrad)
        worn.finish()
        assertTrue(worn.output().contains("real guard"), worn.output())
        val escaped = Fixture(9)
        escaped.talk(Hengrad)
        escaped.finish()
        assertTrue(escaped.output().contains("Flee while you can"), escaped.output())
        assertEquals(9, escaped.stage())
        val late = Fixture(12)
        late.talk(Hengrad)
        late.finish()
        assertTrue(late.output().contains("Get out of here"), late.output())
    }

    @Test
    fun `Hengrad's cell gate opens for the keys once the player is locked in`() {
        val f = Fixture(8)
        f.give(Keys, 1)
        f.player.coords = FightArenaPlaces.HengradCell
        f.place(PrisonGate, HengradGateCoords, LocAngle.South)
        f.useKeysOn(PrisonGate, HengradGateCoords, LocAngle.South)
        f.finish()
        assertEquals(CoordGrid(2600, 3141, 0), f.player.coords)
        assertEquals(1, f.player.inv.count(Keys))
    }

    /* Journal */

    @Test
    fun `the journal follows the stage`() {
        val expected =
            mapOf(
                1 to "chest",
                2 to "door guards",
                3 to "northernmost cell",
                4 to "Khali brew",
                5 to "cell keys",
                7 to "ogre",
                8 to "Hengrad",
                9 to "scorpion",
                10 to "Bouncer",
                12 to "arena door",
                13 to "tell <red>Lady Servil",
            )
        for ((stage, text) in expected) {
            val f = Fixture(stage)
            assertTrue(f.quest.questLog(f.access()).contains(text), "stage $stage")
        }
        val sammy = Fixture(3)
        sammy.player.arenaMetSammy = true
        assertTrue(sammy.quest.questLog(sammy.access()).contains("Head Guard"))
        val done = Fixture(14)
        assertTrue(done.quest.completedLog(done.access()).contains("QUEST COMPLETE"))
    }

    /* Fixture */

    private class StubSite : ArenaSite {
        var inside = false
        var entered = 0
        var available = true
        val spawned = mutableListOf<Npc>()
        val engaged = mutableListOf<Npc>()
        val removed = mutableListOf<Npc>()

        override suspend fun enter(access: ProtectedAccess): Boolean {
            if (!available) {
                access.mes("No arena.")
                return false
            }
            entered++
            inside = true
            access.telejump(FightArenaPlaces.ArenaEntry, org.rsmod.api.player.hook.TeleportType.Exempt)
            return true
        }

        override suspend fun leave(access: ProtectedAccess, dest: CoordGrid) {
            inside = false
            access.telejump(dest, org.rsmod.api.player.hook.TeleportType.Exempt)
        }

        override fun isInside(player: Player): Boolean = inside

        override fun spawn(player: Player, type: String, at: CoordGrid, face: Direction?): Npc? {
            if (!inside) return null
            val npc = Npc(type, at)
            spawned += npc
            return npc
        }

        override fun remove(npc: Npc) {
            removed += npc
        }

        override fun npcsOf(player: Player): List<Npc> = spawned.filter { it !in removed }

        override fun engage(npc: Npc, player: Player) {
            engaged += npc
        }

        override fun owns(npc: Npc): Boolean = npc in spawned

        fun assertSpawned(type: String, engaged: Boolean = false) {
            val npc = spawned.firstOrNull { it.type.isType(type) }
            assertNotNull(npc, "$type was not spawned: ${spawned.map { it.type.internalName }}")
            if (engaged) {
                assertTrue(this.engaged.contains(npc), "$type was not set on the player")
            }
        }
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("fight-arena-test")
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
                    getRandom = { DefaultGameRandom(1) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 905L
                slotId = 1
                assignUid()
                coords = CoordGrid(2600, 3141, 0)
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

        val quest = FightArenaQuest()
        val site = StubSite()
        val scenes = FightArenaScenes(quest, site)
        private val doors = FightArenaDoors(locs)
        private var nextSlot = 0

        init {
            for (x in 2576..2632 step 8) {
                for (z in 3136..3200 step 8) {
                    collision.allocateIfAbsent(x, z, 0)
                }
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
            with(FightArenaPeople(quest, scenes, doors, objs, aiInteractions)) {
                scripts.startup()
            }
            with(FightArenaLocs(quest, scenes, doors, site, objs)) { scripts.startup() }
            stage(stage)
        }

        fun stage() = quest.quest.getQuestStage(player)

        fun stage(value: Int) {
            VarPlayerIntMapSetter.set(player, "varbit.fight_arena_progress", value)
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

        fun wear() {
            player.worn[0] = InvObj(Helmet, 1)
            player.worn[4] = InvObj(Plate, 1)
        }

        fun fillInventory() {
            for (slot in 0 until 28) {
                if (player.inv[slot] == null) {
                    player.inv[slot] = InvObj("obj.logs", 1)
                }
            }
        }

        fun talk(symbol: String) {
            val npc = Npc(symbol, player.coords.translateZ(1))
            run { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        private fun boundLoc(symbol: String, coords: CoordGrid, angle: LocAngle): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val shape = if (symbol == Chest) LocShape.CentrepieceStraight else LocShape.WallStraight
            return BoundLocInfo(
                LocInfo(
                    if (shape == LocShape.WallStraight) 0 else 2,
                    coords,
                    LocEntity(type.id, shape.id, angle.id),
                ),
                type,
            )
        }

        fun place(symbol: String, coords: CoordGrid, angle: LocAngle) {
            locs.add(coords, symbol, 1000, angle, LocShape.WallStraight)
        }

        fun loc(symbol: String, coords: CoordGrid, angle: LocAngle = LocAngle.West) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val loc = boundLoc(symbol, coords, angle)
            run { assertTrue(events.publish(this, LocEvents.Op1(loc, loc, type))) }
        }

        fun door(symbol: String, coords: CoordGrid, angle: LocAngle, slot: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val loc = boundLoc(symbol, coords, angle)
            run {
                val event =
                    if (slot == 2) LocEvents.Op2(loc, loc, type) else LocEvents.Op1(loc, loc, type)
                assertTrue(events.publish(this, event))
            }
        }

        fun useKeysOn(symbol: String, coords: CoordGrid, angle: LocAngle) =
            useOnLoc(symbol, coords, angle, Keys)

        fun useOnLoc(symbol: String, coords: CoordGrid, angle: LocAngle, obj: String) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val item = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            val loc = boundLoc(symbol, coords, angle)
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

        fun assertComplete(scroll: Boolean = true) {
            assertEquals(14, stage())
            assertEquals(14, player.vars["varp.arenaquest"])
            assertEquals(2, player.vars["varp.qp"])
            assertEquals(12175, player.statMap.getXP("stat.attack"))
            assertEquals(2175, player.statMap.getXP("stat.thieving"))
            assertEquals(1000, player.inv.count(Coins))
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
        private const val Lady = "npc.lady_servil_vis"
        private const val Sammy = "npc.sammy_servil_vis"
        private const val HeadGuard = "npc.arena_guard2"
        private const val Lazy = "npc.arena_guard3"
        private const val Guard = "npc.arena_guard1"
        private const val DoorGuard = "npc.arena_guard_door_1"
        private const val DoorGuardEast = "npc.arena_guard_door_2"
        private const val Barman = "npc.khazard_barman"
        private const val Hengrad = "npc.hengrad"
        private const val Spectator = "npc.arena_spectator"
        private const val Joe = "npc.fightslave_joe"
        private const val Kelvin = "npc.fightslave_kelvin"
        private const val Slave = "npc.fightslave"

        private const val Chest = "loc.arena_guard_chest_shut"
        private const val Door1 = "loc.fightarena_door1"
        private const val ArenaDoor = "loc.fightarena_door2"
        private const val EscapeDoor = "loc.fightarena_door2_escape"
        private const val PrisonGate = "loc.arena_prisondoor"
        private const val SammyGate = "loc.arena_jeremydoor"

        private const val Helmet = "obj.khazard_helmet"
        private const val Plate = "obj.khazard_platemail"
        private const val Keys = "obj.khazard_cellkeys"
        private const val Brew = "obj.khali_brew"
        private const val Beer = "obj.beer"
        private const val Coins = "obj.coins"

        private val ChestCoords = CoordGrid(2613, 3189, 0)
        private val SammyGateCoords = CoordGrid(2617, 3167, 0)
        private val SammyCorridor = CoordGrid(2618, 3167, 0)
        private val HengradGateCoords = CoordGrid(2600, 3142, 0)
        private val OutsideWest = CoordGrid(2584, 3141, 0)
        private val InsideWest = CoordGrid(2585, 3141, 0)
        private val OutsideEast = CoordGrid(2617, 3172, 0)
        private val InsideEast = CoordGrid(2617, 3171, 0)

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
