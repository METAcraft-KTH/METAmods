#!/usr/bin/env python3
"""Generate the four screen-ink overlay textures.

    python3 mods/rivals-paint/tools/ink_overlays.py

Writes `src/main/resources/assets/rivals-paint/textures/effect/ink_1.png` … `ink_4.png`, and a
contact sheet at `/tmp/ink_sheet_splatter.png` (the four states 2x2 over mid grey, upscaled 2x NEAREST,
drawn in DATA's ink the way the shader draws it) so the art can be looked at without starting the game.

That directory is forced: a post chain's texture input names a bare location, and the client resolves
it as `textures/effect/<path>.png`, so `end_of_frame.json` asks for `rivals-paint:ink_1`.

These are PLACEHOLDERS, and they are checked in as ordinary resources so an artist can paint over
them. The format they have to keep is in `textures/effect/README.md`, and in short:

    320 x 180 RGBA.  ALPHA is coverage and is 0 or 255, never anything between — the shader draws a
    hard pixel-art edge and a soft alpha would read as a blurred one.  RGB is a greyscale SHADING map,
    not a colour: dark is shadow, mid is the base, light is a highlight.  The shader maps that
    luminance to four tones of the team's own colour, so an overlay is the same drawing in either
    team's ink.

What this script draws is **paint splatter**, from a reference the user sent of a wall twenty minutes
after a paintball fight: spiky splats, dots thrown everywhere between them, and long droopy drips.

* A splat's silhouette is a polar radius profile, not a circle:
  `r(θ) = R · (1 + Σ aᵢ · max(0, cos(kᵢθ + φᵢ))^pᵢ) · (1 − notch)`. Three to five harmonics of different
  frequency (5..13), with sharp exponents (up to 30) for the thin spikes and gentle ones (2..4) for the
  broad bumps, plus a slow wobble so the body is not round underneath. The notch term is a harmonic
  that bites back INTO the body, which is what makes the rim read as torn rather than as a flower.
* Satellite droplets: every splat throws dots outward, thickest near the body and thinning with
  distance, plus a few single-texel specks further out. The reference is freckled everywhere.
* Drips run straight DOWN from the bottom of a splat, long and thin and tapering, each ending in a
  fatter bead. They are the most recognisable thing in the reference and they are what "droopy" means.
* Small splats of their own — R 6..14 texels — are sprinkled through the ring outside the clear middle,
  so the screen is not four big shapes and nothing else.

The four states are **cumulative**: one deterministic list of splats, each tagged with the state it
first appears in, every splat already on screen growing by {@code GROWTH} per later state, and every
drip getting {@code DRIP_GROWTH} longer per later state — paint runs as time passes, which is the whole
of "twenty minutes after". A drip's earlier lengths are drawn under its current one, so a later state
contains every texel of an earlier one; the script asserts that texel for texel.

Shading is the four bands the shader steps on and nothing else: a one-texel outline along every alpha
edge, the body in BASE, a lighter island inside it shaped like its own core, and a small highlight glint
at the top-left of each big body and on each drip bead. No gradients, no speckle: the hard alpha edge
and the four flat tones are the whole of the pixel-art look.

Stdlib plus Pillow, no anti-aliasing (the 320x180 grid is the pixel art), and deterministic: every
random-looking number is a hash of a fixed seed, so re-running produces the same files byte for byte.
"""
import math
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "src/main/resources/assets/rivals-paint/textures/effect")
SHEET = "/tmp/ink_sheet_splatter.png"

WIDTH = 320
HEIGHT = 180
STATES = 4

# How much a splat already on screen grows per later state, and how much longer its drips run. Paint
# spreads a little and runs a lot: the drips lengthening is what reads as time passing.
GROWTH = 1.22
DRIP_GROWTH = 1.4
# The clear island in the middle, as a fraction of the height, per state. It only ever shrinks, which is
# what lets a later state contain every texel of an earlier one.
CLEAR = [0.50, 0.44, 0.38, 0.34]
# Roughly how much of the screen each state covers. Spiky shapes are airier than round ones, so these
# are lower than a solid-blob drawing's would be. Asserted with a wide tolerance: the numbers only catch
# a state that has stopped building up.
WANT = [0.12, 0.26, 0.42, 0.60]
TOLERANCE = 0.06

