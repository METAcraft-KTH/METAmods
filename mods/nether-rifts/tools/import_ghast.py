"""Imports the artist's Chained Ghast (a Blockbench modded-entity .bbmodel) as src/main/resources/model/nether_rifts/
war_ghast.bbmodel, fitted to how BIL plays it. Run from the module:
python3 tools/import_ghast.py <artist file> [<texture png>]

- The texture is the PNG given beside the file, when there is one (the artist sends the latest texture as a PNG too;
  the one in the file may be older), else the file's own. Its uv size is set to its real size: BIL scales UVs by a
  texture's uv size, which Blockbench's modded-entity format ignores.
- Keys for bones the file no longer has (left over from groups the artist deleted) are dropped.
- The model moves so the ghast's body (the face cubes) sits on y = 0, centred on x and z: BIL puts y = 0 at the
  entity's feet, and the ghast's 4 x 4 x 4 box must be its body. WarGhast.DECK_DROP is how far the lowest cube (the
  deck's underside) then hangs below the feet; the import prints it.
- Names: BIL turns any bone whose name starts with "head" (and all in it) with the mob's look, so the faces
  (head-sad, head-shooting) become face_sad and face_shooting, and each crew figure's bones take its name (head2 in
  spear_piglin becomes spear_piglin_head; the drummer_piglin's are drummer_*). body2 becomes body. The markers and the
  Grenadier's magma_bomb keep their names.
- Each tentacle turns about the top centre of its cube (the artist's tentacle2/3 and 8/9 have each other's pivots).
- Fewer bones, as each is a display entity. A figure's overlay layers (jacket, sleeves, pants) join the part they
  cover and move with it. Their own keys go (the artist has given them none; the import names any it drops), so that
  is exact whatever the keys' interpolation. The deck's chains join the deck while nothing animates them, and so do the
  Grenadier's goggles his head. The bomb pile on the deck joins the deck too: its bombs are placed by idle keys that
  hold one value (and no other animation moves them), which the import bakes into the cubes (moved, and turned by the
  element rotation BIL's item models take).
- Markers (empty groups, as BIL keeps them as nodes): <figure>_feet at the middle of each figure's feet, and
  <figure>_facing 8 px in front of it, so WarGhast can put the crew where they stand and turn them the way they
  face. The ghast's held items (mob/HeldItems.java): item_right for its main hand (the spear) and item_left for its
  off hand (the crossbow); the artist places them, item_right in spear_piglin's left fist and item_left on
  crossbow_piglin's body. Where the file has none, item_right goes in spear_piglin's left fist, item_left in
  crossbow_piglin's right fist and item_head in the Grenadier's head.
- Armour pieces on spear_piglin and their item models for every vanilla material (tools/piglin_armour.py,
  assets/nether_rifts/{models/item,items}/war_ghast/armour/).
- The animations other than idle are made whole: BIL gives a .bbmodel's animations every bone (the top one playing
  poses the whole model), where Blockbench plays the artist's on top of idle. So each takes idle's tracks for the bones
  and channels it doesn't key itself (shoot, dive and climb: the whole track, so BIL samples exactly idle's values over
  their length; WarGhast starts idle again with them, so idle carries on where they end), and drop_bomb, a short loop
  of the Grenadier's throw, becomes idle with that throw in place of his arm and bomb, repeated a whole number of times
  over idle's length (12 throws of 1/3 s for the artist's 0.32 s; the catmullrom curve now runs on into the next throw
  where a lone loop ended it on its last key). crew_gone is left as it is: WarGhast hides each figure itself.
Any keyframe interpolation will do (linear, catmullrom, step, bezier): the import moves whole tracks and never resamples
them, but for drop_bomb's throw, whose keys it only spaces out."""
import base64
import copy
import io
import json
import re
import sys
import uuid
from pathlib import Path

from PIL import Image

import piglin_armour
from piglin_armour import find

