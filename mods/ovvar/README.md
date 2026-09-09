# Ovvar

Student overalls (ovvar) with sewn-on patches for METAcraft — server-side, Fabric + Polymer,
Minecraft 26.2. Players need nothing but the auto-served resource pack.

## What it adds

One ovve per chapter — Data (cerise), IT (purple), the older silicon-blue IT — and the Media
frack. An ovve is a single item worn in the legs slot with pockets: it is a bundle, filled and
emptied with the usual bundle clicks — also while worn, by clicking items onto the legs slot. Right-click is the bundle's (hold to empty); sneak +
right-click rolls the top up or down; neither equips it, so drag it in or shift-click. While the
top is up and the chest slot is free the
mod keeps a companion "top" there so the sleeves render; it is not a real item and deletes itself
anywhere else. Real chest armour goes on over it as usual (right-click it, or swap it into the
slot) and hides the top until it comes off again. Leather-grade defence, no durability. The
look is an equipment asset cut from the skin overlays on metacraft.se/style.

Patches are items (`ovvar:patch_<id>`). The ovve has *fields* (`Layout.java`): menu spots that
hold one patch each from a short list — chest, back, sleeves, legs — and pinned spots for one
big patch that is either on or off (the chapter patch across the seat). The sewn set lives in
the `ovvar:patches` component as `field=patch` entries, and the client sees only one equipment
asset per half plus a dye colour whose bits say what every field shows; the pack's core shader
decodes them. No file per combination, no pack reloads.

Sewing: put the ovve on an armour stand, hold a patch, and look at the stand — the patch shows
where you aim while it fits; right-click to sew it on. An empty hand on a sewn patch unpicks it.
Pinned patches also go on at the smithing table (ovve + patch, no template).

## Debug commands (gamemasters)

    /ovvar give [player] <chapter> [patches]   e.g. /ovvar give it all, /ovvar give data chest_l=metacraft,seat=chapter
    /ovvar patches <patches>                   re-sew the ovve in your main hand (all / none / field=patch, bare ids)
    /ovvar showcase <chapter>                  armour stands: top down, top up, each field, all fields

## Building

    ./gradlew runDatagen   # turns src/main/resources/art into src/main/generated (assets)
    ./gradlew build        # build/libs/ovvar-<version>.jar (Polymer bundled; Fabric API separate)

`build` refuses to run without the generated assets, and the mod refuses to start without them.

## Testing

`Start Server.command` runs an offline dev server on localhost with the pack auto-hosted;
`Start Vanilla Client.command` launches a plain vanilla client that joins it. Give yourself an
ovve with `/ovvar give data all` or from the Ovvar creative tab.

## Adding a chapter

Drop the chapter's 64×64 skin overlay (the same file the website uses; pure green = "erase the
skin here") into `src/main/resources/art/ovvar/`, add a line to `Chapter.java` naming it and,
optionally, its rolled-down (nercabbad) overlay and a tint colour, then `runDatagen`. The
armour layers, equipment definitions, icons and names are derived from that.

## Adding a patch

One line in `Patches.java` (id, name) and a PNG at `src/main/resources/art/ovvar/patches/<id>.png`
— 4×4 per cell it covers — then list it in the fields that may hold it in `Layout.java`, then
`runDatagen`. A menu field's bit count caps its list (4 bits = 15 patches); the layout is checked
at start-up and datagen fails loudly when something doesn't fit. Append to a field's list —
the value sewn into an ovve is the position in it, so reordering changes what's shown.

## How the look works

The client draws an equipment asset (`assets/ovvar/equipment/<chapter>/<top|bottom|bottom_nercabbad>.json`)
as a stack of 64×32 layer textures over the armour model: the chapter's base, then one *dyeable*
layer per field (`textures/entity/equipment/<layer>/patch/<field>.png`, shared by all chapters).
A dyeable layer is only drawn when the item has a dye colour, and the colour reaches the shader as
the vertex colour — that is the only per-item data an armour shader ever gets, so the patch bits
travel in it: 23 bits per half, laid out by `Layout` (offset and width per field), packed by
`Looks.dye` as three base-255 digits so no byte is 0. The tooltip's "Dyed" line is hidden.

A field's texture carries no garment art at all: a marker row (texel 63,15 magenta alpha 2 says
"ours"; 62,15 says offset, bits and cell count; 61,15… the cells' u, v and side) and a library of
its patches' art in the top strip, which the model never draws for chest or legs. The pack's
entity core shader (`assets/minecraft/shaders/core/entity.fsh` + `assets/ovvar/shaders/include/ovvar.glsl`)
reads the marker, pulls the field's value out of the dye bits, and on the field's cells samples
the chosen library entry instead; the vertex shader lights patch layers as white so the data
colour never tints the art. Everything else is sampled exactly as vanilla. The overlay's body
(16,16), right arm (40,16) and right leg (0,16) boxes are at the same coordinates in the armour
layout, so datagen only copies boxes.

## Asymmetric sleeves and legs

The armour model draws the left arm and leg as mirror images of the right ones from the same
texture strips, so vanilla can't show different art per side. The same shader detects mirrored
fragments from the handedness of the texture mapping: on a base texture it samples the limb boxes
one strip up, where datagen puts the mirrored left-side art; on a patch layer it picks the cells
of the left-side fields. Clients whose core shaders are replaced (Iris, OptiFine) see the plain
mirrored overalls without patches — nothing breaks. Shaderpack users run `OvvarShaderPatcher.jar`
(built from `tools/shaderpatcher`, Java 11+, shipped inside the resource pack at
`assets/ovvar/shaderpatcher/` and worth linking from the website): double-clicked, it writes a
`+ovvar` copy of every pack in `.minecraft/shaderpacks` with `ovvar.glsl` spliced into the pack's
own entity program (the texture-coordinate and vertex-colour varyings are shadowed, so the pack's
code needs no changes). Patched cleanly: BSL, Bliss, Complementary Reimagined and Unbound,
MakeUp Ultra Fast, Solas, Photon, Super Duper Vanilla.
