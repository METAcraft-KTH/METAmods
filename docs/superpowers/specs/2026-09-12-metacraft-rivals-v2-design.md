# Metacraft Rivals v2: feel, art, any-block paint, ink — design

Date: 2026-09-12. Status: approved design. Builds on
`2026-09-11-metacraft-rivals-paint-prototype-design.md` (v1), which stays the reference for
paint blocks, donors, the tally and commands. Same constraints: Minecraft 26.2, Polymer
0.17.5+26.2, vanilla clients, standalone module, branch off `dev`, fork only.

Playtest feedback on v1: no splash or area effect on impact, no recoil or "chunk" when firing,
splats too small and visibly tiled, gun pointing upward in first person, art too rough, paint
refused by stairs and other non-full blocks. Added asks: an ink tank with reload and a visible
ammo bar, squid form when sneaking on own paint.

## 1. Impact: splash and area paint

`PaintBall.onHitBlock` / `onHitEntity` call one new entry point:

```java
static int Painter.splash(ServerLevel level, Vec3 impact, BlockPos struck, Direction face, PaintColor colour, RandomSource random)
```

1. The v1 3×3 blob on `struck`/`face` (unchanged).
2. Fourteen rays from `impact + face.normal * 0.05`: the six axis directions and the eight
   body diagonals (normalised), length 1.5 blocks, via
   `level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ball))`.
   Every `BLOCK` hit paints `paintFace(hit.getBlockPos(), hit.getDirection())`. So a floor shot
   next to a wall also paints the wall and the corner fills in.
3. Particles: `DustParticleOptions(colour.rgb, 1.6f)`, 24 at the impact point (spread 0.35,
   speed 0.02) and 4 at every ray hit. Vanilla renders dust in any RGB.
4. Sound at impact: `SLIME_BLOCK_HIT`, volume 0.8, pitch 1.3.

Entity hits use the ball's position as `impact` and the block under the entity as `struck`
with `face = UP`, then the same rays.

## 2. Firing feel

In `PaintGun.use` after a successful shot, for a `ServerPlayer` with a connection:

- **Camera kick:** `ClientboundPlayerRotationPacket(0f, true, -2.5f, true)` (relative pitch up),
  and one tick later `(0f, true, 1.8f, true)` to settle. A tiny `Recoil` queue on
  `ServerTickEvents.END_SERVER_TICK` holds the pending settle per player.
- **Push:** `player.push(-look.x * 0.06, 0, -look.z * 0.06); player.hurtMarked = true` (the
  flag makes the server send the velocity).
- **Muzzle burst:** 10 dust particles at `eye + look * 0.9`, spread 0.1.
- **Sound:** `SNOWBALL_THROW` volume 0.7 pitch 0.7 layered with `SLIME_BLOCK_PLACE` volume 0.5
  pitch 1.4.
- Ball: `VELOCITY = 1.8f`, `INACCURACY = 2.0f`; gravity and cooldown (4 ticks) unchanged.

## 3. Splat art and blockstate variety

Source: Kenney Splat Pack (CC0), eight chunky silhouettes (`splat03, 04, 05, 06, 07, 12, 13,
14` from the 256 px set) committed under `src/main/resources/kenney/splat/` with Kenney's
licence file, and a credit line in the README.