MODULE = Path(__file__).resolve().parent.parent
OUT = MODULE / "src/main/resources/model/nether_rifts/war_ghast.bbmodel"
MODEL = "war_ghast"

# The crew figures (WarGhast.Crew), and the prefix their bones take.
CREW = {"drummer_piglin": "drummer", "spear_piglin": "spear_piglin", "crossbow_piglin": "crossbow_piglin", "bombardier": "bombardier"}
# Groups inside a figure that keep their names: the markers, and the bomb in the Grenadier's hand
KEEP_NAME = re.compile(r"^(item_|magma_bomb$)")
RENAME = {"body2": "body", "head-sad": "face_sad", "head-shooting": "face_shooting"}
FACES = ("face_sad", "face_shooting")
OVERLAY = re.compile(r"_(jacket|left_sleeve|right_sleeve|left_pants|right_pants)$")
CHAIN = re.compile(r"^chain")
# Groups without keys that join their parent bone
JOIN_PARENT = ("bombardier_goggles",)
# Groups placed by keys that never move them, which join the deck with their places baked in
STATIC_ON_DECK = ("bomb_pile",)
TENTACLE = re.compile(r"^tentacle\d+$")
# How far ahead of a figure's feet its facing marker is, in model units (the model faces -z).
FACING = 8
# The ghast's held and worn items, where the file has no marker of its own: (marker, figure, part, where in the part's
# main cube). The ghast is right-handed: its main hand (the spear) in spear_piglin's left fist, its off hand (the
# crossbow) with crossbow_piglin.
ITEM_MARKERS = [("item_right", "spear_piglin", "left_arm", "bottom"), ("item_left", "crossbow_piglin", "right_arm", "bottom"),
	("item_head", "bombardier", "head", "centre")]
# spear_piglin wears the ghast's armour
ARMOUR_BONES = {role: f"spear_piglin_{role}" for role in piglin_armour.ROLES}
IDLE = "idle"
# Played on top of idle: made whole with idle's tracks
ON_IDLE = ("shoot", "dive", "climb")
# A short loop played over idle for a while: repeated over idle's length, with idle's tracks
LOOPED_ON_IDLE = ("drop_bomb",)


def subtree(node):
	"""The uuids of a node's groups, itself first."""
	out = [node["uuid"]]
	for c in node["children"]:
		if isinstance(c, dict):
			out += subtree(c)
	return out


def cubes_of(node):
	return [c for c in node["children"] if isinstance(c, str)]


def outliner_node(d, name):
	g = group(d, name)
	lst, i = find(d["outliner"], g["uuid"])
	return lst[i]


def group(d, name):
	found = [g for g in d["groups"] if g["name"] == name]
	assert len(found) == 1, f"{len(found)} groups named {name}"
	return found[0]


def animators(d, uid):
	return [a["animators"][uid] for a in d["animations"] if uid in a["animators"]]


def has_keys(d, uid):
	return any(an.get("keyframes") for an in animators(d, uid))


def rename(d, g, new):
	g["name"] = new
	for an in animators(d, g["uuid"]):
		an["name"] = new


def use_texture(d, png):
	"""Embeds the PNG as the model's texture, in place of the file's own."""
	tex = d["textures"][0]
	img = Image.open(png)
	assert (img.width, img.height) == (tex["width"], tex["height"]), f"{png} is {img.size}, the file's texture {tex['width']} x {tex['height']}"
	tex["source"] = "data:image/png;base64," + base64.b64encode(Path(png).read_bytes()).decode()


def drop_stale_keys(d):
	"""Keys for bones the file no longer has: the artist deleted the group, Blockbench kept its animator."""
	uids = {g["uuid"] for g in d["groups"]}
	for a in d["animations"]:
		for uid, an in list(a["animators"].items()):
			if an.get("type", "bone") == "bone" and uid not in uids:
				print(f"{a['name']}: dropped the keys of {an.get('name')}, which isn't in the file")
				del a["animators"][uid]