# Luminances, which the shader turns into four tones of the team colour. These sit inside its bands:
# < 0.3 shadow, < 0.6 base, < 0.85 light, else highlight.
SHADOW = 0.16
BASE = 0.45
BAND = 0.72
HIGHLIGHT = 0.93

# The splats: (first state, x as a fraction of the width, y as a fraction of the height, radius as a
# fraction of the height, seed). Screen coordinates, so y grows downward. The big ones sit on or just
# past the border with their spikes reaching inward, because paint arrives from outside the frame; the
# small ones are scattered through the ring between the border and the clear middle.
BIG = [
	(1, 0.04, 0.06, 0.146, 11),
	(1, 0.93, 0.05, 0.134, 12),
	(1, 0.10, 0.95, 0.134, 13),
	(1, 0.88, 0.96, 0.140, 14),
	(1, 0.50, -0.04, 0.112, 15),
	(2, -0.02, 0.48, 0.123, 21),
	(2, 1.02, 0.55, 0.123, 22),
	(2, 0.33, 1.03, 0.112, 23),
	(2, 0.72, -0.05, 0.118, 24),
	(3, 0.20, -0.03, 0.106, 31),
	(3, 1.01, 0.18, 0.112, 32),
	(3, 0.62, 1.04, 0.118, 33),
	(3, -0.02, 0.82, 0.106, 34),
	(4, 0.86, 1.02, 0.112, 41),
	(4, -0.03, 0.16, 0.106, 42),
	(4, 1.03, 0.86, 0.106, 43),
	(4, 0.40, -0.04, 0.101, 44),
]
# The small ones, in the same shape of list. R is in texels rather than a fraction: these are 6..14
# texels across whatever the screen, because a small splat is a small splat.
SMALL = [
	(1, 0.22, 0.20, 8.5, 51),
	(1, 0.76, 0.24, 6.6, 52),
	(2, 0.30, 0.78, 10.4, 53),
	(2, 0.64, 0.13, 7.6, 54),
	(2, 0.12, 0.60, 8.5, 55),
	(3, 0.86, 0.68, 11.4, 56),
	(3, 0.45, 0.88, 7.6, 57),
	(3, 0.18, 0.38, 6.6, 58),
	(4, 0.80, 0.42, 9.5, 59),
	(4, 0.52, 0.18, 8.5, 60),
	(4, 0.28, 0.62, 7.6, 61),
	(4, 0.70, 0.82, 10.4, 62),
]


def noise(seed, i):
	"""A stable value in 0..1 from two integers. sin-hash, so it needs no library and no seed state."""
	x = math.sin(i * 12.9898 + seed * 78.233) * 43758.5453
	return x - math.floor(x)


def span(seed, i, low, high):
	"""A stable value in low..high."""
	return low + (high - low) * noise(seed, i)


