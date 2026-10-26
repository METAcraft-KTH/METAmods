# More Dyes

METAmods module `mods/moredyes` (mod id `moredyes`). Targets Minecraft 26.3 / Polymer 0.18.4+26.3.
Assets are generated: `./gradlew runDatagen` (repo root) after editing `colors.json`.

Server-side dye colours for vanilla clients: Cerise, Laserviolet, and whatever `colors.json`
says next. Fabric + [Polymer](https://polymer.pb4.eu), Minecraft 26.3, Java 25. Players need
only the auto-served resource pack; no client mod.

This file is the how-to-build. Status: all block phases done — dyes; wool, concrete, concrete powder,
terracotta, glazed terracotta (visible note-block donors); carpet (flat tripwire donor, no
collision); candles (invisible lantern donor + item display, server-sent flame particles) and
candle cakes (vanilla cake on the client + candle display); wool and concrete stairs and slabs
(vanilla's wool or concrete stair or slab in the nearest vanilla colour on the client, with one item
display per block drawing ours over it, see below); beds (spare bed-state donors); shulker
boxes (invisible donor + base/lid displays); stained glass and panes (leaves / copper-bars
donors — since 26.x the client picks the render layer per sprite, so they are truly
translucent); bundles; sheep (colour attachment, vanilla sheep sent invisible, whole-sheep
display-entity rig animated server-side); banner patterns (every registered pattern, vanilla or
datapack, derived per colour at registry load and tinted at pack build; applied with our dyes at a
vanilla loom, see below); banners
in our colours (six of our wool over a stick: a white banner under our full-cover layer, see below); recipes for everything vanilla dyes make, including leather/horse/wolf armour
and firework stars mixed with vanilla dyes; happy ghast harnesses and llama carpet decor (vanilla's
equippable on our own equipment asset); sign text in our exact colour (component colour on every
line, kept when the sign is edited; the sign's own colour, which glow ink's outline uses, is the
nearest vanilla dye; a vanilla dye takes our colour off the lines, so any dye recolours any sign); wolf and cat collars in the nearest vanilla colour. Not supported: beacon-beam
tinting (our glass passes the beam untinted).

## Stairs and slabs

The client is shown vanilla's wool or concrete stair or slab of the same shape, in the nearest
vanilla colour (the same stand-in dye signs, collars and the loom use: Cerise is pink, Laserviolet
purple), and holds that vanilla item, so its placement prediction is the block the server then
shows. One item display per block draws our model over it, grown by 1/256 about a point inside the
shape, so every face sits 1/1024 to 3/1024 of a block outside the vanilla face under it rather than
risk z-fighting with it (`ShapedBlocks.COVER_GROWTH`; more than that hides the far edges of the
selection outline). Where the display is not drawn (beyond its 64-block view range, or for the
moment between a block arriving and its display) the vanilla block shows, never a hole: an
invisible stair, which this used before, still hid its neighbours' faces and showed through the
world. Double slabs are the material's full block, with no display. They take no Polymer donor
states.

## Later

- **Collars in our exact colour.** The client tints a collar by a vanilla `DyeColor`, so today it
  gets the nearest one. A pet could be sent like our sheep are: the vanilla wolf or cat drawn
  without its collar and a display-entity collar in our colour riding on it (see `sheep/`
  `SheepOverlay` and `SheepWoolRig`), animated with the head.

## Adding a colour

Add an entry to `src/main/resources/colors.json` — `id`, `name`, `rgb`; optional are `flower`, the
name of the colour's flower ("<name> Flower" when absent), `ramp: [dark, light]` (derived in CIELAB from `rgb` when absent), and `vanilla`, the vanilla dye shown for
it on vanilla clients (signs, collars, banners, the stairs and slabs under their display) when the
nearest one by text colour reads wrong (Laserviolet's would be light gray, so it says `purple`) — and run `./gradlew runDatagen`. Everything
else loops over the list: blocks, items, recipes, tags, banner patterns, tests, the showcase.

A colour can drift instead of holding still: add `"animation": {"frametime": 150, "interpolate": true,
"frames": ["#FF0000", ...]}` (see RGB). Each frame is a hex colour or `{"rgb": ..., "ramp": [dark, light]}`;
datagen writes every texture of the colour as a strip with one frame per keyframe plus its `.png.mcmeta`,
banner patterns do the same, and dyed leather, horse and wolf armour follows the same blend per player.
Keep about 24 frames (vanilla blends linearly in RGB, so far-apart hues go muddy). `rgb` stays the
colour for what cannot animate: fireworks and the map. The worn happy ghast harness and llama carpet
are drawn from no atlas, so they hold one frame cut into horizontal bands of the animation's optional
`"stripes"` colours (three keyframes sampled evenly when absent; RGB sets red, green, blue), and a canvas paints it as random pixels from its frames.
A hand-drawn texture of an animated colour stands still (the RGBouquet, the RGB dye and glazed terracotta).

What limits how many colours fit is Polymer's donor-state budget, and `Looks` decides once at
startup, per family, whether the colour count fits the visible-donor pools ("donor" look: our
model as chunk geometry) or the family switches to a shared invisible donor plus one item display
per placed block ("display" look). The decision is logged at startup and applies to all colours of
that family. A server can set it per family in `config/moredyes.json5` (`auto`, `donor` or `display`,
also editable with `/meta-config-screen moredyes`); `-Dmoredyes.look.<family>=donor|display` overrides
that for comparison. Today only glass
panes are planned this way (their copper-bars pools hold two colours in donor mode); beds
(about 15), carpets (about 30) and glass (about 50) get the same treatment when needed. A pool
that cannot serve even the chosen look still stops the server.

## Using it in game

- Dye a sheep: right-click it with a dye. Shear/kill drops the coloured wool. Vanilla dye turns
  it back. Admins: `/moredyes dye <targets> <color>`.
- Banner patterns: our dyes in a vanilla loom (`LoomMenuMixin`): the server's dye slot and shift-click
  take them, and the result is the pattern's derived version in our colour, applied with white. Pattern
  items (vanilla's flower or globe, kultur's Pirkko) and using up one banner and one dye per take stay
  vanilla's; so does the six-layer cap, except on a banner in our colour (below). The vanilla client runs
  the loom itself on its own view of the items, so the client's copy of our dye (only the client's,
  `ModDyeItem.modifyBasePolymerItemStack`) carries a `dye` component of the nearest vanilla colour:
  that lets it into the dye slot and lists the patterns. Two things show it: the pattern buttons'
  tooltips name that colour ("Pink Cross"), and until the server's result arrives (a round trip,
  plus up to a tick) the preview and result slot show the client's guess, the pattern in that colour.
  The same guess shows again for that moment after each take while banners and dyes remain. The server's
  result always replaces it; nothing taken is ever the guess. Other side effects of the client
  component are predictions only: on a tamed wolf the client predicts the nearest-colour collar,
  which is what the server sets (`Collars`); on a sheep the client plays the dye sound and swings,
  also when the server then does nothing (a sheep already in that colour).
