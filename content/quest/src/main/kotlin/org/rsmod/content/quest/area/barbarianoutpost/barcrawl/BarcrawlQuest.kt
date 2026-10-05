package org.rsmod.content.quest.area.barbarianoutpost.barcrawl

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Alfred Grimhand's Barcrawl.
 *
 * `varp.barcrawl` holds the stage in [STAGE_VARBIT]: the Barbarian guard multinpc reads it (0 and 1
 * show the pre-crawl guard, [STAGE_COMPLETE] the one with Toggle-vials) and [PROGRESS_VARBIT], the
 * cache varbit the quest list reads, mirrors it. The cache has no vars for the individual bars, so
 * each signature is a flag on `varp.barcrawl_state`, cleared whenever the guard hands out a card.
 *
 * Every barcrawl bartender serves through [serve]. The bartenders whose everyday dialogue lives in
 * other modules add the "I'm doing Alfred Grimhand's Barcrawl." option when [canServe] allows it.
 */
@Singleton
class BarcrawlQuest @Inject constructor() :
    QuestScript(
        QUEST_KEY,
        "varp.barcrawl",
        rewards {
            extra("Access to the Barbarian Outpost agility course, and the vial-smashing trick.")
        },
        ItemRewardDisplay(CARD),
        questVarbit = STAGE_VARBIT,
    ) {
    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Barcrawl end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the guard completes it at $STAGE_COMPLETE."
        }

        onPlayerLogin { syncProgress(player) }
        onOpHeld1(CARD) { readCard() }
    }

    override fun subTitle(): String =
        "talking to a <col=800000>Barbarian guard</col> at the gate of the " +
            "<col=800000>Barbarian Outpost</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "The <red>Barbarian guards</red> at the <red>Barbarian Outpost</red> only let " +
                    "barbarians in. To prove I can drink like one I must complete the " +
                    "<red>Alfred Grimhand Barcrawl</red>."
            )

            if (!hasCard(access)) {
                objective(
                    "I have lost my <red>barcrawl card</red>. The guard can give me a new one, " +
                        "but I will have to start the barcrawl all over again."
                ) {}
                return@questJournal
            }

            objective(
                "Each bar on my <red>barcrawl card</red> will serve me its strongest drink and " +
                    "sign the card:"
            ) {}
            for (bar in BarcrawlBar.entries) {
                val entry = "${bar.barName} - ${bar.drinkName}"
                if (isSigned(access.player, bar)) strike(entry) else line(entry)
            }

            if (allSigned(access.player)) {
                objective(
                    "Every bar has signed my card. I should take it back to a " +
                        "<red>Barbarian guard</red>."
                ) {}
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I drank the strongest drink in each of the ten bars on the Alfred Grimhand " +
                    "barcrawl card and had every one of them signed."
            )
            line(
                "The barbarians now let me into the Barbarian Outpost agility course, and one of " +
                    "the guards showed me how to smash my empty vials as I finish my potions."
            )
        }

    fun isSigned(player: Player, bar: BarcrawlBar): Boolean = player.vars[bar.varbit] == 1

    fun allSigned(player: Player): Boolean = BarcrawlBar.entries.all { isSigned(player, it) }

    fun clearSignatures(player: Player) {
        VarPlayerIntMapSetter.set(player, STATE_VARP, 0)
    }

    fun syncProgress(player: Player) {
        VarPlayerIntMapSetter.set(player, PROGRESS_VARBIT, quest.getQuestStage(player))
    }

    private fun sign(player: Player, bar: BarcrawlBar) {
        VarPlayerIntMapSetter.set(player, bar.varbit, 1)
    }

    fun canServe(player: Player, bar: BarcrawlBar): Boolean =
        quest.isQuestInProgress(player) && CARD in player.inv && !isSigned(player, bar)

    fun hasCard(access: ProtectedAccess): Boolean =
        CARD in access.player.inv || access.bank.contains(CARD)

    fun start(access: ProtectedAccess) {
        quest.setQuestStage(access, STAGE_STARTED)
        syncProgress(access.player)
    }

    fun complete(access: ProtectedAccess) {
        quest.setQuestStage(access, STAGE_COMPLETE)
        syncProgress(access.player)
    }

    /**
     * The player tells [Dialogue.npc] that they are on the barcrawl and, if they can pay, is served
     * [bar]'s drink, suffers its effects and has the card signed. [server] names whoever signs in
     * the chat messages (the Rising Sun's barmaids each sign under their own name).
     */
    suspend fun Dialogue.serve(bar: BarcrawlBar, server: String = "The bartender") {
        chatPlayer(neutral, BARCRAWL_LINE)
        for (line in bar.pitch) {
            speak(line)
        }
        if (player.inv.count(COINS) < bar.price) {
            chatPlayer(sad, bar.brokeLine)
            return
        }
        if (access.invDel(access.inv, COINS, bar.price).failure) {
            return
        }
        sign(player, bar)
        access.drink(bar, server)
        bar.afterwards?.let { speak(it) }
    }

    private suspend fun Dialogue.speak(line: BarcrawlLine) {
        when (line) {
            is BarcrawlLine.Npc -> chatNpc(happy, line.text)
            is BarcrawlLine.Player -> chatPlayer(if (line.slurred) drunk else neutral, line.text)
            is BarcrawlLine.Overhead -> {
                access.say(line.text)
                if (line.text == HICCUP_TEXT) {
                    access.soundSynth(HICCUP_SOUND)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.drink(bar: BarcrawlBar, server: String) {
        for ((index, message) in bar.messages.withIndex()) {
            if (index == DRINK_MESSAGE) {
                anim(DRINK_SEQ)
                soundSynth(DRINK_SOUND)
            }
            mes(message.format(server))
            if (index == DRINK_MESSAGE) {
                applyEffect(bar.effect)
            }
            if (index != bar.messages.lastIndex) {
                delay(MESSAGE_TICKS)
            }
        }
        val sway = bar.effect.swayTicks ?: return
        delay(random.of(sway))
        camShakeResetAll()
    }

    private fun ProtectedAccess.applyEffect(effect: DrinkEffect) {
        for (stat in effect.drained) {
            statSub(stat, DRAIN_CONSTANT, DRAIN_PERCENT)
        }
        val percent = effect.damagePercent
        if (percent != null) {
            val damage =
                (player.baseHitpointsLvl * random.of(percent) / 100)
                    .coerceAtLeast(1)
                    .coerceAtMost(player.hitpoints - 1)
            if (damage > 0) {
                queueHit(delay = 1, type = HitType.Typeless, damage = damage)
            }
        }
        if (effect.swayTicks != null) {
            camShake(
                CamShakeAxis.PAN_LEFT_RIGHT,
                random = 0,
                amplitude = SWAY_AMPLITUDE,
                rate = SWAY_RATE,
            )
        }
    }

    private suspend fun ProtectedAccess.readCard() {
        if (quest.isQuestCompleted(player) || allSigned(player)) {
            mes("You are too drunk to be able to read the barcrawl card.")
            return
        }
        ifOpenMainModal(SCROLL_INTERFACE)
        val lines = buildList {
            add("The Official Alfred Grimhand Barcrawl")
            add("")
            for (bar in BarcrawlBar.entries) {
                val colour = if (isSigned(player, bar)) SIGNED_COLOUR else UNSIGNED_COLOUR
                add("<col=$colour>${bar.barName} - ${bar.drinkName}</col>")
            }
        }
        for (line in 1..SCROLL_LINES) {
            ifSetText("component.scroll:line$line", lines.getOrElse(line - 1) { "" })
        }
        soundSynth(PAPER_SOUND)
    }

    companion object {
        const val QUEST_KEY = "miniquest_barcrawl"
        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 2

        const val CARD = "obj.barcrawl_card"
        const val COINS = "obj.coins"
        const val BARCRAWL_LINE = "I'm doing Alfred Grimhand's Barcrawl."

        const val STAGE_VARBIT = "varbit.barcrawl_stage"
        const val PROGRESS_VARBIT = "varbit.barcrawl_progress"
        const val STATE_VARP = "varp.barcrawl_state"

        const val DRINK_SEQ = "seq.human_eat"
        const val DRINK_SOUND = "synth.drink"
        const val HICCUP_SOUND = "synth.hiccup"
        const val HICCUP_TEXT = "Hiccup!"
        const val PAPER_SOUND = "synth.paper_move"

        const val DRINK_MESSAGE = 1
        const val MESSAGE_TICKS = 2
        const val DRAIN_CONSTANT = 5
        const val DRAIN_PERCENT = 4
        const val SWAY_AMPLITUDE = 12
        const val SWAY_RATE = 2

        const val SCROLL_INTERFACE = "interface.scroll"
        const val SCROLL_LINES = 14
        const val SIGNED_COLOUR = "00ff00"
        const val UNSIGNED_COLOUR = "ff0000"
    }
}
