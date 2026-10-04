package org.rsmod.api.hotreload

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.PlayerList

public fun interface ReloadReporter {
    public fun report(requesters: Set<ReloadRequester>, success: Boolean, lines: List<String>)
}

public class PlayerReloadReporter @Inject constructor(private val playerList: PlayerList) :
    ReloadReporter {
    private val logger = InlineLogger()

    override fun report(requesters: Set<ReloadRequester>, success: Boolean, lines: List<String>) {
        for (line in lines) {
            if (success) logger.info { line } else logger.warn { line }
        }
        val chatLines = lines.take(MAX_CHAT_LINES).map { it.take(MAX_CHAT_LINE_LENGTH) }
        for (requester in requesters) {
            if (requester !is ReloadRequester.Admin) {
                continue
            }
            val player = requester.uid.resolve(playerList) ?: continue
            for (line in chatLines) {
                player.mes(line)
            }
            if (lines.size > MAX_CHAT_LINES) {
                player.mes("(${lines.size - MAX_CHAT_LINES} more lines in the server log)")
            }
        }
    }

    private companion object {
        private const val MAX_CHAT_LINES = 6
        private const val MAX_CHAT_LINE_LENGTH = 120
    }
}
