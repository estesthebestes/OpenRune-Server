package org.rsmod.content.quest.area.barbarianoutpost.barcrawl

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocEvents
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
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.CARD
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.COINS
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs.DeadMansChestBartender
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs.DragonInnBartender
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs.FlyingHorseBartender
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs.ForestersArmsBartender
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.npcs.JollyBoarBartender
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
class BarcrawlInteractionTest {
    @Test
    fun `claiming to be a barbarian starts the miniquest and hands over a clean card`() {
        val f = Fixture()
        f.sign(BarcrawlBar.BlueMoon)
        f.choose(1, 2)
        f.talkToGuard()
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.player.inv.count(CARD))
        assertFalse(f.quest.isSigned(f.player, BarcrawlBar.BlueMoon))
        assertTrue(f.said("The guard hands you a Barcrawl card."))
    }

    @Test
    fun `the stage is held in varp barcrawl and mirrored for the quest list`() {
        val f = Fixture()
        f.choose(1, 2)
        f.talkToGuard()
        assertEquals(STAGE_STARTED, f.player.vars["varp.barcrawl"])
        assertEquals(STAGE_STARTED, f.player.vars["varbit.barcrawl_progress"])
        f.signAll()
        f.choose(2)
        f.talkToGuard()
        assertEquals(STAGE_COMPLETE, f.player.vars["varp.barcrawl"])
        assertEquals(STAGE_COMPLETE, f.player.vars["varbit.barcrawl_progress"])
    }

    @Test
    fun `declining or admitting to not being a barbarian keeps the miniquest unstarted`() {
        for (options in listOf(listOf(2), listOf(1, 1))) {
            val f = Fixture()
            f.choose(*options.toIntArray())
            f.talkToGuard()
            assertEquals(0, f.stage(), "options $options")
            assertEquals(0, f.player.inv.count(CARD), "options $options")
        }
    }

    @Test
    fun `a full inventory gets no card and does not start the miniquest`() {
        val f = Fixture()
        f.fill()
        f.choose(1, 2)
        f.talkToGuard()
        assertEquals(0, f.stage())
        assertEquals(0, f.player.inv.count(CARD))
        assertFalse(f.said("Barcrawl"))
    }

    @Test
    fun `an unfinished card is kept and the guard sends the player back`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.sign(BarcrawlBar.Blurberry)
        f.talkToGuard()
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.player.inv.count(CARD))
        assertTrue(f.said("I haven't finished it yet."))
    }

    @Test
    fun `a lost card is replaced and the signatures start over`() {
        val f = Fixture(STAGE_STARTED)
        f.sign(BarcrawlBar.BlueMoon, BarcrawlBar.RisingSun)
        f.talkToGuard()
        assertEquals(1, f.player.inv.count(CARD))
        assertEquals(STAGE_STARTED, f.stage())
        for (bar in BarcrawlBar.entries) {
            assertFalse(f.quest.isSigned(f.player, bar), bar.name)
        }
    }

    @Test
    fun `handing in a fully signed card completes the miniquest once`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.signAll()
        f.choose(1)
        f.talkToGuard()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.player.inv.count(CARD))
        assertEquals(1, f.player.vars["varbit.auto_smash_vials"])
        assertEquals(1, f.player.vars["varbit.quests_completed_count"])
        f.choose(2)
        f.talkToGuard()
        f.talkToGuard()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.player.vars["varbit.quests_completed_count"])
    }

    @Test
    fun `the vial smashing offer can be declined at the hand-in`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.signAll()
        f.choose(2)
        f.talkToGuard()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.player.vars["varbit.auto_smash_vials"])
    }

    @Test
    fun `an unsigned bar keeps the hand-in from completing`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.signAll()
        VarPlayerIntMapSetter.set(f.player, BarcrawlBar.RustyAnchor.varbit, 0)
        f.talkToGuard()
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.player.inv.count(CARD))
    }

    @Test
    fun `walking away from the hand-in keeps the card and the stage`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.signAll()
        f.talkToGuardAndAbortAtVialOffer()
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.player.inv.count(CARD))
        assertEquals(0, f.player.vars["varbit.auto_smash_vials"])
    }

    @Test
    fun `after the miniquest the guard toggles vial smashing`() {
        val f = Fixture(STAGE_COMPLETE)
        f.choose(1)
        f.talkToGuard()
        assertEquals(1, f.player.vars["varbit.auto_smash_vials"])
        f.choose(1)
        f.talkToGuard()
        assertEquals(0, f.player.vars["varbit.auto_smash_vials"])
        f.guardOp3()
        assertEquals(1, f.player.vars["varbit.auto_smash_vials"])
        assertTrue(f.said("Vial smashing is now turned on."))
        f.guardOp3()
        assertEquals(0, f.player.vars["varbit.auto_smash_vials"])
        assertTrue(f.said("Vial smashing is now turned off."))
    }

    @Test
    fun `every bar takes its price, signs the card and refuses without the coins`() {
        for (bar in BarcrawlBar.entries) {
            val f = Fixture(STAGE_STARTED)
            f.give(CARD)
            f.give(COINS, bar.price - 1)
            f.serve(bar)
            assertFalse(f.quest.isSigned(f.player, bar), "${bar.name} served without the coins")
            assertEquals(bar.price - 1, f.player.inv.count(COINS), bar.name)

            val g = Fixture(STAGE_STARTED)
            g.give(CARD)
            g.give(COINS, bar.price + 5)
            g.serve(bar)
            assertTrue(g.quest.isSigned(g.player, bar), "${bar.name} did not sign")
            assertEquals(5, g.player.inv.count(COINS), bar.name)
            assertTrue(g.said(bar.messages.first().format("")), bar.name)
            for (other in BarcrawlBar.entries.filter { it != bar }) {
                assertFalse(g.quest.isSigned(g.player, other), "${bar.name} also signed $other")
            }
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun `a drink lowers the stats it is meant to`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.give(COINS, 100)
        val drained = BarcrawlBar.BlueMoon.effect.drained
        for (stat in drained) {
            f.player.statMap.setBaseLevel(stat, 60)
            f.player.statMap.setCurrentLevel(stat, 60)
        }
        f.serve(BarcrawlBar.BlueMoon)
        for (stat in drained) {
            assertTrue(f.player.statMap.getCurrentLevel(stat) < 60, stat)
        }
    }

    @Test
    fun `a bar only offers the barcrawl with the card, in progress, and while unsigned`() {
        val f = Fixture()
        assertFalse(f.quest.canServe(f.player, BarcrawlBar.DragonInn))
        f.give(CARD)
        assertFalse(f.quest.canServe(f.player, BarcrawlBar.DragonInn))
        VarPlayerIntMapSetter.set(f.player, "varbit.barcrawl_stage", STAGE_STARTED)
        assertTrue(f.quest.canServe(f.player, BarcrawlBar.DragonInn))
        f.sign(BarcrawlBar.DragonInn)
        assertFalse(f.quest.canServe(f.player, BarcrawlBar.DragonInn))
        f.take(CARD)
        assertFalse(f.quest.canServe(f.player, BarcrawlBar.BlueMoon))
    }

    @Test
    fun `the Dragon Inn bartender serves the barcrawl from the menu`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.give(COINS, 20)
        f.choose(4)
        f.talkTo("npc.dragon_bartender")
        assertTrue(f.quest.isSigned(f.player, BarcrawlBar.DragonInn))
        assertEquals(8, f.player.inv.count(COINS))
        assertEquals(0, f.player.inv.count("obj.beer"))
    }

    @Test
    fun `without the card the same menu slot is the cheap beer`() {
        val f = Fixture(STAGE_STARTED)
        f.give(COINS, 20)
        f.choose(4)
        f.talkTo("npc.dragon_bartender")
        assertFalse(f.quest.isSigned(f.player, BarcrawlBar.DragonInn))
        assertEquals(1, f.player.inv.count("obj.beer"))
        assertEquals(18, f.player.inv.count(COINS))
    }

    @Test
    fun `a bar that already signed does not sign again`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.give(COINS, 40)
        f.sign(BarcrawlBar.FlyingHorseInn)
        f.choose(2)
        f.talkTo("npc.flyinghorse_bartender")
        assertEquals(40, f.player.inv.count(COINS))
    }

    @Test
    fun `the other bartenders in this module serve the barcrawl too`() {
        for ((npc, bar, option) in
            listOf(
                Triple("npc.deadmans_bartender", BarcrawlBar.DeadMansChest, 4),
                Triple("npc.flyinghorse_bartender", BarcrawlBar.FlyingHorseInn, 3),
                Triple("npc.foresters_bartender", BarcrawlBar.ForestersArms, 3),
                Triple("npc.jollyboar_bartender", BarcrawlBar.JollyBoarInn, 4),
            )) {
            val f = Fixture(STAGE_STARTED)
            f.give(CARD)
            f.give(COINS, bar.price)
            f.choose(option)
            f.talkTo(npc)
            assertTrue(f.quest.isSigned(f.player, bar), npc)
            assertEquals(0, f.player.inv.count(COINS), npc)
        }
    }

    @Test
    fun `reading the card lists the bars until every one is signed`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.sign(BarcrawlBar.BlueMoon)
        f.readCard()
        assertTrue(f.player.ui.containsModal("interface.scroll"))
        assertTrue(f.said("Uncle Humphrey's Gutrot"))
        f.signAll()
        f.readCard()
        assertTrue(f.said("You are too drunk to be able to read the barcrawl card."))
    }

    @Test
    fun `the gate stays shut for non-barbarians and the guard answers it`() {
        val f = Fixture(STAGE_STARTED)
        f.at(WEST_OF_GATE)
        f.gate(UPPER_PANEL)
        assertEquals(WEST_OF_GATE, f.player.coords)
        assertTrue(f.said("So, how's the Barcrawl coming along?"))
        assertTrue(f.hasPanels())
    }

    @Test
    fun `the gate is locked when no guard is around`() {
        val f = Fixture(STAGE_STARTED, guardAtGate = false)
        f.at(WEST_OF_GATE)
        f.gate(UPPER_PANEL)
        assertEquals(WEST_OF_GATE, f.player.coords)
        assertTrue(f.said("The gate is locked."))
    }

    @Test
    fun `completing the miniquest opens the gate to the agility course`() {
        val f = Fixture(STAGE_COMPLETE)
        f.at(WEST_OF_GATE)
        f.gate(UPPER_PANEL)
        assertEquals(WEST_OF_GATE.translateX(1), f.player.coords)
        assertFalse(f.hasPanels())
    }

    @Test
    fun `a realm that assumes every quest done keeps the gate open`() {
        val previous = QuestRequirements.activePolicy()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
        try {
            val f = Fixture(0)
            f.at(WEST_OF_GATE)
            f.gate(UPPER_PANEL)
            assertEquals(WEST_OF_GATE.translateX(1), f.player.coords)
        } finally {
            QuestRequirements.install(previous)
        }
    }

    @Test
    fun `from the course side the gate always lets the player out`() {
        val f = Fixture(0)
        f.at(WEST_OF_GATE.translateX(2))
        f.gate(LOWER_PANEL)
        assertEquals(WEST_OF_GATE, f.player.coords)
    }

    @Test
    fun `the journal strikes signed bars and lists the rest`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CARD)
        f.sign(BarcrawlBar.BlueMoon)
        val journal = f.quest.questLog(f.access())
        assertTrue(journal.contains("<str>Blue Moon Inn - Uncle Humphrey's Gutrot</str>"), journal)
        assertTrue(journal.contains("Rusty Anchor - Black Skull Ale"), journal)
        assertFalse(journal.contains("<str>Rusty Anchor"), journal)
        f.signAll()
        assertTrue(f.quest.questLog(f.access()).contains("Barbarian guard"))
        assertTrue(f.quest.completedLog(f.access()).contains("QUEST COMPLETE"))
    }

    private class Fixture(stage: Int = 0, guardAtGate: Boolean = true) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("barcrawl-test")
        private lateinit var regions: RegionRegistry
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getCollision = { collision },
                    getNpcList = { npcs },
                    getNpcInteractions = { NpcInteractions(events) },
                    getRandom = { DefaultGameRandom(1) },
                    getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
                    getAreaChecker = { AreaChecker(regions, AreaIndex()) },
                )
        private val clock = MapClock(100)
        private val npcRepo: NpcRepository
        private val locRepo: LocRepository
        private val objRepo = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
        private val picks = ArrayDeque<Int>()

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 9114L
                observerUUID = 9114L
                slotId = 1
                assignUid()
                coords = TOWN
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

        val quest = BarcrawlQuest()

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
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
            with(BarbarianGuard(quest, npcRepo, objRepo, locRepo)) { scripts.startup() }
            with(DeadMansChestBartender(quest, objRepo)) { scripts.startup() }
            with(DragonInnBartender(quest, objRepo)) { scripts.startup() }
            with(FlyingHorseBartender(quest, objRepo)) { scripts.startup() }
            with(ForestersArmsBartender(quest, objRepo)) { scripts.startup() }
            with(JollyBoarBartender(quest, objRepo)) { scripts.startup() }
            locRepo.add(UPPER_PANEL, "loc.barbariangatel", 1_000_000, LocAngle.East, LocShape.WallStraight)
            locRepo.add(LOWER_PANEL, "loc.barbariangater", 1_000_000, LocAngle.East, LocShape.WallStraight)
            if (guardAtGate) {
                npcRepo.add(Npc("npc.barbguard1", GUARD_TILE), Int.MAX_VALUE)
            }
            if (stage > 0) {
                VarPlayerIntMapSetter.set(player, "varbit.barcrawl_stage", stage)
            }
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = quest.quest.getQuestStage(player)

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            if (count <= 0) return
            val slot = player.inv.indexOfFirst { it == null }
            player.inv[slot] = InvObj(obj, count)
        }

        fun take(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun sign(vararg bars: BarcrawlBar) {
            for (bar in bars) VarPlayerIntMapSetter.set(player, bar.varbit, 1)
        }

        fun signAll() = sign(*BarcrawlBar.entries.toTypedArray())

        fun at(coords: CoordGrid) {
            player.coords = coords
        }

        fun said(text: String): Boolean = output().contains(text)

        fun hasPanels(): Boolean =
            locRepo.findAll(UPPER_PANEL).any() && locRepo.findAll(LOWER_PANEL).any()

        fun talkToGuard() = talkTo("npc.barbguard1", GUARD_TILE)

        fun talkTo(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        fun guardOp3() {
            val npc = Npc("npc.barbguard1", GUARD_TILE)
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op3(npc))) }
        }

        fun serve(bar: BarcrawlBar) {
            val npc = Npc("npc.dragon_bartender", player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { startDialogue(npc) { with(quest) { serve(bar, "Server") } } }
        }

        fun readCard() {
            val type = checkNotNull(ServerCacheManager.getItem(CARD.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == CARD.asRSCM() }
            dispatch {
                val event = HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv)
                assertTrue(events.publish(this, event))
            }
        }

        fun gate(panel: CoordGrid) {
            val symbol = if (panel == UPPER_PANEL) "loc.barbariangatel" else "loc.barbariangater"
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val info =
                LocInfo(
                    LocLayerConstants.of(LocShape.WallStraight.id),
                    panel,
                    LocEntity(type.id, LocShape.WallStraight.id, LocAngle.East.id),
                )
            val loc = BoundLocInfo(info, type)
            dispatch { assertTrue(events.publish(this, LocEvents.Op1(loc, loc, type))) }
        }

        fun talkToGuardAndAbortAtVialOffer() {
            val npc = Npc("npc.barbguard1", GUARD_TILE)
            npcRepo.add(npc, Int.MAX_VALUE)
            begin { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
            repeat(200) {
                if (coroutine.isIdle) fail<Unit>("hand-in ended before the vial offer: ${output()}")
                if (output().contains("Do you want to do that?") && waitingOnMenu()) {
                    player.clearPendingAction(events)
                    return
                }
                step()
                result?.getOrThrow()
            }
            fail<Unit>("never reached the vial offer: ${output()}")
        }

        private fun waitingOnMenu(): Boolean =
            coroutine.isAwaiting(ResumePauseButtonInput::class) &&
                player.ui.containsModal("interface.chatmenu")

        private fun begin(block: suspend ProtectedAccess.() -> Unit) {
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
        }

        private fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            begin(block)
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
        val TOWN = CoordGrid(3000, 3000, 0)
        val UPPER_PANEL = CoordGrid(2545, 3570, 0)
        val LOWER_PANEL = CoordGrid(2545, 3569, 0)
        val WEST_OF_GATE = CoordGrid(2545, 3570, 0)
        val GUARD_TILE = CoordGrid(2544, 3569, 0)

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            val previous = QuestRequirements.activePolicy()
            restored += { QuestRequirements.install(previous) }
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
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
