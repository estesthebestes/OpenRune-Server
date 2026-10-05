# Grand Exchange

A working Grand Exchange for single-player and small private servers. Players trade against other
players' offers first and then against a simulated market priced from the real OSRS Wiki prices.

Code lives in two places:

- `api/grand-exchange`: the domain (prices, tax, order book, matching, fill models, slot codec).
  No client or UI dependencies; unit-tested with fakes and without a cache or a network.
- `content/interfaces/grand-exchange-ui` (+ `pack`): clerks, booths, the `ge_offers`,
  `ge_offers_side`, `ge_collect` and `ge_history` interfaces, and the adapter that backs a `Player`
  with varps and inventories. (Not named `grand-exchange`: Gradle would give it the same
  coordinates as the api module and the build would loop.)

## How a trade works

1. **Escrow.** Confirming an offer takes the coins (buy) or the items (sell, noted or not) from the
   inventory in one transaction. Nothing else moves until a fill happens.
2. **Players first.** The new offer is matched against opposing offers of other *online* players,
   best price first and oldest first among equals. The trade happens at the price of the offer that
   was already resting, like OSRS: a buyer who bid 1,600 against a resting sell at 1,500 pays 1,500
   and is refunded 100 per item.
3. **Then the market.** What is left trades against the simulated market if its price crosses the
   market price, as decided by the `FillModel`. The instant model fills everything at once: a buy
   at or above the wiki `high` fills at `high`, a sell at or below the wiki `low` fills at `low`
   (the difference is refunded or paid out as extra coins).
4. **Otherwise it rests.** The offer stays open, in the order book while the player is online.
   Resting offers are re-checked against the market whenever new prices arrive and at login.
5. **Collection.** Everything earned or returned lands in the slot's collection box: the item (always
   unnoted) in slot 0, coins in slot 1, platinum tokens in slot 2. A finished slot is free again once
   its box is empty.

The fee is charged to the seller per item at the fill price: `floor(price * rate)` capped at
`cap` per item, so items under 50 coins pay nothing. The exempt items from the wiki (bonds, low level
food and ammo, teleport tablets, basic tools, energy potions) are matched by name in
`TaxExemptItems`.

`MarketQuote.buyAt` is the wiki `high`, `sellAt` the wiki `low`. When only one side is known the
other copies it; when neither is known the item's cache `cost` is used. The "Guide price" button
sets the price that fills immediately for the side being set up (`high` for buys, `low` for sells).

### Limits of v1

- **Players match only while online.** The order book holds the open offers of online players. An
  offline player's offers are not in it, so another player's offer cannot match them. They rejoin the
  book at login (time priority restarts at login) and are checked against any new prices then.
- **No buy limits.** `BuyLimits` is a seam with a no-op implementation (`NoBuyLimits`); the wiki
  limits are already cached (`GePrices.buyLimit`) and the cache has `stockMarketBuyLimit`, so a real
  limit only needs a new implementation bound in its place.
- **Instant fills only.** `gameplay.grand-exchange.fill-model` accepts `instant`. A volume-paced
  `gradual` model implements `FillModel` (`marketFill` + `shouldSweep`) and is added to `FillModels`;
  nothing else changes.
