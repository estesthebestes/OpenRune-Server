# Chat commands

Every `::` command the server ships with, grouped by what you'd use it for. Type a command in the
normal chat box, starting with `::`, and press Enter, e.g. `::item coins 1000000`. The server
replies in your chat box.

- **Who can use them:** almost every command needs **administrator** rights. A player without them
  gets no reply at all. A few commands work for everyone; they are marked *(any player)*.
- **Getting admin rights:** rights come from the `rights` column of your row in the `accounts`
  table. Set it to `ADMINISTRATOR` (or `ADMIN`) and log in again:

  ```sql
  UPDATE accounts SET rights = 'ADMINISTRATOR' WHERE lower(account_name) = 'yourname';
  ```

  When the realm has `dev_mode = 1` in the `realms` table, players who log in are made
  administrators automatically. That is handy on a local test server; turn it off on a public one.
- **In-game help:** `::commands` lists every command you can use with its description, and
  `::commands <text>` filters that list by name or description (e.g. `::commands xp`).
- **Typing rules:**
  - Commands are not case sensitive. The whole line is lower-cased before it runs.
  - Put exactly one space between arguments.
  - `[x]` means optional and `<x>` means required. Don't type the brackets.
- **When something goes wrong:** a wrong number or a missing argument prints the command's usage
  line. A name that doesn't exist (an item, npc, varp...) usually prints
  `Uncaught exception! Please report this to an Administrator.` That just means the name is wrong.
- **Ticks:** durations are in game ticks. One tick is 0.6 seconds, so 100 ticks is 1 minute.

For reload details (what each target covers, what needs a restart) see
[hot-reload.md](hot-reload.md). For loading outside plugins see
[external-plugins.md](external-plugins.md).

## Finding names and ids

Commands take either a number id or an internal **name** (a "gameval"). Internal names are Jagex's
own names, so they are lower-case with underscores and often differ from what you see in game:

| You see in game | Internal name | Id |
|---|---|---|
| Coins | `coins` | 995 |
| Abyssal whip | `abyssal_whip` | 4151 |
| Fire rune | `firerune` | 554 |
| Prayer potion(4) | `4doseprayerrestore` | 2434 |
| Fire cape | `tzhaar_cape_fire` | 6570 |
| Armadyl godsword | `ags` | 11802 |
| Shark (noted) | `cert_shark` | 386 |

Ways to find them:

- **Items:** the easiest route is the item's id from the [OSRS Wiki](https://oldschool.runescape.wiki)
  (the "Item ID" line in the infobox), then `::item 4151`. Or skip names entirely and use
  `::spawn`, which searches by the normal in-game name.
- **Noted items:** the noted version is `cert_` + the name (`cert_shark`), or the wiki's noted id.
- **NPCs:** `::npc` only takes names. To turn a wiki NPC id into its name, run `::transmog <id>`.
  The reply shows the name (`::transmog 3106` replies `npc.man`). Then run `::transmog` with
  nothing after it to change back.
- **Interfaces:** `::interface <id>` opens it and shows its name.
- **Everything else** (objects, animations, varps, varbits, sounds): the names live in the
  server's gameval tables. `.data/gamevals/*.rscm` are plain text `name=id` files, but they only
  hold a small set of extra names. The full lists for items, npcs, objects, interfaces, varps and
  varbits are in the binary `.data/gamevals-binary/gamevals.dat`. The repo's `tools/osrs-mcp` tool
  has a `gameval_search` lookup over all of them (see its README), or look at how existing content
  code refers to a thing (e.g. `"varp.cookquest"` means the name `cookquest`).
- **Name prefixes:** type names *without* their table prefix: `::item coins`, not
  `::item obj.coins`. `::synth` and `::skillxp` also accept the prefixed form.
- **Coordinates:** stand on the tile and run `::mypos`.

## Items & inventory

