# Ovvar: the instant channel ordered by popularity (design)

Owner: "the load order / priority of instant load should be based on how many patches of that kind
exist in the world." Today the ≤ `Looks.INSTANT_DESIGNS` designs that can ride in the dye colour are the
*first* ones in `Patches.ALL`; they should be the *most common* ones across everybody's wardrobes.

Owner's decisions, taken as given here: the servers share one JDBC store and that is the normal case;
the catalogue will grow without bound; a re-rank never forces a loading screen on anybody; a demoted
patch is allowed to stop being instant; the change ships behind a config flag defaulting to today's
behaviour; popularity is counted as sewn + stashed across the shared store; and **the preview texture
grows rather than the art shrinking** — every instant patch is drawn at its full drawn size, always
(§3.3).

This is a design spec. No code is written here; §11 is the task breakdown a plan can be cut from.

## 1. What is true today (verified)

`Looks.instant(Placement)` is the whole of the current rule:

```java
public static final int INSTANT = 3, INSTANT_DESIGNS = 21;
public static boolean instant(Placement p) {
    return Patches.code(p.patch()) <= INSTANT_DESIGNS;
}
```

and `Patches.code` is nothing but catalogue position — `content/Patches.java`:

```java
/** index + 1, what the preview bits carry. */
public static int code(Patch patch) {
    return ALL.indexOf(patch) + 1;
}
```

which is why the catalogue carries the comment *"New entries go last: a design's instant code is its
position here, so inserting one in the middle would repaint everything already sewn."*

`Looks.state(Placement)` turns a placement into `cell * INSTANT_DESIGNS + (code - 1)`; `Looks.rank`
combinadic-packs up to three of those into 22 bits; `Looks.encode` makes three non-zero base-255 digits
of it and that is the dye colour. The shader unranks it in `assets/ovvar/shaders/include/ovvar.glsl`.

The art the shader draws comes from the **preview library**, baked at build time in
`datagen/GeneratedAssets.java` (~259–332). Two things are written into
`textures/entity/equipment/<layer>/preview_<piece>.png`:

- the **library**: each design's art blitted into a block of cells (a cell is `Spot.SIZE` = 4 skin
  texels), allocated by `libraryBlock` (~707), biggest-block-first since `afafbb1`;
- the **design table**: a row per `(design, Patches.Fit)` at
  `slot = design + fit.ordinal() * FIT_SLOTS`, holding `rgb(libraryX, libraryY, cells)` and, one table
  right, `rgb(artWidth, artHeight, 0)`.

Both loops skip anything past the cap: `if (Patches.code(patch) > Looks.INSTANT_DESIGNS) continue;`
(lines 272 and 294).

**The most important thing I found.** The shader does *not* hard-code the design order, the design
count, or where any art lives. `ovvar.glsl` reads

```glsl
float designs = kind.b, m = kind.g * designs;
...
float slot = design + fit * OVVAR_FIT_SLOTS;
vec4 pe = ovvar_read(OVVAR_TABLE_X + 2.0*OVVAR_TABLE_COLUMNS + floor(slot/16.0), mod(slot,16.0));
```

and `kind` is the marker texel datagen writes as
`rgb(KIND_PREVIEW, cells.size(), Looks.INSTANT_DESIGNS)`. Design order, design count and art placement
are **data in the texture**. Reordering the designs is a *texture* change and needs no GLSL change.

Nothing persisted holds a code. I searched every use of `Patches.code` (5 in main, 2 in tests): `Looks`
twice, `GeneratedAssets` twice, the two tests. Persistence is by **id**: `store/Wardrobe.CODEC` keys the
stash `Codec.STRING → int` and designs by `Placement.CODEC`; `pack/Combos` writes `combos.json` through
`KeyedCombo.KEY_CODEC`, which is `piece.id + ":" + placement keys`; `Patches.ID_CODEC` is the item/
component codec. The only place a code escapes the process is the dye colour of a live item — which is
exactly the consistency problem in §5 and is never saved.

## 2. What is counted

**Earned totals, store-wide: sewn + stash.** The owner's words were "how many patches of that kind exist
in the world", and the store is where the world's patches are: `Wardrobe` is built so that "a patch lives
in exactly one place, in the stash or on a design". Per patch id:

```
popularity(id) = Σ over wardrobes [ stash.getOrDefault(id, 0)
                                  + Σ over designs.values() (placements of that patch) ]
```

Two things this deliberately does not count, both worth the owner knowing:

