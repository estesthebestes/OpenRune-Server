package org.rsmod.content.quest.area.ardougne.monksfriend

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Cedric
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.ChildsBlanket
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Complete
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Monk
import org.rsmod.content.quest.area.ardougne.monksfriend.MonksFriendQuest.Companion.Omad

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MonksFriendCacheTest {

    @Test
    fun `the quest row matches the end stage and points`() {
        val row = QuestRow.getRow("dbrow.quest_monksfriend".asRSCM())
        assertEquals(Complete, row.endstate)
        assertEquals(1, row.questpoints)
        assertTrue(row.requirementQuests.isEmpty())
    }

    @Test
    fun `progress sits on the quest varp and the party expiry is a temporary server varp`() {
        val progress =
            checkNotNull(ServerCacheManager.getVarbit("varbit.monks_friend_progress".asRSCM()))
        assertEquals("varp.drunkmonkquest".asRSCM(RSCMType.VARP), progress.baseVar.id)
        assertEquals(0, progress.startBit)
        assertTrue((1 shl (progress.endBit - progress.startBit + 1)) > Complete)

        val varp = "varp.monks_friend_party_end".asRSCM(RSCMType.VARP)
        assertTrue(varp <= 0xFFFF)
        assertEquals(VarpLifetime.Temp, ServerCacheManager.getVarp(varp)!!.scope)
    }

    @Test
    fun `the monks talk on op one`() {
        for (name in listOf(Omad, Cedric, Monk)) {
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
            assertEquals("Talk-to", type.actions.getOpOrNull(0), name)
        }
    }

    @Test
    fun `the blanket lies in the den and the brothers stand where the quest expects`() {
        val npcs = rawSpawns("npcs")
        assertEquals(1, npcs.count { it.first == Omad })
        assertEquals(1, npcs.count { it.first == Cedric })
        assertTrue(npcs.count { it.first == Monk } >= 1)
        assertEquals(1, rawSpawns("objs").count { it.first == ChildsBlanket })
    }

    @Test
    fun `the den ladder climbs down through the shared dungeon ladder script`() {
        val ladder =
            checkNotNull(ServerCacheManager.getObject(ThievesDenLadder.Ladder.asRSCM(RSCMType.LOC)))
        assertEquals("content.dungeonladder_down".asRSCM(RSCMType.CONTENT), ladder.contentGroup)
        assertEquals("Climb-down", ladder.actions.getOpOrNull(0))
    }

    @Test
    fun `the thieves stand guard over the blanket`() {
        val npcs = rawSpawns("npcs")
        assertEquals(1, npcs.count { it.first == "npc.headthief_blanket" })
        assertTrue(npcs.count { it.first == "npc.thief_blanket" } >= 2)
    }

    private fun rawSpawns(kind: String): List<Pair<String, String>> {
        val dir =
            listOf("", "../../")
                .map { java.io.File("$it.data/raw-cache/map/$kind") }
                .first { it.isDirectory }
        val key = if (kind == "npcs") "npc" else "obj"
        val pattern = Regex("$key = \"($key[.][a-z0-9_]+)\"\\s*\\r?\\ncoords = \"([0-9_]+)\"")
        return dir.listFiles { f -> f.name.endsWith(".toml") }!!.flatMap { file ->
            pattern.findAll(file.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }
    }

    private companion object {
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