| Command | What it does | Example |
|---|---|---|
| `::item <name or id> [amount]` | Spawns an item into your inventory. A multi-word name can be typed with spaces (`abyssal whip` = `abyssal_whip`). Amount defaults to 1 and must be a plain number (no `k`/`m`). If the name itself ends in a number, use underscores (`amulet_of_glory_4`), otherwise the number is read as the amount. Non-stackable items stop when the inventory is full. Also `::invadd`. | `::item coins 1000000`, `::item 4151`, `::item cert_shark 500` |
| `::spawn` | Opens the item spawn window. Search by in-game name, pick 1 / 100 / 1000 / X, click an item. Toggles send items noted or straight to your bank. Too many non-stackables are noted automatically. | `::spawn` |
| `::spawnold` | Older spawner: item search box, then an amount prompt, repeating until you close it. | `::spawnold` |
| `::craftmat <name> [crafts]` | Spawns the materials (and any missing tools) to craft an item `crafts` times (default 1). Only Crafting recipes. | `::craftmat red_dragonhide_body 6` |
| `::pet [name]` | With no name, pick a pet from a menu. With an item name, awards that pet through the normal pet rules. | `::pet`, `::pet hell_pet` |
| `::invclear` | Deletes everything in your inventory (not equipment or bank). There is no undo. | `::invclear` |
| `::openbank` | Opens your bank anywhere. | `::openbank` |

## Movement & teleport

| Command | What it does | Example |
|---|---|---|
| `::tele <x> <y> [level]` | Teleports to a tile. Level is 0 (ground) to 3, default 0. `x,y,level` with commas also works. | `::tele 3222 3218 0`, `::tele 3164,3487` |
| `::mypos` | Prints your tile (x y level) plus zone, map square and build area. | `::mypos` |
| `::up [n]` / `::down [n]` | Moves you up/down `n` floors (default 1) on the same tile. | `::up`, `::down 2` |
| `::forward [n]` | Teleports `n` tiles north (default 1). | `::forward 10` |
| `::backwards [n]` | Teleports `n` tiles south. Also `::back`. | `::back 5` |
| `::left [n]` / `::right [n]` | Teleports `n` tiles west / east. | `::right 3` |
| `::telezone <zoneX> <zoneY> <level>` | Teleports to the corner of a zone (8x8 tile block). Mostly for developers. | `::telezone 400 400 0` |
| `::instanceexit <key>` | Teleports to an instanced boss's exit tile. With no key it lists the known keys. | `::instanceexit callisto` |

Admins can also click the world map to teleport there.

Handy coordinates:

| Place | Command |
|---|---|
| Lumbridge (spawn) | `::tele 3222 3218 0` |
| Varrock square | `::tele 3213 3424 0` |
| Grand Exchange | `::tele 3164 3487 0` |
| Edgeville bank | `::tele 3094 3491 0` |
| Falador park | `::tele 2965 3380 0` |
| Draynor Village | `::tele 3093 3244 0` |
| Camelot | `::tele 2757 3477 0` |

## Character & stats

| Command | What it does | Example |
|---|---|---|
| `::master` | Sets every skill to 99. | `::master` |
| `::reset` | Sets every skill to 1 (Hitpoints to 10). | `::reset` |
| `::god` | Toggles god mode (you take no damage). | `::god` |
| `::maxhit` | Toggles always hitting your max. | `::maxhit` |
| `::spellbook <book>` | Switches spellbook: `standard`, `ancients`, `lunars` or `arceuus`. Also `::book`. | `::book lunars` |
| `::gamemode <mode>` | Sets the account type: `normal`, `ironman`, `uim` or `hcim`. Saved on logout. | `::gamemode ironman` |
| `::transmog [npc name or id]` | Makes you look like an NPC. With nothing after it, changes you back. | `::transmog goblin`, `::transmog` |
| `::die <pvm or pvp> [true]` | Kills you to test death. `true` makes it count as a Wilderness death. **You really lose items** like a normal player would, so bank first. `pvp` uses you as the killer. | `::die pvm`, `::die pvp true` |
| `::poison <damage> [severity]` | Poisons you starting at `damage`. Or use 0 and a raw severity. | `::poison 6` |
| `::venom` / `::venomclear` | Envenoms you / cures venom. | `::venom` |
| `::disease [drain]` / `::diseaseclear` | Diseases you, draining `drain` per tick (default 3) / cures it. | `::disease 5` |
| `::discordlink [discord name]` | *(any player)* Gives you a code and sends a Discord DM to link your account. Defaults to your username. You must be in the Discord server. | `::discordlink myname` |

## XP & rates

