"""Draws the nether rift's edge sprite and writes both rift parts' models and item definitions.
The panel's sprite, rift_panel.png, is the nether panorama: tools/gen_panorama.py writes it.
Also the bomb warning's ring (BurningPatches): its sprite, a flat item model lying face up, and its item definition.
Run from the module: python3 tools/gen_textures.py"""
import json, math, struct, zlib
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/nether_rifts"
BODY_TINT = -66054   # 0xFFFEFDFA: the body marker at stage 1; Rift re-dyes it per stage
EDGE_TINT = -66055   # 0xFFFEFDF9

def png(path, w, h, pixels):
	raw = b"".join(b"\x00" + bytes(c for px in pixels[y * w:(y + 1) * w] for c in px) for y in range(h))
	def chunk(kind, data):
		return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def write_json(path, data):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(data, indent="\t") + "\n")

def edge():
	"""16×16: bright along the middle row, fading out to both sides."""
	px = []
	for y in range(16):
		a = max(0.0, 1.0 - abs(y - 7.5) / 8.0) ** 1.5
		for _ in range(16):
			px.append((255, 140, 240, int(40 + 215 * a)))
	return px

# The faces sit this far either side of the model's middle: just clear of the nether portal's slab (6 to 10 on
# its thin axis), so they never fight it for depth.
CLEAR = 2.16

def quad(texture, inward):
	"""Two faces either side of the middle, each facing out; with inward, each also faces in, so from either side
	one face is seen through the portal (the shader draws the glass's body there)."""
	face = {"uv": [0, 0, 16, 16], "texture": "#rift", "tintindex": 0}
	near, far = 8 + CLEAR, 8 - CLEAR
	south = {"south": face, **({"north": face} if inward else {})}
	north = {"north": face, **({"south": face} if inward else {})}
	return {"textures": {"rift": f"nether_rifts:item/{texture}", "particle": f"nether_rifts:item/{texture}"},
		"elements": [{"from": [0, 0, near], "to": [16, 16, near], "faces": south},
			{"from": [0, 0, far], "to": [16, 16, far], "faces": north}]}

def ring():
	"""32x32: a bright red-orange ring at the edge, a dark red rim inside it and a faint red wash over the middle,
	so the ground under it still shows; edges eased by how much of each pixel is covered."""
	px = []
	for y in range(32):
		for x in range(32):
			d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
			def cover(inner, outer):
				return max(0.0, min(1.0, outer - d + 0.5) - max(0.0, min(1.0, inner - d + 0.5)))
			band, rim, wash = cover(12.5, 15.5), cover(11.5, 12.5), cover(0, 11.5)
			if band > 0:
				px.append((255, 72, 24, int(255 * band)))
			elif rim > 0:
				px.append((150, 16, 8, int(220 * rim)))
			elif wash > 0:
				px.append((210, 32, 12, int(70 * wash)))
			else:
				px.append((0, 0, 0, 0))
	return px

def flat(texture):
	"""A face-up square across the whole model at mid height, with no shading: an item display lays it on the ground."""
	face = {"uv": [0, 0, 16, 16], "texture": "#0"}
	return {"textures": {"0": f"nether_rifts:item/{texture}", "particle": f"nether_rifts:item/{texture}"},
		"elements": [{"from": [0, 8, 0], "to": [16, 8, 16], "shade": False, "light_emission": 15,
			"faces": {"up": face, "down": {**face, "uv": [0, 16, 16, 0]}}}]}

def main():
	png(ASSETS / "textures/item/rift_edge.png", 16, 16, edge())
	png(ASSETS / "textures/item/bomb_warning.png", 32, 32, ring())
	write_json(ASSETS / "models/item/bomb_warning.json", flat("bomb_warning"))
	write_json(ASSETS / "items/bomb_warning.json", {"model": {"type": "minecraft:model", "model": "nether_rifts:item/bomb_warning"}})
	for name, tint in (("rift_panel", BODY_TINT), ("rift_edge", EDGE_TINT)):
		write_json(ASSETS / "models/item" / f"{name}.json", quad(name, name == "rift_panel"))
		write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model",
			"model": f"nether_rifts:item/{name}", "tints": [{"type": "minecraft:dye", "default": tint}]}})
	print("wrote", ASSETS)

main()
