package org.rsmod.content.areas.city.grandexchange

import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiTimer
import org.rsmod.plugin.scripts.ScriptContext

private val AMBIENT_CYCLES = 250..350

internal fun ScriptContext.ambientChatter(npc: String, random: GameRandom, lines: List<String>) {
    onAiTimer(npc) {
        this.npc.say(random.pick(lines))
        this.npc.aiTimer(random.of(AMBIENT_CYCLES))
    }
}
