"""Imports the artist's War Drummer (a Blockbench modded-entity .bbmodel) as src/main/resources/model/nether_rifts/
war_drummer.bbmodel, fitted to how BIL plays it. Run from the module: python3 tools/import_drummer.py <artist file>

- The texture is padded to 128 x 64 (the spare columns stay empty), its uv size set to match: BIL scales UVs by a
  texture's uv size, which Blockbench's modded-entity format ignores.
- The beat's leg tracks (a still stance) are swapped for the walk's, so it marches as it drums: BIL gives a
  .bbmodel's animations every bone, so the walk can't play under it.
- The flat bones are nested so they move together: ears in the head, the head, arms and jacket in the body,
  sleeves and pants in their limbs. A bone authored beside its new parent had the parent's motion copied into its
  own keys; that comes off so nothing moves twice. For the head only its position comes off (its rotation stays
  relative, so it rides the body's lean); sleeves and pants just follow.
- Empty marker groups for held and head items (item_right, item_left, item_head) are added where missing.
- Armour pieces are added where missing, and their item models written for every vanilla material
  (assets/nether_rifts/{models/item,items}/war_drummer/armour/): tools/piglin_armour.py, shared with the ghast's import.
Keyframes must be linear (the artist's are), so taking a parent's track off a child is exact at every frame."""
import base64
import io
import json
import sys
import uuid
from pathlib import Path

from PIL import Image

import piglin_armour
from piglin_armour import find

MODULE = Path(__file__).resolve().parent.parent
OUT = MODULE / "src/main/resources/model/nether_rifts/war_drummer.bbmodel"
WIDTH, HEIGHT = 128, 64

# (child, parent, what comes off the child's keys)
RIG = [("left_ear", "head", ()), ("right_ear", "head", ()),
       ("head", "body", ("position",)),
       ("jacket", "body", ("rotation", "position")),
       ("left_arm", "body", ("rotation", "position")), ("right_arm", "body", ("rotation", "position")),
       ("left_sleeve", "left_arm", None), ("right_sleeve", "right_arm", None),
       ("left_pants", "left_leg", None), ("right_pants", "right_leg", None)]
BEAT_FREE = {"left_leg", "right_leg", "left_pants", "right_pants"}


def pad_texture(d):
	tex = d["textures"][0]
	img = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")
	assert img.width <= WIDTH and img.height <= HEIGHT, img.size
	wide = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
	wide.paste(img, (0, 0))
	buf = io.BytesIO()
	wide.save(buf, "PNG", optimize=True)
	tex["source"] = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
	tex["width"] = tex["uv_width"] = WIDTH
	tex["height"] = tex["uv_height"] = HEIGHT
	d["resolution"] = {"width": WIDTH, "height": HEIGHT}


def series(animator, channel):
	return sorted(((k["time"], [float(k["data_points"][0][c]) for c in "xyz"]) for k in (animator or {}).get("keyframes", [])
		if k["channel"] == channel), key=lambda p: p[0])


def at(s, t):
	if not s:
		return [0.0, 0.0, 0.0]
	if t <= s[0][0]:
		return s[0][1]
	if t >= s[-1][0]:
		return s[-1][1]
	for (t0, a), (t1, b) in zip(s, s[1:]):
		if t0 <= t <= t1:
			f = (t - t0) / (t1 - t0) if t1 > t0 else 0
			return [x + (y - x) * f for x, y in zip(a, b)]


def number(v):
	v = round(v, 4)
	return str(int(v)) if v == int(v) else str(v)


def nest(d, child, parent, off):
	uid = {g["name"]: g["uuid"] for g in d["groups"]}
	for a in d["animations"]:
		for k in (k for an in a["animators"].values() for k in an.get("keyframes", [])):
			assert k.get("interpolation", "linear") == "linear", f"{a['name']}: {k.get('interpolation')} keys"
		anims = a["animators"]
		if off is None:
			anims.pop(uid[child], None)
			continue
		c, p = anims.get(uid[child]), anims.get(uid[parent])
		assert not series(p, "scale"), f"{parent} scales in {a['name']}"
		keep = [k for k in (c or {}).get("keyframes", []) if k["channel"] not in off]
		for channel in off:
			cs, ps = series(c, channel), series(p, channel)
			for t in sorted({t for t, _ in cs} | {t for t, _ in ps}):
				v = [x - y for x, y in zip(at(cs, t), at(ps, t))]
				keep.append({"channel": channel, "data_points": [{"x": number(v[0]), "y": number(v[1]), "z": number(v[2])}],
					"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{a['name']}/{child}/{channel}/{t}")), "time": t, "color": -1,
					"interpolation": "linear"})
		if keep or c is not None:
			anims.setdefault(uid[child], {"name": child, "type": "bone"})["keyframes"] = keep
	lst, i = find(d["outliner"], uid[child])
	node = lst.pop(i)
	plst, j = find(d["outliner"], uid[parent])
	plst[j]["children"].append(node)


# Where HeldItems draws the mob's gear (empty groups; mob/HeldItems.java): the bottom-centre of each fist, the
# centre of the head. Added where the artist's file has none.
MARKERS = [("item_right", "right_arm", (6, 11, 0)), ("item_left", "left_arm", (-6, 11, 0)), ("item_head", "head", (0, 26, 0))]


def add_markers(d):
	names = {g["name"] for g in d["groups"]}
	template = next(g for g in d["groups"] if g["name"] == "head")
	for name, parent, origin in MARKERS:
		if name not in names:
			piglin_armour.add_group(d, "war_drummer", name, parent, origin, template)


def main(src):
	d = json.loads(Path(src).read_text())
	d["name"] = "war_drummer"
	pad_texture(d)
	# BIL gives a .bbmodel's animations every bone (the top one playing poses the whole model), so the walk can't
	# play under the beat: the beat takes the walk's legs instead, both being 1 s loops
	beat = next(a for a in d["animations"] if a["name"] == "beat")
	walk = next(a for a in d["animations"] if a["name"] == "walk")
	assert float(beat["length"]) == float(walk["length"]), (beat["length"], walk["length"])
	beat["animators"] = {k: an for k, an in beat["animators"].items() if an.get("name") not in BEAT_FREE}
	beat["animators"].update({k: json.loads(json.dumps(an)) for k, an in walk["animators"].items() if an.get("name") in BEAT_FREE})
	uid = {g["name"]: g["uuid"] for g in d["groups"]}
	for child, parent, off in RIG:
		lst, i = find(d["outliner"], uid[child])
		plst, j = find(d["outliner"], uid[parent])
		if lst is plst[j]["children"]:
			continue  # the artist nested it already: its keys are relative as they stand
		nest(d, child, parent, off)
	add_markers(d)
	jar = piglin_armour.mc_jar()
	bones = {role: role for role in piglin_armour.ROLES}
	piglin_armour.add_armour(d, jar, "war_drummer", bones)
	piglin_armour.write_armour_assets(d, jar, "war_drummer", bones)
	OUT.write_text(json.dumps(d, indent="\t") + "\n")
	print("wrote", OUT)


if __name__ == "__main__":
	main(sys.argv[1])
