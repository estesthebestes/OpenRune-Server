package org.rsmod.content.quest.area.wilderness.entertheabyss

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Enter the Abyss (miniquest).
 *
 * `varp.abyssal_miniquest` holds the stage, which both Mage of Zamorak multinpcs read: the Varrock
 * temple mage only exists from [STAGE_SENT_TO_VARROCK], and the Wilderness mage gains his Teleport
 * op at [STAGE_COMPLETE]. Each essence mine teleporter's reading is its own cache varbit on
 * `varp.abyssal_warp`, so the reading count is always derived from which sources are set, and the
 * readings outlive the orb: a lost orb is replaced empty and a single teleport refills it once all
 * [READINGS_NEEDED] are recorded. The dialogue flags are the cache's `abyssal_miniquest_*` varbits
 * on `varp.runemysteries_secondary`.
 */
@Singleton
class EnterTheAbyssQuest :
    QuestScript(
        QUEST_KEY,
        "varp.abyssal_miniquest",
        rewards {
            xp("stat.runecrafting", RUNECRAFT_XP)
            extra("The Mage of Zamorak in the Wilderness will now teleport you to the Abyss.")
        },
        ItemRewardDisplay(ABYSSAL_BOOK),
        questVarbit = PROGRESS_VARBIT,
    ) {

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun meetsRequirements(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, RUNE_MYSTERIES)

    fun isResearching(player: Player): Boolean =
        stage(player) == STAGE_RESEARCHING || stage(player) == STAGE_READINGS_TAKEN

    fun setStage(access: ProtectedAccess, stage: Int) {
        quest.setQuestStage(access, stage)
    }

    /** Puts an already rewarded player back at the endstate without paying out again. */
    fun restoreCompletion(player: Player) {
        VarPlayerIntMapSetter.set(player, PROGRESS_VARBIT, STAGE_COMPLETE)
    }

    fun hasReading(player: Player, teleporter: EssenceMineTeleporter): Boolean =
        player.vars[readingVarbit(teleporter)] != 0

    fun recordReading(player: Player, teleporter: EssenceMineTeleporter) {
        VarPlayerIntMapSetter.set(player, readingVarbit(teleporter), 1)
    }

    fun readingCount(player: Player): Int = EssenceMineTeleporter.entries.count { hasReading(player, it) }

    fun readingsRemaining(player: Player): Int = (READINGS_NEEDED - readingCount(player)).coerceAtLeast(0)

    fun hasOrb(player: Player): Boolean = EMPTY_ORB in player.inv || FULL_ORB in player.inv

    override fun subTitle(): String =
        "talking to the <col=800000>Mage of Zamorak</col> at the northern end of the " +
            "<col=800000>River Lum</col> in the <col=800000>Wilderness</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            val stage = stage(p)

            if (stage < STAGE_SENT_TO_VARROCK) {
                return@questJournal
            }
            objective(
                "I spoke to the <red>Mage of Zamorak</red> in the Wilderness. He told me that the " +
                    "Wilderness was no place to talk and asked me to meet him in the " +
                    "<red>Chaos Temple</red> in south-east <red>Varrock</red>, by the rune shop.",
            ) {}
            if (stage == STAGE_SENT_TO_VARROCK) {
                return@questJournal
            }

            objective(
                "The Mage of Zamorak wants to learn how to reach the <red>Rune Essence Mine</red>. " +
                    "He gave me a <red>scrying orb</red> and asked me to carry it while being " +
                    "teleported to the mine from three different places.",
            ) {}
            for (teleporter in EssenceMineTeleporter.entries) {
                val entry = "${teleporter.displayName()} - ${teleporter.locationName()}"
                if (hasReading(p, teleporter)) strike(entry) else line(entry)
            }

            if (!hasOrb(p) && FULL_ORB !in player.bank && EMPTY_ORB !in player.bank) {
                objective(
                    "I have lost my scrying orb. The Mage of Zamorak in Varrock can give me " +
                        "another without losing the readings it has taken.",
                ) {}
            }
            if (stage == STAGE_RESEARCHING) {
                val remaining = readingsRemaining(p)
                val places = if (remaining == 1) "place" else "places"
                objective("I still need to teleport to the mine from $remaining more $places.") {}
            } else if (EMPTY_ORB in p.inv) {
                objective(
                    "My replacement orb is still empty. One more teleport to the mine with it in " +
                        "my backpack should restore the readings.",
                ) {}
            } else {
                objective(
                    "The orb has absorbed enough teleport information. I should take it back to " +
                        "the <red>Mage of Zamorak</red> in Varrock.",
                ) {}
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I met the Mage of Zamorak in the Wilderness and again in Varrock's Chaos Temple. " +
                    "He gave me a scrying orb, which I carried while being teleported to the Rune " +
                    "Essence Mine from three different places.",
            )
            line(
                "In return for the readings, the Zamorak Magical Institute shared its research " +
                    "notes on the Abyss and gave me a small essence pouch. The Mage of Zamorak in " +
                    "the Wilderness will now teleport me to the Abyss.",
            )
        }

    private fun EssenceMineTeleporter.displayName(): String =
        when (this) {
            EssenceMineTeleporter.Sedridor -> "Archmage Sedridor"
            EssenceMineTeleporter.Aubury -> "Aubury"
            EssenceMineTeleporter.Cromperty -> "Wizard Cromperty"
            EssenceMineTeleporter.Brimstail -> "Brimstail"
            EssenceMineTeleporter.Distentor -> "Wizard Distentor"
        }

    private fun EssenceMineTeleporter.locationName(): String =
        when (this) {
            EssenceMineTeleporter.Sedridor -> "Wizards' Tower"
            EssenceMineTeleporter.Aubury -> "Varrock"
            EssenceMineTeleporter.Cromperty -> "East Ardougne"
            EssenceMineTeleporter.Brimstail -> "Tree Gnome Stronghold"
            EssenceMineTeleporter.Distentor -> "Wizards' Guild"
        }

    companion object {
        const val QUEST_KEY = "miniquest_entertheabyss"
        const val PROGRESS_VARBIT = "varbit.abyssal_miniquest_progress"
        const val RUNE_MYSTERIES = "quest_runemysteries"

        const val STAGE_SENT_TO_VARROCK = 1
        const val STAGE_RESEARCHING = 2
        const val STAGE_READINGS_TAKEN = 3
        const val STAGE_COMPLETE = 4

        const val READINGS_NEEDED = 3
        const val RUNECRAFT_XP = 1000.0

        const val EMPTY_ORB = "obj.scrying_orb_empty"
        const val FULL_ORB = "obj.scrying_orb_full"
        const val ABYSSAL_BOOK = "obj.rcu_instruction_book"
        const val SMALL_POUCH = "obj.rcu_pouch_small"

        fun readingVarbit(teleporter: EssenceMineTeleporter): String =
            when (teleporter) {
                EssenceMineTeleporter.Sedridor -> "varbit.rcu_essencespot_wizardstower"
                EssenceMineTeleporter.Aubury -> "varbit.rcu_essencespot_aubury"
                EssenceMineTeleporter.Cromperty -> "varbit.rcu_essencespot_cromperty"
                EssenceMineTeleporter.Brimstail -> "varbit.rcu_essencespot_brimstail"
                EssenceMineTeleporter.Distentor -> "varbit.rcu_essencespot_wizardsguild"
            }
    }
}
