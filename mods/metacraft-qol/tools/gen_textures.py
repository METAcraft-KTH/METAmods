"""Draws the void anchor's placeholder textures. Run from the module: python3 tools/gen_textures.py

Everything is drawn from fixed seeds, so a rerun gives the same files. Replace the PNGs with real
art whenever; nothing else depends on this script.
"""
import json
import random
from pathlib import Path

from PIL import Image

import anchor_art

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/metacraft/textures"
BLOCK = ROOT / "block"
ITEM = ROOT / "item"
ASSETS = ROOT.parent
RIFT_VARIANTS = 8               # keep in step with Rift.VARIANTS
RAY_VARIANTS = 4                # keep in step with Rift.RAY_VARIANTS
RIFT_TINT = -65795              # #FEFEFD, the marker the item shader looks for; Rift dyes glowing cracks #FEFEFC
CORE_TINT = -65797              # #FEFEFB, the shatter rift's core

OBSIDIAN = [(20, 10, 34), (28, 14, 46), (36, 18, 60), (46, 22, 76)]
CRYING = [(120, 40, 200), (150, 70, 230)]
RIM = [(92, 56, 120), (110, 70, 140), (78, 46, 104)]
VOID = (6, 2, 12)
PIP_OFF = (40, 24, 56)
PIP_ON = [(200, 130, 255), (240, 200, 255)]


def rift(seed, frames=4, size=64, ray=False):
	"""The rift's sprite: a crack in space. A jagged main split runs along the sprite's x axis (the
	display stretches along x first, then widens it), widest in the middle, with hairline branches
	off it. Its alpha is the map the shader reads: 1 inside the split (the view into the void),
	about 0.95 to 0.55 on the edges and along the hairlines (the glowing cracks), under 0.45 in the
	halo. Its colours are the painted crack a client without the shader sees. The frames only
	flicker the glow; the cracks stay put. Each seed gives a differently shaped crack: how it
	wanders, how wide it gapes, how many branches it throws off and where.

	A ray (the shatter rift's cracks round its core) starts at the sprite's left edge, widest there
	where it meets the core, and runs out to a point, its branches all leaning outward.
	"""
	import math
	rng = random.Random(seed)

	def jagged(p0, p1, depth, rough):
		"""A line from p0 to p1, split and nudged sideways `depth` times."""
		pts = [p0, p1]
		for _ in range(depth):
			out = [pts[0]]
			for q0, q1 in zip(pts, pts[1:]):
				mx, my = (q0[0] + q1[0]) / 2, (q0[1] + q1[1]) / 2
				dx, dy = q1[0] - q0[0], q1[1] - q0[1]
				length = math.hypot(dx, dy)
				nudge = rng.uniform(-rough, rough) * length
				out += [(mx - dy / length * nudge, my + dx / length * nudge), q1]
			pts = out
		return pts

	# draw cracks until one fits: the split keeps to the middle band, nothing runs off the sprite
	while True:
		if ray:
			main = jagged((-1.0, 0.0), (0.94, rng.uniform(-0.25, 0.25)), 4, rng.uniform(0.3, 0.42))
		else:
			main = jagged((-0.94, rng.uniform(-0.18, 0.18)), (0.94, rng.uniform(-0.18, 0.18)), 4, rng.uniform(0.34, 0.5))
		gape = rng.uniform(0.06, 0.09)
		count = rng.randint(6, 12)
		branches = []
		for k in range(count):
			# spread the branches along the split, mostly alternating sides, leaning outward
			first = len(main) // 5 if ray else 2
			i = first + (k * (len(main) - first - 2)) // count + rng.randrange(0, 2)
			ox, oy = main[min(i, len(main) - 3)]
			side = (1 if k % 2 else -1) * (-1 if rng.random() < 0.2 else 1)
			lean = 1 if ray or ox > 0 else -1
			angle = rng.uniform(0.45, 1.15)
			length = rng.uniform(0.22, 0.55)
			end = (ox + math.cos(angle) * length * lean, oy + math.sin(angle) * length * side)
			branch = jagged((ox, oy), end, 3, 0.45)
			branches.append(branch)
			if rng.random() < 0.6:
				j = len(branch) // 2
				bx, by = branch[j]
				fork = rng.uniform(0.4, 0.8) * side
				twig_len = length * rng.uniform(0.35, 0.6)
				twig = (bx + math.cos(angle + fork) * twig_len * lean, by + math.sin(angle + fork) * twig_len * side)
				branches.append(jagged((bx, by), twig, 2, 0.45))
		if all(abs(y) < 0.4 for _, y in main) and all(abs(x) < 0.97 and abs(y) < 0.88 for b in branches for x, y in b):
			break

	def nearest(pts, x, y):
		"""Distance to a polyline, and how far along it (0..1) the nearest point is."""
		best, along, run = 9.0, 0.0, 0.0
		total = sum(math.hypot(q1[0] - q0[0], q1[1] - q0[1]) for q0, q1 in zip(pts, pts[1:]))
		for q0, q1 in zip(pts, pts[1:]):
			dx, dy = q1[0] - q0[0], q1[1] - q0[1]
			seg = math.hypot(dx, dy)
			t = max(0.0, min(1.0, ((x - q0[0]) * dx + (y - q0[1]) * dy) / (seg * seg)))
			d = math.hypot(x - q0[0] - t * dx, y - q0[1] - t * dy)
			if d < best:
				best, along = d, (run + t * seg) / total
			run += seg
		return best, along

	img = Image.new("RGBA", (size, size * frames))
	px = 2.0 / size
	stars = [(rng.uniform(-0.5, 0.5), rng.uniform(-0.06, 0.06)) for _ in range(6)]
	for y in range(size):
		for x in range(size):
			u = (x + 0.5) / size * 2 - 1
			v = (y + 0.5) / size * 2 - 1
			d, along = nearest(main, u, v)
			if ray:
				width = gape * 1.3 * max(0.0, 1 - along) ** 0.9              # widest at the core, out to a point
			else:
				width = gape * max(0.0, math.sin(math.pi * along)) ** 0.7     # widest mid-way, closed at the tips
			edge = d - width
			hair = min((nearest(b, u, v)[0] for b in branches), default=9.0)
			if edge < 0:
				a = 1.0
			elif edge < 1.6 * px:
				a = 0.95 - edge / (1.6 * px) * 0.4
			elif hair < 0.7 * px:
				a = 0.82
			else:
				glow = min(edge, hair)
				a = 0.45 * math.exp(-glow / 0.05) if glow < 0.25 else 0.0
				if a < 0.02:
					continue
			for f in range(frames):
				flicker = 1.0 + 0.12 * math.sin(f * 1.9 + u * 7)
				if a >= 0.999:
					c = (10, 4, 24)
					for sx, sy in stars:
						if (u - sx) ** 2 + (v - sy) ** 2 < 0.0009:
							c = (210, 190, 255)
				elif a > 0.5:
					c = (255, int(min(255, 200 * flicker)), 255)
				else:
					t = min(1.0, a / 0.45)
					c = (int(150 + 60 * t), int(60 + 40 * t), int(220 + 30 * t))
				alpha = max(1, min(255, int(round(a * 255 * (flicker if a < 0.5 else 1.0)))))
				img.putpixel((x, f * size + y), c + (alpha,))
	return img


