package org.rsmod.content.quest.area.ardougne.fightarena

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fight Arena. The stage lives in `varp.arenaquest` (endstate 14 in `dbrow.quest_fightarena`)
 * through the server-only progress varbit. The cache's own npcs read that varp: Sammy sits in his
 * cell until [SammyFreed], Justin stands in the arena only while Khazard holds him, and so on, so
 * the stage values below follow the visibility tables of those npcs. Jagex's `arenaquest_*`
 * varbits on `varp.arenaquest_secondary` carry the small flags (Sammy met, which opponent's
 * introduction was seen, door tried), so the quest needs no varps of its own.
 */
@Singleton
class FightArenaQuest @Inject constructor() :
    QuestScript(
        "quest_fightarena",
        "varp.arenaquest",
        rewards {
            xp("stat.attack", AttackXpReward)
            xp("stat.thieving", ThievingXpReward)
            item("obj.coins", CoinReward, label = "1,000 Coins")
        },
        ItemRewardDisplay(KhazardPlatemail),
        questVarbit = "varbit.fight_arena_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "Fight Arena end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at $Complete."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage(access.player) < stage) {
            quest.setQuestStage(access, stage)
        }
    }

    /** The opponent the player has to beat next, or null once the fights are over. */
    fun nextOpponent(stage: Int): Opponent? =
        when (stage) {
            SammyFreed,
            OgreFight -> Opponent.Ogre
            ScorpionFight -> Opponent.Scorpion
            ScorpionDefeated -> Opponent.Bouncer
            BouncerDefeated,
            KhazardFight -> Opponent.General
            else -> null
        }

    override fun subTitle(): String =
        "talking to <col=800000>Lady Servil</col> west of the <col=800000>Ardougne " +
            "Monastery</col>, just outside the north-east corner of the " +
            "<col=800000>Gnome Maze</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            val stage = stage(p)
            description(
                "<red>Lady Servil</red>'s husband <red>Justin</red> and son <red>Sammy</red> " +
                    "have been seized by <red>General Khazard</red>'s men and forced to fight " +
                    "in his arena. I have agreed to rescue them."
            )
            objective(
                "Khazard's guards will only let their own kind into the arena. There is a " +
                    "<red>chest</red> of guard armour in the north-eastern house south of the " +
                    "monastery."
            ) {
                visibleWhen { stage == Started }
            }
            objective(
                "I have the <red>Khazard armour</red>. If I wear the helmet and platebody the " +
                    "<red>door guards</red> should let me into the arena's prison."
            ) {
                visibleWhen { stage == ArmourTaken }
            }
            objective(
                "I am inside the prison wearing a disguise. <red>Sammy</red> is in the " +
                    "northernmost cell of the east wing, and I should ask him how to get the " +
                    "keys."
            ) {
                visibleWhen { stage == InPrison && !p.arenaMetSammy }
            }
            objective(
                "Sammy says the <red>Head Guard</red> holds the cell keys. He sits in the " +
                    "south-eastern corner of the prison, by the stairs."
            ) {
                visibleWhen { stage == InPrison && p.arenaMetSammy }
            }
            objective(
                "The Head Guard longs for a <red>Khali brew</red>, which sends a drinker to " +
                    "sleep. The <red>barman</red> west of the prison sells it for 5 coins."
            ) {
                visibleWhen { stage == HeadGuardBriefed }
                hasItem("khali_brew", "I have a Khali brew to give the Head Guard.")
                    .preserveObjective(strikeObjective = false)
            }
            objective(
                "The Head Guard is dozing and I have the <red>cell keys</red>. I should free " +
                    "<red>Sammy</red>."
            ) {
                visibleWhen { stage == HasKeys }
            }
            objective(
                "I freed Sammy and followed him into the arena, where an <red>ogre</red> was " +
                    "attacking his father."
            ) {
                visibleWhen { stage == SammyFreed || stage == OgreFight }
            }
            objective(
                "I beat the ogre, but <red>General Khazard</red> had me thrown into a cell. " +
                    "<red>Hengrad</red>, who shares it, might have something to say."
            ) {
                visibleWhen { stage == OgreDefeated }
            }
            objective(
                "Khazard has set a <red>scorpion</red> on me. I have to win my way through his " +
                    "arena to free the Servils."
            ) {
                visibleWhen { stage == ScorpionFight }
            }
            objective(
                "The scorpion is dead, and now the arena's champion, <red>Bouncer</red>, is " +
                    "after me."
            ) {
                visibleWhen { stage == ScorpionDefeated }
            }
            objective(
                "I killed Bouncer. <red>General Khazard</red> is furious and has come for me " +
                    "himself, though he released the Servils. I can fight him, or leave through " +
                    "the arena door."
            ) {
                visibleWhen { stage == BouncerDefeated || stage == KhazardFight }
            }
            objective(
                "The Servils are free and I have left the arena. I should tell <red>Lady " +
                    "Servil</red>."
            ) {
                visibleWhen { stage == KhazardEscaped || stage == KhazardBeaten }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Lady Servil asked me to rescue her husband Justin and her son Sammy from " +
                    "General Khazard's fight arena."
            )
            line(
                "I borrowed a set of Khazard guard armour, got the Head Guard drunk on Khali " +
                    "brew for his keys and freed Sammy."
            )
            line(
                "I fought off a Khazard ogre, a Khazard scorpion and the hellhound Bouncer to " +
                    "win the Servils' freedom, and Lady Servil rewarded me."
            )
        }

    enum class Opponent {
        Ogre,
        Scorpion,
        Bouncer,
        General,
    }

    companion object {
        const val Started = 1
        const val ArmourTaken = 2
        const val InPrison = 3
        const val HeadGuardBriefed = 4
        const val HasKeys = 5
        const val SammyFreed = 6
        const val OgreFight = 7
        const val OgreDefeated = 8
        const val ScorpionFight = 9
        const val ScorpionDefeated = 10
        const val BouncerDefeated = 11
        const val KhazardFight = 12
        const val KhazardEscaped = KhazardFight
        const val KhazardBeaten = 13
        const val Complete = 14

        const val AttackXpReward = 12175.0
        const val ThievingXpReward = 2175.0
        const val CoinReward = 1000
        const val KeyPrice = 5
        const val BeerPrice = 2
        const val RecommendedCombat = 50

        const val Coins = "obj.coins"
        const val KhazardHelmet = "obj.khazard_helmet"
        const val KhazardPlatemail = "obj.khazard_platemail"
        const val CellKeys = "obj.khazard_cellkeys"
        const val KhaliBrew = "obj.khali_brew"
        const val Beer = "obj.beer"
    }
}
