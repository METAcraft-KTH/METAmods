# Traders

A server-side Fabric mod that adds three villager-like shopkeepers using Polymer. Players join with a vanilla client
and accept the server's required resource pack to see them. A trader stands where it is put, turns to look at players
within 8 blocks, and a right-click opens the vanilla trade screen with its offers from the config.

| Entity | Name | Sells |
| --- | --- | --- |
| `traders:data_trader` | Drifvare | `ovvar:data_ovve` |
| `traders:it_trader` | Punkphöz | `ovvar:it_ovve`, `ovvar:it_kisel_ovve` |
| `traders:media_trader` | RSA | `ovvar:media_frack` |

```mcfunction
/summon traders:data_trader ~ ~ ~
/summon traders:it_trader ~ ~ ~ {Rotation:[180f,0f],CustomName:"Ovve seller"}
```

`/traders spawn` (game master permission, or the `traders:traders` permission) spawns all three in a row in front of
you, data on your left and media on your right. The outer two are turned in towards the middle one, as if all three
were looking at one person standing 4 blocks in front of them.

## How it looks

Clients see a Blockbench model played by BIL (the same library the nether rifts' mobs use), with an interaction
box over it that sends clicks to the trader. They are not villagers and don't pretend to be one to the client:
a vanilla resource pack can only retexture villagers by type and profession, not by name tag, so a reskin that
only some villagers get isn't possible without a client mod. A display model has no such limit.

The models are `src/main/resources/model/traders/<data|it|media>_trader.bbmodel`. To change one, open the file in
Blockbench, edit and save it in place. Keep the bone named `head`, which BIL turns to face where the trader looks.
They have no animations.

## Trades

`config/traders.json5` is written on the first start:

```json5
{
  trades: {
    data: [{ price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:data_ovve" } }],
    it: [
      { price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:it_ovve" } },
      { price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:it_kisel_ovve" } },
    ],
    media: [{ price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:media_frack" } }],
  },
}
```

Each trader (`data`, `it`, `media`) has its own list; any other key is logged as an error and ignored, and the rest
of the file still loads. Each trade has a `price`, a `result`, and optionally a `second_price`, `max_uses` and `id`. Items
are registry ids, so any item from any installed mod works. A trade naming an item that isn't registered (a mod that
isn't installed) is left out, with a warning in the log when the server starts and after each `/reload`. The file
keeps it, so it comes back once the mod is installed again, and the default list is safe without ovvar (which the
mod recommends). `/reload` applies a changed file to the next screen opened.

Trades never run out by default. `max_uses` limits how many times each player can make a trade, ever: the count is
saved with the world (`<world>/data/traders/purchases.dat`), holds across screens and restarts, and never restocks. A used-up
trade shows crossed out on that player's screen. Uses are counted per trader under the trade's `id`, which defaults to
the result's item, so changing a price or reordering the list keeps everyone's count. A trader's trades selling the
same item share one count (one ovve per player, whichever price they pay); give one its own `id` to count it apart,
or a new `id` to start everyone's count again. Every purchase is counted, also of unlimited trades, so a limit added
later counts what players bought before it.

```json5
{ price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:data_ovve" }, max_uses: 1 }
```

There is no demand pricing, levels, restocking or experience. The screen has no level bar.

## Behaviour

Traders are invulnerable (only `/kill` and creative players can remove them), have no gravity so they stay where they
are put, even in mid-air, can't be pushed, knocked back or leashed, and never despawn.
Only one player trades with a trader at a time.

## Tests

`./gradlew :mods:traders:runClientGameTest` (needs a display, see the other modules' notes on running it on
Xvfb) starts a vanilla client, visits each trader, checks that its model shows and that its trade screen has its offers, buys an offer, and checks that the
one-use offer is used up when the screen opens again. `./gradlew :mods:traders:test` checks the config file and the
saved purchase counts.
