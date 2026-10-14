"""Vanilla's piglin armour on an artist's piglin, for the imports (tools/import_drummer.py, tools/import_ghast.py).

- add_armour puts the armour pieces in a .bbmodel where missing: vanilla's piglin armour cubes, each in a group inside
  the piglin bone it covers, textured iron in the file (a second texture). mob/ArmourPieces.java shows them.
- write_armour_assets writes, for every vanilla armour material, item models of those cubes as BIL builds a bone's
  model, onto vanilla's own armour textures (assets/nether_rifts/{models/item,items}/<model>/armour/), and an atlas
  file that puts those textures where item models can reach them.
The textures and the materials come from the Minecraft jar (MC_JAR, else loom's).

A piglin's bones are given by role (head, body, right_arm, left_arm, right_leg, left_leg) mapped to the file's group
names; the drummer's are named after their roles."""
import base64
import glob
import io
import json
import os
import shutil
import uuid
import zipfile
from pathlib import Path

from PIL import Image

MODULE = Path(__file__).resolve().parent.parent
ASSETS = MODULE / "src/main/resources/assets"
MC_VERSION = "26.3"
ROLES = ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")


def find(nodes, u):
	"""The outliner list holding the node with uuid u, and its index there; None if it isn't in it."""
	for i, n in enumerate(nodes):
		if isinstance(n, dict):
			if n["uuid"] == u:
				return nodes, i
			found = find(n["children"], u)
			if found:
				return found
	return None


# Vanilla's piglin armour (AbstractPiglinModel.createArmorMeshSet: HumanoidModel's armour meshes without ears; its
# deformations from LayerDefinitions): helmet, chestplate and boots grown 1.02, the hat 0.5 more, leggings 0.5 and their
# legs 0.1 less. Boxes are vanilla's (x, y, z, w, h, d) about the part's pivot, y down, on a 64 x 32 texture; the left
# limbs are mirrored. (group, piglin role, layer, [(box, grow, uv, mirror)], anchor)
# A piece lines up with its bone at the end its texture draws (the drummer's body and limbs are 11 px to vanilla's 12,
# so there it overhangs 1 px at the other). "pivot" keeps vanilla's place about the joint (the helmet, chestplate, arms
# and leggings' legs hang from it); "bottom" sits the cube on the bottom of the bone's cubes (the boots on the ground,
# the leggings' waist on the hips: their textures draw only the lower half).
LEG = (-2, 0, -2, 4, 12, 4)
BODY = (-4, 0, -2, 8, 12, 4)
ARMOUR = [
	("armor_head", "head", "humanoid", [((-4, -8, -4, 8, 8, 8), 1.02, (0, 0), False), ((-4, -8, -4, 8, 8, 8), 1.52, (32, 0), False)], "pivot"),
	("armor_chest", "body", "humanoid", [(BODY, 1.02, (16, 16), False)], "pivot"),
	("armor_chest_right", "right_arm", "humanoid", [((-3, -2, -2, 4, 12, 4), 1.02, (40, 16), False)], "pivot"),
	("armor_chest_left", "left_arm", "humanoid", [((-1, -2, -2, 4, 12, 4), 1.02, (40, 16), True)], "pivot"),
	("armor_legs", "body", "humanoid_leggings", [(BODY, 0.5, (16, 16), False)], "bottom"),
	("armor_legs_right", "right_leg", "humanoid_leggings", [(LEG, 0.4, (0, 16), False)], "pivot"),
	("armor_legs_left", "left_leg", "humanoid_leggings", [(LEG, 0.4, (0, 16), True)], "pivot"),
	("armor_feet_right", "right_leg", "humanoid", [(LEG, 0.92, (0, 16), False)], "bottom"),
	("armor_feet_left", "left_leg", "humanoid", [(LEG, 0.92, (0, 16), True)], "bottom"),
]
ARMOUR_W, ARMOUR_H = 64, 32
# a second layer (leather's overlay) is drawn this much further out than the first, so the two never fight
LAYER_GAP = 0.05


def mc_jar():
	jar = os.environ.get("MC_JAR") or next(iter(sorted(glob.glob(os.path.expanduser(
		f"~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/{MC_VERSION}/*.jar")))), None)
	assert jar, "no Minecraft jar: set MC_JAR"
	return zipfile.ZipFile(jar)


