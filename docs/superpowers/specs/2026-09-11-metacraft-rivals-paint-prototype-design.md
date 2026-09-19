# Metacraft Rivals: paint prototype — design

Date: 2026-09-11. Status: approved design, prototype scope.

A Splatoon-like gamemode for vanilla clients: a paint gun shoots coloured blobs that leave
paint on the faces of blocks, two teams compete for coverage. This spec covers the prototype
only: paint blocks, the gun and projectile, minimal teams and a coverage score. No arena, no
round loop, no player-side effects (speed on own paint, damage on enemy paint) yet.

## Constraints

- **Target:** Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2, Polymer
  0.17.5+26.2, Java 25 — exactly what the `dev` branch of METAmods pins. The module is built
  off `dev`, not the `26.3` migration branch.
- **Vanilla clients.** Nothing on the client but the auto-served resource pack (Polymer
  autohost, marked required, as moredyes and ovvar do).
- **Not shipped in `dist`.** The pack retextures three vanilla blocks as paint, which is only
  acceptable on a dedicated Rivals server. The root build gets a `standaloneMods` list that
  `initDist` and `dist`'s `CheckMods` both skip; Rivals runs on its own with
  `./gradlew mods:metacraft-rivals:runServer`.
- **Repository:** the user's fork `Froosty11/metamods` (`origin`). No PR to upstream.

## How paint is shown (the donor decision)

There is no vanilla block whose states are both genuinely unused and free of collision while
covering several faces of a cell. Polymer's own `BlockModelType` pools have no multiface type.
The survey of no-collision, multi-face client blocks in 26.2:

| Block | Faces | States | Free for us? |
|---|---|---|---|
| Glow lichen, sculk vein, resin clump (`MultifaceBlock`) | all 6, independently | 128 each | only if the block is given up wholesale |
| Pale moss carpet with `bottom=false` | 4 walls, none/low/tall | 81 | walls only, no floor or ceiling |
| Vine | 5 (no floor), biome tinted | 32 | no floor |
| Redstone wire | floor + 4 wall segments | 1296 | no ceiling, power tinted |
| Tripwire | floor only | 128 | already Polymer's flat pool |

Decision: give the three multiface blocks up wholesale, one colour each. Sculk vein and resin
clump carry the two team colours (neither emits light); glow lichen is the spare third colour,
vanilla's lit one (light 7), which is acceptable for a third, "special" paint. Its light
behaviour with our block is **unverified**: the server-side paint block emits no light, so the
server's light data says dark, and Polymer re-syncs light after block changes; whether the client
shows a steady glow, a brief flash, or nothing at all has to be checked with a real client.
Real sculk veins, resin clumps and glow lichen look like paint on a Rivals server; that server
has no use for them.

Consequences that follow and are accepted for the prototype:

- **One colour per cell.** A cell (the air block in front of a surface) holds one paint block,
  so a hit in another colour recolours the whole cell, keeping its faces.
- **No entities for paint.** Paint is chunk geometry, scales to any arena, costs nothing per
  block at runtime.
- Only the three donor texture files are overridden in the pack; the donor blockstate and
  model files stay vanilla (a 0.1-thick quad per face, no ambient occlusion).

## Module

`mods/metacraft-rivals`, mod id `metacraft-rivals`, package `nu.metacraft.rivals`, version
property `rivals_version = 0.1.0`, entry point `nu.metacraft.rivals.Rivals`. Copied from the
existing module template (build.gradle, fabric.mod.json, checkstyle). Dependencies:
`polymer-core`, `polymer-resource-pack`, `polymer-autohost`, `fabric-api`; `metacraft-lib`
comes in from the root build automatically. No mixins, no access widener, no datagen: every
asset is either a JSON file in `src/main/resources` or generated at pack build time.

Layout:

