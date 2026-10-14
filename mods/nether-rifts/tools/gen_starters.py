"""Writes Blockbench starter projects for the artist into blockbench/: the rift mobs' bone trees, bone names,
texture slot and animation stubs (names, lengths, loop modes) set up, on a blockout of the current placeholder
(or vanilla geometry for a new mob), so the artist only models and animates.
Run from the module: python3 tools/gen_starters.py
Same conventions as gen_models.py (whose helpers this borrows): Blockbench Generic Model, format 5.0, uuid5 ids,
16 units = 1 block, feet at y = 0, the model faces -Z, +X is the mob's right. Re-running gives byte-identical files.
The helmet is a Java Block/Item model project instead. After writing, every file is checked (see check())."""
import base64
import json
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))
import gen_models as gm  # noqa: E402  (Model, import_java_entity, textures from the jar, the banner)

OUT = TOOLS.parent / "blockbench"

# The template's bones as we name them (sides are the mob's own: the template is mirrored on import).
PIGLIN_BONES = {"Body": "body", "head": "head", "leftear": "ear_left", "rightear": "ear_right", "RightArm": "arm_right",
	"LeftArm": "arm_left", "RightLeg": "leg_right", "LeftLeg": "leg_left"}
# Cube names come from the template's bone names; these read better.
CUBE_NAMES = {"leftear_": "ear_left_", "rightear_": "ear_right_", "rightarm_": "arm_right_", "leftarm_": "arm_left_",
	"rightleg_": "leg_right_", "leftleg_": "leg_left_", "tentacles_": "tentacle_"}

# The animations each starter carries: name -> (length in seconds, Blockbench loop mode).
ANIMATIONS = {
	"war_drummer": {"idle": (2.0, "loop"), "walk": (1.0, "loop"), "beat": (0.5, "once"),
		"big_beat_windup": (0.5, "once"), "big_beat": (0.5, "once"), "death": (1.0, "hold")},
	"war_ghast": {"idle": (3.0, "loop"), "shoot": (1.0, "once"), "drop_bomb": (0.3, "once"), "dive": (1.0, "hold"),
		"climb": (1.0, "hold"), "crew_gone": (0.1, "loop")},
	"rift_captain": {"idle": (3.0, "loop"), "walk": (1.6, "loop"), "attack": (1.2, "once"),
		"spin_windup": (1.0, "once"), "spin": (0.5, "loop"), "dizzy": (1.0, "loop"), "slam": (1.5, "once"),
		"roar": (2.0, "once"), "death": (2.0, "hold")},
	"bombardier_helmet": {},
}


def stubs(m):
	"""Empty animations (no keyframes) with the right names, lengths and loop modes."""
	for name, (length, loop) in ANIMATIONS[m.name].items():
		if name not in {a["name"] for a in m.animations}:
			m.animation(name, length, loop, {})
	order = list(ANIMATIONS[m.name])
	m.animations.sort(key=lambda a: order.index(a["name"]))


def tidy_cube_names(m):
	for element in m.elements:
		for old, new in CUBE_NAMES.items():
			if element["name"].startswith(old):
				element["name"] = new + element["name"][len(old):]


def texture(m, **extra):
	return {"name": f"{m.name}.png", "path": "", "folder": "", "namespace": "", "id": "0", "width": m.size,
		"height": m.size, "uv_width": m.size, "uv_height": m.size, "particle": False, "render_mode": "default",
		"render_sides": "auto", "frame_time": 1, "visible": True, "internal": True, "saved": False,
		"uuid": gm.uid(m.name, "t"), **extra,
		"source": "data:image/png;base64," + base64.b64encode(gm.png(m.size, m.size, m.atlas)).decode()}


def save(data, name):
	OUT.mkdir(parents=True, exist_ok=True)
	path = OUT / f"{name}.bbmodel"
	path.write_text(json.dumps(data, indent="\t") + "\n")
	print("wrote", path.relative_to(TOOLS.parent))


def write_entity(m):
	"""A Generic Model project (what BIL loads), as gen_models writes them."""
	tidy_cube_names(m)
	stubs(m)
	save({"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
		"name": m.name, "model_identifier": m.name,
		"resolution": {"width": m.size, "height": m.size},
		"elements": m.elements, "groups": m.groups,
		"outliner": [m._outliner(n) for n in m.roots],
		"textures": [texture(m)],
		"animations": m.animations}, m.name)