def box_faces(uv, size, mirror, scale=(1, 1)):
	"""Blockbench's box UV (js/outliner/cube.js updateUV), so the file matches what Blockbench would make of it."""
	w, h, d = size
	faces = [("east", [0, d], [d, h]), ("west", [d + w, d], [d, h]), ("up", [d + w, d], [-w, -d]),
		("down", [d + 2 * w, 0], [-w, d]), ("south", [2 * d + w, d], [w, h]), ("north", [d, d], [w, h])]
	if mirror:
		faces = [(n, [f[0] + s[0], f[1]], [-s[0], s[1]]) for n, f, s in faces]
		(e, ef, es), (wn, wf, ws) = faces[0], faces[1]
		faces[0], faces[1] = (e, wf, ws), (wn, ef, es)
	return {n: [(f[0] + uv[0]) * scale[0], (f[1] + uv[1]) * scale[1], (f[0] + s[0] + uv[0]) * scale[0], (f[1] + s[1] + uv[1]) * scale[1]]
		for n, f, s in faces}


def armour_cubes(d, bones):
	"""Each piece's cubes in the file's space (x mirrored and y up from vanilla's), with its group's origin."""
	groups = {g["name"]: g for g in d["groups"]}
	elements = {e["uuid"]: e for e in d["elements"]}
	pieces = []
	for name, role, layer, cubes, anchor in ARMOUR:
		bone = bones[role]
		pivot = groups[bone]["origin"]
		lst, i = find(d["outliner"], groups[bone]["uuid"])
		bottom = min(elements[c]["from"][1] for c in lst[i]["children"] if isinstance(c, str))
		out = []
		for (x, y, z, w, h, dd), grow, uv, mirror in cubes:
			lo = [pivot[0] - (x + w), pivot[1] - (y + h), pivot[2] + z]
			if anchor == "bottom":
				lo[1] = bottom
			out.append((lo, [lo[0] + w, lo[1] + h, lo[2] + dd], grow, uv, mirror, (w, h, dd)))
		pieces.append((name, bone, layer, list(pivot), out))
	return pieces


def add_group(d, model, name, parent, origin, template):
	"""An empty group (a marker, or a piece's group to fill) inside the group named parent; returns its outliner node."""
	group = dict(template, name=name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, f"{model}/{name}")), origin=list(origin),
		rotation=[0, 0, 0], children=[])
	d["groups"].append(group)
	plst, j = find(d["outliner"], next(g["uuid"] for g in d["groups"] if g["name"] == parent))
	node = {"uuid": group["uuid"], "isOpen": False, "children": []}
	plst[j]["children"].append(node)
	return node


def add_armour(d, jar, model, bones):
	if any(g["name"] == ARMOUR[0][0] for g in d["groups"]):
		return
	# the file shows iron: vanilla's humanoid layer, and its leggings layer beside it
	tex = Image.new("RGBA", (2 * ARMOUR_W, ARMOUR_H), (0, 0, 0, 0))
	for i, layer in enumerate(("humanoid", "humanoid_leggings")):
		tex.paste(Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/entity/equipment/{layer}/iron.png"))).convert("RGBA"), (i * ARMOUR_W, 0))
	buf = io.BytesIO()
	tex.save(buf, "PNG", optimize=True)
	first = d["textures"][0]
	index = len(d["textures"])
	d["textures"].append(dict(first, name="armour_iron.png", relative_path="armour_iron.png", id=str(index),
		width=2 * ARMOUR_W, height=ARMOUR_H, uv_width=2 * ARMOUR_W, uv_height=ARMOUR_H, use_as_default=False,
		uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, f"{model}/armour_iron")), source="data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()))
	d["textures"][index].pop("sync_to_project", None)
	template = next(g for g in d["groups"] if g["name"] == bones["head"])
	for name, bone, layer, pivot, cubes in armour_cubes(d, bones):
		children = []
		for n, (lo, hi, grow, uv, mirror, size) in enumerate(cubes):
			offset = [uv[0] + (ARMOUR_W if layer == "humanoid_leggings" else 0), uv[1]]
			cube = {"name": name, "box_uv": True, "render_order": "default", "locked": False, "export": True, "scope": 0,
				"allow_mirror_modeling": True, "from": lo, "to": hi, "autouv": 0, "color": 0, "inflate": grow, "origin": pivot,
				"uv_offset": offset, "faces": {f: {"uv": uv4, "texture": index} for f, uv4 in box_faces(offset, size, mirror).items()},
				"type": "cube", "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{model}/{name}/{n}"))}
			if mirror:
				cube["mirror_uv"] = True
			d["elements"].append(cube)
			children.append(cube["uuid"])
		add_group(d, model, name, bone, pivot, template)["children"] = children


