# Metacraft Rivals v3: gloss that renders, real squid form, blobby shots, more weapons — design

Date: 2026-09-12. Status: approved design. Builds on v1 and v2 (`2026-09-11-…-prototype-design.md`,
`2026-09-12-…-v2-design.md`). Same constraints: Minecraft 26.2, Polymer 0.17.5+26.2, vanilla
clients, standalone module, branch off `dev`, fork only.

Playtest feedback on v2: the gloss never showed; squid form is a mild buff with no gameplay;
shots should look blobbier and bounce; three more weapons: a flamethrower-style sprayer, a
sniper that leaves a trail and paints the floor under it, and the iconic bucket.

## 1. Gloss on the right shader

Chunk geometry in 26.2 is drawn by `assets/minecraft/shaders/core/terrain.vsh` / `terrain.fsh`
(nearest/RGSS sampling, `ChunkSection` uniform block), not `block.*`, which is why v2's override
had no effect. v3 moves the same addition there:

- `terrain.vsh`: `out vec3 viewPos = (ModelViewMat * vec4(pos, 1.0)).xyz` (`ModelViewMat` comes
  from `chunksection.glsl`; `pos` is already camera-relative).
- `terrain.fsh`: after the existing sampling and fog-visibility mix, before the cutout test,
  read the raw sampled alpha (`tex.a` from the same `sampleNearest`/`sampleRGSS` result, before
  the `vertexColor` multiply) and apply the v2 gloss block under the same marker guard
  `abs(tex.a - 0.898) < 0.004`; set `color.a = 1.0` for paint.
- `block.vsh`/`block.fsh` overrides are removed from the pack and the resources; the test
  asserts the terrain pair instead.

## 2. Squid form, for real

Sneaking in own-colour ink (block paint or display quads under or one cell above the feet, as
v2) enters squid form; leaving the ink, standing up, or standing in enemy ink ends it.

While a squid, transient attribute modifiers (ids under `metacraft-rivals:squid/…`, applied with
`addOrUpdateTransientModifier`, removed on exit and on disconnect/server stop):

| Attribute | Modifier | Effect |
|---|---|---|
| `generic.scale` | ×0.5 (`ADD_MULTIPLIED_TOTAL −0.5`) | half size: low camera, small hitbox |
| `generic.movement_speed` | +80 % (`ADD_MULTIPLIED_TOTAL 0.8`) | fast swimming |
| `generic.jump_strength` | +0.2 (`ADD_VALUE`) | hops out of ink |
| `generic.step_height` | +0.5 (`ADD_VALUE`) | glides over slabs and stairs |

