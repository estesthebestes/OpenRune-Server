package org.rsmod.content.quest.area.ardougne.tribaltotem

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The stage lives in `varp.totemquest` (endstate 5 in `dbrow.quest_tribaltotem`) through the
 * server-only progress varbit. The combination lock keeps its four dials in Jagex's own
 * `totemquest_combodoor_code*` varbits; the door counts as unlocked while they spell the code.
 */
@Singleton
class TribalTotemQuest @Inject constructor() :
    QuestScript(
        "quest_tribaltotem",
        "varp.totemquest",
        rewards {
            xp("stat.thieving", ThievingXpReward)
            extra("$SwordfishReward Swordfish")
        },
        ItemRewardDisplay(Totem),
        questVarbit = "varbit.tribal_totem_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "Tribal Totem end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $Complete."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    override fun subTitle(): String =
        "talking to <col=800000>Kangai Mau</col> in the <col=800000>Shrimp and Parrot</col> " +
            "restaurant in <col=800000>Brimhaven</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(player.player)
            val inv = player.player.inv
            description(
                "<red>Kangai Mau</red> of the Rantuki tribe wants me to take back the sacred " +
                    "totem that the explorer <red>Lord Handelmort</red> stole from his people " +
                    "and keeps in his house in <red>East Ardougne</red>."
            )
            objective(
                "The front door of Handelmort's mansion is locked. The <red>GPDT depot</red> " +
                    "in East Ardougne holds a crate addressed to Handelmort, and another crate " +
                    "holding Wizard Cromperty's teleport block, bound for the Wizards' Tower."
            ) {
                visibleWhen { stage == Started }
                custom(
                    inv.count(AddressLabel) > 0,
                    "I have taken the address label. I should put it on the other crate.",
                )
            }
            objective(
                "I have put Handelmort's address on the teleport block's crate. I should ask a " +
                    "<red>GPDT employee</red> to deliver it."
            ) {
                visibleWhen { stage == LabelPlaced }
            }
            objective(
                "The teleport block is now in the mansion. <red>Wizard Cromperty</red>, in the " +
                    "north-east of East Ardougne, can teleport me to it. The door code is " +
                    "Handelmort's middle name, and I will need <red>Thieving level 21</red> to " +
                    "spot the trap on the stairs."
            ) {
                visibleWhen { stage == Delivered }
            }
            objective(
                "I have spotted the trap on the stairs. The totem should be in a " +
                    "<red>chest</red> upstairs."
            ) {
                visibleWhen { stage == TrapFound }
                custom(
                    inv.count(Totem) > 0,
                    "I have the totem. I should take it back to <red>Kangai Mau</red> in " +
                        "Brimhaven.",
                )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Kangai Mau asked me to recover the Rantuki tribe's totem from Lord " +
                    "Handelmort's mansion in East Ardougne."
            )
            line(
                "I had Wizard Cromperty's teleport block delivered to the mansion by changing " +
                    "its address label, and he teleported me inside. I opened the combination " +
                    "lock with Handelmort's middle name, avoided the trap on the stairs, and " +
                    "found the totem in a chest upstairs."
            )
            line("Kangai Mau thanked me and gave me five swordfish.")
        }

    companion object {
        const val Started = 1
        const val LabelPlaced = 2
        const val Delivered = 3
        const val TrapFound = 4
        const val Complete = 5

        const val ThievingXpReward = 1775.0
        const val SwordfishReward = 5
        const val TrapThievingLevel = 21

        const val Totem = "obj.tribal_totem"
        const val AddressLabel = "obj.tribal_totem_label"
        const val Swordfish = "obj.swordfish"

        const val KangaiMau = "npc.kangai_mau"
        const val Horacio = "npc.horacio"
        const val GpdtEmployee = "npc.rpdt_employee"
        const val CrompertyPre = "npc.cromperty_pre_diary"
        const val CrompertyPost = "npc.cromperty_post_diary"

        val DepotLanding = CoordGrid(2649, 3271, 0)
        val MansionLanding = CoordGrid(2638, 3321, 0)
        val StairsTop = CoordGrid(2631, 3321, 1)
        val SewerLanding = CoordGrid(2640, 9697, 0)
    }
}