# The drummer's rest pose: arms forward, hands over the drum (as in gen_models).
DRUMMING_ARMS = (55, 0, 0)


def war_drummer():
	"""The current drummer placeholder without its drumsticks (the drum is beaten by hand now); the snout banner is
	its own bone, head_banner."""
	m = gm.Model("war_drummer")
	arms = DRUMMING_ARMS
	gm.import_java_entity(m, "piglin.json", (0, 0), skip={"leftItem"}, rename=PIGLIN_BONES,
		# the legs stay at the root, so the body's bob doesn't lift the feet off the ground
		parents={"head": "body", "arm_right": "body", "arm_left": "body"},
		rotations={"arm_right": arms, "arm_left": arms})
	m.sheet(gm.piglin_banner(), (64, 0))
	m.sheet([row[::-1] for row in gm.piglin_banner()], (84, 0))
	# the horned gold helm
	m.cube("head", "helm", (-5.5, 30, -4.5), (5.5, 33, 4.5), "gold_block")
	m.cube("head", "helm_brim", (-5.75, 29.5, -4.75), (5.75, 30.5, 4.75), "gold_block")
	horn = [((5.75, 30, -1), (7.75, 32, 1)), ((7.25, 32, -0.75), (8.75, 34, 0.75)), ((7.75, 34, -0.5), (8.75, 36.5, 0.5))]
	for i, (frm, to) in enumerate(horn):
		m.cube("head", f"horn_right_{i}", frm, to, "bone_block_side")
		m.cube("head", f"horn_left_{i}", *gm.mirrored(frm, to), "bone_block_side")
	# the black snout banner behind the head, on a pole clipped to the helm's back
	m.group("head_banner", (0, 32, 6), parent="head")
	edge = (64, 0, 65, 40)
	m.element("head_banner", "banner_flag", (-6.5, 24, 5), (6.5, 50, 6), {
		"north": {"uv": [64, 0, 84, 40], "texture": 0}, "south": {"uv": [84, 0, 104, 40], "texture": 0},
		"east": {"uv": list(edge), "texture": 0}, "west": {"uv": list(edge), "texture": 0},
		"up": {"uv": [64, 0, 84, 1], "texture": 0}, "down": {"uv": [64, 39, 84, 40], "texture": 0}})
	m.cube("head_banner", "banner_bar", (-7.5, 50, 4.75), (7.5, 51, 5.75), "stick")
	m.cube("head_banner", "banner_pole", (-0.5, 30, 6), (0.5, 52, 7), "stick")
	m.cube("head_banner", "banner_clip", (-1, 31, 4.5), (1, 33, 7), "gold_block")
	# the drum at the belly
	m.group("drum", (0, 11, -8), parent="body")
	m.cube("drum", "shell", (-4.5, 7, -13), (4.5, 15, -3), {"side": "barrel_side", "up": "white_wool", "down": "barrel_bottom"})
	sides = {"north", "east", "south", "west"}
	m.cube("drum", "band_top", (-4.5, 13.5, -13), (4.5, 15, -3), "gold_block", inflate=0.25, only=sides)
	m.cube("drum", "band_bottom", (-4.5, 7, -13), (4.5, 8.5, -3), "gold_block", inflate=0.25, only=sides)
	write_entity(m)


# The war ghast's placeholder layout (as in gen_models.war_ghast).
GHAST_SCALE = 4
DECK_TOP, DECK_BOTTOM = -52, -56
CREW_Z = -28
CREW = {"crew_crossbow_left": -15, "crew_bombardier": 5, "crew_crossbow_right": 25.5}
CROSSBOW_HOLD = (60, 0, 0)
TNT = {"side": "tnt_side", "up": "tnt_top", "down": "tnt_bottom"}
CHAIN_SHEET = (96, 64)
# crew_gone shrinks the crew figures to this (as the placeholder does) once they jump off as real mobs
GONE = (0.01, 0.01, 0.01)


def chain(m, group, name, x, z, top, bottom, size=2):
	"""A vanilla chain from top to bottom at size times vanilla's: two crossed planes of the chain texture."""
	cx, cy = CHAIN_SHEET
	half, tile = 1.5 * size, 16 * size
	y, i = top, 0
	while y > bottom:
		low = max(bottom, y - tile)
		v = 16 * (y - low) / tile
		m.plane(group, f"{name}_{i}_a", (x - half, low, z), (x + half, y, z),
			{"north": (cx, cy, cx + 3, cy + v), "south": (cx, cy, cx + 3, cy + v)})
		m.plane(group, f"{name}_{i}_b", (x, low, z - half), (x, y, z + half),
			{"east": (cx + 3, cy, cx + 6, cy + v), "west": (cx + 3, cy, cx + 6, cy + v)})
		y, i = low, i + 1


