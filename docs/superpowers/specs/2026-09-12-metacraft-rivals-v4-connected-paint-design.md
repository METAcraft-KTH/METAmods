# Metacraft Rivals v4 — connected paint (design)

Over v3. The user's ask: "liquid should probs be like the full block side; and edges rounded so like
connected textures mod". Two teams now: "We just need 2 teams. IT and DATA see ovve colors."

## 1. What changes

Paint stops being a decal picked at random per cell. A painted region renders as a continuous
sheet: every face inside the region is a full square, and only the faces on the region's border
carry a rounded, slightly wobbly edge on the sides that face unpainted cells. Vanilla clients still
see only vanilla blocks; the trick is that the server now tells the client, per cell and per face,
which of the four in-plane neighbours are painted, and the resource pack turns that into the right
edge tile.

Two teams and two colours, taken from the ovvar chapters: **DATA** `#BD3754` and **IT** `#8A57BD`
(the most saturated cloth pixels of `art/ovvar/data.png` / `it.png`, sampled the way ovvar's
mockups do). `PaintColor` becomes `DATA, IT`; `/rivals setup` creates teams `data` and `it`;
the tank dye, blob, bossbars and tally follow.

## 2. The state budget

A single-face cell needs its face (6) and four connection bits (16): 96 states per colour. A cell
with paint on more than one face (a corner: floor plus wall in the same air cell) keeps v3's
per-face encoding: 2^6 − 1 − 6 = 57 combinations (the empty and single-face combinations never
occur in this mode). Per colour 153, for two colours 306.

Client states that render whatever we say, have no collision, emit no light and have no
client-side behaviour (no `animateTick` particles, no fluid, no tint, not climbable). Glow lichen is
**not** in the pool: `GlowLichenBlock.emission` gives light 7 to every state with a face, so paint on
its states would glow while the rest stayed dark.

| donor | usable states | note |
|---|---|---|
| sculk vein | 63 | six face booleans; waterlogged renders water, the all-false state renders nothing |
| resin clump | 63 | same |
| tripwire | 128 | attached × disarmed × powered × n/e/s/w; no `animateTick`, thin outline shape only |
| redstone wire, power 0 | 81 | n/e/s/w ∈ {none, side, up}; `animateTick` only spawns dust when power > 0; our model carries no tint index |

335 states; 306 used. Which donor state stands for which paint state is chosen **by shape**, not by
slicing the pool into contiguous runs. The pack can repaint a borrowed state but cannot change its
outline, and the client draws the targeted-block highlight from the client state's shape — contiguous
slicing put floor cells on sculk-vein states drawing slabs on random faces and wall cells on a
tripwire square lying at the floor, so the highlight box floated nowhere near the ink. Four rules,
applied in this order by a deterministic allocator in `PaintStates` that spends each donor state once
and throws rather than reusing one or running a pool dry:

1. **Splat cells are exact.** A multiface donor's shape is the union of 1-px slabs on its set faces,
   so a splat mask takes the state whose six face flags *are* that mask: DATA on sculk vein, IT on
   resin clump, 57 each. The outline is exactly the painted faces.
2. **Floor cells (attach `DOWN`) are flat.** Tripwire `attached=true` — `column(16, 1, 2.5)`, a
   full-square 2.5-px slab lying on the floor. 32 states (16 per colour).
3. **Wall cells (attach N/E/S/W) are striped.** A redstone-wire state whose side on the attach
   direction is `up` carries `boxZ(10, 16, 0, 1)`, a full-height 1-px strip climbing that face — the
   nearest thing any donor has to a wall decal. The 65 strip states are handed out fewest-other-`up`
   first, each to whichever of its own `up` directions is currently shortest, which spreads them
   17/16/16/16 over N/E/S/W: at least eight per colour per direction. The rest fall back to
   the leftover flat states (tripwire `attached=true` remainder, then the 16 redstone states with no
   `up`) and finally to half boxes.
4. **Ceiling cells (attach `UP`) are half boxes.** Tripwire `attached=false` — `column(16, 0, 8)`.
   No donor draws a ceiling slab with 16 spare states, and ceilings are the rarest cell there is.

The 306 come out as 114 exact multiface (splats), 32 flat (floors), 65 strip + 48 flat + 15 half
(walls) and 32 half (ceilings); 29 states are left over (12 multiface and 17 half — every strip and
every flat state is spent).

`connected()`, `splat()`, `all()` and `entry()` keep their signatures — `entry()` is now a reverse map
built alongside the table — so `PaintArt` follows the allocator without change.

Two game tests. `paintStatesAreUniqueAndSafe` asserts the 306 states are distinct, never waterlogged,
never all-false, and emit no light (`BlockState.getLightEmission()` is 0), and that `entry()` inverts
`connected()`/`splat()` for every input. `paintStatesOutlineTheInk` asserts the shapes: every splat
state's six face flags equal its mask; every floor state's `getShape(EmptyBlockGetter.INSTANCE,
BlockPos.ZERO).bounds()` has `maxY ≤ 3/16` over the full square; and for each wall direction at least
8 of each colour's 16 states reach that face with a box at least 15/16 tall. Real tripwire, redstone
dust, sculk veins or resin clumps placed by players in an arena render as paint; same caveat as v1,
documented.