class Splat:
	"""A thrown splat: a spiky silhouette, the dots it threw, and the drips running off the bottom of it.

	The silhouette is polar. A circle of radius R has lobes added to it — each harmonic contributes
	`a·max(0, cos(kθ + φ))^p`, which is a row of k bumps around the rim, and the exponent decides what
	kind: 2..4 spreads the bump into a broad swell, 20..30 pinches it into a thin spike. A few of each,
	at different frequencies and phases, is what a splat's outline is. The notch harmonic subtracts
	instead, biting into the body between the lobes, which is what stops the rim reading as a flower.
	"""

	def __init__(self, cx, cy, r, seed, drips=True, base=None, soft=False):
		self.cx = cx
		self.cy = cy
		self.r = r
		# What the splat measured when it first landed. The dots it threw and the drips running off it
		# are pinned to THIS and not to the grown radius: a dot that moved outward as the splat spread
		# would uncover the texel it used to be on, and every state has to contain the one before it.
		self.base = r if base is None else base
		self.seed = seed
		self.terms = []
		if not soft:
			# The teeth: two or three rows of short sharp points round the rim. Short is the whole
			# point — an amplitude near one and a body this size is a starburst, which is what the
			# first attempt at this drew.
			for i in range(2 + int(2 * noise(seed, 1))):
				self.terms.append((span(seed, 10 + i, 7.0, 13.0),
						span(seed, 20 + i, 0.0, math.tau),
						span(seed, 30 + i, 0.10, 0.26),
						span(seed, 40 + i, 10.0, 30.0)))
			# And two or three long thin tongues, which is what a splat has instead of teeth all round:
			# a low frequency and a sharp exponent is a few narrow spits of paint thrown further.
			self.terms.append((span(seed, 5, 2.0, 5.0),
					span(seed, 6, 0.0, math.tau),
					span(seed, 7, 0.38, 0.70),
					span(seed, 8, 18.0, 40.0)))
		for i in range(2):
			self.terms.append((span(seed, 50 + i, 3.0, 7.0),
					span(seed, 60 + i, 0.0, math.tau),
					span(seed, 70 + i, 0.06, 0.15),
					span(seed, 80 + i, 2.0, 4.0)))
		# The slow wobble, so the body under the lobes is not a circle either.
		self.terms.append((2.0, span(seed, 90, 0.0, math.tau), 0.08, 2.0))
		self.notch = (span(seed, 91, 4.0, 9.0), span(seed, 92, 0.0, math.tau),
				span(seed, 93, 0.05, 0.12) if not soft else 0.06)
		self.reach = self.r * (1.0 + sum(term[2] for term in self.terms))
		self.drips = drips

	def radius(self, angle):
		total = 0.0
		for k, phase, amplitude, sharp in self.terms:
			lobe = math.cos(k * angle + phase)
			if lobe > 0.0:
				total += amplitude * lobe ** sharp
		k, phase, amplitude = self.notch
		bite = math.cos(k * angle + phase)
		return self.r * (1.0 + total) * (1.0 - (amplitude * bite ** 4 if bite > 0.0 else 0.0))

	def contains(self, px, py):
		dx = px - self.cx
		dy = py - self.cy
		d2 = dx * dx + dy * dy
		if d2 > self.reach * self.reach:
			return False
		if d2 <= self.r * self.r * 0.64:
			return True
		at = self.radius(math.atan2(dy, dx))
		return d2 <= at * at

	def bottom(self, x):
		"""The lowest covered y in the column {@code x}, or None if the splat does not reach it."""
		low = None
		y = int(self.cy)
		while y <= int(self.cy + self.reach) + 1:
			if self.contains(x + 0.5, y + 0.5):
				low = y
			y += 1
		return low


def dots(splat, state):
	"""The satellite droplets: (x, y, radius) thrown outward, thickest near the body.

	Fixed to the splat's FIRST size, never the grown one — a dot that moved outward as the splat grew
	would uncover the texel it used to be on, and every state has to contain the one before it.
	"""
	out = []
	count = 22 + int(22 * noise(splat.seed, 2))
	for i in range(count):
		angle = span(splat.seed, 100 + i, 0.0, math.tau)
		# Distance biased inward: the square of a 0..1 hash piles the dots up near the rim and thins
		# them out with distance, which is how paint lands.
		t = noise(splat.seed, 200 + i) ** 0.6
		away = splat.base * (0.85 + 1.05 * t)
		size = 1.0 + 3.0 * (1.0 - t) * noise(splat.seed, 300 + i)
		out.append((splat.cx + math.cos(angle) * away, splat.cy + math.sin(angle) * away, size))
	# And a few specks further out still, one texel each: the freckles between the splats.
	for i in range(8 + int(8 * noise(splat.seed, 3))):
		angle = span(splat.seed, 400 + i, 0.0, math.tau)
		away = splat.base * span(splat.seed, 500 + i, 1.3, 2.6)
		out.append((splat.cx + math.cos(angle) * away, splat.cy + math.sin(angle) * away, 1.0))
	return out