def bombardier_kit(m, head, body, arm):
	"""Goggles, a satchel of fire charges on the back of the left hip, a charge in the throwing hand (piglin space)."""
	m.cube(head, "goggle_band", (-5.25, 28, -4.25), (5.25, 30, 4.25), "brown_wool")
	m.cube(head, "lens_right", (1, 28, -4.75), (4, 30, -4.25), "light_blue_stained_glass")
	m.cube(head, "lens_left", (-4, 28, -4.75), (-1, 30, -4.25), "light_blue_stained_glass")
	m.cube(body, "satchel", (-7, 13, 2.25), (-3, 18, 5.25), "brown_wool")
	m.cube(body, "satchel_charge", (-6, 18, 3), (-4, 20, 5), "magma_block")
	m.cube(arm, "charge", (4.5, 9, -1.5), (7.5, 12, 1.5), "magma_block")


def war_ghast():
	"""The current war ghast placeholder, regrouped: the four deck chains get their own bone, the crew figures get
	heads, and there are empty bones for the new chain harness and for extra crew. The red happy-ghast harness of the
	placeholder is left out: the chain harness replaces it."""
	m = gm.Model("war_ghast", size=256)
	m.sheet(gm.texture_from_jar("entity/piglin/piglin.png")[2], (0, 64))
	gm.import_java_entity(m, "ghast.json", (0, 0), scale=GHAST_SCALE,
		rename={f"tentacles_{i}": f"tentacle_{i}" for i in range(9)})
	_, _, shooting = gm.texture_from_jar("entity/ghast/ghast_shooting.png")
	m.sheet([row[32:64] for row in shooting[32:64]], (64, 64))
	m.group("face", (0, 32, -31.5), parent="body")
	m.plane("face", "shooting_face", (-32, 0, -31.5), (32, 64, -31.5), {"north": (64, 64, 96, 96)})
	_, _, links = gm.texture_from_jar("block/iron_chain.png")
	m.sheet(links, CHAIN_SHEET)

	# chains wrapped round the ghast, crossing to a ring on top: the artist's to make
	m.group("harness", (0, 64, 0), parent="body")
	# the four chains from the body's sides down to the deck's corners
	rim = DECK_TOP + 3
	m.group("chains", (0, 22, 0), parent="body")
	corners = [(sx, sz) for sx in (1, -1) for sz in (1, -1)]
	tags = {(sx, sz): f"{'right' if sx > 0 else 'left'}_{'back' if sz > 0 else 'front'}" for sx, sz in corners}
	for sx, sz in corners:
		chain(m, "chains", f"chain_{tags[sx, sz]}", 33 * sx, 20 * sz, 22, rim)

	# the deck: 4x4 blocks of crimson planks with a rim, gold brackets where the chains hold it, TNT on its back corners
	m.group("deck", (0, 0, 0), parent="body")
	for i in range(4):
		for j in range(4):
			x, z = -32 + 16 * i, -32 + 16 * j
			m.cube("deck", f"board_{i}_{j}", (x, DECK_BOTTOM, z), (x + 16, DECK_TOP, z + 16), "crimson_planks")
	m.cube("deck", "rim_front", (-32, DECK_TOP, -32), (32, rim, -30.5), "stripped_crimson_stem")
	m.cube("deck", "rim_back", (-32, DECK_TOP, 30.5), (32, rim, 32), "stripped_crimson_stem")
	m.cube("deck", "rim_right", (30.5, DECK_TOP, -30.5), (32, rim, 30.5), "stripped_crimson_stem")
	m.cube("deck", "rim_left", (-32, DECK_TOP, -30.5), (-30.5, rim, 30.5), "stripped_crimson_stem")
	for sx, sz in corners:
		m.cube("deck", f"bracket_{tags[sx, sz]}", (33 * sx - 2.5, DECK_TOP, 20 * sz - 2.5),
			(33 * sx + 2.5, rim + 1, 20 * sz + 2.5), "gold_block")
	m.cube("deck", "tnt_left", (-19, DECK_TOP, 22.5), (-11, DECK_TOP + 8, 30.5), TNT)
	m.cube("deck", "tnt_right", (21, DECK_TOP, 22.5), (29, DECK_TOP + 8, 30.5), TNT)

	# The crew: vanilla piglins on the deck's front. A figure's body and legs are its crew_* bone; its head (with the
	# ears) and its moving arm(s) are bones in it, named after the figure (bone names stay unique in the model).
	for crew, x in CREW.items():
		who = crew[len("crew_"):]
		bomber = who == "bombardier"
		head, arm = f"{who}_head", f"{who}_arm_throw" if bomber else f"{who}_arms"
		m.shift = (x, DECK_TOP, CREW_Z)
		gm.import_java_entity(m, "piglin.json", (0, 64), skip={"leftItem"},
			rename={"Body": crew, "head": head, "RightLeg": crew, "LeftLeg": crew, "RightArm": arm,
				"LeftArm": crew if bomber else arm, "leftear": f"{who}_ear_left", "rightear": f"{who}_ear_right"},
			parents={crew: "deck", head: crew, arm: crew}, rotations={} if bomber else {arm: CROSSBOW_HOLD})
		if bomber:
			bombardier_kit(m, head, crew, arm)
		else:
			m.cube(arm, "crossbow_grip", (-6, 11, -1), (6, 12.5, 1), "spruce_planks")
			m.cube(arm, "crossbow_stock", (-1, 4, -1), (1, 12.5, 1), "spruce_planks")
			m.cube(arm, "crossbow_bow", (-6, 4, -1.5), (6, 5.5, 0), "dark_oak_log")
			m.cube(arm, "crossbow_bolt", (-0.5, 2.5, -0.5), (0.5, 4, 0.5), "iron_block")
	m.shift = (0, 0, 0)
	# room for more figures (a drummer, torch bearers); crew_gone hides them too
	m.group("crew_extra", (0, DECK_TOP, CREW_Z), parent="deck")

	# explosives on chains, one each side between the deck's chains
	for side, sx, material in (("left", -1, TNT), ("right", 1, "magma_block")):
		m.group(f"bomb_{side}", (34 * sx, 22, 0), parent="body")
		chain(m, f"bomb_{side}", "chain", 34 * sx, 0, 22, -6)
		m.cube(f"bomb_{side}", "bomb", (34 * sx - 4, -14, -4), (34 * sx + 4, -6, 4), material)

	crews = [g["name"] for g in m.groups if g["name"].startswith("crew_")]
	m.animation("crew_gone", 0.1, "loop", {g: {"scale": [(0, GONE), (0.1, GONE)]} for g in crews})
	write_entity(m)


