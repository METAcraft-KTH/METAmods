"""Builds the nether rift's panorama sprite (rift_panel.png) from the six faces PanoramaCapture took.
Run from the module: python3 tools/gen_panorama.py [face size, default 512]
or: python3 tools/gen_panorama.py alpha (re-stamps the committed sprite's alpha, without the faces)

Reads tools/panorama/panorama_0..5.png (Minecraft's own panorama, 1024 px a face, taken facing south, looking
into the Eskmorra fortress build: see PanoramaCapture and the README), box-downsamples each face to 512 (sharp
enough that the fortress reads through a crack; the shader takes the face size from the sprite, so any size
works), lifts its shadows, posterises it lightly toward pixel art and lays the faces out 3×2:

	row 0: face 0 | face 1 | face 2
	row 1: face 3 | face 4 | face 5

Face order (the shader, nether_rift.glsl in metacraft-shaders, expects exactly this), each seen from the
camera with image right and image up as given:
	0 looks south (+Z): right is -X, up is +Y
	1 looks west  (-X): right is -Z, up is +Y
	2 looks north (-Z): right is +X, up is +Y
	3 looks east  (+X): right is +Z, up is +Y
	4 looks up    (+Y): right is -X, up is -Z
	5 looks down  (-Y): right is -X, up is +Z

Every pixel's alpha is FALLBACK_ALPHA. The shader reads the colour whatever the alpha (metacraft-shaders' item
shader skips the alpha cutout for nether rifts), and alpha strictly between 0 and 255 makes Minecraft draw the
panel blended, which the glass's tint needs. A client whose core shaders are replaced (Iris, OptiFine, no
patched pack) draws the raw sprite instead, and its alpha test throws all of it away: it sees the plain nether
portal behind, not the panorama stretched over it.

Standard library only: a small PNG reader (8-bit RGB/RGBA, not interlaced) and writer."""
import struct, sys, zlib
from pathlib import Path

MODULE = Path(__file__).resolve().parent.parent
FACES = MODULE / "tools/panorama"
OUT = MODULE / "src/main/resources/assets/nether_rifts/textures/item/rift_panel.png"
LEVELS = 24   # per channel: a light posterise, enough to band the fog into steps
GAMMA = 0.7   # lifts the nether's shadows: through a thin crack, under the rift's vignette, they'd read as black
FALLBACK_ALPHA = 1   # of 255: under any alpha test (vanilla's and packs' are 0.1), not 0, so the sprite counts as translucent

def read_png(path):
	data = path.read_bytes()
	assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path} is not a PNG"
	pos, idat, w = 8, b"", 0
	while pos < len(data):
		length, kind = struct.unpack(">I4s", data[pos:pos + 8])
		body = data[pos + 8:pos + 8 + length]
		if kind == b"IHDR":
			w, h, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
			assert depth == 8 and colour in (2, 6) and interlace == 0, f"{path}: only 8-bit RGB/RGBA, not interlaced"
			bpp = 4 if colour == 6 else 3
		elif kind == b"IDAT":
			idat += body
		pos += 12 + length
	raw = zlib.decompress(idat)
	stride = w * bpp
	rows, prev = [], bytearray(stride)
	for y in range(h):
		f = raw[y * (stride + 1)]
		line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
		for i in range(stride):
			a = line[i - bpp] if i >= bpp else 0
			b = prev[i]
			c = prev[i - bpp] if i >= bpp else 0
			if f == 1:
				line[i] = (line[i] + a) & 255
			elif f == 2:
				line[i] = (line[i] + b) & 255
			elif f == 3:
				line[i] = (line[i] + (a + b) // 2) & 255
			elif f == 4:
				p = a + b - c
				pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
				line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
		rows.append([tuple(line[x * bpp:x * bpp + 3]) for x in range(w)])
		prev = line
	return w, h, rows

def write_png(path, w, h, rows):
	raw = b"".join(b"\x00" + bytes(c for px in row for c in (*px[:3], FALLBACK_ALPHA)) for row in rows)
	def chunk(kind, body):
		return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def downsample(w, h, rows, size):
	"""Box filter: each output pixel is the mean of the source pixels it covers."""
	out = []
	for oy in range(size):
		y0, y1 = oy * h // size, max(oy * h // size + 1, (oy + 1) * h // size)
		line = []
		for ox in range(size):
			x0, x1 = ox * w // size, max(ox * w // size + 1, (ox + 1) * w // size)
			s, n = [0, 0, 0], 0
			for y in range(y0, y1):
				row = rows[y]
				for x in range(x0, x1):
					p = row[x]
					s[0] += p[0]; s[1] += p[1]; s[2] += p[2]
					n += 1
			line.append(tuple(s[i] / n for i in range(3)))
		out.append(line)
	return out

def posterise(c):
	step = 255 / (LEVELS - 1)
	return tuple(min(255, int(round(round(255 * (v / 255) ** GAMMA / step) * step))) for v in c)

def main():
	if sys.argv[1:] == ["alpha"]:
		w, h, rows = read_png(OUT)
		write_png(OUT, w, h, rows)
		print(f"re-stamped {OUT} ({w}×{h}) with alpha {FALLBACK_ALPHA}")
		return
	size = int(sys.argv[1]) if len(sys.argv) > 1 else 512
	atlas = [[(0, 0, 0)] * (size * 3) for _ in range(size * 2)]
	for face in range(6):
		w, h, rows = read_png(FACES / f"panorama_{face}.png")
		small = downsample(w, h, rows, size)
		col, row = face % 3, face // 3
		for y in range(size):
			for x in range(size):
				atlas[row * size + y][col * size + x] = posterise(small[y][x])
		print(f"face {face} done")
	write_png(OUT, size * 3, size * 2, atlas)
	print(f"wrote {OUT} ({size * 3}×{size * 2}, faces of {size})")

main()
