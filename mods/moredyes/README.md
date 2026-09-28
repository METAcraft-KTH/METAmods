# More Dyes

METAmods module `mods/moredyes` (mod id `moredyes`). Targets Minecraft 26.2 / Polymer 0.17.1+26.2.
Assets are generated: `./gradlew runDatagen` (repo root) after editing `colors.json`.

Server-side dye colours for vanilla clients: Cerise, Laserviolet, and whatever `colors.json`
says next. Fabric + [Polymer](https://polymer.pb4.eu), Minecraft 26.2, Java 25. Players need
only the auto-served resource pack; no client mod.

The design and phase plan live in the "Cerise & Laserviolet" artifact; this file is the
how-to-build. Status: all block phases done — dyes; wool, concrete, concrete powder,
terracotta, glazed terracotta (visible note-block donors); carpet (flat tripwire donor, no
collision); candles (invisible lantern donor + item display, server-sent flame particles) and
candle cakes (vanilla cake on the client + candle display); wool and concrete stairs and slabs
(invisible shaped donor + one item display per block); beds (spare bed-state donors); shulker
boxes (invisible donor + base/lid displays); stained glass and panes (leaves / copper-bars
donors — since 26.x the client picks the render layer per sprite, so they are truly
translucent); bundles; sheep (colour attachment, vanilla sheep sent invisible, whole-sheep
display-entity rig animated server-side); banner patterns (every registered pattern, vanilla or
datapack, derived per colour at registry load and tinted at pack build; applied through the
dye-loom GUI); recipes for everything vanilla dyes make, including leather/horse/wolf armour
and firework stars mixed with vanilla dyes. Not supported by design: wolf/cat collars, sign
text, harnesses, and beacon-beam tinting (our glass passes the beam untinted).

## Adding a colour

Add an entry to `src/main/resources/colors.json` — `id`, `name`, `rgb`; `ramp: [dark, light]` is
optional (derived in CIELAB from `rgb` when absent) — and run `./gradlew runDatagen`. Everything
else loops over the list: blocks, items, recipes, tags, banner patterns, tests, the showcase.

What limits how many colours fit is Polymer's donor-state budget, and `Looks` decides once at
startup, per family, whether the colour count fits the visible-donor pools ("donor" look: our
model as chunk geometry) or the family switches to a shared invisible donor plus one item display
per placed block ("display" look). The decision is logged at startup and applies to all colours of
that family; `-Dmoredyes.look.<family>=donor|display` forces it for comparison. Today only glass
panes are planned this way (their copper-bars pools hold two colours in donor mode); beds
(about 15), carpets (about 30) and glass (about 50) get the same treatment when needed. A pool
that cannot serve even the chosen look still stops the server.

## Using it in game

- Dye a sheep: right-click it with a dye. Shear/kill drops the coloured wool. Vanilla dye turns
  it back. Admins: `/moredyes dye <targets> <color>`.
- Banner patterns: hold a banner (or a shield with a base colour) in one hand and a dye in the
  other, right-click the air. Pick a pattern; it is applied with white as the carrier colour.
- Dyes have no recipe yet (a flower is planned); use the creative tab or `/give`.

## Ground rules (short version)

- **Never extend `DyeColor`.** Colours are a mod-owned registry (`ModColor`); our dyes are not
  vanilla `DyeItem`s. Anything DyeColor-keyed that cannot show a new colour on a vanilla
  client (wolf/cat collars, sign text, vanilla dye-tag recipes) is simply not supported.
- **Fallbacks are errors.** A Polymer donor pool that is short at startup stops the server
  with a message naming the family. A visual path that fails at runtime shows a command block
  and logs an error. The one deliberate vanilla approximation is the map colour (64-entry
  client palette), computed as the nearest by RGB at init.
- **The pack is required.** A vanilla client that declines is disconnected by the game.
  Enable auto-hosting once on the server: `config/polymer/auto-host.json` → `{ "enabled": true }`
  (behind a proxy also set `"forced_address"`).

## Adding a colour

1. Add an entry to `src/main/resources/colors.json`:
   ```json
   { "id": "seafoam", "name": "Seafoam", "rgb": "#5FE0C0", "ramp": ["#1F7A66", "#C8FFF0"] }
   ```
   `ramp` is the dark and light stop the texture recolouring maps onto; tune it by eye.
2. `./gradlew runDatagen` (standard Fabric data generation, `metacraft.moredyes.datagen`; output in `src/main/generated`).
3. `./gradlew build`. Everything else — blocks, items, models, loot, recipes, tags, names — is
   derived. No Java knows a colour id.

Never delete an entry once worlds exist; mark it `"retired": true` so existing blocks resolve.

## Build

```
export JAVA_HOME=/path/to/jdk-25
./gradlew runDatagen build
```

Output: `build/libs/moredyes-<version>.jar` (Polymer bundled). Server needs Fabric Loader
≥ 0.19.2 and Fabric API 0.150.x for 26.2.

Dev server: `./gradlew mods:moredyes:runServer` (or `Start Server.command`); `run/` ships `eula.txt`,
`server.properties` with `online-mode=false`, and `config/polymer/auto-host.json` enabling the
pack host. Vanilla client: `python3 tools/vanilla_client.py` (or `Start Vanilla Client.command`)
downloads the real 26.2 client into `run-vanilla/`, starts it with an offline profile and joins
`localhost`. That is the client that counts. `./gradlew runClient` also exists but it is a
Fabric client with Polymer's client half loaded, so it does not show what players see; it runs
in `run-client/` so it cannot clobber the server's generated pack.

## Tests

`./gradlew mods:moredyes:runGametest` (or `Run Tests.command`) runs the server-side game tests in
`metacraft.moredyes.gametest` on a throwaway test server and fails the build on any failure; the
JUnit-style report lands in `build/test-results/gametest.xml`. They cover what a vanilla client is
*sent* — every block state resolving to a real client state, the sheep being sent invisible, the
derived banner registry — plus drops and interactions (double slabs, beds, shulker contents,
concrete hardening, sheep dye/shear/death/vanilla-dye reset, candle tags). In a normal world an op
can run the same tests with vanilla's `/test runall` and watch the beacons. Pixels stay a visual
check with the vanilla client and `/moredyes showcase`.

## Layout

```
colors.json                         single source of truth
metacraft.moredyes.datagen.GeneratedAssets                 colours → textures, models, lang, loot, recipes, tags
metacraft.moredyes.MoreDyes         entrypoint: colours → content → resource pack
metacraft.moredyes.color            ModColor record, colors.json loader
metacraft.moredyes.content          Family enum, ColoredBlocks, ShapedBlocks (stairs/slabs +
                                    display holder), items, creative tab, ClientStates
metacraft.moredyes.sheep            SheepColors (attachment + events), SheepOverlay, SheepWoolRig
metacraft.moredyes.banner           BannerPatterns (derive + tint), DyeLoomGui (sgui)
metacraft.moredyes.mixin            Sheep shear/colour/breeding, Mob death loot, RegistryLoadTask
```

Generated assets are gitignored (they are recoloured Mojang textures); regenerate, don't edit.

## Reference

The stairs/slabs pattern (invisible shaped donor + item display per block) comes from
[craftycorvid/wool-polymer](https://github.com/craftycorvid/wool-polymer) (MIT), which also
provided the 26.2 build template.