# Vanilla's piglin brute model is 2 blocks to the top of its head; the captain, a General Chungus of a brute, stands 4.
CAPTAIN_SCALE = 2.0


def rift_captain():
	"""A new boss: the vanilla piglin (brute texture) at 2x, a placeholder netherite longsword held forward in its right
	fist. The look to model is the movie's piglin general (netherite helm with a gold streak, a gold snout on the chest,
	rope-bound arms, a skull belt buckle)."""
	m = gm.Model("rift_captain")
	s = CAPTAIN_SCALE
	gm.import_java_entity(m, "piglin.json", (0, 0), scale=s, skip={"leftItem"}, rename=PIGLIN_BONES,
		texture="entity/piglin/piglin_brute.png",
		parents={"head": "body", "arm_right": "body", "arm_left": "body"})
	# the longsword in the right fist, pointing forward: a gold grip and guard, a netherite blade about 2.5 blocks long
	hand = (6 * s, 13.5 * s, 0)
	m.group("sword", hand, parent="arm_right")
	hx, hy = hand[0], hand[1]
	m.cube("sword", "grip", (hx - 1, hy - 1, -2), (hx + 1, hy + 1, 6), "gold_block")
	m.cube("sword", "guard", (hx - 1, hy - 4, -4), (hx + 1, hy + 4, -2), "gold_block")
	m.cube("sword", "blade", (hx - 0.5, hy - 2, -44), (hx + 0.5, hy + 2, -4), "netherite_block")
	write_entity(m)


# The helmet's flat colours (gen_models paints materials it has no block texture for from its PALETTE).
HELMET_COLOURS = {"strap": (74, 48, 30), "lens": (150, 212, 232), "brass": (214, 170, 64),
	"head_reference": (226, 150, 140)}


