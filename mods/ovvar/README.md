# Ovvar

Student overalls (ovvar) with sewn-on patches for METAcraft — server-side, Fabric + Polymer,
Minecraft 26.3. Players need nothing but the auto-served resource pack.

## What it adds

One ovve per chapter — Data (cerise), IT (purple), the older silicon-blue IT — and the Media
frack. An ovve is a single item worn in the legs slot with pockets: it is a bundle, filled and
emptied with the usual bundle clicks — also while worn, by clicking items onto the legs slot. It
holds twice a bundle through METAcraft's own bundle mod (`metacraft-bundles`, a hard dependency in
METAmods; here `libs/metacraft-bundles-1.0.0.jar` is compiled against and the dev server runs
without it, with vanilla-sized pockets). Right-click is the bundle's (hold to empty); sneak +
right-click rolls the top up or down; neither equips it, so drag it in or shift-click. While the
top is up and the chest slot is free the
mod keeps a companion "top" there so the sleeves render; it is not a real item and deletes itself
anywhere else. Real chest armour goes on over it as usual (right-click it, or swap it into the
slot) and hides the top until it comes off again. Leather-grade defence, no durability. The
look is an equipment asset cut from the skin overlays on metacraft.se/style.

Patches are items (`ovvar:patch_<id>`) and go on any 4×4-texel cell of the ovve (`Spot.java`: every
face you see of the body, sleeves and legs — not the inner faces — keeping off the collar, the
belt, the hands and the cuffs), plus the seat, which takes a patch two cells wide across it, and one
on each shoulder. Nearly every cell is on the boxes' side rows, skin rows 20–32 (`Spot.FACE_ROW`);
the shoulders are on the arm boxes' *top* faces, rows 16–20 (`Spot.TOP_ROW`). Which rows each part
uses came out of the playtest:

```
      ___________
     /  SHOULDER \         the arm box seen from above: the whole 4×4 top face
     \___________/         is the cell, and its front edge is the top of the sleeve
     |           |
     |  sleeve   |
```

| part | faces | cell rows (`v`) |
| --- | --- | --- |
| body, chest | front (8 wide) | 21, 26 — row 20 is the collar, row 31 the belt |
| body, back | back (8 wide) | 21 (`BACK_TOP_LEFT`/`_RIGHT`) *or* 22 (`BACK_BIG`, the whole face) |
| sleeves | outer, front, back | 21, 25 — a texel lower than they were, row 31 is the hand |
| shoulders | the arm box's **top** face | 16–20, the whole face (`SHOULDER_R`/`_L`) |
| legs | outer, front, back | 22, 26 — two texels lower than they were, row 31 is the cuff under a boot |
| seat | both legs' back faces | 22, following `LEG_BACK_TOP`; a tall seat patch hangs a texel over it each way |

