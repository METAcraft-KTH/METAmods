# Nether Rifts

While **rift mode** is on (a new world starts with it on), lighting a nether portal in the overworld opens a
flickering rift instead of a portal. Light it twice more and it tears open: a raid of nether mobs comes
through in waves, built on vanilla raids (small / medium / large portals raid like easy / normal / hard).
They drop a little quartz, gold, glowstone, nether wart and, from brutes, blaze rods. From wave 2 a Piglin
Drummer leads each wave, marching and drumming a beat every half second while a player is within 16 blocks: every 4th, its War Beat gives rift piglins within
12 blocks Speed and Strength, and its Shock Beat hurts players within 4 blocks and throws them back. Every
drummer keeps the same beat (the world clock's), so several drum and slam together. Drummers also walk with the
nether's own piglins: a piglin spawning naturally there brings one along 3% of the time (`mobs.drummer.natural_chance`).
That one drums while its piglins fight (a player, or a hoglin on a hunt) and buffs them; like any drummer it is a piglin
to piglins (they share anger with it), but keeps the gold truce and turns zombie in the overworld as they do, where a
rift's drummer ignores gold and never turns. No drummer barters or picks anything up. A drummer shows the armour it
wears as a piglin does (vanilla's piglin armour and textures in every vanilla material, leather dyed, enchanted pieces
glinting; no trims), and a nether drummer may spawn in gold armour as piglins do (each piece 10% of the time). In Large waves 5 and
later come chained ghasts: in a harness, a deck slung under them on chains with a crew of a Piglin Drummer, a Piglin
Grenadier (in goggles), a spear piglin and a crossbow piglin. Their gear shows on the crew (a golden spear in the
spear piglin's left hand, a crossbow on the crossbow piglin, and any armour on the spear piglin, for show only: it
doesn't protect the ghast). A chained ghast floats with its deck clear of the ground and at most 20
blocks over it (`mobs.bombardier.max_float_height`), and goes after players below it. The crew can be shot or struck
on the deck (each has its own health and a roomy hitbox; the deck's cover spares them 40% of the damage after armour,
`mobs.bombardier.crew.damage_taken`; the spear piglin's armour protects it; the wooden deck turns arrows from below),
and each one killed there takes its work with it: the spear piglin stabs the ghast into shooting fireballs, the
Grenadier drops the bombs, the crossbow piglin shoots bolts at players it can see (every 3 seconds,
`mobs.bombardier.crew.crossbow_every`), and the drummer drums in time with every Piglin Drummer, his War Beat
reaching the piglins under the ghast. Stabbed, the ghast lurches and shoots, its mouth open. They make bombing runs, the
ghast pitching into the dive and back as it climbs out, the Grenadier dropping magma bombs from the deck, each bomb's landing
spot marked on the ground a second before it hits; the bombs don't break blocks, or hurt rift mobs, armour stands
or loot on the ground. When a chained ghast dies, the crew left on it jump off where they stood, with the health they had left, and glide
down to fight on foot, the spear piglin wearing the ghast's armour: the Grenadier,
a piglin in goggles with a golden sword, then stops every 4 seconds to lob a magma bomb by hand at
a player 4 to 12 blocks off, the landing spot again marked a second ahead. The Piglin General, in a large raid's
last wave, gets its own red boss bar. Winning closes the rift and turns its obsidian frame to crying obsidian. While rift mode is on, eyes of ender can't be crafted.

`/rifts stabilise` ends rift mode: waiting rifts become working portals and portal-blocker's nether locks
lift. After that a crying-obsidian frame, lit with flint and steel, opens a rift on purpose (and crumbles
when the raid ends).

Players within 32 blocks of a new rift are warned in chat ("The realm is unstable", or for a crying-obsidian
rift that it woke one). The warning's `[Read more]` opens metacraft-booklet's Unstable Portals page (`metacraft:rifts`);
nether-rifts only compiles against Booklet, and leaves the link out on a server without metacraft-booklet.

Clients see a rift's blocks as a vanilla nether portal (swirl, particles, hum, and its overlay if you walk in;
on the server they stay rift blocks and teleport nobody). Over it, metacraft-shaders' item shader draws glass
cracking as pixel art fixed to the world (16 pixels a block): hairlines, then a web, then holes through which a
far-off nether fortress shows (a panorama captured in game, of Eskmorra: credits below) through a soft purple
haze drifting like the portal's swirl, the portal reddening as the rift tears. Players whose core shaders are
replaced (Iris, OptiFine) see the panel's raw sprite instead, which is all but transparent: the plain portal with
a glowing edge round it; unless their shaderpack went through ovvar's shader patcher, which puts the same drawing
into the pack (seen working in Complementary Reimagined and Unbound, BSL and Bliss).

Config: `config/nether-rifts.json5`; its `mobs` section tunes the Piglin Drummer and the chained ghast's bombing (ranges,
effect and fire durations, bomb power, the bombing run's heights, measured to the deck, and timing), and how often
and how far the Grenadier throws on foot. Commands: `/rifts …`, permission `metacraft.nether_rifts.admin`.

To see the mobs' looks: `/rifts showcase` lays out, in rows in front of you and facing you, a Piglin Drummer per
animation (each looping it), Piglin Drummers in every kind of armour with gear in hand or on the head, a Piglin
Grenadier and a vanilla piglin in iron, and two Chained Ghasts behind them (kit and diamond armour, one holding its
shooting face), each named. They stand still and silent, can't be hurt and never despawn. `/rifts showcase clear`
takes them away; a new `/rifts showcase` replaces the last.

Tests (game tests): `./gradlew :mods:nether-rifts:test` also runs the tests of the modules it depends on, and
metacraft-lib's fail in a fresh checkout until its data is generated, so run
`./gradlew :mods:metacraft-lib:runDatagen` once first. Or skip the dependencies' tests:

```
./gradlew :mods:nether-rifts:test -x :mods:portal-blocker:test -x :mods:portal-opening:test -x :mods:simple-custom-features:test -x :mods:metacraft-zones:test -x :mods:metacraft-core:test -x :mods:metacraft-config:test -x :mods:metacraft-lib:test
```

Screenshots: `./gradlew :mods:nether-rifts:runClientGameTest` (opens a window).

The mob models in `src/main/resources/model/nether_rifts/`, the Piglin Drummer and the Chained Ghast with its crew,
are by Millia, brought in by re-runnable imports that
fit them to how BIL plays them. The Chained Ghast's source files are in `blockbench/delivered/` (`blockbench/README.md`). `python3 tools/import_drummer.py <artist's .bbmodel>` does the Piglin Drummer: adds
the gear markers and the armour pieces (vanilla's piglin armour cubes, in the bones they cover), and writes the
armour's item models for each vanilla material (`assets/nether_rifts/{models/item,items}/war_drummer/armour/`) with an
item-atlas file that lets them use vanilla's own armour textures; it reads the materials from the local Minecraft jar.
`python3 tools/import_ghast.py blockbench/delivered/war_ghast.bbmodel blockbench/delivered/war_ghast.png` does the
Chained Ghast: takes the texture PNG, puts the ghast's body on the entity's box (it prints the deck's drop,
`WarGhast.DECK_DROP`), renames the crew's bones (BIL turns any bone named `head…` with the mob's look), folds the overlay
layers, the goggles and the bomb pile into their parts, adds the markers the crew jump off at (the artist marks where
the gear goes), the spear piglin's armour (`…/war_ghast/armour/`), and gives each animation idle's tracks for the
bones it doesn't move (BIL plays one animation at a time, over every bone); both share `tools/piglin_armour.py`.
`tools/gen_models.py` builds placeholder models from vanilla geometry; none are in use now. The Grenadier on foot has no model of its own: clients see a
vanilla piglin wearing the `nether_rifts:bombardier_helmet` item (a Polymer item, never dropped or craftable),
whose item model `assets/nether_rifts/models/item/bombardier_helmet.json` is a placeholder of vanilla block
textures, drawn on the head by its `head` display; an artist's model can replace that file as it is.

The panorama looks into **Eskmorra - Nether Fortress** by Bluespartan, built by zombiespartan456 and shiiiner
(2014), <https://www.planetminecraft.com/project/eskmorra---nether-fortress-download/>. Its terms, from that page:
"If you want to use the build for anything [go] ahead. but DO NOT take credit, repost it, claim [it] was made by
someone else or just for you." So: credit to them, and the world itself isn't in this repo. To take the panorama
again, download the world from that page into an empty folder outside the repo, then point the capture at its
`region` folder (it copies the region files into the test server's nether, over a fixed-seed nether, and turns
the build's biomes into nether wastes):

```
./gradlew :mods:nether-rifts:runClientGameTest -PcapturePanorama "-PpanoramaWorld=/path/to/eskmorra download/region"
python3 tools/gen_panorama.py
```

The first writes `tools/panorama/` (1024 px a face, not committed: only the sprite made from it is; from over the
lava sea at x 394, y 42, z 672; add
`-PpanoramaSpots="x,y,z;x,y,z"` to try other spots, each into its own folder); the second turns it into the
panel's sprite (512 px a face, its alpha all but zero so the fallback shows nothing; `python3
tools/gen_panorama.py alpha` re-stamps only that).
