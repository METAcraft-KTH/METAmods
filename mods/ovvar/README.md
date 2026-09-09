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

Patches are items (`ovvar:patch_<id>`). Each is pinned to a spot on the ovve by the catalogue in
`Patches.java`; the sewn set lives in the `ovvar:patches` component and the client is handed the
pre-generated equipment definition for exactly that combination. Cap: 12 patches per half
(top / bottom), because every combination is a file in the pack.

## Debug commands (gamemasters)

    /ovvar give [player] <chapter> [patches]   e.g. /ovvar give it all, /ovvar give data metacraft,nolle
    /ovvar patches <patches>                   re-sew the ovve in your main hand (all / none / ids)
    /ovvar showcase <chapter>                  armour stands: top down, top up, each patch, all patches

## Building

    ./gradlew :mods:ovvar:runDatagen   # turns src/main/resources/art into src/main/generated (assets)
    ./gradlew :mods:ovvar:build

`build` refuses to run without the generated assets, and the mod refuses to start without them.

## Adding a chapter

Drop the chapter's 64×64 skin overlay (the same file the website uses; pure green = "erase the
skin here") into `src/main/resources/art/ovvar/`, add a line to `Chapter.java` naming it and,
optionally, its rolled-down (nercabbad) overlay and a tint colour, then `runDatagen`. The
armour layers, equipment definitions, icons and names are derived from that.

## Adding a patch

One line in `Patches.java` (id, name, spot) and a 4×4 PNG at `src/main/resources/art/ovvar/patches/<id>.png`,
then `runDatagen`. The spot grid is in `Spot.java`. Append to the catalogue — the look key is a
bitmask over it, so reordering changes what every sewn ovve in the world shows.

## Asymmetric sleeves and legs

The armour model draws the left arm and leg as mirror images of the right ones from the same
texture strips, so vanilla can't show different patches per side. The pack overrides the entity
core shader (`assets/minecraft/shaders/core/entity.fsh`): on our textures (marked by a magenta
alpha-2 texel at 63,15) it detects mirrored fragments from the handedness of the texture mapping
and samples the arm/leg boxes one strip up, in the otherwise unused top rows, where datagen puts
the left-side art (base limbs mirrored face by face, left-spot patches flipped). Clients whose
core shaders are replaced (Iris, OptiFine) never sample that strip and see the plain mirrored
look — nothing breaks, sides just match.

## How the look works

The client draws an equipment asset (`assets/ovvar/equipment/<chapter>/<top|bottom|bottom_nercabbad>/<patches>.json`)
as a stack of 64×32 layer textures over the armour model: the base, then one shared layer per
sewn patch. The overlay's body (16,16), right arm
(40,16) and right leg (0,16) boxes are at the same coordinates in the armour layout, and the
model mirrors the right limbs onto the left, so datagen only copies boxes. `Looks` is the one
place that turns a garment stack into an asset key; datagen writes a JSON for every key it can
return, so a missing one is a bug and fails loudly rather than showing a leather piece.
