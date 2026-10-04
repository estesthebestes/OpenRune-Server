package org.rsmod.content.quest.area.ardougne.plaguecity

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.ardougne.GAS_MASK
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Plague City. The stage lives in `varp.elenaquest` (endstate 29 in `dbrow.quest_plaguecity`),
 * which the cache's Elena cell npc (`npc.elenap`) reads directly: she shows while the varp is
 * below [STAGE_FREED_ELENA]. Everything else the client draws (Edmond in the sewer, the mud hole,
 * the grill and rope, Elena at home) is a cache varbit on `varp.elenaquest_extra_bits`; the
 * quest's server-only flags are varbits on `varp.plaguecity_state`.
 */
class PlagueCityQuest :
    QuestScript(
        "quest_plaguecity",
        "varp.elenaquest",
        rewards {
            xp("stat.mining", MINING_XP)
            extra("An Ardougne teleport scroll")
        },
        ItemRewardDisplay(GAS_MASK),
        questVarbit = "varbit.plaguecity_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Plague City end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $STAGE_COMPLETE."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Moves the quest forward to [stage] unless it is already there or past it. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage(access.player) < stage) {
            quest.setQuestStage(access, stage)
        }
    }

    internal fun clearProgress(player: Player) {
        player.edmondBelow = false
        player.elenaHome = false
        player.mudDug = false
        player.grillChecked = false
        player.pipeState = PIPE_BLOCKED
        player.pictureAsked = false
        player.keyAsked = false
        player.bucketsPoured = 0
        player.toldToDig = false
        player.metJethick = false
        player.gotNote = false
    }

    override fun subTitle(): String =
        "talking to <col=800000>Edmond</col> behind his house in the north-west corner of " +
            "<col=800000>East Ardougne</col>, beside the wall."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            val stage = stage(p)
            description(
                "<red>Edmond</red>'s daughter <red>Elena</red>, a healer, crossed the wall into " +
                    "plague-stricken <red>West Ardougne</red> three weeks ago and has not been " +
                    "heard from since. I agreed to help find her."
            )
            objective(
                "<red>Alrena</red> can make me a gas mask if I bring her some " +
                    "<red>dwellberries</red>. They grow in McGrubor's Wood, west of Seers' " +
                    "Village."
            ) {
                visibleWhen { stage == STAGE_STARTED }
                hasItem("dwellberries", "I have some dwellberries.").strike()
            }
            objective(
                "Alrena made me a <red>gas mask</red>. Edmond wants me to dig down into the " +
                    "sewers from the <red>mud patch</red> behind his house, but the soil needs " +
                    "softening with <red>four buckets of water</red> first."
            ) {
                visibleWhen { stage == STAGE_HAS_GAS_MASK }
            }
            objective(
                "The soil is soft enough now. I should dig into it with a <red>spade</red>."
            ) {
                visibleWhen { stage == STAGE_SOIL_SOFTENED }
            }
            objective(
                "I fell through into the sewers and Edmond followed me down. He thinks the " +
                    "<red>pipe to the south</red> comes up in West Ardougne."
            ) {
                visibleWhen { stage == STAGE_DUG_TUNNEL }
            }
            objective(
                "There is an iron <red>grill</red> over the pipe that I cannot pull off alone. " +
                    "If I tie a <red>rope</red> to it, Edmond and I could pull together."
            ) {
                visibleWhen { stage == STAGE_GRILL_CHECKED }
                hasItem("rope", "I have a rope.").strike()
            }
            objective(
                "I tied the rope to the grill. I should ask <red>Edmond</red> to help pull."
            ) {
                visibleWhen { stage == STAGE_ROPE_TIED }
            }
            objective(
                "We pulled the grill off. With my <red>gas mask</red> on I can climb the pipe " +
                    "into West Ardougne, where I should look for <red>Jethick</red>, an old " +
                    "friend of the family."
            ) {
                visibleWhen { stage == STAGE_GRILL_REMOVED && !p.pictureAsked }
            }
            objective(
                "Jethick wants to see a <red>picture of Elena</red> before he can help. There " +
                    "should be one in Edmond's house."
            ) {
                visibleWhen { stage == STAGE_GRILL_REMOVED && p.pictureAsked }
                hasItem("elena_picture", "I have a picture of Elena.").strike()
            }
            objective(
                "Jethick says Elena was staying with the <red>Rehnison family</red> in the " +
                    "timbered house at the north end of town. He asked me to return a " +
                    "<red>book</red> he borrowed from them."
            ) {
                visibleWhen { stage == STAGE_GOT_BOOK }
            }
            objective(
                "The Rehnisons let me in. Their daughter <red>Milli</red>, upstairs, saw what " +
                    "happened to Elena."
            ) {
                visibleWhen { stage == STAGE_BOOK_RETURNED }
            }
            objective(
                "Milli saw Elena dragged into the boarded-up <red>plague house</red> in the " +
                    "south-east corner of West Ardougne."
            ) {
                visibleWhen { stage == STAGE_TALKED_MILLI }
            }
            objective(
                "The mourners will not let me into the plague house. I need clearance from the " +
                    "<red>head mourner</red> or <red>Bravek</red>, the city warder, at the " +
                    "civic office north of the town square."
            ) {
                visibleWhen { stage == STAGE_MOURNER_REFUSED || stage == STAGE_CLERK_PERMISSION }
            }
            objective(
                "Bravek is too hungover to help. He gave me a note with his herbalist's " +
                    "<red>hangover cure</red>: chocolate dust mixed into a bucket of milk, " +
                    "then snape grass."
            ) {
                visibleWhen { stage == STAGE_TALKED_BRAVEK }
                hasItem("hangover_cure", "I have a hangover cure.").strike()
            }
            objective(
                "Bravek is feeling much better. I should tell him the mourners will not " +
                    "listen to me."
            ) {
                visibleWhen { stage == STAGE_CURED_BRAVEK }
            }
            objective("Bravek gave me a <red>warrant</red> to enter the plague house.") {
                visibleWhen { stage == STAGE_GOT_WARRANT }
                hasItem("warrant", "I have the warrant.").strike()
            }
            objective(
                "I sneaked into the plague house. Elena is locked in the cell downstairs and " +
                    "the <red>key</red> is stashed somewhere in the house."
            ) {
                visibleWhen { stage == STAGE_SNEAKED_IN }
                hasItem("elenakey", "I found a small key.").strike()
            }
            objective(
                "I unlocked the cell door. I should tell <red>Elena</red> she is free to go."
            ) {
                visibleWhen { stage == STAGE_UNLOCKED_CELL }
            }
            objective(
                "I freed Elena. She said her father would reward me; I should climb down the " +
                    "<red>manhole</red> in the town square, go back up the mud pile and talk " +
                    "to <red>Edmond</red>."
            ) {
                visibleWhen { stage == STAGE_FREED_ELENA }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Edmond's daughter Elena went into plague-ridden West Ardougne to help the " +
                    "sick and never came back. With a gas mask from Alrena and a lot of water " +
                    "I dug down into the sewers, and Edmond and I pulled the grill off a pipe " +
                    "that came up inside the city."
            )
            line(
                "Jethick pointed me to the Rehnisons, whose daughter Milli saw Elena dragged " +
                    "into the plague house. After curing the city warder Bravek's hangover he " +
                    "gave me a warrant, I slipped past the mourners, found the key and set " +
                    "Elena free."
            )
            line("Edmond gave me a magic scroll that teaches the Ardougne Teleport spell.")
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_HAS_GAS_MASK = 2
        const val STAGE_SOIL_SOFTENED = 3
        const val STAGE_DUG_TUNNEL = 4
        const val STAGE_GRILL_CHECKED = 5
        const val STAGE_ROPE_TIED = 6
        const val STAGE_GRILL_REMOVED = 7
        const val STAGE_GOT_BOOK = 8
        const val STAGE_BOOK_RETURNED = 9
        const val STAGE_TALKED_MILLI = 10
        const val STAGE_MOURNER_REFUSED = 11
        const val STAGE_CLERK_PERMISSION = 12
        const val STAGE_TALKED_BRAVEK = 13
        const val STAGE_CURED_BRAVEK = 14
        const val STAGE_GOT_WARRANT = 15
        const val STAGE_SNEAKED_IN = 16
        const val STAGE_UNLOCKED_CELL = 17

        /** `npc.elenap` (Elena in her cell) is hidden from this value upwards. */
        const val STAGE_FREED_ELENA = 28
        const val STAGE_COMPLETE = 29

        const val MINING_XP = 2425.0
        const val BUCKETS_NEEDED = 4

        const val DWELLBERRIES = "obj.dwellberries"
        const val PICTURE = "obj.elena_picture"
        const val BOOK = "obj.turnip_book"
        const val SCRUFFY_NOTE = "obj.scruffy_note"
        const val HANGOVER_CURE = "obj.hangover_cure"
        const val WARRANT = "obj.warrant"
        const val SMALL_KEY = "obj.elenakey"
        const val TELEPORT_SCROLL = "obj.ardougnescroll"
    }
}
