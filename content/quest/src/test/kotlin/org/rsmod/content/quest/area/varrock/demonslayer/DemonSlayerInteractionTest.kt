package org.rsmod.content.quest.area.varrock.demonslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ObjectServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.Aris
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.CaptainRovin
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.SirPrysin
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.TraibornRitual
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.WizardTraiborn
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DemonSlayerInteractionTest {
    // Aris

    @Test fun `paying Aris, watching the vision and agreeing starts the quest`() {
        val f = Fixture()
        f.give(Coins)
        f.talk(ArisNpc)
        f.finish(listOf(1, 4, 5))
        assertEquals(1, f.stage())
        assertEquals(1, f.player.vars["varp.demonstart"] and 0b11111)
        assertEquals(0, f.player.inv.count(Coins))
        assertEquals(listOf(1, 2, 3, 4, 5), f.storedIncantation().sorted())
        assertTrue(f.output().contains("Die, foul demon!"), f.output())
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test fun `the incantation Aris repeats is the one that was stored`() {
        val f = Fixture()
        f.give(Coins)
        f.talk(ArisNpc)
        f.finish(listOf(1, 4, 3, 5))
        val spoken = f.script.incantationSpoken(f.player, ending = ".")
        assertTrue(f.output().replace("<br>", " ").contains(spoken), f.output())
    }

    @Test fun `Aris without a coin or after declining does not start the quest`() {
        val broke = Fixture()
        broke.talk(ArisNpc)
        broke.finish()
        assertEquals(0, broke.stage())
        assertTrue(broke.output().contains("I don't have any money."), broke.output())

        val declined = Fixture()
        declined.give(Coins)
        declined.talk(ArisNpc)
        declined.finish(listOf(2))
        assertEquals(0, declined.stage())
        assertEquals(1, declined.player.inv.count(Coins))
        assertEquals(0, declined.storedIncantation().sum())
    }

    @Test fun `leaving before agreeing keeps the quest unstarted`() {
        val f = Fixture()
        f.give(Coins)
        f.talk(ArisNpc)
        f.partially(80, listOf(1, 1))
        assertEquals(0, f.stage())
        assertEquals(0, f.player.inv.count(Coins))
    }

    @Test fun `Aris greets each stage differently`() {
        val expectations =
            listOf(
                Triple(1, false, "I'm still working on it."),
                Triple(2, false, "I found Sir Prysin."),
                Triple(2, true, "I have the sword now."),
                Triple(3, true, "You're a hero now."),
            )
        for ((stage, sword, line) in expectations) {
            val f = Fixture(stage)
            f.player.silverlightCaseEmpty = sword
            f.talk(ArisNpc)
            f.finish(listOf(if (stage == 1) 4 else 2))
            assertTrue(f.output().contains(line), "stage $stage: ${f.output()}")
        }
    }

    @Test fun `Aris repeats the incantation during the quest`() {
        val f = Fixture(2)
        f.script.rollIncantation(f.player)
        f.talk(ArisNpc)
        f.finish(listOf(1, 2))
        val spoken = f.script.incantationSpoken(f.player, ending = ".")
        assertTrue(f.output().replace("<br>", " ").contains(spoken), f.output())
    }

    // Sir Prysin

    @Test fun `Sir Prysin explains the keys and moves the quest on`() {
        val f = Fixture(1)
        f.talk(PrysinNpc)
        f.finish(listOf(3, 1, 1, 1, 1, 1, 3))
        assertEquals(2, f.stage())
        assertTrue(f.output().contains("special box"), f.output())
        assertTrue(f.output().contains("drain just outside the palace kitchen"), f.output())
    }

    @Test fun `introducing yourself to Sir Prysin never advances the quest`() {
        for (stage in listOf(0, 1)) {
            for (option in 1..2) {
                val f = Fixture(stage)
                f.talk(PrysinNpc)
                f.finish(listOf(option))
                assertEquals(stage, f.stage(), "stage $stage option $option")
            }
        }
    }

    @Test fun `with some keys Sir Prysin only reminds`() {
        val f = Fixture(2)
        f.give(KeyRovin, KeyDrain)
        f.talk(PrysinNpc)
        f.finish(listOf(2))
        assertEquals(0, f.player.inv.count(Silverlight))
        assertEquals(1, f.player.inv.count(KeyRovin))
        assertEquals(1, f.player.inv.count(KeyDrain))
        assertFalse(f.player.silverlightCaseEmpty)
        assertTrue(f.output().contains("the one that you dropped down the drain"), f.output())
    }

    @Test fun `three keys are swapped for Silverlight in one step`() {
        val f = Fixture(2)
        f.give(KeyRovin, KeyDrain, KeyTraiborn)
        f.talk(PrysinNpc)
        f.finish()
        for (key in listOf(KeyRovin, KeyDrain, KeyTraiborn)) {
            assertEquals(0, f.player.inv.count(key), key)
        }
        assertEquals(1, f.player.inv.count(Silverlight))
        assertTrue(f.player.silverlightCaseEmpty)
        assertEquals(2, f.stage())
        f.talk(PrysinNpc)
        f.finish()
        assertEquals(1, f.player.inv.count(Silverlight))
    }

    @Test fun `a full inventory still receives Silverlight for the keys`() {
        val f = Fixture(2)
        f.give(KeyRovin, KeyDrain, KeyTraiborn)
        for (slot in 3 until 28) f.player.inv[slot] = InvObj("obj.logs", 1)
        f.talk(PrysinNpc)
        f.finish()
        assertEquals(1, f.player.inv.count(Silverlight))
        assertEquals(0, f.player.inv.count(KeyRovin))
    }

    @Test fun `a lost Silverlight is returned for free during the quest`() {
        val f = Fixture(2)
        f.player.silverlightCaseEmpty = true
        f.talk(PrysinNpc)
        f.finish()
        assertEquals(1, f.player.inv.count(Silverlight))
        f.talk(PrysinNpc)
        f.finish()
        assertEquals(1, f.player.inv.count(Silverlight))
        assertTrue(f.output().contains("No, not yet."), f.output())
    }

    @Test fun `after the quest a replacement Silverlight costs 500 coins`() {
        val f = Fixture(3)
        f.player.inv[0] = InvObj(Coins, 600)
        f.talk(PrysinNpc)
        f.finish(listOf(2, 2, 2))
        assertEquals(1, f.player.inv.count(Silverlight))
        assertEquals(100, f.player.inv.count(Coins))
    }

    @Test fun `a replacement is refused without the coins and declined offers cost nothing`() {
        val poor = Fixture(3)
        poor.player.inv[0] = InvObj(Coins, 499)
        poor.talk(PrysinNpc)
        poor.finish(listOf(2, 2, 2))
        assertEquals(0, poor.player.inv.count(Silverlight))
        assertEquals(499, poor.player.inv.count(Coins))

        val declined = Fixture(3)
        declined.player.inv[0] = InvObj(Coins, 600)
        declined.talk(PrysinNpc)
        declined.finish(listOf(2, 2, 1))
        assertEquals(0, declined.player.inv.count(Silverlight))
        assertEquals(600, declined.player.inv.count(Coins))
    }

    @Test fun `no replacement is offered to someone who still has Silverlight`() {
        val f = Fixture(3)
        f.give(Silverlight)
        f.talk(PrysinNpc)
        f.finish(listOf(1))
        assertEquals(1, f.player.inv.count(Silverlight))
        assertFalse(f.output().contains("lost Silverlight"), f.output())
    }

    // Captain Rovin

    @Test fun `Captain Rovin gives his key to someone who makes the case`() {
        val f = Fixture(2)
        f.talk(RovinNpc)
        f.finish(listOf(3, 1, 2, 2, 3, 1))
        assertEquals(1, f.player.inv.count(KeyRovin))
        assertTrue(f.output().contains("Captain Rovin hands you a key."), f.output())
    }

    @Test fun `Captain Rovin refuses a weak case or an insult`() {
        val attempts = listOf(listOf(3, 1, 1), listOf(3, 1, 2, 1), listOf(1), listOf(2))
        for (options in attempts) {
            val f = Fixture(2)
            f.talk(RovinNpc)
            f.finish(options)
            assertEquals(0, f.player.inv.count(KeyRovin), "options $options")
        }
    }

    @Test fun `the wrong pleas loop back to the same question`() {
        val f = Fixture(2)
        f.talk(RovinNpc)
        f.finish(listOf(3, 1, 2, 2, 1, 2, 3, 1))
        assertEquals(1, f.player.inv.count(KeyRovin))
    }

    @Test fun `Captain Rovin points to the pockets or bank once the key was given`() {
        val carrying = Fixture(2)
        carrying.give(KeyRovin)
        carrying.talk(RovinNpc)
        carrying.finish(listOf(3, 1))
        assertEquals(1, carrying.player.inv.count(KeyRovin))
        assertTrue(carrying.output().contains("Check your pockets."), carrying.output())

        val banked = Fixture(2)
        banked.access().bank[0] = InvObj(KeyRovin, 1)
        banked.talk(RovinNpc)
        banked.finish(listOf(3, 1))
        assertEquals(0, banked.player.inv.count(KeyRovin))
        assertTrue(banked.output().contains("checked your bank account"), banked.output())
    }

    @Test fun `a destroyed key means repeating the whole conversation`() {
        val f = Fixture(2)
        f.talk(RovinNpc)
        f.finish(listOf(3, 1, 2, 2, 3, 1))
        f.player.inv[0] = null
        f.talk(RovinNpc)
        f.finish(listOf(3, 1, 2, 2, 3, 1))
        assertEquals(1, f.player.inv.count(KeyRovin))
    }

    @Test fun `Rovin offers the demon option only while the keys are being collected`() {
        for (stage in listOf(0, 1, 3)) {
            val f = Fixture(stage)
            f.talk(RovinNpc)
            f.finish(listOf(3, 1))
            assertEquals(0, f.player.inv.count(KeyRovin), "stage $stage")
        }
    }

    // Wizard Traiborn

    @Test fun `Traiborn falls through to his normal conversation outside the key hunt`() {
        for (stage in listOf(0, 1, 3)) {
            val f = Fixture(stage)
            f.talk(TraibornNpc)
            f.finish()
            assertTrue(f.output().contains("standard conversation"), "stage $stage")
        }
        val f = Fixture(2)
        f.talk(TraibornNpc)
        f.finish(listOf(2))
        assertTrue(f.output().contains("standard conversation"), f.output())
    }

    @Test fun `asking about the key records the ritual`() {
        val f = Fixture(2)
        f.talk(TraibornNpc)
        f.finish(listOf(1, 3, 2))
        assertTrue(f.player.traibornAsked)
        assertEquals(0, f.player.traibornBonesGiven)
        assertFalse(f.player.traibornKeyGiven)
    }

    @Test fun `the spinach roll and the long way round both reach the ritual`() {
        val roll = Fixture(2)
        roll.talk(TraibornNpc)
        roll.finish(listOf(1, 2, 1, 2, 2))
        assertEquals(1, roll.player.inv.count("obj.spinach_roll"))
        assertTrue(roll.player.traibornAsked)

        val long = Fixture(2)
        long.talk(TraibornNpc)
        long.finish(listOf(1, 2, 2, 1, 2, 2))
        assertEquals(0, long.player.inv.count("obj.spinach_roll"))
        assertTrue(long.player.traibornAsked)

        val leaving = Fixture(2)
        leaving.talk(TraibornNpc)
        leaving.finish(listOf(1, 1, 1, 1))
        assertFalse(leaving.player.traibornAsked)
    }

    @Test fun `bones are handed over in instalments and the key arrives with the last set`() {
        val f = Fixture(2)
        f.player.traibornAsked = true
        for (slot in 0 until 10) f.player.inv[slot] = InvObj(Bones, 1)
        f.talk(TraibornNpc)
        f.finish(listOf(1))
        assertEquals(10, f.player.traibornBonesGiven)
        assertEquals(0, f.player.inv.count(Bones))
        assertEquals(0, f.player.inv.count(KeyTraiborn))
        assertTrue(f.output().contains("I still need 15 more."), f.output())

        for (slot in 0 until 20) f.player.inv[slot] = InvObj(Bones, 1)
        f.talk(TraibornNpc)
        f.finish(listOf(1))
        assertEquals(25, f.player.traibornBonesGiven)
        assertEquals(5, f.player.inv.count(Bones))
        assertEquals(1, f.player.inv.count(KeyTraiborn))
        assertTrue(f.player.traibornKeyGiven)
        assertEquals(1, f.ritual.performed)
    }

    @Test fun `no bones means no progress and noted bones do not count`() {
        val f = Fixture(2)
        f.player.traibornAsked = true
        f.talk(TraibornNpc)
        f.finish(listOf(1))
        assertEquals(0, f.player.traibornBonesGiven)
        f.player.inv[0] = InvObj("obj.cert_bones", 10)
        f.talk(TraibornNpc)
        f.finish(listOf(1))
        assertEquals(0, f.player.traibornBonesGiven)
        assertEquals(10, f.player.inv.count("obj.cert_bones"))
    }

    @Test fun `with a full inventory the final bones still bring the key`() {
        val f = Fixture(2)
        f.player.traibornAsked = true
        f.player.traibornBonesGiven = 24
        for (slot in 0 until 28) f.player.inv[slot] = InvObj("obj.logs", 1)
        f.player.inv[0] = InvObj(Bones, 1)
        f.talk(TraibornNpc)
        f.finish(listOf(1))
        assertEquals(1, f.player.inv.count(KeyTraiborn))
        assertEquals(0, f.player.inv.count(Bones))
    }

    @Test fun `a lost key costs another twenty five bones`() {
        val f = Fixture(2)
        f.player.traibornAsked = true
        f.player.traibornKeyGiven = true
        f.player.traibornBonesGiven = 25
        f.talk(TraibornNpc)
        f.finish(listOf(1))
        assertFalse(f.player.traibornKeyGiven)
        assertEquals(0, f.player.traibornBonesGiven)

        val kept = Fixture(2)
        kept.player.traibornAsked = true
        kept.player.traibornKeyGiven = true
        kept.player.traibornBonesGiven = 25
        kept.give(KeyTraiborn)
        kept.talk(TraibornNpc)
        kept.finish(listOf(1))
        assertTrue(kept.player.traibornKeyGiven)
        assertEquals(25, kept.player.traibornBonesGiven)
    }

    // Drain

    @Test fun `searching the drain mentions the key only while it is needed`() {
        val f = Fixture(2)
        f.search(DrainKey)
        assertTrue(f.output().contains("That must be the key Sir Prysin dropped."), f.output())

        val early = Fixture(1)
        early.search(DrainKey)
        assertTrue(early.output().contains("Nothing interesting"), early.output())

        val done = Fixture(3)
        done.search(DrainNoKey)
        assertTrue(done.output().contains("Nothing interesting"), done.output())
    }

    @Test fun `water washes the key into the sewer and it can then be taken once`() {
        val f = Fixture(2)
        f.give("obj.bucket_water")
        f.useOnDrain(DrainKey, "obj.bucket_water")
        assertEquals(1, f.player.drainKeyState)
        assertEquals(0, f.player.inv.count("obj.bucket_water"))
        assertEquals(1, f.player.inv.count("obj.bucket_empty"))

        f.search(MudKey)
        assertEquals(2, f.player.drainKeyState)
        assertEquals(1, f.player.inv.count(KeyDrain))
        f.search(MudKey)
        assertEquals(1, f.player.inv.count(KeyDrain))
    }

    @Test fun `pouring water does nothing before the key hunt`() {
        val f = Fixture(1)
        f.give("obj.bucket_water")
        f.useOnDrain(DrainKey, "obj.bucket_water")
        assertEquals(0, f.player.drainKeyState)
        assertTrue(f.output().contains("You pour the liquid down the drain."), f.output())
    }

    @Test fun `a destroyed drain key can be washed down again`() {
        val f = Fixture(2)
        f.player.drainKeyState = 2
        f.give("obj.jug_water")
        f.useOnDrain(DrainNoKey, "obj.jug_water")
        assertEquals(1, f.player.drainKeyState)

        val keeping = Fixture(2)
        keeping.player.drainKeyState = 2
        keeping.give(KeyDrain, "obj.bowl_water")
        keeping.useOnDrain(DrainNoKey, "obj.bowl_water")
        assertEquals(2, keeping.player.drainKeyState)
    }

    @Test fun `the sewer key cannot be taken before it was washed down`() {
        val f = Fixture(2)
        f.search(MudKey)
        assertEquals(0, f.player.inv.count(KeyDrain))
        assertEquals(0, f.player.drainKeyState)
    }

    // Delrith

    @Test fun `the right incantation banishes Delrith and completes the quest once`() {
        val f = Fixture(2)
        f.player.silverlightCaseEmpty = true
        f.give(Silverlight)
        f.script.rollIncantation(f.player)
        val order = f.script.incantation(f.player).map { it + 1 }
        f.banish(order)
        assertEquals(3, f.stage())
        assertEquals(1, f.arena.left)
        assertEquals(0, f.arena.restored)
        assertEquals(3, f.player.vars["varp.qp"])
        assertEquals(1, f.player.inv.count(Silverlight))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.output().contains("Delrith is sucked into the vortex..."), f.output())
        f.banish(order)
        assertEquals(3, f.player.vars["varp.qp"])
    }

    @Test fun `a wrong incantation restores Delrith and leaves the quest unfinished`() {
        val f = Fixture(2)
        f.player.silverlightCaseEmpty = true
        f.script.rollIncantation(f.player)
        f.banish(f.wrongWords())
        assertEquals(2, f.stage())
        assertEquals(1, f.arena.restored)
        assertEquals(0, f.arena.left)
        assertEquals(0, f.player.vars["varp.qp"])
        assertTrue(f.output().contains("That was the wrong incantation."), f.output())
    }

    @Test fun `every shuffled incantation uses each word exactly once`() {
        val f = Fixture()
        repeat(50) {
            f.script.rollIncantation(f.player)
            assertEquals(listOf(0, 1, 2, 3, 4), f.script.incantation(f.player).sorted())
        }
    }

    @Test fun `a Delrith that slipped away during the prompt still finishes when correct`() {
        val f = Fixture(2)
        f.player.silverlightCaseEmpty = true
        f.script.rollIncantation(f.player)
        f.arena.gone = true
        f.banish(f.script.incantation(f.player).map { it + 1 })
        assertEquals(3, f.stage())

        val wrong = Fixture(2)
        wrong.script.rollIncantation(wrong.player)
        wrong.arena.gone = true
        wrong.banish(wrong.wrongWords())
        assertEquals(2, wrong.stage())
        assertEquals(0, wrong.arena.restored)
    }

    @Test fun `every gameval the quest binds resolves`() {
        val names =
            listOf(
                "area.demon_slayer_stone_circle",
                "dbrow.music_delrith_summoning",
                "dbrow.music_wally_cutscene",
                "loc.qip_ds_questdrain_key",
                "loc.qip_ds_questdrain_nokey",
                "loc.qip_ds_rustykey_mud",
                "loc.qip_ds_stone_table",
                "loc.qip_ds_wizards_key_wardrobe_magic",
                "loc.fai_varrock_posh_sink",
                "npc.aris",
                "npc.captain_rovin",
                "npc.delrith",
                "npc.delrith_weakened",
                "npc.qip_ds_dark_wizard_denath",
                "npc.qip_ds_wally",
                "npc.qip_ds_young_dark_wizard1",
                "npc.qip_ds_young_dark_wizard2",
                "npc.qip_ds_young_dark_wizard3",
                "npc.sir_prysin",
                "npc.sir_prysin_silverlight",
                "npc.traiborn",
                "obj.spinach_roll",
                "obj.cert_bones",
                "queue.demonslayer_delrith_timeout",
                "seq.human_pickupfloor",
                "seq.human_pickuptable",
                "seq.qip_ds_bones_wizard_anim",
                "seq.qip_ds_dark_wizard_chanting",
                "seq.qip_ds_delrith_banished",
                "seq.qip_ds_delrith_struck_down",
                "seq.qip_ds_delrith_summoned",
                "seq.qip_ds_presenting_silverlight_start",
                "seq.qip_ds_presenting_sword_end",
                "seq.qip_ds_presenting_sword_middle",
                "seq.qip_ds_reading_crystalball",
                "seq.qip_ds_recieving_silverlight",
                "seq.qip_ds_table_explosion",
                "seq.qip_ds_wally_cutscene",
                "seq.qip_ds_wardrobe_appear",
                "seq.qip_ds_wardrobe_disappear",
                "spotanim.qip_ds_bone_spotanim",
                "synth.aide_teleport_portal",
                "synth.bones_to_bananas_all",
                "synth.cleave",
                "synth.coins_jingle_1",
                "synth.crumble_hit",
                "synth.crystal_sing",
                "synth.curse_cast_and_fire",
                "synth.found_gem",
                "synth.pick2",
                "synth.pillory_unlock",
                "synth.spellfail",
                "synth.summon_npc",
                "synth.teleport_reverse",
                "synth.waterstrike_hit",
                "synth.weaken_all",
                "varbit.delrith_drain_key",
                "varbit.delrith_incantation_1",
                "varbit.delrith_incantation_5",
                "varbit.delrith_seen_summoning_cutscene",
                "varbit.delrith_silverlight_case",
                "varbit.demon_slayer_bones_given",
                "varbit.demon_slayer_traiborn_asked",
                "varbit.demon_slayer_traiborn_key_given",
                "varbit.demonslayer_main",
                "varp.demon_slayer_state",
                "varp.demonstart",
            )
        for (name in names) {
            assertTrue(name.asRSCM() >= 0, name)
        }
        assertNotNull(ServerCacheManager.getVarbit("varbit.demon_slayer_bones_given".asRSCM()))
    }

    @Test fun `the quest stage is held in the cache progress varbit`() {
        val f = Fixture(2)
        assertEquals(2, f.player.vars["varbit.demonslayer_main"])
        f.player.incantation1 = 3
        assertEquals(2, f.player.vars["varbit.demonslayer_main"])
        assertEquals(3, f.script.quest.maxSteps)
    }

    private class StubRitual : TraibornRitual {
        var performed = 0

        override suspend fun Dialogue.perform(npc: Npc) {
            performed++
        }
    }

    private class StubArena : DelrithBanishment.Arena {
        var gone = false
        var left = 0
        var restored = 0

        override fun present() = !gone

        override fun playBanishEffects(access: ProtectedAccess) {}

        override fun leave(access: ProtectedAccess) {
            left++
        }

        override fun restore(access: ProtectedAccess) {
            restored++
        }
    }

    private class StandardTraibornOwner(private val traiborn: WizardTraiborn) : PluginScript() {
        override fun ScriptContext.startup() {
            onOpNpc1("npc.traiborn") {
                startDialogue(it.npc) {
                    chatNpc(confused, "Ello young thingummywut.")
                    if (with(traiborn) { traibornDialogue() }) {
                        return@startDialogue
                    }
                    chatNpc(neutral, "This is the standard conversation.")
                }
            }
        }
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("demon-slayer-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 794L
                slotId = 1
                assignUid()
                coords = CoordGrid(3203, 3424, 0)
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

        val script = DemonSlayerQuest()
        val ritual = StubRitual()
        val arena = StubArena()
        private val banishment = DelrithBanishment(script)

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            val objs = ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap()))
            val vision =
                object : WallyVision {
                    override suspend fun ProtectedAccess.play(quest: DemonSlayerQuest) = false
                }
            with(script) { scripts.startup() }
            with(Aris(script, vision)) { scripts.startup() }
            with(SirPrysin(script, objs)) { scripts.startup() }
            with(CaptainRovin(script, objs)) { scripts.startup() }
            with(StandardTraibornOwner(WizardTraiborn(script, objs, ritual))) { scripts.startup() }
            with(DemonSlayerDrain(script)) { scripts.startup() }
            VarPlayerIntMapSetter.set(player, "varbit.demonslayer_main", stage)
        }

        fun stage() = script.quest.getQuestStage(player)

        fun access() = ProtectedAccess(player, coroutine, context)

        fun give(vararg objs: String) {
            var slot = 0
            for (obj in objs) {
                while (player.inv[slot] != null) slot++
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun storedIncantation() =
            listOf(
                player.incantation1,
                player.incantation2,
                player.incantation3,
                player.incantation4,
                player.incantation5,
            )

        fun wrongWords(): List<Int> {
            val right = script.incantation(player).map { it + 1 }
            return right.reversed().takeIf { it != right } ?: (right.drop(1) + right.first())
        }

        @OptIn(InternalApi::class)
        fun talk(npc: String) = start {
            val target = Npc(npc, player.coords.translateZ(1)).apply { slotId = 7 }
            assertTrue(events.publish(this, NpcEvents.Op1(target)))
        }

        /** Advances up to [count] steps and then walks away, as a player closing the dialogue. */
        fun partially(count: Int, options: List<Int>) {
            val selections = options.iterator()
            repeat(count) { if (!coroutine.isIdle) advance(selections) }
            player.clearPendingAction(events)
        }

        private fun loc(type: String): Pair<BoundLocInfo, ObjectServerType> {
            val locType = checkNotNull(ServerCacheManager.getObject(type.asRSCM()))
            val bound =
                BoundLocInfo(
                    LocInfo(2, CoordGrid(3225, 3496, 0), LocEntity(locType.id, 10, 0)),
                    locType,
                )
            return bound to locType
        }

        fun search(type: String) {
            val (loc, locType) = loc(type)
            start { assertTrue(events.publish(this, LocEvents.Op1(loc, loc, locType))) }
            finish()
        }

        fun useOnDrain(type: String, obj: String) {
            val (loc, locType) = loc(type)
            val item = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = (0 until 28).first { player.inv[it]?.id == item.id }
            start {
                assertTrue(events.publish(this, LocUEvents.Op(loc, loc, locType, item, slot)))
            }
            finish()
        }

        /** Speaks the 1-based [words] at the incantation prompt. */
        fun banish(words: List<Int>) {
            start { with(banishment) { banish(arena) } }
            finish(words)
        }

        private fun start(block: suspend ProtectedAccess.() -> Unit) {
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

        fun output() = client.messages.joinToString(" ").replace("<br>", " ")
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
        private const val ArisNpc = "npc.aris"
        private const val PrysinNpc = "npc.sir_prysin"
        private const val RovinNpc = "npc.captain_rovin"
        private const val TraibornNpc = "npc.traiborn"
        private const val Coins = "obj.coins"
        private const val Bones = "obj.bones"
        private const val Silverlight = "obj.silverlight"
        private const val KeyTraiborn = "obj.silverlight_key_1"
        private const val KeyRovin = "obj.silverlight_key_2"
        private const val KeyDrain = "obj.silverlight_key_3"
        private const val DrainKey = "loc.qip_ds_questdrain_key"
        private const val DrainNoKey = "loc.qip_ds_questdrain_nokey"
        private const val MudKey = "loc.qip_ds_rustykey_mud"
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
