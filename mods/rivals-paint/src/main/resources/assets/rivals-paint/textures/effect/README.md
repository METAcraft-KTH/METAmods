# Screen ink — the four overlays

`ink_1.png` … `ink_4.png` are the ink that lands on a player's screen. They are ordinary textures:
paint over them and the game uses what you painted. Nothing in the shader needs changing.

They live in `textures/effect/` and nowhere else, and they keep these names. A post effect's texture
input is written as a bare location — `end_of_frame.json` says `rivals-paint:ink_1` — and the
client turns that into `textures/effect/ink_1.png` by itself. A file anywhere else, or a location that
spells the directory out, is simply not found, and the screen fills with the magenta-and-black
missing-texture checker instead.

## The format

| | |
|---|---|
| Size | **320 × 180**, RGBA, exactly. All four. |
| Alpha | **Coverage**, and only ever **0 or 255** — never a value between. |
| RGB | A **greyscale shading map**, not a colour. |

**Alpha is where the ink is.** The shader draws a hard, blocky edge on purpose, so a soft or
anti-aliased alpha does not make a soft edge — it makes a ragged one. Keep it binary.

**RGB is light, not paint.** The shader never shows your grey: it reads the luminance and picks one of
four tones of the *team's* colour, so the same drawing works for both teams. The four bands are:

| luminance | tone |
|---|---|
| under 0.30 | the team colour at 55% — shadow |
| 0.30 – 0.60 | the team colour — the base |
| 0.60 – 0.85 | the team colour mixed a quarter toward white — light |
| over 0.85 | the team colour mixed 60% toward white — highlight |

So paint in four values and you get exactly four tones. Suggested: **40** for shadow, **115** for the
base, **185** for light, **235** for highlight. Anything in between still works; it just lands in one
of the four bands.

## What the four states mean

They are **how much health the player has lost**, in quarters. The amount the shader reads is
`255 × (maxHealth − health) / maxHealth`, so a player on full health has a clean screen and one on
their last sliver is looking through `ink_4`. Ink is not a hit meter that drains on a timer: it goes
when the health comes back, and healing up wipes it.

| file | when | how much |
|---|---|---|
| `ink_1.png` | amount 1–63 | up to a quarter of your health gone — a little ink around the edges |
| `ink_2.png` | 64–127 | about half gone — creeping in |
| `ink_3.png` | 128–191 | about three quarters gone — most of the way |
| `ink_4.png` | 192–255 | nearly dead — nearly covered, **centre still clear** |

The shader does not cut from one to the next. It draws the state below at full strength and fades in
the texels the next one *adds* on top of it, over that whole quarter of health — which is the second
reason the states have to be cumulative, and the reason the texels a state adds are worth composing as
whole new splats rather than as a redrawn edge.

Three rules the drawings have to keep:

- **Ink comes in from the edges.** It is thrown at the player's face; it does not appear in the middle
  of the glass. Every state should read as ink creeping inward from the sides, top and bottom.
- **Leave the middle clear.** The player is aiming through it. The shader keeps a circle of 9% of the
  screen height clear whatever the texture says, but the drawing should not be relying on that — the
  placeholders keep a wider island of their own (35% of the height in state 4, more in the earlier
  ones).
- **Each state contains the one before it.** Ink that landed at a quarter of your health gone is still
  there at half: state 2 has to cover every texel state 1 covers, state 3 every texel of state 2, and
  state 4 every texel of state 3. Otherwise ink flickers off as the player is hurt, which reads as the
  screen being wiped at the worst possible moment. A game test checks it, so a hand-drawn replacement
  has to keep it: paint each state on top of a copy of the one before rather than from scratch.

## Pixel size

The overlay is stretched across the whole screen with no filtering, so one texel is a fat block of
screen pixels — about 6 × 6 at 1080p. That is the grid to draw on; there is no finer detail to be had.
On a window that is not 16:9 the texels come out as rectangles rather than squares, which is the price
of ink that reaches every edge on every screen.

## The placeholders

`tools/ink_overlays.py` (Python, Pillow) drew the ones checked in here, and can redraw them:

```
python3 mods/rivals-paint/tools/ink_overlays.py
```

It is a stand-in, not a pipeline — an artist's files are meant to replace these outright, and the
script does not have to be kept working once they do. It also writes a contact sheet to
`/tmp/ink_sheet_splatter.png`: the four states 2×2 over mid grey, drawn in DATA's ink the way the shader
draws them, which is the quickest way to look at a change.

**How they are built up.** The drawing is paint splatter, from a reference of a wall twenty minutes
after a paintball fight. A splat's silhouette is a polar radius profile rather than a circle —
`r(θ) = R · (1 + Σ aᵢ · max(0, cos(kᵢθ + φᵢ))^pᵢ) · (1 − notch)` — with two or three rows of short sharp
teeth (high frequency, high exponent), two or three long thin tongues (low frequency, very high
exponent), a couple of broad bumps, a slow wobble so the body is not round underneath, and a notch
harmonic that bites back *into* the rim so it reads as torn rather than as a flower. Every splat throws
satellite droplets — a couple of dozen dots of one to four texels, thickest near the rim and thinning
with distance, plus single-texel specks further out — and long tapering drips straight down from its
underside, each ending in a fatter bead. Besides the big edge and corner splats there are small
independent ones, six to fourteen texels, scattered through the ring outside the clear middle.

Five big splats and two small ones land in state 1; seven more arrive in each later state, every splat
already on screen spreads by 22%, and every drip runs 40% longer — paint moves while you are being shot
at, which is what the four states are for. A drip's earlier lengths are drawn underneath its current
one, which is what keeps the states nested. Coverage runs about 15% / 27% / 41% / 56%.

The shading is the four bands and nothing else: a one-texel outline along every alpha edge, the body in
the base tone, a lighter island inside it shaped like a blob of its own, and a small highlight glint at
the top-left of each big body and on each drip bead. No gradients and no speckle.