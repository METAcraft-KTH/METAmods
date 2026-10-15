# Starter projects for the rift mobs

Each file in `starters/` is ready to open in Blockbench 5 (File > Open). The bones, their names, the texture and the animations
are already set up. The geometry inside is a rough blockout with the right size and positions. Replace it with your
own model, then animate. Sizes, timings and the look we are after are in the spec sheet (`SPEC.md` in the kit).
Reference renders are in `references/`, and the goggles concepts are in `concept/`.

| File | What it is | What to do |
| --- | --- | --- |
| `war_drummer.bbmodel` | The War Drummer, a piglin with a war drum at its belly. The blockout is the current placeholder, without drumsticks: it beats the drum by hand. | Remodel and animate. `head_banner` (the black snout banner) may go; we haven't decided. |
| `war_ghast.bbmodel` | The war ghast: a ghast with a wooden deck hung under it on four chains, and a crew of three piglins on the deck. | Remodel and animate. Model the chain harness in `harness` (chains round the ghast, crossing to a ring on top). Extra crew (a drummer, torch bearers) go in `crew_extra`. `crew_gone` is already filled in: keep it. |
| `rift_captain.bbmodel` | The Rift Captain, a new boss: a 4-block-tall piglin general with a netherite longsword, after the movie's General Chungus. | New mob: model and animate. The blockout is a vanilla brute at 2x with a placeholder sword. |
| `bombardier_helmet.bbmodel` | The Ghast Bombardier's goggles, worn on a piglin's head. A Java Block/Item model. | Item: model and texture. No animations. |

## Rules

- Keep every bone name and every animation name exactly as they are. The code plays animations by name, and a
  renamed animation never plays.
- You may add bones inside the given ones freely. Don't move a given bone to a different parent.
- +X is the mob's right side. Blockbench's vanilla entity templates are mirrored, so don't paste them in
  un-mirrored.
- 16 units = 1 block. The feet are at y = 0. The mob faces -Z (north).
- Animations that play at the same time must touch different bones. `walk` moves only the legs and the body.
  Beats, attacks and throws move only the arms, the head and held items.
- Each stub's length is the maximum length. Don't make an animation longer; shorter is fine.
- Death animations end with the mob on the ground. They are set to "hold", so the last frame stays.
- One texture atlas per model, embedded in the project.
- In `war_ghast`, the deck's underside must stay 56 units (3.5 blocks) below the ghast's feet and inside the
  4 x 4 block footprint.

## The helmet

- One unit is one pixel of the piglin's head. The head is 10 wide, 8 high and 8 deep, and the box
  `piglin_head_reference` shows it. That box is set not to export; leave it so.
- The face is the north side (z = 4). The `head` display slot scales the model so it sits on the head; keep it.
- The texture exports as `nether_rifts:item/bombardier_helmet`.

## Delivering

Send back the `.bbmodel` files. For the helmet, also send File > Export > Block/Item Model (the JSON file) and its
texture PNG.

## Delivered

What the artist sent back is kept in `delivered/`, as sent (the starters above stay as they were handed out). The
module's README says how each is brought in.

| File | What it is | Brought in by |
| --- | --- | --- |
| `war_ghast.bbmodel`, `war_ghast.png` | The Chained Ghast (2026-10-15): the crew in the deck, a bomb pile and a chain harness, the gear markers (`item_right`, `item_left`, `item_head`), and the animations idle, shoot, drop_bomb, dive, climb and crew_gone. The PNG is the latest texture (the file's own is a little older). | `tools/import_ghast.py` |
| `bombardier_goggles.bbmodel`, `.png` | The Grenadier's goggles, worn on a piglin's head (in place of `bombardier_helmet.bbmodel`). | `tools/bbmodel_to_item.py` |
| `bombardier_arm.bbmodel`, `.png` | The Grenadier's gloved right arm and sleeve, with the magma bomb in its fist, held over a piglin's arm. | `tools/bbmodel_to_item.py` |

Earlier deliveries (the Piglin Drummer, the first Chained Ghast) came in before this folder and aren't kept.
