"""The void anchor's block textures, drawn pixel by pixel: an End-forged artifact.

A purpur frame (lit from the top left) around a recessed obsidian panel. The sides carry four
glowing slots, lit one per charge; the top holds an ender-eye-like iris in a socket, closed when the
anchor is empty and, when charged, an animation that glances around and blinks. The bottom is end
stone bricks. gen_textures.py calls draw_all(); nothing here is random except the obsidian's grain,
which uses a fixed seed.
"""
import json
import random
from pathlib import Path

from PIL import Image

PURPUR = {"hi": (214, 176, 214), "lt": (190, 146, 190), "base": (166, 120, 166), "dk": (132, 90, 134), "dkr": (104, 68, 108)}
OBSIDIAN = [(16, 11, 26), (22, 15, 36), (28, 19, 45), (35, 24, 55)]
VEIN = (88, 40, 140)
RECESS_SHADOW = (8, 5, 14)
RECESS_LIGHT = (58, 40, 84)
GLOW = {"core": (255, 150, 255), "hot": (228, 92, 255), "mid": (160, 48, 226), "halo": (96, 30, 150)}
SLOT_OFF = {"core": (12, 8, 20), "rim": (44, 30, 62)}
END_STONE = {"lt": (234, 236, 186), "base": (219, 221, 160), "dk": (192, 192, 132), "mortar": (152, 150, 104)}
IRIS = {"bright": (150, 255, 222), "lt": (82, 214, 182), "base": (40, 160, 140), "dk": (18, 96, 92)}
SCLERA = {"lt": (70, 34, 92), "base": (46, 18, 62), "dk": (28, 10, 40)}
PUPIL = (6, 10, 14)
LID = {"lt": (92, 60, 112), "base": (64, 38, 82), "dk": (40, 22, 54), "seam": (12, 6, 18)}


def put(img, x, y, c):
	if 0 <= x < img.width and 0 <= y < img.height:
		img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def frame(img):
	"""A two-pixel purpur frame, lit from the top left, with grooves at the corners."""
	n = img.width
	for y in range(n):
		for x in range(n):
			if 2 <= x < n - 2 and 2 <= y < n - 2:
				continue
			outer = x == 0 or y == 0 or x == n - 1 or y == n - 1
			if x == 0 or y == 0:
				c = PURPUR["hi"]
			elif x == n - 1 or y == n - 1:
				c = PURPUR["dkr"]
			elif x == 1 or y == 1:
				c = PURPUR["lt"]
			else:
				c = PURPUR["dk"]
			if not outer and (x in (1, n - 2)) and (y in (1, n - 2)):
				c = PURPUR["base"]
			put(img, x, y, c)
	# grooves that split the frame into four purpur bars, as purpur pillars meet
	for d in (5, 10):
		put(img, d, 0, PURPUR["lt"]); put(img, d, 1, PURPUR["base"])
		put(img, d, n - 1, PURPUR["dkr"]); put(img, d, n - 2, PURPUR["dkr"])
		put(img, 0, d, PURPUR["lt"]); put(img, 1, d, PURPUR["base"])
		put(img, n - 1, d, PURPUR["dkr"]); put(img, n - 2, d, PURPUR["dkr"])


def panel(img, seed):
	"""The recessed obsidian panel: shadow under the top-left lip, light on the bottom-right one, fine grain, a vein or two."""
	rng = random.Random(seed)
	n = img.width
	for y in range(2, n - 2):
		for x in range(2, n - 2):
			put(img, x, y, rng.choice(OBSIDIAN))
	for i in range(2, n - 2):
		put(img, i, 2, RECESS_SHADOW); put(img, 2, i, RECESS_SHADOW)
		put(img, i, n - 3, RECESS_LIGHT); put(img, n - 3, i, RECESS_LIGHT)
	put(img, 2, n - 3, RECESS_SHADOW); put(img, n - 3, 2, RECESS_SHADOW)
	return rng


def slots(img, lit):
	"""Four vertical slots, three pixels apart. Lit ones are glowing tubes, white-hot in the middle and
	violet at the ends, each on its own; unlit ones are visible recesses."""
	columns = (3, 6, 9, 12)
	top, bottom = 4, 11
	for i, x in enumerate(columns):
		on = i < lit
		for y in range(top, bottom + 1):
			end = y in (top, bottom)
			if on:
				c = GLOW["mid"] if end else (GLOW["core"] if 6 <= y <= 9 else GLOW["hot"])
				put(img, x, y, c)
			else:
				# a recess: a dark core with its lit lip on the right, as the light comes from the top left
				put(img, x, y, SLOT_OFF["core"])
				if x + 1 <= 12:
					put(img, x + 1, y, SLOT_OFF["rim"])
		if not on:
			put(img, x, bottom + 1, SLOT_OFF["rim"])
			if x + 1 <= 12:
				put(img, x + 1, bottom + 1, SLOT_OFF["rim"])


