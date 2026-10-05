package org.rsmod.content.quest.area.wilderness.entertheabyss

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The appendage on the north-west wall of the Abyssal Nexus's central room. It works like a lever,
 * dropping briefly into its down form while the player is teleported to Lumbridge; it is the
 * Nexus's only exit for players who arrived through the Abyss tunnel.
 */
class AbyssNexusAppendage @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(APPENDAGE) { pull(it.loc) }
    }

    internal suspend fun ProtectedAccess.pull(appendage: BoundLocInfo) {
        arriveDelay()
        faceLoc(appendage)
        anim(PULL_ANIM)
        locRepo.change(appendage, APPENDAGE_DOWN, DOWN_TICKS)
        soundSynth(LEVER_SOUND)
        mes("You pull the appendage...")
        delay(PULL_TICKS)
        teleportOut()
    }

    internal suspend fun ProtectedAccess.teleportOut() {
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_GFX_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_TICKS)
        telejump(LUMBRIDGE, TeleportType.Exempt)
        resetAnim()
        mes("...and are teleported away.")
    }

    internal companion object {
        const val APPENDAGE = "loc.abyssalsire_exit_lever"
        const val APPENDAGE_DOWN = "loc.abyssalsire_exit_lever_inactive"
        val LUMBRIDGE = CoordGrid(3222, 3218)

        private const val PULL_ANIM = "seq.human_pull_lever"
        private const val LEVER_SOUND = "synth.lever"
        private const val PULL_TICKS = 2
        private const val DOWN_TICKS = 5
        private const val TELEPORT_ANIM = "seq.human_castteleport"
        private const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        private const val TELEPORT_GFX_HEIGHT = 92
        private const val TELEPORT_SOUND = "synth.teleport_all"
        private const val TELEPORT_TICKS = 2
    }
}
