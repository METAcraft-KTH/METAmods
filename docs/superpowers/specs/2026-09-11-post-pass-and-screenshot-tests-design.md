# Ovvar on 26.3: post-pass rendering and a screenshot test suite

Branch `26.3` of Froosty11/metamods. Written 2026-09-11 after the rc-1 bump.

## Goal

Patches an ovve wears are drawn by a post-effect shader fed per wearer through a data strip,
so no placement ever needs a resource-pack rebuild or a client reload. The renderer is the
prototype in commit aa2d015 on branch `post` of the `Metacraft/ovvar` sandbox, moved onto
Minecraft 26.3's `end_of_frame` post effect. A client-driven screenshot suite proves what the
player sees, on every change.

## Sequencing

1. Rework `metacraft-bundles`' `BundleContentsMixin` for rc-1. Nothing in ovvar runs until the
   ovve, a bundle, loads.
2. Port the post pass into `mods/ovvar` on `26.3`.
3. Build the screenshot suite against it.

Each step ends green: compile, checkstyle, datagen, the server game tests, and for step 3 the
client screenshot tests.

## 1. Bundles mixin on rc-1

rc-1 restructured `BundleContents.Mutable`: it extends `GrowableMutableContainer` →
`SimpleMutableContainer` (which owns `items`), its constructor is private `(List, Fraction,
int)` and `asMutable()` creates it, and the weight arithmetic moved into private
`getStackedWeight(ItemStack)` and `getWeightWithAddedItems(Fraction, ItemStack)`, called from
`tryInsert`, `tryTransfer`, `setItem` and `addSlotWithItem`. `getMaxAmountToAdd` and `removeOne`
no longer read weights; `findStackIndex` delegates to `findStackIndexWithinRange`.

Changes:
- The nested `Mutable` mixin extends `GrowableMutableContainer<BundleContents>` so `items` is the
  inherited protected field; the `@Shadow items` goes.
- The constructor injection becomes a `@ModifyReturnValue` on `BundleContents.asMutable()` in the
  outer mixin, copying the size factor onto the new mutable.
- The weight hooks target `getStackedWeight` and `getWeightWithAddedItems` (divide by the
  factor), replacing the three hooks on `getMaxAmountToAdd`/`tryInsert`/`removeOne`.
- The insertion-index hook moves to `findStackIndexWithinRange`; the `copyWithCount` arg hook on
  `tryInsert` stays (still there) and gains `mergeIdenticalStacks` if it splits stacks there too.
- `BundleHelper`'s `new Mutable(contents)` → `contents.asMutable()` (done).

Verified by the existing bundle game tests in moredyes (`bundleDye`) plus a new bundles game
test: a bundle with a size factor accepts exactly factor × 64 items and reports the same
weight after `asMutable().toImmutable()`.

## 2. The post pass on 26.3

Port aa2d015 file by file onto the current `mods/ovvar` (which has Acuadragon100's codec
rework and today's stand-sprite, seat and stitch fixes; `Placement` holds a `Patch`, tabs
everywhere):

- `post/Rows`, `post/WearerStrip`: as in the prototype, minus the glowing trigger display.
- `ModComponents.ROW`, `OvveItem.inventoryTick` row assignment, `OvveTop`/`OvveFeet` carrying
  the row, `Looks` (INSTANT = 2, row × ranks in the dye bits, `instantPrefix`, `overflow`),
  `EquipmentJson` single dyeable layer with `color_when_undyed`, `GeneratedAssets.postPass`,
  kind 3 garment textures.
- Shaders: the tag in `entity.fsh`, the data glyph in `text.vsh`/`text.fsh`, the `ovvar.glsl`
  changes, `post/compose.fsh`. `post/clear.fsh` and the `entity_outline.json` chain go.
- New `assets/minecraft/post_effect/end_of_frame.json`: one pass, `compose` reading
  `minecraft:main` and the post texture, writing `minecraft:main`. The client adds
  `end_of_frame` to every frame's effect list when the pack defines it (GameRenderer.update,
  rc-1), so no trigger is needed.