def bombardier_helmet():
	"""The Ghast Bombardier's goggles, a Java Block/Item model. Built in head pixels: the piglin's head
	(10 wide, 8 high, 8 deep) is the box x 3..13, y 4..12, z 4..12, its face at z = 4 (north) and its right at +X.
	display.head scales it 1.6x, which undoes the game's 0.625 for a head item, so one unit is one head pixel."""
	gm.PALETTE.update(HELMET_COLOURS)
	m = gm.Model("bombardier_helmet", size=32)
	m.group("helmet", (8, 8, 8))
	m.group("goggles", (8, 9, 4), parent="helmet")
	m.cube("goggles", "strap", (2.75, 8, 3.75), (13.25, 10, 12.25), "strap")
	m.cube("goggles", "lens_right", (9, 8, 3.25), (12, 10, 3.75), "lens")
	m.cube("goggles", "lens_left", (4, 8, 3.25), (7, 10, 3.75), "lens")
	m.cube("goggles", "bridge", (7, 8.5, 3.5), (9, 9.5, 3.75), "brass")
	# The piglin's bare head for scale: shown in Blockbench, never exported (export: false).
	ref = list(m._face("head_reference", "north", 2, 2, None))
	m.element("helmet", "piglin_head_reference", (3, 4, 4), (13, 12, 12),
		{f: {"uv": ref, "texture": 0} for f in ("north", "east", "south", "west", "up", "down")})
	m.elements[-1]["export"] = False
	stubs(m)
	save({"meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
		"name": m.name, "parent": "", "java_block_version": "1.21.11", "ambientocclusion": True,
		"front_gui_light": False, "visible_box": [1, 1, 0], "variable_placeholders": "",
		"variable_placeholder_buttons": [], "unhandled_root_fields": {},
		"resolution": {"width": m.size, "height": m.size},
		"elements": m.elements, "groups": m.groups,
		"outliner": [m._outliner(n) for n in m.roots],
		"textures": [texture(m, namespace="nether_rifts", folder="item", particle=True)],
		"display": {"head": {"scale": [1.6, 1.6, 1.6]}}}, m.name)


STARTERS = [war_drummer, war_ghast, rift_captain, bombardier_helmet]

# The bone trees the starters must have: {bone: {child bones}} with the roots under None.
TREES = {
	"war_drummer": {None: {"body", "leg_left", "leg_right"}, "body": {"head", "arm_left", "arm_right", "drum"},
		"head": {"ear_left", "ear_right", "head_banner"}},
	"war_ghast": {None: {"body"}, "body": {"face", *{f"tentacle_{i}" for i in range(9)}, "harness", "chains", "deck",
		"bomb_left", "bomb_right"}, "deck": {"crew_bombardier", "crew_crossbow_left", "crew_crossbow_right", "crew_extra"},
		"crew_bombardier": {"bombardier_head", "bombardier_arm_throw"},
		"bombardier_head": {"bombardier_ear_left", "bombardier_ear_right"},
		"crew_crossbow_left": {"crossbow_left_head", "crossbow_left_arms"},
		"crossbow_left_head": {"crossbow_left_ear_left", "crossbow_left_ear_right"},
		"crew_crossbow_right": {"crossbow_right_head", "crossbow_right_arms"},
		"crossbow_right_head": {"crossbow_right_ear_left", "crossbow_right_ear_right"}},
	"rift_captain": {None: {"body", "leg_left", "leg_right"}, "body": {"head", "arm_left", "arm_right"},
		"head": {"ear_left", "ear_right"}, "arm_right": {"sword"}},
	"bombardier_helmet": {None: {"helmet"}, "helmet": {"goggles"}},
}
BONE_NAME = re.compile(r"[a-z][a-z0-9]*(_[a-z0-9]+)*")


