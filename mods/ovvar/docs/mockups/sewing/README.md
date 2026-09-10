# Stitching minigame: sprite-text mockups

Three ways to draw the stitching dialog with sprite glyphs from the auto-served pack instead of
vanilla buttons. Everything here is renderable with what a server-side dialog already gives us:
a title, plain-text body lines, a grid of `ActionButton`s with a custom-font label each, and an
exit button. No client mod, no new packets. The textures are placeholders; the concept is the
point.

`render_mockups.py` draws every PNG in this directory from the real patch art and the vanilla
font, at 1 GUI px = 3 image px (see its docstring for the two-line setup).

| File | What |
|------|------|
| `0_current.png` | The dialog as it is today, for comparison |
| `a_seam_grid.png` | **A** — the same two-column seam, skinned |
| `b_lacing.png` | **B** — the grid *is* the picture: the patch in the middle, needles beside it |
| `b_lacing_annotated.png` | B with each element mapped to its dialog primitive |
| `c_hoop.png` | **C** — one picture in the body, two needle buttons underneath |
| `abc_side_by_side.png` | A, B, C at the same moment on the purple IT ovve |

Each strip shows the start, three of six stitches pulled, and the last pull.

## The trick every concept rests on

A dialog button draws its background sprite first and its label text over it. A label glyph that
is opaque and as large as the button hides the vanilla button entirely, so **any button can look
like anything** — the same move `metacraft-bundles` already makes with `bundle_textures.json`
(bitmap glyphs, a `space` provider with negative advances, `withShadowColor(0)`, `WHITE`).

Rules that keep it working:

- Button height is fixed at 20 GUI px; width is per button (`CommonButtonData.width`). A glyph
  with `height: 22, ascent: 14` centres on a 20 px button and overhangs it by 1 px top and
  bottom; with 2 px more width it also overhangs the sides. Since backgrounds never draw in the
  grid gaps and labels draw last, **neighbouring glyphs meet across the gaps** and a column of
  label buttons reads as one continuous picture (this is what makes B seamless).
- The label style is `WHITE` + `shadow_color 0` + a custom font. Text colour multiplies the
  glyph, so **one greyscale cloth glyph tinted with the chapter's colour** gives the cerise Data,
  purple IT and blue kisel cloth for free; no per-chapter textures.
- Layers compose at runtime with negative-advance spaces: `cloth` `←back` `stitch` `←back`
  `needle`. There is no texture per game state; the server strings glyphs together the way
  `SewingGame.dialog` strings buttons together today.
- Body pictures (`PlainMessage`) are 9 px line strips, or one tall glyph followed by blank lines
  to reserve its height. Buttons can't be placed beside the body, only below it, which is why
  A and C put the picture above and B puts it inside the grid.
- Label buttons (no action) still get a hover highlight underneath — hidden by the glyph. The
  clickable cell shows it is clickable by *content* (the needle, lighter cloth), not by hover.
  A tooltip still works ("Stitch 3 of 6").

## A — the seam, skinned

Least change to the game logic: still 2 columns × `stitches` rows. Every cell is cloth; the
2 px column gap is the seam itself (dark selvedge on each inner edge). Done rows show a cross
stitch on the side the needle went in and a dashed under-thread crossing to the next row; the
live row shows the needle with its trailing thread on lighter cloth; future rows just show
pinholes. Above the grid a 36 px body strip shows the real patch art on cloth with the spot label
and the count. Exit button is a cloth band with scissors.

Good: nothing about `SewingGame` changes except how labels are built. Weak: the patch is
decoration, the seam doesn't visibly attach it.

## B — lacing the patch on (recommended)

3 columns: needle | picture | needle. The middle column is six non-clickable label buttons whose
glyphs are 20 px slices of one 120 × 130 picture: cloth, the patch at 20× (a 4 × 4 patch is
80 px; a seat patch would be 8 × 4 at 12× = 96 × 48), and the stitches. Row *r*'s stitch lands
on the patch edge at that row's height, alternating left and right edge, so the thread laces down
the patch like a real whip stitch. The under-thread between anchors is dashed (it runs behind
the patch). The needle button sits in the side column *at the row it will pierce*, pointing at
the edge, thread trailing back to the last stitch across the grid gap.

Good: the patch visibly gets attached, click position and stitch position coincide, the layout
generalises to any `stitches` count (rows) and to seat patches (wider middle column). Weak: the
middle picture needs a glyph per slice per patch (six per patch per state layer) — cheap, and
datagen already writes per-patch textures for the pack anyway.

## C — the hoop

The body is one 240 × 99 picture: a table, an embroidery hoop with the chapter cloth, the patch
at 12×, six stitch slots around it, a spool with the thread left and a `stitch n of 6` line. The
grid is a single row of two wide buttons, `from the left` / `from the right`; the live one carries
the needle, the other is dimmed cloth. Alternate clicks, rhythm-game style.

Good: the shortest dialog, the nicest single picture, room for flourish (the spool empties).
Weak: the buttons stop meaning *where* the stitch goes, so it is more a two-key rhythm than
sewing; two buttons in fixed places also makes the click pattern trivial.

## What a real implementation needs (for B)

1. `datagen`: a font `assets/ovvar/font/sewing.json` with providers for cloth (grey, 46 × 22
   and 122 × 22), the patch slices per patch (`textures/gui/sewing/<patch>/<row>.png`, 122 × 22
   each, drawn with `Tex` from the art), stitch marks and the needle (both sides), the exit
   band, and a `space` provider with `-46`, `-122` advances. Same pattern as
   `bundle_textures.json`.
2. `SewingGame.dialog`: build labels as `Component`s in that font instead of text; three columns
   instead of two; `withShadowColor(0)`, cloth tinted with `Chapter.tint` (or the sampled
   overlay colour for chapters without one).
3. The game tests still click through `nextPull`; only the labels change.

Hand-drawn art replaces the placeholders one for one at these sizes: cloth tile 122 × 22 (grey),
needle-left/right 46 × 22, stitch cross ~7 × 7, pinhole 2 × 2, dashed under-thread segments,
scissors band 212 × 22. Patch slices stay generated from the patch art.
