package org.rsmod.content.quest.area.ardougne.hazeelcult

import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import org.rsmod.api.player.musicClocks
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.PreserveObjectivePlacement
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The stage lives in `varp.hazeelcultquest` (endstate 9 in `dbrow.quest_hazeelcult`) through the
 * server-only progress varbit. Both sides share stages 4 and 6; the side is Clivet's own varbit,
 * which Jagex flips when the player refuses him. All other flags are Jagex's `hazeelcult_*`
 * varbits on `varp.hazeelcult_secondary`; only the sewer valves need server varbits.
 */
@Singleton
class HazeelCultQuest @Inject constructor() :
    QuestScript(
        "quest_hazeelcult",
        "varp.hazeelcultquest",
        rewards {
            xp("stat.thieving", ThievingXpReward)
            item("obj.coins", CoinReward, label = "2,000 Coins")
        },
        ItemRewardDisplay(HazeelMark),
        questVarbit = "varbit.hazeel_cult_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "Hazeel Cult end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $Complete."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = stage(player) >= Complete

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage(access.player) < stage) {
            quest.setQuestStage(access, stage)
        }
    }

    /** The Carnilleans' first reward screen. The real one only follows Jones's arrest. */
    fun showPartialCompletion(access: ProtectedAccess) {
        access.player.musicClocks = 0
        access.player.client.write(MidiJingle(QuestCompleteJingle))
        access.ifOpenMain("interface.questscroll")
        access.ifSetText("component.questscroll:quest_title", "You have completed Hazeel Cult!")
        access.ifSetText("component.questscroll:quest_reward1", "5 Coins")
        access.ifSetObj(
            "component.questscroll:quest_model",
            obj = CarnilleanArmour,
            zoom = ItemRewardDisplay(HazeelMark).zoom,
        )
        for (line in 2..7) {
            access.ifSetText("component.questscroll:quest_reward$line", "")
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Ceril Carnillean</col> in the south-west of " +
            "<col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            val stage = stage(p)
            val ceril = p.sidedWithCeril
            description(
                "<red>Ceril Carnillean</red> is plagued by thieving cultists from a cave south " +
                    "of East Ardougne. I offered to help him."
            )
            objective(
                "Ceril thinks the cult hides in a <red>cave</red> east of the Clock Tower, south " +
                    "of the city. I should find it."
            ) {
                visibleWhen { stage == Started }
            }
            objective(
                "A cultist named <red>Clivet</red> waits in the cave. He has offered me a place " +
                    "in the cult. I must decide whether to help the cult or the Carnilleans."
            ) {
                visibleWhen { stage == ChooseSide }
            }
            objective(
                "I refused Clivet and he fled on a raft. The raft only reaches the " +
                    "<red>hideout</red> if the five <red>sewer valves</red> are set correctly, " +
                    "and I will have to work out how. The leader, <red>Alomone</red>, should " +
                    "have the stolen armour."
            ) {
                visibleWhen { ceril && stage == SideChosen }
                custom(
                    SewerValve.correctCount(p) == SewerValve.entries.size,
                    "The valves are all set. I should be able to reach the hideout by raft.",
                )
                    .preserveObjective(PreserveObjectivePlacement.After, strikeObjective = false)
            }
            objective(
                "I killed <red>Alomone</red>. The stolen <red>Carnillean armour</red> is in the " +
                    "chest in the hideout. I should take it back to <red>Ceril</red>."
            ) {
                visibleWhen { ceril && stage == InHideout }
            }
            objective(
                "Ceril would not believe me about his butler, <red>Jones</red>, and sent me " +
                    "away. I need proof. Perhaps there is something in the butler's cupboard upstairs."
            ) {
                visibleWhen { ceril && stage == ArmourReturned && !p.hazeelEvidenceFound }
            }
            objective(
                "I found a bottle of <red>poison</red> and a <red>Hazeel's mark</red> in the " +
                    "cupboard. I should show them to <red>Ceril</red>."
            ) {
                visibleWhen { ceril && stage == ArmourReturned && p.hazeelEvidenceFound }
            }
            objective(
                "I accepted Clivet's offer to join the cult. To prove my loyalty I must " +
                    "<red>poison Ceril's food</red> with the bottle he gave me. The range is in " +
                    "the Carnilleans' basement."
            ) {
                visibleWhen { !ceril && stage == SideChosen }
            }
            objective(
                "I poisoned the soup, but the family dog died instead. I should see how " +
                    "<red>Ceril</red> took it, then report to <red>Clivet</red>."
            ) {
                visibleWhen { !ceril && stage == FoodPoisoned && !p.hazeelSewerChat }
                custom(
                    p.hazeelPoisonSuccess,
                    "Ceril is mourning the dog. Clivet should hear of it.",
                )
            }
            objective(
                "Clivet gave me a <red>mark of Hazeel</red>. Its pattern shows how to set the " +
                    "<red>sewer valves</red> so the raft can reach the cult's hideout, where " +
                    "<red>Alomone</red> awaits."
            ) {
                visibleWhen { !ceril && stage == FoodPoisoned && p.hazeelSewerChat }
            }
            objective(
                "Alomone wants me to find a <red>scroll of restoration</red> hidden in the " +
                    "Carnillean mansion, with help from <red>Butler Jones</red>. A key may be " +
                    "hidden in the basement, and the scroll somewhere the family never goes."
            ) {
                visibleWhen { !ceril && stage == InHideout }
                custom(
                    p.inv.contains(ChestKey),
                    "I have found a chest key in the basement crate.",
                )
                    .preserveObjective(PreserveObjectivePlacement.After, strikeObjective = false)
                custom(
                    p.inv.contains(HazeelScroll),
                    "I have the scroll. I should return to <red>Alomone</red>.",
                )
                    .preserveObjective(PreserveObjectivePlacement.After, strikeObjective = false)
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Ceril Carnillean asked me to find the family armour that a cult had stolen from " +
                    "his house, and in the sewers I met their spokesman, Clivet."
            )
            if (player.player.sidedWithCeril) {
                line(
                    "I refused the cult, defeated their leader Alomone and returned the armour. " +
                        "Ceril would not believe his butler was a cultist until I found poison " +
                        "and a mark of Hazeel in Jones's cupboard. Jones was arrested, and Ceril " +
                        "rewarded me with 2,000 coins."
                )
            } else {
                line(
                    "I joined the cult and poisoned the Carnilleans' soup, though Scruffy the " +
                        "dog was the one who died. With the butler's help I found the scroll of " +
                        "restoration, and Alomone used it to bring the Mahjarrat Hazeel back to " +
                        "life."
                )
            }
        }

    companion object {
        const val Started = 2
        const val ChooseSide = 3
        const val SideChosen = 4
        const val FoodPoisoned = 5
        const val InHideout = 6
        const val ArmourReturned = 7
        const val Complete = 9

        const val ThievingXpReward = 1500.0
        const val CoinReward = 2000
        const val FakeEndingCoins = 5
        const val RecommendedCombat = 10

        const val QuestCompleteJingle = 153

        const val Coins = "obj.coins"
        const val Poison = "obj.poison"
        const val HazeelMark = "obj.mark_of_hazeel"
        const val CarnilleanArmour = "obj.carnillean_armour"
        const val ChestKey = "obj.carnilleanchestkey"
        const val HazeelScroll = "obj.hazeel_scroll"
    }
}
