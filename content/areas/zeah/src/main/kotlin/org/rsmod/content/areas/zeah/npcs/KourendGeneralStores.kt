package org.rsmod.content.areas.zeah.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.kourend.ClientOfKourend
import org.rsmod.content.quest.area.kourend.KourendStore
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KourendGeneralStores
@Inject
constructor(private val shops: Shops, private val clientOfKourend: ClientOfKourend) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(LEENZ) { startDialogue(it.npc) { leenz() } }
        onOpNpc3(LEENZ) { player.openLeenzShop(it.npc) }
        onOpNpc1(REGATH) { startDialogue(it.npc) { regath() } }
        onOpNpc1(MUNTY) { startDialogue(it.npc) { munty() } }
        onOpNpc1(JENNIFER) { startDialogue(it.npc) { jennifer() } }
        onOpNpc1(HORACE) { startDialogue(it.npc) { horace() } }
    }

    private fun Player.openLeenzShop(npc: Npc) {
        shops.open(this, npc, "Leenz's General Supplies", "inv.piscarilius_generalstore")
    }

    private suspend fun Dialogue.storeMenu(
        store: KourendStore,
        vararg options: Pair<String, Topic>,
    ): Topic {
        val topic = clientOfKourend.storeTopic(player, store)
        return menu(options.toList() + listOfNotNull(topic?.let { it to Topic.Quest }))
    }

    private suspend fun Dialogue.interview(store: KourendStore) =
        with(clientOfKourend) { storeInterview(store) }

    private suspend fun Dialogue.leenz() {
        chatNpc(happy, "A great adventurer, from afar? How may I help with my repertoire?")
        val topic =
            storeMenu(
                KourendStore.Piscarilius,
                "Let's trade." to Topic.Trade,
                "I don't need anything right now." to Topic.Leave,
            )
        when (topic) {
            Topic.Trade -> {
                chatPlayer(neutral, "Let's trade.")
                player.openLeenzShop(npcOrThrow())
            }
            Topic.Quest -> interview(KourendStore.Piscarilius)
            else -> chatPlayer(neutral, "I don't need anything right now.")
        }
    }

    private suspend fun Dialogue.regath() {
        chatNpc(neutral, "What do you require, traveller? I deal in many things.")
        while (true) {
            val topic =
                storeMenu(
                    KourendStore.Arceuus,
                    "Let's see what you've got." to Topic.Trade,
                    "Who are you?" to Topic.Who,
                    "I don't need anything." to Topic.Leave,
                )
            when (topic) {
                Topic.Trade -> return chatPlayer(neutral, "Let's see what you've got.")
                Topic.Who -> {
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(neutral, "My name is Regath, traveller.")
                    chatPlayer(quiz, "What is this place?")
                    chatNpc(
                        neutral,
                        "This place is where the mystical meets the mundane, where the magical " +
                            "realm reaches out to touch the ordinary world of commerce.",
                    )
                    chatNpc(
                        neutral,
                        "Or, to put it in terms you may find easier, this is a shop that sells " +
                            "magic things for money. I trade in various other goods as well.",
                    )
                }
                Topic.Quest -> return interview(KourendStore.Arceuus)
                Topic.Leave -> return chatPlayer(neutral, "I don't need anything.")
            }
        }
    }

    private suspend fun Dialogue.munty() {
        chatNpc(quiz, "Can I help you, human?")
        while (true) {
            val topic =
                storeMenu(
                    KourendStore.Lovakengj,
                    "Who are you?" to Topic.Who,
                    "Let's trade." to Topic.Trade,
                    "No, I'm fine." to Topic.Leave,
                )
            when (topic) {
                Topic.Who -> {
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        neutral,
                        "Why, this is my little shop. I stock all sorts of things the Lovakengj " +
                            "workers need. If it's pickaxes you're after, though, go and see " +
                            "Toothy in the sulphur mine.",
                    )
                    chatNpc(quiz, "Now, can I help you with anything today?")
                }
                Topic.Trade -> return chatPlayer(neutral, "Let's trade.")
                Topic.Quest -> return interview(KourendStore.Lovakengj)
                Topic.Leave -> return chatPlayer(neutral, "No, I'm fine.")
            }
        }
    }

    private suspend fun Dialogue.jennifer() {
        chatNpc(happy, "What're yer lookin' for, me luv'?")
        val topic =
            storeMenu(
                KourendStore.Shayzien,
                "Let's trade." to Topic.Trade,
                "I don't need anything right now." to Topic.Leave,
            )
        when (topic) {
            Topic.Trade -> {
                chatPlayer(neutral, "Let's trade.")
                chatNpc(
                    happy,
                    "Well, me luv', I've got some special rations for the soldiers out in the " +
                        "fields, courtesy o' the Void Knights on the mainland. 'Ave a look.",
                )
            }
            Topic.Quest -> interview(KourendStore.Shayzien)
            else -> chatPlayer(neutral, "I don't need anything right now.")
        }
    }

    private suspend fun Dialogue.horace() {
        chatNpc(
            happy,
            "Hello, I'm Horace. Welcome to my shop. Would you like to buy or sell something?",
        )
        while (true) {
            val topic =
                storeMenu(
                    KourendStore.Hosidius,
                    "Who are you?" to Topic.Who,
                    "Okay, let's trade." to Topic.Trade,
                    "No, I'm fine." to Topic.Leave,
                )
            when (topic) {
                Topic.Who -> {
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        happy,
                        "Hello, my friend, I'm Horace! This is the city of Hosidius. Nearly " +
                            "everyone round here works the farms. Without us, Kourend wouldn't " +
                            "have a single crop.",
                    )
                    chatNpc(quiz, "So, are you buying or selling anything today?")
                }
                Topic.Trade -> return chatPlayer(neutral, "Okay, let's trade.")
                Topic.Quest -> return interview(KourendStore.Hosidius)
                Topic.Leave -> return chatPlayer(neutral, "No, I'm fine.")
            }
        }
    }

    private fun Dialogue.npcOrThrow(): Npc = checkNotNull(npc)

    private enum class Topic {
        Who,
        Trade,
        Quest,
        Leave,
    }

    private companion object {
        const val LEENZ = "npc.piscarilius_generalstore_keeper"
        const val REGATH = "npc.arceuus_generalstore"
        const val MUNTY = "npc.lovakengj_generalstore"
        const val JENNIFER = "npc.shayzien_generalstore"
        const val HORACE = "npc.hosidius_generalstore"
    }
}