class Drip:
	"""A run of paint straight down from the bottom of a splat, tapering into a fatter bead."""

	def __init__(self, x, top, width, length):
		self.x = x
		self.top = top
		self.width = width
		self.length = length

	def half(self, y):
		"""Half the run's width at a height down it, tapering to a little over half what it started."""
		t = (y - self.top) / self.length
		return self.width * (1.0 - 0.42 * t)

	def bead(self):
		return self.x, self.top + self.length, self.width * 1.15


def drips(splat, state, first):
	"""A splat's drips at {@code state}, longest last, with every earlier length drawn under them.

	The earlier lengths are what keeps the states nested: a drip that simply got longer would leave its
	old bead sticking out past the taper of the new run, and the subset assertion would catch it. Drawn
	as a run of them, the extra beads read as swellings in the run, which is what a real drip does.
	"""
	if not splat.drips:
		return []
	out = []
	# Measured off the splat as it first landed, never off the grown one. A drip whose top followed the
	# spreading rim would leave the rows above its new top to the splat — and a splat is spiky, so a
	# column can be covered at a spike tip and bare just above it. Pinning the top keeps every state a
	# superset of the one before, and the grown rim covers the drip's shoulders anyway.
	first_size = Splat(splat.cx, splat.cy, splat.base, splat.seed)
	count = 1 + int(2.9 * noise(splat.seed, 4))
	for i in range(count):
		x = splat.cx + splat.base * span(splat.seed, 600 + i, -0.55, 0.55)
		top = first_size.bottom(int(x))
		if top is None:
			continue
		width = max(1.2, splat.base * span(splat.seed, 700 + i, 0.07, 0.13))
		base = splat.base * span(splat.seed, 800 + i, 1.2, 3.4)
		for grown in range(first, state + 1):
			out.append(Drip(x, top - 1, width, base * DRIP_GROWTH ** (grown - first)))
	return out


def scene(state):
	"""Every splat on screen in {@code state}, with the dots and drips that go with each."""
	out = []
	for first, fx, fy, fr, seed in BIG:
		if state < first:
			continue
		grow = GROWTH ** (state - first)
		splat = Splat(fx * WIDTH, fy * HEIGHT, fr * HEIGHT * grow, seed, base=fr * HEIGHT)
		out.append((splat, first, True))
	for first, fx, fy, r, seed in SMALL:
		if state < first:
			continue
		grow = GROWTH ** (state - first)
		# The little ones drip too, but only the bigger half of them: a six-texel splat with a drip on it
		# is a tadpole.
		splat = Splat(fx * WIDTH, fy * HEIGHT, r * grow, seed, drips=r >= 8.0, base=r)
		out.append((splat, first, False))
	return out


def disc(mask, cx, cy, r):
	for y in range(max(0, int(cy - r)), min(HEIGHT, int(cy + r) + 2)):
		for x in range(max(0, int(cx - r)), min(WIDTH, int(cx + r) + 2)):
			dx = x + 0.5 - cx
			dy = y + 0.5 - cy
			if dx * dx + dy * dy <= r * r:
				mask[y][x] = True


