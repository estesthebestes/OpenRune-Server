package org.rsmod.content.quest.area.varrock.demonslayer

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess

/**
 * The incantation prompt after Delrith has been weakened. The world side (the weakened demon, the
 * circle, the music) is handed in through [Arena] so the decision and the quest completion can be
 * exercised on their own.
 */
@Singleton
class DelrithBanishment @Inject constructor(private val quest: DemonSlayerQuest) {

    interface Arena {
        /** Whether the weakened Delrith is still standing in the circle. */
        fun present(): Boolean

        fun playBanishEffects(access: ProtectedAccess)

        /** Takes Delrith off the field and puts the player back on the road outside. */
        fun leave(access: ProtectedAccess)

        /** Turns the weakened Delrith back into the full-strength one and resumes the fight. */
        fun restore(access: ProtectedAccess)
    }

    suspend fun ProtectedAccess.banish(arena: Arena) {
        val chosen = ArrayList<Int>(WORDS.size)
        startDialogue {
            chatPlayer(quiz, "Now what was that incantation again?")
            for (step in WORDS.indices) {
                val pick =
                    choice5(
                        WORDS[0], 0,
                        WORDS[1], 1,
                        WORDS[2], 2,
                        WORDS[3], 3,
                        WORDS[4], 4,
                        title = "Word ${step + 1} of the incantation",
                    )
                chosen += pick
                chatPlayer(angry, WORDS[pick] + if (step == WORDS.lastIndex) "!" else "...")
            }
        }
        if (chosen == quest.incantation(player)) {
            banished(arena)
        } else {
            wrong(arena)
        }
    }

    private suspend fun ProtectedAccess.banished(arena: Arena) {
        arena.playBanishEffects(this)
        mesbox("Delrith is sucked into the vortex...")
        mesbox("...back into the dark dimension from which he came.")
        arena.leave(this)
        mes("You scurry away from the enraged dark wizards having defeated the demon.")
        if (quest.stage(player) == DemonSlayerQuest.STAGE_KEY_HUNT) {
            quest.quest.advanceQuestStage(this)
        }
    }

    private suspend fun ProtectedAccess.wrong(arena: Arena) {
        soundSynth("synth.spellfail")
        mesbox("The vortex collapses. That was the wrong incantation.")
        if (arena.present()) {
            arena.restore(this)
        }
    }

    private companion object {
        val WORDS = DemonSlayerQuest.WORDS
    }
}