| Command | What it does | Example |
|---|---|---|
| `::rates` | Shows every live rate: XP (personal, global, event, per skill), regen, run, prayer, skilling, combat, respawn, drops and active events. | `::rates` |
| `::xprate [player] [rate]` | No arguments: shows your XP rates. With a rate: sets your personal rate. With a name first: sets that online player's rate (use spaces or underscores in the name). Rate is above 0 and at most 1000, `x` allowed. Saved with the character. | `::xprate 5`, `::xprate some_player x10` |
| `::globalxp [rate]` | Shows or sets the realm-wide XP multiplier for everyone. **Writes the `realms` table in the database**, so it survives restarts. | `::globalxp 2` |
| `::basexp [rate]` | Shows or sets the starting XP rate for **new** characters. Writes the database; existing characters keep theirs. | `::basexp 10` |
| `::skillxp [skill] [mult]` | No arguments: lists per-skill multipliers. Otherwise sets one skill's multiplier (e.g. `woodcutting`, `runecrafting`). `1` clears it. Lasts until restart or `::reload config`; put it in `game.yml` to keep it. Skill names are not checked, so a typo silently does nothing. | `::skillxp mining 3` |
| `::droprate [mult]` | Shows or sets the server drop multiplier. It applies to drops marked as boostable (2 turns 1/512 into 1/256). Lasts until restart or `::reload config`. | `::droprate 2` |
| `::events` | Lists scheduled events (double XP weekends etc.) from `game.yml` with their state. | `::events` |

The final XP rate is personal x global x event x skill. To change rates permanently, edit
`gameplay` in `game.yml` and run `::reload config` (see [hot-reload.md](hot-reload.md)).

## World, NPCs & objects

| Command | What it does | Example |
|---|---|---|
| `::npc <ticks> <name>` | Spawns an NPC on your tile for `ticks`. It stands still. Name only (no ids, use underscores). Also `::npcadd`. | `::npc 500 goblin` |
| `::npcgrid <name> [ticks]` | Spawns a 3x3 grid of an NPC just north of you (default 500 ticks). Good for testing area attacks. | `::npcgrid goblin 1000` |
| `::object <ticks> <name> [angle] [shape]` | Spawns an object on your tile. Angle 0-3, shape defaults to 10 (a normal object). Name only. Also `::locadd`. | `::object 100 bookcase` |
| `::objectdel <ticks> [shape]` | Removes the object on your tile for `ticks`, then it comes back. Also `::locdel`. | `::objectdel 100` |
| `::testloot <npc name> [count]` | Rolls an NPC's death drops `count` times (default 100) without fighting. Loot lands on your tile. | `::testloot godwars_bandos_avatar 100` |
| `::star [location]` | Starts a shooting star crash at a random site (`::star` or `::star any`) or at a site key such as `mining_guild`, `dwarven_mine`, `catherby_bank` or `hosidius_mine` (full list in `ShootingStarsTable.kt`). Only exists while shooting stars are enabled. | `::star`, `::star mining_guild` |
| `::gemstonecrab` | Forces the active Gemstone Crab to burrow now. | `::gemstonecrab` |

## Hot reload & server

| Command | What it does | Example |
|---|---|---|
| `::reload` | Lists every reload target with when it last ran and whether it worked. | `::reload` |
| `::reload <target>` | Reloads one target: `realm`, `config`, `drops`, `shootingstars`, `spawns`, `types`, `shops`, `code`. | `::reload drops` |
| `::reload all` | Reloads every target except `shops` and `code`. | `::reload all` |
| `::reload types build` | Rebuilds the cache first, then reloads types. | `::reload types build` |
| `::hotswap [module] [build]` | Swaps recompiled content code into the running server. `build` compiles the named module first. Needs `hot-reload.code: true` in `game.yml`. | `::hotswap`, `::hotswap generic-npcs build` |
| `::rescript <ScriptName>` | Re-runs one content script's startup. | `::rescript LumbridgeScript` |
| `::slowreboot <ticks>` | Starts the "System update in..." countdown for everyone. At zero all players are logged out and logins are refused; then restart the server yourself. `0` cancels the countdown. Max 65535. | `::slowreboot 500` |
| `::reboot` | Stops the server process **immediately**. It only comes back if you (or a process manager) start it again. | `::reboot` |
| `::plugins` | Lists outside plugins in `plugins/` with a menu to enable, disable, load or reload them. | `::plugins` |
| `::loadplugin <name>` | Loads (or reloads) a plugin by its file or folder name. Same as `::pluginreload`. | `::loadplugin example-plugin` |
| `::pluginenable <name>` | Marks a plugin enabled and loads it if it isn't running. | `::pluginenable my-plugin` |
| `::plugindisable <name>` | Unloads a plugin and keeps it off after restarts. | `::plugindisable my-plugin` |

