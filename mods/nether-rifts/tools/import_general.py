"""Imports the artist's Piglin General (a Blockbench modded-entity .bbmodel) as src/main/resources/model/nether_rifts/
piglin_general.bbmodel, fitted to how BIL plays it. Run from the module:
python3 -I tools/import_general.py <artist file> <output .bbmodel>

- The geometry is scaled by SCALE (1.5, PiglinGeneral.MODEL_SCALE: keep the two the same): cube from, to, origin and
  inflate, group origins and every position key. Rotations are angles and stay as they are. The artist's box UVs carry
  explicit faces, which BIL reads, so the texture still maps the same.
- idle_pose_(dont_use), the artist's reference pose, is dropped.
- The animations are made whole: BIL gives a .bbmodel's animations every bone (the top one playing poses the whole
  model), where Blockbench lets a bone the animation doesn't key stay at rest. Each animation takes idle's first pose
  for every bone it doesn't key (a bone idle doesn't key either gets a rest rotation), so every animation keys every bone.
- The root bone's full turns (spin and dizzy turn `general` from 0 to 360 degrees) are split into steps of at most 90
  degrees at evenly spaced times: BIL works in quaternions, where 0 and 360 degrees are the same orientation and the
  turn would not show.
- The bones keep their names: `head` already turns with the mob's look in BIL. The texture stays as embedded.
Keyframes of the root's turns must be linear (the artist's are), so splitting them is exact."""
import json
import math
import sys
import uuid
from pathlib import Path

# PiglinGeneral.MODEL_SCALE
SCALE = 1.5
ROOT = "general"
# The most a split key may turn the root, in degrees
MAX_STEP = 90
DROPPED = "idle_pose_(dont_use)"
IDLE = "idle"


# series, at and number are import_drummer.py's (copied: python3 -I leaves the script's folder off the import path)
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


def scale_geometry(d):
	for e in d["elements"]:
		for key in ("from", "to", "origin"):
			e[key] = [x * SCALE for x in e[key]]
		if e.get("inflate"):
			e["inflate"] *= SCALE
	for g in d["groups"]:
		g["origin"] = [x * SCALE for x in g["origin"]]
	for a in d["animations"]:
		for an in a["animators"].values():
			for k in an.get("keyframes", []):
				if k["channel"] == "position":
					for p in k["data_points"]:
						for c in "xyz":
							p[c] = number(float(p[c]) * SCALE)


def key(animation, bone, channel, time, values, template):
	return {"channel": channel, "data_points": [{c: number(v) for c, v in zip("xyz", values)}],
		"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{animation}/{bone}/{channel}/{time}")), "time": time,
		"color": template.get("color", -1), "interpolation": template.get("interpolation", "linear")}


def fill_from_idle(d):
	idle = next(a for a in d["animations"] if a["name"] == IDLE)
	for a in d["animations"]:
		for g in d["groups"]:
			mine = a["animators"].get(g["uuid"])
			if mine and mine.get("keyframes"):
				continue
			theirs = idle["animators"].get(g["uuid"])
			keys = []
			for channel in sorted({k["channel"] for k in (theirs or {}).get("keyframes", [])}):
				first = next(k for k in sorted(theirs["keyframes"], key=lambda k: k["time"]) if k["channel"] == channel)
				keys.append(key(a["name"], g["name"], channel, 0, at(series(theirs, channel), 0), first))
			if not keys:
				keys.append(key(a["name"], g["name"], "rotation", 0, [0, 0, 0], {}))  # idle leaves it at rest too
			if keys:
				an = a["animators"].setdefault(g["uuid"], {"name": g["name"], "type": "bone", "rotation_global": False,
					"quaternion_interpolation": False})
				an["keyframes"] = keys


def split_turns(d):
	root = next(g for g in d["groups"] if g["name"] == ROOT)["uuid"]
	for a in d["animations"]:
		an = a["animators"].get(root)
		if not an or not an.get("keyframes"):
			continue
		rotation = sorted((k for k in an["keyframes"] if k["channel"] == "rotation"), key=lambda k: k["time"])
		out = [k for k in an["keyframes"] if k["channel"] != "rotation"]
		for k in rotation:
			assert k.get("interpolation", "linear") == "linear", f"{a['name']}: {k.get('interpolation')} turn"
		for k0, k1 in zip(rotation, rotation[1:]):
			v0, v1 = (series({"keyframes": [k]}, "rotation")[0][1] for k in (k0, k1))
			steps = max(1, math.ceil(max(abs(y - x) for x, y in zip(v0, v1)) / MAX_STEP - 1e-9))
			for i in range(1, steps):
				t = round(k0["time"] + (k1["time"] - k0["time"]) * i / steps, 4)
				out.append(key(a["name"], ROOT, "rotation", t, [x + (y - x) * i / steps for x, y in zip(v0, v1)], k0))
		an["keyframes"] = sorted(out + rotation, key=lambda k: (k["channel"], k["time"]))


def main(src, dst):
	d = json.loads(Path(src).read_text())
	d["name"] = "piglin_general"
	d["animations"] = [a for a in d["animations"] if a["name"] != DROPPED]
	scale_geometry(d)
	fill_from_idle(d)
	split_turns(d)
	Path(dst).write_text(json.dumps(d, indent="\t") + "\n")
	print("wrote", dst)


if __name__ == "__main__":
	main(sys.argv[1], sys.argv[2])