```
nu.metacraft.rivals.Rivals            entrypoint: content, pack, commands, tally ticking
nu.metacraft.rivals.PaintColor        enum: id, display name, RGB, donor block
nu.metacraft.rivals.paint.PaintBlock  MultifaceBlock + PolymerBlock, one per colour
nu.metacraft.rivals.paint.Painter     splat geometry: which cells/faces a hit paints
nu.metacraft.rivals.paint.PaintTally  per-level counts of painted faces per colour + painted cells
nu.metacraft.rivals.gun.PaintGun      item: right-click shoots, colour from team
nu.metacraft.rivals.gun.PaintBall     Snowball subclass + PolymerEntity, paints on hit
nu.metacraft.rivals.pack.SplatTexture procedural splat PNG per colour, added to the pack
nu.metacraft.rivals.RivalsCommands    /rivals setup | gun | score | reset
nu.metacraft.rivals.gametest.*        Fabric game tests
```

## Components

### PaintColor

```java
enum PaintColor { MAGENTA("magenta", 0xEA2C8E, Blocks.SCULK_VEIN),
                  LIME("lime", 0x8DE800, Blocks.RESIN_CLUMP),
                  CYAN("cyan", 0x00D5F5, Blocks.GLOW_LICHEN) }
```

Each entry knows its id (also the vanilla team name), a display name, an RGB and the donor
block. `donorTexture()` is `assets/minecraft/textures/block/<donor path>.png`. A static
`byTeam(PlayerTeam)` resolves a team to a colour by name, or empty.

### PaintBlock

`PaintBlock extends MultifaceBlock implements PolymerBlock`, registered once per colour as
`metacraft-rivals:paint_<id>`. Properties: `noCollision`, `noOcclusion`, `instabreak`,
`noLootTable`, `pushReaction(DESTROY)`. Keeps vanilla's six face properties and
`waterlogged`; the block is never placed waterlogged. No item.

`getPolymerBlockState(state, ctx)` returns the donor's default state with each of the six face
properties copied from `state` and `waterlogged=false`. At registration the block asserts the
donor state has all six face properties and `waterlogged`; a mismatch is a startup failure with
a message naming the colour. No Polymer pool request is involved, so nothing can run out.

Vanilla `MultifaceBlock` behaviour that stays: faces whose support block disappears are removed
by `updateShape`, and a block with no faces becomes air. The tally must see those removals
(see PaintTally).

### Painter

Pure geometry, no I/O beyond the level. Entry point:

```java
static int splat(ServerLevel level, BlockPos struck, Direction face, PaintColor colour, RandomSource random)
```

`struck` is the solid block that was hit, `face` the struck face. The canvas cell is
`struck.relative(face)`. The blob is the 3×3 of positions in the plane of `face` around
`struck`; the four corners are each dropped with probability 0.5 so blobs look organic. For
each candidate surface block `q` in the blob: paint only if `MultifaceBlock.canAttachTo(level,
face, canvas, ...)` holds for that surface and the canvas cell `q.relative(face)` is air or a
`PaintBlock`. Painting a cell:

- air → `paint_<colour>` with the single face property `face.getOpposite()` set (the lichen face
  property names the side the paint sits on, which is the side facing back into the surface);
- same colour → same state with that face property set;
- another colour → `paint_<colour>` with the union of the old faces and the new face.

Returns the number of faces newly painted in this colour. Every state change goes through
`level.setBlock(pos, state, UPDATE_ALL)`, so the client sees the donor change immediately and
the tally hooks fire. No wrapping onto adjacent walls yet.

### PaintTally

One instance per `ServerLevel`, held in a map on `Rivals`. It holds only a `Set<BlockPos>` of
cells the `Painter` has ever painted; `Painter.splat` adds every cell it writes. Counts are not
kept as deltas: `count()` walks the set, reads each cell's current state, sums the set face
properties per colour, and drops cells that no longer hold a `PaintBlock` (paint removed by a
player, by `updateShape` when its support block went, or by `/rivals reset`). Vanilla's
`setBlock` only calls `onPlace` when the block changes, so a face added to a same-colour cell
would be invisible to hooks; walking a few thousand positions once a second is cheap and cannot
drift. In-memory only: a server restart forgets the cell set until paint is shot again.
Documented prototype limit.

