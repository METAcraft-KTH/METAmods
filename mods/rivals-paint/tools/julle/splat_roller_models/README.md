# Splat Roller Model

Updated, enlarged Roller: 15 cubes, whole-unit geometry, 1.8-block-wide head
when held in third person. Target: Minecraft Java 26.1 / 26.2.

Open model/splat_roller_orange_preview.bbmodel in Blockbench for the colored
editing preview. model/splat_roller.bbmodel is the neutral master for dyeable
Minecraft exports. Both include embedded textures and display transforms.

The resourcepack folder contains the Java geometry, item tint definition,
shared textures, and pack metadata. Install that folder as a resource pack,
or copy its assets into your own pack.

Use minecraft:item_model="splat:roller" and a minecraft:dyed_color component
with a 24-bit RGB integer. The ink faces use tintindex 0 and minecraft:dye.
Orange: 16740892 (#FF721C). Purple: 9389037 (#8F43ED).
Fixed lime, ivory, and dark surfaces retain their colors.

The shared atlas filenames retain the name shooter; the included weapon is
only the Roller. Export the neutral master to preserve runtime dye support.
The orange preview has baked ink color and is intended for editing/viewing.

Includes a model render and actual Minecraft 26.2 first-/third-person images.
Models, textures, and rendering assets only; no recipes or gameplay logic.

Unofficial fan model inspired by Splatoon. Splatoon belongs to Nintendo;
Minecraft belongs to Mojang/Microsoft.
