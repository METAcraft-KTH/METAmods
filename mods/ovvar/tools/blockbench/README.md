# The Ovvar Blockbench plugin

Draw a patch, sew it onto an ovve, and see it on a player exactly as the game draws it — no
Minecraft running. Then export it into the checkout, ready to commit.

## Install

1. Blockbench **5.0 or newer**, the desktop app (the web version cannot read your checkout).
2. *File › Plugins › Load Plugin from File…* → `mods/ovvar/tools/blockbench/ovvar.js`.
3. In the checkout, once: `./gradlew :mods:ovvar:runDatagen`. The plugin reads
   `mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json`, which that writes. Nothing under
   `src/main/generated/` is committed, so a fresh clone has to run it before the plugin will open.

## Point it at a checkout

*File › New › Ovvar*. Blockbench opens its own Project window first; close it, and the plugin's
question is behind it: type the path of the folder holding `mods/ovvar`. It is remembered between
sessions.

Blockbench will ask for permission to **access a folder**, naming that path. Choose *Always allow
for this plugin*. The permission is per folder: a second checkout asks again, and the plugin can
touch nothing outside the folder you named — that is Blockbench's scoped file system, not a
promise of ours.

If the manifest is not there the plugin says so and names the command to run.

## Workflow

- **Chapter** and **Zipped down** pick the garment. A chapter with nothing to roll down has the
  checkbox greyed.
- **Sew…** picks a patch and a spot. Spots are grouped by half. A seat patch has one place to go
  and nothing else may go there; everything else may overlap, which is the point of an ovve —
  the big back cell is meant to be worn under the two small ones.
- **×** beside a placement unpicks it.
- **New patch…** makes a blank art and a catalogue entry in this project. Ids are
  `[a-z0-9_]+` and unique; ordinary art is an even 6–16 px each way (12×12 is the usual), a seat
  patch is 16 wide and 8–12 tall. A blank patch draws nothing and says so in the panel until you
  have painted on it.
- **Add size…** draws a patch again at 8×8, 12×12 or 16×16 — for when the automatic shrink gets
  an outline or a letter wrong. It starts you from the shrink's own answer.
- Paint in Blockbench's Paint mode. The ovve redraws on every finished stroke.
- **Export to repo** writes every art you drew or changed to
  `mods/ovvar/src/main/resources/art/ovvar/patches/`, warns before overwriting anything, and then
  shows the `Patches.java` line(s) to paste. New entries go **last** in the `ALL` list: a design's
  instant code is its position, so inserting one in the middle repaints everything already sewn.
- Then, in the checkout: re-run `./gradlew :mods:ovvar:runDatagen`, and commit the new art under
  `mods/ovvar/src/main/resources/art/ovvar/patches/` together with the `Patches.java` line. Nothing
  under `src/main/generated/` is ever committed — datagen rewrites it.

## What the preview is and is not

The base cloth is drawn as the chapter's own texture, unsqueezed; a patch is drawn with the game's
squeeze baked into the pixels, the way `GeneratedAssets.placedWrapped` bakes it. The two only
disagree where a patch hangs over onto a neighbouring face — a patch bends round the chest's
corner here as it does in game, while the cloth behind it does not.

One thing the preview shows that the game does not: a seam of cloth down the middle of a seat
patch. The two leg boxes are each half a unit proud and so overlap, and Blockbench has to pick one
of the two coplanar back faces to draw; in game each leg's inner rim is hidden behind the other
leg. The pixels are the same — only the seam is ours.

The plugin does not do dye colours and the instant channel, the wardrobe paper doll, armour worn
over the ovve, posing, or editing the chapter garments themselves (they are only displayed).

## Tests

```sh
node mods/ovvar/tools/blockbench/test.js <checkout>
```

Node 20 or newer; no dependencies. It builds the plugin the way `build.sh` does and runs the
composition half against the committed textures: every placement texture, every trim, every
generated art size, the chapter layers, 512 samples of the squeeze. The Blockbench half — the
format, the cubes, the panel, the dialogs — cannot be unit-tested and is not covered. CI does not
run this: run it by hand, and always before committing a rebuilt `ovvar.js`.

## Building

```sh
mods/ovvar/tools/blockbench/build.sh
```

`ovvar.js` is `cat src/*.js`, nothing else: no npm, no bundler, so a diff of the built file is a
diff of the sources. Commit both. Blockbench caches a file plugin's code for the session — after a
rebuild, reload it from *File › Plugins* rather than expecting a restart to pick it up.

## How it stays honest

The plugin has no copy of `Spot`, `Patches` or `Chapter`: datagen writes them out as
`manifest.json` and a game test (`BlockbenchManifestTests`) holds that file to the enums field for
field, `Spot.anchored` included. A cell moved in Java is a changed manifest, and a changed
manifest is a changed plugin — or a failing test.
