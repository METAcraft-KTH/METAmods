# Paint gun: design sheet

> **v2: the shipped model is converted from Kenney's blaster-b by `tools/obj2mc.py`; the parts
> table below describes the v1 hand-built model and is kept for reference.** The converter
> surface-voxelises the CC0 mesh in `tools/kenney/`, quantises its colormap into a 16-texel
> palette and greedy-merges the voxels into boxes, so the model is now a few hundred small
> elements rather than nine named ones, the frame is Y up with the barrel along -Z, and the
> orange body family is the dye-tinted part instead of a single "tank" box.

Companion to the Rivals paint prototype spec. The gun is a vanilla "Java Block/Item" element
model (what Blockbench exports) with nine boxes, one flat palette texture, and a dye-tinted
tank so the same model shows every team colour. Orthographic views rendered from the JSON are
in `2026-09-11-paint-gun-design-sheet.svg`; regenerate them after any edit with

```
python3 mods/metacraft-rivals/tools/gun_sheet.py \
    mods/metacraft-rivals/src/main/resources/assets/metacraft-rivals/models/item/paint_gun.json \
    docs/superpowers/specs/2026-09-11-paint-gun-design-sheet.svg
```

## Look

A chunky, friendly blaster in the Splatoon spirit rather than a realistic firearm: a stubby
barrel with a black muzzle ring and an orange nozzle, a boxy light-grey body, a big ink tank
riding on top that is *the* colour of the gun, a dark grip, and one orange indicator light on
the side. Flat colours, no texture detail: the shading comes from vanilla's per-face lighting.

## Frame and units

Units are 1/16 block. v2's frame has Y up, the barrel running along -Z, and the whole model
centred on (8, 8, 8); it comes out of `tools/obj2mc.py`'s voxelisation of the Kenney mesh,
rather than from copying a vanilla item's frame.

The in-hand `display` transforms were checked against in-game screenshots: the converter's
neutral values (no rotation, scale 0.6, first person translated by 1, 2, 1 and third person by
0, 3, 1) turned out right in both views, so they stay.

The table below documents v1's handheld-sword frame, kept here for history:

| Axis | Meaning |
|---|---|
| +Y | forward, toward the muzzle |
| -X | the top of the gun (tank side) |
| +X | the bottom of the gun (grip side) |
| Z | width, centred on 8 |

## Parts

| Part | From | To | Size | Palette | Notes |
|---|---|---|---|---|---|
| barrel | 6.5, 8, 6.5 | 9.5, 15, 9.5 | 3×7×3 | 0 dark grey | |
| muzzle_ring | 6, 14, 6 | 10, 16, 10 | 4×2×4 | 1 black | |
| nozzle_tip | 7, 16, 7 | 9, 17.5, 9 | 2×1.5×2 | 3 orange | pokes past the ring |
| body | 5, 2.5, 5.5 | 11, 8, 10.5 | 6×5.5×5 | 2 light grey | |
| tank | 1, 3, 5 | 5, 9, 11 | 4×6×6 | 4 white, **tintindex 0** | dye-tinted to the team colour |
| tank_cap | 0, 4, 6 | 1, 8, 10 | 1×4×4 | 0 dark grey | |
| grip | 11, 1.5, 6.5 | 16, 5, 9.5 | 5×3.5×3 | 0 dark grey | |
| trigger_guard | 11, 5, 7.5 | 12.5, 7, 8.5 | 1.5×2×1 | 1 black | |
| indicator | 5, 8, 4.5 | 7, 10, 5.5 | 2×2×1 | 3 orange | on the -Z side |

## Palette

`assets/metacraft-rivals/textures/item/paint_gun_palette.png`, 16×16 (v2, one texel per
colour; the v1 sheet used 8×8, one colour per column) (rows are identical). A face samples
column *c* with `"uv": [c, 0, c+1, 1]` — one texel wide, because v2's palette has 16
columns across the same 16 uv units. (v1's 8-column palette used `"uv": [2c, 0, 2c+2, 2]`.)

| Column | Colour | Used by |
|---|---|---|
| 0 | #3A3A3A dark grey | barrel, grip, tank cap |
| 1 | #1E1E1E black | muzzle ring, trigger guard |
| 2 | #C8C8C8 light grey | body |
| 3 | #FF8A00 orange | nozzle tip, indicator |
| 4 | #F2F2F2 white | tank (tinted) |
| 5 | #8A8A8A mid grey | spare |

## Colour variants

One model serves every colour. The tank faces carry `tintindex: 0`, and the item definition
`assets/metacraft-rivals/items/paint_gun.json` declares a `minecraft:dye` tint with a white
default, so the client multiplies the tank's white by the stack's `minecraft:dyed_color`
component. The server sets that component on the client-side stack to the shooter's team
colour (the exact paint RGB, not a chat colour), and clears it for a player without a team,
which shows a white, empty-looking tank. Adding a fourth colour needs no new asset.

Static per-colour files are not needed; if an artist wants to open a variant in Blockbench,
File → Open Model on `paint_gun.json` and set the tint colour in the texture panel.

## Display transforms

| Context | Rotation | Translation | Scale |
|---|---|---|---|
| thirdperson_righthand | 0, -90, 55 | 0, 4, 0.5 | 0.85 |
| thirdperson_lefthand | 0, 90, -55 | 0, 4, 0.5 | 0.85 |
| firstperson_righthand | 0, -90, 25 | 1.13, 3.2, 1.13 | 0.68 |
| firstperson_lefthand | 0, 90, -25 | 1.13, 3.2, 1.13 | 0.68 |
| gui | 0, 0, -90 | 0 | 0.9 |
| ground | 0, 0, -90 | 0, 2, 0 | 0.5 |
| fixed | 0, 0, -90 | 0 | 0.9 |

The gui, ground and fixed contexts lay the gun on its side with the muzzle to the right.
