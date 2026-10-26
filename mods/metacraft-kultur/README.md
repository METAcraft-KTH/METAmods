# Kultur

METAmods module `mods/metacraft-kultur` (mod id `metacraft-kultur`, namespace `kultur`). Chapter culture for vanilla clients:
banner and shield patterns for the chapters and their clubs, paintings, and drinks. Fabric +
[Polymer](https://polymer.pb4.eu), Minecraft 26.3, Java 25. Players need only the auto-served
resource pack; no client mod. The successor of PolymITer (the IT chapter's 1.21.10 mod this content
comes from); the ovvar and patches from there live in `mods/ovvar`.

## What is here, and what is not

IT's content: ITK, QMISK and TMEIT banner patterns free in any loom, the Pirkko pattern behind a
pattern item ("gated", below — `kultur:pirkko_banner_pattern`, from creative or `/give` for now),
Emelie Stark's painting "The Guardian of Kistan" (3×4), and PolymITer's four drinks (below). Data
and Media have their places in the catalogue and nothing in them yet — that is what this file is for.

Not here yet, on purpose: loot tables for the pattern items, any Data or Media art.

With `moredyes` on the same server every pattern here also comes in Cerise and Laserviolet: its dyes
work in the vanilla loom; nothing in this module knows about that. A Cerise Pirkko needs the Pirkko
pattern item in the loom, as a white one does.

## The drinks

PolymITer's four IT drinks are made with [Patbox's Brewery](https://modrinth.com/mod/brewery), and only
with it: without Brewery on the server there is no way to make them. Kultur never depends on it; it
looks once, at start-up, and only one of the two modes below is ever active.

### With Brewery

Brewed with Brewery: cook the
ingredients in a water cauldron over fire, a campfire or lava, take the brew out with a glass bottle, and distil the
two that say so in a brewing stand. No barrel ageing.

| drink | cauldron | cook | then | alcohol |
| --- | --- | --- | --- | --- |
| Alcoholic Beverage | 4 potatoes, 2 sugar | 10 min | — | 20 |
| Spiken Patch Drink | 4 potatoes, 4 sweet berries | 8 min | distil once | 30 |
| Släggan Patch Drink | 4 potatoes, 1 suspicious stew | 8 min | distil once | 35 |
| Nyckeln Patch Drink | 2 apples, 1 sugar | 2 min | — | 0 |

Each alcoholic one sends you fast or slow for a minute or more (Släggan: slow three times in four,
with strength once you are drunk, else fast with weakness), harder the drunker you are. Brewery's
own drunkenness (stagger, nausea, poisoning) comes on top. Past 100 alcohol a kultur drink blacks you
out, as PolymITer's fourth drink did: you come to somewhere within 250 blocks with a headache, blind
for ten seconds and a little hurt, at 30 alcohol. Brewery counts a poor brew as stronger than it is,
so bad ones get you there sooner.

The drinks are data, `src/main/resources/data/kultur/brewery_drinks/*.json` (Brewery's format; their
bottles, PolymITer's models, are `assets/kultur/items/drink/`), which only Brewery reads. Four
potatoes, not six, and a second ingredient each, because a cauldron offers
every drink whose ingredients are all in it and Brewery's vodka is six potatoes.
`KulturDrinkTests` checks every recipe brews only its own drink.

Brewery 0.17 on 26.3 cannot find its own drunkenness file (`brewery_effects.json`: it lists the
resource root, which 26.3 refuses with "Invalid path ''"), so without help none of that loads on a
real server. `compat/brewery`'s mixin finds it per namespace instead; it applies only when Brewery is
loaded, and can go when Brewery fixes it.

### Without Brewery

Kultur registers the drinks itself (`metacraft.kultur.drinks`), but **nothing makes them**: no
brewing-stand recipe and no crafting recipe. Season 6 brings the drinks with Brewery, at an event;
until then they exist only through `/give` and the creative tab. (PolymITer brewed them in a brewing
stand — water bottle + potato, then sweet berries or suspicious stew — and crafted Nyckeln from an
apple and a water bottle. Those recipes are gone; the alcohol values below are what the drinks carry.)

| drink | alcohol |
| --- | --- |
| Alcoholic Beverage | 20 |
| Spiken Patch Drink | 30 |
| Släggan Patch Drink | 35 |
| Nyckeln Patch Drink | 0 |

Drinking one puts its alcohol on your meter, Brewery's: it adds the drink's value (and never leaves
you below it), drains about 14 a minute, and comes down 3 for a slice of bread and 10 for a bucket of
milk. Brewery's drunkenness comes in steps of ten, all below the blackout, and the step you are on is
the level of **Inebriated** — on you whenever there is alcohol in you, its timer the time until
you are sober:

| alcohol | Inebriated | adds |
| --- | --- | --- |
| > 0 | I | nothing; tipsy |
| 30 | II | weakness |
| 40 | III | slowness |
| 50 | IV | the stagger: little steps you did not mean to take |
| 60 | V | nausea |
| 70 | VI | alcohol poisoning, a point every 4 s, never below half a heart |
| 80 | VII | darkness |

Every one of these shows its icon. Each drink also has its own effect, as with Brewery: Alcoholic
Beverage and Spiken send you fast or slow for 200 s, Släggan slow three times in four (and strong once
you are past 60), else fast (and weak past 60); harder past 70. Nyckeln does nothing; it is the sober one.

A drink that takes you to 100 blacks you out instead: you wake up somewhere within 250 blocks (in the
same dimension; if there is nowhere safe you stay where you are), with a splitting headache (four
points of alcohol poisoning, which can kill), blind for 10 s, every other effect gone, at 30. Only
loaded ground counts as somewhere to wake up (looking further would freeze the server); with none,
you wake where you were and keep your other effects, so a blackout at sea does not take your water
breathing. Three Spiken is step VII; the fourth blacks you out. Splash and lingering bottles of the
drinks do nothing to anybody: only drinking counts.

Inebriated is `kultur:inebriated`; a vanilla client is told it is Luck, which the pack draws with
PolymITer's icon and names "Inebriated" ("Berusad" in Swedish). Those three files are in
`src/main/resources/drinks_pack/assets/minecraft/`, not `assets/`, and go into the pack only in this
mode (`DrinksPack`), so with Brewery Luck stays Luck. Polymer merges every mod's
`assets/<namespace>/lang/*.json` key by key, so they add to another mod's `minecraft` lang files rather
than replace them. Vanilla's own Luck (a creative-only potion) shows as Inebriated too. A client names
the bottles, and arrows tipped in a lingering one, from the potion: Kultur's lang has
`item.minecraft.{potion,splash_potion,lingering_potion,tipped_arrow}.effect.kultur_<drink>`.

None of this registers with Brewery on the server: no potions,
effect, damage type, pack files, tick handler or mixin (`DrinksMixinPlugin`, the inverse of
`KulturMixinPlugin`).

## Adding a banner pattern

1. Draw the pattern on a copy of a vanilla banner texture: 64×64 (or 128×128; any multiple of 64
   works, and the regions below scale with it). The flag's front face is the 20×40 area at (1,1).
   Draw there and nowhere else; datagen copies the front onto the back face for you and clears
   the rest.
2. Save it as `src/main/resources/art/kultur/<chapter>/banner/<id>.png`. `<id>` is lowercase
   letters, digits and underscores, unique across every chapter (`slaggan`, not `släggan`).
3. Optionally draw a shield version, `.../shield/<id>.png`, same size, in the shield's 12×22 face
   at (1,1) (vanilla stays inside the 10×20 at (2,2)). Without one, the banner's front face is
   scaled to half size onto the shield, which is fine for bold logos and poor for thin text.
4. Add a line to `src/main/resources/kultur.json` under your chapter:
   `{ "id": "<id>", "name": "<Name>" }`. Add `"item": true` to make it need a pattern item in the
   loom, and then also draw a 16×16 icon at `.../item/<id>.png`.
5. `./gradlew runDatagen` from the repo root, then commit your art and your `kultur.json` line.
   `src/main/generated` is a build artefact, not something to commit — everyone regenerates it
   locally, and the build refuses to package until `runDatagen` has run.

Names: the lang file is generated from `name` — "Light Blue ITK" and so on for all sixteen dyes,
and "<Name> Banner Pattern" for the item.

## Adding a painting

1. A PNG at 16 pixels per block: 48×64 for a 3×4 painting. Save it as
   `src/main/resources/art/kultur/<chapter>/painting/<id>.png`.
2. `{ "id": "<id>", "title": "...", "author": "...", "width": 3, "height": 4 }` under your chapter in
   `kultur.json`.
3. `./gradlew runDatagen`.

Every painting is placeable; a player gets it the vanilla way (a painting item with the variant, or
luck with a blank painting on a wall big enough).

## Adding a chapter

`{ "id": "<chapter>", "name": "<Chapter>", "patterns": [], "paintings": [] }` in `kultur.json`, and a
folder `art/kultur/<chapter>/`.

## Building and testing

- `./gradlew :mods:metacraft-kultur:build` — needs `runDatagen` to have run; the build says so if not.
- `./gradlew :mods:metacraft-kultur:test` — unit tests: the catalogue's validation and the texture
  normaliser (`Masks`).
- `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer` —
  server game tests (`KulturGameTests`): every catalogue entry in its registry and tag, gated
  patterns behind their item, every asset in the jar, the pack's Inebriated. The dev runtime has
  Brewery, so this also runs `KulturDrinkTests`; `KulturVanillaDrinkTests` passes at once.
- The same with `-PkulturNoBrewery` — the dev runtime without Brewery, so `KulturVanillaDrinkTests`
  runs (no recipe makes a drink, the meter, every step, the blackout, sobering) and `KulturDrinkTests` passes at
  once. Run both.
- `./gradlew :mods:metacraft-kultur:runClientGameTest` — a real client joins a test server and
  screenshots a banner, a shield and the painting, and the drinks, into
  `build/run/clientGameTest/screenshots/`. With `-PkulturNoBrewery` it screenshots Kultur's own
  bottles (`drinks_vanilla_bottles.png`) and the inventory at 55 alcohol, Inebriated IV with weakness
  and slowness (`drinks_vanilla_inebriated.png`). Look at them; that is the test.
- `./gradlew :mods:metacraft-kultur:runServer` — a dev server with the pack auto-hosted; join with a
  vanilla client, `/give @s minecraft:loom`, `/give @s kultur:pirkko_banner_pattern`.

## How it works

`kultur.json` is the only list. `GeneratedAssets` (datagen) writes, per pattern, the
`banner_pattern` definition, the tag entries, the normalised banner and shield textures and sixteen
lang lines; per painting, the `painting_variant` definition, its texture and two lang lines; per
gated pattern, the item's model and icon. Banner patterns and painting variants are dynamic
registries the server syncs to every client, so a vanilla client needs nothing but the textures,
which Polymer serves in the pack. The only Java that runs on a server is `ModContent`: one Polymer
item per gated pattern, disguised as the vanilla flower pattern item with our model, carrying
`provides_banner_patterns` for its tag.
