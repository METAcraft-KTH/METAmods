# METAcraft-QoL

Small quality-of-life features for Metacraft. Each lives in its own package
(`nu.metacraft.qol.<feature>`) and its own section of `config/metacraft-qol.json`, with an
`enabled` switch. A section missing from the file takes its defaults, so adding a feature never
breaks an existing file.

- [Void anchor](#void-anchor): catches you when you fall into the End void.
- [Concrete cauldron](#concrete-cauldron): throw concrete powder into a water cauldron to make concrete.
- [Silence mobs](#silence-mobs): the muffler (or a "silence me" name tag) silences a mob.

## Void anchor

A respawn anchor for the End void. Elytra rockets are off on Metacraft, so nothing used to save a
player who fell off an End island; a void anchor does.

- **Craft** it like a respawn anchor: crying obsidian on the top and bottom rows, eyes of ender in the middle.
- **Charge** it with the fuel item (end crystals by default), up to four charges. A dispenser holding
  the fuel item tops up an anchor in front of it.
- **Bind** it by using it with an empty hand. It only works in the End; elsewhere it refuses to bind
  (and, unlike a respawn anchor, never explodes or sets your spawn). One anchor per player; several
  players may share one, and its charges.
- **Fall.** Below the End's lowest Y a rift opens beneath a bound player, their fall eases into it,
  and they come out beside their anchor, which loses one charge. If the anchor is empty, broken or
  blocked, the player is told so and falls as before (into season 5's overworld teleport, if that
  is on). Players without an anchor see no change.

`/voidanchor rift [pos]` (permission `metacraft.qol.voidanchor`, level 2) opens a rift that only
looks, for checking how it renders.

### Config

The `void_anchor` section of `config/metacraft-qol.json`:

| Key | Default | |
|---|---|---|
| `enabled` | `true` | off: no rescues; the block stays, but using it only says void anchors are off |
| `fuel_item` | `minecraft:end_crystal` | the item that adds one charge |
| `trigger_y_offset` | `0` | the rift opens below the End's lowest Y plus this |
| `rift_depth` | `6.0` | how far below the player the rift opens |
| `descent_speed` | `0.3` | blocks per tick while sinking into the rift |
| `rift_ticks` | `30` | the longest a player sinks before the rift takes them |
| `rift_size` | `4.0` | the rift's width in blocks |

### The rift shader

The rift is an item display of `metacraft:rift`, a flat quad with a painted, animated sprite
tinted exactly `#FEFEFD`. The item shader override lives in **metacraft-shaders**
(`assets/minecraft/shaders/core/item.{vsh,fsh}`), shared with nether-rifts; the void anchor's drawing
is `assets/metacraft/shaders/include/void_rift.glsl` there. A vertex with that tint is flagged, and
its fragments draw a moving swirl of stars instead of the sprite. Every other item takes the vanilla
path. A client without the shader still sees the painted sprite.

The overrides are vanilla 26.3's shaders plus that one branch, so **re-diff them against vanilla on
every Minecraft update**. 26.3 compiles shaders to SPIR-V, hence `gl_VertexIndex`, not `gl_VertexID`.
Which vertex counts as a quad's first depends on where its draw starts in a shared buffer, so the
swirl repeats every quarter turn: a shifted corner order turns it by 90° and nothing visibly changes.

Block and rift textures are placeholders drawn by `tools/gen_textures.py`; replace them with real art
whenever.

## Concrete cauldron

Throw concrete powder into a water cauldron and the whole dropped stack turns into the matching
concrete, for one level of water: 64 powder thrown as one stack costs the same as a single one, so
throw full stacks. From the last level the cauldron is left empty; rain or dripstone refill it.
Only water cauldrons do this (not empty, lava or powder-snow ones). The concrete stays where it
landed, so a hopper under the cauldron collects it, as hoppers do with any item in a cauldron.

Every colour works: the concrete is whichever one vanilla hardens that powder into.

Config: the `concrete_cauldron` section of `config/metacraft-qol.json` has just `enabled`.

## Silence mobs

**The muffler** (shapeless: any wool, an amethyst shard and string): right-click a mob to silence it,
again to undo it. It's never used up. It works on mobs with a right-click of their own too
(villagers, horses), because it acts before them.

**Or a name tag**, for players used to the datapack: one named `silence me` silences the mob, one
named `unsilence me` undoes it. Case, spaces and underscores don't matter (`Silence_Me` works), and
the tag is used up as naming would use it (not in creative). Any other name is ordinary naming.

Either way the mob keeps the name it had, or stays unnamed; it glows for a moment, chimes, and the
actionbar says "Silenced".

This replaces the Vanilla Tweaks "Silence Mobs" datapack, which on 26.3 left every silenced mob
named "silenced" for good (its clean-up looks for the wrong tag and calls a function that isn't
there), never ran its "hold an unsilence tag to see silenced mobs" highlight (a misspelt function),
and silenced the nearest mob with the name within 16 blocks rather than the one clicked.

Config: the `silence_mobs` section of `config/metacraft-qol.json` has just `enabled`; off, the
muffler does nothing and "silence me" is just a name.

## Tests

- `./gradlew :mods:metacraft-qol:runDatagen :mods:metacraft-qol:test` runs every feature's
  game tests on one test server. Void anchor: charging, binding, dispensers, and the rescue: empty, gone, flying, creative,
  disconnecting, saved-themselves and shared anchors. Concrete cauldron: stacks, single items, the
  last level, colours, other items and dry cauldrons. Silence mobs: the muffler and the name tags through
  a real interact packet, kept names, any case, other names, creative, the muffler's recipe. Each feature's "switched off" tests run in
  their own test environment (`metacraft:qol_<feature>_off`), whose setup and teardown flip the
  config through a test-only `/qoltest <feature> <true|false>` command, so no other test sees the
  feature off.
- `./gradlew :mods:metacraft-qol:runClientGameTest` opens a window: a vanilla client joins
  an in-process server, looks down at a rift, and checks that the shader ran, the rift moves, and
  it looks the same turned a quarter (see above).
  Screenshots land in `build/run/clientGameTest/screenshots`.
