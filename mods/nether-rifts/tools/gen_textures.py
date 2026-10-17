"""Draws the nether rift's edge sprite and writes both rift parts' models and item definitions.
The panel's sprite, rift_panel.png, is the nether panorama: tools/gen_panorama.py writes it.
Run from the module: python3 tools/gen_textures.py"""
import json, struct, zlib
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

def main():
	png(ASSETS / "textures/item/rift_edge.png", 16, 16, edge())
	for name, tint in (("rift_panel", BODY_TINT), ("rift_edge", EDGE_TINT)):
		write_json(ASSETS / "models/item" / f"{name}.json", quad(name, name == "rift_panel"))
		write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model",
			"model": f"nether_rifts:item/{name}", "tints": [{"type": "minecraft:dye", "default": tint}]}})
	print("wrote", ASSETS)

main()