## 3. Blocks and encoding

Two server blocks per colour:

- `ConnectedPaintBlock` — properties `FACE` (`Direction`, 6) and `NEG_U, POS_U, NEG_V, POS_V`
  (the four in-plane connection bits, §5's world frame). Non-collidable; outline shape = the thin
  face slab like the multiface donors so rays and `paintable` keep working.
- `PaintBlock` (v3's `MultifaceBlock` subclass) — the splat fallback for multi-face cells,
  unchanged apart from the two-colour enum and its new client mapping.

In-plane axes per face normal (the same table the shader uses, §5):

| normal axis | u | v | NEG_U | POS_U | NEG_V | POS_V |
|---|---|---|---|---|---|---|
| Y (floor, ceiling) | x | z | west | east | north | south |
| X (east/west walls) | z | y | north | south | down | up |
| Z (north/south walls) | x | y | west | east | down | up |

A neighbour counts as connected when the cell one step in that direction holds paint of the same
colour on the same face, in either mode.

## 4. Painting

`Painter.paintFace(level, cell, face, color)` is the only mutation and now does three things:

1. Set the cell: no paint → connected block for `face`; same colour, different face already →
   splat block with both faces; same colour, same face → no change (return false); other colour →
   replace with a connected block for `face` (overpaint wipes the cell, as in v3).
2. Recompute the cell's connection bits from its four in-plane neighbours.
3. Recompute the connection bits of those four neighbours (only the ones in connected mode with the
   same colour and face; splat-mode cells have no bits).

Everything above `paintFace` — `splat`, `splash`, `line`, radius, rays, display quads for
non-full blocks, tally — is unchanged. `PaintTally` counts one face for a connected cell and the
popcount for a splat cell. `PaintDisplays` quads (slabs, stairs, panes) stay entities; they are the
exception, not the rule, and the user asked for no new entities.

## 5. Art: the shader draws the edges

The Kenney splat variants leave the blocks (the item icon keeps one). The user's suggestion, and
the better design: per cell the client only needs the four bits, and the fragment shader does the
rounding, so there are no edge tiles to draw and the border is a smooth curve at any resolution.

- **Textures.** Per colour 16 textures, 16×16, uniform: the paint colour with the four connection
  bits in the low nibble of the red channel (`r = (base & 0xF0) | bits`), alpha 229 (the paint
  marker, unchanged). Uniform sprites survive mipmapping exactly, which is what makes reading bits
  out of a texel safe. No animation frames; the sheen is animated in the shader instead (the
  v3 liquid-pass frames are dropped).
- **Bit order** is in world terms so the shader can recover it from the face normal alone: for a
  face whose normal is Y the two in-plane axes are (x, z); for X it is (z, y); for Z it is (x, y).
  Bits are `−u, +u, −v, +v` in that order. The server (`ConnectedPaintBlock`) uses the same table,
  the same table is in §3.
- **Shader** (`terrain.fsh`, inside the marker guard): the chunk-space normal is
  `cross(dFdx(chunkPos), dFdy(chunkPos))`; pick the two in-plane axes from its dominant component;
  `p = fract(in-plane chunkPos)` in 0..1; bits from the texel. Signed distance of a rounded box
  whose unconnected sides are inset by 0.06 and whose corners between two unconnected sides have
  radius 0.28; connected sides extend past the cell so the seam is invisible. Add a small
  time-and-position wobble to the inset on unconnected sides. `discard` outside; inside, the
  v3 liquid pass's rim (meniscus on the edge, now computed from the same distance instead of
  neighbour texels), moving multi-wave normal, mixed glint, sheen and fresnel.
- **Models.** One quad model per face (six, shared by all textures via `#paint`), 0.1/16 off the
  face like the multiface donors; blockstate overrides for the four donors point each client state
  at the model for its face with the texture for its colour and bits. Splat-mode states use the
  all-connected texture on every painted face.

## 6. Commands, teams, HUD

- `/rivals setup` → teams `data` (colour `#BD3754`) and `it` (`#8A57BD`); `/team join data|it`.
- `PaintColor.byTeam` maps by team name. `ScoreBars` shows two bars. `/rivals score`, `reset`,
  `gun`, `kit` unchanged.
- Lang: "DATA paint", "IT paint".

## 7. Tests (game tests, in addition to v3's, which are adapted to two colours)

- `paintStatesAreUniqueAndSafe` — all 306 client states distinct, none waterlogged, none all-false.
- `floorPaintConnects` — paint a 3×3 floor: centre has all four bits, an edge cell has three, a
  corner two; the client state of the centre is the all-connected tile's state.
- `wallPaintUsesTheWorldFrame` — a 2-high, 3-wide north wall: the middle-bottom cell has POS_V, NEG_U,
  POS_U set and NEG_V clear.
- `cornerCellFallsBackToSplat` — floor paint then wall paint in the same cell → `PaintBlock` with
  both faces; the neighbours' bits still count it as connected.
- `overpaintReconnects` — IT over the middle of a DATA row: the DATA neighbours lose that bit,
  the IT cell has none.
- `tallyCountsConnectedAndSplat`.

## 8. Out of scope

Inner-corner tiles, paint removal (still only `/rivals reset`), replacing the display quads with
blocks, more than two teams.