def rename_crew(d):
	"""Each figure's bones take its prefix; a trailing number (head2) goes unless that leaves two alike."""
	for figure, prefix in CREW.items():
		inside = set(subtree(outliner_node(d, figure))[1:])
		groups = [g for g in d["groups"] if g["uuid"] in inside and not KEEP_NAME.match(g["name"])]
		bare = [re.sub(r"\d+$", "", g["name"]) for g in groups]
		for g, part in zip(groups, bare):
			rename(d, g, f"{prefix}_{part if bare.count(part) == 1 else g['name']}")


def merge(d, name, drop_keys):
	"""Moves a group's cubes and groups into its parent and drops the group (with its keys, when drop_keys)."""
	g = group(d, name)
	assert all(r == 0 for r in g.get("rotation", [0, 0, 0])), f"{name} is turned at rest"
	if has_keys(d, g["uuid"]):
		if not drop_keys:
			return False
		print(f"{name} joins its part: dropped its own keys")
	lst, i = find(d["outliner"], g["uuid"])
	node = lst.pop(i)
	lst[i:i] = node["children"]
	for a in d["animations"]:
		a["animators"].pop(g["uuid"], None)
	d["groups"].remove(g)
	return True


def held_place(d, uid):
	"""The one place a group's keys hold, as Blockbench applies them to a group (position with x mirrored, rotation
	with x and y mirrored), or None if any animation moves or scales it. Only idle may key it: the others play over
	idle in Blockbench."""
	held = {"position": None, "rotation": None}
	for a in d["animations"]:
		an = a["animators"].get(uid)
		for k in (an or {}).get("keyframes", []):
			if k["channel"] not in held or a["name"] != IDLE:
				return None
			try:
				v = [float(k["data_points"][0][c]) for c in "xyz"]
			except ValueError:
				return None
			if held[k["channel"]] is not None and held[k["channel"]] != v:
				return None
			held[k["channel"]] = v
	p, r = held["position"] or [0, 0, 0], held["rotation"] or [0, 0, 0]
	# (+ 0.0: no -0.0 in the file)
	return [-p[0] + 0.0, p[1], p[2]], [-r[0] + 0.0, -r[1] + 0.0, r[2]]


def bake_static(d, name, by=(0, 0, 0)):
	"""Bakes a still group's place (and its groups') into its cubes, so it can join its parent: moved, then turned by
	the element rotation about the group's pivot. Its keys go."""
	g = group(d, name)
	place = held_place(d, g["uuid"])
	assert place, f"{name} moves"
	offset, turn = place
	assert all(r == 0 for r in g.get("rotation", [0, 0, 0])), f"{name} is turned at rest"
	by = [a + b for a, b in zip(by, offset)]
	elements = {e["uuid"]: e for e in d["elements"]}
	node = outliner_node(d, name)
	for c in cubes_of(node):
		e = elements[c]
		assert not any(e.get("rotation") or []), f"a cube of {name} is turned"
		e["from"] = [round(v + o, 4) for v, o in zip(e["from"], by)]
		e["to"] = [round(v + o, 4) for v, o in zip(e["to"], by)]
		e["origin"] = [round(v + o, 4) for v, o in zip(g["origin"], by)]
		if any(turn):
			e["rotation"] = turn
	for child in node["children"]:
		if isinstance(child, dict):
			assert not any(turn), f"{name} turns the groups in it"
			bake_static(d, group_name(d, child), by)
	for a in d["animations"]:
		a["animators"].pop(g["uuid"], None)
	g["origin"] = [round(v + o, 4) for v, o in zip(g["origin"], by)]
	for child in [c for c in node["children"] if isinstance(c, dict)]:
		merge(d, group_name(d, child), drop_keys=False)


def all_nodes(nodes):
	for n in nodes:
		if isinstance(n, dict):
			yield n
			yield from all_nodes(n["children"])


