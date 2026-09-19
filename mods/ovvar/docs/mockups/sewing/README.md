# Stitching minigame: sprite-text dialog

The stitching dialog draws everything with sprite glyphs from the auto-served pack instead of
vanilla buttons. This directory holds the design exploration (three concepts, `0_` to `c_`), the
pick (B, then reshaped to follow the patch's outline), and `render_asbuilt.py`, which renders the
dialog from the *generated* font data exactly the way the client draws text — so `asbuilt_*.png`
is what the implementation produces, not a mockup.

| File | What |
|------|------|
| `asbuilt_heart_data.png` | **As built**: the heart on the Data ovve, start / 3 of 6 / last pull |
| `asbuilt_variants.png` | As built: 12 and 16 stitches, the seat patch, other chapters |
| `asbuilt_under_the_hood.png` | The 49 vanilla buttons the glyphs hide, side by side with the result |
| `0_current.png` | The dialog before, for comparison |
| `a_seam_grid.png`, `b_lacing.png`, `c_hoop.png`, `abc_side_by_side.png`, `b_lacing_annotated.png` | The three concepts explored first (`render_mockups.py`) |

## The design as built

A 7×7 grid of 20 px buttons is the picture: cloth in the chapter's colour, the patch's art scaled
up in the middle (4×4 at 20×, the seat patch at 12×), and the seam going around the patch's
**outline** — datagen traces the edge of the art's opaque texels, so a heart is sewn around its
lobes and notch, not around a square. The holes are spread evenly along the outline, clockwise
from the top left, alternately just outside the edge (on the cloth) and just inside it (on the
patch), each a little further along: a whip stitch. Pulled stitches show as crosses, the ones to
come as pinholes, and the needle sits on the next hole, coming in from whichever side the edge
faces. The cell under the needle is the button that carries the click; the band below cuts the
thread.

## Why it works on a vanilla client

- A button draws its background, then its label centred over it. An opaque glyph the size of the
  button hides the button. metacraft-bundles already does this in its bundle font.
- A label wider than the button minus 2 px a side is scrolled and clipped, so every label's
  total advance is exactly that (`SewingFont.Label` keeps the books with negative-advance
  spaces). The glyphs it draws can still be anywhere: a cell glyph is 22 px on a 20 px button and
  meets its neighbours across the 2 px grid gaps.
- Buttons draw in grid order, so the last cell's label is on top of everything. It draws the
  patch, the marks and the needle over the whole picture, with moves of up to 132 px back and
  one codepoint per vertical position (only the ascent differs). 860 codepoints in all.
- Text colour multiplies the glyph and `shadow_color 0` removes the shadow, so the art shows as
  drawn. The cloth is one grey-ish tile recoloured per chapter at datagen time.

## Replacing the placeholder art

`src/main/resources/art/ovvar/sewing/`, then `./gradlew runDatagen`:

| File | Size | Notes |
|------|------|-------|
| `cloth.png` | 22×22 | tiles; hue and saturation are replaced per chapter, lightness kept |
| `needle.png` | 26×9 | pointing right, tip at (25, 4); mirrored and turned for the other directions |
| `cross.png` | 7×7 | a pulled stitch, centred on the hole |
| `hole.png` | 7×7 | a hole still to come |
| `band.png` | 154×22 | the exit button; "Cut the thread" is stamped on in the vanilla font |

The patches need nothing: their art is scaled and their outline traced. `Seam.OUT`/`IN` set how
far from the edge the holes sit; `SewingFont.COLS`/`ROWS` the size of the cloth.
