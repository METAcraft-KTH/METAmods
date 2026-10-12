# Pirkko

A server-side Fabric mod that adds small hostile cave mobs and placeable Pirkko figures to Minecraft
using Polymer. Players join with a vanilla client and accept the server's required
resource pack to see the models and colors.

The colored Pirkkos use a six-cuboid model: four stacked rectangular body sections
and two small, level arms. The model is 6.5 model pixels tall (uniformly scaled to 50% of the original), with a narrow lower
base and a wider belly. Its solid-color textures currently have no face.

## Colors

There are 18 colored variants:

- Laserviolett (`#CC99FF`): `pirkko:pirkko_laserviolett`
- Cerise (`#E83D84`): `pirkko:pirkko_cerise`
- All 16 Minecraft dye colors: `pirkko:pirkko_<color>`

The dye color names are `white`, `orange`, `magenta`, `light_blue`, `yellow`,
`lime`, `pink`, `gray`, `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`,
`red`, and `black`. Their textures use Minecraft's DyeColor texture colors.
Minecraft pink and cerise are separate variants.

## Cave mobs

Pirkkos spawn in dark Overworld caves below sea level, in groups of 1–3
(spawn weight 15). Normal monster darkness, ground placement, and mob-cap rules apply.
They also spawn and remain in Peaceful, where they wander without chasing or
attacking, even when hit. Switching to Peaceful stops an existing chase;
switching back enables aggression again. The `spawn_mobs` and `spawn_monsters`
game rules still control their natural spawning.

They have 4 health (2 hearts, the same as a chicken). Outside Peaceful they deal
1 HP (half a heart) per melee hit before armor/shields/resistance, and pursue
players within 24 blocks. Bite damage does not scale with difficulty, Strength,
Weakness, or equipment. Their whole body sways left and right while walking,
easing upright when stopped; they tip over when dying.

Each mob randomly chooses one of the 18 colors and retains it across saves.
Killing one drops exactly one matching placeable figure, unless mob drops are
disabled. Looting does not increase the count. Natural despawning drops nothing.

Pirkkos also expire after 10 minutes of loaded time, so they cannot pile up around
players who never leave (vanilla's distance despawn only applies to mobs far from
every player). Expiry drops nothing and is saved with the mob. Name-tagged, leashed and
riding Pirkkos never expire.

```mcfunction
/summon pirkko:pirkko ~ ~ ~
/summon pirkko:pirkko ~ ~ ~ {Color:"cerise"}
```

The mob uses a 0.5 × 0.5 × 0.5-block attack/collision box,
with the existing half-scale model. Its client representation is an invisible
interaction box plus a Polymer item display; no client mod is required.

## Placing Pirkkos

Use vanilla commands to obtain a figure, then right-click to place it:

```mcfunction
/give @s pirkko:pirkko_laserviolett
/give @s pirkko:pirkko_cerise
/give @s pirkko:pirkko_cyan
/setblock ~ ~ ~ pirkko:pirkko_red
```

Each figure drops its own item when broken. There are currently no crafting or
dyeing recipes or custom commands. Placement chooses one of two orientations,
90° apart, with the colored figure's arms across your view. Opposite facing
directions use the same orientation.

All variants have a flower pot's **6×6×6 model-pixel selection and movement
collision box** (0.375 blocks per side), centered at the bottom of the block.
The visible figure extends beyond this box; aim at its base to select or break it.
There is no visible pot around the figure.

## Installation and build

Targets Minecraft 26.3 with Fabric and Java 25. Install the mod on the server
with Fabric API and Polymer's core, virtual-entity, resource-pack, and autohost
modules. Dependency versions are defined in the root `gradle.properties`.
The standalone jar does not bundle Polymer.

Configure Polymer autohost to build and serve the required resource pack.
Players must accept that pack; they do not need to install a client mod.

Build from the repository root with a Java 25 JDK:

```sh
./gradlew :mods:pirkko:assemble
```

The jar is written to `mods/pirkko/build/libs/`. The repository's combined `dist`
build also includes this module.

## Models and textures

Runtime models, textures, item definitions, translations, and loot tables are
stored under `src/main/resources`. Python is not needed to build or run the mod.

To regenerate the 18 colored variants:

```sh
python -m pip install -r mods/pirkko/tools/requirements.txt
python mods/pirkko/tools/generate_colors.py
```

The shared geometry is in `tools/source/pirkko.json`; the Minecraft dye palette
is in `tools/source/dye-colors.json`. Custom colors are defined in the generator.

## Polymer rendering

Polymer sends a flower-pot block for vanilla selection and collision, and an
item display for the Pirkko model. The resource pack hides the empty flower-pot
blockstate. Ordinary empty flower pots are restored through a separate display
using their vanilla model; filled pots and inventory pot items keep their assets.

This mod reserves the empty flower-pot blockstate appearance and its Polymer
display overlay. Startup fails if another mod already owns that overlay.
