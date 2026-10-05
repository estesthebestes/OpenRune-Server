package org.rsmod.content.quest.area.wilderness.entertheabyss

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.util.Wearpos
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.math.abs
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.shops.Shops
import org.rsmod.content.areas.wilderness.hasActiveSkull
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest
import org.rsmod.content.quest.area.ardougne.tribaltotem.WizardCromperty
import org.rsmod.content.quest.area.gnomestronghold.Brimstail
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.ABYSSAL_BOOK
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.EMPTY_ORB
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.FULL_ORB
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.SMALL_POUCH
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_READINGS_TAKEN
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_RESEARCHING
import org.rsmod.content.quest.area.wilderness.entertheabyss.EnterTheAbyssQuest.Companion.STAGE_SENT_TO_VARROCK
import org.rsmod.content.quest.area.yanille.WizardDistentor
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.runecrafting.altar.KourendAltar
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.content.skills.runecrafting.essence.EssencePortals
import org.rsmod.content.skills.runecrafting.essence.teleportToMainLand
import org.rsmod.content.skills.runecrafting.essence.teleportToRuneEssenceMine
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.ProtectedAccessLostException
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class EnterTheAbyssInteractionTest {

    @Test fun `the Wilderness mage only starts the miniquest after Rune Mysteries`() = respectingProgress {
        val f = Fixture(runeMysteries = false)
        f.talkWilderness(2)
        assertEquals(0, f.stage())
        assertTrue(f.said("You need to have completed Rune Mysteries"), f.output())
        f.completeRuneMysteries()
        f.talkWilderness(2)
        assertEquals(STAGE_SENT_TO_VARROCK, f.stage())
        assertEquals(STAGE_SENT_TO_VARROCK, f.player.vars["varp.abyssal_miniquest"])
    }

    @Test fun `accepting the deal hands over one empty orb and starts the research`() {
        val f = Fixture(stage = STAGE_SENT_TO_VARROCK)
        f.acceptDeal()
        assertEquals(STAGE_RESEARCHING, f.stage())
        assertEquals(1, f.count(EMPTY_ORB))
        assertTrue(f.player.etaOrbGiven)
        f.talkVarrock()
        assertEquals(1, f.count(EMPTY_ORB), "asking again does not hand out a second orb")
        assertTrue(f.said("still needs readings from 3 more locations"), f.output())
    }

    @Test fun `a full backpack gets no orb and no progress until there is room`() {
        val f = Fixture(stage = STAGE_SENT_TO_VARROCK)
        f.fill()
        f.acceptDeal()
        assertEquals(STAGE_SENT_TO_VARROCK, f.stage())
        assertEquals(0, f.count(EMPTY_ORB))
        assertTrue(f.said("you don't have enough room to take it"), f.output())
        f.player.inv[0] = null
        f.talkVarrock(1)
        assertTrue(f.said("Have you considered my offer?"), f.output())
        assertEquals(STAGE_RESEARCHING, f.stage())
        assertEquals(1, f.count(EMPTY_ORB))
    }

    @Test fun `declining or thinking it over keeps the offer open`() {
        val f = Fixture(stage = STAGE_SENT_TO_VARROCK)
        f.talkVarrock(1, 1, 2, 3)
        assertEquals(STAGE_SENT_TO_VARROCK, f.stage())
        assertTrue(f.player.etaReconsidering)
        f.talkVarrock(2)
        assertEquals(STAGE_SENT_TO_VARROCK, f.stage())
        f.talkVarrock(1)
        assertEquals(STAGE_RESEARCHING, f.stage())
    }

    @Test fun `every teleporter records its own reading`() {
        for (teleporter in EssenceMineTeleporter.entries) {
            val f = Fixture(stage = STAGE_RESEARCHING)
            f.give(EMPTY_ORB)
            f.teleport(teleporter)
            assertTrue(f.inMine(), teleporter.name)
            assertTrue(f.eta.hasReading(f.player, teleporter), teleporter.name)
            assertEquals(1, f.eta.readingCount(f.player), teleporter.name)
            assertEquals(teleporter.portal, f.player.vars[EssencePortals.PORTAL_VARBIT])
        }
    }

    @Test fun `any three distinct teleporters in any order fill the orb`() {
        val orders =
            listOf(
                listOf(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty),
                listOf(EssenceMineTeleporter.Distentor, EssenceMineTeleporter.Brimstail, EssenceMineTeleporter.Aubury),
                listOf(EssenceMineTeleporter.Cromperty, EssenceMineTeleporter.Distentor, EssenceMineTeleporter.Sedridor),
            )
        for (order in orders) {
            val f = Fixture(stage = STAGE_RESEARCHING)
            f.give(EMPTY_ORB)
            order.forEachIndexed { index, teleporter ->
                f.teleport(teleporter)
                val expected = if (index < 2) STAGE_RESEARCHING else STAGE_READINGS_TAKEN
                assertEquals(expected, f.stage(), "$order after $teleporter")
            }
            assertEquals(0, f.count(EMPTY_ORB))
            assertEquals(1, f.count(FULL_ORB))
            assertTrue(f.said("is now full"), f.output())
        }
    }

    @Test fun `repeating one teleporter never adds another reading`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        repeat(4) { f.teleport(EssenceMineTeleporter.Aubury) }
        assertEquals(1, f.eta.readingCount(f.player))
        assertEquals(STAGE_RESEARCHING, f.stage())
        assertTrue(f.said("already holds a reading of this teleport"), f.output())
    }

    @Test fun `teleports without the orb in the backpack record nothing`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.teleport(EssenceMineTeleporter.Aubury)
        assertEquals(0, f.eta.readingCount(f.player), "no orb at all")
        f.bank(EMPTY_ORB)
        f.teleport(EssenceMineTeleporter.Sedridor)
        assertEquals(0, f.eta.readingCount(f.player), "orb in the bank")
        assertTrue(f.inMine(), "the teleport itself still works")
    }

    @Test fun `teleports before accepting or after finishing record nothing`() {
        for (stage in listOf(0, STAGE_SENT_TO_VARROCK, STAGE_COMPLETE)) {
            val f = Fixture(stage = stage)
            f.give(EMPTY_ORB)
            f.teleport(EssenceMineTeleporter.Aubury)
            assertEquals(0, f.eta.readingCount(f.player), "stage $stage")
        }
    }

    @Test fun `a cancelled or blocked cast records nothing`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.startTeleport(EssenceMineTeleporter.Aubury)
        f.cancel()
        assertFalse(f.inMine())
        assertEquals(0, f.eta.readingCount(f.player))

        f.teleportDenial = "A teleport block has been cast on you."
        f.teleport(EssenceMineTeleporter.Aubury)
        assertFalse(f.inMine())
        assertEquals(0, f.eta.readingCount(f.player))
    }

    @Test fun `admin, generic and walking moves into the mine record nothing`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.dispatch { telejump(MINE_TILE, TeleportType.Exempt) }
        assertTrue(f.inMine())
        f.dispatch { telejump(MINE_TILE.translateX(1)) }
        f.player.coords = MINE_TILE.translateZ(1)
        assertEquals(0, f.eta.readingCount(f.player))
    }

    @Test fun `Distentor's reading needs no Magic level of its own`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.player.statMap.setBaseLevel("stat.magic", 1)
        f.player.statMap.setCurrentLevel("stat.magic", 1)
        f.teleport(EssenceMineTeleporter.Distentor)
        assertTrue(f.eta.hasReading(f.player, EssenceMineTeleporter.Distentor))
    }

    @Test fun `Brimstail's teleport op and dialogue both take a reading`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.npcOp("npc.gnome_brimstail", NpcEvents::Op3)
        assertTrue(f.eta.hasReading(f.player, EssenceMineTeleporter.Brimstail))
        val g = Fixture(stage = STAGE_RESEARCHING)
        g.give(EMPTY_ORB)
        g.talk("npc.gnome_brimstail", 1)
        assertTrue(g.eta.hasReading(g.player, EssenceMineTeleporter.Brimstail))
        assertTrue(g.said("Hold onto your hat!"), g.output())
    }

    @Test fun `Distentor's teleport op and dialogue both take a reading`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.npcOp("npc.guild_wizard", NpcEvents::Op3)
        assertTrue(f.eta.hasReading(f.player, EssenceMineTeleporter.Distentor))
        val g = Fixture(stage = STAGE_RESEARCHING)
        g.give(EMPTY_ORB)
        g.talk("npc.guild_wizard", 2)
        assertTrue(g.eta.hasReading(g.player, EssenceMineTeleporter.Distentor))
        assertEquals(EssenceMineTeleporter.Distentor.portal, g.player.vars[EssencePortals.PORTAL_VARBIT])
    }

    @Test fun `Cromperty keeps his Tribal Totem script and still takes a reading`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.npcOp("npc.cromperty_pre_diary", NpcEvents::Op3)
        assertTrue(f.eta.hasReading(f.player, EssenceMineTeleporter.Cromperty))
        val g = Fixture(stage = STAGE_RESEARCHING)
        g.give(EMPTY_ORB)
        g.talk("npc.cromperty_pre_diary", 3)
        assertTrue(g.eta.hasReading(g.player, EssenceMineTeleporter.Cromperty))
        val block = Fixture()
        block.talk("npc.cromperty_post_diary", 2, 2, 2)
        assertTrue(block.said("That sounds dangerous. Leave me here."), block.output())
        assertFalse(block.inMine())
    }

    @Test fun `the teleporter that sent the player is where the mine portal returns them`() {
        for (teleporter in EssenceMineTeleporter.entries) {
            val f = Fixture()
            f.teleport(teleporter)
            assertTrue(f.inMine(), teleporter.name)
            f.dispatch { teleportToMainLand() }
            val back = f.player.coords
            assertTrue(
                abs(back.x - teleporter.returnCoord.x) <= 1 && abs(back.z - teleporter.returnCoord.z) <= 1,
                "${teleporter.name} returns to ${teleporter.returnCoord}, was $back",
            )
        }
    }

    @Test fun `the Mage of Zamorak will not talk to anyone wearing Saradomin or Guthix gear`() {
        val sara = Fixture(stage = STAGE_SENT_TO_VARROCK)
        sara.player.worn[Wearpos.Back.slot] = InvObj("obj.saradomin_cape", 1)
        sara.talkWilderness()
        assertTrue(sara.said("I don't speak to Saradominist filth."), sara.output())
        sara.talkVarrock()
        assertTrue(sara.said("How dare you wear such disrespectful attire"), sara.output())
        assertEquals(STAGE_SENT_TO_VARROCK, sara.stage())
        val guthix = Fixture(stage = 0)
        guthix.player.worn[Wearpos.Back.slot] = InvObj("obj.guthix_cape", 1)
        guthix.talkWilderness()
        assertTrue(guthix.said("Pathetic Guthixian... Don't bother me."), guthix.output())
        assertEquals(0, guthix.stage(), "the offer is not made")
        val zamorak = Fixture(stage = 0)
        zamorak.player.worn[Wearpos.Back.slot] = InvObj("obj.zamorak_cape", 1)
        zamorak.talkWilderness(2)
        assertEquals(STAGE_SENT_TO_VARROCK, zamorak.stage())
    }

    @Test fun `a banked small pouch also stops a second one`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.bank(EMPTY_ORB)
        f.give(EMPTY_ORB)
        f.give(SMALL_POUCH)
        f.bank(SMALL_POUCH)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.talkVarrock(3)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(SMALL_POUCH))
    }

    @Test fun `a lost orb is replaced empty and keeps its readings`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.teleport(EssenceMineTeleporter.Aubury)
        f.teleport(EssenceMineTeleporter.Sedridor)
        f.drop(EMPTY_ORB)
        f.talkVarrock()
        assertTrue(f.said("I lost it. Could I have another?"), f.output())
        assertEquals(1, f.count(EMPTY_ORB))
        assertEquals(2, f.eta.readingCount(f.player))
        f.teleport(EssenceMineTeleporter.Cromperty)
        assertEquals(STAGE_READINGS_TAKEN, f.stage())
        assertEquals(1, f.count(FULL_ORB))
    }

    @Test fun `a lost full orb is replaced empty and one teleport refills it`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Brimstail)
        f.drop(FULL_ORB)
        f.talkVarrock()
        assertEquals(1, f.count(EMPTY_ORB))
        assertEquals(STAGE_READINGS_TAKEN, f.stage())
        f.talkVarrock()
        assertTrue(f.said("That orb is empty"), f.output())
        f.teleport(EssenceMineTeleporter.Aubury)
        assertEquals(1, f.count(FULL_ORB))
        assertEquals(3, f.eta.readingCount(f.player), "no fourth reading is taken")
    }

    @Test fun `a banked orb is neither replaced nor accepted`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.bank(FULL_ORB)
        f.talkVarrock()
        assertTrue(f.said("Yes, but it's in my bank."), f.output())
        assertEquals(STAGE_READINGS_TAKEN, f.stage())
        assertEquals(0, f.count(EMPTY_ORB))
    }

    @Test fun `readings survive a relog after one, two and three sources`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        val sources = listOf(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Distentor, EssenceMineTeleporter.Sedridor)
        sources.forEachIndexed { index, teleporter ->
            f.teleport(teleporter)
            f.relog()
            assertEquals(index + 1, f.eta.readingCount(f.player))
            assertTrue(f.eta.hasReading(f.player, teleporter))
        }
        assertEquals(STAGE_READINGS_TAKEN, f.stage())
        assertEquals(STAGE_READINGS_TAKEN, f.player.vars["varp.abyssal_miniquest"])
    }

    @Test fun `handing in the full orb pays out once with no quest points`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.talkVarrock(3)
        f.assertRewarded()
        assertTrue(f.said("The Mage of Zamorak hands you a book and a pouch."), f.output())
        f.talkVarrock(3)
        f.assertRewarded()
        assertTrue(f.said("Ah, you again. What do you want?"), f.output())
    }

    @Test fun `a rolled-back stage cannot pay out a second time`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.talkVarrock(3)
        f.jump(f.player, STAGE_READINGS_TAKEN)
        f.give(FULL_ORB)
        f.talkVarrock(3)
        f.assertRewarded()
        assertEquals(0, f.count(FULL_ORB))
    }

    @Test fun `interrupting the hand-in before the payout keeps the orb`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.start { f.events.publish(this, NpcEvents.Op1(Npc(VARROCK_MAGE, player.coords.translateX(1)))) }
        f.until { f.said("Just don't expect to be using any prayers in there.") }
        f.cancel()
        assertEquals(1, f.count(FULL_ORB))
        assertEquals(STAGE_READINGS_TAKEN, f.stage())
        assertEquals(0, f.player.statMap.getXP("stat.runecrafting"))
    }

    @Test fun `a full backpack still gets every reward`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.fill()
        f.talkVarrock(3)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.count(ABYSSAL_BOOK), "the book takes the orb's slot")
        assertEquals(0, f.count(SMALL_POUCH))
        assertEquals(1, f.ground(SMALL_POUCH), "the pouch drops at the player's feet")
    }

    @Test fun `a player who already owns a small pouch is not given another`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.give(SMALL_POUCH)
        VarPlayerIntMapSetter.set(f.player, "varbit.small_essence_pouch", 3)
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.talkVarrock(3)
        assertEquals(1, f.count(SMALL_POUCH))
        assertTrue(f.said("The Mage of Zamorak hands you a book."), f.output())
        assertEquals(3, f.player.vars["varbit.small_essence_pouch"], "stored essence is untouched")
    }

    @Test fun `a colossal pouch owner gets no small pouch`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.give("obj.rcu_pouch_colossal")
        f.collect(EssenceMineTeleporter.Aubury, EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Cromperty)
        f.talkVarrock(3)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(SMALL_POUCH))
        assertEquals(1, f.count(ABYSSAL_BOOK))
    }

    @Test fun `the Abyss teleport is refused until the miniquest is complete`() {
        for (stage in 0 until STAGE_COMPLETE) {
            val f = Fixture(stage = stage)
            f.npcOp(WILDERNESS_MAGE, NpcEvents::Op4)
            assertFalse(f.inAbyss(), "stage $stage")
        }
        val f = Fixture(stage = STAGE_COMPLETE)
        f.npcOp(WILDERNESS_MAGE, NpcEvents::Op4)
        assertTrue(f.inAbyss())
    }

    @Test fun `entering the Abyss drains prayer and skulls the player`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.setPrayer(43)
        f.talkWilderness(2)
        assertTrue(f.inAbyss())
        assertEquals(0, f.player.prayerLvl)
        assertTrue(f.player.hasActiveSkull())
    }

    @Test fun `a teleport block stops the Abyss teleport and its side effects`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.setPrayer(43)
        f.teleportDenial = "A teleport block has been cast on you."
        f.npcOp(WILDERNESS_MAGE, NpcEvents::Op4)
        assertFalse(f.inAbyss())
        assertEquals(43, f.player.prayerLvl)
        assertFalse(f.player.hasActiveSkull())
    }

    @Test fun `an abyssal bracelet spends a charge instead of the skull`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.wear(AbyssTeleport.BRACELETS[0])
        f.npcOp(WILDERNESS_MAGE, NpcEvents::Op4)
        assertFalse(f.player.hasActiveSkull())
        assertEquals(AbyssTeleport.BRACELETS[1].asRSCM(), f.player.worn[Wearpos.Hands.slot]?.id)

        val last = Fixture(stage = STAGE_COMPLETE)
        last.wear(AbyssTeleport.BRACELETS.last())
        last.npcOp(WILDERNESS_MAGE, NpcEvents::Op4)
        assertFalse(last.player.hasActiveSkull())
        assertNull(last.player.worn[Wearpos.Hands.slot])
        assertTrue(last.said("crumbles to dust"), last.output())
    }

    @Test fun `an already skulled player keeps the bracelet charge`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.wear(AbyssTeleport.BRACELETS[2])
        f.npcOp(WILDERNESS_MAGE, NpcEvents::Op4)
        assertFalse(f.player.hasActiveSkull())
        f.wear(AbyssTeleport.BRACELETS[2])
        f.dispatch { f.abyss.arrive(this) }
        f.player.worn[Wearpos.Hands.slot] = null
        f.dispatch { f.abyss.arrive(this) }
        assertTrue(f.player.hasActiveSkull())
        f.wear(AbyssTeleport.BRACELETS[2])
        f.dispatch { f.abyss.arrive(this) }
        assertEquals(AbyssTeleport.BRACELETS[2].asRSCM(), f.player.worn[Wearpos.Hands.slot]?.id)
        assertTrue(f.player.hasActiveSkull())
    }

    @Test fun `the Varrock mage cannot be used before the player is sent there`() {
        val f = Fixture(stage = 0)
        f.talkVarrock()
        assertEquals(0, f.stage())
        assertFalse(f.said("Ah, you again"), f.output())
    }

    @Test fun `the two mages agree on what to say at each stage`() {
        val sent = Fixture(stage = STAGE_SENT_TO_VARROCK)
        sent.talkWilderness(2)
        assertTrue(sent.said("I already told you to meet me"), sent.output())
        val done = Fixture(stage = STAGE_COMPLETE)
        done.talkWilderness(3)
        assertTrue(done.said("Unless you're here to teleport or buy something?"), done.output())
        assertFalse(done.inAbyss())
    }

    @Test fun `the journal tracks each source and the reading count`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.teleport(EssenceMineTeleporter.Aubury)
        val journal = f.eta.questLog(f.access())
        assertTrue(journal.contains("<str>Aubury - Varrock</str>"), journal)
        assertTrue(journal.contains("2 more places"), journal)
    }

    @Test fun `two players collect independent readings`() {
        val f = Fixture(stage = STAGE_RESEARCHING)
        f.give(EMPTY_ORB)
        f.give(EMPTY_ORB, f.second)
        f.teleport(EssenceMineTeleporter.Aubury)
        f.teleport(EssenceMineTeleporter.Sedridor, f.second)
        f.teleport(EssenceMineTeleporter.Brimstail, f.second)
        assertEquals(setOf(EssenceMineTeleporter.Aubury), f.readings(f.player))
        assertEquals(setOf(EssenceMineTeleporter.Sedridor, EssenceMineTeleporter.Brimstail), f.readings(f.second))
        assertNotEquals(f.stage(f.player), STAGE_READINGS_TAKEN)
    }

    @Test fun `arriving rolls a layout and lands in front of its blockage`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.npcOp(WILDERNESS_MAGE, NpcEvents::Op4)
        val layout = f.player.vars[AbyssGate.LAYOUT_VARBIT]
        assertTrue(layout in AbyssGate.LAYOUTS)
        assertEquals(AbyssGate.entries[layout].front, f.player.coords)
        assertNull(AbyssGate.entries[layout].obstacle(layout), "the blockage itself has no way through")
    }

    @Test fun `every obstacle passes at level 99 for 25 xp and restores the layout`() {
        for (obstacle in AbyssObstacle.entries - AbyssObstacle.Passage) {
            val f = Fixture(stage = STAGE_COMPLETE)
            val (layout, gate) = f.find(obstacle)
            val stat = checkNotNull(obstacle.stat)
            f.tools()
            f.level(stat, 99)
            f.attempt(gate, layout)
            assertEquals(gate.inside, f.player.coords, obstacle.name)
            assertEquals(25, f.player.statMap.getXP(stat), obstacle.name)
            assertEquals(layout, f.player.vars[AbyssGate.LAYOUT_VARBIT], obstacle.name)
            assertTrue(f.said(checkNotNull(obstacle.success)), f.output())
        }
    }

    @Test fun `obstacles needing a tool refuse without one`() {
        val missing =
            mapOf(
                AbyssObstacle.Rock to "You need a pickaxe",
                AbyssObstacle.Tendrils to "You need an axe",
                AbyssObstacle.Boil to "You need a tinderbox",
            )
        for ((obstacle, message) in missing) {
            val f = Fixture(stage = STAGE_COMPLETE)
            val (layout, gate) = f.find(obstacle)
            val stat = checkNotNull(obstacle.stat)
            f.level(stat, 99)
            f.attempt(gate, layout)
            assertEquals(gate.front, f.player.coords, obstacle.name)
            assertTrue(f.said(message), f.output())
            assertEquals(0, f.player.statMap.getXP(stat))
        }
    }

    @Test fun `a low-level attempt can fail and leaves the player outside`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        val (layout, gate) = f.find(AbyssObstacle.Eyes)
        f.level("stat.thieving", 1)
        f.attempt(gate, layout)
        assertEquals(gate.front, f.player.coords)
        assertTrue(f.said(checkNotNull(AbyssObstacle.Eyes.failure)), f.output())
        assertEquals(0, f.player.statMap.getXP("stat.thieving"))
        assertEquals(layout, f.player.vars[AbyssGate.LAYOUT_VARBIT])
    }

    @Test fun `the passage always lets the player through and the blockage never does`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        val (layout, gate) = f.find(AbyssObstacle.Passage)
        f.attempt(gate, layout)
        assertEquals(gate.inside, f.player.coords)
        val blockage = AbyssGate.entries[layout]
        f.attempt(blockage, layout)
        assertEquals(blockage.front, f.player.coords)
    }

    @Test fun `interrupting an obstacle mid-clear restores the layout`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        val (layout, gate) = f.find(AbyssObstacle.Rock)
        f.tools()
        f.level("stat.mining", 99)
        f.player.coords = gate.front
        VarPlayerIntMapSetter.set(f.player, AbyssGate.LAYOUT_VARBIT, layout)
        f.start { with(f.obstacles) { attempt(gate) } }
        f.until { f.player.vars[AbyssGate.LAYOUT_VARBIT] !in AbyssGate.LAYOUTS }
        f.cancel()
        assertEquals(layout, f.player.vars[AbyssGate.LAYOUT_VARBIT])
        assertEquals(gate.front, f.player.coords)
    }

    @Test fun `rifts open into their altars without a talisman`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        for (rift in listOf(AbyssRift.Air, AbyssRift.Nature, AbyssRift.Death)) {
            f.player.coords = AbyssGate.West.inside
            f.rift(rift)
            assertEquals(rift.entrance(), f.player.coords, rift.name)
        }
    }

    @Test fun `quest-locked rifts refuse players who have not done the quest`() = respectingProgress {
        val f = Fixture(stage = STAGE_COMPLETE)
        for (rift in listOf(AbyssRift.Cosmic, AbyssRift.Law, AbyssRift.Death)) {
            f.player.coords = AbyssGate.West.inside
            f.rift(rift)
            assertEquals(AbyssGate.West.inside, f.player.coords, rift.name)
        }
        assertTrue(f.said("You need to have completed"), f.output())
    }

    @Test fun `the law rift keeps Entrana's ban on weapons and armour`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.player.coords = AbyssGate.West.inside
        f.player.worn[Wearpos.Torso.slot] = InvObj("obj.rune_platebody", 1)
        f.rift(AbyssRift.Law)
        assertEquals(AbyssGate.West.inside, f.player.coords)
        assertTrue(f.said("You cannot take weapons or armour through the law rift."), f.output())
        f.player.worn[Wearpos.Torso.slot] = null
        f.rift(AbyssRift.Law)
        assertEquals(AbyssRift.Law.entrance(), f.player.coords)
    }

    @Test fun `the blood rift reaches the true Blood Altar after Sins of the Father`() {
        respectingProgress {
            val f = Fixture(stage = STAGE_COMPLETE)
            f.player.coords = AbyssGate.West.inside
            f.bloodRift(repeatLast = true)
            assertEquals(AbyssGate.West.inside, f.player.coords)
            assertTrue(f.said("You need to have completed Sins of the Father"), f.output())
        }
        val f = Fixture(stage = STAGE_COMPLETE)
        f.player.coords = AbyssGate.West.inside
        f.bloodRift(repeatLast = true)
        assertEquals(AbyssRift.Blood.entrance(), f.player.coords)
        assertEquals(AbyssRifts.TRUE_ALTAR, f.player.vars[AbyssRifts.LAST_BLOOD_RIFT])
    }

    @Test fun `Kourend's blood altar unlocks by crafting there then using dark essence on the rift`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.player.coords = AbyssGate.West.inside
        f.bloodRift(repeatLast = false)
        assertTrue(f.said("You have not yet unlocked this rift."), f.output())
        f.give("obj.bigblankrune")
        f.useOnRift(KourendAltar.Blood, "obj.bigblankrune")
        assertTrue(f.said("The blood rift will not respond to you until you have crafted some blood runes."), f.output())
        KourendAltar.Blood.markCrafted(f.player)
        f.bloodRift(repeatLast = false)
        assertEquals(AbyssGate.West.inside, f.player.coords)
        assertTrue(f.said("The rift cannot find the true Blood Altar."), f.output())
        f.useOnRift(KourendAltar.Blood, "obj.bigblankrune")
        assertTrue(KourendAltar.Blood.riftRedirected(f.player))
        assertEquals(1, f.count("obj.bigblankrune"), "the dark essence is not used up")
        assertTrue(f.said("The power of the Dark Altar redirects the rift"), f.output())
        f.bloodRift(repeatLast = false)
        assertEquals(AbyssRifts.KOUREND_BLOOD_LANDING, f.player.coords)
        assertEquals(AbyssRifts.KOUREND, f.player.vars[AbyssRifts.LAST_BLOOD_RIFT])
        f.player.coords = AbyssGate.West.inside
        f.bloodRift(repeatLast = true)
        assertEquals(AbyssRifts.KOUREND_BLOOD_LANDING, f.player.coords, "op1 now repeats Kourend")
        f.player.coords = AbyssGate.West.inside
        f.bloodRift(repeatLast = false)
        assertEquals(AbyssRift.Blood.entrance(), f.player.coords, "op2 now leads to the true altar")
        assertEquals(AbyssRifts.TRUE_ALTAR, f.player.vars[AbyssRifts.LAST_BLOOD_RIFT])
    }

    @Test fun `the soul rift follows the same unlock and ignores other items`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.player.coords = AbyssGate.West.inside
        f.soulRift()
        assertTrue(f.said("You have not yet unlocked this rift."), f.output())
        f.give("obj.arceuus_essence_block_dark")
        f.give("obj.bronze_axe")
        f.useOnRift(KourendAltar.Soul, "obj.bronze_axe")
        assertTrue(f.said("The rift does not respond to that."), f.output())
        KourendAltar.Soul.markCrafted(f.player)
        f.useOnRift(KourendAltar.Soul, "obj.arceuus_essence_block_dark")
        f.useOnRift(KourendAltar.Soul, "obj.arceuus_essence_block_dark")
        assertTrue(f.said("The rift is already connected"), f.output())
        assertEquals(1, f.count("obj.arceuus_essence_block_dark"))
        f.soulRift()
        assertEquals(AbyssRifts.KOUREND_SOUL_LANDING, f.player.coords)
        assertFalse(KourendAltar.Blood.riftRedirected(f.player), "each rift unlocks separately")
    }

    @Test fun `the Nexus tunnel moves the player from the outer ring into the Nexus`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.player.coords = CoordGrid(3039, 4805)
        f.dispatch { with(f.nexus) { enterNexus() } }
        assertEquals(AbyssNexusPassage.NEXUS_LANDING, f.player.coords)
    }

    @Test fun `the appendage teleports the player out of the Nexus to Lumbridge`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.player.coords = AbyssNexusPassage.NEXUS_LANDING
        f.dispatch { with(f.appendage) { teleportOut() } }
        assertEquals(AbyssNexusAppendage.LUMBRIDGE, f.player.coords)
        assertTrue(f.said("...and are teleported away."), f.output())
    }

    private class Fixture(stage: Int = 0, runeMysteries: Boolean = true) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("enter-the-abyss-test")
        private var result: Result<Unit>? = null
        var teleportDenial: String? = null
        private val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> teleportDenial }))
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getTeleportValidator = { validator },
            getAreaChecker = { unused<AreaChecker>() },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { DefaultGameRandom(Random(5)) },
        )
        private val clock = MapClock(100)
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        private val objRepo = ObjRepository(clock, objRegistry)
        private val npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
        private val picks = ArrayDeque<Int>()

        var player = newPlayer(7001L, 1)
        val second = newPlayer(7002L, 2)
        private var active = player

        val eta = EnterTheAbyssQuest()
        val runeMysteriesQuest = RuneMysteriesQuest()
        val abyss = AbyssTeleport()
        val obstacles = AbyssObstacles()
        val rifts = AbyssRifts()
        val nexus = AbyssNexusPassage()
        val appendage = AbyssNexusAppendage(unused())

        init {
            val altars = (AbyssRift.entries.mapNotNull { it.entrance() } + AbyssRifts.KOUREND_BLOOD_LANDING + AbyssRifts.KOUREND_SOUL_LANDING + EssenceMineTeleporter.entries.map { it.returnCoord })
                .map { (it.x / 64) * 64 to (it.z / 64) * 64 }
            for ((x0, z0) in SQUARES + altars) {
                for (level in 0..1) for (x in x0 until x0 + 64 step 8) for (z in z0 until z0 + 64 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                eta, runeMysteriesQuest,
                MageOfZamorakVarrock(eta, objRepo),
                MageOfZamorakWilderness(eta, abyss, Shops(events)),
                ScryingOrbReadings(eta),
                Brimstail(runeMysteriesQuest),
                WizardDistentor(runeMysteriesQuest),
                WizardCromperty(TribalTotemQuest()),
                obstacles, rifts, nexus, appendage,
            )) {
                with(script) { scripts.startup() }
            }
            for (p in listOf(player, second)) {
                if (runeMysteries) completeRuneMysteries(p)
                if (stage > 0) jump(p, stage)
            }
        }

        @OptIn(InternalApi::class)
        private fun newPlayer(id: Long, slot: Int) = Player().apply {
            this.client = this@Fixture.client
            uuid = id
            observerUUID = id
            slotId = slot
            assignUid()
            coords = START
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for ((stat, level) in listOf("stat.hitpoints" to 50, "stat.prayer" to 43, "stat.magic" to 70)) {
                statMap.setBaseLevel(stat, level.toByte())
                statMap.setCurrentLevel(stat, level.toByte())
            }
        }

        fun access(p: Player = active) = ProtectedAccess(p, coroutine, context)

        fun stage(p: Player = player): Int = eta.stage(p)

        fun jump(p: Player, stage: Int) =
            VarPlayerIntMapSetter.set(p, EnterTheAbyssQuest.PROGRESS_VARBIT, stage)

        fun completeRuneMysteries(p: Player = player) {
            p.attr.getOrPut(QUEST_STAGE_MAP_ATTR) { mutableMapOf() }["quest_runemysteries"] =
                RuneMysteriesQuest.STAGE_COMPLETE
            VarPlayerIntMapSetter.set(p, "varp.runemysteries", RuneMysteriesQuest.STAGE_COMPLETE)
        }

        fun readings(p: Player): Set<EssenceMineTeleporter> =
            EssenceMineTeleporter.entries.filter { eta.hasReading(p, it) }.toSet()

        fun inMine(p: Player = player): Boolean = p.coords.x in 2880..2943 && p.coords.z in 4800..4863

        fun inAbyss(p: Player = player): Boolean = p.coords.x in 3008..3071 && p.coords.z in 4800..4863

        fun give(obj: String, p: Player = player) {
            p.inv[p.inv.indexOfFirst { it == null }] = InvObj(obj, 1)
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot >= 0) player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun wear(obj: String) {
            player.worn[Wearpos.Hands.slot] = InvObj(obj, 1)
        }

        fun setPrayer(level: Int) {
            player.statMap.setCurrentLevel("stat.prayer", level.toByte())
        }

        private val bankInv by lazy { access().bank }

        fun bank(obj: String) {
            drop(obj)
            bankInv[bankInv.indexOfFirst { it == null }] = InvObj(obj, 1)
        }

        fun ground(obj: String): Int = objRegistry.findAll(player.coords).count { it.type == obj.asRSCM() }

        fun relog() {
            val loaded = newPlayer(player.uuid ?: 0L, 3)
            for ((varp, value) in player.vars.backing) {
                if (ServerCacheManager.getVarp(varp)?.scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            for (slot in player.inv.indices) loaded.inv[slot] = player.inv[slot]
            loaded.coords = player.coords
            player = loaded
            active = loaded
        }

        fun find(obstacle: AbyssObstacle): Pair<Int, AbyssGate> =
            AbyssGate.LAYOUTS.firstNotNullOf { layout ->
                AbyssGate.entries.firstOrNull { it.obstacle(layout) == obstacle }?.let { layout to it }
            }

        fun tools() {
            for (tool in listOf("obj.bronze_pickaxe", "obj.bronze_axe", "obj.tinderbox")) give(tool)
        }

        fun level(stat: String, level: Int) {
            player.statMap.setBaseLevel(stat, level.toByte())
            player.statMap.setCurrentLevel(stat, level.toByte())
        }

        fun attempt(gate: AbyssGate, layout: Int) {
            player.coords = gate.front
            VarPlayerIntMapSetter.set(player, AbyssGate.LAYOUT_VARBIT, layout)
            dispatch { with(obstacles) { attempt(gate) } }
        }

        fun rift(rift: AbyssRift) = dispatch { with(rifts) { enter(rift) } }

        fun bloodRift(repeatLast: Boolean) = dispatch { with(rifts) { enterBloodRift(repeatLast) } }

        fun soulRift() = dispatch { with(rifts) { enterKourend(KourendAltar.Soul) } }

        fun useOnRift(altar: KourendAltar, obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch { with(rifts) { useOnRift(altar, type) } }
        }

        fun collect(vararg teleporters: EssenceMineTeleporter) {
            for (teleporter in teleporters) teleport(teleporter)
        }

        fun teleport(teleporter: EssenceMineTeleporter, p: Player = player) {
            p.coords = START
            val npc = Npc(TELEPORTERS.getValue(teleporter), p.coords.translateX(1))
            withActive(p) { dispatch { teleportToRuneEssenceMine(npc, teleporter) } }
        }

        fun startTeleport(teleporter: EssenceMineTeleporter) {
            val npc = Npc(TELEPORTERS.getValue(teleporter), player.coords.translateX(1))
            start { teleportToRuneEssenceMine(npc, teleporter) }
            step(player)
        }

        fun acceptDeal() = talkVarrock(1, 1, 2, 1)

        fun talkVarrock(vararg options: Int) = talk(VARROCK_MAGE, *options)

        fun talkWilderness(vararg options: Int) = talk(WILDERNESS_MAGE, *options)

        fun talk(type: String, vararg options: Int) {
            picks += options.toList()
            npcOp(type, NpcEvents::Op1)
        }

        fun npcOp(type: String, op: (Npc) -> SuspendEvent<ProtectedAccess>) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { events.publish(this, op(npc)) }
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        private fun settle(p: Player) {
            while (p.isDelayed) {
                p.currentMapClock++
                p.processedMapClock = p.currentMapClock
            }
        }

        fun start(block: suspend ProtectedAccess.() -> Unit) {
            settle(player)
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val access = access(player)
            val begin: suspend () -> Unit = { access.block() }
            begin.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        fun until(predicate: () -> Boolean) {
            repeat(300) {
                if (predicate()) return
                step(player)
                result?.getOrThrow()
            }
            fail<Unit>("Condition was not reached: ${output()}")
        }

        fun cancel() {
            coroutine.cancel()
            val error = result?.exceptionOrNull()
            assertTrue(error is CancellationException || error is ProtectedAccessLostException, "$error")
            result = null
            player.activeCoroutine = null
        }

        private fun withActive(p: Player, block: () -> Unit) {
            active = p
            try {
                block()
            } finally {
                active = player
            }
        }

        fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            val p = active
            settle(p)
            p.clearPendingAction(events)
            result = null
            p.activeCoroutine = coroutine
            val access = access(p)
            val begin: suspend () -> Unit = { access.block() }
            begin.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
            repeat(600) {
                if (coroutine.isIdle) {
                    picks.clear()
                    return
                }
                step(p)
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun step(p: Player) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent = listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox", "objectbox_double")
                    .firstOrNull { p.ui.containsModal("interface.$it") }
                    ?: error("Unknown dialogue: ${output()}")
                val input = when (parent) {
                    "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", picks.removeFirstOrNull() ?: 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    "objectbox_double" -> ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                    else -> ResumePauseButtonInput("component.$parent:continue", -1)
                }
                coroutine.resumeWith(input)
            } else {
                p.currentMapClock++
                p.processedMapClock = p.currentMapClock
                p.pendingSequence = EntitySeq.NULL
                coroutine.advance()
            }
        }

        fun said(text: String): Boolean = output().contains(text)

        fun output() = client.messages.joinToString("\n").replace("<br>", " ")

        fun assertRewarded() {
            assertEquals(STAGE_COMPLETE, stage())
            assertEquals(STAGE_COMPLETE, player.vars["varp.abyssal_miniquest"])
            assertEquals(1000, player.statMap.getXP("stat.runecrafting"))
            assertEquals(0, player.vars["varp.qp"])
            assertEquals(1, count(ABYSSAL_BOOK))
            assertEquals(1, count(SMALL_POUCH))
            assertEquals(0, count(FULL_ORB))
            assertTrue(player.etaRewarded)
        }
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
        const val VARROCK_MAGE = "npc.rcu_zammy_mage1_edge"
        const val WILDERNESS_MAGE = "npc.rcu_zammy_mage1"
        val START = CoordGrid(3260, 3384, 0)
        val MINE_TILE = CoordGrid(2912, 4833, 0)

        val TELEPORTERS =
            mapOf(
                EssenceMineTeleporter.Sedridor to "npc.head_wizard",
                EssenceMineTeleporter.Aubury to "npc.aubury",
                EssenceMineTeleporter.Cromperty to "npc.cromperty_pre_diary",
                EssenceMineTeleporter.Brimstail to "npc.gnome_brimstail",
                EssenceMineTeleporter.Distentor to "npc.guild_wizard",
            )

        /** Varrock, the essence mine and the Abyss. */
        val SQUARES = listOf(3200 to 3200, 3200 to 3328, 2880 to 4800, 3008 to 4800, 3008 to 4736)

        private fun respectingProgress(block: () -> Unit) {
            val previous = QuestRequirements.activePolicy()
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
            try {
                block()
            } finally {
                QuestRequirements.install(previous)
            }
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
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
        }

        @JvmStatic @AfterAll fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