`share(colour)` = faces of that colour ÷ all painted faces (0 when nothing is painted).

Score display: one server bossbar per colour that has ever been painted this session, named
"<Display name> 43 %", colour matching the team, progress = share, refreshed every 20 ticks
from `ServerTickEvents.END_SERVER_TICK`, shown to every online player.

### PaintGun

`PaintGun extends Item implements PolymerItem`, `metacraft-rivals:paint_gun`, stack size 1.
Client item: `minecraft:warped_fungus_on_a_stick` (no client-side use behaviour) with the item
model swapped to our own 3D model, named "Paint Gun". Tooltip line: "Shoots paint in your team's
colour".

**Model.** A custom Blockbench-style element model, designed in
`2026-09-11-paint-gun-design-sheet.md` (parts, palette, frame, display transforms; SVG views
alongside) and shipped as `assets/metacraft-rivals/models/item/paint_gun.json` with the 8×8
palette texture `textures/item/paint_gun_palette.png`. The item definition
`assets/metacraft-rivals/items/paint_gun.json` is a `minecraft:model` with one
`minecraft:dye` tint (default white). The tank's faces carry `tintindex 0`, so the tank takes
the colour of the stack's `minecraft:dyed_color` component. That component lives on the
server-side stack: every inventory tick the gun compares its holder's team colour with the
stack's dye and rewrites it only when it differs (removing it for a holder without a team,
which shows a white tank). Polymer copies the component to every viewer, so the holder's
colour is what opponents see too; resolving it per packet recipient instead would tint each
gun in the viewer's own colour. One model therefore serves every colour; there are no
per-colour model files.

`use(level, player, hand)` on the server:

1. Resolve the colour via `PaintColor.byTeam(player.getTeam())`. None → action-bar message
   "Join a team first: /team join magenta" and `InteractionResult.FAIL`.
2. Spawn a `PaintBall` with `Projectile.spawnProjectileFromRotation` at velocity 1.5,
   inaccuracy 1.0, gravity default (0.03), and a throw sound (`SNOWBALL_THROW`, pitch 0.8).
3. `player.getCooldowns().addCooldown(stack, 4)`. Vanilla clients keep sending use packets while
   the button is held, so this is the fire rate.

### PaintBall

`PaintBall extends Snowball implements PolymerEntity`, entity type
`metacraft-rivals:paint_ball` built like vanilla's snowball (size 0.25, tracking range 4, update
interval 10) and registered with `PolymerEntityUtils.registerType`. `getPolymerEntityType` is
`EntityType.SNOWBALL`. Its item stack is a vanilla `firework_star` with a `FIREWORK_EXPLOSION`
component whose colour is the paint colour, so the client draws a tinted round blob without any
texture. No synched entity data is needed: the ball keeps its `PaintColor` in a plain field,
the projectile is server-only state, and the item stack carries the visual.

`onHitBlock(hit)` → `Painter.splat(level, hit.getBlockPos(), hit.getDirection(), colour, random)`.
The client shows vanilla's snowball break particles; a colour-tinted burst is left for later.
`onHitEntity(hit)` → find the block under the entity's feet (`blockPosition().below()`) and splat
its up face. Both discard the entity afterwards. `Snowball.onHitEntity` is overridden, so its
damage to blazes is not inherited.

### SplatTexture

`static byte[] png(PaintColor colour)`: a 16×16 RGBA image. A deterministic mask (seeded by the
colour ordinal) unions three or four discs of radius 3 to 6 texels around the centre plus a few
single-texel drips; inside the mask the pixel is the colour at alpha 255 with a slight darker
ring at the edge, outside it is transparent. Written with `javax.imageio.ImageIO` (present on the
server JVM). Registered on `PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT`:
`builder.addData(colour.donorTexture(), SplatTexture.png(colour))` for every colour. Replacing
the procedural art later is dropping a file at the same path into
`src/main/resources/assets/minecraft/textures/block/` and skipping the generated one.

### Commands

