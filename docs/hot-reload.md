# Hot reload

Most gameplay tuning, data and content code can change while the server runs. Everything goes
through one system (`api/hot-reload`): a reload **prepares** on a background thread (reads files,
the database or compiled classes, and validates them) and only then **applies** on the game thread
at the start of the next tick. A reload that fails to prepare changes nothing.

## Commands

All commands need administrator rights.

| Command | What it does |
|---|---|
| `::reload` | Lists every reload target with its last result |
| `::reload <target>` | Reloads one target (see below) |
| `::reload all` | Reloads every target except `shops` and `code` |
| `::reload types build` | Runs `gradlew :or-cache:buildCache` first, then reloads types |
| `::xprate [name] <rate>` | Sets a player's personal XP rate (no args: show yours) |
| `::globalxp <rate>` | Updates the realm's global XP multiplier and reloads the realm |
| `::basexp <rate>` | Sets the XP rate new characters start with |
| `::skillxp <skill> <mult>` | Per-skill XP multiplier until restart or `::reload config` |
| `::droprate <mult>` | Server drop multiplier until restart or `::reload config` |
| `::rates` | Shows every live rate and active events |
| `::events` | Lists scheduled events from `game.yml` |
| `::hotswap [module] [build]` | Swaps in recompiled code; `build` compiles the module first |
| `::rescript <ScriptName>` | Re-runs one plugin script's `startup()` |

Player rates (`::xprate`) live in memory and are saved by the normal autosave, so they never need
a logout. Editing `account_characters.xp_rate_in_hundreds` in the database while a player is online
does **not** work: the next autosave writes the in-memory value back.

## Targets

| Target | Source | What becomes live |
|---|---|---|
| `realm` | `realms` table | base/global XP rate, login message and broadcast, spawn points, dev mode |
| `config` | `game.yml` | `gameplay.*`: drop multiplier, quest requirement mode, XP, regen, skilling, combat, world rates, events |
| `drops` | `content/drops/src/main/resources/drops/tables` | every TOML drop table (Kotlin `@RegisterDropTable` tables are kept) |
| `shootingstars` | `content/events/shooting-stars/src/main/resources/shootingstars.toml` | intervals and teleports (`is_enabled` needs a restart) |
| `spawns` | `.data/raw-cache/map/{npcs,objs}` | map npc and ground item spawns, applied as a diff |
| `types` | `.data/cache/SERVER` | server-only npc/item/loc/inv fields: stats, params (incl. woodcutting rates, shop links), costs, shop stock, loc content groups (doors, gates) |
| `typesrc` | `.data/raw-cache/server/**`, `content/**/pack/.../configs` | watch-only: rebuilds the cache, then applies `types` (same as `::reload types build`) |
| `shops` | — | resets every opened shared shop to its configured stock |
| `code` | `content/**` and `api/**` build output | recompiled classes; see "Fixing content live" |

`hot-reload.paths.<target>` in `game.yml` overrides where `drops`, `shootingstars`, `spawns-npcs`
and `spawns-objs` read from, which is how a release build (no source tree) can still reload them.

## Automatic reloads

```yaml
hot-reload:
  watch: true        # reload a target shortly after its files are saved
  debounce-ms: 500
  code: true         # enable ::hotswap and watch compiled classes (dev only)
```

With `watch: true`, saving `game.yml`, a drop table, a spawn file or the shooting star settings
reloads it automatically. Saving type data (for example a door entry in
`.data/raw-cache/server/loc/*.toml`, npc stats or shop stock) rebuilds the cache and applies it
(`typesrc`, 2 s quiet period, about a minute for the build). A finished `buildCache` reloads
`types` (3 s quiet period), and with
`code: true` every compile of a content or api module is hot swapped (1 s quiet period). These
settings themselves need a restart.

## Gameplay rates

Everything under `gameplay` in `game.yml` reloads with `::reload config`. The defaults match OSRS;
see `game.example.yml` for every key. Highlights:

- `xp.skill-multipliers` stacks with the player's rate and the realm global rate.
- `regen.*` changes health, stat, boost, special attack, run energy and prayer behaviour; interval
  changes re-arm the timers of players already online.
- `skilling.*`, `combat.*` and `world.*` scale success chances, resource respawn and depletion,
  player/npc damage (PvE only), npc aggression, npc respawns, loot visibility and shop restocking.
- `events` schedule temporary multipliers (double XP weekends and similar). They are checked every
  minute, overlap by multiplying, and announce when they start and end.

## Types: what can and cannot change live

`::reload types` copies only fields the client never sees. Each reload lists anything it skipped:

- **Copied:** npc combat stats, hunt/wander/attack ranges, respawn rate, regen rate, params; item
  costs, weight, tradeability, params; loc params and content groups (so a loc can become a door,
  gate or any other content-driven interactable live); shop stock, flags and (shared shops) size.
- **Needs a restart (and a client cache update):** names, options, models and animations, sizes,
  collision, new ids, inventory scope, weapon categories.
- Npcs in the world get new stats immediately when they are idle and unharmed; others on respawn.

## Fixing content live

Turn on `hot-reload.code`, edit a content or api file, then either compile from the IDE
(`Ctrl+F9`) / `gradlew :content:<path>:classes`, or run `::hotswap <module> build` in game.
Changed classes are redefined in place, new classes (including whole new scripts, such as a new
quest) are defined, and every script whose classes changed has its handlers removed and its
`startup()` run again. Player state (quest progress, varps) is untouched.

- **Any JDK:** changes inside existing methods and lambdas, plus brand new classes and scripts.
- **JetBrains Runtime only:** adding or removing methods, fields or non-suspend lambdas in an
  existing class. Install JBR 21, add `org.gradle.java.installations.paths=<jbr dir>` to
  `~/.gradle/gradle.properties`, and start with `gradlew run -Photswap`.
- A restart is still needed for: Guice module bindings (`PluginModule`), constructor dependency
  changes, `object`/companion initialisers that already ran, and cache or gamevals changes.
- In-flight dialogues and queued actions finish on the old code.

`gradlew run -Pdebug` also opens a debugger on port 5005 for IntelliJ's own HotSwap.

## Always needs a restart

Gamevals and new ids, anything the client renders (interfaces, CS2, models, map locs), dbtables
(mining rocks, cooking, slayer tasks and similar), ports, database and central settings, the world
id, `hot-reload` settings, and `@RegisterDropTable` Kotlin drop tables (unless hot swapped as code).
