package org.rsmod.content.quest.area.barbarianoutpost.barcrawl

/**
 * The ten bars on the Alfred Grimhand barcrawl card, in the order the card lists them. [varbit]
 * is the bar's signature bit, one flag each on `varp.barcrawl_state`.
 */
enum class BarcrawlBar(
    val varbit: String,
    val barName: String,
    val drinkName: String,
    val price: Int,
    val pitch: List<BarcrawlLine>,
    val brokeLine: String,
    val messages: List<String>,
    val effect: DrinkEffect,
    val afterwards: BarcrawlLine? = null,
) {
    BlueMoon(
        varbit = "varbit.barcrawl_signed_blue_moon",
        barName = "Blue Moon Inn",
        drinkName = "Uncle Humphrey's Gutrot",
        price = 50,
        pitch =
            listOf(
                BarcrawlLine.Npc(
                    "Oh no not another of you guys. These barbarian barcrawls cause too much " +
                        "damage to my bar."
                ),
                BarcrawlLine.Npc("You're going to have to pay 50 gold for the Uncle Humphrey's Gutrot."),
            ),
        brokeLine = "I don't have 50 coins.",
        messages =
            listOf(
                "You buy some Uncle Humphrey's Gutrot.",
                "You drink the Uncle Humphrey's Gutrot.",
                "Your insides feel terrible.",
                "The bartender signs your card.",
            ),
        effect =
            DrinkEffect(
                drained = listOf("stat.attack", "stat.defence", "stat.strength", "stat.smithing"),
                damagePercent = 5..8,
            ),
        afterwards = BarcrawlLine.Overhead("Blearrgh!"),
    ),
    Blurberry(
        varbit = "varbit.barcrawl_signed_blurberry",
        barName = "Blurberry Bar",
        drinkName = "Fire Toad Blast",
        price = 10,
        pitch =
            listOf(
                BarcrawlLine.Npc(
                    "Ah, you've come to the best stop on your list! I'll give you my famous Fire " +
                        "Toad Blast! It'll cost you 10 coins."
                )
            ),
        brokeLine = "I don't have 10 coins right now.",
        messages =
            listOf(
                "You buy a Fire Toad Blast.",
                "Your mouth and throat burn as you gulp it down.",
                "Blurberry signs your card.",
            ),
        effect = DrinkEffect(damagePercent = 8..12),
    ),
    DeadMansChest(
        varbit = "varbit.barcrawl_signed_dead_mans_chest",
        barName = "Dead Man's Chest",
        drinkName = "Supergrog",
        price = 15,
        pitch =
            listOf(BarcrawlLine.Npc("Haha time to be breaking out the old Supergrog. That'll be 15 coins please.")),
        brokeLine = "Sorry I don't have 15 coins.",
        messages =
            listOf(
                "The bartender serves you a glass of strange thick dark liquid.",
                "You wince and drink it.",
                "You stagger backwards.",
                "You think you see 2 bartenders signing 2 barcrawl cards.",
            ),
        effect =
            DrinkEffect(
                drained =
                    listOf("stat.attack", "stat.defence", "stat.herblore", "stat.cooking", "stat.prayer")
            ),
    ),
    DragonInn(
        varbit = "varbit.barcrawl_signed_dragon_inn",
        barName = "Dragon Inn",
        drinkName = "Fire Brandy",
        price = 12,
        pitch =
            listOf(BarcrawlLine.Npc("I suppose you'll be wanting some Fire Brandy. That'll cost you 12 coins.")),
        brokeLine = "Sorry I don't have 12 coins.",
        messages =
            listOf(
                "The bartender hands you a small glass and sets light to the contents.",
                "You blow out the flame and drink it.",
                "Your vision blurs and you stagger slightly.",
                "You can just about make out the bartender signing your barcrawl card.",
            ),
        effect = DrinkEffect(drained = listOf("stat.attack", "stat.defence")),
    ),
    FlyingHorseInn(
        varbit = "varbit.barcrawl_signed_flying_horse",
        barName = "Flying Horse Inn",
        drinkName = "Heart Stopper",
        price = 8,
        pitch =
            listOf(BarcrawlLine.Npc("Fancy a bit of Heart Stopper then do you? It'll only be 8 coins.")),
        brokeLine = "Sorry, I don't have 8 coins.",
        messages =
            listOf(
                "The bartender hands you a shot of Heart Stopper.",
                "You grimace and drink it.",
                "You clutch your chest.",
                "Through your tears you see the bartender...",
                "signing your barcrawl card.",
            ),
        effect = DrinkEffect(damagePercent = 20..30),
    ),
    ForestersArms(
        varbit = "varbit.barcrawl_signed_foresters_arms",
        barName = "Forester's Arms",
        drinkName = "Liverbane Ale",
        price = 18,
        pitch =
            listOf(
                BarcrawlLine.Npc(
                    "Oh you're a barbarian then. Now which of these barrels contained the " +
                        "Liverbane Ale? That'll be 18 coins please."
                )
            ),
        brokeLine = "Sorry, I don't have 18 coins.",
        messages =
            listOf(
                "The bartender gives you a glass of Liverbane Ale.",
                "You gulp it down.",
                "The room seems to be swaying.",
                "The bartender scrawls his signature on your card.",
            ),
        effect =
            DrinkEffect(
                drained =
                    listOf(
                        "stat.attack",
                        "stat.defence",
                        "stat.fletching",
                        "stat.firemaking",
                        "stat.woodcutting",
                    )
            ),
    ),
    JollyBoarInn(
        varbit = "varbit.barcrawl_signed_jolly_boar",
        barName = "Jolly Boar Inn",
        drinkName = "Olde Suspiciouse",
        price = 10,
        pitch =
            listOf(
                BarcrawlLine.Npc(
                    "Ah, there seems to be a fair few doing that one these days. My supply of Olde " +
                        "Suspiciouse is starting to run low, it'll cost you 10 coins."
                )
            ),
        brokeLine = "I don't have 10 coins right now.",
        messages =
            listOf(
                "You buy a pint of Olde Suspiciouse.",
                "You gulp it down.",
                "Your head is spinning.",
                "The bartender signs your card.",
            ),
        effect =
            DrinkEffect(
                drained =
                    listOf("stat.attack", "stat.defence", "stat.mining", "stat.crafting", "stat.magic")
            ),
        afterwards = BarcrawlLine.Player("Thanksh very mush...", slurred = true),
    ),
    KaramjaSpiritsBar(
        varbit = "varbit.barcrawl_signed_karamja_spirits",
        barName = "Karamja Spirits Bar",
        drinkName = "Ape Bite Liqueur",
        price = 7,
        pitch =
            listOf(
                BarcrawlLine.Npc(
                    "Ah, you'll be wanting some Ape Bite Liqueur then. It's got a lovely banana " +
                        "taste, and it'll only cost you 7 coins."
                )
            ),
        brokeLine = "I don't have 7 coins right now.",
        messages =
            listOf(
                "You buy some Ape Bite Liqueur.",
                "You swirl it around and swallow it.",
                "Zembo signs your card.",
            ),
        effect = DrinkEffect(),
        afterwards = BarcrawlLine.Player("Mmmmm, dat was luverly...", slurred = true),
    ),
    RisingSun(
        varbit = "varbit.barcrawl_signed_rising_sun",
        barName = "Rising Sun",
        drinkName = "Hand of Death Cocktail",
        price = 70,
        pitch =
            listOf(
                BarcrawlLine.Npc("Heehee, this'll be fun!"),
                BarcrawlLine.Npc(
                    "You'll be after our Hand of Death cocktail, then. Lots of expensive parts to " +
                        "the cocktail, though, so it will cost you 70 coins."
                ),
            ),
        brokeLine = "I don't have that much money on me.",
        messages =
            listOf(
                "You buy a Hand of Death cocktail.",
                "You drink the cocktail.",
                "You stumble around the room.",
                "%s giggles and signs your card.",
            ),
        effect =
            DrinkEffect(
                drained = listOf("stat.attack", "stat.defence", "stat.ranged", "stat.fishing"),
                damagePercent = 8..12,
                swayTicks = 3..8,
            ),
    ),
    RustyAnchor(
        varbit = "varbit.barcrawl_signed_rusty_anchor",
        barName = "Rusty Anchor",
        drinkName = "Black Skull Ale",
        price = 8,
        pitch =
            listOf(
                BarcrawlLine.Npc("Are you sure? You look a bit skinny for that."),
                BarcrawlLine.Player("Just give me whatever I need to drink here."),
                BarcrawlLine.Npc("Ok one Black Skull Ale coming up, 8 coins please."),
            ),
        brokeLine = "I don't have 8 coins with me.",
        messages =
            listOf(
                "You buy a Black Skull Ale.",
                "You drink your Black Skull Ale...",
                "Your vision blurs.",
                "The bartender signs your card.",
            ),
        effect = DrinkEffect(),
        afterwards = BarcrawlLine.Overhead("Hiccup!"),
    )
}

sealed class BarcrawlLine {
    abstract val text: String

    data class Npc(override val text: String) : BarcrawlLine()

    data class Player(override val text: String, val slurred: Boolean = false) : BarcrawlLine()

    data class Overhead(override val text: String) : BarcrawlLine()
}

/**
 * What a barcrawl drink does to the drinker: the listed stats fall by a few levels, and
 * [damagePercent] (of base Hitpoints) is taken as damage. [swayTicks] rocks the camera for a
 * random number of ticks in that range.
 */
data class DrinkEffect(
    val drained: List<String> = emptyList(),
    val damagePercent: IntRange? = null,
    val swayTicks: IntRange? = null,
)