Fix in the port, with tests:
- The KTH overhang's scrambled columns: the compose shader's `artTexel` must apply the same
  wrap and column selection as `ovvar_uv` for oversize art; a server game test can't see it,
  so this is a screenshot scene (below), and `Spot.anchored` gets a Java mirror test of the
  wrapped column for an oversize patch on a sleeve corner.
- The trim slot: gone, along with the trim channel (`Trims`, trim datagen, the trim material
  and patterns) — the post pass carries what the trim did.

Remove, not disable: `Combos` (per-combination pack builds, pushes, claims, `/ovvar reload`),
`Looks.claimIfNeeded`, the push-calm config, and the README sections on reloads. The pack
Polymer hosts becomes static: garments, patch sprites, the sewing font, the post texture, the
data font. `StandDisplays` stays as it is (sprites on stands).

Server game tests added: `WearerStrip.text` round-trips (header, payloads, the 32-column
limit, row 0 for a stand), `Looks.encode/rank` with the row folded in stays under 255³ for
every half, `overflow` is in sewing order and never contains an instant placement.

## 3. Screenshot suite (hybrid)

Fabric's client game-test API (module 6.0.7, 26.3) drives a real client: in-process dedicated
server, connect, scripted input, `assertScreenshotEquals` against golden PNGs with per-scene
region and algorithm.

Loom: in `mods/ovvar/build.gradle`, `fabricApi { configureTests { createSourceSet = true;
modId = "ovvar-clienttest"; enableClientGameTests = true; eula = true } }` → source set
`clientTest`, task `runClientGameTest`, entrypoint `fabric-client-gametest`.

Keeping Polymer vanilla toward the test client (the "hybrid"): the client under test has
polymer-core on its classpath, and the server marks a client Polymer-aware only when it
answers the server's hello (`polymer-networking`: `ClientPacketRegistry.handleHello`). The
test source set ships one client-only mixin that cancels `handleHello`, so the client never
answers and the server serves it as vanilla: real pack, real shaders, real post pass. The
first task in step 3 is a spike proving this with one scene (the vanilla-only equipment layer
present in the screenshot).

Scenes, one test each, camera fixed by teleporting the server player and setting rotation:
1. `worn_front_rest`, `worn_back_rest`: an ovve with two instant patches, front and back.
2. `worn_overflow_sleeve`: three patches on one sleeve, so the post pass draws the third.
3. `worn_kth_corner`: KTH hanging over a sleeve corner (the scramble regression).
4. `seat`: the seat patch from behind.
5. `nercabbad`: the top rolled down.
6. `stand_preview`: a stand with a sewn patch and a ghost preview.

Goldens live in `mods/ovvar/src/clientTest/resources/screenshots/<scene>.png`, 854×480,
compared with the API's default algorithm on a region that excludes the HUD and hotbar; a
scene fails if any patch pixel block differs. Regenerating goldens is an explicit
`-Dovvar.updateScreenshots=true` run, never automatic. `README.md` gets a "Screenshot tests"
section: how to run, how to add a scene, how to bless a change.

CI is out of scope for this spec; the suite must run locally on macOS with a window first.

## Risks

- Polymer's author discourages tampering with its internals. The hello cancel lives in the
  test source set only and never ships; if Polymer adds a supported switch, use it.
- `end_of_frame` runs after the level and hand but its exact place relative to the GUI is
  unverified; the strip is painted over by the pass itself, so GUI order only matters if the
  GUI draws before the pass. The spike scene checks the hotbar is intact.
- The text-display glyph pinning depends on the vanilla text shader being overridable by the
  pack on 26.3 as on 26.2; verified in the spike.
- The other 26.3 blockers (lib's recipe hooks, the seven modules) are separate work.

## Out of scope

Iris/shaderpack compatibility for the post pass; the moredyes sandbox; CI runners.