def rift_core(size=32):
	"""The shatter rift's core: a white-hot point in a violet glow. The shader draws its own (a
	swirling vortex); this is what a client without it sees."""
	import math
	img = Image.new("RGBA", (size, size))
	for y in range(size):
		for x in range(size):
			r = math.hypot((x + 0.5) / size * 2 - 1, (y + 0.5) / size * 2 - 1)
			hot = math.exp(-(r / 0.16) ** 2)
			glow = math.exp(-r / 0.22) * max(0.0, 1 - r)
			a = min(1.0, hot + glow)
			if a < 0.02:
				continue
			c = tuple(int(lo + (255 - lo) * hot) for lo in (190, 90, 255))
			img.putpixel((x, y), c + (int(a * 255),))
	return img


def flash():
	"""The white-out on teleporting: one wide white glyph, shown as a title. The font scales it up
	(height 512) until it covers any screen."""
	return Image.new("RGBA", (128, 16), (255, 255, 255, 255))


MUFFLER_OUT=(38,22,30)
MUFFLER_CU={"hi":(240,170,120),"lt":(214,128,88),"base":(178,92,64),"dk":(128,60,44),"dkr":(88,40,32)}
MUFFLER_WOOL={"hi":(246,243,238),"lt":(226,221,214),"base":(198,192,184),"dk":(160,152,146)}
MUFFLER_AM={"hi":(244,220,255),"lt":(206,150,255),"base":(160,96,228),"dk":(104,58,170),"dkr":(70,36,120)}
MUFFLER_STR=(232,228,214); MUFFLER_STRD=(170,160,140)