`/rivals`, for game masters (fabric-permissions-api node `metacraft.rivals`, default
`PermissionLevel.GAMEMASTERS`, the same idiom as the other METAmods commands):

- `setup` — for every `PaintColor`, create (or update) the vanilla scoreboard team named after the
  colour id: display name, team colour (the nearest `TeamColor`), friendly fire off, collision
  rule never. Replies with the teams and the join command.
- `gun` — gives the executing player a paint gun.
- `score` — prints faces and share per colour in the executing player's level.
- `reset` — removes every tracked paint block in the level (set to air) and zeroes the tally.

## Data flow

1. Player right-clicks with the gun → `PaintGun.use` → team → colour → `PaintBall` spawned.
2. `PaintBall` flies (vanilla projectile physics) → `onHitBlock` → `Painter.splat`.
3. `Painter` computes the blob → `level.setBlock` per cell.
4. `Painter` tracks every cell it writes in `PaintTally`; counts are read live once a second.
5. Every 20 ticks → bossbars refreshed from the tally.
6. Polymer sends each `PaintBlock` state as the donor state to clients; the required pack has the
   donor's texture replaced by the splat.

## Error handling

- Donor property mismatch at startup → `IllegalStateException` naming the colour; the server
  does not start half-registered.
- No team on shoot → message, no projectile, no cooldown.
- Every `PaintBlock` state maps to a donor state by construction (the six face properties are
  verified at startup), so there is no runtime mapping failure to handle.
- `/rivals reset` in a level with no tally → "Nothing painted".
- Pack build failure (ImageIO throws) → propagate; a Rivals server without splat textures is not
  worth starting.

## Testing

Fabric server game tests in `nu.metacraft.rivals.gametest.RivalsGameTests`, run with
`./gradlew mods:metacraft-rivals:runGameTest` (Fabric API's game test runner on a throwaway
server, as moredyes does on its branch):

1. **Donor mapping:** for every colour and a handful of face combinations, the client state is the
   donor block with identical face flags and `waterlogged=false`.
2. **Floor splat:** a splat on the up face of a stone block paints the cell above with
   `down=true` in that colour, and the returned count is ≥ 1 and ≤ 9.
3. **Recolour:** a cell painted magenta with `down` and `north` faces, hit by lime, becomes
   `paint_lime` with both faces.
4. **Blob bounds:** after a splat on a flat 5×5 stone floor, only cells within the 3×3 around the
   hit are painted, and none where the surface below is missing.
5. **Tally:** counts follow splats and recolours; `reset` zeroes the counts and removes the blocks.
6. **Gun without a team:** using the gun spawns no `PaintBall` and applies no cooldown.
7. **Gun with a team:** using the gun spawns one `PaintBall` whose item is a firework star with
   the team colour.
8. **SplatTexture:** the PNG decodes to 16×16 with both opaque and transparent pixels, and the
   opaque pixels are the colour.
9. **Gun tank colour:** the client-side stack for a player on the magenta team carries a
   `minecraft:dyed_color` of the magenta RGB; for a player without a team it carries none.
10. **Gun model assets:** the mod jar contains the item definition, the model and the palette
    texture at their expected paths, and the model's element coordinates stay within -16..32.

Pixels are checked by eye with a vanilla client against the dev server (the
`tools/vanilla_client.py` harness from the moredyes branch can be copied if wanted).

## Build and run

- `./gradlew mods:metacraft-rivals:build`
- `./gradlew mods:metacraft-rivals:runServer` — `run/` ships `eula.txt`, `server.properties`
  with `online-mode=false`, and `config/polymer/auto-host.json` enabling the pack host, mirroring
  ovvar's run directory.
- In game: `/rivals setup`, `/team join magenta @s`, `/rivals gun`, shoot, `/rivals score`.

## Out of scope (next iterations)

Arena bounds and a round timer; wrapping blobs around edges onto walls; speed on own paint and
damage on enemy paint; a persistent tally; real splat art and a gun model; more than three
colours (needs a different donor strategy, most likely display entities for extra colours).
