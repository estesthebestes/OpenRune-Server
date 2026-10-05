package org.rsmod.content.quest.area.ardougne.fightarena

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * Where the arena fights take place. In OSRS the arena is a private copy per player; the quest
 * logic only ever talks to this interface so it can be exercised without a running instance
 * manager.
 */
interface ArenaSite {
    /** Fades the player into a fresh private arena. False if no arena could be made. */
    suspend fun enter(access: ProtectedAccess): Boolean

    /** Fades the player out of their arena, if they are in one, and on to [dest]. */
    suspend fun leave(access: ProtectedAccess, dest: CoordGrid)

    fun isInside(player: Player): Boolean

    /** Spawns [type] at the arena tile matching world tile [at]. Null outside an arena. */
    fun spawn(player: Player, type: String, at: CoordGrid, face: Direction? = null): Npc?

    fun remove(npc: Npc)

    /** Every npc spawned into the player's arena so far. */
    fun npcsOf(player: Player): List<Npc>

    /** Makes [npc] attack [player]. */
    fun engage(npc: Npc, player: Player)

    /** Whether [npc] stands in a player's private arena rather than in the open world. */
    fun owns(npc: Npc): Boolean
}

/**
 * Private copies of the fight arena's map square (40,49). Each player's fights happen in their
 * own copy, so opponents are never shared. The copy keeps the world's local layout; world tiles
 * are translated into it with [InstanceManager.resolveCoord].
 */
@Singleton
class ArenaInstance
@Inject
constructor(
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val aiInteractions: AiPlayerInteractions,
) : ArenaSite {

    private val doorType =
        ServerCacheManager.getObject(Door.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $Door")
    private val escapeDoorType =
        ServerCacheManager.getObject(EscapeDoor.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $EscapeDoor")

    override suspend fun enter(access: ProtectedAccess): Boolean {
        if (manager.sessionForPlayer(access.player) != null) {
            access.mes("You are already inside an instance.")
            return false
        }
        var finished = false
        try {
            access.ifCloseChat()
            access.fadeToBlack()
            val entered =
                try {
                    createAndEnter(access)
                } catch (e: Exception) {
                    logger.error(e) { "Fight arena entry failed for ${access.player.displayName}." }
                    false
                }
            if (entered) {
                // Let the client rebuild the instance before the overlay is lifted.
                access.delay(1)
            }
            access.fadeFromBlack()
            access.closeFadeOverlay()
            finished = true
            return entered
        } finally {
            if (!finished) {
                access.closeFadeOverlayNow()
            }
        }
    }

    override suspend fun leave(access: ProtectedAccess, dest: CoordGrid) {
        var finished = false
        try {
            access.ifCloseChat()
            access.fadeToBlack()
            manager.sessionForPlayer(access.player)?.takeIf { it.key == Key }?.let {
                manager.leave(access.player, it, access.mapClock)
            }
            access.telejump(dest, TeleportType.Exempt)
            access.delay(1)
            access.fadeFromBlack()
            access.closeFadeOverlay()
            finished = true
        } finally {
            if (!finished) {
                access.closeFadeOverlayNow()
            }
        }
    }

    override fun isInside(player: Player): Boolean = manager.sessionForPlayer(player)?.key == Key

    override fun spawn(player: Player, type: String, at: CoordGrid, face: Direction?): Npc? {
        val session = manager.sessionForPlayer(player)?.takeIf { it.key == Key } ?: return null
        val coords = manager.resolveCoord(session, at) ?: return null
        val npc = Npc(type, coords)
        npc.mode = NpcMode.None
        if (face != null) {
            npc.respawnDir = face
        }
        npcRepo.add(npc, Int.MAX_VALUE)
        manager.attachNpc(session.id, npc)
        if (face != null) {
            npc.lockFacingDirection(face)
        }
        return npc
    }

    override fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    override fun npcsOf(player: Player): List<Npc> {
        val session = manager.sessionForPlayer(player)?.takeIf { it.key == Key } ?: return emptyList()
        return manager.npcsForInstance(session.id)
    }

    override fun engage(npc: Npc, player: Player) {
        npc.clearFacingLock()
        npc.mode = npc.type.defaultMode
        npc.opPlayer2(player, aiInteractions)
    }

    override fun owns(npc: Npc): Boolean {
        val id = manager.instanceForNpc(npc) ?: return false
        return manager.sessionForId(id)?.key == Key
    }

    private fun createAndEnter(access: ProtectedAccess): Boolean {
        val enter = FightArenaPlaces.ArenaEntry
        val area =
            InstanceArea.copyRegions(
                regionIds = listOf(FightArenaPlaces.ArenaRegionId),
                level = 0,
                enterCoord = RegionLocal(0, enter.mx, enter.mz, enter.lx, enter.lz),
                exitCoord = FightArenaPlaces.ArenaExit,
            )
        val spec =
            InstanceSpec(
                fee = 0,
                maxPlayers = 1,
                reclaimTicks = ReclaimTicks,
                graceTicks = GraceTicks,
                destroyWhenEmpty = true,
                area = area,
                settingsRowId = -1,
                bossName = "General Khazard",
            )
        val created = manager.create(access.player, Key, spec, InstanceAccess.Private, access.mapClock)
        val session =
            when (created) {
                is InstanceManager.Result.Failed -> {
                    access.mes(created.reason)
                    return false
                }
                is InstanceManager.Result.Created -> created.session to created.enter
                is InstanceManager.Result.Joined -> created.session to created.enter
            }
        access.telejump(session.second, TeleportType.Exempt)
        if (access.player.coords != session.second) {
            access.mes("You can't enter the arena right now.")
            manager.leave(access.player, session.first, access.mapClock)
            return false
        }
        manager.finalizeEntry(access.player, session.first, access.mapClock)
        swapEscapeDoor(session.first)
        return true
    }

    private fun swapEscapeDoor(session: org.rsmod.api.instances.InstanceSession) {
        val at = manager.resolveCoord(session, FightArenaPlaces.ArenaDoor) ?: return
        val door = locRepo.findExact(at, doorType) ?: return
        locRepo.change(door, escapeDoorType, Int.MAX_VALUE)
    }

    companion object {
        const val Key = "fightarena"
        const val Door = "loc.fightarena_door2"
        const val EscapeDoor = "loc.fightarena_door2_escape"

        private val logger = InlineLogger()

        private const val ReclaimTicks = 100
        private const val GraceTicks = 50
    }
}

private const val FadeOutDuration = 15
private const val FadeInDuration = 50

internal suspend fun ProtectedAccess.fadeToBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 255,
        endColour = 0,
        endTransparency = 0,
        clientDuration = FadeOutDuration,
    )
    delay(3)
}

internal suspend fun ProtectedAccess.fadeFromBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 0,
        endColour = 0,
        endTransparency = 255,
        clientDuration = FadeInDuration,
    )
    delay(1)
}
