package org.rsmod.content.quest.area.ardougne.monksfriend

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The stage lives in `varp.drunkmonkquest` (endstate 80 in `dbrow.quest_monksfriend`) through the
 * server-only progress varbit. The only other state is the temporary expiry of Brother Omad's
 * party, which keeps the monks hiccuping for twenty minutes after the quest.
 */
class MonksFriendQuest :
    QuestScript(
        "quest_monksfriend",
        "varp.drunkmonkquest",
        rewards {
            xp("stat.woodcutting", WoodcuttingXpReward)
            extra("8 Law Runes")
            extra("Access to Brother Omad's parties")
        },
        ItemRewardDisplay(ChildsBlanket),
        questVarbit = "varbit.monks_friend_progress",
    ) {

    private var Player.partyEnd by intVarp("varp.monks_friend_party_end")

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "Monk's Friend end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $Complete."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun partyActive(player: Player): Boolean = player.partyEnd > player.currentMapClock

    fun startParty(player: Player) {
        player.partyEnd = player.currentMapClock + PartyTicks
    }

    override fun subTitle(): String =
        "talking to <col=800000>Brother Omad</col> at the <col=800000>Monastery</col> south of " +
            "<col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(player.player)
            val inv = player.player.inv
            description(
                "<red>Brother Omad</red> has not slept in days. Thieves in the forest have " +
                    "stolen the <red>blanket</red> of the child in his care, and the child " +
                    "will not stop crying without it."
            )
            objective(
                "The thieves hide in a secret <red>cave</red> in the forest, west of the " +
                    "monastery. A <red>ladder</red> inside the stone circle leads down to it. " +
                    "I should bring the <red>child's blanket</red> back to Brother Omad."
            ) {
                visibleWhen { stage == Started }
                custom(
                    inv.count(ChildsBlanket) > 0,
                    "I have the blanket. I should take it to <red>Brother Omad</red>.",
                )
            }
            objective(
                "Brother Omad has finally got some sleep and is planning a party for the " +
                    "child's birthday. I should ask him how he is."
            ) {
                visibleWhen { stage == BlanketReturned }
            }
            objective(
                "Brother Omad needs <red>wine</red> for the party, but <red>Brother Cedric</red> " +
                    "has not returned with it for three days. He is probably drunk and lost in " +
                    "the forest north of the monastery, south of <red>Ardougne Zoo</red>."
            ) {
                visibleWhen { stage == SentForCedric }
            }
            objective(
                "I found Brother Cedric. He is very drunk and needs a <red>jug of water</red> " +
                    "to sober up."
            ) {
                visibleWhen { stage == CedricFound }
                custom(
                    inv.count(JugOfWater) > 0,
                    "I have a jug of water. I should take it to <red>Brother Cedric</red>.",
                )
            }
            objective(
                "Brother Cedric is sober, but the cart he was pulling is broken. He needs " +
                    "help to mend it."
            ) {
                visibleWhen { stage == WaterGiven }
            }
            objective(
                "I need to bring Brother Cedric some <red>logs</red> or a <red>plank</red> so " +
                    "that he can repair his cart."
            ) {
                visibleWhen { stage == CartAccepted }
                custom(
                    inv.count(Logs) > 0 || inv.count(Plank) > 0,
                    "I have some wood. I should take it to <red>Brother Cedric</red>.",
                )
            }
            objective(
                "Brother Cedric is mending his cart and will bring the wine soon. I should " +
                    "tell <red>Brother Omad</red>."
            ) {
                visibleWhen { stage == WoodGiven }
            }
            objective(
                "Brother Omad has thanked me and the party is about to begin."
            ) {
                visibleWhen { stage == PartyStarted }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Thieves had stolen the blanket of the child Brother Omad looks after. I found " +
                    "their cave in the forest and brought it back, so the child could sleep."
            )
            line(
                "Brother Omad then asked me to find Brother Cedric, who was lost and drunk in " +
                    "the forest with the party's wine. I sobered him up with a jug of water and " +
                    "gave him some wood to repair his cart."
            )
            line(
                "Brother Omad rewarded me with 8 law runes and threw a party that none of us " +
                    "will forget."
            )
        }

    companion object {
        const val Started = 1
        const val BlanketReturned = 2
        const val SentForCedric = 3
        const val CedricFound = 4
        const val WaterGiven = 5
        const val CartAccepted = 6
        const val WoodGiven = 7
        const val PartyStarted = 8
        const val Complete = 80

        const val PartyTicks = 2000
        const val WoodcuttingXpReward = 2000.0
        const val LawRuneReward = 8

        const val Omad = "npc.brother_omad"
        const val Cedric = "npc.brother_cedric"
        const val Monk = "npc.monk_ardougne"

        const val ChildsBlanket = "obj.childs_blanket"
        const val JugOfWater = "obj.jug_water"
        const val Logs = "obj.logs"
        const val Plank = "obj.woodplank"
        const val LawRune = "obj.lawrune"
    }
}