Reload targets in short (full table in [hot-reload.md](hot-reload.md)):

| Target | Reloads |
|---|---|
| `realm` | XP rates, login message, spawn points and dev mode from the `realms` table |
| `config` | `gameplay` settings in `game.yml` (rates, regen, events...) |
| `drops` | TOML drop tables |
| `shootingstars` | Shooting star schedule (`shootingstars.toml`) |
| `spawns` | Map NPC and ground item spawns |
| `types` | Server-side NPC/item/object stats, params and shop stock from the built cache |
| `shops` | Resets every shared shop to its starting stock |
| `code` | Recompiled code (same as `::hotswap`) |

## Debug

| Command | What it does | Example |
|---|---|---|
| `::anim <name>` | Plays an animation on you. Name only. | `::anim emote_wave` |
| `::spot <name> [height]` | Plays a graphic (spotanim) on you. Name only. | `::spot fx_emote_party01_active` |
| `::synth <id or name>` | Plays a sound effect. | `::synth 2277`, `::synth pillory_wrong` |
| `::npcanim <anim id> [radius]` | Plays an animation on the nearest NPC within `radius` tiles (default 12). Ids only. Also `::nanim`. | `::npcanim 7018` |
| `::npcspot <spotanim id> [height] [radius]` | Plays a graphic on the nearest NPC. Ids only. Also `::nspot`. | `::npcspot 1216 96` |
| `::npcproj <travel id> [impact id] [radius] [projectile id]` | Fires a projectile from the nearest NPC at you. Ids only. Also `::nproj`. | `::npcproj 1202 1203` |
| `::interface <id or name>` | Opens an interface. Also `::ifopen`. | `::interface 12`, `::interface bankmain` |
| `::componentdebug` | Toggles printing info about every interface button you click. | `::componentdebug` |
| `::getvarp <name>` | Shows a varp's value. Name only. | `::getvarp cookquest` |
| `::varp <name> <value>` | Sets a varp. | `::varp cookquest 1` |
| `::getvarbit <name>` | Shows a varbit's value. Name only. | `::getvarbit emote_hotline_bling` |
| `::varbit <name> <value>` | Sets a varbit. | `::varbit emote_hotline_bling 1` |

Varp and varbit changes edit the player's real state (quest progress, settings), so note the old
value with `::getvarp` / `::getvarbit` before changing it.

## Common recipes

**Give yourself money and supplies**

```
::item coins 10000000
::item cert_shark 1000
::item 4doseprayerrestore 4
::openbank
```

**A full set of gear**

```
::item rune_full_helm
::item rune_platebody
::item rune_platelegs
::item rune_kiteshield
::item abyssal_whip
::item amulet_of_glory_4
::item dragon_boots
```

Or open `::spawn`, turn on the Bank toggle and click through the items you want.

**Max stats and get back to normal**

```
::master
::reset
```

**Teleport around**

```
::tele 3222 3218 0
::tele 3213 3424 0
::tele 3164 3487 0
```

Lumbridge, Varrock square and the Grand Exchange.

**Test a quest step.** Quest progress is stored in a varp or varbit. Cook's Assistant uses the varp
`cookquest`: 0 = not started, 1 = started, 2 = finished.

```
::getvarp cookquest
::varp cookquest 1
```

**Change the XP rate**

```
::xprate 10          your own rate
::globalxp 2         everyone, saved in the database
::skillxp prayer 5   one skill, until restart or ::reload config
::rates              check the result
```

**Reload drops after editing a drop table.** Save the TOML file under
`content/drops/src/main/resources/drops/tables`, then:

```
::reload drops
::testloot godwars_bandos_avatar 100
```

With `hot-reload.watch: true` in `game.yml` the reload happens on its own when you save.
