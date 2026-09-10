# Ovvar

Student overalls (ovvar) with sewn-on patches for METAcraft — server-side, Fabric + Polymer,
Minecraft 26.2. Players need nothing but the auto-served resource pack.

## What it adds

One ovve per chapter — Data (cerise), IT (purple), the older silicon-blue IT — and the Media
frack. An ovve is a single item worn in the legs slot with pockets: it is a bundle, filled and
emptied with the usual bundle clicks — also while worn, by clicking items onto the legs slot. It
holds twice a bundle through METAcraft's own bundle mod (`metacraft-bundles`, a hard dependency in
METAmods; here `libs/metacraft-bundles-1.0.0.jar` is compiled against and the dev server runs
without it, with vanilla-sized pockets). Right-click is the bundle's (hold to empty); sneak +
right-click rolls the top up or down; neither equips it, so drag it in or shift-click. While the
top is up and the chest slot is free the
mod keeps a companion "top" there so the sleeves render; it is not a real item and deletes itself
anywhere else. Real chest armour goes on over it as usual (right-click it, or swap it into the
slot) and hides the top until it comes off again. Leather-grade defence, no durability. The
look is an equipment asset cut from the skin overlays on metacraft.se/style.

Patches are items (`ovvar:patch_<id>`) and go on any 4×4-texel cell of the ovve (`Spot.java`: every
face you see of the body, sleeves and legs — not the inner faces — keeping off the collar, the
belt, the hands and the cuffs: 32 cells, plus the seat for the 8×4 chapter patch). Sewing: put the ovve on
an armour stand, hold a patch, look at the stand — the patch shows washed out on the cell you aim
at (the trim channel in a "ghost" material, so the preview costs no dye bits), the action bar
names it, right-click sews it on; sneak to aim at the far face of the part you look at
(the back of the body, the back of an arm). The aim follows the stand's pose. An empty hand on a sewn patch unpicks it. Seat
patches also go on at the smithing table (ovve + patch, no template). No cap on the number of
patches.

With the stitching minigame on (`config/ovvar.json`: `sewing_minigame`, `stitches`; default on,
6 stitches) the right-click opens a dialog instead: the patch lies on the ovve's cloth and the
seam goes around its edge, following the shape of the art (a heart is sewn around its lobes). The
holes come in pairs — one on the cloth just outside the edge where the thread comes out, one on
the patch just inside where it goes in — so each pair is a stitch over the edge, and the thread
runs under the cloth to the next pair (`Seam.Style.WHIP`; `ZIGZAG` draws every run on top like a
machine seam). The needle sits on the next hole, coming in over the edge; click where it is to
pull it through. Each pull sounds at the stand, the last one sews the patch (`SewingGame`);
Escape or the "Cut the thread" band abandons it, and the patch only leaves your hand when the
seam is done. The dialog's clicks come back as custom click actions (`CustomClickMixin`).