- Six patterns on our colour: at a loom a banner in our colour takes six patterns on top of its
  colour layer (seven layers), with any dye: the server's loom lifts its six-layer cap by one for a
  banner whose first layer is our colour (`BannerPatterns.layerCap`). The vanilla client's loom screen
  counts the banner's layers itself and lists nothing from six on, so a coloured banner with five
  patterns on top is sent to the client, in the loom's banner slot only, as those five patterns on a
  plain banner of the nearest vanilla colour (`BannerPatterns.loomClientView`, `LoomBannerViewMixin`);
  the menu keeps the real banner and the result is the real one. What the player sees: that banner's
  icon in the input slot is, say, a pink banner rather than a Cerise one (its name still says Cerise);
  shift-clicked or placed in, the real banner shows for a round trip before the server swaps the view
  in; taken back out by hand, the cursor holds the view for a round trip until the server sends the
  real banner. The result slot and the preview show the real banner. The vanilla client renders up to
  sixteen layers on the block, the item and a shield; its tooltip lists only the first six, so the
  last pattern goes unnamed there. Copying such a banner works (`BannerDuplicateRecipeMixin` lifts
  vanilla's six-layer limit by one in the same way); placing, breaking, the shield recipe and the
  block entity never trimmed.
- Banners in our colours: six of our wool over a stick, vanilla's banner shape and recipe-book
  group (`moredyes:<color>_banner`). The result is a `minecraft:white_banner` whose first layer is
  the derived `moredyes:<color>/minecraft/base` (vanilla's full-cover pattern) applied with white,
  with `item_name` "Cerise Banner" (`block.moredyes.<color>_banner`). It places, breaks and copies
  like any banner, takes six patterns on top at a vanilla loom (the colour is not one of them), and
  decorates a shield. A cauldron washes patterns off down to the colour but not the
  colour itself (`CauldronInteractionsMixin`), as with a banner's own colour. To copy one, the blank
  is a plain *white* banner: vanilla only copies onto a banner with no layers.
- Dyes come from flowers: a sniffer digs up a Cerose, a Laser Violet or an RGBouquet beside vanilla's
  torchflower seeds (`ModFlower`; colors.json's `flower` names it), and each crafts into its dye.

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
≥ 0.19.5 and Fabric API 0.161.x for 26.3.

Dev server: `./gradlew mods:moredyes:runServer`; set `online-mode=false` in `run/server.properties`
and turn on Polymer's pack host (`config/polymer/auto-host.json`), then join `localhost` with a
vanilla client. That is the client that counts. `./gradlew runClient` also exists but it is a
Fabric client with Polymer's client half loaded, so it does not show what players see; it runs
in `run-client/` so it cannot clobber the server's generated pack.

## Tests

`JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:moredyes:runServer` runs the
server-side game tests in `metacraft.moredyes.gametest` on a test server and exits non-zero on any
failure. `./gradlew :mods:moredyes:runClientGameTest` (opens a window) has a vanilla client join an
in-process server and take screenshots of the stairs and slabs (`src/gametest`, screenshots in
`build/run/clientGameTest/screenshots`), judged by eye. They cover what a vanilla client is
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
metacraft.moredyes.content          Family enum, Looks, ModContent (registration, creative tab)
metacraft.moredyes.content.block    ColoredBlocks, ShapedBlocks (stairs/slabs + display holder),
                                    GlassBlocks, ContainerBlocks, ModFlower, ClientStates
metacraft.moredyes.content.item     ColoredBlockItem, ModDyeItem, ModBundleItem, ModCushionItem,
                                    ModHarnessItem
metacraft.moredyes.sheep            SheepColors (attachment + events), SheepOverlay, SheepWoolRig
metacraft.moredyes.banner           BannerPatterns (derive + tint, coloured banners, the loom's layer cap
                                    and client view)
metacraft.moredyes.mixin            Sheep shear/colour/breeding, Mob death loot, RegistryLoadTask
```

Generated assets are gitignored (they are recoloured Mojang textures); regenerate, don't edit.

## Reference

The item display per stair or slab comes from
[craftycorvid/wool-polymer](https://github.com/craftycorvid/wool-polymer) (MIT), which also
provided the original build template.
