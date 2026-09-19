# Shooter, Charger and Slosher models

This snapshot contains the three completed models and their dyeable Java exports.

| Model | Cubes | Java item model ID |
|---|---:|---|
| Shooter | 19 | `splat:shooter` |
| Charger | 19 | `splat:charger` |
| Slosher | 15 | `splat:slosher` |

Open `model/splat_NAME_orange_preview.bbmodel` in Blockbench for a colored view.
Use `model/splat_NAME.bbmodel` as the neutral ink master for Minecraft export.
All projects include embedded textures and first-/third-person display transforms.
Separate shared texture PNGs are in `textures/`; orange render previews are in
`previews/`. The `shooter_body` / `shooter_ink` filenames are shared by all models.

The `resourcepack/` directory contains the Java 26.1 / 26.2 geometry, item/tint
JSON, textures and pack metadata. Copy this directory into Minecraft's
resourcepacks directory, or use its assets in your own item implementation.

Assign `splat:NAME` as the item's `minecraft:item_model`. Ink faces use
`tintindex: 0` with a `minecraft:dye` tint source reading the item's
`minecraft:dyed_color` component. Set that component to a 24-bit RGB integer.
Orange defaults to 16740892 (#FF721C); purple is 9389037 (#8F43ED).
The shared neutral ink atlas is grayscale; export it rather than the baked
orange preview texture. Fixed lime, ivory and dark parts remain untinted.

Models, textures and rendering assets only; no recipes or gameplay logic.
Original fan-made geometry. Splatoon belongs to Nintendo; Minecraft belongs
to Mojang/Microsoft. This is an unofficial fan model.