def shift(d, by):
	for e in d["elements"]:
		for key in ("from", "to", "origin"):
			e[key] = [round(v + o, 4) for v, o in zip(e[key], by)]
	for g in d["groups"]:
		g["origin"] = [round(v + o, 4) for v, o in zip(g["origin"], by)]


def box(d, cubes):
	elements = {e["uuid"]: e for e in d["elements"]}
	lo = [min(elements[c]["from"][a] for c in cubes) for a in range(3)]
	hi = [max(elements[c]["to"][a] for c in cubes) for a in range(3)]
	return lo, hi


def main_cube(d, node):
	"""A part's own cube: its biggest one that isn't inflated (the head's skull, not its snout or goggles)."""
	elements = {e["uuid"]: e for e in d["elements"]}
	own = [elements[c] for c in cubes_of(node) if not elements[c].get("inflate")]
	return max(own, key=lambda e: (e["to"][0] - e["from"][0]) * (e["to"][1] - e["from"][1]) * (e["to"][2] - e["from"][2]))


def add_markers(d):
	names = {g["name"] for g in d["groups"]}
	template = group(d, "body")
	for figure, prefix in CREW.items():
		legs = [c for n in all_nodes([outliner_node(d, figure)]) if re.search(r"_(left|right)_leg$", group_name(d, n)) for c in cubes_of(n)]
		assert legs, f"{figure} has no legs"
		lo, hi = box(d, legs)
		feet = [(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2]
		for name, at in ((f"{figure}_feet", feet), (f"{figure}_facing", [feet[0], feet[1], feet[2] - FACING])):
			if name not in names:
				piglin_armour.add_group(d, MODEL, name, figure, at, template)
	for name, figure, part, where in ITEM_MARKERS:
		if name in names:
			continue
		bone = f"{CREW[figure]}_{part}"
		cube = main_cube(d, outliner_node(d, bone))
		lo, hi = cube["from"], cube["to"]
		centre = [(a + b) / 2 for a, b in zip(lo, hi)]
		at = [centre[0], lo[1], centre[2]] if where == "bottom" else centre
		piglin_armour.add_group(d, MODEL, name, bone, at, template)


def group_name(d, node):
	return next(g["name"] for g in d["groups"] if g["uuid"] == node["uuid"])


def animation(d, name):
	found = [a for a in d["animations"] if a["name"] == name]
	assert len(found) == 1, f"{len(found)} animations named {name}"
	return found[0]


def channels(an):
	return {k["channel"] for k in an.get("keyframes", [])}


def copied_keys(keys, anim, bone, channel):
	"""Copies of keyframes, with ids of their own in the animation they go to."""
	out = []
	for n, k in enumerate(keys):
		k = copy.deepcopy(k)
		k["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{MODEL}/{anim}/{bone}/{channel}/{n}"))
		out.append(k)
	return out


def on_idle(d, name):
	"""Gives an animation idle's tracks for the bones and channels it doesn't key itself."""
	idle, a = animation(d, IDLE), animation(d, name)
	for uid, an in idle["animators"].items():
		mine = a["animators"].setdefault(uid, {"name": an["name"], "type": an.get("type", "bone"), "keyframes": []})
		mine.setdefault("keyframes", [])
		own = channels(mine)
		for channel in sorted(channels(an) - own):
			keys = [k for k in an["keyframes"] if k["channel"] == channel]
			mine["keyframes"] += copied_keys(keys, name, an["name"], channel)


