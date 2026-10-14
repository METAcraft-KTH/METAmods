"""Imports the artist's Chained Ghast (a Blockbench modded-entity .bbmodel) as src/main/resources/model/nether_rifts/
war_ghast.bbmodel, fitted to how BIL plays it. Run from the module: python3 tools/import_ghast.py <artist file>

- The texture's uv size is set to its real size: BIL scales UVs by a texture's uv size, which Blockbench's
  modded-entity format ignores.
- The model moves so the ghast's body (the face cubes) sits on y = 0, centred on x and z: BIL puts y = 0 at the
  entity's feet, and the ghast's 4 x 4 x 4 box must be its body. WarGhast.DECK_DROP is how far the lowest cube (the
  deck's underside) then hangs below the feet; the import prints it.
- Names: BIL turns any bone whose name starts with "head" (and all in it) with the mob's look, so the faces
  (head-sad, head-shooting) become face_sad and face_shooting, and each crew figure's bones take its name
  (head2 in piglin1 becomes piglin1_head; the drummer_piglin's are drummer_*). body2 becomes body.
- Each tentacle turns about the top centre of its cube (the artist's tentacle2/3 and 8/9 had each other's pivots).
- A figure's overlay layers (jacket, sleeves, pants) join the part they cover, without keys of their own: they move
  with it. The deck's chains join the deck while nothing animates them. Fewer bones: each is a display entity.
- Markers (empty groups, as BIL keeps them as nodes): <figure>_feet at the middle of each figure's feet, and
  <figure>_facing 8 px in front of it, so WarGhast can put the crew where they stand and turn them the way they
  face; item_left in piglin1's left fist, item_right in piglin2's right fist and item_head in the bombardier's head
  for the ghast's held and worn items (mob/HeldItems.java). Added where missing.
- Armour pieces on piglin1 and their item models for every vanilla material (tools/piglin_armour.py,
  assets/nether_rifts/{models/item,items}/war_ghast/armour/).
Keyframes must be linear."""
import json
import re
import sys
from pathlib import Path

import piglin_armour
from piglin_armour import find

MODULE = Path(__file__).resolve().parent.parent
OUT = MODULE / "src/main/resources/model/nether_rifts/war_ghast.bbmodel"
MODEL = "war_ghast"

# The crew figures (WarGhast.Crew), and the prefix their bones take.
CREW = {"drummer_piglin": "drummer", "piglin1": "piglin1", "piglin2": "piglin2", "bombardier": "bombardier"}
RENAME = {"body2": "body", "head-sad": "face_sad", "head-shooting": "face_shooting"}
FACES = ("face_sad", "face_shooting")
OVERLAY = re.compile(r"_(jacket|left_sleeve|right_sleeve|left_pants|right_pants)$")
CHAIN = re.compile(r"^chain")
TENTACLE = re.compile(r"^tentacle\d+$")
# How far ahead of a figure's feet its facing marker is, in model units (the model faces -z).
FACING = 8
# The ghast's held and worn items: (marker, figure, part, where in the part's main cube). The ghast is left-handed: its
# main hand (the spear) in piglin1's left fist, its off hand (the crossbow) in piglin2's right.
ITEM_MARKERS = [("item_left", "piglin1", "left_arm", "bottom"), ("item_right", "piglin2", "right_arm", "bottom"),
	("item_head", "bombardier", "head", "centre")]
# piglin1 wears the ghast's armour
ARMOUR_BONES = {role: f"piglin1_{role}" for role in piglin_armour.ROLES}


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


def rename_crew(d):
	"""Each figure's bones take its prefix; a trailing number (head2) goes unless that leaves two alike."""
	for figure, prefix in CREW.items():
		inside = set(subtree(outliner_node(d, figure))[1:])
		groups = [g for g in d["groups"] if g["uuid"] in inside and g["name"] != "magma_bomb"]
		bare = [re.sub(r"\d+$", "", g["name"]) for g in groups]
		for g, part in zip(groups, bare):
			rename(d, g, f"{prefix}_{part if bare.count(part) == 1 else g['name']}")


def merge(d, name, drop_keys):
	"""Moves a group's cubes and groups into its parent and drops the group (with its keys, when drop_keys)."""
	g = group(d, name)
	assert all(r == 0 for r in g.get("rotation", [0, 0, 0])), f"{name} is turned at rest"
	if not drop_keys and has_keys(d, g["uuid"]):
		return False
	lst, i = find(d["outliner"], g["uuid"])
	node = lst.pop(i)
	lst[i:i] = node["children"]
	for a in d["animations"]:
		a["animators"].pop(g["uuid"], None)
	d["groups"].remove(g)
	return True


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


def main(src):
	d = json.loads(Path(src).read_text())
	d["name"] = MODEL
	for a in d["animations"]:
		for k in (k for an in a["animators"].values() for k in an.get("keyframes", [])):
			assert k.get("interpolation", "linear") == "linear", f"{a['name']}: {k.get('interpolation')} keys"
	for tex in d["textures"]:
		tex["uv_width"], tex["uv_height"] = tex["width"], tex["height"]

	rename_crew(d)
	for old, new in RENAME.items():
		if any(g["name"] == old for g in d["groups"]):
			rename(d, group(d, old), new)
	for g in list(d["groups"]):
		if OVERLAY.search(g["name"]):
			merge(d, g["name"], drop_keys=True)
		elif CHAIN.search(g["name"]):
			merge(d, g["name"], drop_keys=False)

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

	heads = [g["name"] for g in d["groups"] if g["name"].startswith("head")]
	assert not heads, f"BIL would turn these with the look: {heads}"
	OUT.write_text(json.dumps(d, indent="\t") + "\n")
	bones = sum(1 for n in all_nodes(d["outliner"]) if cubes_of(n))
	print("wrote", OUT, f"({bones} bones); the lowest cube is", -bottom, "units under the feet: DECK_DROP =", -bottom / 16)


if __name__ == "__main__":
	main(sys.argv[1])