def side(charge):
	img = Image.new("RGBA", (16, 16))
	frame(img)
	panel(img, 11)
	slots(img, charge)
	return img


def eye_frame(look=(0, 0), lid=0.0):
	"""The top: frame, panel, a round socket and the eye in it. look moves the iris; lid 0..1 closes it."""
	img = Image.new("RGBA", (16, 16))
	frame(img)
	panel(img, 23)
	cx = cy = 7.5
	# the socket: a ring of purpur-lit obsidian, darker toward the bottom right
	for y in range(16):
		for x in range(16):
			d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
			if 4.6 <= d < 5.6:
				shade = (x - cx + y - cy) / 10
				put(img, x, y, RECESS_LIGHT if shade < -0.15 else ((24, 16, 36) if shade > 0.15 else (40, 28, 60)))
	# the eyeball
	for y in range(16):
		for x in range(16):
			d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
			if d < 4.6:
				shade = (x - cx + y - cy) / 9
				put(img, x, y, SCLERA["lt"] if shade < -0.25 else (SCLERA["dk"] if shade > 0.3 else SCLERA["base"]))
	ix, iy = cx + look[0], cy + look[1]
	for y in range(16):
		for x in range(16):
			d = ((x - ix) ** 2 + (y - iy) ** 2) ** 0.5
			if d < 2.7 and ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 < 4.4:
				c = IRIS["dk"] if d > 2.1 else (IRIS["lt"] if (x - ix) + (y - iy) < -0.5 else IRIS["base"])
				put(img, x, y, c)
	# a slit pupil, and the catch-light
	px, py = round(ix - 0.5), round(iy - 0.5)
	for dy in (-1, 0, 1):
		put(img, px, py + dy, PUPIL); put(img, px + 1, py + dy, PUPIL)
	put(img, px - 1, py - 1, IRIS["bright"])
	if lid > 0:
		# the lids close from top and bottom toward a seam that curves down in the middle
		def seam(x):
			return 8 if abs(x - cx) < 3 else 7
		rows = round(4.6 * lid)
		for y in range(16):
			for x in range(16):
				d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
				if d >= 4.6:
					continue
				closed = lid >= 1
				upper = y < seam(x) if closed else y < cy - 4.6 + rows + 0.5
				lower = y > seam(x) if closed else y > cy + 4.6 - rows - 0.5
				if upper:
					put(img, x, y, LID["lt"] if (y <= 4 or (x - cx + y - cy) < -3) else LID["base"])
				elif lower:
					put(img, x, y, LID["dk"])
		if lid >= 1:
			for x in range(16):
				if abs(x - cx) < 4.4:
					put(img, x, seam(x), LID["seam"])
	return img


# The open eye's animation: (look, lid, ticks). It rests, glances, blinks.
EYE = [((0, 0), 0.0, 60), ((-1.4, 0), 0.0, 24), ((0, 0), 0.0, 30), ((1.4, 0.4), 0.0, 24), ((0, 0), 0.0, 40),
       ((0, 0), 0.5, 2), ((0, 0), 1.0, 3), ((0, 0), 0.5, 2), ((0, -1.2), 0.0, 20), ((0, 0), 0.0, 30)]


def top_open():
	strip = Image.new("RGBA", (16, 16 * len(EYE)))
	for i, (look, lid, _) in enumerate(EYE):
		strip.alpha_composite(eye_frame(look, lid), (0, 16 * i))
	meta = {"animation": {"frames": [{"index": i, "time": t} for i, (_, _, t) in enumerate(EYE)]}}
	return strip, meta


def top_closed():
	return eye_frame((0, 0), 1.0)


def bottom():
	img = Image.new("RGBA", (16, 16))
	rng = random.Random(5)
	for y in range(16):
		row = y // 4
		offset = 4 if row % 2 else 0
		for x in range(16):
			if y % 4 == 3 or (x + offset) % 8 == 7:
				c = END_STONE["mortar"]
			elif y % 4 == 0 or (x + offset) % 8 == 0:
				c = END_STONE["lt"]
			elif y % 4 == 2:
				c = END_STONE["dk"]
			else:
				c = rng.choice((END_STONE["base"], END_STONE["base"], END_STONE["lt"]))
			put(img, x, y, c)
	return img


def draw_all(block_dir: Path):
	block_dir.mkdir(parents=True, exist_ok=True)
	for old in block_dir.glob("void_anchor_top*.png*"):
		old.unlink()
	for charge in range(5):
		side(charge).save(block_dir / f"void_anchor_side_{charge}.png")
	strip, meta = top_open()
	strip.save(block_dir / "void_anchor_top_open.png")
	(block_dir / "void_anchor_top_open.png.mcmeta").write_text(json.dumps(meta, indent="\t") + "\n")
	top_closed().save(block_dir / "void_anchor_top_closed.png")
	bottom().save(block_dir / "void_anchor_bottom.png")