def coverage(state, scene_):
	"""The alpha mask: True where there is ink."""
	mask = [[False] * WIDTH for _ in range(HEIGHT)]
	for splat, first, big in scene_:
		reach = splat.reach
		for y in range(max(0, int(splat.cy - reach)), min(HEIGHT, int(splat.cy + reach) + 2)):
			row = mask[y]
			for x in range(max(0, int(splat.cx - reach)), min(WIDTH, int(splat.cx + reach) + 2)):
				if not row[x] and splat.contains(x + 0.5, y + 0.5):
					row[x] = True
		for x, y, r in dots(splat, state):
			if r <= 1.0:
				if 0 <= int(x) < WIDTH and 0 <= int(y) < HEIGHT:
					mask[int(y)][int(x)] = True
			else:
				disc(mask, x, y, r)
		for drip in drips(splat, state, first):
			for y in range(max(0, int(drip.top)), min(HEIGHT, int(drip.top + drip.length) + 1)):
				half = drip.half(y + 0.5)
				for x in range(max(0, int(drip.x - half)), min(WIDTH, int(drip.x + half) + 2)):
					if abs(x + 0.5 - drip.x) <= half:
						mask[y][x] = True
			bx, by, br = drip.bead()
			disc(mask, bx, by, br)
	# The island the player aims through. Carved after everything, and only ever smaller in a later
	# state, so carving it cannot break the rule that state N contains state N-1.
	clear = CLEAR[state - 1] * HEIGHT
	for y in range(HEIGHT):
		dy = y + 0.5 - HEIGHT / 2.0
		if abs(dy) > clear:
			continue
		row = mask[y]
		for x in range(WIDTH):
			dx = x + 0.5 - WIDTH / 2.0
			if dx * dx + dy * dy < clear * clear:
				row[x] = False
	return mask


def depths(mask):
	"""How many texels inside the ink each covered texel is, by a breadth-first walk from the edge."""
	far = WIDTH + HEIGHT
	out = [[0 if not mask[y][x] else far for x in range(WIDTH)] for y in range(HEIGHT)]
	frontier = []
	for y in range(HEIGHT):
		for x in range(WIDTH):
			if not mask[y][x]:
				continue
			# The texture's own border counts as the inside, so ink running off the screen has no rim:
			# an outline along the border would read as a frame drawn round the whole view.
			for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
				nx, ny = x + ax, y + ay
				if 0 <= nx < WIDTH and 0 <= ny < HEIGHT and not mask[ny][nx]:
					out[y][x] = 1
					frontier.append((x, y))
					break
	head = 0
	while head < len(frontier):
		x, y = frontier[head]
		head += 1
		for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
			nx, ny = x + ax, y + ay
			if not (0 <= nx < WIDTH and 0 <= ny < HEIGHT) or not mask[ny][nx]:
				continue
			if out[ny][nx] > out[y][x] + 1:
				out[ny][nx] = out[y][x] + 1
				frontier.append((nx, ny))
	return out


def shade(mask, depth, scene_, state):
	"""The greyscale map: the four bands the shader steps on, and nothing between them."""
	lum = [[BASE if mask[y][x] else None for x in range(WIDTH)] for y in range(HEIGHT)]
	for splat, first, big in scene_:
		# The lighter island: the splat's own core, shrunk and nudged up-left. Shaped like the splat
		# rather than round, because the reference's inner mark is the same torn shape as the outside.
		core = Splat(splat.cx - splat.r * 0.13, splat.cy - splat.r * 0.13, splat.r * 0.36, splat.seed + 7,
				soft=True)
		reach = core.reach
		for y in range(max(0, int(core.cy - reach)), min(HEIGHT, int(core.cy + reach) + 2)):
			for x in range(max(0, int(core.cx - reach)), min(WIDTH, int(core.cx + reach) + 2)):
				# Off the rim, so the outline and a band of base survive all the way round.
				if mask[y][x] and depth[y][x] >= 4 and core.contains(x + 0.5, y + 0.5):
					lum[y][x] = BAND
		if big:
			# One glint, top-left, where the light is.
			glint = max(1.0, splat.r * 0.07)
			gx = splat.cx - splat.r * 0.40
			gy = splat.cy - splat.r * 0.40
			for y in range(max(0, int(gy - glint)), min(HEIGHT, int(gy + glint) + 2)):
				for x in range(max(0, int(gx - glint)), min(WIDTH, int(gx + glint) + 2)):
					dx, dy = x + 0.5 - gx, y + 0.5 - gy
					if dx * dx + dy * dy <= glint * glint and mask[y][x] and depth[y][x] >= 2:
						lum[y][x] = HIGHLIGHT
		for drip in drips(splat, state, first):
			bx, by, br = drip.bead()
			r = max(1.0, br * 0.38)
			cx, cy = bx - br * 0.25, by - br * 0.25
			for y in range(max(0, int(cy - r)), min(HEIGHT, int(cy + r) + 2)):
				for x in range(max(0, int(cx - r)), min(WIDTH, int(cx + r) + 2)):
					dx, dy = x + 0.5 - cx, y + 0.5 - cy
					if dx * dx + dy * dy <= r * r and mask[y][x] and depth[y][x] >= 2:
						lum[y][x] = HIGHLIGHT
	# The outline last, so it survives every band above: one dark texel along every alpha edge.
	for y in range(HEIGHT):
		for x in range(WIDTH):
			if lum[y][x] is not None and depth[y][x] == 1:
				lum[y][x] = SHADOW
	image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
	pixels = image.load()
	for y in range(HEIGHT):
		for x in range(WIDTH):
			if lum[y][x] is None:
				continue
			value = max(0, min(255, int(round(lum[y][x] * 255))))
			pixels[x, y] = (value, value, value, 255)
	return image


