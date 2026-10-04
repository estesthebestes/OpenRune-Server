package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.PreserveObjectivePlacement
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The stage lives in `varp.treequest` (endstate 9 in `dbrow.quest_treegnomevillage`) through the
 * server-only progress varbit. Everything else is Jagex's own state on `varp.279`: the three
 * tracker flags, the ballista's answer (the x coordinate minus one, rolled when Montai sends the
 * player for the trackers) and the village spirit tree's `bolren_got_orbs`.
 */
@Singleton
class TreeGnomeVillageQuest @Inject constructor() :
    QuestScript(
        "quest_treegnomevillage",
        "varp.treequest",
        rewards {
            xp("stat.attack", AttackXpReward)
            extra("A gnome amulet")
            extra("Use of the spirit trees")
        },
        ItemRewardDisplay(GnomeAmulet),
        questVarbit = "varbit.tree_gnome_village_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "Tree Gnome Village end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $Complete."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage(access.player) < stage) {
            quest.setQuestStage(access, stage)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>King Bolren</col> in the centre of the <col=800000>Tree Gnome " +
            "Maze</col>, south-east of <col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(player.player)
            description(
                "<red>King Bolren</red> of the tree gnomes has asked me to retrieve the " +
                    "<red>orb of protection</red> that General Khazard's troops seized on the " +
                    "battlefield north of the maze."
            )
            objective(
                "<red>Commander Montai</red> leads the gnome troops on the battlefield, north of " +
                    "the maze. He should tell me how I can help."
            ) {
                visibleWhen { stage == Started }
            }
            objective(
                "Montai needs <red>six normal logs</red> to build up the gnome battlements."
            ) {
                visibleWhen { stage == AgreedToGatherLogs }
                custom(
                    player.invTotal(player.inv, Logs) >= LogsNeeded,
                    "I have the logs. I should take them to <red>Montai</red>.",
                )
            }
            objective(
                "I gave Montai the logs. He needs a moment to organise his troops, then he will " +
                    "tell me about the next phase of the attack."
            ) {
                visibleWhen { stage == LogsGiven }
            }
            objective(
                "The <red>ballista</red> in the south-west corner of the battlefield can breach " +
                    "the <red>Khazard stronghold</red>, but it needs the stronghold's " +
                    "coordinates. Montai sent three <red>tracker gnomes</red> to find them."
            ) {
                visibleWhen { stage == TrackersSent }
                custom(player.player.knowsHeight, "The first tracker gnome gave me the height.")
                    .preserveObjective(PreserveObjectivePlacement.After, strikeObjective = false)
                    .strike()
                custom(player.player.knowsY, "The second tracker gnome gave me the y coordinate.")
                    .preserveObjective(PreserveObjectivePlacement.After, strikeObjective = false)
                    .strike()
                custom(
                        player.player.knowsX,
                        "The third tracker gnome has lost his mind. He only spoke in riddles " +
                            "about the x coordinate.",
                    )
                    .preserveObjective(PreserveObjectivePlacement.After, strikeObjective = false)
                    .strike()
            }
            objective(
                "I know where the stronghold is. I should fire the <red>ballista</red> in the " +
                    "south-west corner of the battlefield, and work out the x coordinate."
            ) {
                visibleWhen { stage == TrackersSent && player.player.hasKnownAllCoordinates() }
            }
            objective(
                "The ballista has broken through the stronghold wall. I should climb over the " +
                    "<red>crumbled wall</red> and find the orb inside."
            ) {
                visibleWhen { stage == StrongholdBreached }
            }
            objective(
                "I have found the <red>orb of protection</red>. I should take it back to " +
                    "<red>King Bolren</red> in the centre of the maze."
            ) {
                visibleWhen { stage == HasOrb }
            }
            objective(
                "Khazard's men raided the village while I was away and stole the other two " +
                    "orbs. A <red>Khazard warlord</red> carries them north of the stronghold, " +
                    "behind West Ardougne."
            ) {
                visibleWhen { stage == OrbReturned }
            }
            objective(
                "I defeated the Khazard warlord and found the <red>orbs of protection</red>. " +
                    "I should return them to <red>King Bolren</red>."
            ) {
                visibleWhen { stage == WarlordSlain }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Bolren asked me to retrieve the orb of protection seized by General " +
                    "Khazard's troops. I took logs to Commander Montai, found the three tracker " +
                    "gnomes and fired the ballista into the Khazard stronghold, where I took the " +
                    "orb from a chest."
            )
            line(
                "While I was away Khazard's men raided the village and stole the other two orbs. " +
                    "I defeated the Khazard warlord and took them from his remains."
            )
            line(
                "The gnomes returned all three orbs to the spirit tree. King Bolren gave me a " +
                    "gnome amulet and the use of the spirit trees."
            )
        }

    companion object {
        const val Started = 1
        const val AgreedToGatherLogs = 2
        const val LogsGiven = 3
        const val TrackersSent = 4
        const val StrongholdBreached = 5
        const val HasOrb = 6
        const val OrbReturned = 7
        const val WarlordSlain = 8
        const val Complete = 9

        const val RecommendedCombat = 45
        const val LogsNeeded = 6
        const val MaxTrackerAnswer = 4
        const val AttackXpReward = 11450.0

        const val Logs = "obj.logs"
        const val Orb = "obj.orb_of_protection"
        const val Orbs = "obj.orbs_of_protection"
        const val GnomeAmulet = "obj.gnome_amulet"
    }
}

var Player.knowsHeight: Boolean by boolVarBit("varbit.gnometracker_h")
var Player.knowsY: Boolean by boolVarBit("varbit.gnometracker_y")
var Player.knowsX: Boolean by boolVarBit("varbit.gnometracker_x")

/** The x coordinate the ballista must be given, minus one. Rolled when the trackers are sent. */
var Player.ballistaAnswer: Int by intVarBit("varbit.ballista")

/** `varbit.bolren_got_orbs`: 1 while the ceremony runs, 2 once the orbs sit in the spirit tree. */
var Player.bolrenGotOrbs: Int by intVarBit("varbit.bolren_got_orbs")

fun Player.hasKnownAllCoordinates(): Boolean = knowsHeight && knowsY && knowsX