The **shoulders** (`SHOULDER_R`, `SHOULDER_L`) are the one pair of cells not on the boxes' side
rows: each is the whole of an arm box's top face, the arm strip's u 44..48 on rows 16–20, and any
plain patch goes on it, centred. `Spot.top()` is the test — read off the row — and it decides three
things everywhere a cell is drawn: no squeeze round the box (a top face is not on the strip's
perimeter, so there is no slack to take up), the rows the cell is measured down from
(`Spot.TOP_ROW`, not `FACE_ROW`), and — the one thing a shoulder does differently from every other
cell — that **art bigger than the cell is clipped to the face, not bent round it.** A top face's
four edges have no neighbouring face in the layout to continue onto (the strip is a loop round the
box's *sides*, not over the top of it), so a 12×12 patch on a shoulder keeps its middle 8×8 and the
rest is simply cut off, in the pack path, in the dye colour and on a stand alike — unless the patch
has art at a size that fits the face, which is what the per-size variants are for (ITK's 8×8 is
drawn, IT's is scaled down from its 16×16; see "art at more than one size"). Bending it over
the four edges, the way a side cell's overhang bends round the corners, is a later version's job.
`Spot.face()` answers `TOP_FACE` (4) for them rather than one of the four side faces, which is what
datagen puts in the placement texture's kind texel and what `ovvar.glsl` reads back. The left arm's
top face is mirrored in u like every other left-limb face.

A cell is 4×4 texels except where the entry in `Spot` says otherwise (`Spot.width`/`height`), and
there are two that do. The **seat** is two cells wide, one tall, across the back of both legs — but
a seat patch may be 16×8 up to 16×12 (`Patches.SEAT_HEIGHT_MAX`), centred on the two cells the way
oversize plain art is centred on its own cell, so the extra rows hang onto the cloth below and
above. It is always the full two cells wide: half of it goes on each leg, and there is nowhere for a
narrower one to be. The
**big back cell** (`BACK_BIG`) is the whole back face below the collar, 8×8 texels — 16×16 px,
exactly `Patches.MAX_ART` — so it is the one cell on which the biggest patch in the catalogue lies
whole, with nothing wrapped round onto the face next door. Any plain patch goes on it, centred.

**Overlapping patches.** The big back cell covers the two `BACK_TOP` cells and all three may be
sewn at once — a big piece on the back with smaller ones over it is how the back of a real ovve is
built up. The stack is one number, `Spot.layer()`: how many overlapping cells are bigger than this
one, so a patch is drawn over the cell it lies inside and the big cell is the background it was
sewn to be. Every path reads it from there (`Spot.stacked`) — the pack's layer list
(`EquipmentJson.layerTextures`, which the paper doll composites too), the sprites on a stand (the
one on top is lifted a hair further off the cloth), and the instant channel, where the cell table
carries the layer in its own B: the ranked set the dye colour holds has no order of its own, so the
shader draws the highest layer of the cells a fragment falls in — and, since the dye colour is a
single layer, falls back to the cell underneath where the top cell's art has painted nothing, which
is what the pack path's separate layers do by themselves.

The **seat** is the one thing that still shuts cells out (`overlapping()`, worked out from the
cells' own rectangles): it is not one patch on one cell but one patch cut in half across two cells
of two different boxes, drawn as a single sprite on the seam between them, so "one of them on top"
has no answer all three paths could give. Sew the seat or the legs' back cells, not both.

Cells come and go, and players' items and stored wardrobe rows name them by id, so
`SpotPlacements.CODEC` drops a placement naming a cell or a patch this build has not got — with a
log line — and keeps the rest of the design rather than failing the whole of it. (`BACK_LOW_LEFT`
and `BACK_LOW_RIGHT` are what went when `BACK_BIG` arrived.)

The catalogue (`Patches.java`) holds ITK, METAcraft Rivals '26, IT and Data, then
Spiken, Släggan, Ticket to my heart, the Maid dress and Pung, then Vlad's IN, IN (gold), the three
Nyckeln keys and KomMN, and last Måns's JGS, TMEIT and TMEIT Marshal. ITK, IT, Data, Spiken, Släggan,
the Maid dress, the ticket and Måns's three are 12×12 and hang over their neighbours (except on the big back
cell). Several are also drawn at more than one size: ITK ships `itk_8x8.png` (Kexana's original 8×8
ITK, the art that shipped before the 12×12), so it lands whole on a shoulder instead of losing its
edges, and `itk_16x16.png`; IT ships `it_16x16.png` (PolymITer's 16×16 IT sprite), so it fills the big
back cell and is its own inventory icon at 1:1, and its 8×8 is scaled down from that 16×16 rather than
drawn — see "art at more than one size" below; Spiken, Släggan, JGS, TMEIT and TMEIT Marshal each ship
an 8×8 and a 16×16 beside their 12×12 — Ticket to my heart among them, now Måns's 12×12 redraw in
place of Cactooz's 9×6 original;
Rivals and Pung are the seat patches — Rivals is Data's cerise with a creeper against IT's laser
violet with a VS, at the seat's own 16×8; Pung is 16×10, so its top and bottom rails hang over the
seat's row onto the cloth. (Kexana's Nyckeln'26, an 8×8 of the same key as Vlad's Nyckeln 0x2, is gone from the
list; its id still reads as that patch, so an ovve sewn with it loads.) New entries go at the end of the list: a design's instant
code is its position in it (see "the dye colour" below), so an entry inserted in the middle would
repaint every patch already sewn. Släggan's file is `slaggan.png` — a resource id is `[a-z0-9_.-]`,
so the ä lives in the display name only. Seat art is drawn as seen from behind, the only
way anybody sees a seat, so it reads across the figure the way it was drawn: the art's left half
goes on the wearer's left leg, which is the leg at the viewer's left from behind
(`Spot.seatColumn`). The armour model draws that leg as a mirror image off the right leg's strips,
so each of the three paths that draw a seat has to mirror its half back — the pack's `_l` texture
is pre-flipped, the shader flips the fragment, and the paper doll flips the part. Both player
paths are pinned to the art itself by `seatHalvesSitOnTheLegTheArtWasDrawnFor`, never to the cut.
Sewing: put the ovve on
an armour stand, hold a patch, look at the stand — the patch shows washed out on the cell you aim
at (a ghosted sprite, see below), the action bar
names it, right-click sews it on; sneak to aim at the far face of the part you look at
(the back of the body, the back of an arm). The aim follows the stand's pose. Shears on a sewn
patch unpick it. No cap on the number of patches. An **empty hand** on the stand's top — its body or
an arm — takes the whole ovve off, into your hand, as clicking its legs does. It has to be ours to
do: the chest slot holds the companion top (`OvveTopItem`) while the ovve's top is up, and that is
not a possession — out of a chest slot it deletes itself and the ovve's own tick puts a fresh one
straight back, so vanilla's swap of that slot looked like the top jumping back onto the stand and
nothing else happening. The garment is one item in the legs slot, so taking its top off the stand
means taking the ovve off; the companion goes with it. Real chest armour over the ovve is still
vanilla's to swap, and a session stand still refuses everything but sewing. While the ovve is on a stand its patches are flat item displays laid on their cells
(`StandDisplays`, Polymer virtual entities following the stand's pose; the armour draws none of
them there), so a sewing session needs no resource pack at all — the pack matters once the ovve
is taken off and worn. A shoulder's sprite lies flat on the arm box's own top face, which faces
straight up on an unposed stand. A big patch lies flat on its cell's face, the overhang sticking out past
the corner (`PatchPieces` can instead cut it at the corners and lay each piece on the face it
hangs over, so it bends round the box on the stand too; that is off for now,
`BEND_ROUND_CORNERS`, until the pieces line up with the armour on posed stands); datagen makes
one item model per piece — a single zero-thickness quad, so the displays are sprites, not slabs
— and a ghosted twin of each (mixed
60 % to white), which is what the patch being aimed at is shown as, on top of everything, until
it is sewn. That is the whole preview. The companion top and the virtual cuffs carry the same
on-stand flag, so nothing draws the patches twice.

With the stitching minigame on (`config/ovvar.json`: `sewing_minigame`, `stitches`; default on,
6 stitches for a cell-sized patch — a longer outline, a bigger patch or an intricate edge, takes
proportionally more, up to 16) the right-click opens a dialog instead: the patch lies on the ovve's cloth and the
seam goes around its edge, following the shape of the art (a notched edge is sewn into its notch). The
holes come in pairs — one on the cloth just outside the edge where the thread comes out, one on
the patch just inside where it goes in — so each pair is a stitch over the edge, and the thread
runs under the cloth to the next pair (`Seam.Style.WHIP`; `ZIGZAG` draws every run on top like a
machine seam). The needle sits on the next hole, coming in over the edge; click where it is to
pull it through. Each pull sounds at the stand, the last one sews the patch (`SewingGame`);
Escape or the "Cut the thread" band abandons it, and the patch only leaves your hand when the
seam is done. The dialog's clicks come back as custom click actions (`CustomClickMixin`).

Every button in that dialog is a sprite. The picture is a 7×7 grid of 20 px buttons whose
labels are glyphs of a bitmap font the pack carries (`SewingFont`,
`assets/ovvar/font/sewing.json`): an opaque cloth tile a pixel larger than the button on every
side hides the vanilla button and meets its neighbours across the grid gaps, and the last cell's
label — buttons draw in grid order, so it comes out on top — also draws the patch, the thread
(a row of dot glyphs, so any angle works), the stitch marks and the needle over the whole picture
with negative-advance spaces; every such glyph has one codepoint per vertical position, and its
texture is padded below so no ascent exceeds its height (the client drops the whole font
otherwise). The cell under the needle carries the click. Datagen builds the glyph textures from
`art/ovvar/sewing/`: `cloth.png` (22×22, recoloured in every chapter's colour), `needle.png`
(26×9, pointing right; mirrored and turned for the other directions), `thread.png` (3×3),
`stitch_in.png`, `stitch_out.png` and `hole.png` (5×5), `band.png` (154×22, the text is stamped
on), plus each patch's art scaled up whole to fit 96 px (an 8×8 at 12×, a 12×12 at 8×, a
16-wide seat patch at 6×). It also
traces each patch's outline from its opaque texels into `ovvar/outlines.json` (`Outline`), which
`Seam` spreads the holes along at runtime. Replace the PNGs and `runDatagen`; the
`sewingLabelsFitTheirButtons` game test checks every label of every seam still measures what the
client centres without scrolling. Mockups of the design are in `docs/mockups/sewing/`.

## Wardrobes: one look per player, on every server

An ovve belongs to a player (`ovvar:owner`, set the first time a player's inventory ticks it, or
by `/ovvar give`). What they have sewn on it, and the patches they own but have not sewn (their
*stash*), make up their *wardrobe*: one versioned row in a store shared by all the servers
(`Wardrobe`, `Wardrobes`). Sew on one server, log into another and wear the same ovve; a
recrafted ovve just shows the design again. Only the look and the patches travel: pockets,
enchantments, the top being up or down stay with the item.

### An ovve is its owner's

Handing somebody an ovve does not hand them the wardrobe behind it, so an owned ovve is worn and
changed by its owner and by nobody else:

- **wearing.** A foreign ovve does not go in the legs slot: the armour slot refuses it (dragging,
  clicking, shift-clicking), a dispenser aimed at the player refuses it, right-clicking it says
  "That ovve belongs to \<name\>" in red, and one forced in anyway (`/item replace`, another mod,
  the rule changed while it was worn) is taken off on the wearer's next tick and put back in their
  inventory. `designs.others_ovve` chooses between that (`block`, the default), `rebind` (a given
  ovve becomes the holder's, showing *their* design) and `allow` (anyone may wear it, showing its
  owner's design — how it was before);
- **changing.** With `designs.edit_requires_owner` on (the default) only the owner sews a patch on
  an owned ovve or shears one off it, on any stand: "That ovve belongs to \<name\>; only they can
  change it". The name comes from the server's own name cache, or "someone else" for an owner it
  has never seen.

An ovve with no owner is still anybody's: they may wear it and sew on it, and it becomes theirs the
first time a player's inventory ticks it (`bind_on_pickup`) — and with `bind_on_pickup` off nothing
binds and `rebind` hands nothing over either. A gamemaster is outside all of this: `/ovvar give` and
`/ovvar patches` write a player's design straight into their wardrobe, whoever runs them. Stands and mannequins wear and show
anybody's ovve unchanged — that is what makes a sewing stand and a showcase work.

A patch lives in exactly one place: as an item in the world, in a stash, or on a design. The
`ovvar:patches` component on an owned ovve is a copy for drawing, refreshed every tick and never a
source. Every change is a compare-and-set naming the version it saw; a write that lost the race
fails, the cache refetches, and the player is told to try again. A sew takes the patch (from the
hand or the stash) as the click lands and it comes back if the store says no; an unpicked patch is
handed out (to the hand or the stash) only after the store has let go of it. So two ovves of one
owner, or two servers, cannot hand the same patch out twice. Ovves without an owner (`/ovvar
stands`, showcase) keep their patches on the item as before.

### The stash

`/ovvar stash` opens the wardrobe screen (below); `/ovvar look [player]` opens the same screen
read-only on somebody's ovve — anybody's, here or not — with their stash left out of it. On a
survival server, on your own:

- **left-click a patch** to take one out as an ordinary item: sew it on any armour stand wearing
  your ovve, the way it always worked, or trade it. The chest button puts every patch item you
  carry back in;
- **right-click a patch** for a private sewing session, only where `sessions` is on (off by
  default; `stash_click` swaps the two buttons):
  an armour stand named after you appears two blocks ahead in a walking pose wearing your ovve;
  hotbar slot 9 gets the patch (as many as the stash holds) and slot 8 a pair of shears, both fake
  and pinned there (`ovvar:session`: no dropping, no moving, the hotbar selection is held to those
  two). Aim and right-click to sew straight from the stash, shears to unpick back into it. Walk
  away, idle, die, or `/ovvar stash done` and the stand goes and your two slots come back
  (`StashSession`). Nobody else can touch your stand.

On a **minigame server** (`stash.minigame_server`) the menu is view-only, no stand takes a patch,
and any patch item that lands in an inventory is banked into the stash at once, so nothing is lost
to a locked or wiped inventory. Sewing also needs a game mode in `sew_game_modes` (adventure is
not one) and a score of 0 in the `ingame` objective. A patch earned (`/ovvar patch give`, or banked)
plays the totem-of-undying flourish with the patch's art and explains the stash in chat.

### Wardrobe screen

`WardrobeGui` (`/ovvar stash`) is a `GENERIC_9x6` chest drawn as a full-bleed piece of stitched
cloth, tinted to the open chapter's colour, instead of the plain vanilla chest background. Slot
`row * 9 + col`:

```
row 0   [tab][tab][tab][tab][tab][tab][tab][ <- ][ -> ]  <- an ovve per owned chapter; the preview turns
row 1   [ patch  ][ patch  ][ patch  ][ patch  ][ patch  ] | [prev][prev][prev][prev]
row 2   [ patch  ][ patch  ][ patch  ][ patch  ][ patch  ] | [prev][prev][prev][prev]
row 3   [ patch  ][ patch  ][ patch  ][ patch  ][ patch  ] | [prev][prev][prev][prev]
row 4   [ patch  ][ patch  ][ patch  ][ patch  ][ patch  ] | [prev][prev][prev][prev]
row 5   [take out][put in][sew][see in 3D][finish][ · ][ · ][help][close]
```

**The screen explains itself.** Every slot says what it is and, where it cannot be used, why not:

- a tab is the chapter's *ovve*, named "Data ovve", the one on show glinting and its lore reading
  "(showing)" while the others read "Click to switch to it";
- a patch in the pocket says how many of it are in the stash, where it may be sewn, and who drew it
  ("Art by Kexana" — the credit `PatchItem` puts on the item itself);
- the preview is the **whole** ovve, top and trousers as one figure, and the two buttons at cols 7
  and 8 turn it a quarter at a time (front, their right, back, their left; per open screen, front to
  begin with). There is no piece toggle any more — the chestplate and boots that prompted "why is
  there a chestplate and boots there?" were it — because there are no longer two halves to choose
  between;
- **empty states** say what would be there, across the whole panel and in the glyph layer rather
  than as one item in the middle of it: "NO PATCHES YET / EARN THEM AT / CHAPTER EVENTS" over the
  pocket and "NOTHING SEWN YET / TAKE A PATCH TO / A SEWING STAND" over the bare garment, both baked
  pixel text (`TinyType`, `WardrobeFont.NO_PATCHES` and `NOTHING_SEWN`) drawn by the title. The slots
  underneath stay empty, so there is nothing left to hover, click or mistake for a patch;
- **row 5** is one verb per action with a line saying what it does, each wearing an icon of our own
  (`WardrobeAction`, `tools/wardrobe_icons.py`, six 16×16 sprites in `art/ovvar/`). An action this
  server refuses is the *same icon at half brightness with a small red slash*, still named for the
  verb ("Take out (not here)"), carrying the reason `StashConfig` gives (`whyNoWithdraw`,
  `whyNoSessions`, `whyNoDeposit`, `whyNoMannequin` — "Minigame server: look only", "Sewing sessions
  are off on this server", "Wear an ovve first") and with no click on it. It used to be missing
  altogether, then a pane of grey glass; a missing slot teaches nobody anything and the glass read
  as a bug. The slash is a shape and not only a colour, so it reads refused without colour too;
- the **help** item (col 7) explains the screen top to bottom in five lines — tab row, left panel,
  right panel, bottom row — then what this server allows and what is sewn on each ovve.

The right block is a **picture of the player's own ovve**, not a list of icons, and it turns.
A vanilla client cannot draw an entity inside a chest screen, so `WardrobePreview` draws a paper
doll: the humanoid model's faces, cut out of the very equipment-layer textures the client draws the
garment with, laid out flat. Front and back put each arm (4×12 skin px) beside the torso (8×12) and
the two legs (4×12) below it; a side shows the body's own side face with the sleeve beside it and the
trouser leg below, which is where that side's cells are.

```
[arm][  torso  ][arm]          [body][sleeve]
     [leg][leg]                [leg]                  front/back        a side
```

**It is built up in layers, not baked per design.** One glyph per (chapter, angle) draws the bare
garment, and one small glyph per (patch, cell) draws that patch exactly where it lands on the doll —
cropped to its own art, so the glyph is a dozen pixels square, and placed by space advances and its
own ascent. The title stacks the bare ovve and then one glyph per sewn placement, so a design is
composed at the moment the screen opens. A cell sits on exactly one face of one box, and a *side*
face is seen from exactly one of the four angles, so that is **6 × 4 = 24 bare glyphs plus one per
(patch, cell the doll can show, angle that draws it) — fixed**, however much anybody sews: nothing
is regenerated and no pack is pushed when a patch goes
on. (v2 keyed the art by patch combination instead, which grew with every design, spent a glyph
budget and needed a pack build and a loading screen each time somebody sewed something.) `Combos` is
therefore back to what it was before v2, doing equipment definitions and nothing else.

A **shoulder** is the exception, being on a box's top face: no side of the figure looks down on it,
so the front and the back views each draw it **foreshortened** — the face's four texel rows averaged
in pairs into two, a 6 px cap sitting on the top of the sleeve column. Nothing else on the figure
moves: it is already exactly the panel's height (a face for the top, a face for the trousers), so
the cap takes the sleeve's own top rows rather than room of its own. It is the same face seen from
two opposite sides, so the back view's cap is the front view's turned through 180°, and the shoulder
is the one kind of cell with a glyph on two angles (`WardrobePreview.anglesOf`; `angleOf` names the
front one, which is the single picture of the cell that callers wanting one get, and where its
tooltip is quoted from). It is hoverable on both views, each at the slot that view really draws it
in — which is the sleeve's own slot, the cap sitting on the sleeve's columns.

The cells you cannot see from an angle simply have no glyph there. The wearer's left limbs are the
mirror images the armour model draws — and a left cell's art is pre-mirrored in its own texture to
suit — so every left limb is drawn flipped; the wearer's right is on the viewer's left from the front
and on the viewer's right from behind, which is why the two swap ends. The source textures hold 4
texels per skin px (`Spot.DETAIL`) but every face is first dropped back to the art's own 2 per skin
px (`Spot.ART_DETAIL`; lossless, the texture being the art scaled up by exactly that), and the doll
is drawn at 3 px per skin px, so each face is resampled ×1.5 from art (which keeps every texel the
patch art has, at the price of every other column being 2 px wide), then: 1 px
transparent gaps between the parts, the viewer's right 15 % towards black and either arm 12 % further
(a limb is a box turning away from you), a seam at the waist, and a 1 px dark outline drawn *on* the
silhouette's own outermost pixels so it costs no room. Without the gaps and the outline a front view
of arms hanging at the sides is one flat slab of cloth and reads as a texture strip; with them it
reads as a figure. A patch layer takes the same shading and keeps off the outline's own pixels, so a
patch at the edge of a sleeve cannot cut the figure's edge open. There is no head: the garment's art
has none.

Every glyph is placed by arithmetic, in the container's title, which the client draws at (8, 6)
inside the container: `WardrobeFont.move` walks the cursor with a `space` provider of ±1 … ±128 px
and back again, and a glyph's *ascent* puts its top at a given container y (a bitmap glyph's top
lands at `textY + 7 − ascent`, so anything below the header has a negative one). That is also how
the tab highlight follows the player's own tab order and how the stats readout is right-aligned.

The preview's sixteen slots therefore carry no icon at all: **on every angle**, an item wearing the
`ovvar:invisible` model (a transparent 16×16) with a name and one lore line, no click handler, over
each placement the angle on show draws — the picture shows through and all that is left of the slot
is its "\<patch\> on \<spot\>" tooltip. Turn the figure and the tooltips turn with it: a patch sewn
on the back is hoverable on the back view and its front slot is empty, because a cell is on one face
of one box and a side face is seen from one of the four sides (a shoulder, on a box's top face, is
hoverable from the front and from behind).
`./gradlew :mods:ovvar:wardrobeSheet` composites the whole screen, and all four angles side by side,
to a PNG (`WardrobeSheet`, a dev tool) so the doll can be looked at without starting a client, and
`-PsheetState=audit` prints every cell's glyph against the art it is meant to be showing. "The art",
for a patch bigger than its cell, is only the part of it that lands on the cell's own face: datagen
wraps what hangs over round the box, so those columns are drawn on the face next door and the cell's
glyph is right not to have them. `WardrobePreview.shownArt` is that window — and, for the seat, the
two legs' halves in the order the back view puts the legs; for a shoulder, the rows of the art
averaged in the same pairs the cap averages, since those averages are the colours the doll really
draws — and both the audit and the `wardrobePreviewDrawsEveryCellsOwnPatchArt` game test compare
against it, for every angle that draws the cell.

**`/ovvar look [player]`** opens the same screen read-only on somebody else's ovve: their chapters as
tabs (the ones they have a design for — their inventory is none of our business and may not be
loaded), their design on the doll, the rotation buttons, the tooltips. No stash, no take out, no put
in, no sewing, no mannequin: help and close only, and the help item is titled "A look at \<name\>'s
ovve". It works for a player who is not here, because a wardrobe is a row in a store rather than an
inventory — the name goes through the server's own profile resolver (the one `/whitelist add` uses)
and the wardrobe is fetched by UUID.

The left block (cols 0-4, rows 1-4) is the patch collection — the stash, one slot per kind, left
and right click exactly as before (take out / start a session) — and it **pages** once there are
more kinds than fit, where it used to give the last slot up to a "+N more" marker. Twenty kinds
still use all twenty slots; from the twenty-first the two ends of the bottom row become the page
arrows (the rotation arrows' own sprites, pointing the same ways), so a page holds eighteen — and
both arrow slots are reserved on every page, even one that needs only the one, so an arrow never
moves under the pointer. Kinds are sorted by name, so a kind keeps its place as the counts change;
the page is per open screen; and "page 2/3" is drawn in the tiny type in the action row's spare
middle (cols 5-6), directly under the pocket's right end — the pocket is slots edge to edge with
nothing but 1 px gutters between them, and five pixels of type has to go somewhere it does not sit
on an icon. `WardrobeGui.perPage` and `pageCount` are the whole of the arithmetic, and the game test
exercises them as arithmetic: the catalogue holds two patches, so no wardrobe this server can build
has 45 kinds to page through. Which slot of the right block a
placement's tooltip sits on is `WardrobeGui.previewSlot(angle, spot)`, and it is measured, not
tabulated: `WardrobePreview.cellRect` puts a cell-shaped mask through the very `blit` the
compositor draws a patch with — the same per-part offset, the model's mirroring and the ×1.5
resample — and gives back the pixel rectangle the cell lands in, or null when that angle does not
show the cell. The panel is exactly the 4×4 block of 18 px slot cells at rows 1-4, cols 5-8, so the
slot is the one holding that rectangle's centre, and the tooltip cannot drift from the picture the
way a hand-kept table could (the front view's slots are the ones the old table gave, and a game
test pins them). `previewSlot(spot)` is the front overload, for the callers that only ever mean the
front. A shoulder's cap is over its sleeve's columns, so the two share a slot on the front view and
the shoulder has one on the back view too. Two cells of one angle can still share a slot (16 slots, and a sleeve is 12 px wide), so the
last placement drawn to a slot wins its tooltip — a design with only one cell per slot (the common
case) always shows correctly.
Row 5's "finish" (col 4) is the old `StashGui`'s "Finish sewing" button, shown only while a stash
session is running.

The container title carries the background: `WardrobeArt` tints the single greyscale template
(`art/ovvar/wardrobe_template.png`) to the chapter's colour and draws it as one `bitmap` glyph in
the `ovvar:wardrobe` font, the same negative-space trick as the sewing dialog
(`SewingFont`) and better-pets' `pet_gui` — a `space` provider moves the cursor to the corner,
the glyph draws the whole 176×126 background, another space moves the cursor back, and every other
glyph the screen needs follows the same way. The title's own **text** is the ovve's name and nothing
else: the client draws it in the vanilla font at about 6 px a character on a screen 176 px wide, and
saying `Data ovve · earned 32 · sewn 11 · stash 0` there ran off the end of it. The counts are pixel
glyphs instead (`TinyType`, a 3×5 type face drawn in `TinyType`'s own table), right-aligned in the
spare header width: as many of the three as fit beside the name, and a name long enough to crowd
them out gives up its garment word rather than the counts ("Silicon-blue IT", not "Silicon-blue IT
ovve"). The help item's lore has all three in full whatever the header had room for. 176×126 is exactly
the `GENERIC_9x6` container's own six 18px rows below its header (`18 + 18*6`), not a px more, so
the glyph never paints opaque cloth or a stitch line over the player's own inventory below it.

"See it in 3D" (col 3, once "Show on mannequin") reuses the `/ovvar showcase` mannequin builder
(`ModCommands.buildMannequin`) through `WardrobeMannequin`, which adds the guardrails a
gamemaster-only command does not need: refused outright on a minigame server; one per player (a
second click discards the first, tracked by holding the entity, not by re-finding it in the
world); gone on its own after 60 seconds, past 8 blocks, or on disconnect; and the copy of the
ovve it wears has its `BUNDLE_CONTENTS` stripped and is permanently invulnerable (and can never
die at all (`ServerLivingEntityEvents.ALLOW_DEATH`), so there is no death event left to drop
its equipment on) — the guard against a copy of somebody's real, pocket-stuffed ovve becoming a
dupe machine.

**Deviations from the design doc**, for the record:
- The doc calls for 7 backgrounds; `Chapter` has 6 values (`DATA`, `IT`, `IT_KISEL`, `MEDIA`,
  `DATA_POLYMITER`, `IT_POLYMITER`), so there are 6 — one per chapter, as the doc's own "one
  background per chapter" says.
- the doc's two piece toggles are gone altogether: the preview shows the whole garment, both halves
  at once, and the two buttons there now turn it instead. Where a half still has to be named it is
  "trousers", not "feet": the legs and waist are what a wearer sees, and the boots render channel
  `OvveFeet` adds to that half is not a garment piece of its own.
- Row 5's "take out" and "sew on stand" are reminder icons, not buttons: there is no "selected
  patch" state, so the actual gestures stay on the collection slots themselves, as they always
  were. On a minigame server they are greyed out with the reason rather than gone.
- The doc's "mode text in the middle, drawn as background text" is instead the mode's first line
  in the help book (col 7), and the reason on each refused action: a container title is one line,
  and the glyphs on it are already drawing the background, the preview and the counts.

**Template art notes** (`tools/wardrobe_template.py`, run once, checked in): 176×126 (see above —
exactly the container's own rows), greyscale + alpha; a base cloth tone (132) with ±6 per-pixel
luminance noise for a woven feel; the patch panel (cols 0-4, rows 1-4) is a lighter "cream canvas
pocket" (200); the preview panel (cols 5-8, rows 1-4) is a slightly darker cloth (118), with no
painted silhouette any more — it always carries the rendered paper doll, and two figures on top of
each other was half of the "bad overlap of textures" this fixed. A dashed white overlock-stitch
outline (255) runs around one box per cell of the tab row and the action row, both panels, and the
whole background.

**Everything drawn lies on the slot grid.** A slot's *icon* fills the 16×16 at (8 + 18·col,
18 + 18·row) and the 18×18 *cell* around it — the ring the vanilla slot frame would use, which this
background hides — starts one pixel up and to the left, at (7 + 18·col, 17 + 18·row). Every line the
template draws is on that ring and never inside the 16×16; before that the panels' and the tab row's
outlines ran a pixel inside the slots and every tab's ovve icon had a stitch line through it. The
script checks it before saving and the `wardrobeTemplateBoxesAreOnTheSlotGrid` game test checks the
checked-in PNG, every slot of the grid. `WardrobeFont.cellX`/`cellY` are the same arithmetic for
everything placed at runtime. `WardrobeArt` tints
every non-white pixel by `luminance/255 * colour` and leaves anything at or above luminance 250
pure white, so the stitching reads the same on every chapter.

`config/ovvar.json` → `designs` (the store):

    backend                  file (default) | jdbc
    file_directory           file backend: an absolute directory, "" = <world>/ovvar/wardrobes
    jdbc.url                 jdbc:mariadb://host:3306/db  or  jdbc:postgresql://host/db  (drivers bundled)
    jdbc.user, jdbc.password, jdbc.password_env   the password from the file, or from the named environment variable
    jdbc.table               created if missing: owner CHAR(36) PRIMARY KEY, version, data (JSON), updated_at
    jdbc.driver_class        force a driver class; "" lets the URL pick
    jdbc.connect_timeout_seconds, jdbc.query_timeout_seconds
    bind_on_pickup           an unowned ovve becomes the first holder's (default true)
    others_ovve              somebody else's ovve: block (default: not wearable, nothing sewn on or off it)
                             | rebind (a given ovve becomes the holder's) | allow (anyone wears it, owner's design)
    edit_requires_owner      only an owned ovve's owner may sew on it or unpick from it (default true)
    sew_when_unreachable     store down: sew anyway and queue the write (default false: refuse, keep the patch)
    unpick_when_unreachable  store down: hand the patch back anyway and queue the write (default false; the dupe direction)
    retry_seconds            how often failed loads and queued writes are retried (default 15)
    log_queries              log every load and store

`config/ovvar.json` → `stash` (this server's rules):

    minigame_server          true: view-only stash, no sewing or unpicking anywhere, patch items banked (default false)
    sew_game_modes           game modes that may sew and take patches out (default survival, creative)
    ingame_objective         scoreboard objective; a non-zero score means "in a game", no sewing (default "ingame", "" = off)
    bank_on_pickup           minigame (default: only on a minigame server) | always | never
    bank_in_creative         bank creative players' patch items too (default false)
    unpick_to_stash          unpicking on an ordinary stand sends the patch to the stash instead of the hand (default false)
    withdraw                 right-click in the stash takes a patch out as an item (default true; never on a minigame server)
    sessions                 the private sewing flow exists (default false: the stash only hands patches out)
    stash_click              withdraw (default: left-click takes the patch out as an item, right-click opens a session) | session (the reverse)
    any_stand                sew and unpick on any armour stand wearing an owned ovve, not only a session stand (default true)
    session_reach            blocks a player may walk from their session stand (default 8)
    session_seconds          idle time before a session ends (default 300)
    explain_in_chat          the stash explanation when a patch is earned (default true)

`config/ovvar.json` → `server` (what this server calls itself):

    name                     this server's name in the MOTD (default "METAcraft")

The MOTD is set from those two blocks when the server is up, so the server list says what a player
gets before they join: `METAcraft Survival · ovve sewing on stands, patches are items`, or
`METAcraft Minigame · ovve stash only, no sewing` where `stash.minigame_server` is on. Every key
above is optional in the file: the defaults are the ones documented here, and a key only needs
writing to change it.

Since JSON has no comments, `config/ovvar.json` and each of its `designs`, `stash`, `server` and
`designs.jdbc` blocks carry their own `_help` object (rewritten every save, so edits to it do not
stick) with a `_about` line and one entry per key, the same text as above; open the file itself if
you would rather read the help there than here. Every block is always written, even one that is
exactly its own defaults, so its `_help` is always there too.

The file backend is fine for one server or a shared mount; a network of servers wants `jdbc`
(MariaDB/MySQL and PostgreSQL drivers ship in the jar).

## Debug commands (gamemasters)

    /ovvar give [player] <chapter> [patches]   their stored design; with patches (all / cell.patch / ids) those replace it
    /ovvar patches <patches>                   re-sew the ovve in your main hand (all / none / cell.patch, bare ids); owned: replaces the design
    /ovvar showcase <chapter>                  armour stands: top down, top up, each patch, every cell filled
    /ovvar stands <chapter>                    three posed stands in a plain ovve, for testing the sewing aim
    /ovvar minigame [on [stitches]|off]        the stitching minigame setting; saved to config/ovvar.json
    /ovvar aimlog on|off                       log every stand click and aim change with its numbers (server log)
    /ovvar stitch <cell.patch>                 open the stitching dialog on the nearest ovve stand, no aiming needed
    /ovvar reload                              (any player) the latest resource pack, now
    /ovvar patch give <targets> <patch> [n]    a patch into the stash of every selected player, with the flourish
    /ovvar stash                               (any player) the stash menu; stash done ends a session; stash deposit banks held patches
    /ovvar store status                        the wardrobe store: backend, cache, queued writes, this server's role, sessions
    /ovvar store show [player]                 a player's wardrobe (version, designs per chapter, stash)
    /ovvar store reload [player]               drop and refetch a player's wardrobe
    /ovvar store reconnect                     re-read the config and reopen the store

## Building

    ./gradlew runDatagen   # turns src/main/resources/art into src/main/generated (assets)
    ./gradlew build        # build/libs/ovvar-<version>.jar (Polymer bundled; Fabric API separate)

`build` refuses to run without the generated assets, and the mod refuses to start without them.

## Testing

`Start Server.command` runs an offline dev server on localhost with the pack auto-hosted;
`Start Vanilla Client.command` launches a plain vanilla client that joins it. Give yourself an
ovve with `/ovvar give data all` or from the Ovvar creative tab. `Run Tests.command` runs the
game tests — `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:ovvar:runServer`,
with `-PrunDir=<dir>` when a dev server already holds `./run`'s world lock — (`OvvarGameTests`): every cell aimed at on stands at rest, posed and turned, and the
sneak far-face rule, checked against `StandAim.cell`, the independent cell → point mapping (the
shoulders are left out of the posed run: an arm rotated 60° about z swings the top of its box into
the torso, so there is no line of sight to its top face); that a shoulder's plane faces straight up,
that looking down at one resolves the shoulder and not the sleeve's front face beneath it, and that
its sprite lies on that plane; that an empty-handed click on a stand's chest takes the whole ovve off
with its patches and leaves no companion top, while a click on a leg is still left to vanilla's own
swap; and
the stitching minigame played through with the clicks its dialog sends (stale clicks ignored,
sewn on the last pull, nothing sewn after cutting the thread). `WardrobeTests` runs the store
(both backends, the compare-and-set cache, one patch in one place) and the ownership rules: a
foreign ovve is refused by the equip checks and evicted by the tick, a stranger's sew and unpick
change neither the store nor the ovve, the owner's own still work, `rebind` and `allow` still do
what they say, and the MOTD names this server and its mode. It also runs the wardrobe screen: that
nothing the background draws overlaps a slot's icon, the title's glyphs and spaces, the tab row and
its highlight, the empty-state notices, the four angles and that every visible cell has a glyph of
its own per angle that draws it, that a placement is drawn by that glyph on the angle that shows it
and by nothing on the angles that do not, that a shoulder patch is clipped to the arm's top face and
drawn nowhere else on the texture (the left arm's mirrored), that its cap on the doll is the
foreshortened top face over the sleeve's own columns, the rotation buttons, the title's length and the stats readout's place, what each mode
does to the action row (and that a refused action wears the dimmed model), and that `/ovvar look` is
read-only and driven by the other player's design.

## Adding a chapter

Drop the chapter's 64×64 skin overlay (the same file the website uses; pure green = "erase the
skin here") into `src/main/resources/art/ovvar/`, add a line to `Chapter.java` naming it and,
optionally, its rolled-down (nercabbad) overlay and a tint colour, then `runDatagen`. The
armour layers, equipment definitions, icons and names are derived from that. A chapter can
instead name a ready-made 64×32 leggings texture for its rolled-down state (`nercabbadArmour`);
it is shifted to the chapter's colour and used as it is. The `*_polymiter` chapters do this with
PolymITer's hand-drawn ovve (`art/ovvar/polymiter/nercabbad.png`), there to compare the two
styles in game — `/ovvar give data_polymiter down` next to `/ovvar give data down`.

## Adding a patch

One line in `Patches.java` (id, name; `true` for a seat patch) and a PNG at
`src/main/resources/art/ovvar/patches/<id>.png` — 8×8 for a cell-sized patch, 16×8 to 16×12 for a
seat patch, or any even size up to 16×16 declared in the catalogue line: such a patch is centred on
its cell and hangs over the neighbours, later-sewn on top, all the way round the part — past a
limb's outer face lies its back face, the strip being a loop. Patch art is drawn at twice the skin's
resolution (`Spot.ART_DETAIL`: a cell is 8×8 art px) while garment and patch textures are the armour
layout at four times it (`Spot.DETAIL`), the art scaled up ×2 as datagen bakes it and the shader
dividing by the same ratio (`OVVAR_ART_SCALE`) when it reads the library — so art stays the size it
always was on the cloth, and the texture's head rows hold four times the library they did. A big patch rides in the dye
colour like any other (the shader bends it round the corners from its own cell's face, as the
pack will — except on a shoulder, where it is clipped to the top face instead). Then `runDatagen`.
The first 21
designs in the catalogue can ride in the dye colour (instant, previewable); later ones only go
through the pack; the preview library is the head rows of the texture (64×16 skin texels less the tables and
the marker corner, packed by the texel, an art at its own size) and datagen fails loudly when that runs out.

### Art at more than one size

A patch may ship its art at another size as well: `<id>_<w>x<h>.png` beside its own PNG, so ITK is
`itk.png` (12×12, the size its catalogue line declares), `itk_8x8.png` (Kexana's original 8×8 ITK,
which is what the patch was before the 12×12 was drawn) and `itk_16x16.png`, and IT is `it.png`
(Cactooz's 12×12) and `it_16x16.png` (PolymITer's own 16×16 sprite). Nothing is declared — the
catalogue entry stays one line and datagen finds the variants by file name (even sizes up to 16×16;
a seat patch's variants are 16 px wide, its own width). A file that begins with a patch's id and an
underscore but is not a size this build can ask for fails datagen, so a misspelt name is not
silently never drawn.

**The smaller sizes of a 16×16 are scaled down when nobody has drawn them.** A patch that has art
at 16×16 — as its catalogue size or as a variant — also gets the sizes a cell can ask for below
that, 12×12 and 8×8, scaled down from it; a drawing of that size always wins, and a seat patch
never gets any (its width is the two cells', so a narrower one has nowhere to sit). So the artist's
workflow is: **draw the 16×16, then draw again only the sizes the scaler gets wrong.** IT's 8×8 is
and IN (gold)'s 12×12 and 8×8 are the generated arts in today's catalogue — ITK, IN and the three
Nyckeln keys are drawn at all three sizes, so they generate nothing.

Nothing is written to the source tree: the generated `Patches.Art` carries the art it is scaled
from, is named like a variant (`patches/it_8x8`, which is what datagen calls the textures and models
it writes for it) and has no classpath resource of its own — ask `Tex.art(art)` for the pixels and
it either reads the PNG or scales the source. The scaling is `Tex.downscaled`: an area-weighted
majority vote, so each output pixel takes whichever colour covers most of the source rectangle it
stands for and the result uses **no colour the source did not** — which the trim channel needs (its
key palette is built from every opaque colour of every patch art) and pixel art wants anyway. An
even split goes to the colour that is rarer in the whole art, which is what keeps an outline, an eye
or a letter stroke alive: the background always has the votes. It was picked by trying the
candidates against the sizes the artists had already drawn both of, and its 12 and 8 px ITK are
pinned texel for texel in the game tests.

Which art a place shows is **one function**, `Patches.artFor(patch, cell)`: the largest that
fits what the cell asks for, and the catalogue's own art when none of them does — which is exactly
what a patch with a single art gets everywhere. What a cell asks for is `Patches.Fit`:

| fit | the cells | what it shows |
| --- | --- | --- |
| `CLIPPED` | a box's **top** face (the shoulders), where the art is cut to the cell | the largest art that fits the cell whole — ITK lands on a shoulder as Kexana's 8×8 instead of losing its edges, IT as the 8×8 scaled from its 16×16 |
| `FILLED` | a cell as big as art may get (`BACK_BIG`, 16×16) | the largest art the patch has — IT and ITK both fill the back at 16×16 |
| `OVER` | every other cell, and the seat | the catalogue's own art, hanging over its neighbours, which is the point of an oversize patch — unless that art is over 12×12 (`Patches.OVER_MAX`, a cell and a half), which was drawn to fill the big back cell and would blanket an ordinary cell's neighbours, and then it is the largest art that fits 12×12 instead |

**The fall-back is the old behaviour, exactly.** A cell that finds nothing it can use takes the
catalogue's art and does with it what it did before variants existed: the item icon scales the
catalogue's art to fill 16 px and centres it, and an ordinary cell hangs the art over its
neighbours. Every non-seat patch in today's catalogue is 12×12 or smaller by its catalogue line, so
the `OVER` rule above never fires for any of them, and every patch but ITK and IT is drawn on every
cell exactly as it always was.

Everything that draws a patch goes through the same call: the pack's placement textures, the instant
channel's library, the paper doll and its glyphs, a stand's sprites and the inventory icon. The one
thing that does not is the **sewing game**, which is played before the cell is settled and so shows
the catalogue's own art whatever the cell will pick.

The instant path cannot ask the question — the dye colour carries a cell and a design, not a
choice — so the preview texture's design tables hold a row per (design, fit), the library holds
every art a design can be drawn as, and the shader reads the row for the fit of the cell it is
drawing (`OVVAR_FIT_*`, the same three-line rule as `Fit.of`). That is what widened the tables to
three skin texels each; `theInstantLibraryHoldsEveryArtADesignCanBeDrawnAs` reads them back through
the shader's own constants.

## Blockbench plugin

`tools/blockbench/ovvar.js` is a Blockbench 5 desktop plugin: draw a patch, sew it onto an ovve,
and see it on a player model exactly as the game draws it, without starting the game — then
export the art back into this checkout. It has no copy of `Spot`, `Patches` or `Chapter`; datagen
writes those out as `src/main/generated/ovvar/blockbench/manifest.json`, `BlockbenchManifestTests`
holds that file to the enums field for field (`Spot.anchored` included), and the plugin's own
Node tests hold its composition to the very placement, trim and garment textures the pack ships.
So a cell moved in `Spot.java` is a failing test, not a plugin that quietly draws last week's
model.

Install, permissions, the workflow and how to run its tests: `tools/blockbench/README.md`.

## How the look works

The client draws an equipment asset as a stack of 64×32 layer textures over the armour model:
the chapter's base, one static texture per sewn placement (`textures/entity/equipment/<layer>/patch/<cell>/<patch>.png`,
all generated by datagen), and a dyeable preview layer. Which layers to stack is the one thing
that is per combination, so each combination of placements on a half is its own tiny equipment
JSON. Datagen writes only the empty ones; `Combos` remembers every combination ever sewn in
`<world>/ovvar/combos.json`, adds their JSONs when Polymer builds the pack, and when a new one
appears (beyond what the dye colour shows, see below) rebuilds the pack. Each build is a generation and each player is on the generation they
last loaded; who gets pushed a new pack, and when, is in "Reloads only when asked for" below;
once a client reports a pack loaded (a mixin on the resource-pack response) the equipment of
every ovve it can see is sent again. Rebuilds are batched: a combination the dye colour can
still show waits a minute and a half for company; one it cannot is built within two seconds.

The dye colour carries patches without any pack change. Up to three placements per half ride in it
— six on the legs when the wearer's feet slot carries the second channel (below), four on the top
when one of them is on the chest or the back (the trim, below): a dyeable layer
is only drawn when the item has a dye colour, and that colour reaches the shader as the vertex
colour — the only per-item data an armour shader ever gets — so it carries the *rank* of the set
of up to three (cell, design) placements among all such sets (packed as three base-255 digits so
no byte is 0; on the top 21 cells × 21 designs, C(441,3) ≈ 14M states under 255³, and a game test
holds every half under the 448 states the shader's float binomials are exact to —
`Looks.INSTANT_STATES`). **Cells and designs share those 448.** The two shoulder cells took the top
half to 21, so the designs came down from 22 to 21 to stay inside it; the cap test now holds the
designs to the most that fits as well as the states to the cap, so neither number can be raised on
its own. The cost is that the 22nd and later entries in the catalogue can no longer ride in the dye
colour — a patch sewn from one of them waits for the pack to catch up (seconds; and the top's trim
channel still carries one of them at once). The catalogue is ten long, so nothing in it is affected
today. The preview texture holds the art of the first 21
designs — any size, in a block of library cells — plus cell and design tables (a cell's row
carries its own size beside its position, since a cell is not one size any more, and its row says
which kind of face it is on: a v above the side rows is a box's top face, where the shader does not
squeeze and clips the art to the cell); the pack's entity core shader
(`assets/minecraft/shaders/core/entity.fsh` + `assets/ovvar/shaders/include/ovvar.glsl`) unranks
the set and draws the art on the cells, lit white so the data colour never tints it. Designs
past the first 21 in the catalogue only go through the pack. The tooltip's "Dyed" line is
hidden.

The top has a fourth instant slot: the armour trim. The client draws a trim as one more layer
whose texture is picked by the trim pattern, and the item's trim is per-item data like the dye
colour, so datagen bakes every (body cell, design) pair as its own pattern
(`textures/trims/entity/humanoid/<cell>_<design>.png`, the art anchored at the cell and bent round
the corners the way the shader does; `trim_pattern/*.json`, one material `ovvar:patch` whose
palette maps the patch's colours to themselves) and `OvveTop.dress` sets the newest placement
that does not fit in the dye colour as the trim, if it is on one of the chest and back
cells (`Trims.fits`). **Body cells only, and it cannot be otherwise.** Vanilla draws the trim
itself, so a trim texture carries none of our marker texels — `ovvar.glsl` never sees it. That
means two things: the squeeze to square pixels has to be baked in texel by texel by datagen, which
costs about a column in sixteen on the body's faces but two in eight on a sleeve; and, the binding
one, there is no `side` for the shader to hide it on the other limb by, so a trim for a cell on one
arm would be drawn on *both* arms, one of the two mirrored. The body is one box and has no other
limb to leak onto. A shoulder is therefore out of the channel too, even though a box's top face
is the one place the squeeze would cost nothing (it is not on the strip's perimeter) — a shoulder
patch reaches a viewer through the dye colour and then through the pack's own layer
(`patch/shoulder_r/<patch>.png`), like any cell on a limb. Trims are material-tinted, so the palette is the identity and the
art comes out as it is; the tooltip's trim line is hidden with the dye line. Everything else is sampled
exactly as vanilla. The overlay's body (16,16), right arm (40,16) and right leg (0,16) boxes are
at the same coordinates in the armour layout, so datagen only copies boxes (with the skin's
second layer painted on, and the left limbs from the skin's own left art).

## The boots pass

The client draws the feet slot with the whole leg boxes of the outer model (vanilla boot
textures are just transparent above the ankle), with its own equipment asset and dye colour: 24
more bits. So an ovve wearer's feet slot carries our legs preview layer too (`OvveFeet`):
vanilla boots of a known material — chainmail, copper, iron, gold, diamond, netherite — are
marked with the wearer and shown to clients as "their layers plus ours", trim and glint kept
(`equipment/feet/<material>.json`); an empty slot is shown to *other* players as virtual cuffs
that exist only in their equipment packets, so the wearer's inventory stays empty there and
boots go on as usual (their own client renders their body from that inventory, so they see up
to three fewer of their own newest leg patches until the pack catches up). The legs then take
six instant patches instead of three. Leather boots use the dye colour for their own colour and
other mods' boots have layers we don't know: over those the channel is off and the legs fall
back to three. The boots pass is
inflated 1.0 where the leggings are 0.5, so the shader draws it on the leggings' pixel grid
(squeezed in x and y) and the two layers' pixels line up.

## Reloads only when asked for

A pushed pack is a loading screen, so nobody gets one they did not cause. On an armour stand
nothing needs the pack (the patches are display entities). The pack is pushed to a player in
exactly two cases: an ovve came into their inventory — off a stand, `/ovvar give`, `/ovvar
patches` — with more patches on a half than their pack plus the dye channels (and the trim) can show, in which
case the pack is built at once and sent to them the moment it is ready (`Looks.claimIfNeeded`
from `OvveItem.inventoryTick`); or they ran `/ovvar reload` (any player), which sends the
current pack, after a build if one is pending. Everyone else keeps the pack they have and sees
what it holds plus the newest patches in the dye channels; a half with more new patches than
that shows the older state to them until they reload or rejoin (a joining player gets the
current pack). Every combination is still built in the background within 90 s so the pack is
complete for whoever joins next.

**This is the feature, not a gap in it.** It is tempting to push the new pack to everybody online
the moment somebody's design outgrows the dye channels — the people looking at a wearer are the
ones whose clients draw it, and until they reload they see only the instant part of it. It was
tried, and it is wrong: a loading screen in the middle of play, for a garment somebody else sewed,
costs far more than a shoulder patch that turns up a minute late. So the wearer gets the push (they
caused it, and they are standing at a wardrobe or a stand, not in a fight), everybody else sees
what their own pack plus the dye channels can show until they choose to `/ovvar reload` or rejoin,
and reloading stays a thing you do when you want to.

## Square pixels

The armour model draws a texel wider than it is tall: the box is inflated (1 on the chest
layer, 0.5 on the leggings layer) but its texture is not, so a face n texels wide covers
n + 2·inflate units while 12 rows cover 12 + 2·inflate — a sleeve texel is 1.5 × 1.167 units,
a chest texel 1.25 × 1.167. Pixel art hates that, so the shader draws everything of ours on the
box sides with square pixels, which leaves 2·inflate units of slack per face. The garment and
the preview (many cells, one per face) centre each face's texels on the face, so the cells sit
on the fabric's grid, and the slack is a margin at every corner: the garment stretches its
edge column across it, the preview shows nothing there. A sewn patch's own texture holds one
patch on one face, so it is drawn continuous round the box from that face instead — its
texels centred, the neighbours' continuing past its edges at the same pixel — and all its
slack lands in the middle of the opposite face, which the patch never reaches: a big patch
hanging over a corner bends round it unbroken, and no corner ever shows a stretched, doubled
or cut column (`ovvar_centred`, `ovvar_anchored` and `ovvar_uv` in `ovvar.glsl`; the face is
in the kind texel's B). Each texture carries which layer it is for (the layer texel, two left
of the marker: R = 2·inflate).

## Asymmetric sleeves and legs

**Seeing what the shader makes of a top face.** Three dev switches in `ovvar.glsl`, all off and
`theTopFaceDebugPaintShipsOff` holding them off, paint a limb box's top face instead of drawing it:

| switch | layer | what it paints |
| --- | --- | --- |
| `OVVAR_DEBUG_TOP_FACE_SIDES` | base garment | **red** where the mirror test calls the fragment mirrored, **blue** where it does not |
| `OVVAR_DEBUG_TOP_FACE_MISS` | preview | **magenta** where no cell of the instant design matched the fragment |
| `OVVAR_DEBUG_TOP_FACE_HIT` | preview | **green** where a cell *did* match — instead of the art, so the colour says the shader got all the way to the library |

(`OVVAR_DEBUG_TOP_FACES` turns on all three.) They are separate because a run with all three on
cannot say which of the paintings changed the picture: `..._HIT` alone leaves the frame otherwise
normal, so two green shoulders mean the shader is drawing both and anything still missing is later
than this shader, while one green shoulder means the preview path really does reach a cell on one
arm only. They paint from four opaque texels datagen puts in every texture of ours (`DEBUG_X`, the
marker row's right-hand end), so a program of ours still only ever returns a texture coordinate.

The armour model draws the left arm and leg as mirror images of the right ones from the same
texture strips, so vanilla can't show different art per side. The same shader detects mirrored
fragments from the handedness of the texture mapping: on a base texture it samples the limb boxes
one strip up, where datagen puts the mirrored left-side art; a placement texture is marked with
its side and hidden on the other limb; the preview slots carry the side in their cell.

**The handedness is read with the sense of the kind of face the fragment is on.** A box's top and
bottom faces need not unwrap the way its four sides do: v runs *across* the box there, from the back
edge to the front, instead of down it (which is what puts the top face's last row against the front
face's first, and is why a shoulder patch reads with the art's top towards the wearer's back). Only
u's direction is then in question, and the handedness follows from it; the derivation is written out
in `ovvar.glsl`. `OVVAR_MIRROR_SENSE` is the side faces' calibration and `OVVAR_TOP_FACE_SAME_SENSE`
says whether the top's is the same one — it is, which the playtest settled: taking it as inverted
put each shoulder's art on the other shoulder. So the top face's u runs the wearer's right to left,
the same way the front face's does, which is also what the edge they share demands.

`mirrored` is computed from both senses, exactly once, and every path reads that one bool, so a top
face and a side face can never disagree about which arm they are on
(`everyPathTellsTheArmsApartTheSameWay` pins that structure, since GLSL does not run in the game
tests). Note what the senses *cannot* explain: both arms' top faces are the very same texels, so
`mirrored` is the only thing that can tell them apart, and under either sense exactly one of the two
shoulder cells is selected per arm — every setting predicts both shoulders drawing, one patch each.
A bare shoulder is not a state either constant can produce. Clients whose core shaders are replaced (Iris, OptiFine) see the plain
mirrored overalls without patches — nothing breaks. Shaderpack users run `OvvarShaderPatcher.jar`
(built from `tools/shaderpatcher`, Java 11+, shipped inside the resource pack at
`assets/ovvar/shaderpatcher/` and worth linking from the website): double-clicked, it writes a
`+ovvar` copy of every pack in `.minecraft/shaderpacks` with `ovvar.glsl` spliced into the pack's
own entity program (the texture-coordinate and vertex-colour varyings are shadowed, so the pack's
code needs no changes). Patched cleanly: BSL, Bliss, Complementary Reimagined and Unbound,
MakeUp Ultra Fast, Solas, Photon, Super Duper Vanilla.

## Credits

Patch art: the 8×8 ITK (`itk_8x8.png`, the original ITK patch, now the
size variant a shoulder wears); ITK, METAcraft Rivals '26 and Data by Froosty11 (placeholders
until redrawn); IT, Spiken, Släggan and Ticket to my heart by Cactooz (the IT patch was the
PolymITer set's, redrawn with a white logo, which the IT ovve's own overlay now carries too), with
PolymITer's own 16×16 IT sprite as that patch's 16 px art (`it_16x16.png`); the
Maid dress by Mackan; Pung is a redraw of a shop patch, nobody credited. The ovve garment art is
original to this mod, cut from the chapter skin
overlays on metacraft.se/style.

`art/ovvar/patches/bakparti.png` — "varning för utsvängande bakparti" — is in the tree but not in
the catalogue: it is 32×8, and a seat is 16 px wide, so it needs redrawing at 16×8 before it can be
registered as a seat patch.