- **Loose patch items** — a patch item lying in a chest or an inventory is not in the store at all, so it
  is invisible to this. On a minigame server `Stash.bank` sweeps them back into the wardrobe, but on a
  survival server they can sit out of the count indefinitely. There is no cheap way to count them (it
  would mean walking every loaded chunk's block entities and every player inventory on every server), and
  the spec does not try.
- **Per-server anything.** The ranking is one ranking for the whole network (§5.2).

Confirmed by the owner: sewn on ovves plus in stashes, summed over every player in the shared store,
loose items uncounted.

### 2.1 Getting it out of the backends

Neither backend can aggregate today. `WardrobeBackend` is exactly three methods —
`load(UUID)`, `store(UUID, Wardrobe, long)`, `describe()` — and `JdbcBackend` keeps the whole wardrobe
as an opaque JSON blob:

```sql
CREATE TABLE IF NOT EXISTS <table> (
  owner CHAR(36) NOT NULL PRIMARY KEY, version BIGINT NOT NULL, data TEXT NOT NULL, updated_at BIGINT NOT NULL)
```

There is no column to `GROUP BY`. A portable SQL aggregate is impossible: the mod targets MariaDB,
PostgreSQL and H2, their JSON functions do not agree, and `data` is not even a JSON column type.

**A scan, on the store thread, at most once a day.** Add to `WardrobeBackend`:

```java
/** Every wardrobe's patch counts, summed per patch id. Called rarely (a re-rank), never per tick. */
Map<String, Long> popularity() throws IOException;
```

- `JdbcBackend`: `SELECT data FROM <table>` streamed with a fetch size, each row parsed with the
  `decode` it already has and folded into a `HashMap`. A few thousand rows of a few hundred bytes is
  well under a second, and it runs on the single-thread store executor, never the server thread.
- `FileBackend`: `Files.list(dir)` over `<uuid>.json` and the same fold — it is the "world's own
  directory" backend, so the directory *is* the population.

**Rejected: a maintained counter table.** It would have to be written by every `Stash.grant`,
`withdraw`, `deposit`, `bank` and every sew/unpick, i.e. a second write next to a compare-and-set that
can fail — and `Wardrobes.update` already has `CONFLICT`/`UNREACHABLE` outcomes and a retry queue. A
counter that drifts out of step with the wardrobes is worse than a once-a-day scan.

### 2.2 Who scans, and what happens when the store is down

Because the store is shared and the ranking must be one ranking (§5.2), **the scan and the ranking are
one server's job at a time, and the result is published in the store**. Any server may take the job; it
is arbitrated by the published row's own compare-and-set (§5.2), so two servers re-ranking at the same
epoch is harmless — one wins, the other reads the winner's answer and uses it.

An ordinary server therefore *reads* a ranking far more often than it computes one. Fallback order:

1. the ranking published in the store;
2. the last ranking this server used, persisted beside the combos file as `<world>/ovvar/instant.json`
   (the ranking, and the epoch it was computed at);
3. catalogue order, truncated to what the library packs.

Step 2 is what makes a store outage invisible: the server starts with yesterday's ranking rather than
repainting everybody as the catalogue. `Wardrobes.available()` already exists and refuses rather than
guesses; this follows it.

## 3. The preview library's capacity

This is the section the whole feature stands on, because the catalogue is unbounded and the library is
not. **The core idea holds: only the top-ranked designs get library blocks, so the library is bounded by
`N` — the number of designs on the channel — and not by the catalogue.** That is already how datagen
behaves (`if (Patches.code(patch) > Looks.INSTANT_DESIGNS) continue;`); popularity ordering only changes
*which* N. Catalogue growth costs nothing in the texture. What it costs is that the N-th best patch keeps
changing, which is §6.

What it does **not** give for free is `N = 21`. The numbers:

### 3.1 What actually bounds the region

- **The region is the equipment texture's head rows**: skin texels x 0..64, y 0..16, as cells of
  `Spot.SIZE` = 4 skin texels → `LIBRARY_COLUMNS = 16` × `LIBRARY_ROWS = 4` = 64 cells. `LIBRARY_ROWS = 4`
  is not a free parameter; it *is* 16 skin texels divided by 4.
- **Why the head rows and nothing else.** The preview texture is a real equipment layer. Everything at
  y ≥ 16 in the armour layout is rasterized by vanilla onto the body, arms and legs, so art parked there
  would be visible garbage to any client not running our core shader. A chest or legs layer never
  rasterizes the helmet rows, so those are free storage. (The preview texture is otherwise blank —
  `Tex.blank(W, H)` plus the marker, the tables and the library; nothing else is written into it.)
- **Minus the tables**: `libraryFree` excludes any cell overlapping skin-texel columns
  `CELL_TABLE_X` (40) through `PATCH_SIZE_TABLE_X + TABLE_COLUMNS` (52), which is columns 10, 11, 12 —
  12 cells — and the marker's own cell (15, 3). **51 usable cells**, which is what README line 577 says.
- **Not bounds, with headroom to spare**: `FIT_SLOTS = 32` (design-table rows per fit; 21 used),
  `TABLE_SIZE = 16 * TABLE_COLUMNS` = 96 entries per table (21 used), the marker texel's B channel
  (holds 255), and every `OVVAR_*` constant in `ovvar.glsl`.
- **Raising `Spot.DETAIL` does not help.** `Spot.PX = SIZE * DETAIL`, so a library cell and a cell-sized
  patch grow together: `Art.cells()` is `(width + PX - 1) / PX`. Doubling D would make today's 12×12 art
  fit one cell, but it would also redefine a cell-sized patch as 16×16, i.e. rescale every patch on
  every garment. It is an art-direction change, not a capacity lever.

### 3.2 What a design costs

`GeneratedAssets` gives every design a block per `Patches.Fit`, deduped by art (`library` is a
`Map<Art,int[]>`, and `catalogue.contains(variant)` folds fits that pick the same PNG). With
`OVER_MAX = Spot.PX * 3 / 2` = 12 and `MAX_ART = Spot.BIG * DETAIL` = 16, a typical 12×12 patch that also
ships a 16×16 and an 8×8 costs `2×2 + 1×1 + 2×2` = **9 cells** (`itk` is exactly this). A 12×12 with no
variants costs 4. Ten patches take somewhere around forty of the fifty-one — hence the biggest-first
packing fix this week. (The exact number is printed by the datagen line *"{} arts in the preview
library"*; reading it off a run is task 0.)

**Two savings are free, and I verified both from the `Spot` enum.**

1. **The bottom half needs only `Fit.OVER`.** Every `Piece.BOTTOM` cell is a leg cell at v 22 or 26 —
   at or below `FACE_ROW` = 20, so `top()` is false — at `Spot.PX` square, or it is `SEAT`, which
   `Fit.of` excludes from `FILLED` by `spot.side != Spot.Side.SEAT`. So `Fit.of` returns `OVER` for all
   thirteen of them. `CLIPPED` exists only for `SHOULDER_R`/`SHOULDER_L` and `FILLED` only for
   `BACK_BIG`, all three `Piece.TOP`. The datagen loop nevertheless writes all three fits into the
   bottom's library and into the feet preview — **blocks no shader path can ever read**. Dropping them
   roughly halves both bottom textures.
2. **A seat patch needs only `OVER`, on both halves.** `Patch.fits(spot)` is `seat == (spot == SEAT)`,
   so `rivals` and `pung` can never land on a shoulder or on `BACK_BIG`. Their `CLIPPED` and `FILLED`
   blocks are dead too.

That leaves `Piece.TOP` as the binding half, and it is still not enough: 51 cells over 21 designs is 2.43
cells each, and one 12×12 art alone is 4. **No arrangement of the current 51 cells holds 21 designs at
today's art sizes.** Being honest about that is the point of this section.

### 3.3 The decision: a bigger preview texture, art untouched (DECIDED)

Downscaling library art is **rejected** — the owner will not have patch sizes limited in any way,
including for the seconds before a pack catches up. Every instant patch is drawn at its full drawn size.
So the texture grows.

**It grows in resolution, not in shape.** I checked what "taller" would actually do, and a non-2:1 file
is a trap worth writing down:

> Equipment layer textures are sampled with UVs baked against the **64×32 humanoid armour layout** and
> normalized to the file — `HumanoidModel`'s `texWidth/texHeight` are 64/32 and the model's UVs are
> already divided by them. The file's own pixel dimensions are only a resolution; its *aspect* is the
> layout. Declare the preview texture 64×64 skin texels and a body fragment at layout v ∈ [16, 32]
> samples file rows 64..128 of a 128-tall PNG — i.e. exactly the "spare" region — and the garment
> renders at half vertical scale. The "never rasterized" guarantee that makes the head rows free
> **depends on the file being 2:1**. A second library region below the layout does not exist.

The change that does work, and gives more room than a second region would:

**Keep the 64×32 layout; raise the preview textures' own detail.** Introduce
`GeneratedAssets.PREVIEW_DETAIL = 4` beside `Spot.DETAIL = 2`, used *only* for the three preview
textures. The PNG becomes **256×128** (64×32 skin texels at 4 px each) instead of 128×64. Nothing about
the layout, the UVs, the garment, `Spot.PX`, `Patches.MAX_ART` or any patch's art changes — `Spot.DETAIL`
is untouched, so the art convention is untouched, which is the owner's requirement.

What it buys: a library cell is `Spot.SIZE` (4 skin texels) × `PREVIEW_DETAIL` = **16 px** instead of 8.
`Art.cells()` computed against the preview's own cell size means **every art in the catalogue today —
up to `MAX_ART` = 16×16 — occupies exactly one cell**. The library is still 51 cells (the region, the
tables' three columns and the marker cell are all in skin texels and scale together), so it holds **51
designs at full size**, against the 21 the channel can name. `N = INSTANT_DESIGNS = 21` becomes
reachable with headroom to spare, and stays reachable as the catalogue grows without bound, because only
the top 21 are ever stored (§3, core idea). Should art ever be allowed past 16 px, `PREVIEW_DETAIL = 8`
(a 512×256 PNG) repeats the trick.

#### What changes

**`datagen/GeneratedAssets`** — the preview block and its helpers become parameterised by a detail value
instead of the file-level `D`:

- `private static final int D = Spot.DETAIL, W = 64 * D, H = 32 * D;` (line 76) gains a preview variant;
  the preview builder works in `PD = PREVIEW_DETAIL`, `PW = 64 * PD`, `PH = 32 * PD`.
- `CELL_TABLE_X = 40 * D`, `TABLE_COLUMNS = 3 * D`, `CELL_SIZE_TABLE_X`, `PATCH_TABLE_X`,
  `PATCH_SIZE_TABLE_X` (685–689), `MARKER_X = W - 1`, `MARKER_Y = H / 2 - 1` (78), `LAYER_X = W - 3`
  (653), `BLANK_X/BLANK_Y` (665) and `DEBUG_X` all multiply by `D` or derive from `W`/`H` — each needs the
  preview's values when writing a preview texture. They stay the same *skin-texel* positions, which is
  the point.
- `libraryFree` (699) compares `cx * Spot.SIZE * D` against the table columns — same substitution.
- `libraryBlock` (707) is unchanged; only the cell size its callers compute changes.
- `marked(Tex, Piece)` (562) begins `require(tex.width == W && tex.height == H, …)` and must take the
  expected size rather than assume the garment's.
- `Tex.blank(W, H)` at line 292 → `Tex.blank(PW, PH)`, and the art blit at line 300 uses `at[i] * PD`.

**`pack/PreviewLibrary`** (the runtime builder, §4) uses the same parameterised code, so this lands once.

**`assets/ovvar/shaders/include/ovvar.glsl`** — the "small pinned GLSL change". Today:

```glsl
const float OVVAR_D = 2.0;
const vec2 OVVAR_TEX = vec2(64.0, 32.0) * OVVAR_D;
```

Both become per-texture, derived from the bound sampler, since the shader now meets two resolutions
(placement textures at D = 2, preview textures at PD = 4):

```glsl
vec2 ovvar_tex()  { return vec2(textureSize(OVVAR_SAMPLER, 0)); }
float ovvar_d()   { return ovvar_tex().x / 64.0; }
```

Everything else is mechanical: `OVVAR_TEX` → `ovvar_tex()` in `ovvar_read` (61–63) and in
`OVVAR_MARKER` (58), `OVVAR_BLANK` (59) and the four `OVVAR_DEBUG_*` constants (147–150), which all read
`OVVAR_TEX.x - n` / `OVVAR_TEX.y * 0.5 - 1.0`; `OVVAR_D` → `ovvar_d()` in `OVVAR_CELL` (36),
`OVVAR_TABLE_X`/`OVVAR_TABLE_COLUMNS` (45–46) and the geometry maths (`ce.r / OVVAR_D`, ~434 onwards).
The `const`s become functions or locals — a syntactic change at every use site, no arithmetic change.
`OVVAR_FIT_SLOTS`, `OVVAR_FIT_*`, `OVVAR_SIDE_ROW` and `OVVAR_SEAT_COLUMN_RIGHT` are resolution-free and
do not move, so the tests that regex them (§9) keep working unaltered.

**The Iris/shaderpack patcher picks this up with no extra work.** `tools/shaderpatcher/.../Patcher.java`
reads the whole file at `private static final String GLSL = readResource("/ovvar.glsl");` and splices it
into the pack's entity program; `mods/ovvar/build.gradle`'s `patcherJar` task copies the real file in:

```groovy
// The very shader code the vanilla override uses, so patched packs and vanilla never drift apart.
from('src/main/resources/assets/ovvar/shaders/include/ovvar.glsl')
```

So the patched packs get whatever `ovvar.glsl` says at build time. The only thing to confirm is that
`OVVAR_SAMPLER` resolves in a patched pack's program the same way it does in the vanilla override — the
patcher already rewrites the sampler and the varyings (`findVarying`, `shadowDecl`), so it should, but it
is worth one patched-pack run.

#### Risk

`textureSize()` is core GLSL from 130 and Minecraft core shaders are `#version 150`, so it is available;
Iris packs are the same or newer. The residual risks are (a) that making `OVVAR_D` non-`const` costs
measurable performance in a per-fragment path — unlikely, it is one `textureSize` per fragment and
drivers hoist it, but worth a glance; and (b) that some shaderpack's patched program does not expose a
sampler the patcher can name in that expression. Both are listed in §12.

The two free savings in §3.2 (no `CLIPPED`/`FILLED` blocks for `Piece.BOTTOM` targets; no shoulder or
`BACK_BIG` variants for seat patches) stay in the plan. They are not needed for capacity once
`PREVIEW_DETAIL` lands, but they remove blocks no shader path can ever read, and they shrink three
textures in every pack build.

### 3.4 Can 21 itself be raised?

Not without moving something else. `Looks.rank` enforces
`m = Spot.cells(piece).size() * INSTANT_DESIGNS ≤ INSTANT_STATES` (448), and the top has 21 cells:
21 × 21 = 441 fits, 21 × 22 = 462 does not. `theInstantChannelStaysInsideWhatTheShaderCanUnrank` asserts
*both* directions —

```java
if (topCells * (Looks.INSTANT_DESIGNS + 1) <= Looks.INSTANT_STATES) helper.fail(...);
```

— so neither number can move on its own. Raising designs to 22 means either giving up a top cell or
raising `INSTANT_STATES`, which is bounded by the shader's float binomials (`ovvar_c3`: C(m,3) must stay
exact under 2²⁴) and by the dye's own 255³ ≈ 16.58 M. At m = 448 the largest rank is about 14.99 M, so
there is a little slack, but it is slack in a float-exactness argument and should be spent only
deliberately. **All of that is out of scope here** (§10); this spec changes *which* designs, not how
many.

## 4. Where the design index moves to

A new `content/InstantTable` replaces `Patches.code` as the design index:

```java
public record InstantTable(List<Patches.Patch> designs, long epoch) {
    public int index(Patches.Patch p);   // -1 when it does not ride the channel
    public int size();                   // ≤ INSTANT_DESIGNS, and ≤ what the library packed
}
```

and the preview textures are built at **runtime** by a new `pack/PreviewLibrary`, registered on
`PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT` alongside `Combos` and `WardrobeArt`, writing
the three preview PNGs (`preview_top`, `preview_bottom`, `EquipmentJson.FEET_PREVIEW`) from the live
table. It reuses the existing code almost verbatim: `datagen/Tex`, `libraryBlock`, `libraryFree` and the
table-writing block all live in `src/main/java` and ship in the jar, and the source arts are classpath
resources — `Patches.discoverVariants` already loads them by `getResource` on a server, not only in
datagen.

Because `N` is an outcome of packing, the runtime builder **truncates rather than throws**. Today
`libraryBlock` throws `"the preview library is full"`, which is right for datagen (a developer sees it)
and wrong at runtime (a pack build would fail in front of players). At runtime it returns "no room", the
ranking is cut there, and the count that lands in the marker texel is the count actually blitted.

Classes that change:

| class | change |
|---|---|
| `content/InstantTable` | new — the ranking, the packing outcome, the epoch |
| `content/Looks` | `instant`, `state`, `rank` take an `InstantTable`; `INSTANT_DESIGNS` stays the ceiling, `table.size()` is the live count used as `m`'s multiplier |
| `pack/InstantRanking` | new — popularity → ranking → hysteresis → packing → publish → persist |
| `pack/PreviewLibrary` | new — the three preview PNGs at pack-creation time; the §3.2 fit savings |
| `pack/Combos` | a `TABLES` map beside `BUILT`; `tableFor(player)`; a `rebuild(reason)` entry point (§5.1) |
| `datagen/GeneratedAssets` | the preview block moves into shared code `PreviewLibrary` calls, parameterised by `PREVIEW_DETAIL` (§3.3); datagen keeps writing a bootstrap texture in catalogue order so a pack is never blank |
| `content/Patches` | the "new entries go last" comment goes; `code` survives only as a tiebreak |
| `store/WardrobeBackend`, `FileBackend`, `JdbcBackend` | `popularity()` |
| `ModCommands` | `/ovvar instant` — the ranking, its epoch, its source, and a manual re-rank |

`ovvar.glsl` changes only as §3.3 describes — resolution becomes per-texture — and not at all for the
ranking itself, since design order, design count and art placement are already data in the texture (§1).

One thing to settle before any of it (task 0): whether `builder.addData(path, …)` after
`PolymerResourcePackUtils.addModAssets(MOD_ID)` overwrites the mod's own asset at the same path, or
whether the mod asset wins. `WardrobeArt` only ever writes *new* paths, so the repo does not answer it.
If mod assets win, the fix is small — write the runtime preview under a distinct path and have
`EquipmentJson.previewTexture(piece)`, already the single place the name is produced, name it — but it
must be checked before anything is built on it.

## 5. Consistency: a stale pack must never draw the wrong patch

A dye colour is decoded against whatever preview texture the client currently has. If server and client
disagree about what design 7 is, the client draws *another patch* — silently, confidently, wrongly.
Missing is fine; wrong is not.

### 5.1 A table per pack generation, and the viewer's own generation

`Combos` already has exactly this machinery, and already uses it this way for combos:

```java
private static final Map<UUID, Integer> PUSHED = new ConcurrentHashMap<>(), LOADED = new ConcurrentHashMap<>();

public static boolean isBuilt(Piece piece, Combo combo, UUID player) {
    if (combo.isEmpty()) return true;
    Set<KeyedCombo> current = BUILT.getOrDefault(generation, Set.of());
    if (player == null) return current.contains(key);
    return BUILT.getOrDefault(LOADED.getOrDefault(player, generation), current).contains(key);
}
```

`LOADED` is filled on `ServerPlayConnectionEvents.JOIN` ("*The pack they got while connecting is the
current one*") and updated by `Combos.packLoaded`, which `mixin/ResourcePackResponseMixin` calls on
`ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED`. **The mod does track which pack each player
has applied.** So:

- the ranking is **frozen per pack build**. Pack creation snapshots the current `InstantTable` the way it
  snapshots `KNOWN` into `building`; `RESOURCE_PACK_FINISHED_EVENT` → `built(snapshot)` files it under
  the new generation in `TABLES`, exactly as it files `BUILT`. `TABLES` is pruned by the same
  `removeIf(g -> g < keep && g < generation)` rule, on the same minimum-live-generation key;
- `Looks.look(stack, piece, player, …)` already takes the viewer's UUID. It asks `Combos.tableFor(player)`
  = `TABLES.get(LOADED.get(player))` and encodes with **that** table;
- a design absent from the viewer's table is simply not instant *for that viewer*: `instant(p)` is false,
  it falls out of `shown`, `complete` goes false, and the existing paths take over — the top's trim
  channel, and `Combos.request`/`claim` to pull the pack forward. That is the "no instant, wait for the
  pack" fallback and it costs nothing new;
- a generation missing from `TABLES` yields an **empty table**: no instant at all, everything through the
  pack. Fail closed, never guess;
- the pack hash changes with the ranking automatically, because the preview PNGs are in the pack and
  their bytes changed. No extra versioning: the hash *is* the ranking's identity.

**The one piece of new plumbing.** `Combos.tick` only rebuilds when the pack is missing a combination:

```java
if (BUILT.getOrDefault(generation, Set.of()).containsAll(KNOWN)) return;   // a request raced the last build
```

A ranking change adds no combinations, so it would never trigger a build. `InstantRanking` needs
`Combos.rebuild(String reason)` — set a flag that this guard also consults, and pull the deadline
forward. Everything downstream (a new generation, a new `TABLES` entry, the log line) then happens on
the existing path.

### 5.2 No forced reload — a re-rank behaves like everyone else's edits

The owner's rule, and the existing behaviour it must match. `Combos`' own header states it:

> *A push is a loading screen, so nobody gets one they did not cause: the pack is pushed to a player only
> when their own sewing outgrew what the dye colour can show of it (`claim`, at once when the build is
> done), or when they ask with `/ovvar reload`. Everyone else keeps the pack they have and sees what it
> holds plus the newest patches in the dye colour; whoever joins gets the current pack.*

A ranking change is **one more reason for a new generation, and nothing more**. Concretely, it is already
right by construction: `built()` adds to `NEEDS_PUSH` only from `CLAIMED_BY` (players who claimed a
combination that just landed) and from `RELOADING` (players who ran the command). A generation produced
by a re-rank has no claimants and no reloaders, so `NEEDS_PUSH` stays empty and nobody is pushed. The
spec's requirement is therefore *don't add anything* — explicitly: `InstantRanking` must not touch
`NEEDS_PUSH`, `CLAIMED_BY` or `RELOADING`.

A player picks the new ranking up exactly two ways, both of them theirs:

- **`/ovvar reload`** — `ModCommands` line 88–89, *"Anyone: the latest resource pack, now (the one reload
  that is asked for)"* → `Combos.reload(player)`, which answers "You already have the latest pack" or
  pushes;
- **relogging** — the `JOIN` handler sets `PUSHED` and `LOADED` to the current generation.

Meanwhile, and this is the consequence worth spelling out: a player on an older generation keeps being
encoded with **their** table (§5.1). A patch demoted out of the channel this morning **keeps working
instantly for them** until they reload, because their pack still holds it at the index the server is
encoding for them. A patch promoted this morning is not instant for them yet, and goes through the pack
or the trim like any other non-instant patch. Nobody ever sees wrong art, and nobody is interrupted.

Demotion of a worn patch therefore needs no special case (owner decision 4): after a reload it falls back
to the pack/trim path like any other non-instant patch, which is a few seconds of `Combos.request`, not a
gap.

### 5.3 The shared store is the network's agreement

The servers share one JDBC store, so each building its own pack would let two of them rank differently at
the margin. `LOADED` is per-connection and reset on join, so a player arriving with the *other* server's
pack would be believed to hold *this* server's generation — the one remaining way wrong art could reach a
screen.

**The ranking is published in the shared store and every server uses the published one.** A reserved row
in the same wardrobe table under a fixed sentinel UUID — no schema change, no new write path, it reuses
`store(UUID, Wardrobe, long)` and its compare-and-set — holding `{epoch, designs: [ids…]}`. (It needs a
`Wardrobe`-shaped carrier or a small widening of the row's JSON; the cleanest is a reserved key inside
the existing `Wardrobe` codec's map, decided at implementation time.)

- A server re-ranks by writing this row with CAS. Two servers re-ranking at the same epoch: one wins, the
  loser re-reads and adopts the winner's list. This is the arbitration §2.2 relies on.
- Every server reads the row at startup and every `instant.scanSeconds`; a newer epoch than the live
  table triggers `Combos.rebuild("ranking")`. All servers converge within one poll interval.
- Because the pack hash changes with the ranking, a player arriving with a foreign pack is pushed the
  local one by the ordinary Polymer join path, and their `LOADED` is truthful again.

The **file backend stays supported as the single-server degenerate case**: `publish` is meaningful only
with a shared store, so with `FileBackend` the published row is the server's own and the whole mechanism
collapses to "compute it, write it, use it".

## 6. Hysteresis

Ranks 21 and 22 swapping back and forth would repaint the world daily, and with an unbounded catalogue
there will always be a crowd at the cut. Four rules:

1. **Cadence.** Re-rank at server start and thereafter per `instant.rerank`: `startup` (only),
   `reload` (also on `/ovvar instant rerank`), `daily` (a 24 h epoch). Default `daily`.
2. **Incumbency margin.** A design already in the table keeps its place unless a challenger beats it by
   more than `instant.margin` (default 10 %, minimum 1 count). Membership is sticky; *ordering within*
   the table is free, since only membership and index matter and the index is rewritten in the same
   build.
3. **Deterministic tiebreak.** Equal counts, after the margin, break by catalogue order — `Patches.code`,
   which survives for exactly this.
4. **Membership changes only at a re-rank**, never on a sew.

A re-rank that changes nothing must not rebuild the pack: `InstantRanking` compares the new list to the
live one and does nothing when equal — including when the store was unreachable and it fell back.

## 7. What stops depending on catalogue order

Stops: `Looks.instant`, `Looks.state`, and the two `GeneratedAssets` loops. That is all of it.

Stays: `Patches.ID_CODEC`, `Patches.get`, `BY_ID`, `Wardrobe.CODEC`, `Placement.CODEC`,
`Combos.KeyedCombo.KEY_CODEC`, every item component — all by **id**. Nothing on disk, in an item, or in
the store holds a code.

So **yes, "new entries go last" can be relaxed** once this lands, and the comment in `Patches.ALL` should
say the order is now only a tiebreak. `Patches.code` itself stays (the tiebreak, and the tests' readable
ordering) but documented as no longer the instant code. This is the part of the change that pays off
forever with an unbounded catalogue: a patch can be inserted anywhere, and the catalogue can be sorted
for humans.

An ovve worn right now carries a dye colour computed under whichever table its *viewer* holds. Nothing is
stored, so nothing needs migrating.

## 8. Config

A new `instant` block in `config/ovvar.json`, following the `_help` convention (`OvvarConfig.HELP`,
`DesignStoreConfig.HELP`, `StashConfig.HELP` — a `Codec.unboundedMap` `_help` field plus
`optionalFieldOf(key).xmap(...)` so the block is always written, with its help, even at its default):

| key | values | default | `_help` |
|---|---|---|---|
| `order` | `catalogue` \| `popularity` | **`catalogue`** | "Which designs ride in the dye colour and show at once: the first ones in the catalogue (catalogue, how it has always been) or the most common ones across every wardrobe (popularity)." |
| `rerank` | `startup` \| `reload` \| `daily` | `daily` | "When the ranking is recomputed. daily re-ranks at startup and once a day; reload adds `/ovvar instant rerank`; startup only at startup. A re-rank rebuilds the pack but never sends anyone a reload — they pick it up with `/ovvar reload` or when they next log in." |
| `margin` | 0.0–1.0 | `0.10` | "How much more common a patch must be than one already riding the channel before it takes its place, so patches near the cut do not swap back and forth." |
| `publish` | bool | `true` | "Write the ranking into the shared store so every server on the network uses the same one. Only meaningful with the jdbc backend." |
| `scanSeconds` | int | `900` | "How often a server re-reads the published ranking from the store." |

`OvvarConfig` gains `InstantConfig instant` in the same shape as `designs`/`stash`, plus
`OvvarConfig.instant(InstantConfig)` beside `designs(DesignStoreConfig)` so tests can swap it. The
default keeps today's behaviour exactly; `popularity` is opt-in until the owner flips it.

## 9. Tests

### Pinned today (all in `gametest/WardrobeTests.java`)

- `theInstantChannelStaysInsideWhatTheShaderCanUnrank` (~2468) — asserts `cells × INSTANT_DESIGNS ≤
  INSTANT_STATES` and, the other way, that one more design would overflow; hard-codes
  `Spot.cells(TOP).size() != 21`. It must also assert `table.size() ≤ INSTANT_DESIGNS` and take a table
  rather than reading `Patches.code`.
- `theInstantLibraryHoldsEveryArtADesignCanBeDrawnAs` (~3070) — reads `OVVAR_TABLE_X`,
  `OVVAR_TABLE_COLUMNS`, `OVVAR_FIT_SLOTS`, `OVVAR_FIT_*` out of `ovvar.glsl` by regex and checks every
  `(design, fit)` row against `Patches.artFor` and the library pixels. Uses `Patches.code(patch) - 1`
  (line 3083) → `table.index(patch)`; must read the runtime-built texture, and must stop expecting
  `CLIPPED`/`FILLED` rows for the bottom halves and for seat patches (§3.2).
- `patchTexelsAreDrawnAtTheColourTheyWerePainted` (~1925) skips non-instant patches by
  `Patches.code(patch) > Looks.INSTANT_DESIGNS` (1953) — same substitution.
- `everyPathTellsTheArmsApartTheSameWay` (~2033) and the seat-column check (~1416) are geometry, not
  order, and stay untouched.

**GLSL does not run in game tests.** The existing tests cope by parsing `ovvar.glsl` as *text* and
pinning its `const float`s against the Java constants, and by reading the generated PNG with `datagen/Tex`
and asserting the tables' bytes. That works unchanged here, and is why the reordering is safe: the test
that proves datagen's tables match the shader can be pointed at the runtime builder's tables. The
end-to-end proof is the `runClientGameTest` screenshot harness, not these.

### New

1. `instantRankingIsDeterministic` — fixed synthetic wardrobes → the same list twice; ties break by
   catalogue order; the margin keeps a narrowly-beaten incumbent and drops a soundly-beaten one.
2. `instantRankingCountsSewnAndStashed` — a patch sewn twice and three in the stash counts five; two
   wardrobes sum; a patch nobody owns scores zero and ranks last.
3. `popularityReadsBothBackends` — `FileBackend` over a temp dir and `JdbcBackend` over
   `jdbc:h2:mem:ovvar_<random>` (the pattern `fileBackendStoresWithVersions` /
   `jdbcBackendStoresWithVersions` already use) seeded alike give the same map.
4. `aStalePlayerGetsNoInstant` — two generations with different tables; a player pinned to the old one
   via `LOADED` never gets a dye naming a design their table lacks, and `complete()` is false instead.
   **The test that protects the whole feature.**
5. `aDemotedPatchKeepsWorkingUntilTheyReload` — the §5.2 consequence, stated as a test: demote a design,
   rebuild; the player on the old generation still gets it in the dye; after `JOIN` re-seeds `LOADED`
   they get the pack path instead. Also asserts `NEEDS_PUSH` stayed empty across the re-rank.
6. `storeDownFallsBackToTheLastRanking` — a throwing backend; `<world>/ovvar/instant.json` is used; with
   no file, catalogue order; no pack rebuild either way.
7. `serversAgreeOnThePublishedRanking` — two `InstantRanking` instances over one H2 store: both publish
   at one epoch, one wins the CAS, both end on the same list.
8. `aCatalogueBiggerThanTheChannelKeepsOnlyTheCommonest` — a **test catalogue** of 40 synthetic patches
   (write `InstantRanking` to take `List<Patch>` rather than reach for `Patches.all()`, so no production
   seam is needed): the table holds the commonest `min(INSTANT_DESIGNS, what packs)` and `Looks.instant`
   is false for the rest. This is the unbounded-catalogue test.
9. `theLibraryPacksWhateverTheRankingAsks` — the ranking is truncated, not thrown, when `libraryBlock`
   runs out; the count in the marker texel equals the count actually blitted.
10. `theBottomLibraryHoldsOnlyOverArt` — §3.2: no `CLIPPED` or `FILLED` block for any `Piece.BOTTOM`
    target, none for a seat patch on either, and the cell count drops accordingly.
11. `theRuntimePreviewMatchesDatagenForCatalogueOrder` — with `instant.order = catalogue` and before the
    §3.2 savings, the runtime PNG is byte-identical to `src/main/generated`. The safety net for the whole
    datagen→runtime move; it is retired by test 10.

### New, for the bigger preview texture (§3.3)

12. `thePreviewTextureKeepsTheArmourAspect` — every preview PNG is exactly 2:1 and `64 * PREVIEW_DETAIL`
    wide. This is the test that stops anyone "adding a second library region" below the layout and
    silently halving every garment's vertical scale. It should carry the §3.3 explanation as its javadoc.
13. `theLibraryHoldsEveryArtAtItsFullSize` — for every design and fit, the size in the design table
    equals `Patches.artFor(...)`'s own width and height, with no downscale anywhere. The owner's "no size
    limits" rule, pinned.
14. `everyInstantDesignFitsTheLibrary` — with a test catalogue of 40 and `INSTANT_DESIGNS` designs
    ranked in, the library packs all of them: no truncation at `N = INSTANT_DESIGNS`. This is what proves
    §3.3 actually solved the capacity problem rather than moving it.
15. `theShaderReadsTheTexturesOwnResolution` — extend the existing regex harness: `ovvar.glsl` must no
    longer declare `OVVAR_D`/`OVVAR_TEX` as literal constants, and `OVVAR_FIT_SLOTS`, `OVVAR_FIT_*`,
    `OVVAR_SIDE_ROW`, `OVVAR_SEAT_COLUMN_RIGHT` must still be present and still match their Java values —
    the resolution-free constants are exactly the ones that must not move.
16. `theTablesAndMarkerLandOnTheSameSkinTexels` — the marker, layer, kind, blank and debug texels and the
    four tables sit at the same *skin-texel* positions in a 256×128 preview as in a 128×64 garment
    texture, i.e. the `D` → `PREVIEW_DETAIL` substitution is complete and nothing was left multiplying by
    the wrong detail.
17. Patched-pack run: `OvvarShaderPatcher` against a real Iris pack, confirming the spliced `ovvar.glsl`
    compiles with the sampler expression. Not a game test — a manual step in task 5.

## 10. Out of scope

- Raising `INSTANT_DESIGNS`, `INSTANT` or `INSTANT_STATES`; any change to the combinadic packing (§3.4).
- Raising `Spot.DETAIL`, `Patches.MAX_ART` or anything else that would change how a patch is drawn on a
  garment. §3.3 raises the preview textures' detail only.
- Any shaderpack-patcher *source* change — the patcher picks `ovvar.glsl` up at build time (§3.3).
- Counting loose patch items (§2).
- Per-chapter, per-player or per-server rankings.
- Changing what `Combos` pushes to whom, or the batching deadlines.
- Recency, "trending", or manually pinned designs in config.
- The trim channel and the boots' second channel.

## 11. Tasks

| # | task | size |
|---|---|---|
| 0 | Verify: the exact current library occupancy (the datagen log line), and whether `builder.addData` overrides `addModAssets` for the same path. Nothing below starts until both are known. | S |
| 1 | `InstantConfig` + `OvvarConfig.instant`, with `_help`; default `catalogue`. Test: an existing config without the block parses. | S |
| 2 | `content/InstantTable`; `Looks.instant`/`state`/`rank` take one; a catalogue-order table wired everywhere so behaviour is byte-identical. Existing tests green. | M |
| 3 | `WardrobeBackend.popularity()` on both backends. Tests 2, 3. | M |
| 4 | Lift the preview build out of `GeneratedAssets` into shared code; `pack/PreviewLibrary` at pack-creation time; truncate instead of throw. Tests 9, 11. | L |
| 4b | §3.3: `PREVIEW_DETAIL = 4`; parameterise the tables, marker, layer, blank, debug texels, `libraryFree` and `marked` by detail; 256×128 preview PNGs. Tests 12, 13, 16. | M |
| 4c | §3.3 GLSL: `OVVAR_D`/`OVVAR_TEX` derived from `textureSize`; every use site follows. Test 15, plus the patched-pack run (test 17) and a look at the per-fragment cost. | M |
| 5 | The §3.2 savings: emit only the fits a piece and a patch can reach. Test 10. | S |
| 6 | `Combos.TABLES` per generation, pruned with `BUILT`; `tableFor(player)`; `rebuild(reason)` past the `containsAll(KNOWN)` guard; `Looks.look` uses the viewer's table. Tests 4, 5. | M |
| 7 | `pack/InstantRanking`: ranking, margin, tiebreak, packing truncation, `<world>/ovvar/instant.json`, fallback order. Tests 1, 6, 8, 14. | L |
| 8 | Publish and poll the ranking in the store under the sentinel UUID; CAS arbitration; rebuild on a newer epoch; file backend as the degenerate case. Test 7. | M |
| 9 | `/ovvar instant [rerank]`; README ("instant"/"Combos", ~570–675, both currently say "the first 21 in the catalogue"); the `Patches.ALL` comment. | S |
| 10 | Two-server screenshot check on `runClientGameTest` before the owner flips the default. | M |

## 12. Open questions

Everything the owner was asked is now decided and recorded above. What is left is not a question of
intent but three things nobody here can answer by reading the repo, all of which belong to a task:

1. **Task 0 — Polymer asset precedence.** Does `builder.addData` override
   `PolymerResourcePackUtils.addModAssets(MOD_ID)` at the same path, or does the mod asset win?
   `WardrobeArt` only ever writes new paths, so the repo does not say. It decides the shape, not the
   substance, of §4, and nothing should start before it is known.
2. **Task 4c — the patched-pack sampler.** `ovvar.glsl` spliced into a real Iris pack must still resolve
   `OVVAR_SAMPLER` inside `textureSize(...)`. The patcher already rewrites the sampler and the varyings,
   so it should; it needs one patched-pack run to confirm (test 17). If it does not, the fallback is to
   keep `OVVAR_D` a literal and have the patcher or the preview path carry the detail in the marker
   texel's spare channel instead — more code, same design.
3. **Task 4c — the cost of a non-`const` resolution.** `ovvar_d()` runs per fragment where a `const` used
   to. Expected to be free (one `textureSize`, hoistable), but it is a hot path and deserves a look
   rather than an assumption.
