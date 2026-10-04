package org.rsmod.content.quest.area.ardougne.monksfriend

import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The cache map holds the thieves' den and its way up (`loc.ladder_from_cellar` at 2561,9622) but
 * not the ladder in the stone circle above it, so it is spawned here. Climbing is handled by the
 * shared dungeon ladder script through the loc's content group.
 */
class ThievesDenLadder @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onGameStartup {
            locRepo.add(Coords, Ladder, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        }
    }

    companion object {
        val Coords = CoordGrid(2561, 3222, 0)
        const val Ladder = "loc.ladder_cellar"
    }
}