def contact(images):
	"""The four states 2x2 over mid grey, upscaled 2x with no filtering.

	Drawn the way the shader draws it — the four luminance bands become four tones of DATA's ink — so
	the sheet shows what a player sees rather than a grey map that has to be imagined in colour.
	"""
	ink = (0.7412, 0.2157, 0.3294)
	tones = []
	for mix, scale in ((0.0, 0.55), (0.0, 1.0), (0.25, 1.0), (0.6, 1.0)):
		tones.append(tuple(int(round(255 * (channel * scale + (1.0 - channel * scale) * mix))) for channel in ink))
	sheet = Image.new("RGB", (WIDTH * 2, HEIGHT * 2))
	pixels = sheet.load()
	for i, image in enumerate(images):
		source = image.load()
		ox, oy = WIDTH * (i % 2), HEIGHT * (i // 2)
		for y in range(HEIGHT):
			for x in range(WIDTH):
				grey, _, _, alpha = source[x, y]
				if alpha < 128:
					pixels[ox + x, oy + y] = (110, 110, 110)
					continue
				lum = grey / 255.0
				pixels[ox + x, oy + y] = tones[0] if lum < 0.3 else tones[1] if lum < 0.6 \
						else tones[2] if lum < 0.85 else tones[3]
	sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
	sheet.save(SHEET)


def main():
	os.makedirs(OUT, exist_ok=True)
	images = []
	masks = []
	for state in range(1, STATES + 1):
		scene_ = scene(state)
		mask = coverage(state, scene_)
		image = shade(mask, depths(mask), scene_, state)
		image.save(os.path.join(OUT, "ink_%d.png" % state))
		images.append(image)
		masks.append(mask)
		covered = sum(1 for row in mask for wet in row if wet) / float(WIDTH * HEIGHT)
		print("ink_%d.png  %d x %d, %d splats, %.1f%% covered (want %.0f%%)"
				% (state, WIDTH, HEIGHT, len(scene_), 100.0 * covered, 100.0 * WANT[state - 1]))
		assert abs(covered - WANT[state - 1]) <= TOLERANCE, \
			"state %d covers %.1f%%, wanted about %.0f%%" % (state, 100.0 * covered, 100.0 * WANT[state - 1])
		assert not mask[HEIGHT // 2][WIDTH // 2], "state %d has ink in the middle of the screen" % state
		if state > 1:
			for y in range(HEIGHT):
				for x in range(WIDTH):
					assert mask[y][x] or not masks[state - 2][y][x], \
						"state %d lost the texel at %d,%d that state %d had" % (state, x, y, state - 1)
	contact(images)
	print("contact sheet %s" % SHEET)


if __name__ == "__main__":
	main()
