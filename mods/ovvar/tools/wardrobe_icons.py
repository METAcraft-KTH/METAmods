#!/usr/bin/env python3
"""Draws the wardrobe screen's own action icons, once, checked in as the source of truth.

The action row used to borrow vanilla items (a hopper, shears, an armour stand) and grey glass for
whatever this server does not allow, which read as somebody else's icons and, in the glass's case,
as a bug. These are ours: one 16x16 sprite per action, in the screen's own palette, drawn here.
metacraft.ovvar.datagen.GeneratedAssets turns each into an item model `ovvar:wardrobe_<name>` and a
dimmed twin `ovvar:wardrobe_disabled_<name>` (half brightness, with a small red slash), which is
what an action this server refuses is drawn as -- the same icon, plainly out of use, instead of a
different item altogether.

Output: src/main/resources/art/ovvar/wardrobe_<name>.png, RGBA.
"""
from PIL import Image

PALETTE = {
	'.': (0, 0, 0, 0),            # transparent
	'o': (0x2B, 0x1F, 0x19, 255),  # outline: the same near-black ink the notices use
	'c': (0xF6, 0xEB, 0xD2, 255),  # cream: canvas, cloth, the mannequin
	's': (0xB9, 0xA8, 0x8C, 255),  # shadow on the cream
	'g': (0xE0, 0xA5, 0x33, 255),  # gold: anything that moves (arrows, thread)
	'y': (0xF6, 0xD4, 0x8A, 255),  # its highlight
	'm': (0xC8, 0xC8, 0xD0, 255),  # metal: the needle
}

# One grid per icon, 16x16. Kept as pixels rather than drawn from shapes: at this size every
# pixel is a decision, and a grid is the only honest way to see what the icon looks like.
ICONS = {
	# Taking a patch out of the stash: it comes up out of the pocket.
	'take_out': [
		'.......oo.......',
		'......oggo......',
		'.....oggggo.....',
		'....oggggggo....',
		'...ooogggooo....',
		'.....oggo.......',
		'.....oggo.......',
		'.....oggo.......',
		'.oooooooooooooo.',
		'.occcccccccccco.',
		'.ocssssssssssco.',
		'.oc..........co.',
		'.oc..........co.',
		'.occcccccccccco.',
		'.oooooooooooooo.',
		'................',
	],
	# Putting held patches in: they go down into it.
	'put_in': [
		'.....oggo.......',
		'.....oggo.......',
		'.....oggo.......',
		'...ooogggooo....',
		'....oggggggo....',
		'.....oggggo.....',
		'......oggo......',
		'.......oo.......',
		'.oooooooooooooo.',
		'.occcccccccccco.',
		'.ocssssssssssco.',
		'.oc..........co.',
		'.oc..........co.',
		'.occcccccccccco.',
		'.oooooooooooooo.',
		'................',
	],
	# Sewing on a stand: a needle, eye first, with its thread behind it.
	'sew': [
		'............oo..',
		'...........omo..',
		'..........omo...',
		'.........omo....',
		'........omo.....',
		'.......omo......',
		'......omo.......',
		'.....omo........',
		'....omo.........',
		'...omo..........',
		'..oo............',
		'.g..............',
		'..gg............',
		'....gg..gg......',
		'......gg..gg....',
		'...........gg...',
	],
	# Turning it: the same arrow, both ways round.
	'rotate_left': [
		'................',
		'................',
		'..........ooo...',
		'.........oogg...',
		'.......oogyggo..',
		'.....oogyggggo..',
		'...oogyggggggo..',
		'..oogggggggggo..',
		'..oogggggggggo..',
		'...ooggggggggo..',
		'.....ooggggggo..',
		'.......ooggggo..',
		'.........ooggo..',
		'..........ooo...',
		'................',
		'................',
	],
}


def mirrored(grid):
	return [row[::-1] for row in grid]


def centred(inner, width=16):
	"""One row: `inner` in the middle of a transparent row, so no row can be the wrong length."""
	pad = width - len(inner)
	if pad < 0:
		raise SystemExit(f"'{inner}' is wider than {width}")
	return '.' * (pad // 2) + inner + '.' * (pad - pad // 2)


def poke(grid, pixels, key):
	rows = [list(row) for row in grid]
	for x, y in pixels:
		rows[y][x] = key
	return [''.join(row) for row in rows]


# Seeing it in 3D: the ovve on a mannequin, with a hint of a turn either side of it. Built row by
# row rather than typed out, so the arms, the post and the base line up on the middle of the icon.
ICONS['see_3d'] = poke([
	centred(''),
	centred('oo'),
	centred('occo'),
	centred('oo'),
	centred('o' * 12),
	centred('o' + 'c' * 10 + 'o'),
	centred('o' * 12),
	centred('oo'),
	centred('occo'),
	centred('occo'),
	centred('occo'),
	centred('occo'),
	centred('o' * 8),
	centred('o' + 'c' * 6 + 'o'),
	centred('o' * 8),
	centred(''),
], [(1, 4), (0, 5), (0, 6), (1, 7), (14, 4), (15, 5), (15, 6), (14, 7)], 'g')

ICONS['rotate_right'] = mirrored(ICONS['rotate_left'])


def write(name, grid):
	if len(grid) != 16 or any(len(row) != 16 for row in grid):
		raise SystemExit(f"{name} is not 16x16")
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y, row in enumerate(grid):
		for x, key in enumerate(row):
			if key not in PALETTE:
				raise SystemExit(f"{name}: no colour '{key}'")
			img.putpixel((x, y), PALETTE[key])
	img.save(f"wardrobe_{name}.png")


def main():
	for name, grid in ICONS.items():
		write(name, grid)
	print(f"{len(ICONS)} icons")


if __name__ == "__main__":
	main()
