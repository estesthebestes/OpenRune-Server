package org.rsmod.content.quest.area.varrock.demonslayer

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class DemonSlayerQuest :
    QuestScript(
        "quest_demonslayer",
        "varp.demonstart",
        rewards { extra("Silverlight") },
        ItemRewardDisplay(SILVERLIGHT),
        questVarbit = "varbit.demonslayer_main",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Demon Slayer end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $STAGE_COMPLETE."
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Aris</col> in her tent on the west side of " +
            "<col=800000>Varrock Square</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            description(
                "<red>Aris</red> the fortune teller foresaw the demon <red>Delrith</red> coming " +
                    "back to destroy Varrock. She says I am destined to stop him, using the " +
                    "sword <red>Silverlight</red> and the magical incantation the hero Wally " +
                    "used long ago."
            )

            objective(
                "<red>Sir Prysin</red> keeps Silverlight in <red>Varrock Palace</red>. I should " +
                    "tell him that Aris sent me."
            ) {
                visibleWhen { stage(p) == STAGE_STARTED }
            }

            objective(
                "Silverlight is locked in a case that needs three keys to open it. Sir Prysin " +
                    "keeps one, <red>Captain Rovin</red> has another and " +
                    "<red>Wizard Traiborn</red> has the third."
            ) {
                visibleWhen { stage(p) == STAGE_KEY_HUNT && !p.silverlightCaseEmpty }
            }

            objective(
                "<red>Captain Rovin</red> is at the top of the north-west tower of the palace."
            ) {
                visibleWhen { stage(p) == STAGE_KEY_HUNT && !p.silverlightCaseEmpty }
                hasItem("silverlight_key_2", "I have Captain Rovin's key.").strike()
            }

            objective(
                "Sir Prysin dropped his key down the <red>drain</red> outside the palace " +
                    "kitchen. A bucket of water poured down it should wash the key into the sewers below."
            ) {
                visibleWhen { stage(p) == STAGE_KEY_HUNT && !p.silverlightCaseEmpty }
                custom(
                    p.drainKeyState == 1,
                    "I washed the key into the sewers. I should look for it in the mud there.",
                )
                hasItem("silverlight_key_3", "I have the key that was dropped down the drain.")
                    .strike()
            }

            objective(
                "<red>Wizard Traiborn</red> is on the first floor of the Wizards' Tower. He " +
                    "needs <red>$BONES_REQUIRED sets of bones</red> to open his wardrobe."
            ) {
                visibleWhen { stage(p) == STAGE_KEY_HUNT && !p.silverlightCaseEmpty }
                custom(
                    p.traibornAsked && p.traibornBonesGiven in 1 until BONES_REQUIRED,
                    "I have given him ${p.traibornBonesGiven} of the $BONES_REQUIRED sets of " +
                        "bones.",
                )
                hasItem("silverlight_key_1", "I have Wizard Traiborn's key.").strike()
            }

            objective(
                "Sir Prysin gave me <red>Silverlight</red>. The demon is being summoned at the " +
                    "<red>stone circle</red> south of Varrock. I must wield Silverlight to " +
                    "weaken him, then speak the incantation to banish him."
            ) {
                visibleWhen { stage(p) == STAGE_KEY_HUNT && p.silverlightCaseEmpty }
                custom(
                    !holdsSilverlight(p),
                    "I have lost Silverlight. Sir Prysin may have it.",
                )
            }

            objective("If I forget the incantation, <red>Aris</red> can tell me it again.") {
                visibleWhen { stage(p) >= STAGE_STARTED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Aris the fortune teller saw the demon Delrith in her crystal ball and told me " +
                    "I was destined to stop him, just as the hero Wally did long ago."
            )
            line(
                "I gathered the three keys to Silverlight's case from Captain Rovin, Wizard " +
                    "Traiborn and the palace drain, and Sir Prysin gave me the sword."
            )
            line(
                "At the stone circle south of Varrock I weakened Delrith with Silverlight and " +
                    "banished him with the incantation."
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun wieldsSilverlight(player: Player): Boolean = player.righthand?.id == SILVERLIGHT_ID

    fun holdsSilverlight(player: Player): Boolean =
        wieldsSilverlight(player) || player.inv.count(SILVERLIGHT) > 0

    fun holdsKey(access: ProtectedAccess, key: String): Boolean =
        access.inv.count(key) > 0 || access.bank.count(key) > 0

    fun keysCarried(player: Player): List<String> = KEYS.filter { player.inv.count(it) > 0 }

    fun rollIncantation(player: Player) {
        val order = WORDS.indices.shuffled().map { it + 1 }
        player.incantation1 = order[0]
        player.incantation2 = order[1]
        player.incantation3 = order[2]
        player.incantation4 = order[3]
        player.incantation5 = order[4]
    }

    /** Indexes into [WORDS] in the order they must be spoken, rolling one if none is stored. */
    fun incantation(player: Player): List<Int> {
        val stored =
            listOf(
                player.incantation1,
                player.incantation2,
                player.incantation3,
                player.incantation4,
                player.incantation5,
            )
        if (stored.sorted() != WORDS.indices.map { it + 1 }) {
            rollIncantation(player)
            return incantation(player)
        }
        return stored.map { it - 1 }
    }

    /** The spoken form, e.g. `Aber... Carlem... Gabindo... Purchai... Camerinthum!`. */
    fun incantationSpoken(player: Player, ending: String = "!"): String =
        incantation(player).joinToString("... ") { WORDS[it] } + ending

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_KEY_HUNT = 2
        const val STAGE_COMPLETE = 3

        const val SILVERLIGHT = "obj.silverlight"
        val SILVERLIGHT_ID: Int = SILVERLIGHT.asRSCM(RSCMType.OBJ)

        const val KEY_TRAIBORN = "obj.silverlight_key_1"
        const val KEY_ROVIN = "obj.silverlight_key_2"
        const val KEY_DRAIN = "obj.silverlight_key_3"
        val KEYS = listOf(KEY_ROVIN, KEY_DRAIN, KEY_TRAIBORN)

        const val BONES = "obj.bones"
        const val BONES_REQUIRED = 25
        const val SILVERLIGHT_FEE = 500

        val WORDS = listOf("Aber", "Gabindo", "Purchai", "Camerinthum", "Carlem")
    }
}