plus vanilla `INVISIBILITY` (as v2), ink refill ×4 (as v2), no shooting (as v2), and **wall
swimming**: while a squid and `horizontalCollision` is true and any horizontally adjacent cell
holds own-colour paint on the face the player pushes against, vertical velocity is set to 0.28
each tick (the player rides up the inked wall). Effects and modifiers are re-applied only when
missing (v2's `keep` rule) so no packets are spammed.

**Enemy ink** (standing in another colour): Slowness II (15 ticks, re-applied as v2), jump
strength modifier −1.0 (`ADD_VALUE`, effectively no jumping), and drip damage: 1.0 (half a
heart) every 20 ticks through `level.damageSources().magic()`; the damage is skipped in creative
and spectator and never below 1 health (it cannot kill, the prototype has no respawn flow).
Squid form is refused while in enemy ink.

`PlayerTick` owns all of it; a `SquidState` per player UUID records whether modifiers are on so
they are removed exactly once.

## 3. Blobby, bouncy shots

- **Look.** The `PaintBall` snowball is hidden from clients (`PolymerEntity.sendPacketsTo` →
  false) and a Polymer `ItemDisplayElement` in an `ElementHolder` attached with
  `EntityAttachment.ofTicking(holder, ball)` follows it: item = stick with `ITEM_MODEL =
  metacraft-rivals:blob` (a rounded 3D blob model: a 10-unit cube with chamfered 4-unit corner
  boxes, all faces `tintindex 0` on a white texture) and `DYED_COLOR` = the colour, scale
  0.55, `setTeleportDuration(1)`/interpolation 1 so it glides. Each tick the element's scale
  wobbles: `x/z = 0.55 + 0.12 sin(t·1.1)`, `y = 0.55 − 0.12 sin(t·1.1)` (squash and stretch).
- **Flight.** Gravity 0.05 (a lobbed arc), velocity per weapon.
- **Bounce.** On a block hit the ball splashes (v2), then, if it has bounces left (1 for the
  shooter, 0 for others unless stated), reflects its velocity about the hit face normal at 45 %
  energy, lifts 0.05 off the surface and keeps flying; the second impact splashes again and
  discards. Entity hits never bounce.
- **Sprayer droplets** (§4) are the same entity with a `lifetime` of 12 ticks after which they
  splash where they are (a ray straight down, then discard), so short-range spray still paints
  the floor.

## 4. Weapons

One item class, `PaintWeapon extends Item implements PolymerItem`, parameterised by a `Weapon`
enum; the v2 `PaintGun` becomes `Weapon.SHOOTER`. Shared: team colour from the holder, ink
(`Ink`, one tank per stack), `InkHud`, recoil/feel scaled per weapon, tank dye on the model,
`/rivals gun <weapon>` (default shooter) and `/rivals kit` (one of each).

| Weapon | Client item | Model | Fire | Ink | Cooldown | Ball |
|---|---|---|---|---|---|---|
| shooter | warped fungus on a stick | blaster-b (v2) | 1 ball | 1 | 4 | v 1.8, spread 2, 1 bounce, 3×3 splat |
| sprayer | warped fungus on a stick | Kenney blaster-o | 3 droplets per click, spread 9 | 1 per click | 4 | v 0.9, lifetime 12, single-face splat + rays, no bounce |
| charger | **spyglass** (hold = charge = scoped) | Kenney blaster-p | release: hitscan line | 4 + 8 × charge | 20 | none (line) |
| slosher | warped fungus on a stick | hand-built bucket | 4 balls in a 30° fan | 15 | 14 | v 1.1, gravity 0.06, 5×5 splat, no bounce |

**Charger.** The client item is a spyglass: vanilla's spyglass `use` always starts using and
zooms (a scope for free), and releasing sends the release. Server side `getUseDuration` = 72000,
`getUseAnimation` = SPYGLASS, `use` starts using (and refuses without a team / while refilling /
in squid form), `releaseUsing(stack, level, entity, timeLeft)` fires with `charge =
min(1, heldTicks / 20)`: range = 10 + 30 × charge blocks, a `level.clip` from the eyes along the
look vector, dust particles every 0.5 blocks along the line, a v2 `splash` where it ends
(entity hit → the block below the entity), and for every whole block along the line a
downward ray (up to 6 blocks) that paints the floor face it meets. Ink 4 + 8 × charge; a
release before 5 ticks does nothing (no ink). Recoil kick 6° at full charge.

**Sprayer.** Three droplets per click with spread 9 and velocity 0.9; each droplet's impact is a
single-face paint plus the v2 rays (no 3×3), 6 dust particles; muzzle burst every click. Because
vanilla clicks every 4 ticks while held, holding fire is a continuous cone.

**Slosher.** Four balls with yaw offsets −15°, −5°, 5°, 15° and pitch −20° from the look
direction, gravity 0.06, splat radius 2 (5×5) on impact, no bounce; a heavier sound layer
(`SLIME_BLOCK_STEP` at 0.6 pitch) and a 3° kick.

**Models.** blaster-o and blaster-p are converted by `tools/obj2mc.py` with their own accent
family (green for -o, green/grey for -p; the script gains `--accent r,g,b[;r,g,b]`); the
bucket is a hand-built element model (body, rim, handle; body faces `tintindex 0`) with the v1
palette approach. Each weapon has its own item definition with the dye tint.

## 5. Tests (game tests)

1. Terrain shader pair is in the pack with the marker guard; no `block.*` override remains.
2. Squid: a sneaking mock player in own ink gets the scale, speed, jump and step modifiers and
   invisibility; leaving removes all four modifiers; enemy ink applies Slowness II and the
   jump modifier and damages a survival mock player by 1.0 after 20 ticks but never below 1
   health; wall swim sets upward velocity when `horizontalCollision` is set and the adjacent
   wall face is own-colour paint.
3. Ball: a shooter ball hitting a floor with a bounce left ends up with upward velocity and 45 %
   speed and paints; a second impact discards; a sprayer droplet with lifetime 12 splashes the
   floor beneath after 12 ticks; the blob holder is attached while the ball lives and gone after
   discard (`holder.getAttachment() == null`).
4. Weapons: `/rivals kit` semantics via `Weapon.give`; each weapon's `use` costs its ink and
   applies its cooldown; the charger's `releaseUsing` paints a floor line under the scan (stone
   floor, look horizontal, at least 3 cells painted along the line) and ignores releases under
   5 ticks; the slosher throws 4 balls; the sprayer throws 3.
5. Assets: every weapon's item definition, model and (where generated) palette exist in the
   jar; converted models stay ≤ 400 elements.

## Out of scope

Respawn/death handling for drip damage, weapon switching UI, a real squid model, persistence of
quads, Iris-compatible gloss.