def looped_on_idle(d, name):
	"""Makes a short loop idle with the loop's tracks in place of idle's, the loop repeated over idle's length."""
	idle, a = animation(d, IDLE), animation(d, name)
	length, short = float(idle["length"]), float(a["length"])
	times = max(1, round(length / short))
	period = length / times
	tracks = {}
	for uid, an in a["animators"].items():
		for channel in channels(an):
			keys = sorted((k for k in an["keyframes"] if k["channel"] == channel), key=lambda k: k["time"])
			spaced = []
			for n in range(times):
				for k in keys:
					t = round(n * period + k["time"] * period / short, 4)
					if spaced and abs(spaced[-1]["time"] - t) < 1e-4:
						# a throw's last key is the next one's first: keep one
						assert spaced[-1]["data_points"] == k["data_points"], f"{name}: {an['name']} {channel} doesn't loop"
						continue
					k = dict(k, time=t)
					spaced.append(k)
			tracks[(uid, channel)] = (an, copied_keys(spaced, name, an["name"], channel))
	animators = copy.deepcopy(idle["animators"])
	for an in animators.values():
		an["keyframes"] = copied_keys(an.get("keyframes", []), name, an["name"], "idle")
	for (uid, channel), (an, keys) in tracks.items():
		mine = animators.setdefault(uid, {"name": an["name"], "type": an.get("type", "bone"), "keyframes": []})
		mine["keyframes"] = [k for k in mine["keyframes"] if k["channel"] != channel] + keys
	a["animators"] = animators
	a["length"] = idle["length"]
	a["loop"] = "loop"
	print(f"{name}: {times} of its {short} s loop over idle's {length} s, {round(period, 4)} s each")


def main(src, texture=None):
	d = json.loads(Path(src).read_text())
	d["name"] = MODEL
	if texture:
		use_texture(d, texture)
	for tex in d["textures"]:
		tex["uv_width"], tex["uv_height"] = tex["width"], tex["height"]
	drop_stale_keys(d)

	rename_crew(d)
	for old, new in RENAME.items():
		if any(g["name"] == old for g in d["groups"]):
			rename(d, group(d, old), new)
	for g in list(d["groups"]):
		if OVERLAY.search(g["name"]):
			merge(d, g["name"], drop_keys=True)
		elif CHAIN.search(g["name"]):
			merge(d, g["name"], drop_keys=False)
	for name in JOIN_PARENT:
		assert merge(d, name, drop_keys=False), f"{name} has keys"
	for name in STATIC_ON_DECK:
		bake_static(d, name)
		merge(d, name, drop_keys=False)

	for g in d["groups"]:
		if TENTACLE.match(g["name"]):
			cubes = cubes_of(outliner_node(d, g["name"]))
			assert len(cubes) == 1, f"{g['name']} has {len(cubes)} cubes"
			lo, hi = box(d, cubes)
			g["origin"] = [(lo[0] + hi[0]) / 2, hi[1], (lo[2] + hi[2]) / 2]

	# the ghast's body on y = 0, centred
	lo, hi = box(d, [c for face in FACES for c in cubes_of(outliner_node(d, face))])
	size = [b - a for a, b in zip(lo, hi)]
	if any(abs(s - 64) > 0.01 for s in size):
		print("warning: the ghast's body is", size, "not 64 (4 blocks) a side")
	shift(d, [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2])
	bottom = min(e["from"][1] - (e.get("inflate") or 0) for e in d["elements"])

	add_markers(d)
	jar = piglin_armour.mc_jar()
	piglin_armour.add_armour(d, jar, MODEL, ARMOUR_BONES)
	piglin_armour.write_armour_assets(d, jar, MODEL, ARMOUR_BONES)

	for name in ON_IDLE:
		on_idle(d, name)
	for name in LOOPED_ON_IDLE:
		looped_on_idle(d, name)

	heads = [g["name"] for g in d["groups"] if g["name"].startswith("head")]
	assert not heads, f"BIL would turn these with the look: {heads}"
	OUT.write_text(json.dumps(d, indent="\t") + "\n")
	bones = sum(1 for n in all_nodes(d["outliner"]) if cubes_of(n))
	tentacles = sum(1 for g in d["groups"] if TENTACLE.match(g["name"]))
	print("wrote", OUT, f"({bones} bones, {tentacles} tentacles); the lowest cube is", -bottom, "units under the feet: DECK_DROP =", -bottom / 16)


if __name__ == "__main__":
	main(*sys.argv[1:3])