At pack build (`SplatArt`, replacing v1's `SplatTexture`), for every colour, shape and
rotation (0/90/180/270):

- crop the centre 72 % of the sprite (the blob overfills the tile so neighbours merge), scale to
  32×32, threshold alpha > 128 into a mask;
- fill = the colour, rim = the mask minus a 2-texel erosion, at 62 % brightness (style B);
- **paint texels get alpha 229 (0.9)**, the marker the gloss shader reads; everything else is
  transparent;
- write `assets/metacraft-rivals/textures/block/splat_<colour>_<shape>_<rot>.png` (96 files) and a
  model `assets/metacraft-rivals/models/block/splat_<colour>_<shape>_<rot>.json`: the vanilla lichen
  quad (`from [0,0,0.1] to [16,16,0.1]`, north and south faces, `ambientocclusion false`)
  pointing at that texture. Rotation is baked into the texture because vanilla blockstates can
  only rotate about X and Y, not about a north/south face's normal.

Blockstate overrides, also generated at pack build, replace vanilla's
`assets/minecraft/blockstates/{sculk_vein,resin_clump,glow_lichen}.json`: the same multipart
structure as vanilla (face property → model with vanilla's per-face `x`/`y` rotation and
`uvlock`, plus the all-faces-false entry), but each `apply` is a list of the colour's 32 variant
models with equal weight. The client picks a variant per block position, so adjacent cells
stop tiling.

`PaintColor.donorTexturePath()` and the v1 texture override go away.

## 4. Paint on any block

`Painter.paintFace` accepts a surface when `!state.isAir() && !state.canBeReplaced()
&& !(block instanceof PaintBlock) && state.getFluidState().isEmpty()`. Two cases:

- **Full face** (`Block.isFaceFull(state.getCollisionShape(level, pos), face)`): v1 behaviour, a
  paint block in the cell in front.
- **Otherwise** (stairs, slabs, fences, panes, walls, glass panes …): a `PaintDisplays` quad set.
  `PaintBlock.isValidStateForPlacement` is overridden with the same relaxed rule so paint next to
  such blocks does not pop off.

`PaintDisplays` (per level, in memory like the tally): for the struck block's shape
(`getCollisionShape`, or `getShape` if that is empty) it takes every `AABB` from `toAabbs()` and,
for the box face on side `face`, spawns one Polymer `ItemDisplayElement` inside an `ElementHolder`
attached with `ChunkAttachment.of(holder, level, cell)`: item = vanilla `stick` with
`ITEM_MODEL = metacraft-rivals:splat_quad_<shape>` (random shape) and `DYED_COLOR = colour`,
`ItemDisplayContext.FIXED`, translated to the box face centre pushed 0.01 out, scaled to the
face's width and height, rotated so the quad faces `face`. The item definitions
`assets/metacraft-rivals/items/splat_quad_<shape>.json` are `minecraft:model` with a dye tint over
`models/item/splat_quad_<shape>.json`, a flat 16×16 quad with `tintindex 0` on a white mask
texture (`textures/item/splat_quad_<shape>.png`, generated with the block art). One cell holds
one colour: a hit in another colour destroys the holder and rebuilds it. The tally counts each
holder's quads as faces of its colour and `reset` destroys the holders. Holders are not
persisted; a restart drops them (same limit as the tally).

## 5. Gloss shader

The pack overrides `assets/minecraft/shaders/core/block.vsh` and `block.fsh` (copies of
26.2's files with additions). The vertex shader adds `out vec3 viewPos =
(ModelViewMat * vec4(pos, 1.0)).xyz`. The fragment shader, after sampling:

```glsl
float marker = texture(Sampler0, texCoord0).a;
if (marker > 0.85 && marker < 0.95) {          // paint texels only
    vec3 n = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
    vec3 v = normalize(-viewPos);
    float t = GameTime * 1200.0;
    vec3 l = normalize(vec3(0.3 + 0.15 * sin(t), 0.8, 0.5 + 0.15 * cos(t)));
    float spec = pow(max(dot(reflect(-l, n), v), 0.0), 24.0) * 0.55;
    float fresnel = pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.25;
    color.rgb += spec + fresnel;
    color.a = 1.0;
}
```

(`GameTime` comes from `#moj_import <minecraft:globals.glsl>`.) All other texels keep vanilla
behaviour byte for byte. Known limit: shader packs replace core shaders and lose the gloss.

## 6. Gun model from Kenney's Blaster Kit

`tools/obj2mc.py` (stdlib + Pillow) converts `blaster-b` (CC0) into the element model:

1. parse the OBJ (triangles, UVs) and the kit's `colormap.png`;
2. surface-voxelise at 40 voxels along the longest axis (Z), each voxel taking the colour of the
   triangle that filled it (nearest colormap texel);
3. greedy-merge equal-colour voxels into boxes (runs along X, then Y, then Z);
4. quantise colours to a palette texture (`paint_gun_palette.png`, one texel per colour), map the
   orange accent family (`#FF7E44`, `#CF534F` and their shades) to white with `tintindex 0`
   (the dye-tinted tank);
5. emit `paint_gun.json` in a gun frame: barrel along −Z, up +Y, with display transforms
   modelled on vanilla's crossbow (which points forward in hand):
   `firstperson_righthand {rotation [-90, 0, -55] …}` as the starting point.

Element cap 400; if greedy merging exceeds it, lower the resolution to 32. The script is
re-runnable and the generated JSON is committed. Orientation is tuned live against F2
screenshots from the vanilla client (`run-vanilla/screenshots/`), one display-transform edit per
round, until the muzzle points forward and the tank sits on top in first and third person.

## 7. Ink, reload, ammo bar

- Ink lives on the server-side gun stack in `DataComponents.CUSTOM_DATA` under `rivals_ink`
  (int, 0..40; absent means 40) and `rivals_refill_until` (server tick). `Ink` is the only
  reader/writer.
- Each shot costs 1. At 0, `use` starts a refill: `rivals_refill_until = now + 30`, sound
  `BOTTLE_FILL`, cooldown 30 ticks; `use` during a refill does nothing; the first tick past the
  deadline sets ink to 40.
- Passive refill: standing in own-colour paint gives +1 ink every 5 ticks; squid form +4.
- **Ammo bar** (`InkHud`): every 10 ticks, and immediately after a shot, each online player holding
  a gun in either hand gets an action-bar message `INK ██████░░░░ 24/40` (ten cells, one per 4
  ink) in the team colour (`TextColor.fromRgb`), `REFILLING…` during a refill, `SQUID` appended
  in squid form. Sent with `sendSystemMessage(text, true)`; the action bar fades in about two
  seconds, so the 10-tick cadence keeps it visible.

## 8. Squid form and enemy paint

`PlayerTick` on `END_SERVER_TICK` for every online player: `cell = player.blockPosition()` (floor
paint lives in the cell the player stands in).

- Own colour and `isShiftKeyDown()`: squid form. Apply, every tick, `INVISIBILITY` (15 ticks,
  ambient, no particles, no icon) and `SPEED` amplifier 1 (15 ticks); the gun refuses to fire
  ("Can't shoot in squid form" on the action bar); fast ink refill. Leaving the paint or
  un-sneaking simply stops the re-application and the effects expire within a second.
- Enemy colour (any colour that is not the player's team): `SLOWNESS` amplifier 0, 15 ticks.
- No team: nothing.

## Tests (game tests unless noted)

1. Splash rays paint the wall next to a floor hit (stone floor + one wall block; splash at the
   floor cell beside the wall; the wall's face is painted).
2. A stair top is painted: after `paintFace` on a stair, `PaintDisplays` has one holder in that
   cell with ≥ 1 element in the colour; the tally counts it; `reset` removes it.
3. Blockstate overrides: the pack builder output for `sculk_vein` parses, has an entry per face
   with 32 `apply` variants, and every referenced model and texture path was also added.
4. Splat art: a generated 32×32 PNG has paint texels at alpha 229 and transparent texels at 0.
5. Shader files exist in the pack output and contain the marker guard string.
6. Ink: a fresh gun reads 40; a shot reads 39; at 0 `use` starts a refill and a tick past the
   deadline reads 40; standing in own paint for 5 ticks adds 1.
7. Squid: a sneaking mock player in own-colour paint gets invisibility and speed after one tick;
   an enemy-colour cell gives slowness; the gun refuses to fire in squid form.
8. Gun model JSON: elements ≤ 400, all coordinates within −16..32, at least one tinted face.
9. Recoil and the action bar are not testable with mock players (no connection) and are checked
   by eye.

## Out of scope (v3)

Persisting display quads and the tally; arena and rounds; damage on enemy paint; a real
squid model; sound design beyond the vanilla layers; Iris-compatible gloss.
