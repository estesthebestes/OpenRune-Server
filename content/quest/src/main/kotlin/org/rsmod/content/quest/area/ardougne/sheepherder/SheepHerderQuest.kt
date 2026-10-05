package org.rsmod.content.quest.area.ardougne.sheepherder

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sheep Herder. The stage lives in `varp.sheepherderquest` (endstate 3 in
 * `dbrow.quest_sheepherder`): [STAGE_STARTED] once Councillor Halgrive hands over the poisoned
 * feed, [STAGE_DISPOSED] when all four colours are burned, [STAGE_COMPLETE] once he has paid up.
 *
 * Each colour's progress is a cache varbit on `varp.sheepherdervar` holding a [SheepState]. The
 * enclosure sheep are multinpcs on those varbits, so a sheep only shows in the pen to the player
 * who herded it there.
 */
class SheepHerderQuest :
    QuestScript(
        QUEST_KEY,
        "varp.sheepherderquest",
        rewards { item(COINS, REWARD_COINS, "3,100 coins") },
        ItemRewardDisplay(CATTLEPROD, zoom = 250),
        questVarbit = "varbit.sheepherder_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Sheep Herder end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $STAGE_COMPLETE."
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Councillor Halgrive</col> in the graveyard south of the church " +
            "in <col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = player.player
            val stage = stage(p)
            val start =
                "<red>Councillor Halgrive</red> asked me to herd the plague-ridden sheep that " +
                    "escaped from a farm into an enclosure, kill them with poisoned feed and " +
                    "burn their remains. There is one <red>red</red>, one <red>green</red>, " +
                    "one <red>blue</red> and one <red>yellow</red> sheep to deal with."
            if (stage >= STAGE_DISPOSED) {
                strike(start)
                for (colour in SheepColour.entries) strike(colour.journalDone())
                objective("All four sheep are dead and burned. I should tell <red>Councillor Halgrive</red>.") {}
                return@questJournal
            }
            description(start)
            if (!hasFeed(p, player.bank)) {
                objective("I've lost the poisoned sheep feed. <red>Councillor Halgrive</red> will have more.") {}
            }
            val missing = protectionMissing(p)
            when {
                missing.isNotEmpty() && !ownsClothing(p, player.bank) ->
                    objective(
                        "<red>Doctor Orbon</red>, inside the church, sells protective clothing " +
                            "for 100 coins. I shouldn't go near the sheep without it."
                    ) {}
                missing.isNotEmpty() ->
                    objective(
                        "I need to wear my ${missing.joinToString(" and ")} to handle the sheep."
                    ) {}
                else -> strike("I'm wearing Doctor Orbon's protective clothing.")
            }
            when {
                CATTLEPROD in p.worn -> strike("I'm wielding the cattleprod.")
                CATTLEPROD in p.inv -> objective("I need to wield the cattleprod.") {}
                else ->
                    objective(
                        "There is a <red>cattleprod</red> lying on the ground near the " +
                            "incinerator inside <red>Farmer Brumty</red>'s enclosure."
                    ) {}
            }
            objective(
                "I must prod one sheep of each colour into the enclosure, feed it the poisoned " +
                    "feed, then burn its bones in the incinerator."
            ) {}
            for (colour in SheepColour.entries) {
                val state = state(p, colour)
                val text = colour.journalLine(state, colour.bones in p.inv)
                if (state == SheepState.BURNED) strike(text) else objective(text) {}
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Councillor Halgrive asked me to dispose of four plague-ridden sheep. Wearing " +
                    "Doctor Orbon's protective clothing, I prodded one sheep of each colour " +
                    "into the enclosure, fed them poisoned feed and burned the remains in the " +
                    "incinerator."
            )
            line("Halgrive paid me back for the clothing, and 3,000 coins on top.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isActive(player: Player): Boolean = stage(player) == STAGE_STARTED

    fun state(player: Player, colour: SheepColour): SheepState =
        SheepState.entries[player.vars[colour.varbit].coerceIn(0, SheepState.entries.size - 1)]

    fun setState(player: Player, colour: SheepColour, state: SheepState) {
        VarPlayerIntMapSetter.set(player, colour.varbit, state.ordinal)
    }

    fun resetSheep(player: Player) {
        for (colour in SheepColour.entries) {
            setState(player, colour, SheepState.LOOSE)
        }
    }

    fun remaining(player: Player): List<SheepColour> =
        SheepColour.entries.filter { state(player, it) != SheepState.BURNED }

    fun hasFeed(player: Player, bank: Inventory?): Boolean =
        FEED in player.inv || (bank != null && FEED in bank)

    fun ownsClothing(player: Player, bank: Inventory?): Boolean =
        owns(player, bank, JACKET) && owns(player, bank, TROUSERS)

    fun owns(player: Player, bank: Inventory?, obj: String): Boolean =
        obj in player.inv || obj in player.worn || (bank != null && obj in bank)

    fun protectionMissing(player: Player): List<String> = buildList {
        if (JACKET !in player.worn) add("plague jacket")
        if (TROUSERS !in player.worn) add("plague trousers")
    }

    fun isProtected(player: Player): Boolean = protectionMissing(player).isEmpty()

    companion object {
        const val QUEST_KEY = "quest_sheepherder"

        const val STAGE_STARTED = 1
        const val STAGE_DISPOSED = 2
        const val STAGE_COMPLETE = 3

        const val CLOTHING_PRICE = 100
        const val REWARD_COINS = 3_100

        const val HALGRIVE = "npc.councillor_halgrive"
        const val ORBON = "npc.doctor_orbon"
        const val BRUMTY = "npc.farmer_brumty"

        const val FEED = "obj.poisoned_feed"
        const val CATTLEPROD = "obj.cattleprod"
        const val JACKET = "obj.plague_jacket"
        const val TROUSERS = "obj.plague_trousers"
        const val COINS = "obj.coins"

        const val NO_PROTECTION =
            "The sheep looks extremely unwell. I don't want to touch it without a full " +
                "protective suit."
    }
}

enum class SheepState {
    LOOSE,
    PENNED,
    BONES,
    BURNED,
}

/**
 * One of the four discoloured sheep. [fieldNpc] is the type the three grazing sheep of that colour
 * are spawned as, [enclosureNpc] the per-player copy that appears in the pen once one is herded.
 * Both are bound by their base ids: the engine dispatches ops on the spawned type, not on the
 * multinpc form the client draws.
 */
enum class SheepColour(
    val label: String,
    val varbit: String,
    val fieldNpc: String,
    val enclosureNpc: String,
    val bones: String,
) {
    RED(
        "red",
        "varbit.sheepherder_sheep_a",
        "npc.plaguesheep_1",
        "npc.herder_plaguesheep_1_enclosure",
        "obj.sheepbonesa",
    ),
    GREEN(
        "green",
        "varbit.sheepherder_sheep_b",
        "npc.plaguesheep_2",
        "npc.herder_plaguesheep_2_enclosure",
        "obj.sheepbonesb",
    ),
    BLUE(
        "blue",
        "varbit.sheepherder_sheep_c",
        "npc.plaguesheep_3",
        "npc.herder_plaguesheep_3_enclosure",
        "obj.sheepbonesc",
    ),
    YELLOW(
        "yellow",
        "varbit.sheepherder_sheep_d",
        "npc.plaguesheep_4",
        "npc.herder_plaguesheep_4_enclosure",
        "obj.sheepbonesd",
    );

    val title: String = label.replaceFirstChar { it.uppercase() }

    fun journalLine(state: SheepState, bonesCarried: Boolean): String =
        when (state) {
            SheepState.LOOSE -> "$title sheep: not yet herded."
            SheepState.PENNED -> "$title sheep: in the enclosure. It needs the poisoned feed."
            SheepState.BONES ->
                if (bonesCarried) {
                    "$title sheep: dead. I have its bones to burn in the incinerator."
                } else {
                    "$title sheep: dead. Its bones are still in the enclosure."
                }
            SheepState.BURNED -> journalDone()
        }

    fun journalDone(): String = "$title sheep: dead and burned."
}