def number_list(v):
	return [round(x, 4) for x in v]


def write_armour_assets(d, jar, model, bones):
	"""Per vanilla material, an item model of each piece as BIL builds a bone's (its cubes about the group's origin, moved
	by 8; BIL scales a bone only past 24 px), on vanilla's textures for that material's layers: layer i is texture #i
	and tint i (tint 0 the dye, the others custom model data colours). ArmourPieces colours them."""
	models_dir = ASSETS / f"nether_rifts/models/item/{model}/armour"
	items_dir = ASSETS / f"nether_rifts/items/{model}/armour"
	shutil.rmtree(models_dir, ignore_errors=True)
	shutil.rmtree(items_dir, ignore_errors=True)
	pieces = armour_cubes(d, bones)
	materials = 0
	for entry in sorted(n for n in jar.namelist() if n.startswith("assets/minecraft/equipment/") and n.endswith(".json")):
		material = Path(entry).stem
		layers = json.loads(jar.read(entry))["layers"]
		for name, bone, layer, pivot, cubes in pieces:
			if layer not in layers:
				continue
			# BIL shrinks a bone whose cubes (with its origin) span more than 24 px, and these models would no longer match
			lo_all = [min(0, *(c[0][a] - c[2] - pivot[a] for c in cubes)) for a in range(3)]
			hi_all = [max(0, *(c[1][a] + c[2] + (len(layers[layer]) - 1) * LAYER_GAP - pivot[a] for c in cubes)) for a in range(3)]
			assert max(h - l for l, h in zip(lo_all, hi_all)) <= 24, name
			textures, elements = {}, []
			for i, info in enumerate(layers[layer]):
				ns, path = info["texture"].split(":")
				textures[str(i)] = f"{ns}:entity/equipment/{layer}/{path}"
				for lo, hi, grow, uv, mirror, size in cubes:
					g = grow + i * LAYER_GAP
					faces = box_faces(uv, size, mirror, (16 / ARMOUR_W, 16 / ARMOUR_H))
					elements.append({"from": number_list([x - g - p + 8 for x, p in zip(lo, pivot)]),
						"to": number_list([x + g - p + 8 for x, p in zip(hi, pivot)]),
						"faces": {f: {"texture": f"#{i}", "tintindex": i, "uv": number_list(uv4)} for f, uv4 in faces.items()}})
			textures["particle"] = textures["0"]
			item_model = {"textures": textures, "elements": elements, "display": {"head": {"rotation": [0, 180, 0]}}}
			tints = [{"type": "minecraft:dye", "default": -1}] + [{"type": "minecraft:custom_model_data", "index": i - 1, "default": -1}
				for i in range(1, len(layers[layer]))]
			model_path = models_dir / material / f"{name}.json"
			model_path.parent.mkdir(parents=True, exist_ok=True)
			model_path.write_text(json.dumps(item_model, indent="\t") + "\n")
			item_path = items_dir / material / f"{name}.json"
			item_path.parent.mkdir(parents=True, exist_ok=True)
			item_path.write_text(json.dumps({"model": {"type": "minecraft:model",
				"model": f"nether_rifts:item/{model}/armour/{material}/{name}", "tints": tints}}, indent="\t") + "\n")
		materials += any(p[2] in layers for p in pieces)
	# item models read the item and block atlases only: vanilla's armour textures join the item atlas (the client adds
	# every pack's atlas sources together, and Polymer merges ours with other mods')
	atlas = ASSETS / "minecraft/atlases/items.json"
	atlas.parent.mkdir(parents=True, exist_ok=True)
	atlas.write_text(json.dumps({"sources": [{"type": "minecraft:directory", "source": f"entity/equipment/{layer}",
		"prefix": f"entity/equipment/{layer}/"} for layer in ("humanoid", "humanoid_leggings")]}, indent="\t") + "\n")
	print(model, "armour models for", materials, "materials")