def muffler():
	"""A tinkerer's hush gadget: a wool-padded copper cup with an amethyst set in it, on a
	string-wrapped copper handle, humming a little. (Its recipe: wool, an amethyst shard, string.)"""
	img=Image.new("RGBA",(16,16))
	def p(x,y,c):
		if 0<=x<16 and 0<=y<16: img.putpixel((x,y),c+(255,))
	# handle, bottom left to the cup
	for i in range(6):
		x,y=1+i,14-i
		p(x-1,y,MUFFLER_OUT); p(x+1,y+1,MUFFLER_OUT)
	for i in range(6):
		x,y=1+i,14-i
		p(x,y,MUFFLER_CU["base"]); p(x+1,y,MUFFLER_CU["dk"]); p(x,y-1,MUFFLER_CU["lt"])
	p(0,15,MUFFLER_OUT); p(1,15,MUFFLER_OUT); p(0,14,MUFFLER_OUT)
	# string wrapped round the grip
	for (x,y) in [(2,12),(3,11),(4,10)]:
		p(x,y,MUFFLER_STR); p(x+1,y,MUFFLER_STRD)
	# the cup: a copper rim round a wool pad
	cx,cy=10.5,5.5
	for y in range(16):
		for x in range(16):
			d=((x-cx)**2+(y-cy)**2)**0.5; shade=(x-cx)+(y-cy)
			if d<3.3:
				p(x,y,MUFFLER_WOOL["hi"] if shade<-2 else MUFFLER_WOOL["lt"] if shade<0.5 else MUFFLER_WOOL["base"] if shade<2.5 else MUFFLER_WOOL["dk"])
			elif d<4.3:
				p(x,y,MUFFLER_CU["hi"] if shade<-2.5 else MUFFLER_CU["lt"] if shade<0 else MUFFLER_CU["base"] if shade<2.5 else MUFFLER_CU["dk"])
			elif d<5.0:
				p(x,y,MUFFLER_OUT)
	# two gear teeth on the rim
	for (x,y,c) in [(15,4,MUFFLER_CU["base"]),(15,5,MUFFLER_CU["dk"]),(13,0,MUFFLER_CU["lt"]),(14,0,MUFFLER_CU["base"])]:
		p(x,y,c)
	# a coil collar where handle meets cup
	for (x,y,c) in [(6,9,MUFFLER_CU["hi"]),(7,9,MUFFLER_CU["dk"]),(7,8,MUFFLER_CU["base"]),(6,10,MUFFLER_CU["dkr"])]:
		p(x,y,c)
	# the amethyst, set in the pad, held by two copper prongs
	for (x,y,c) in [(10,3,MUFFLER_AM["lt"]),(10,4,MUFFLER_AM["hi"]),(11,4,MUFFLER_AM["lt"]),(10,5,MUFFLER_AM["lt"]),(11,5,MUFFLER_AM["base"]),(10,6,MUFFLER_AM["base"]),(11,6,MUFFLER_AM["dk"]),(10,7,MUFFLER_AM["dk"]),(11,7,MUFFLER_AM["dkr"])]:
		p(x,y,c)
	for (x,y,c) in [(9,7,MUFFLER_CU["base"]),(12,7,MUFFLER_CU["dk"]),(9,6,MUFFLER_CU["lt"]),(12,6,MUFFLER_CU["base"])]:
		p(x,y,c)
	# a hum of magic off the cup
	for (x,y,c) in [(15,1,MUFFLER_AM["lt"]),(14,2,MUFFLER_AM["hi"]),(15,11,MUFFLER_AM["lt"])]:
		p(x,y,c)
	return img


def write_json(path, data):
	path.write_text(json.dumps(data, indent="\t") + "\n")


def main():
	anchor_art.draw_all(BLOCK)
	ITEM.mkdir(parents=True, exist_ok=True)
	for old in list(ITEM.glob("rift*.png*")) + list((ASSETS / "models/item").glob("rift*.json")) + list((ASSETS / "items").glob("rift*.json")):
		old.unlink()
	sprites = [(f"rift_{i}", rift(53 + 101 * i)) for i in range(RIFT_VARIANTS)]
	sprites += [(f"rift_ray_{i}", rift(907 + 89 * i, ray=True)) for i in range(RAY_VARIANTS)]
	for name, image in sprites:
		image.save(ITEM / f"{name}.png")
		write_json(ITEM / f"{name}.png.mcmeta", {"animation": {"frametime": 3}})
		write_json(ASSETS / "models/item" / f"{name}.json", {
			"textures": {"rift": f"metacraft:item/{name}", "particle": f"metacraft:item/{name}"},
			"elements": [{"from": [0, 8, 0], "to": [16, 8, 16], "faces": {
				"up": {"uv": [0, 0, 16, 16], "texture": "#rift", "tintindex": 0},
				"down": {"uv": [0, 0, 16, 16], "texture": "#rift", "tintindex": 0}}}]})
		write_json(ASSETS / "items" / f"{name}.json", {"model": {
			"type": "minecraft:model", "model": f"metacraft:item/{name}",
			"tints": [{"type": "minecraft:dye", "default": RIFT_TINT}]}})
	rift_core().save(ITEM / "rift_core.png")
	write_json(ASSETS / "models/item/rift_core.json", {
		"textures": {"core": "metacraft:item/rift_core", "particle": "metacraft:item/rift_core"},
		"elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "faces": {
			"north": {"uv": [0, 0, 16, 16], "texture": "#core", "tintindex": 0},
			"south": {"uv": [0, 0, 16, 16], "texture": "#core", "tintindex": 0}}}]})
	write_json(ASSETS / "items/rift_core.json", {"model": {
		"type": "minecraft:model", "model": "metacraft:item/rift_core",
		"tints": [{"type": "minecraft:constant", "value": CORE_TINT}]}})
	(ROOT / "font").mkdir(exist_ok=True)
	flash().save(ROOT / "font/flash.png")
	(ASSETS / "font").mkdir(exist_ok=True)
	# a title is drawn 10 px above the screen's middle at 4x; ascent puts the glyph's middle there
	write_json(ASSETS / "font/flash.json", {"providers": [
		{"type": "bitmap", "file": "metacraft:font/flash.png", "ascent": 253, "height": 512, "chars": ["\ue000"]}]})
	muffler().save(ITEM / "muffler.png")

if __name__ == "__main__":
	main()
