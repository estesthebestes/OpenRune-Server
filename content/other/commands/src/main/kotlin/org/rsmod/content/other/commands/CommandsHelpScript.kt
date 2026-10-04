package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.cheat.CheatHandler
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CommandsHelpScript @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private lateinit var commands: CheatCommandMap

    override fun ScriptContext.startup() {
        commands = cheatCommandMap
        onCommand("commands") {
            desc = "List the commands you can use: ::commands [search]"
            cheat {
                val query = args.joinToString(" ").trim()
                val entries = entries(player, query)
                if (entries.isEmpty()) {
                    player.mes("No commands match '$query'.")
                    return@cheat
                }
                protectedAccess.launch(player, busyText = BusyText) { show(query, entries) }
            }
        }
    }

    private fun entries(player: Player, query: String): List<Entry> =
        commands.commands.entries
            .filter { (_, handler) -> handler.canUse(player) }
            .groupBy({ it.value.key() }, { it.key })
            .map { (key, names) -> Entry(names.sortedBy { it.length }, key.desc, key.usage) }
            .filter { query.isEmpty() || it.matches(query) }
            .sortedBy { it.names.first() }

    private fun ProtectedAccess.show(query: String, entries: List<Entry>) {
        val lines = entries.flatMap { it.lines() }.take(MaxLines)
        ifOpenMain(Journal)
        runClientScript(JournalInit)
        val title = if (query.isEmpty()) "Commands" else "Commands: $query"
        ifSetText("component.questjournal:title", "<col=7f0000>$title</col>")
        for (line in 1..MaxLines) {
            ifSetText("component.questjournal:qj$line", lines.getOrElse(line - 1) { "" })
        }
    }

    private fun CheatHandler.key() = Key(desc.orEmpty(), usage?.takeUnless { it == DefaultUsage })

    private data class Key(val desc: String, val usage: String?)

    private data class Entry(val names: List<String>, val desc: String, val usage: String?) {
        fun matches(query: String): Boolean =
            names.any { it.contains(query, ignoreCase = true) } ||
                desc.contains(query, ignoreCase = true)

        fun lines(): List<String> {
            val header = names.joinToString(" / ") { "<col=ff981f>::$it</col>" }
            val body = wrap(desc).map { "  $it" }
            val usage = usage?.let { text -> wrap(text).map { "  <col=7f7f7f>$it</col>" } }
            return listOf(header) + body + usage.orEmpty()
        }
    }

    private companion object {
        private const val Journal = "interface.questjournal"
        private const val JournalInit = 5240
        private const val MaxLines = 210
        private const val WrapWidth = 60
        private const val DefaultUsage = "Invalid arguments!"
        private const val BusyText = "You can't look at the command list right now."

        private fun wrap(text: String): List<String> {
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in text.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > WrapWidth) {
                    lines += line.toString()
                    line = StringBuilder()
                }
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
            if (line.isNotEmpty()) lines += line.toString()
            return lines
        }
    }
}
