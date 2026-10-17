#!/usr/bin/env python3
"""Draws the greyscale wardrobe screen template, once, checked in as the source of truth.

metacraft.ovvar.pack.WardrobeArt tints this at runtime: output = round(luminance/255 * chapterColour)
per channel, except pixels at or above WHITE_THRESHOLD (the stitching) which are kept pure white.
So every tone in this file other than the stitching should sit comfortably below that threshold.

Geometry follows the vanilla chest grid. A slot's *item* fills the 16x16 at
x = 8 + 18*col .. + 16, y = 18 + 18*row .. + 16, and the 18x18 *cell* around it — the ring the
vanilla slot frame is drawn on, which this background hides — starts one pixel up and to the left,
at x = 7 + 18*col, y = 17 + 18*row. Every line this template draws must lie on that ring and never
inside the 16x16, or the drawn box crosses the item's icon (the v2 screen did, and it looked wrong:
`cell_rect` and `stitch_rect(inset=0)` are what keep them apart, and the
`wardrobeTemplateBoxesAreOnTheSlotGrid` game test checks it). Rows 0..5 are the six rows of the
GENERIC_9x6 container; row 0 is the tab row, rows 1-4 the patch/preview body, row 5 the action row.

Output: 176x126 -- exactly 18 (the header margin) + 18*6 (six 18px rows), the GENERIC_9x6
container's own slot area and nothing past it, so no opaque cloth or stitching paints over the
player's inventory below it. 8-bit greyscale + alpha (mode "LA").
"""
import random

from PIL import Image

WIDTH, HEIGHT = 176, 126   # 18 + 18*6: the header margin plus exactly six 18px rows, no further
random.seed(20260912)  # deterministic noise: re-running this script must not change the checked-in file

CLOTH = 132        # base cloth tone
CLOTH_NOISE = 6     # +/- per-pixel luminance noise for the woven-cloth feel
PATCH_PANEL = 200   # the cream canvas pocket the patches sit on
PREVIEW_PANEL = 118 # slightly darker cloth behind the preview, so the paper doll reads against it
STITCH = 255        # overlock stitching: pure white, never tinted (see WHITE_THRESHOLD in WardrobeArt)

SLOT = 18
ORIGIN_X, ORIGIN_Y = 8, 18


def item_rect(row, col, cols=1, rows=1):
	"""The 16x16 an item's icon fills, per slot; nothing drawn here may touch it."""
	x0 = ORIGIN_X + SLOT * col
	y0 = ORIGIN_Y + SLOT * row
	return x0, y0, x0 + SLOT * (cols - 1) + 16, y0 + SLOT * (rows - 1) + 16


def cell_rect(row, col, cols=1, rows=1):
	"""The 18x18 cells of a block of slots: its outline lies on the vanilla slot frame's own ring."""
	x0 = ORIGIN_X - 1 + SLOT * col
	y0 = ORIGIN_Y - 1 + SLOT * row
	return x0, y0, x0 + SLOT * cols, y0 + SLOT * rows


def fill(img, box, value):
	x0, y0, x1, y1 = box
	for y in range(max(0, y0), min(HEIGHT, y1)):
		for x in range(max(0, x0), min(WIDTH, x1)):
			img.putpixel((x, y), (value, 255))


def stitch_rect(img, box, inset=0):
	"""A 1px dashed-looking overlock outline (every other pixel) around the given rectangle."""
	x0, y0, x1, y1 = box
	x0 += inset
	y0 += inset
	x1 -= inset
	y1 -= inset
	for x in range(x0, x1):
		if (x - x0) % 2 == 0:
			img.putpixel((x, y0), (STITCH, 255))
			img.putpixel((x, y1 - 1), (STITCH, 255))
	for y in range(y0, y1):
		if (y - y0) % 2 == 0:
			img.putpixel((x0, y), (STITCH, 255))
			img.putpixel((x1 - 1, y), (STITCH, 255))


def check_no_overlap(img):
	"""Nothing stitched may lie inside a slot's 16x16: that is what "bad overlap of textures" was."""
	for row in range(6):
		for col in range(9):
			x0, y0, x1, y1 = item_rect(row, col)
			for y in range(y0, y1):
				for x in range(x0, x1):
					if img.getpixel((x, y))[0] >= STITCH:
						raise SystemExit(f"stitching at ({x}, {y}) is inside the item area of slot ({row}, {col})")


def main():
	img = Image.new("LA", (WIDTH, HEIGHT), (CLOTH, 255))

	# base cloth, everywhere, with subtle luminance noise
	for y in range(HEIGHT):
		for x in range(WIDTH):
			n = random.randint(-CLOTH_NOISE, CLOTH_NOISE)
			v = max(0, min(254, CLOTH + n))  # stays under STITCH so noise is never mistaken for stitching
			img.putpixel((x, y), (v, 255))

	# tab row (row 0) and action row (row 5): one stitched box per cell, so every button reads as a
	# button of its own and no line of it crosses the icon inside.
	for col in range(9):
		stitch_rect(img, cell_rect(0, col))
		stitch_rect(img, cell_rect(5, col))

	# patch panel: rows 1-4, cols 0-4 -- the cream canvas pocket, one box round the block
	patch_panel = cell_rect(1, 0, cols=5, rows=4)
	fill(img, patch_panel, PATCH_PANEL)
	stitch_rect(img, patch_panel)

	# preview panel: rows 1-4, cols 5-8
	preview_panel = cell_rect(1, 5, cols=4, rows=4)
	fill(img, preview_panel, PREVIEW_PANEL)
	stitch_rect(img, preview_panel)
	# No painted silhouette here any more: the panel always carries WardrobePreview's rendered paper
	# doll of the player's own ovve, and two figures on top of each other is the overlap we just fixed.

	# outer border, the whole background
	stitch_rect(img, (0, 0, WIDTH, HEIGHT), inset=0)

	check_no_overlap(img)
	img.save("wardrobe_template.png")


if __name__ == "__main__":
	main()
