# Pirkko

A server-side Fabric mod that adds small hostile cave mobs and placeable Pirkko heads to Minecraft
using Polymer. Players join with a vanilla client and accept the server's required
resource pack to see the models and colors.

The colored Pirkkos use a six-cuboid model: four stacked rectangular body sections
and two small, level arms. The model is 8 model pixels tall, its largest dimension, with a narrow lower
base and a wider belly. Its solid-color textures currently have no face.

## Colors

There are 18 colored variants, plus an animated rainbow:

- Laserviolett (`#CC99FF`): `laserviolett`
- Cerise (`#E83D84`): `cerise`
- All 16 Minecraft dye colors: `<color>`
- `rainbow`, an animated texture with the same 24 keyframes and slow drift as moredyes' RGB colour, so an RGB
  Pirkko and RGB wool change together (resource-pack animation, no server cost). Its inventory icon is a wave of the same colours running down the
  icon instead, fast enough to see (4 ticks per frame)

The dye color names are `white`, `orange`, `magenta`, `light_blue`, `yellow`,
`lime`, `pink`, `gray`, `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`,
`red`, and `black`. Their textures use Minecraft's DyeColor texture colors.
Minecraft pink and cerise are separate variants. Models are `pirkko:pirkko_<variant>`.

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

Each mob chooses one of the 18 colors when it spawns and retains it across saves. The choice follows the
temperature of the biome at the surface above the spawn (caves have their own biomes): cerise is the coldest color and laserviolett the warmest, and the rest are ranked by how
warm they look (blues and white cold, greens and black neutral, pinks, browns, yellows, oranges and red warm).
Colors close to the biome's warmth are likelier, so frozen biomes mostly give cerise and blues, temperate ones
a mix of neutral colors, and deserts and badlands mostly laserviolett, red and orange. One spawn in ten ignores
temperature and picks any of the 18 colors at random. `/summon` without a `Color` follows the same rules.
Killing one drops exactly one matching Pirkko head, unless mob drops are
disabled. Looting does not increase the count. Natural despawning drops nothing.

Pirkkos also expire after 10 minutes of loaded time, so they cannot pile up around
players who never leave (vanilla's distance despawn only applies to mobs far from
every player). Expiry drops nothing and is saved with the mob. Name-tagged, leashed and
riding Pirkkos never expire.

```mcfunction
/summon pirkko:pirkko ~ ~ ~
/summon pirkko:pirkko ~ ~ ~ {Color:"cerise"}
```

The mob uses a 10 × 14 × 10 px attack/collision box,
with the unscaled model, so its hands reach 1 px past the box. Its client representation is an invisible
interaction box plus a Polymer item display; no client mod is required.

## Pirkko heads

Each variant is a real item, `pirkko:pirkko_<variant>`, so vanilla commands work:

```mcfunction
/give @s pirkko:pirkko_red
/setblock ~ ~ ~ pirkko:pirkko_rainbow
/setblock ~ ~ ~ pirkko:pirkko_cyan_wall[facing=north]
```

`/pirkko give <players> <variant>` (permission level 2) is a shortcut that takes the bare variant name.

Like vanilla heads, an item places a standing block (`pirkko:pirkko_<variant>`, 16 rotations) on a
floor or a wall block (`pirkko:pirkko_<variant>_wall`) on a side face. Clients see a player head with a
fully transparent skin; the figure is a Polymer item display inside it. Selection, collision and breaking
use the vanilla head's, and breaking a head drops its item through an ordinary loot table.

- On the floor the figure stands on the block and follows the head's rotation, with the arms across its front.
- On a wall the figure's feet are on the wall and it points away from it, arms level.
- The item is shown as a flat icon (`textures/item/head_<variant>.png`, from `tools/source/sprites`), except in an item
  frame, where it shows the figure itself at the frame's half scale. The figure's feet are on the surface the
  frame hangs on, so it stands on a floor, hangs from a ceiling and sticks out of a wall.

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

Runtime models, textures, item definitions, and translations are
stored under `src/main/resources`. Python is not needed to build or run the mod.

To regenerate the variants, including the transparent head skins and the rainbow animation:

```sh
python -m pip install -r mods/pirkko/tools/requirements.txt
python mods/pirkko/tools/generate_colors.py
```

The shared geometry is in `tools/source/pirkko.json`; the Minecraft dye palette
is in `tools/source/dye-colors.json`. Custom colors are defined in the generator.

## Polymer rendering

The head blocks implement Polymer's `PolymerHeadBlock`: the standing block maps to a vanilla player head and
the wall block to a wall head, and Polymer sends each client a skull block entity whose profile uses the
transparent `pirkko:entity/pirkko_<variant>` skin. `BlockWithElementHolder` supplies the item display, so
Polymer owns its lifetime. The mod has no mixins and no shaders.