- Ironmen are refused (`IronmanRestrictions.block(player, IronmanActivity.GRAND_EXCHANGE)`). Item
  sets (the clerk's "Sets" option) are not implemented. Prices are capped at 2,147,483,647 per item.
- The history keeps the last 10 finished trades.

## Prices

`api/grand-exchange/price`: `WikiPriceFeed` reads `/latest` (and `/mapping` for buy limits, at most
once a day) from `prices.runescape.wiki` with the configured `User-Agent`, on a daemon thread every
`refresh-minutes`, never on the game thread. Every good answer is written atomically to
`snapshot-path` and restored at boot, so a restart or an offline machine keeps working prices. The
order is: this run's live fetch, then the restored snapshot, then the item's cache `cost`.

`MarketPrices` (used by examine, the drop warning threshold, shop sell values, the guide price
interface and items kept on death) now asks a `MarketPriceSource` set before falling back to the cache
cost. The exchange contributes the mid-point of the wiki high and low. Switching it changes examine
text and risk values to real prices, and it makes the items-kept-on-death ordering follow GE prices
as on OSRS. With `enabled: false`, or for an item without a wiki price, nothing changes.

## Config

`game.yml`, under `gameplay.grand-exchange` (see `game.example.yml`); read at startup:

| Key | Default | |
|---|---|---|
| `enabled` | `true` | `false` closes the exchange and keeps `MarketPrices` on the cache cost |
| `fill-model` | `instant` | unknown names log a warning and fall back to `instant` |
| `prices.live` | `true` | `false` never touches the network; only the snapshot is used |
| `prices.refresh-minutes` | `5` | the wiki asks not to be hammered |
| `prices.snapshot-path` | `.data/ge/prices.json` | |
| `prices.user-agent` | `OpenRune-Server GE price feed (private server) - github.com/OpenRune` | |
| `tax.rate` | `0.02` | per item, rounded down; the client shows it from `ge_transmit_taxrate` (tenths of a percent, 0-51.1%) |
| `tax.cap` | `5000000` | per item |

## State and persistence

Following `AGENTS.md`, state lives in permanent varps and inventories, with no central database
table. Packed in `content/interfaces/grand-exchange-ui/pack/.../grand_exchange.toml`:

- `varp.ge_slots_0..38` (ids 64100-64139 except 64132, which is `varp.barrows_state`): the eight
  slots as one bit-packed stream (`SlotCodec`: 153 bits per slot: state, type, item, quantity, price,
  completed quantity, gold; slots may straddle two varps). Transmit `Never`.
- `varp.ge_tax_slot_long_0..7` (cache ids 5754-5761): the fee paid so far per slot. They are `long`
  on the client, so they are kept as saturating ints on the server and sent with `VarpLong`.
- `inv.tradingpost_sell_0..5`, `inv.ge_collect_6`, `inv.ge_collect_7` (cache invs, ids 518-523,
  539, 540): the collection boxes. The pack only makes them `Perm`, `Always` stacking and
  unprotected. The offers interface already reads them through `enum_150`.
- `inv.ge_history` (custom id 1102, 40 slots): the history ring, four stacks per trade.

Escrow is implied by the slot numbers (an open buy holds `remaining * price` coins, an open sell holds
`remaining` items), so a slot record is the only source of truth for what is held.

A match updates both players' slots and boxes in one synchronous step on the game thread
(`GrandExchange.place` / `sweep` / `join`). Tests assert that coins and items are conserved across
long random sessions, with the market and the fee as the only sources and sinks.

## How the client UI is driven

The 241 `ge_offers` scripts apply every button to their own copy of the setup varps and also send
the op to the server. The server is authoritative: it applies the same change (`OfferMath` mirrors
the clientscript arithmetic) and writes the result back.

| Client state | Where | Written by the server |
|---|---|---|
| `varbit.ge_selectedslot` (4-7 of `armourhitsound`), `ge_transmit_taxrate` (20-28) | slot shown / fee rate | yes |
| `varbit.ge_newoffer_quantity` (0-30 of `bankpin_2`), `ge_newoffer_type` (31) | setup panel | yes |
| `varbit.ge_price_custom` (13-19 of `bank_extratab`) | the `-X%`/`+X%` percentage | yes |
| `varp.tradingpost_search` (obj) | item being set up | yes |
| `varplayer_5753` (long, no gameval name) | price being set up | `VarpLong`; the int copy is a player attribute |
| `varp.ge_last_offer_*` | "Repeat Offer" | yes |
| `UpdateStockMarketSlotV2` | `stockmarket_*` clientscript state per slot | on login, open and every change |
| invs `tradingpost_sell_0..5`, `ge_collect_6/7` | collection boxes | inventory transmit |

Long varps (the price, the per-slot fee and item sink prices) need a build-time fix. The CS2
compiler emits `push_var` / `pop_var` for every varp, but the client reads and writes long varps
with `push_var_long` / `pop_var_long` (opcodes 64 / 65). Left as compiled, any script touching one
aborts on an empty long stack, which blanks the setup price, total and Confirm label and the fee
line of the status view. `or-cache`'s `LongVarpPatch` task runs after `PackCs2` in `buildCache` and
rewrites those opcodes in the packed scripts, using the `long` entries in `symbols/varp.sym`.

The status byte of an `UpdateStockMarketSlotV2` packs the state in its low 3 bits and a sell flag
in bit 3. The scripts only distinguish 0 (empty), 1 (pending), 2 (in progress) and 5 (finished), so
completed and aborted offers are both sent as 5; any other value reads as an offer still running.

The op protocol (comsub = the child index the clientscripts create with `cc_create`):

- `ge_offers:index_N` child 2: op1 view, op2 abort, op3 modify. Child 3 op1: create buy offer
  (opens the item search). Child 4 op1: create sell offer.
- `ge_offers:setup` children 1-7 quantity (`-1`, `+1`, `+1`, `+10`, `+100`, `+1K`/`All`, enter/`All`),
  8-15 price (`-1`, `+1`, `-5%`, guide, enter, `+5%`, `-X%`, `+X%`; op2 on the last two customises).
  `setup_confirm` places the offer.
- `ge_offers:details_status` child 0 abort, child 1 modify. `details_collect` children 2 (item) and 3
  (coins): item ops follow the client (`Collect-notes`/`Collect-items`, `Bank`, `Examine`).
- `ge_offers:collectall` child 0 collect to inventory / bank, child 1 repeat offer.
  `ge_offers:history` opens `ge_history`; `ge_history:exchange` goes back.
- `ge_offers_side:items` (the inventory): op1 offers the item while a sell offer is being set up.
- `ge_collect:collect_N` children 3 (item) and 4 (coins), `collect_inv` / `collect_bank`.

The server enables those ranges with `IfSetEvents`; without that the engine drops the ops. The
history window is filled with `ge_history_init` / `ge_history_addline` / `ge_history_finish`.

Entry points: `npc.ge_clerk_1..4` op1 (talk), op3 (Exchange), op4 (History), op5 (Sets: not
available); `loc.exchange_bank_wall_exchange` op1 (Exchange); op3 (Collect) on the three Grand
Exchange booth locs. Bankers' "Collect" already opens `ge_collect` and works with the same script.

## Verifying in a client

See the checklist in the task report; the short version: open at a clerk, buy at, under and over the
market price, sell, abort, collect (notes, items, bank), history, trade between two players,
ironman refusal, and restart the server offline (`prices.live: false`) to confirm the snapshot is
used.

## Debugging the offer price

The client cannot send a var back to the server, so the price (and quantity) in the setup panel is
mirrored: every button op runs the same arithmetic on the server (`OfferMath`) and the server then
writes its absolute value to `varplayer_5753`. `confirm` places the offer with that server value.
`GeOfferFlowTest` drives the real handlers against a port of the client's arithmetic to keep the two
in step. With the `org.rsmod.content.interfaces.grandexchange` logger at `DEBUG` the live log shows:

- `GE button: ...` every op, with the price and quantity held when it arrived;
- `GE price: ... source=<+5%|-5%|+1|-1|guide|enter|+10%x|guide-on-select|restore> before=.. stored=..
  sentToClient=..` every time the price is set and mirrored to the client;
- `GE quantity: ... before=.. after=..`;
- `GE confirm: ... price=..` and `GE placed: ... price=.. filled=.. state=..` for the offer as sent to
  the exchange and as it was stored (state `OPEN` is a resting offer).

If the price shown in the client ever differs from the `GE confirm` line, look for a `GE button`
line missing for the last click (the engine drops modal buttons while a script is suspended).