def check(path):
	"""Fails loudly if a starter isn't a sound project or doesn't match the brief."""
	data = json.loads(path.read_text())
	name = path.stem
	groups = {g["uuid"]: g for g in data["groups"]}
	elements = {e["uuid"]: e for e in data["elements"]}
	assert len(groups) == len(data["groups"]) and len(elements) == len(data["elements"]), f"{name}: duplicate uuids"
	seen_groups, seen_elements, tree = [], [], {}

	def walk(node, parent):
		assert node["uuid"] in groups, f"{name}: outliner group {node['uuid']} has no group"
		bone = groups[node["uuid"]]["name"]
		seen_groups.append(node["uuid"])
		tree.setdefault(parent, set()).add(bone)
		for child in node["children"]:
			if isinstance(child, str):
				assert child in elements, f"{name}: outliner element {child} doesn't exist"
				seen_elements.append(child)
			else:
				walk(child, bone)

	for node in data["outliner"]:
		if isinstance(node, str):
			assert node in elements, f"{name}: outliner element {node} doesn't exist"
			seen_elements.append(node)
		else:
			walk(node, None)
	assert sorted(seen_groups) == sorted(groups), f"{name}: groups missing from (or twice in) the outliner"
	assert sorted(seen_elements) == sorted(elements), f"{name}: elements missing from (or twice in) the outliner"
	names = [g["name"] for g in data["groups"]]
	assert len(set(names)) == len(names), f"{name}: bone names repeat"
	for bone in names:
		assert BONE_NAME.fullmatch(bone), f"{name}: bone {bone!r} isn't lowercase snake_case"
		for token in bone.split("_"):
			assert token in ("left", "right") or ("left" not in token and "right" not in token), \
				f"{name}: bone {bone!r} has a side not written as _left/_right"
	for parent, children in TREES[name].items():
		got = tree.get(parent, set())
		assert got == children, f"{name}: {parent or 'the root'} has {sorted(got)}, not {sorted(children)}"
	# animations
	animations = {a["name"]: a for a in data.get("animations", [])}
	assert len(animations) == len(data.get("animations", [])), f"{name}: animation names repeat"
	assert {a: (v["length"], v["loop"]) for a, v in animations.items()} == ANIMATIONS[name], f"{name}: animations differ"
	for a in animations.values():
		for key, animator in a["animators"].items():
			assert key in groups and groups[key]["name"] == animator["name"], f"{name}/{a['name']}: animator {key} isn't a group"
	if name == "war_ghast":
		gone = animations["crew_gone"]["animators"]
		crews = {g["name"] for g in data["groups"] if g["name"].startswith("crew_")}
		assert {v["name"] for v in gone.values()} == crews, f"{name}: crew_gone doesn't scale exactly {crews}"
		assert all(k["channel"] == "scale" for v in gone.values() for k in v["keyframes"])
		for a in animations.values():
			if a["name"] != "crew_gone":
				assert not a["animators"], f"{name}/{a['name']}: should be an empty stub"
		# the deck's underside stays 56 units under the ghast's feet, inside its 4x4-block footprint
		deck = next(g for g in data["groups"] if g["name"] == "deck")
		node = next(n for n in iter_nodes(data["outliner"]) if n["uuid"] == deck["uuid"])
		cubes = [elements[u] for u in subtree_elements(node)]
		bottom = min(c["from"][1] for c in cubes)
		assert bottom == DECK_BOTTOM, f"{name}: the deck's underside is at {bottom}"
		for c in cubes:
			if c["from"][1] == bottom:
				assert all(-32 <= c[k][i] <= 32 for k in ("from", "to") for i in (0, 2)), f"{name}: {c['name']} overhangs"
	elif name != "bombardier_helmet":
		for a in animations.values():
			assert not a["animators"], f"{name}/{a['name']}: should be an empty stub"
	# one embedded texture
	assert len(data["textures"]) == 1 and data["textures"][0]["source"].startswith("data:image/png;base64,")
	if data["meta"]["model_format"] == "java_block":
		assert "head" in data["display"]
		for e in data["elements"]:
			assert all(-16 <= v <= 32 for v in e["from"] + e["to"]), f"{name}: {e['name']} is outside -16..32"
		assert all(g["rotation"] == [0, 0, 0] for g in data["groups"])
	else:
		assert data["meta"] == {"format_version": "5.0", "model_format": "free", "box_uv": False}
	print("checked", path.relative_to(TOOLS.parent))


def iter_nodes(nodes):
	for n in nodes:
		if isinstance(n, dict):
			yield n
			yield from iter_nodes(n["children"])


def subtree_elements(node):
	for child in node["children"]:
		if isinstance(child, str):
			yield child
		else:
			yield from subtree_elements(child)


if __name__ == "__main__":
	for build in STARTERS:
		build()
	for build in STARTERS:
		check(OUT / f"{build.__name__}.bbmodel")
