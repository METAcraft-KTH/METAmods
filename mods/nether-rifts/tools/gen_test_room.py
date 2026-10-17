"""Writes the game tests' room: 24 wide, 16 tall, 24 deep, a stone floor and air above.
Run from the module: python3 tools/gen_test_room.py"""
from pathlib import Path

W, H, D = 24, 16, 24
OUT = Path(__file__).resolve().parent.parent / "src/test/resources/structures/data/nether_rifts/structure/rift_room.snbt"

lines = ["{", f"\tsize: [ {W}, {H}, {D} ],", "\tentities: [],", "\tblocks: ["]
blocks = []
for x in range(W):
	for y in range(H):
		for z in range(D):
			blocks.append(f"\t\t{{ pos: [ {x}, {y}, {z} ], state: {1 if y == 0 else 0} }}")
lines.append(",\n".join(blocks))
lines += ["\t],", "\tpalette: [", '\t\t{ Name: "minecraft:air" },', '\t\t{ Name: "minecraft:stone" }', "\t],", "\tDataVersion: 4189", "}"]
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(lines) + "\n")
print(f"wrote {OUT}")
