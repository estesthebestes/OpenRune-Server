package org.rsmod.content.quest.area.coaltrucks.dwarfcannon

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.MANUAL
import org.rsmod.content.quest.area.coaltrucks.dwarfcannon.DwarfCannonQuest.Companion.NOTES
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Nulodion's notes on cannon ammo and the Dwarf Multicannon instruction manual. */
class DwarfCannonBooks : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(NOTES) { read(NOTES_TITLE, NOTES_CHAPTERS) }
        onOpHeld1(MANUAL) { read(MANUAL_TITLE, MANUAL_CHAPTERS) }
    }

    /**
     * The book's page arrows are pause buttons. The server queues the book to close when one is
     * pressed, and the client ignores further presses until the interface is sent again, so every
     * turn re-opens the book at the new spread.
     */
    private suspend fun ProtectedAccess.read(title: String, chapters: List<Pair<String, List<String>>>) {
        val spreads = layout(chapters)
        var spread = 0
        openSpread(title, spreads, spread)
        while (true) {
            val input = pauseButton()
            val turned =
                when (input.component) {
                    PAGE_LEFT -> spread - 1
                    PAGE_RIGHT -> spread + 1
                    else -> spread
                }
            if (turned in spreads.indices) {
                spread = turned
            }
            openSpread(title, spreads, spread)
        }
    }

    private fun ProtectedAccess.openSpread(title: String, spreads: List<Pair<List<String>, List<String>>>, spread: Int) {
        ifOpenMainModal(BOOK_INTERFACE)
        player.runClientScript(
            BOOK_INIT_SCRIPT,
            RSCM.getRSCM("component.book:close_button"),
            RSCM.getRSCM("component.book:close_graphic"),
            RSCM.getRSCM(PAGE_LEFT),
            RSCM.getRSCM("component.book:page_left_graphic"),
            RSCM.getRSCM(PAGE_RIGHT),
            RSCM.getRSCM("component.book:page_right_graphic"),
        )
        ifSetText("component.book:title", title)
        ifSetEvents(PAGE_LEFT, -1..-1, IfEvent.PauseButton)
        ifSetEvents(PAGE_RIGHT, -1..-1, IfEvent.PauseButton)
        val (left, right) = spreads[spread]
        for (line in 1..LINES_PER_PAGE) {
            ifSetText("component.book:page_left_text_$line", left.getOrElse(line - 1) { "" })
            ifSetText("component.book:page_right_text_$line", right.getOrElse(line - 1) { "" })
        }
        ifSetText("component.book:page_left_number", (spread * 2 + 1).toString())
        ifSetText("component.book:page_right_number", (spread * 2 + 2).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == spreads.lastIndex)
    }

    private companion object {
        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26

        const val NOTES_TITLE = "Nulodion's Notes"
        const val MANUAL_TITLE = "Dwarf Multicannon Manual"

        val NOTES_CHAPTERS =
            listOf(
                "<u>Cannon Ammunition</u>" to
                    listOf(
                        "Ammo for the Dwarf Multi Cannon must be made from steel bars. The bars " +
                            "must be heated in a furnace and used with the ammo mould.",
                    ),
            )

        val MANUAL_CHAPTERS =
            listOf(
                "<u>Constructing the cannon</u>" to
                    listOf(
                        "To construct the cannon, firstly set down the base of the cannon firmly " +
                            "onto the ground. Next add the Dwarf stand to the cannon base. Then " +
                            "add the Barrels.",
                        "Lastly add the Furnace, which powers the cannon. You should now have a " +
                            "fully set up Multi Cannon, to splat nasty creatures!",
                    ),
                "<u>Making ammo.</u>" to
                    listOf(
                        "The ammo for the cannon is made from steel bars. Firstly you must heat up " +
                            "a steel bar in a furnace.",
                        "Now pour the molten steel into a cannon ammo mould. You should now have a " +
                            "ready to fire Multi cannon ball.",
                    ),
                "<u>Firing the Cannon.</u>" to
                    listOf(
                        "The cannon will only fire when monsters are available to target. If you " +
                            "are carrying enough ammo the cannon will fire up to 30 rounds before " +
                            "it runs out and stops.",
                        "The cannon will automatically target non friendly creatures.",
                    ),
                "<u>Dwarf Cannon Warranty</u>" to
                    listOf(
                        "If your cannon is stolen or has been lost, after or during being set up, " +
                            "the Dwarf engineer will replace the parts, however cannon parts that " +
                            "were given away or dropped will not be replaced for free.",
                        "It is only possible to operate one cannon at a time.",
                        "By order of the members of the noble Dwarven Black Guard.",
                    ),
            )

        fun layout(chapters: List<Pair<String, List<String>>>): List<Pair<List<String>, List<String>>> {
            val lines = mutableListOf<String>()
            for ((heading, paragraphs) in chapters) {
                if (lines.size % LINES_PER_PAGE != 0) {
                    repeat(LINES_PER_PAGE - lines.size % LINES_PER_PAGE) { lines += "" }
                }
                lines += heading
                lines += ""
                for (paragraph in paragraphs) {
                    lines += wrap(paragraph)
                    lines += ""
                }
            }
            val pages = lines.chunked(LINES_PER_PAGE)
            val padded = if (pages.size % 2 == 0) pages else pages + listOf(emptyList())
            return padded.chunked(2).map { it[0] to it[1] }
        }

        fun wrap(text: String): List<String> {
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in text.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > WRAP_WIDTH) {
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
