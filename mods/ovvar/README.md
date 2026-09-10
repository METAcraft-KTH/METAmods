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
an armour stand, hold a patch, look at the stand — the patch shows on the cell you aim at, the
action bar names it, right-click sews it on; sneak to aim at the far face of the part you look at
(the back of the body, the back of an arm). The aim follows the stand's pose. An empty hand on a sewn patch unpicks it. Seat
patches also go on at the smithing table (ovve + patch, no template). No cap on the number of
patches.

With the stitching minigame on (`config/ovvar.json`: `sewing_minigame`, `stitches`; default on,
6 stitches) the right-click opens a dialog instead: the seam runs down the middle of a two-column
grid, one row per stitch, and the needle sits on alternate sides — click it back and forth across
the seam. Each pull sounds at the stand, the last one sews the patch (`SewingGame`); Escape or
"Cut the thread" abandons it, and the patch only leaves your hand when the seam is done. The
dialog's clicks come back as custom click actions (`CustomClickMixin`).

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
`src/main/resources/art/ovvar/patches/<id>.png` — 8×8, or 16×8 for a seat patch (garment and
patch textures are the armour layout at twice the skin's resolution, `Spot.DETAIL`) — then
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
build. The first plain patch sewn on a half is worn as the item's *armour trim*: datagen makes
one trim pattern per (cell, patch) — `data/ovvar/trim_pattern/`, textures under
`textures/trims/entity/`, an identity palette and an `armor_trims` atlas source — and vanilla
draws it. Limb cells are tagged in the texture's alpha (254 right, 253 left) so the shader can
cut the other limb. The next three placements per half ride in the dye colour: a dyeable layer
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