Every button in that dialog is a sprite. The picture is a 7×7 grid of 20 px buttons whose
labels are glyphs of a bitmap font the pack carries (`SewingFont`,
`assets/ovvar/font/sewing.json`): an opaque cloth tile a pixel larger than the button on every
side hides the vanilla button and meets its neighbours across the grid gaps, and the last cell's
label — buttons draw in grid order, so it comes out on top — also draws the patch, the thread
(a row of dot glyphs, so any angle works), the stitch marks and the needle over the whole picture
with negative-advance spaces; every such glyph has one codepoint per vertical position, and its
texture is padded below so no ascent exceeds its height (the client drops the whole font
otherwise). The cell under the needle carries the click. Datagen builds the glyph textures from
`art/ovvar/sewing/`: `cloth.png` (22×22, recoloured in every chapter's colour), `needle.png`
(26×9, pointing right; mirrored and turned for the other directions), `thread.png` (3×3),
`stitch_in.png`, `stitch_out.png` and `hole.png` (5×5), `band.png` (154×22, the text is stamped
on), plus each patch's art scaled up (4×4 at 20×, the seat patch at 12×). It also
traces each patch's outline from its opaque texels into `ovvar/outlines.json` (`Outline`), which
`Seam` spreads the holes along at runtime. Replace the PNGs and `runDatagen`; the
`sewingLabelsFitTheirButtons` game test checks every label of every seam still measures what the
client centres without scrolling. Mockups of the design are in `docs/mockups/sewing/`.

## Debug commands (gamemasters)

    /ovvar give [player] <chapter> [patches]   e.g. /ovvar give it all, /ovvar give data front_top_left.metacraft,seat.chapter
    /ovvar patches <patches>                   re-sew the ovve in your main hand (all / none / cell.patch, bare ids)
    /ovvar showcase <chapter>                  armour stands: top down, top up, each patch, every cell filled
    /ovvar stands <chapter>                    three posed stands in a plain ovve, for testing the sewing aim
    /ovvar minigame [on [stitches]|off]        the stitching minigame setting; saved to config/ovvar.json
    /ovvar aimlog on|off                       log every stand click and aim change with its numbers (server log)

## Building

    ./gradlew runDatagen   # turns src/main/resources/art into src/main/generated (assets)
    ./gradlew build        # build/libs/ovvar-<version>.jar (Polymer bundled; Fabric API separate)

`build` refuses to run without the generated assets, and the mod refuses to start without them.

## Testing

`Start Server.command` runs an offline dev server on localhost with the pack auto-hosted;
`Start Vanilla Client.command` launches a plain vanilla client that joins it. Give yourself an
ovve with `/ovvar give data all` or from the Ovvar creative tab. `Run Tests.command` runs the
game tests (`OvvarGameTests`): every cell aimed at on stands at rest, posed and turned, and the
sneak far-face rule, checked against `StandAim.cell`, the independent cell → point mapping; and
the stitching minigame played through with the clicks its dialog sends (stale clicks ignored,
sewn on the last pull, nothing sewn after cutting the thread).

## Adding a chapter

Drop the chapter's 64×64 skin overlay (the same file the website uses; pure green = "erase the
skin here") into `src/main/resources/art/ovvar/`, add a line to `Chapter.java` naming it and,
optionally, its rolled-down (nercabbad) overlay and a tint colour, then `runDatagen`. The
armour layers, equipment definitions, icons and names are derived from that. A chapter can
instead name a ready-made 64×32 leggings texture for its rolled-down state (`nercabbadArmour`);
it is shifted to the chapter's colour and used as it is. The `*_polymiter` chapters do this with
PolymITer's hand-drawn ovve (`art/ovvar/polymiter/nercabbad.png`), there to compare the two
styles in game — `/ovvar give data_polymiter down` next to `/ovvar give data down`.

## Adding a patch

One line in `Patches.java` (id, name; `true` for a seat patch) and a PNG at
`src/main/resources/art/ovvar/patches/<id>.png` — 8×8 for a cell-sized patch, 16×8 for a seat
patch, or any even size up to 16×16 declared in the catalogue line: such a patch is centred on
its cell and hangs over the neighbours, later-sewn on top (garment and patch textures are the
armour layout at twice the skin's resolution, `Spot.DETAIL`). A patch that hangs over never
rides in the dye colour (the preview library holds cell-sized art), so it shows after the pack
build; its ghost preview is instant. Then
`runDatagen`. The first 22
designs in the catalogue can ride in the dye colour (instant, previewable); later ones only go
through the pack; the preview library holds 30 cells for those 22 — datagen fails loudly when
that runs out.

## How the look works

The client draws an equipment asset as a stack of 64×32 layer textures over the armour model:
the chapter's base, one static texture per sewn placement (`textures/entity/equipment/<layer>/patch/<cell>/<patch>.png`,
all generated by datagen), and a dyeable preview layer. Which layers to stack is the one thing
that is per combination, so each combination of placements on a half is its own tiny equipment
JSON. Datagen writes only the empty ones; `Combos` remembers every combination ever sewn in
`<world>/ovvar/combos.json`, adds their JSONs when Polymer builds the pack, and when a new one
appears (beyond what the trim and the dye colour show, see below) rebuilds the pack. Each build is a generation and each player is on the generation they
last loaded; the new pack is pushed only to players the server is sending an ovve their
generation can't draw — the people close enough to see it — and once their client reports it
loaded (a mixin on the resource-pack response) the equipment of every ovve they can see is sent
again. Everyone else keeps their pack. Rebuilds are batched: a combination the dye colour can still show waits a
minute and a half for company; one it cannot is built within two seconds. In practice: the
first four patches on a half never need a build, and after that the newest three always show
at once while the older ones sit in the pack.

Two more channels carry patches without any pack change, so a normal ovve never causes a
build. The patch being aimed at is worn as the item's *armour trim*: datagen makes
one trim pattern per (cell, patch) — `data/ovvar/trim_pattern/`, textures under
`textures/trims/entity/`, an `armor_trims` atlas source whose key palette is every colour any
patch uses, and two materials: `patch` (identity) and `ghost` (washed out) — and vanilla draws
it. The trim carries only the preview of the patch being aimed at, in the ghost material; sewn
patches go to the dye colour and the pack. Limb cells are tagged in the texture's alpha (254
right, 253 left) so the shader can cut the other limb. Up to three placements per half ride in the dye colour
— six on the legs when the wearer's feet slot carries the second channel (below): a dyeable layer
is only drawn when the item has a dye colour, and that colour reaches the shader as the vertex
colour — the only per-item data an armour shader ever gets — so it carries the *rank* of the set
of up to three (cell, design) placements among all such sets (packed as three base-255 digits so
no byte is 0; 20 cells × 22 designs, C(440,3) ≈ 14M states under 255³). The preview texture holds the art of the first 22
designs plus cell and design tables; the pack's entity core shader
(`assets/minecraft/shaders/core/entity.fsh` + `assets/ovvar/shaders/include/ovvar.glsl`) unranks
the set and draws the art on the cells, lit white so the data colour never tints it. The
placement being aimed at takes one of the three. Designs past the first 22 in the catalogue only
go through the pack. The tooltip's "Dyed" and trim lines are hidden. Everything else is sampled
exactly as vanilla. The overlay's body (16,16), right arm (40,16) and right leg (0,16) boxes are
at the same coordinates in the armour layout, so datagen only copies boxes (with the skin's
second layer painted on, and the left limbs from the skin's own left art).

## The boots pass

The client draws the feet slot with the whole leg boxes of the outer model (vanilla boot
textures are just transparent above the ankle), with its own equipment asset and dye colour: 24
more bits. So an ovve wearer's feet slot carries our legs preview layer too (`OvveFeet`):
vanilla boots of a known material — chainmail, copper, iron, gold, diamond, netherite — are
marked with the wearer and shown to clients as "their layers plus ours", trim and glint kept
(`equipment/feet/<material>.json`); an empty slot is shown to *other* players as virtual cuffs
that exist only in their equipment packets, so the wearer's inventory stays empty there and
boots go on as usual (their own client renders their body from that inventory, so they see up
to three fewer of their own newest leg patches until the pack catches up). The legs then take
six instant patches instead of three. Leather boots use the dye colour for their own colour and
other mods' boots have layers we don't know: over those the channel is off and the legs fall
back to three. The boots pass is
inflated 1.0 where the leggings are 0.5, so the shader draws it on the leggings' pixel grid
(squeezed in x and y) and the two layers' pixels line up.

## Reloads wait for a calm moment

A pushed pack is a loading screen, so a player gets one only after `push_after_calm_seconds`
(config, default 20) without taking or dealing damage, sewing, or moving more than
`push_calm_distance` blocks (default 8) — nobody loses a fight to a reload, and a sewing session
ends in one reload rather than one every few patches. Until then they see what their pack plus
the dye channels can show; nothing goes missing, the newest patches just wait.

## Square pixels

The armour model draws a texel wider than it is tall: the box is inflated (1 on the chest
layer, 0.5 on the leggings layer) but its texture is not, so a face n texels wide covers
n + 2·inflate units while 12 rows cover 12 + 2·inflate — a sleeve texel is 1.5 × 1.167 units,
a chest texel 1.25 × 1.167. Pixel art hates that, so the shader draws everything of ours on the
box sides with square pixels: each strip's texels are wrapped around the box at the square
size, continuous across the corners — a patch hanging over a corner just bends round it — and
the slack that leaves (the inflated box is wider than its texels) is taken up in the middle of
the seam faces, the inner face of an arm or leg and both sides of the body, where the garment's
centre column stretches and patches leave it to the fabric (`ovvar_wrap` in `ovvar.glsl`,
`Spot.wrap` in Java). Anchor faces — a limb's outer face, the body's front and back — keep their
art centred. Each texture carries which layer it is for (the layer texel, two left of the
marker: R = 2·inflate). The ghost preview is a vanilla-drawn trim, so datagen bakes the same
wrap into the trim textures, to the texel — good enough for a ghost, which is why sewn patches
never ride as the trim.

## Asymmetric sleeves and legs

The armour model draws the left arm and leg as mirror images of the right ones from the same
texture strips, so vanilla can't show different art per side. The same shader detects mirrored
fragments from the handedness of the texture mapping: on a base texture it samples the limb boxes
one strip up, where datagen puts the mirrored left-side art; a placement texture is marked with
its side and hidden on the other limb; the preview slots carry the side in their cell. Clients whose core shaders are replaced (Iris, OptiFine) see the plain
mirrored overalls without patches — nothing breaks. Shaderpack users run `OvvarShaderPatcher.jar`
(built from `tools/shaderpatcher`, Java 11+, shipped inside the resource pack at
`assets/ovvar/shaderpatcher/` and worth linking from the website): double-clicked, it writes a
`+ovvar` copy of every pack in `.minecraft/shaderpacks` with `ovvar.glsl` spliced into the pack's
own entity program (the texture-coordinate and vertex-colour varyings are shadowed, so the pack's
code needs no changes). Patched cleanly: BSL, Bliss, Complementary Reimagined and Unbound,
MakeUp Ultra Fast, Solas, Photon, Super Duper Vanilla.
