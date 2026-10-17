"""Converts a Blockbench Java Block/Item project (.bbmodel, model format java_block) to a Java item model, as Blockbench's
File > Export > Block/Item Model writes it, onto a texture of the mod's. Run from the module:
python3 tools/bbmodel_to_item.py <.bbmodel> <texture id> <item model .json> [<cube name> ...] [--centre]

- Each cube becomes an element, in the project's order; given cube names, only those cubes (and none that isn't
  there). A cube's name stays on its element.
- Java models have no inflate: a cube's inflate grows its from and to by that much on every axis.
- Face UVs are in the project's texture pixels (its resolution, which needn't be square: the goggles' is 36 x 16), and
  Java models want 0 to 16 on each axis: u scales by 16 / width, v by 16 / height. A face's rotation, tint index and
  cull face carry over; a face with no texture is left out.
- A turned cube keeps its turn about its origin: as an angle about one axis where it turns about one, else as x, y
  and z (which item models also take).
- The display block (each slot's rotation, translation and scale) carries over as it is; Blockbench's own extras don't.
- --centre moves the cubes (turns and all) so the middle of what they cover is the model's middle, 8, 8, 8: for a
  piece cut out of a bigger model (the bomb out of the Grenadier's arm) to be shown on its own.
- Every face draws texture #0, the texture id given (e.g. nether_rifts:item/bombardier_helmet), which is also the
  particle. The project's own texture is the PNG to put there: the tool doesn't copy it."""
import json
import math
import sys
from pathlib import Path

AXES = "xyz"


def number(v):
	v = round(float(v), 5)
	return int(v) if v == int(v) else v


def element(cube, width, height):
	grow = cube.get("inflate") or 0
	out = {"name": cube["name"],
		"from": [number(v - grow) for v in cube["from"]],
		"to": [number(v + grow) for v in cube["to"]]}
	turn = [float(v) for v in cube.get("rotation") or (0, 0, 0)]
	turned = [a for a in range(3) if turn[a]]
	if len(turned) == 1:
		out["rotation"] = {"angle": number(turn[turned[0]]), "axis": AXES[turned[0]], "origin": [number(v) for v in cube["origin"]]}
	elif turned:
		out["rotation"] = {**{AXES[a]: number(turn[a]) for a in range(3)}, "origin": [number(v) for v in cube["origin"]]}
	if cube.get("shade") is False:
		out["shade"] = False
	if cube.get("light_emission"):
		out["light_emission"] = cube["light_emission"]
	faces = {}
	for side, face in cube["faces"].items():
		if face.get("texture") is None:
			continue
		u0, v0, u1, v1 = face["uv"]
		f = {"uv": [number(u0 * 16 / width), number(v0 * 16 / height), number(u1 * 16 / width), number(v1 * 16 / height)],
			"texture": f"#{face['texture']}"}
		for key in ("rotation", "tintindex", "cullface"):
			if face.get(key) not in (None, 0, ""):
				f[key] = face[key]
		faces[side] = f
	out["faces"] = faces
	return out


def corners(el):
	"""An element's eight corners, turned as it is."""
	rot = el.get("rotation")
	if rot is None:
		angles = (0, 0, 0)
	elif "axis" in rot:
		angles = tuple(rot["angle"] if a == rot["axis"] else 0 for a in AXES)
	else:
		angles = tuple(rot.get(a, 0) for a in AXES)
	origin = rot["origin"] if rot else (0, 0, 0)
	out = []
	for x in (el["from"][0], el["to"][0]):
		for y in (el["from"][1], el["to"][1]):
			for z in (el["from"][2], el["to"][2]):
				p = [x - origin[0], y - origin[1], z - origin[2]]
				for axis, angle in zip(range(3), angles):
					if not angle:
						continue
					c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
					i, j = [(1, 2), (2, 0), (0, 1)][axis]
					p[i], p[j] = p[i] * c - p[j] * s, p[i] * s + p[j] * c
				out.append([p[k] + origin[k] for k in range(3)])
	return out


def centre(elements):
	"""Moves the elements so the middle of the box round them all (turned) is at 8, 8, 8."""
	points = [p for el in elements for p in corners(el)]
	shift = [8 - (min(p[k] for p in points) + max(p[k] for p in points)) / 2 for k in range(3)]
	for el in elements:
		el["from"] = [number(el["from"][k] + shift[k]) for k in range(3)]
		el["to"] = [number(el["to"][k] + shift[k]) for k in range(3)]
		if "rotation" in el:
			el["rotation"]["origin"] = [number(el["rotation"]["origin"][k] + shift[k]) for k in range(3)]


def convert(src, texture, names=(), centred=False):
	d = json.loads(Path(src).read_text())
	assert d["meta"]["model_format"] == "java_block", f"{src} is a {d['meta']['model_format']} project, not a Java Block/Item one"
	width, height = d["resolution"]["width"], d["resolution"]["height"]
	cubes = [e for e in d["elements"] if e.get("type", "cube") == "cube" and e.get("export", True)]
	if names:
		missing = set(names) - {c["name"] for c in cubes}
		assert not missing, f"{src} has no cube named {', '.join(sorted(missing))}"
		cubes = [c for c in cubes if c["name"] in names]
	model = {"textures": {"0": texture, "particle": texture}, "elements": [element(c, width, height) for c in cubes]}
	if centred:
		centre(model["elements"])
	display = {slot: {k: v for k, v in t.items() if k in ("rotation", "translation", "scale")} for slot, t in (d.get("display") or {}).items()}
	if display:
		model["display"] = display
	return model


def main(src, texture, out, *names):
	centred = "--centre" in names
	model = convert(src, texture, [n for n in names if n != "--centre"], centred)
	Path(out).parent.mkdir(parents=True, exist_ok=True)
	Path(out).write_text(json.dumps(model, indent="\t") + "\n")
	print("wrote", out, f"({len(model['elements'])} elements)")


if __name__ == "__main__":
	main(*sys.argv[1:])
