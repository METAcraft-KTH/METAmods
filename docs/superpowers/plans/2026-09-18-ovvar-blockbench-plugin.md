# Ovvar Blockbench Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vlad draws a patch in Blockbench, sews it onto an ovve, sees it on a player model exactly as the game draws it (no Minecraft running), and exports it into the checkout ready to commit.

**Architecture:** A datagen provider (`datagen/BlockbenchManifest.java`) writes `src/main/generated/ovvar/blockbench/manifest.json` plus the generated-size art PNGs, so `Spot`/`Patches`/`Chapter` are published as data and CI's `runDatagen` catches drift. A Blockbench desktop plugin (`mods/ovvar/tools/blockbench/ovvar.js`, built by `cat src/*.js`) reads that manifest off a checkout, ports `GeneratedAssets.placed`/`placedWrapped`/`withLeft`/`flattened` and `Tex.downscaled` into plain functions, composes the two garment textures per half, and hangs them on six inflated box-UV cubes. The composition half has no Blockbench types in it, so Node's built-in test runner drives it against the committed goldens.

**Tech Stack:** Blockbench 5.x plugin API (desktop variant; installed here as `/Applications/Blockbench.app`, version 5.1.6), plain ES2020 JS, Node ≥ 20 built-in test runner (`node:test`) for tests, Java 25 datagen provider in the mod.

**Spec:** `docs/superpowers/specs/2026-09-18-ovvar-blockbench-plugin-design.md`

## Global Constraints

- No npm dependencies anywhere in `tools/blockbench` — not for the PNG codec, not for the tests; Node's built-ins only.
- The plugin is a single built file `mods/ovvar/tools/blockbench/ovvar.js` = `cat src/*.js` in lexicographic order; `build.sh` does exactly that and nothing else.
- Composition code (`00-manifest.js`, `10-compose.js`) has no Blockbench types: no `Texture`, `Cube`, `Blockbench`, `Project`, `Canvas`, `Undo`, `Vue`. Everything it touches is passed in.
- Commits carry **NO** `Co-Authored-By` trailer (owner's rule) and no `🤖 Generated with` line.
- Commit messages in the repo's style: lowercase `ovvar: <what changed, as a sentence>`.
- Java style: tabs for indentation, dense explanatory comments in the voice of the neighbouring files, `./gradlew :mods:ovvar:checkstyleMain` must pass (the one rule is "indent must use tab characters").
- The PolymITer chapters (`DATA_POLYMITER`, `IT_POLYMITER`) are excluded from the manifest — reference textures due for removal.
- "Game-exact" means pixel-identical to the committed PNGs under `mods/ovvar/src/main/generated`, outside the eight marker texels listed in the manifest's `markerTexels`.
- Every Blockbench API fact used below has a "verify in Blockbench 5 console" step before it is relied on. Prefer the installed app's own source (extracted in Task 0, step 1) over the wiki.
- Gradle is run by the executor only where a step says so; `./gradlew :mods:ovvar:runDatagen` and `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:ovvar:runServer` are the only two Gradle invocations in this plan.
- All paths below are relative to the worktree root `/Users/winsty-dev/Personal Projects/MinecraftModding/Metacraft/METAmods/.claude/worktrees/rivals` unless absolute.

---

## File structure

| file | responsibility |
|---|---|
| `mods/ovvar/src/main/java/metacraft/ovvar/datagen/BlockbenchManifest.java` (new) | writes `manifest.json` + `art/*.png` |
| `mods/ovvar/src/main/java/metacraft/ovvar/datagen/GeneratedAssets.java` | five private constants become package-private so the manifest can quote them |
| `mods/ovvar/src/main/java/metacraft/ovvar/datagen/OvvarDataGenerator.java` | registers the new provider |
| `mods/ovvar/src/main/java/metacraft/ovvar/gametest/BlockbenchManifestTests.java` (new) | manifest vs `Spot`/`Patches`/`Chapter`/`Spot.anchored` |
| `mods/ovvar/src/main/resources/fabric.mod.json` | adds the new gametest entrypoint |
| `mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json` (generated) | the manifest |
| `mods/ovvar/src/main/generated/ovvar/blockbench/art/it_8x8.png` (generated) | the one generated-size art today |
| `mods/ovvar/tools/blockbench/src/00-manifest.js` (new) | namespace, PNG codec, io, manifest loading |
| `mods/ovvar/tools/blockbench/src/10-compose.js` (new) | image ops + the whole port + `compose` |
| `mods/ovvar/tools/blockbench/src/20-model.js` (new) | format, cubes, textures |
| `mods/ovvar/tools/blockbench/src/30-panel.js` (new) | sidebar, dialogs, export |
| `mods/ovvar/tools/blockbench/src/90-plugin.js` (new) | `Plugin.register`, events, unload |
| `mods/ovvar/tools/blockbench/build.sh` (new) | `cat src/*.js > ovvar.js` |
| `mods/ovvar/tools/blockbench/ovvar.js` (new, committed) | the built plugin |
| `mods/ovvar/tools/blockbench/test.js` (new) | `node test.js <checkout>` |
| `mods/ovvar/tools/blockbench/README.md` (new) | install, permissions, workflow, tests |
| `mods/ovvar/README.md` | a short "Blockbench plugin" section |

---

## Vocabulary these tasks share

Fixed names, used identically in every task below. Nothing here is negotiable between tasks.

- **image** — `{w: number, h: number, data: Uint8Array}` with `data.length === w * h * 4`, RGBA, row-major, **not** premultiplied. An alpha-0 pixel keeps its RGB (Java's `Tex` does, and `placed` copies any texel whose packed ARGB is non-zero, so a green pixel at alpha 0 *is* copied — this must survive the round trip).
- **argb** — a packed integer `(a << 24 | r << 16 | g << 8 | b) >>> 0`. `OVVAR.tex.get` returns one; `OVVAR.tex.set` takes one.
- **manifest** — the parsed `manifest.json`, always the variable `m`.
- **cell** — a manifest `cells[]` entry (a `Spot`). **patch** — a `patches[]` entry. **art** — an `arts[]` entry inside a patch.
- **placement** — `{cell: "<cell id>", patch: "<patch id>"}`.
- **design** — `{chapter: "<chapter id>", nercabbad: boolean, placements: placement[]}`.
- **piece** — `"top"` or `"bottom"`.
- **ctx** — `{m, art(file) -> image, base(chapterId, piece, nercabbad) -> image}`; the only thing `compose` needs from the world.
- **A / B** — the right-hand and left-hand garment texture of one piece. A is bound to the body cube and the right limb cubes, B to the `mirror_uv` left limb cubes.

---

## Task 0: Spike — the six-cube armour model in Blockbench 5, by hand

Proves the model + UV layout before a line of porting. Nothing is committed from this task except the notes file; the whole point is to find out whether `mirror_uv` on a box-UV cube reproduces the armour model's left-limb mirroring face for face.

**Files:**
- Create: `mods/ovvar/tools/blockbench/NOTES-spike.md` (a scratch file; deleted in Task 9)

**Interfaces:**
- Consumes: the committed `mods/ovvar/src/main/generated/assets/ovvar/textures/entity/equipment/humanoid/data/top.png` and `.../humanoid_leggings/data/bottom.png` (both 128×64).
- Produces: written-down answers to six questions, quoted by Tasks 2, 7 and 8.

- [ ] **Step 1: Extract Blockbench 5's own source so every later API claim can be checked against it**

  Run:
  ```sh
  rm -rf /tmp/bbasar /tmp/bbsrc
  npx --yes @electron/asar extract /Applications/Blockbench.app/Contents/Resources/app.asar /tmp/bbasar
  node -e '
  const m = JSON.parse(require("fs").readFileSync("/tmp/bbasar/dist/bundle.js.map","utf8"));
  const fs = require("fs");
  fs.mkdirSync("/tmp/bbsrc", {recursive: true});
  m.sources.forEach((s, i) => {
    const out = "/tmp/bbsrc/" + s.replace(/[^a-zA-Z0-9.]/g, "_");
    fs.writeFileSync(out, m.sourcesContent[i] || "");
  });
  console.log("wrote", m.sources.length, "sources to /tmp/bbsrc");
  '
  ```
  Expected: `wrote 369 sources to /tmp/bbsrc` (the count may differ on a later 5.x; anything above 300 is fine).

  The files this plan quotes:
  - `/tmp/bbsrc/.._js_formats_minecraft_skin.ts` — the `armor` model template, the authoritative armour cube table
  - `/tmp/bbsrc/.._js_outliner_types_cube.js` — `Cube`
  - `/tmp/bbsrc/.._js_texturing_textures.js` — `Texture`
  - `/tmp/bbsrc/.._js_io_format.ts` — `ModelFormat`
  - `/tmp/bbsrc/.._js_interface_panels.ts`, `.._js_interface_dialog.ts`, `.._js_undo.js`, `.._js_plugin_loader.ts`

- [ ] **Step 2: Read the armour template out of Blockbench's own skin format**

  Run:
  ```sh
  grep -n 'Armor\|Chestplate\|Boot\|Belt\|Helmet' /tmp/bbsrc/.._js_formats_minecraft_skin.ts | sed -n '1,20p'
  ```
  Expected to contain, verbatim (Blockbench's `armor` skin model, bedrock-style `origin`/`size`):
  ```
  {"name": "Chestplate",       "origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16], "inflate": 1.01}
  {"name": "Right Arm Armor",  "origin": [-8, 12, -2], "size": [4, 12, 4], "uv": [40, 16], "inflate": 1}
  {"name": "Left Arm Armor",   "origin": [ 4, 12, -2], "size": [4, 12, 4], "uv": [40, 16], "inflate": 1, "mirror": true}
  {"name": "Belt",             "origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16], "inflate": 0.51}
  {"name": "Right Leg Armor",  "origin": [-3.9, 0, -2], "size": [4, 12, 4], "uv": [0, 16], "inflate": 0.5}
  {"name": "Left Leg Armor",   "origin": [-0.1, 0, -2], "size": [4, 12, 4], "uv": [0, 16], "inflate": 0.5, "mirror": true}
  ```
  Write into `NOTES-spike.md`: this is the table Task 8 builds, with three corrections — `inflate` 1.01 → **1.0** and 0.51 → **0.5** (Blockbench fudges them to avoid z-fighting with the skin; the game uses `Spot.inflate` = 1.0 / 0.5 exactly), and the ±0.1 x-fudge on the legs → **0**.

- [ ] **Step 3: Convert bedrock `origin`/`size` to Blockbench `from`/`to`**

  Run:
  ```sh
  sed -n '/function parseCube/,/^	}/p' /tmp/bbsrc/.._js_formats_bedrock_bedrock.js 2>/dev/null || node -e '
  const m = JSON.parse(require("fs").readFileSync("/tmp/bbasar/dist/bundle.js.map","utf8"));
  const i = m.sources.findIndex(s => s.endsWith("formats/bedrock/bedrock.js"));
  const s = m.sourcesContent[i], j = s.indexOf("function parseCube");
  console.log(s.slice(j, j + 900));
  '
  ```
  Expected to contain:
  ```
  base_cube.from.V3_set(s.origin)
  base_cube.from[0] = -(base_cube.from[0] + s.size[0])
  base_cube.to[0] = s.size[0] + base_cube.from[0]
  ```
  So `from = [-(origin[0] + size[0]), origin[1], origin[2]]`, `to = from + size`. Write the converted table into `NOTES-spike.md` — **this is the table Task 8 uses**:

  | cube | `from` | `to` | `uv_offset` | `inflate` | `mirror_uv` | texture |
  |---|---|---|---|---|---|---|
  | `ovve_body` | `[-4, 12, -2]` | `[4, 24, 2]` | `[16, 16]` | `1.0` | `false` | A(top) |
  | `ovve_arm_r` | `[4, 12, -2]` | `[8, 24, 2]` | `[40, 16]` | `1.0` | `false` | A(top) |
  | `ovve_arm_l` | `[-8, 12, -2]` | `[-4, 24, 2]` | `[40, 16]` | `1.0` | `true` | B(top) |
  | `ovve_belt` | `[-4, 12, -2]` | `[4, 24, 2]` | `[16, 16]` | `0.5` | `false` | A(bottom) |
  | `ovve_leg_r` | `[0, 0, -2]` | `[4, 12, 2]` | `[0, 16]` | `0.5` | `false` | A(bottom) |
  | `ovve_leg_l` | `[-4, 0, -2]` | `[0, 12, 2]` | `[0, 16]` | `0.5` | `true` | B(bottom) |

  Note in `NOTES-spike.md`: **Blockbench's +x is the wearer's right** (the right arm lands at x 4..8). Also note: Alex arms are 3 px wide *in the skin*, but the **armour** model is always 4 wide — there is no slim armour model, so this table never changes.

- [ ] **Step 4: Build the model by hand in Blockbench 5 and bind the committed textures**

  Open Blockbench, *File › New › Skin*, pick **Steve**, and confirm the inner player appears. Then open the developer console (*Help › Developer › Toggle DevTools*, or ⌥⌘I) and paste:
  ```js
  const table = [
    {name:'ovve_body',  from:[-4,12,-2], to:[4,24,2],  uv:[16,16], inflate:1.0, mirror:false},
    {name:'ovve_arm_r', from:[4,12,-2],  to:[8,24,2],  uv:[40,16], inflate:1.0, mirror:false},
    {name:'ovve_arm_l', from:[-8,12,-2], to:[-4,24,2], uv:[40,16], inflate:1.0, mirror:true},
    {name:'ovve_belt',  from:[-4,12,-2], to:[4,24,2],  uv:[16,16], inflate:0.5, mirror:false},
    {name:'ovve_leg_r', from:[0,0,-2],   to:[4,12,2],  uv:[0,16],  inflate:0.5, mirror:false},
    {name:'ovve_leg_l', from:[-4,0,-2],  to:[0,12,2],  uv:[0,16],  inflate:0.5, mirror:true},
  ];
  Project.texture_width = 128; Project.texture_height = 64;
  window.spikeCubes = table.map(c => new Cube({
    name: c.name, from: c.from, to: c.to, origin: [0,0,0],
    box_uv: true, uv_offset: c.uv, inflate: c.inflate, mirror_uv: c.mirror
  }).init());
  Canvas.updateAll();
  spikeCubes.map(c => [c.name, c.box_uv, c.mirror_uv, c.inflate, c.uv_offset.join(',')]);
  ```
  **Verify in Blockbench 5 console:** the returned array must show `box_uv === true` and the `mirror_uv`/`inflate`/`uv_offset` you asked for on all six. If `box_uv` came back `false`, the Skin format forced per-face UV — record that and use the ModelFormat of Task 8 (`box_uv: true`) instead of Skin for this spike.

  **Verify in Blockbench 5 console** that the texture size stuck: `[Project.texture_width, Project.texture_height]` → `[128, 64]`.

- [ ] **Step 5: Load the two committed garment textures and bind them**

  In the console (edit `CHECKOUT` to the worktree path):
  ```js
  const CHECKOUT = '/Users/winsty-dev/Personal Projects/MinecraftModding/Metacraft/METAmods/.claude/worktrees/rivals';
  const GEN = CHECKOUT + '/mods/ovvar/src/main/generated/assets/ovvar/textures/entity/equipment/';
  window.texTop = new Texture({name: 'spike_top.png'}).fromPath(GEN + 'humanoid/data/top.png').add(false);
  window.texBottom = new Texture({name: 'spike_bottom.png'}).fromPath(GEN + 'humanoid_leggings/data/bottom.png').add(false);
  spikeCubes[0].applyTexture(texTop, true);
  spikeCubes[1].applyTexture(texTop, true);
  spikeCubes[2].applyTexture(texTop, true);
  spikeCubes[3].applyTexture(texBottom, true);
  spikeCubes[4].applyTexture(texBottom, true);
  spikeCubes[5].applyTexture(texBottom, true);
  Canvas.updateAll();
  [texTop.width, texTop.height, texBottom.width, texBottom.height];
  ```
  **Verify in Blockbench 5 console:** `[128, 64, 128, 64]`. If `fromPath` is not a function on this build, run `Object.getOwnPropertyNames(Texture.prototype).filter(n => /from/i.test(n))` and use whichever loader it lists (the extracted `/tmp/bbsrc/.._js_texturing_textures.js` has the authoritative list).

- [ ] **Step 6: Screenshot and compare with the game by eye**

  Use Blockbench's own screenshot action: *View › Screenshot Model* (or console `BarItems.screenshot_model.trigger()`), save to `/tmp/ovvar-spike-front.png`. Rotate 180° and save `/tmp/ovvar-spike-back.png`.

  Compare against the real game:
  - `/tmp/ovvar-promo/shots-run2/01_hero_lineup_front.png` — the Data ovve from the front
  - `/tmp/ovvar-promo/shots-run2/07_hero_lineup_back.png` — from the back
  - `/tmp/ovvar-promo/shots-run2/04_closeup_torso_sleeve.png` — the sleeve, where left/right mirroring shows

  Record in `NOTES-spike.md`, one line each:
  1. Does the body's front/back art land on the right faces? (expected yes)
  2. Does the **right** arm show the right sleeve's art unmirrored? (expected yes)
  3. Does the **left** arm show the *same strip* mirrored? (expected yes — and this is the bug the mirror strip fixes: a plain `mirror_uv` cube on texture A shows the **right** arm's art flipped, not the left arm's own art)
  4. Does the leg's inflate 0.5 read visibly thinner than the arm's 1.0? (expected yes)
  5. Any face rotated or off by a texel? (expected no)
  6. Does the model's silhouette match the screenshots? (expected yes)

- [ ] **Step 7: Prove the mirror strip is what is needed**

  Texture B is A with the mirror strip copied down. Fake it by hand in the console to confirm before anything is ported:
  ```js
  // Copy skin rows 0..16 (texture rows 0..32) onto rows 16..32 (texture rows 32..64)
  // for the arm columns (skin u 40..56 -> texture x 80..112) and the leg columns (0..16 -> 0..32).
  function mirrorStrip(tex) {
    const c = document.createElement('canvas');
    c.width = tex.width; c.height = tex.height;
    const g = c.getContext('2d');
    g.drawImage(tex.img, 0, 0);
    g.drawImage(c, 80, 0, 32, 32, 80, 32, 32, 32);   // arm
    g.drawImage(c, 0, 0, 32, 32, 0, 32, 32, 32);     // leg
    return c.toDataURL('image/png');
  }
  window.texTopB = new Texture({name: 'spike_top_B.png'}).fromDataURL(mirrorStrip(texTop)).add(false);
  window.texBottomB = new Texture({name: 'spike_bottom_B.png'}).fromDataURL(mirrorStrip(texBottom)).add(false);
  spikeCubes[2].applyTexture(texTopB, true);
  spikeCubes[5].applyTexture(texBottomB, true);
  Canvas.updateAll();
  ```
  **Verify in Blockbench 5 console** that `fromDataURL` exists: `typeof Texture.prototype.fromDataURL` → `'function'`.

  Screenshot again (`/tmp/ovvar-spike-mirrored.png`) and compare the left sleeve with `/tmp/ovvar-promo/shots-run2/04_closeup_torso_sleeve.png` and `12_player_third_person_back.png`. Record in `NOTES-spike.md`: **does the left limb now show the left limb's own art the right way round?** (expected yes — this is the whole spike). This canvas trick is throwaway: the real plugin never uses canvas for pixels (canvas premultiplies alpha and would destroy the RGB of alpha-0 texels); Task 7 does the copy on the image type.

- [ ] **Step 8: Commit the notes**

  ```sh
  git add mods/ovvar/tools/blockbench/NOTES-spike.md
  git commit -m "ovvar: spike notes for the Blockbench armour model and the mirror strip"
  ```

---

## Task 1: The Node/Blockbench PNG codec and the io shim

`src/00-manifest.js` — the namespace, a PNG decoder and encoder, and the one `io` object both hosts build the same way. No Blockbench types.

**Files:**
- Create: `mods/ovvar/tools/blockbench/src/00-manifest.js`
- Create: `mods/ovvar/tools/blockbench/test.js`
- Create: `mods/ovvar/tools/blockbench/build.sh`

**Interfaces:**
- Produces:
  - `OVVAR.png.decode(bytes: Uint8Array, inflate: (Uint8Array) => Uint8Array) -> image`
  - `OVVAR.png.encode(image, deflate: (Uint8Array) => Uint8Array) -> Uint8Array`
  - `OVVAR.makeIo(require, scope: string) -> io` where `io = {read(path) -> Uint8Array, write(path, Uint8Array), exists(path) -> boolean, list(dir) -> string[], mkdirp(dir), decode(bytes) -> image, encode(image) -> Uint8Array, dataUrl(image) -> string, join(...parts) -> string}`
  - `OVVAR.loadManifest(io, checkout) -> m`
  - `OVVAR.artPath(m, checkout, art) -> string`
- Consumes: nothing.

- [ ] **Step 1: Write the failing test file**

  Create `mods/ovvar/tools/blockbench/test.js`:
  ```js
  // node test.js [<checkout>]
  // Builds the plugin the way build.sh does (cat src/*.js) and runs it through the same
  // wrapper Blockbench uses -- new Function('requireNativeModule', 'require', code) -- so the
  // tests exercise the very file Vlad loads, not a parallel copy of it.
  const fs = require('node:fs');
  const path = require('node:path');
  const test = require('node:test');
  const assert = require('node:assert');

  const HERE = __dirname;
  const SRC = path.join(HERE, 'src');
  const CHECKOUT = process.argv[2] || path.resolve(HERE, '..', '..', '..', '..');

  const files = fs.readdirSync(SRC).filter(f => f.endsWith('.js')).sort();
  const code = files.map(f => fs.readFileSync(path.join(SRC, f), 'utf8')).join('\n');
  const OVVAR = new Function('requireNativeModule', 'require', code + '\nreturn OVVAR;')(require, require);

  const io = OVVAR.makeIo(require, CHECKOUT);

  module.exports = {OVVAR, io, CHECKOUT, test, assert, path, fs};

  test('the built plugin exposes its namespace', () => {
    assert.ok(OVVAR.png, 'OVVAR.png missing');
    assert.ok(OVVAR.makeIo, 'OVVAR.makeIo missing');
  });

  test('png round-trips a committed 8-bit RGBA file', () => {
    const p = path.join(CHECKOUT, 'mods/ovvar/src/main/resources/art/ovvar/patches/itk_8x8.png');
    const im = io.decode(io.read(p));
    assert.strictEqual(im.w, 8);
    assert.strictEqual(im.h, 8);
    assert.strictEqual(im.data.length, 8 * 8 * 4);
    const again = io.decode(io.encode(im));
    assert.strictEqual(again.w, im.w);
    assert.strictEqual(again.h, im.h);
    assert.deepStrictEqual(Array.from(again.data), Array.from(im.data));
  });

  test('png decodes a 128x64 generated texture and a non-RGBA file', () => {
    const big = io.decode(io.read(path.join(CHECKOUT,
      'mods/ovvar/src/main/generated/assets/ovvar/textures/entity/equipment/humanoid/data/top.png')));
    assert.strictEqual(big.w, 128);
    assert.strictEqual(big.h, 64);
    // bakparti.png is colour type 2 (RGB, no alpha): the decoder must fill alpha 255.
    const rgb = io.decode(io.read(path.join(CHECKOUT,
      'mods/ovvar/src/main/resources/art/ovvar/patches/bakparti.png')));
    assert.strictEqual(rgb.w, 32);
    assert.strictEqual(rgb.h, 8);
    assert.strictEqual(rgb.data[3], 255);
  });
  ```

- [ ] **Step 2: Run it and watch it fail**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected failure: `Error: ENOENT: no such file or directory, scandir '.../tools/blockbench/src'` (the `src` directory does not exist yet).

- [ ] **Step 3: Write `src/00-manifest.js`**

  Create `mods/ovvar/tools/blockbench/src/00-manifest.js`:
  ```js
  // The one namespace every file of the plugin shares. Blockbench loads the built ovvar.js as
  // new Function('requireNativeModule', 'require', code), so `var` here is function-scoped to the
  // plugin: nothing of ours lands on window, and onunload has nothing global to clean up.
  var OVVAR = {};

  OVVAR.VERSION = 1;

  // ---- PNG, by hand
  //
  // Not the canvas: a canvas premultiplies alpha, so it loses the RGB of a texel written at alpha 0
  // -- and datagen's `placed` copies any texel whose packed ARGB is non-zero, alpha 0 included, so
  // those texels are part of the goldens. Everything here is byte-exact instead. zlib does the
  // compression: Blockbench hands plugins 'zlib' with no permission prompt (it is on the
  // unrestricted module list), and Node has it built in, so one code path serves both.

  OVVAR.png = {
    SIGNATURE: [137, 80, 78, 71, 13, 10, 26, 10],

    decode: function (bytes, inflate) {
      for (var i = 0; i < 8; i++) {
        if (bytes[i] !== OVVAR.png.SIGNATURE[i]) throw new Error('not a PNG (bad signature)');
      }
      var view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
      var at = 8;
      var w = 0, h = 0, depth = 0, colour = 0, interlace = 0;
      var palette = null, trns = null;
      var idat = [];
      while (at < bytes.length) {
        var len = view.getUint32(at);
        var type = String.fromCharCode(bytes[at + 4], bytes[at + 5], bytes[at + 6], bytes[at + 7]);
        var body = bytes.subarray(at + 8, at + 8 + len);
        if (type === 'IHDR') {
          w = view.getUint32(at + 8);
          h = view.getUint32(at + 12);
          depth = bytes[at + 16];
          colour = bytes[at + 17];
          interlace = bytes[at + 20];
        } else if (type === 'PLTE') {
          palette = body.slice();
        } else if (type === 'tRNS') {
          trns = body.slice();
        } else if (type === 'IDAT') {
          idat.push(body.slice());
        } else if (type === 'IEND') {
          break;
        }
        at += 12 + len;
      }
      if (depth !== 8) throw new Error('only 8-bit PNGs are supported, this one is ' + depth + '-bit');
      if (interlace !== 0) throw new Error('interlaced PNGs are not supported');
      var total = 0;
      for (var k = 0; k < idat.length; k++) total += idat[k].length;
      var joined = new Uint8Array(total);
      var off = 0;
      for (var k2 = 0; k2 < idat.length; k2++) { joined.set(idat[k2], off); off += idat[k2].length; }
      var raw = inflate(joined);

      // Samples per pixel, by colour type: 0 grey, 2 RGB, 3 palette index, 4 grey+alpha, 6 RGBA.
      var channels = colour === 0 ? 1 : colour === 2 ? 3 : colour === 3 ? 1 : colour === 4 ? 2 : 4;
      var stride = w * channels;
      var lines = new Uint8Array(h * stride);
      var pos = 0;
      for (var y = 0; y < h; y++) {
        var filter = raw[pos++];
        var line = raw.subarray(pos, pos + stride);
        pos += stride;
        var here = y * stride, above = here - stride;
        for (var x = 0; x < stride; x++) {
          var a = x >= channels ? lines[here + x - channels] : 0;
          var b = y > 0 ? lines[above + x] : 0;
          var c = (x >= channels && y > 0) ? lines[above + x - channels] : 0;
          var v = line[x];
          if (filter === 1) v = (v + a) & 255;
          else if (filter === 2) v = (v + b) & 255;
          else if (filter === 3) v = (v + ((a + b) >> 1)) & 255;
          else if (filter === 4) {
            var p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
            var pred = (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
            v = (v + pred) & 255;
          } else if (filter !== 0) throw new Error('unknown PNG filter ' + filter + ' on row ' + y);
          lines[here + x] = v;
        }
      }

      var out = new Uint8Array(w * h * 4);
      for (var py = 0; py < h; py++) {
        for (var px = 0; px < w; px++) {
          var s = py * stride + px * channels, d = (py * w + px) * 4;
          if (colour === 6) {
            out[d] = lines[s]; out[d + 1] = lines[s + 1]; out[d + 2] = lines[s + 2]; out[d + 3] = lines[s + 3];
          } else if (colour === 2) {
            out[d] = lines[s]; out[d + 1] = lines[s + 1]; out[d + 2] = lines[s + 2]; out[d + 3] = 255;
          } else if (colour === 0) {
            out[d] = out[d + 1] = out[d + 2] = lines[s]; out[d + 3] = 255;
          } else if (colour === 4) {
            out[d] = out[d + 1] = out[d + 2] = lines[s]; out[d + 3] = lines[s + 1];
          } else if (colour === 3) {
            var idx = lines[s];
            out[d] = palette[idx * 3]; out[d + 1] = palette[idx * 3 + 1]; out[d + 2] = palette[idx * 3 + 2];
            out[d + 3] = (trns && idx < trns.length) ? trns[idx] : 255;
          } else {
            throw new Error('unsupported PNG colour type ' + colour);
          }
        }
      }
      return {w: w, h: h, data: out};
    },

    // Always 8-bit RGBA, filter 0 (None) on every row: the smallest encoder that is still a legal
    // PNG, and the goldens are never compared byte for byte -- only pixel for pixel.
    encode: function (image, deflate) {
      var w = image.w, h = image.h;
      var raw = new Uint8Array(h * (1 + w * 4));
      for (var y = 0; y < h; y++) {
        raw[y * (1 + w * 4)] = 0;
        raw.set(image.data.subarray(y * w * 4, (y + 1) * w * 4), y * (1 + w * 4) + 1);
      }
      var idat = deflate(raw);
      var ihdr = new Uint8Array(13);
      var dv = new DataView(ihdr.buffer);
      dv.setUint32(0, w); dv.setUint32(4, h);
      ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
      var chunks = [
        OVVAR.png.chunk('IHDR', ihdr),
        OVVAR.png.chunk('IDAT', idat),
        OVVAR.png.chunk('IEND', new Uint8Array(0))
      ];
      var size = 8;
      for (var i = 0; i < chunks.length; i++) size += chunks[i].length;
      var out = new Uint8Array(size);
      out.set(OVVAR.png.SIGNATURE, 0);
      var at = 8;
      for (var j = 0; j < chunks.length; j++) { out.set(chunks[j], at); at += chunks[j].length; }
      return out;
    },

    chunk: function (type, body) {
      var out = new Uint8Array(12 + body.length);
      var dv = new DataView(out.buffer);
      dv.setUint32(0, body.length);
      for (var i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
      out.set(body, 8);
      dv.setUint32(8 + body.length, OVVAR.png.crc(out.subarray(4, 8 + body.length)));
      return out;
    },

    CRC_TABLE: (function () {
      var t = new Int32Array(256);
      for (var n = 0; n < 256; n++) {
        var c = n;
        for (var k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
        t[n] = c;
      }
      return t;
    })(),

    crc: function (bytes) {
      var c = -1;
      for (var i = 0; i < bytes.length; i++) c = OVVAR.png.CRC_TABLE[(c ^ bytes[i]) & 255] ^ (c >>> 8);
      return (c ^ -1) >>> 0;
    }
  };

  // ---- io
  //
  // One factory for both hosts. Blockbench hands a plugin a scoped `require(name, options)`:
  // 'path', 'zlib' and 'buffer' are free, 'fs' prompts once per directory and returns a file
  // system that refuses anything outside `scope`. Node's own require ignores the second argument,
  // so this same call works there.
  OVVAR.makeIo = function (req, scope) {
    var fs = req('fs', {
      scope: scope,
      optional: false,
      message: 'Ovvar needs to read the manifest and the patch art in your METAmods checkout, and to write exported patch art back into it.'
    });
    var zlib = req('zlib');
    var nodePath = req('path');
    var Buf = req('buffer').Buffer;
    var io = {
      scope: scope,
      join: function () { return nodePath.join.apply(nodePath, arguments); },
      read: function (p) { var b = fs.readFileSync(p); return new Uint8Array(b.buffer, b.byteOffset, b.length); },
      write: function (p, bytes) { fs.writeFileSync(p, Buf.from(bytes.buffer, bytes.byteOffset, bytes.length)); },
      exists: function (p) { return fs.existsSync(p); },
      list: function (d) { return fs.readdirSync(d); },
      mkdirp: function (d) { fs.mkdirSync(d, {recursive: true}); },
      inflate: function (bytes) {
        var b = zlib.inflateSync(Buf.from(bytes.buffer, bytes.byteOffset, bytes.length));
        return new Uint8Array(b.buffer, b.byteOffset, b.length);
      },
      deflate: function (bytes) {
        var b = zlib.deflateSync(Buf.from(bytes.buffer, bytes.byteOffset, bytes.length), {level: 9});
        return new Uint8Array(b.buffer, b.byteOffset, b.length);
      }
    };
    io.decode = function (bytes) { return OVVAR.png.decode(bytes, io.inflate); };
    io.encode = function (image) { return OVVAR.png.encode(image, io.deflate); };
    io.dataUrl = function (image) {
      var bytes = io.encode(image);
      return 'data:image/png;base64,' + Buf.from(bytes.buffer, bytes.byteOffset, bytes.length).toString('base64');
    };
    return io;
  };

  // ---- the manifest

  OVVAR.MANIFEST_PATH = 'mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json';

  OVVAR.loadManifest = function (io, checkout) {
    var p = io.join(checkout, OVVAR.MANIFEST_PATH);
    if (!io.exists(p)) {
      throw new Error('No Ovvar manifest at ' + p + ' -- run ./gradlew :mods:ovvar:runDatagen in that checkout first.');
    }
    var text = '';
    var bytes = io.read(p);
    for (var i = 0; i < bytes.length; i += 8192) {
      text += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i + 8192, bytes.length)));
    }
    var m = JSON.parse(decodeURIComponent(escape(text)));
    if (m.version > OVVAR.VERSION) {
      throw new Error('This manifest is version ' + m.version + '; this plugin understands version '
        + OVVAR.VERSION + '. Update the plugin (tools/blockbench/ovvar.js).');
    }
    m.checkout = checkout;
    m.cellById = {};
    for (var c = 0; c < m.cells.length; c++) m.cellById[m.cells[c].id] = m.cells[c];
    m.patchById = {};
    for (var p2 = 0; p2 < m.patches.length; p2++) m.patchById[m.patches[p2].id] = m.patches[p2];
    m.chapterById = {};
    for (var ch = 0; ch < m.chapters.length; ch++) m.chapterById[m.chapters[ch].id] = m.chapters[ch];
    m.artByFile = {};
    for (var p3 = 0; p3 < m.patches.length; p3++) {
      var arts = m.patches[p3].arts;
      for (var a = 0; a < arts.length; a++) m.artByFile[arts[a].file] = arts[a];
    }
    return m;
  };

  // Where an art's PNG is: a drawing lives in the source tree, a generated size beside the manifest.
  OVVAR.artPath = function (m, checkout, art) {
    if (art.generated) {
      var base = art.file.slice(art.file.lastIndexOf('/') + 1);
      return [checkout, 'mods/ovvar/src/main/generated/ovvar/blockbench/art', base].join('/');
    }
    return [checkout, 'mods/ovvar/src/main/resources/art/ovvar', art.file].join('/');
  };
  ```

- [ ] **Step 4: Run the tests — the first three must pass**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 3`, `# fail 0`.

- [ ] **Step 5: Write `build.sh` and build once**

  Create `mods/ovvar/tools/blockbench/build.sh`:
  ```sh
  #!/bin/sh
  # The whole build: the plugin is the concatenation of src/*.js, in name order. No npm, no
  # bundler, no minifier -- Vlad loads one readable file through File > Plugins > Load from file,
  # and `git diff` on it is a diff of the sources.
  set -e
  cd "$(dirname "$0")"
  cat src/*.js > ovvar.js
  echo "built ovvar.js ($(wc -l < ovvar.js) lines from $(ls src/*.js | wc -l) sources)"
  ```
  Then:
  ```sh
  chmod +x mods/ovvar/tools/blockbench/build.sh
  mods/ovvar/tools/blockbench/build.sh
  ```
  Expected: `built ovvar.js (NNN lines from 1 sources)`.

- [ ] **Step 6: Commit**

  ```sh
  git add mods/ovvar/tools/blockbench/src/00-manifest.js mods/ovvar/tools/blockbench/test.js mods/ovvar/tools/blockbench/build.sh mods/ovvar/tools/blockbench/ovvar.js
  git commit -m "ovvar: a PNG codec and a scoped io shim the plugin and its Node tests share"
  ```

---

## Task 2: `BlockbenchManifest.java` — publish `Spot`, `Patches` and `Chapter` as data

**Files:**
- Modify: `mods/ovvar/src/main/java/metacraft/ovvar/datagen/GeneratedAssets.java:61-69` (the skin-box constants) and `:645-660` (the marker texel constants) — drop `private` from `BODY`, `RIGHT_ARM`, `RIGHT_LEG`, `BODY_OUTER`, `RIGHT_ARM_OUTER`, `RIGHT_LEG_OUTER`, `LEFT_ARM`, `LEFT_ARM_OUTER`, `LEFT_LEG`, `LEFT_LEG_OUTER`, `WAIST`, `MARKER_X`, `MARKER_Y`, `MARKER_KIND_X`, `LAYER_X`, `DEBUG_X`, `DEBUG`, `BLANK_X`, `BLANK_Y` so the manifest quotes them instead of copying them
- Create: `mods/ovvar/src/main/java/metacraft/ovvar/datagen/BlockbenchManifest.java`
- Modify: `mods/ovvar/src/main/java/metacraft/ovvar/datagen/OvvarDataGenerator.java:8-11`
- Create: `mods/ovvar/src/main/java/metacraft/ovvar/gametest/BlockbenchManifestTests.java`
- Modify: `mods/ovvar/src/main/resources/fabric.mod.json:21-24` (gametest entrypoints)

**Interfaces:**
- Consumes: `Spot.values()`, `Spot.face(Spot)`, `Spot.anchored(double, double, int)`, `Spot.stripStart/stripWidth`, `Spot.inflate(Piece)`, `Patches.all()`, `Patches.variants(Patch)`, `Patches.artFor(Patch, Fit)`, `Chapter.values()`, `Tex.art(Patches.Art)`, `Tex.png()`.
- Produces:
  - `public final class BlockbenchManifest implements DataProvider`
  - `public BlockbenchManifest(FabricPackOutput output)`
  - `public static final int VERSION = 1`
  - `public static final String RESOURCE = "/ovvar/blockbench/manifest.json"`
  - `public static final int SAMPLES = 512`
  - `public static double sampleSkinX(int k)` = `k / 8.0`
  - `public static double sampleInflate(int k)` = `k % 2 == 0 ? 0.5 : 1.0`
  - `public static int sampleAnchor(int k)` = `k % 4`
  - the files `src/main/generated/ovvar/blockbench/manifest.json` and `.../art/it_8x8.png`

**The schema, fixed here and read by Tasks 3-8.** Keys in this exact order and spelling:

```
{ "version": 1,
  "detail": 2, "faceRow": 20, "faceRows": 12, "topRow": 16, "topRows": 4, "topFace": 4,
  "mirrorShift": 16, "cellSize": 4, "bigCell": 8, "px": 8,
  "maxArt": 16, "overMax": 12, "seatHeightMax": 20, "icon": 16,
  "skin": [64, 32], "texture": [128, 64],
  "inflate": {"top": 1.0, "bottom": 0.5},
  "markerTexels": [[121,31],[122,31],[123,31],[124,31],[125,31],[126,31],[127,31],[127,30]],
  "skinBoxes": {"body":[16,16,24,16], "rightArm":[40,16,16,16], "rightLeg":[0,16,16,16],
                "bodyOuter":[16,32,24,16], "rightArmOuter":[40,32,16,16], "rightLegOuter":[0,32,16,16],
                "leftArm":[32,48,16,16], "leftArmOuter":[48,48,16,16],
                "leftLeg":[16,48,16,16], "leftLegOuter":[0,48,16,16],
                "waist":[16,30,24,2]},
  "chapters": [{"id":"data","name":"Data","art":"data.png","nercabbad":"data-nercabbad.png",
                "tint":null,"rollable":true,
                "layers":{"top":"humanoid/data/top.png","bottom":"humanoid_leggings/data/bottom.png",
                          "bottomNercabbad":"humanoid_leggings/data/bottom_nercabbad.png"}}, ...],
  "cells": [{"id":"front_top_left","piece":"top","layerFolder":"humanoid","u":20,"v":21,"w":4,"h":4,
             "side":"body","top":false,"face":1,"layer":0,
             "stripStart":16,"stripWidth":24,"fit":"over","label":"chest, top left"}, ...],
  "patches": [{"id":"itk","name":"ITK","seat":false,"w":12,"h":12,"artist":"Froosty11",
               "arts":[{"file":"patches/itk_8x8.png","w":8,"h":8,"default":false,"generated":false,"source":null},
                       {"file":"patches/itk.png","w":12,"h":12,"default":true,"generated":false,"source":null},
                       {"file":"patches/itk_16x16.png","w":16,"h":16,"default":false,"generated":false,"source":null}],
               "fits":{"over":"patches/itk.png","clipped":"patches/itk_8x8.png","filled":"patches/itk_16x16.png"}}, ...],
  "anchoredSamples": [[0.0, 0.5, 0, 1.6363636363636365], ... 512 of them],
  "stackedOrder": "layer ascending, then sewing order" }
```

Notes that Tasks 3-8 depend on:
- `art.file` always ends in `.png` and is `Patches.Art.file() + ".png"` (so `"patches/itk.png"`, `"patches/it_8x8.png"`).
- `art.source` is the `file` of the art a generated one is scaled from, or `null`.
- `cells[].side` is the lowercased `Spot.Side` name: `"body"`, `"right"`, `"left"`, `"seat"`.
- `cells[].fit` is the lowercased `Patches.Fit` name: `"over"`, `"clipped"`, `"filled"`.
- `chapters[].layers.*` are paths under `src/main/generated/assets/ovvar/textures/entity/equipment/`.
- `anchoredSamples[k] = [sampleSkinX(k), sampleInflate(k), sampleAnchor(k), Spot.anchored(...)]`. The sample order sweeps `skinX` in steps of 0.125 across the whole of `[0, 64)` while cycling both inflations and all four anchors — 512 rows exactly, and every strip (legs, body, arms) and both integer and fractional columns are covered. (A literal "skinX × inflate × anchor, first 512" nest would only reach `skinX` 7.875, i.e. the leg strip.)

- [ ] **Step 1: Write the failing game test**

  Create `mods/ovvar/src/main/java/metacraft/ovvar/gametest/BlockbenchManifestTests.java`:
  ```java
  package metacraft.ovvar.gametest;

  import com.google.gson.JsonArray;
  import com.google.gson.JsonObject;
  import com.google.gson.JsonParser;
  import metacraft.ovvar.content.Chapter;
  import metacraft.ovvar.content.Patches;
  import metacraft.ovvar.content.Spot;
  import metacraft.ovvar.datagen.BlockbenchManifest;
  import net.fabricmc.fabric.api.gametest.v1.GameTest;
  import net.minecraft.gametest.framework.GameTestHelper;

  import java.io.InputStream;
  import java.io.InputStreamReader;
  import java.nio.charset.StandardCharsets;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.Locale;

  /**
   * The Blockbench plugin's goldens are the pack's own textures, and what tells it how to read
   * them is {@link BlockbenchManifest}. So the manifest has to be the enums, not a copy of them:
   * a cell added, moved or resized in {@link Spot}, a patch added to {@link Patches}, a change to
   * {@link Spot#anchored} — each of those must come out of {@code runDatagen} as a changed
   * manifest, and if it does not, this fails rather than the plugin silently drawing last week's
   * model.
   */
  public final class BlockbenchManifestTests {
  	private static JsonObject manifest(GameTestHelper helper) {
  		try (InputStream in = BlockbenchManifest.class.getResourceAsStream(BlockbenchManifest.RESOURCE)) {
  			if (in == null) {
  				helper.fail(BlockbenchManifest.RESOURCE + " missing: run './gradlew :mods:ovvar:runDatagen'");
  				return null;
  			}
  			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
  		} catch (java.io.IOException e) {
  			helper.fail("could not read " + BlockbenchManifest.RESOURCE + ": " + e);
  			return null;
  		}
  	}

  	@GameTest
  	public void theManifestsCellTableIsSpot(GameTestHelper helper) {
  		JsonObject m = manifest(helper);
  		JsonArray cells = m.getAsJsonArray("cells");
  		if (cells.size() != Spot.values().length) {
  			helper.fail("the manifest has " + cells.size() + " cells, Spot has " + Spot.values().length);
  		}
  		List<String> wrong = new ArrayList<>();
  		for (int i = 0; i < Spot.values().length; i++) {
  			Spot spot = Spot.values()[i];
  			JsonObject c = cells.get(i).getAsJsonObject();
  			check(wrong, spot.id(), "id", c.get("id").getAsString(), spot.id());
  			check(wrong, spot.id(), "piece", c.get("piece").getAsString(), spot.piece.id);
  			check(wrong, spot.id(), "u", c.get("u").getAsInt(), spot.u);
  			check(wrong, spot.id(), "v", c.get("v").getAsInt(), spot.v);
  			check(wrong, spot.id(), "w", c.get("w").getAsInt(), spot.width);
  			check(wrong, spot.id(), "h", c.get("h").getAsInt(), spot.height);
  			check(wrong, spot.id(), "side", c.get("side").getAsString(), spot.side.name().toLowerCase(Locale.ROOT));
  			check(wrong, spot.id(), "top", c.get("top").getAsBoolean(), spot.top());
  			check(wrong, spot.id(), "face", c.get("face").getAsInt(), Spot.face(spot));
  			check(wrong, spot.id(), "layer", c.get("layer").getAsInt(), spot.layer());
  			check(wrong, spot.id(), "stripStart", c.get("stripStart").getAsInt(), Spot.stripStart(spot));
  			check(wrong, spot.id(), "stripWidth", c.get("stripWidth").getAsInt(), Spot.stripWidth(spot));
  			check(wrong, spot.id(), "fit", c.get("fit").getAsString(), Patches.Fit.of(spot).name().toLowerCase(Locale.ROOT));
  			check(wrong, spot.id(), "label", c.get("label").getAsString(), spot.label());
  		}
  		if (!wrong.isEmpty()) helper.fail("the manifest's cell table has drifted from Spot: " + wrong);
  		helper.succeed();
  	}

  	@GameTest
  	public void theManifestsAnchoredSamplesAreSpotAnchored(GameTestHelper helper) {
  		JsonObject m = manifest(helper);
  		JsonArray samples = m.getAsJsonArray("anchoredSamples");
  		if (samples.size() != BlockbenchManifest.SAMPLES) {
  			helper.fail("the manifest has " + samples.size() + " anchored samples, not " + BlockbenchManifest.SAMPLES);
  		}
  		for (int k = 0; k < samples.size(); k++) {
  			JsonArray row = samples.get(k).getAsJsonArray();
  			double skinX = row.get(0).getAsDouble(), inflate = row.get(1).getAsDouble();
  			int anchor = row.get(2).getAsInt();
  			if (skinX != BlockbenchManifest.sampleSkinX(k) || inflate != BlockbenchManifest.sampleInflate(k)
  					|| anchor != BlockbenchManifest.sampleAnchor(k)) {
  				helper.fail("anchored sample " + k + " is not the grid's own row: " + row);
  			}
  			double want = Spot.anchored(skinX, inflate, anchor);
  			if (Math.abs(row.get(3).getAsDouble() - want) > 1e-12) {
  				helper.fail("anchored sample " + k + " (" + row + ") should be " + want);
  			}
  		}
  		helper.succeed();
  	}

  	@GameTest
  	public void theManifestsCatalogueIsPatchesAndChapter(GameTestHelper helper) {
  		JsonObject m = manifest(helper);
  		JsonArray patches = m.getAsJsonArray("patches");
  		if (patches.size() != Patches.all().size()) {
  			helper.fail("the manifest has " + patches.size() + " patches, the catalogue has " + Patches.all().size());
  		}
  		List<String> wrong = new ArrayList<>();
  		for (int i = 0; i < Patches.all().size(); i++) {
  			Patches.Patch patch = Patches.all().get(i);
  			JsonObject p = patches.get(i).getAsJsonObject();
  			check(wrong, patch.id(), "id", p.get("id").getAsString(), patch.id());
  			check(wrong, patch.id(), "name", p.get("name").getAsString(), patch.name());
  			check(wrong, patch.id(), "seat", p.get("seat").getAsBoolean(), patch.seat());
  			check(wrong, patch.id(), "w", p.get("w").getAsInt(), patch.width());
  			check(wrong, patch.id(), "h", p.get("h").getAsInt(), patch.height());
  			JsonArray arts = p.getAsJsonArray("arts");
  			check(wrong, patch.id(), "arts", arts.size(), patch.variants().size());
  			for (int a = 0; a < Math.min(arts.size(), patch.variants().size()); a++) {
  				Patches.Art art = patch.variants().get(a);
  				check(wrong, patch.id(), "art " + a, arts.get(a).getAsJsonObject().get("file").getAsString(), art.file() + ".png");
  			}
  			for (Patches.Fit fit : Patches.Fit.values()) {
  				String key = fit.name().toLowerCase(Locale.ROOT);
  				check(wrong, patch.id(), "fit " + key, p.getAsJsonObject("fits").get(key).getAsString(),
  						Patches.artFor(patch, fit).file() + ".png");
  			}
  		}
  		// The PolymITer chapters are reference art due for removal and are left out on purpose.
  		List<String> want = new ArrayList<>();
  		for (Chapter chapter : Chapter.values()) if (!chapter.id.endsWith("_polymiter")) want.add(chapter.id);
  		List<String> got = new ArrayList<>();
  		for (var e : m.getAsJsonArray("chapters")) got.add(e.getAsJsonObject().get("id").getAsString());
  		if (!got.equals(want)) wrong.add("chapters " + got + " should be " + want);
  		if (!wrong.isEmpty()) helper.fail("the manifest's catalogue has drifted: " + wrong);
  		helper.succeed();
  	}

  	private static void check(List<String> wrong, String owner, String field, Object got, Object want) {
  		if (!got.equals(want)) wrong.add(owner + "." + field + " = " + got + ", should be " + want);
  	}
  }
  ```

- [ ] **Step 2: Register the test entrypoint and run it — it must fail to compile**

  In `mods/ovvar/src/main/resources/fabric.mod.json`, change the `fabric-gametest` array to:
  ```json
  		"fabric-gametest": [
  			"metacraft.ovvar.gametest.OvvarGameTests",
  			"metacraft.ovvar.gametest.WardrobeTests",
  			"metacraft.ovvar.gametest.BlockbenchManifestTests"
  		],
  ```
  Then:
  ```sh
  ./gradlew :mods:ovvar:compileJava
  ```
  Expected failure: `error: package metacraft.ovvar.datagen does not exist` / `cannot find symbol: class BlockbenchManifest`.

- [ ] **Step 3: Open the five constant groups in `GeneratedAssets`**

  In `mods/ovvar/src/main/java/metacraft/ovvar/datagen/GeneratedAssets.java`, delete the word `private` from these declarations (leave `static final` and the javadoc exactly as they are), so `BlockbenchManifest` — same package — publishes the very constants the generator draws with rather than a second copy:
  ```java
  	/** Skin-layout boxes (x, y, w, h) that the armour model draws, and which garment owns each. */
  	static final int[] BODY = {16, 16, 24, 16};
  	static final int[] RIGHT_ARM = {40, 16, 16, 16};
  	static final int[] RIGHT_LEG = {0, 16, 16, 16};
  	/** The skin's second layer for each, and the left limbs (base, second layer) — the website draws these in 3D. */
  	static final int[] BODY_OUTER = {16, 32, 24, 16};
  	static final int[] RIGHT_ARM_OUTER = {40, 32, 16, 16}, RIGHT_LEG_OUTER = {0, 32, 16, 16};
  	static final int[] LEFT_ARM = {32, 48, 16, 16}, LEFT_ARM_OUTER = {48, 48, 16, 16};
  	static final int[] LEFT_LEG = {16, 48, 16, 16}, LEFT_LEG_OUTER = {0, 48, 16, 16};
  	/** The trousers' share of the body box: the bottom two texel rows of its side faces (the waistband). */
  	static final int[] WAIST = {16, 30, 24, 2};
  ```
  and further down (the texel contract with `ovvar.glsl`):
  ```java
  	static final int MARKER_X = W - 1, MARKER_Y = H / 2 - 1, MARKER = 0x02FF00FF;
  ```
  ```java
  	static final int MARKER_KIND_X = W - 2, KIND_SIDED = 1, KIND_PREVIEW = 2;
  ```
  ```java
  	static final int LAYER_X = W - 3;
  ```
  ```java
  	static final int DEBUG_X = W - 7;
  	static final int[] DEBUG = {0xFFFF0000, 0xFF0000FF, 0xFFFF00FF, 0xFF00FF00};
  ```
  ```java
  	static final int BLANK_X = W - 1, BLANK_Y = H / 2 - 2;
  ```

- [ ] **Step 4: Write `BlockbenchManifest.java`**

  Create `mods/ovvar/src/main/java/metacraft/ovvar/datagen/BlockbenchManifest.java` (tabs, not spaces):
  ```java
  package metacraft.ovvar.datagen;

  import com.google.common.hash.Hashing;
  import com.google.gson.JsonArray;
  import com.google.gson.JsonElement;
  import com.google.gson.JsonObject;
  import metacraft.ovvar.Ovvar;
  import metacraft.ovvar.content.Chapter;
  import metacraft.ovvar.content.Patches;
  import metacraft.ovvar.content.Piece;
  import metacraft.ovvar.content.Spot;
  import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
  import net.minecraft.data.CachedOutput;
  import net.minecraft.data.DataProvider;

  import java.io.IOException;
  import java.io.UncheckedIOException;
  import java.nio.file.Path;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.Locale;
  import java.util.concurrent.CompletableFuture;

  /**
   * The Blockbench plugin's copy of the model, as data. {@code tools/blockbench/ovvar.js} draws a
   * patch on an ovve without a game running, which means it has to know everything
   * {@link Spot}, {@link Patches} and {@link Chapter} know — where every cell is, which art each
   * cell shows, how the shader squeezes a strip round an inflated box. Rewriting that in
   * JavaScript would be two tables to keep in step; writing it out here is one, and it is
   * committed with the rest of {@code src/main/generated}, so the same {@code runDatagen} check CI
   * already runs catches a plugin that has fallen behind the enums.
   *
   * <p>Beside the manifest: a PNG of every art nobody drew ({@link Patches.Art#generated}), so the
   * plugin can show today's catalogue whole, and so the scaler it ports
   * ({@link Tex#downscaled}) has a golden to be held to. Nothing else is written — the plugin
   * reads the placement textures, the trims and the garment layers the generator already writes.
   */
  public final class BlockbenchManifest implements DataProvider {
  	/** Bumped when the schema changes in a way an older plugin could not read; the plugin refuses a newer one. */
  	public static final int VERSION = 1;
  	/** Under {@code src/main/generated}, beside {@code ovvar/outlines.json}; on the classpath at runtime. */
  	public static final String DIR = Ovvar.MOD_ID + "/blockbench/";
  	public static final String RESOURCE = "/" + DIR + "manifest.json";

  	/**
  	 * How many rows of {@link Spot#anchored} the manifest pins. The grid sweeps {@code skinX} in
  	 * eighths of a texel across the whole layout — sample {@code k} is
  	 * {@code (k / 8.0, k % 2 == 0 ? 0.5 : 1.0, k % 4)} — so 512 rows reach every strip (legs, body,
  	 * arms), both inflations, all four anchor faces, and the fractional columns between texels,
  	 * which is where the squeeze's arithmetic actually differs.
  	 */
  	public static final int SAMPLES = 512;

  	public static double sampleSkinX(int k) {
  		return k / 8.0;
  	}

  	public static double sampleInflate(int k) {
  		return k % 2 == 0 ? 0.5 : 1.0;
  	}

  	public static int sampleAnchor(int k) {
  		return k % 4;
  	}

  	private static final int D = Spot.DETAIL, W = 64 * D, H = 32 * D;
  	/** Reference art due for removal: the plugin does not offer them. */
  	private static final String POLYMITER = "_polymiter";

  	private final Path root;
  	private final List<CompletableFuture<?>> writes = new ArrayList<>();
  	private CachedOutput out;

  	public BlockbenchManifest(FabricPackOutput output) {
  		this.root = output.getOutputFolder();
  	}

  	@Override
  	public String getName() {
  		return "Ovvar Blockbench manifest";
  	}

  	@Override
  	public CompletableFuture<?> run(CachedOutput output) {
  		this.out = output;
  		writes.clear();
  		JsonObject m = new JsonObject();
  		m.addProperty("version", VERSION);
  		m.addProperty("detail", Spot.DETAIL);
  		m.addProperty("faceRow", Spot.FACE_ROW);
  		m.addProperty("faceRows", Spot.FACE_ROWS);
  		m.addProperty("topRow", Spot.TOP_ROW);
  		m.addProperty("topRows", Spot.TOP_ROWS);
  		m.addProperty("topFace", Spot.TOP_FACE);
  		m.addProperty("mirrorShift", Spot.MIRROR_SHIFT);
  		m.addProperty("cellSize", Spot.SIZE);
  		m.addProperty("bigCell", Spot.BIG);
  		m.addProperty("px", Spot.PX);
  		m.addProperty("maxArt", Patches.MAX_ART);
  		m.addProperty("overMax", Patches.OVER_MAX);
  		m.addProperty("seatHeightMax", Patches.SEAT_HEIGHT_MAX);
  		m.addProperty("icon", Patches.ICON);
  		m.add("skin", ints(64, 32));
  		m.add("texture", ints(W, H));
  		JsonObject inflate = new JsonObject();
  		for (Piece piece : Piece.values()) inflate.addProperty(piece.id, Spot.inflate(piece));
  		m.add("inflate", inflate);
  		m.add("markerTexels", markerTexels());
  		m.add("skinBoxes", skinBoxes());
  		m.add("chapters", chapters());
  		m.add("cells", cells());
  		m.add("patches", patches());
  		m.add("anchoredSamples", anchoredSamples());
  		// Spot.stacked has no table to write out — it is a sort — so the manifest states the rule
  		// and the plugin's own stacking test is what holds it.
  		m.addProperty("stackedOrder", "layer ascending, then sewing order");
  		json(root.resolve(DIR + "manifest.json"), m);
  		Ovvar.LOGGER.info("[{} datagen] Blockbench manifest: {} cells, {} patches, {} anchored samples",
  				Ovvar.MOD_ID, Spot.values().length, Patches.all().size(), SAMPLES);
  		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
  	}

  	/**
  	 * The texels every garment and placement texture carries for {@code ovvar.glsl} — the marker,
  	 * its kind, the layer's inflation, the debug palette, and the texel the shader promises is
  	 * always clear. The plugin composes the cloth, not the shader's contract, so its goldens are
  	 * compared everywhere <em>but</em> here; writing the list out means a texel moved in
  	 * {@link GeneratedAssets} moves in the plugin's tests too.
  	 */
  	private static JsonArray markerTexels() {
  		JsonArray out = new JsonArray();
  		for (int i = 0; i < GeneratedAssets.DEBUG.length; i++) out.add(ints(GeneratedAssets.DEBUG_X + i, GeneratedAssets.MARKER_Y));
  		out.add(ints(GeneratedAssets.LAYER_X, GeneratedAssets.MARKER_Y));
  		out.add(ints(GeneratedAssets.MARKER_KIND_X, GeneratedAssets.MARKER_Y));
  		out.add(ints(GeneratedAssets.MARKER_X, GeneratedAssets.MARKER_Y));
  		out.add(ints(GeneratedAssets.BLANK_X, GeneratedAssets.BLANK_Y));
  		return out;
  	}

  	private static JsonObject skinBoxes() {
  		JsonObject out = new JsonObject();
  		out.add("body", ints(GeneratedAssets.BODY));
  		out.add("rightArm", ints(GeneratedAssets.RIGHT_ARM));
  		out.add("rightLeg", ints(GeneratedAssets.RIGHT_LEG));
  		out.add("bodyOuter", ints(GeneratedAssets.BODY_OUTER));
  		out.add("rightArmOuter", ints(GeneratedAssets.RIGHT_ARM_OUTER));
  		out.add("rightLegOuter", ints(GeneratedAssets.RIGHT_LEG_OUTER));
  		out.add("leftArm", ints(GeneratedAssets.LEFT_ARM));
  		out.add("leftArmOuter", ints(GeneratedAssets.LEFT_ARM_OUTER));
  		out.add("leftLeg", ints(GeneratedAssets.LEFT_LEG));
  		out.add("leftLegOuter", ints(GeneratedAssets.LEFT_LEG_OUTER));
  		out.add("waist", ints(GeneratedAssets.WAIST));
  		return out;
  	}

  	/**
  	 * The chapters the plugin offers, with the layer textures the generator already writes for
  	 * each: the plugin loads those rather than cutting the overlays again, so a chapter whose
  	 * cloth is tinted or hand-drawn (PolymITer's leggings) costs it nothing.
  	 */
  	private static JsonArray chapters() {
  		JsonArray out = new JsonArray();
  		for (Chapter chapter : Chapter.values()) {
  			if (chapter.id.endsWith(POLYMITER)) continue;
  			JsonObject c = new JsonObject();
  			c.addProperty("id", chapter.id);
  			c.addProperty("name", chapter.name);
  			c.addProperty("art", chapter.overlay + ".png");
  			c.addProperty("nercabbad", chapter.nercabbadOverlay == null ? null : chapter.nercabbadOverlay + ".png");
  			if (chapter.tint == null) c.add("tint", com.google.gson.JsonNull.INSTANCE); else c.addProperty("tint", chapter.tint);
  			c.addProperty("rollable", chapter.rollable);
  			JsonObject layers = new JsonObject();
  			layers.addProperty("top", Piece.TOP.layer + "/" + chapter.id + "/top.png");
  			layers.addProperty("bottom", Piece.BOTTOM.layer + "/" + chapter.id + "/bottom.png");
  			layers.addProperty("bottomNercabbad", chapter.rollable
  					? Piece.BOTTOM.layer + "/" + chapter.id + "/bottom_nercabbad.png" : null);
  			c.add("layers", layers);
  			out.add(c);
  		}
  		return out;
  	}

  	/** The cell table, in {@link Spot}'s own order — which is the order the game test compares. */
  	private static JsonArray cells() {
  		JsonArray out = new JsonArray();
  		for (Spot spot : Spot.values()) {
  			JsonObject c = new JsonObject();
  			c.addProperty("id", spot.id());
  			c.addProperty("piece", spot.piece.id);
  			c.addProperty("layerFolder", spot.piece.layer);
  			c.addProperty("u", spot.u);
  			c.addProperty("v", spot.v);
  			c.addProperty("w", spot.width);
  			c.addProperty("h", spot.height);
  			c.addProperty("side", spot.side.name().toLowerCase(Locale.ROOT));
  			c.addProperty("top", spot.top());
  			c.addProperty("face", Spot.face(spot));
  			c.addProperty("layer", spot.layer());
  			c.addProperty("stripStart", Spot.stripStart(spot));
  			c.addProperty("stripWidth", Spot.stripWidth(spot));
  			c.addProperty("fit", Patches.Fit.of(spot).name().toLowerCase(Locale.ROOT));
  			c.addProperty("label", spot.label());
  			out.add(c);
  		}
  		return out;
  	}

  	/**
  	 * The catalogue, and — as a side effect — a PNG of every art nobody drew. A generated art has
  	 * no file anywhere in the source tree ({@link Patches.Art#resource} says so out loud), so this
  	 * is the only place its pixels are ever written down, and the plugin's ported scaler is held
  	 * to them.
  	 */
  	private JsonArray patches() {
  		JsonArray out = new JsonArray();
  		for (Patches.Patch patch : Patches.all()) {
  			JsonObject p = new JsonObject();
  			p.addProperty("id", patch.id());
  			p.addProperty("name", patch.name());
  			p.addProperty("seat", patch.seat());
  			p.addProperty("w", patch.width());
  			p.addProperty("h", patch.height());
  			p.addProperty("artist", patch.artist());
  			JsonArray arts = new JsonArray();
  			for (Patches.Art art : patch.variants()) {
  				JsonObject a = new JsonObject();
  				a.addProperty("file", art.file() + ".png");
  				a.addProperty("w", art.width());
  				a.addProperty("h", art.height());
  				a.addProperty("default", art.byDefault());
  				a.addProperty("generated", art.generated());
  				a.addProperty("source", art.generated() ? art.source().file() + ".png" : null);
  				arts.add(a);
  				if (art.generated()) {
  					String name = art.file().substring(art.file().lastIndexOf('/') + 1);
  					png(root.resolve(DIR + "art/" + name + ".png"), Tex.art(art));
  				}
  			}
  			p.add("arts", arts);
  			JsonObject fits = new JsonObject();
  			for (Patches.Fit fit : Patches.Fit.values()) {
  				fits.addProperty(fit.name().toLowerCase(Locale.ROOT), Patches.artFor(patch, fit).file() + ".png");
  			}
  			p.add("fits", fits);
  			out.add(p);
  		}
  		return out;
  	}

  	private static JsonArray anchoredSamples() {
  		JsonArray out = new JsonArray();
  		for (int k = 0; k < SAMPLES; k++) {
  			double skinX = sampleSkinX(k), inflate = sampleInflate(k);
  			int anchor = sampleAnchor(k);
  			JsonArray row = new JsonArray();
  			row.add(skinX);
  			row.add(inflate);
  			row.add(anchor);
  			row.add(Spot.anchored(skinX, inflate, anchor));
  			out.add(row);
  		}
  		return out;
  	}

  	private static JsonArray ints(int... values) {
  		JsonArray out = new JsonArray();
  		for (int v : values) out.add(v);
  		return out;
  	}

  	private void json(Path path, JsonElement element) {
  		writes.add(DataProvider.saveStable(out, element, path));
  	}

  	private void png(Path path, Tex tex) {
  		byte[] data = tex.png();
  		writes.add(CompletableFuture.runAsync(() -> {
  			try {
  				out.writeIfNeeded(path, data, Hashing.sha1().hashBytes(data));
  			} catch (IOException e) {
  				throw new UncheckedIOException(e);
  			}
  		}));
  	}
  }
  ```

- [ ] **Step 5: Register the provider**

  Replace the body of `mods/ovvar/src/main/java/metacraft/ovvar/datagen/OvvarDataGenerator.java`:
  ```java
  	@Override
  	public void onInitializeDataGenerator(FabricDataGenerator generator) {
  		var pack = generator.createPack();
  		pack.addProvider(GeneratedAssets::new);
  		// The Blockbench plugin's copy of Spot/Patches/Chapter, so it can draw a patch the way the
  		// game does without a game running.
  		pack.addProvider(BlockbenchManifest::new);
  	}
  ```

- [ ] **Step 6: Compile and checkstyle**

  ```sh
  ./gradlew :mods:ovvar:compileJava :mods:ovvar:checkstyleMain
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run datagen and inspect what appeared**

  ```sh
  ./gradlew :mods:ovvar:runDatagen
  ls mods/ovvar/src/main/generated/ovvar/blockbench/
  ls mods/ovvar/src/main/generated/ovvar/blockbench/art/
  ```
  Expected: `art  manifest.json`, and the art directory holding exactly `it_8x8.png` — today's only generated size (`it` ships a 12×12 default and a 16×16 drawing, so only its 8×8 is scaled; `itk` ships all three and generates nothing, and seat patches never generate).

  Sanity-check the manifest:
  ```sh
  node -e '
  const m = require("./mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json");
  console.log(m.version, m.cells.length, m.patches.length, m.chapters.length, m.anchoredSamples.length);
  console.log(m.cells[0]);
  console.log(m.patches.find(p => p.id === "it").fits);
  '
  ```
  Expected:
  ```
  1 34 10 4 512
  { id: 'front_top_left', piece: 'top', layerFolder: 'humanoid', u: 20, v: 21, w: 4, h: 4, side: 'body', top: false, face: 1, layer: 0, stripStart: 16, stripWidth: 24, fit: 'over', label: 'chest, top left' }
  { over: 'patches/it.png', clipped: 'patches/it_8x8.png', filled: 'patches/it_16x16.png' }
  ```

- [ ] **Step 8: Run the game tests**

  ```sh
  JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:ovvar:runServer -PrunDir=/tmp/ovvar-gametest-bb
  ```
  Expected: the run ends with all tests passed and the three new names (`theManifestsCellTableIsSpot`, `theManifestsAnchoredSamplesAreSpotAnchored`, `theManifestsCatalogueIsPatchesAndChapter`) among them, none failed.

- [ ] **Step 9: Commit**

  ```sh
  git add mods/ovvar/src/main/java/metacraft/ovvar/datagen/BlockbenchManifest.java
  git add mods/ovvar/src/main/java/metacraft/ovvar/datagen/GeneratedAssets.java
  git add mods/ovvar/src/main/java/metacraft/ovvar/datagen/OvvarDataGenerator.java
  git add mods/ovvar/src/main/java/metacraft/ovvar/gametest/BlockbenchManifestTests.java
  git add mods/ovvar/src/main/resources/fabric.mod.json
  git add mods/ovvar/src/main/generated/ovvar/blockbench
  git commit -m "ovvar: datagen writes a Blockbench manifest of the cells, patches and chapters"
  ```

---

## Task 3: `src/10-compose.js` part 1 — the image type and `anchored`

**Files:**
- Create: `mods/ovvar/tools/blockbench/src/10-compose.js`
- Modify: `mods/ovvar/tools/blockbench/test.js` (append two tests)

**Interfaces:**
- Consumes: `OVVAR.loadManifest`, `OVVAR.artPath`, `io.decode`, `io.read` (Task 1).
- Produces:
  - `OVVAR.tex.blank(w, h) -> image`
  - `OVVAR.tex.get(im, x, y) -> argb`
  - `OVVAR.tex.set(im, x, y, argb)` *(mutates; only ever called on an image the caller just made)*
  - `OVVAR.tex.copy(im) -> image`
  - `OVVAR.tex.blit(dst, src, sx, sy, w, h, dx, dy) -> image` *(returns a new image; source alpha replaces, alpha 0 skipped — `Tex.blit`)*
  - `OVVAR.tex.crop(im, x, y, w, h) -> image`
  - `OVVAR.tex.flipXRect(im, x, y, w, h) -> image`
  - `OVVAR.tex.flipX(im) -> image`
  - `OVVAR.tex.scale(im, factor) -> image`
  - `OVVAR.tex.composite(base, over) -> image`
  - `OVVAR.tex.isEmpty(im) -> boolean`
  - `OVVAR.tex.diff(a, b, ignore) -> string[]` *(`ignore` is an array of `[x, y]`; returns up to 12 human-readable differences, empty when equal)*
  - `OVVAR.compose.anchored(skinX, inflate, anchor) -> number`

- [ ] **Step 1: Write the failing tests**

  Append to `mods/ovvar/tools/blockbench/test.js`:
  ```js
  test('the image ops behave like Tex', () => {
    const t = OVVAR.tex;
    const a = t.blank(4, 3);
    assert.strictEqual(a.w, 4);
    assert.strictEqual(a.h, 3);
    assert.strictEqual(t.get(a, 1, 1), 0);
    t.set(a, 1, 1, 0xFF804020);
    assert.strictEqual(t.get(a, 1, 1), 0xFF804020);
    // A texel written at alpha 0 keeps its colour: datagen's `placed` copies any non-zero packed
    // ARGB, so those texels are part of the goldens and may not be flattened away.
    t.set(a, 2, 1, 0x0000FF00);
    assert.strictEqual(t.get(a, 2, 1), 0x0000FF00);
    // blit skips alpha-0 source texels (Tex.blit tests a(p) > 0), so 0x0000FF00 does not travel.
    const b = t.blit(t.blank(4, 3), a, 0, 0, 4, 3, 0, 0);
    assert.strictEqual(t.get(b, 1, 1), 0xFF804020);
    assert.strictEqual(t.get(b, 2, 1), 0);
    // flipX mirrors the whole image; flipXRect only the rectangle.
    const f = t.flipX(a);
    assert.strictEqual(t.get(f, 2, 1), 0xFF804020);
    // scale is nearest-neighbour by an integer factor.
    const s = t.scale(a, 2);
    assert.strictEqual(s.w, 8);
    assert.strictEqual(s.h, 6);
    assert.strictEqual(t.get(s, 3, 3), 0xFF804020);
    assert.strictEqual(t.get(s, 2, 2), 0xFF804020);
    // crop lifts a rectangle out; the source's alpha-0 texels do not survive blit, as above.
    const c = t.crop(a, 1, 1, 2, 1);
    assert.strictEqual(c.w, 2);
    assert.strictEqual(t.get(c, 0, 0), 0xFF804020);
    assert.deepStrictEqual(t.diff(a, t.copy(a), []), []);
  });

  test('anchored matches all 512 manifest samples exactly', () => {
    const m = OVVAR.loadManifest(io, CHECKOUT);
    assert.strictEqual(m.anchoredSamples.length, 512);
    let worst = 0;
    for (const [skinX, inflate, anchor, want] of m.anchoredSamples) {
      const got = OVVAR.compose.anchored(skinX, inflate, anchor);
      worst = Math.max(worst, Math.abs(got - want));
    }
    assert.ok(worst < 1e-9, 'anchored is off by ' + worst);
  });
  ```

- [ ] **Step 2: Run and watch it fail**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected failure: `TypeError: Cannot read properties of undefined (reading 'blank')` — `OVVAR.tex` does not exist.

- [ ] **Step 3: Write the image ops and `anchored`**

  Create `mods/ovvar/tools/blockbench/src/10-compose.js`:
  ```js
  // The port of metacraft.ovvar.datagen.Tex, GeneratedAssets and content.Spot, as plain functions
  // on {w, h, data} images. No Blockbench types in this file: node test.js runs it as it is, and
  // its goldens are the very PNGs the mod ships.

  OVVAR.tex = {
    blank: function (w, h) {
      return {w: w, h: h, data: new Uint8Array(w * h * 4)};
    },

    copy: function (im) {
      return {w: im.w, h: im.h, data: im.data.slice()};
    },

    // Packed ARGB, the way Tex keeps a texel. Reading a texel that is transparent but coloured
    // gives back its colour: Tex does, and `placed` branches on the packed value being non-zero.
    get: function (im, x, y) {
      var i = (y * im.w + x) * 4;
      return ((im.data[i + 3] << 24) | (im.data[i] << 16) | (im.data[i + 1] << 8) | im.data[i + 2]) >>> 0;
    },

    set: function (im, x, y, argb) {
      var i = (y * im.w + x) * 4;
      im.data[i] = (argb >>> 16) & 255;
      im.data[i + 1] = (argb >>> 8) & 255;
      im.data[i + 2] = argb & 255;
      im.data[i + 3] = (argb >>> 24) & 255;
    },

    a: function (p) { return (p >>> 24) & 255; },
    r: function (p) { return (p >>> 16) & 255; },
    g: function (p) { return (p >>> 8) & 255; },
    b: function (p) { return p & 255; },

    /** Tex.blit: source alpha replaces, no blending, alpha-0 source texels left alone. */
    blit: function (dst, src, sx, sy, w, h, dx, dy) {
      if (sx + w > src.w || sy + h > src.h || dx + w > dst.w || dy + h > dst.h) {
        throw new Error('blit outside bounds: ' + w + 'x' + h + ' from (' + sx + ',' + sy + ') to (' + dx + ',' + dy + ')');
      }
      var out = OVVAR.tex.copy(dst);
      for (var y = 0; y < h; y++) {
        for (var x = 0; x < w; x++) {
          var p = OVVAR.tex.get(src, sx + x, sy + y);
          if (OVVAR.tex.a(p) > 0) OVVAR.tex.set(out, dx + x, dy + y, p);
        }
      }
      return out;
    },

    crop: function (im, x, y, w, h) {
      return OVVAR.tex.blit(OVVAR.tex.blank(w, h), im, x, y, w, h, 0, 0);
    },

    flipXRect: function (im, x, y, w, h) {
      var out = OVVAR.tex.copy(im);
      for (var yy = y; yy < y + h; yy++) {
        for (var i = 0; i < w; i++) OVVAR.tex.set(out, x + i, yy, OVVAR.tex.get(im, x + (w - 1 - i), yy));
      }
      return out;
    },

    flipX: function (im) {
      return OVVAR.tex.flipXRect(im, 0, 0, im.w, im.h);
    },

    /** Nearest-neighbour upscale by an integer factor (Tex.scale). */
    scale: function (im, factor) {
      var w = im.w * factor, h = im.h * factor;
      var out = OVVAR.tex.blank(w, h);
      for (var y = 0; y < h; y++) {
        for (var x = 0; x < w; x++) {
          OVVAR.tex.set(out, x, y, OVVAR.tex.get(im, (x / factor) | 0, (y / factor) | 0));
        }
      }
      return out;
    },

    /** Tex.composite: `over` alpha-composited on top of `base`, same size, rounding as Java's. */
    composite: function (base, over) {
      if (over.w !== base.w || over.h !== base.h) throw new Error('size mismatch');
      var out = OVVAR.tex.blank(base.w, base.h);
      for (var y = 0; y < base.h; y++) {
        for (var x = 0; x < base.w; x++) {
          var bp = OVVAR.tex.get(base, x, y), tp = OVVAR.tex.get(over, x, y);
          var ta = OVVAR.tex.a(tp) / 255, ba = OVVAR.tex.a(bp) / 255;
          var oa = ta + ba * (1 - ta);
          if (oa === 0) continue;
          var rr = Math.round((OVVAR.tex.r(tp) * ta + OVVAR.tex.r(bp) * ba * (1 - ta)) / oa);
          var gg = Math.round((OVVAR.tex.g(tp) * ta + OVVAR.tex.g(bp) * ba * (1 - ta)) / oa);
          var bb = Math.round((OVVAR.tex.b(tp) * ta + OVVAR.tex.b(bp) * ba * (1 - ta)) / oa);
          OVVAR.tex.set(out, x, y, (Math.round(oa * 255) << 24 | rr << 16 | gg << 8 | bb) >>> 0);
        }
      }
      return out;
    },

    isEmpty: function (im) {
      for (var i = 3; i < im.data.length; i += 4) if (im.data[i] !== 0) return false;
      return true;
    },

    /**
     * Up to twelve differences between two images, as readable lines; [] when they are the same.
     * `ignore` is a list of [x, y] the comparison skips -- the marker texels the manifest names,
     * which are the shader's contract and not part of the cloth.
     */
    diff: function (a, b, ignore) {
      if (a.w !== b.w || a.h !== b.h) return ['sizes differ: ' + a.w + 'x' + a.h + ' vs ' + b.w + 'x' + b.h];
      var skip = {};
      for (var i = 0; i < (ignore || []).length; i++) skip[ignore[i][0] + ',' + ignore[i][1]] = true;
      var out = [];
      for (var y = 0; y < a.h && out.length < 12; y++) {
        for (var x = 0; x < a.w && out.length < 12; x++) {
          if (skip[x + ',' + y]) continue;
          var pa = OVVAR.tex.get(a, x, y), pb = OVVAR.tex.get(b, x, y);
          if (pa !== pb) {
            out.push('(' + x + ',' + y + ') ' + pa.toString(16).padStart(8, '0') + ' != ' + pb.toString(16).padStart(8, '0'));
          }
        }
      }
      return out;
    }
  };

  OVVAR.compose = {};

  /** Spot.pixel: the model unit a layer's texel is drawn at (ovvar_pixel in ovvar.glsl). */
  OVVAR.compose.pixel = function (skinX, inflate) {
    return skinX < 16 ? 13.0 / 12 : (12 + 2 * inflate) / 12;
  };

  /**
   * Spot.anchored, line for line: the strip-local texel column shown at side-row texel `skinX`
   * when the art is anchored on face `anchor` of an `inflate`-inflated box, or -1 in the slack on
   * the opposite face. This is what makes art bend round a corner, and it is baked into the trim
   * textures, so it is pinned by 512 samples in the manifest.
   */
  OVVAR.compose.anchored = function (skinX, inflate, anchor) {
    var p = OVVAR.compose.pixel(Math.floor(skinX), inflate), e = 2 * inflate;
    var body = skinX >= 16 && skinX < 40;
    var stripStart = skinX < 16 ? 0 : body ? 16 : 40;
    var local = skinX - stripStart;
    var n1 = body ? 8 : 4, total = 8 + 2 * n1;
    var perimeter = total + 4 * e;
    var k = local < 4 ? 0 : local < 4 + n1 ? 1 : local < 8 + n1 ? 2 : 3;
    var s = [0, 4, 4 + n1, 8 + n1], n = [4, n1, 4, n1], U = [0, 4 + e, 4 + n1 + 2 * e, 8 + n1 + 3 * e];
    var u = U[k] + (local - s[k]) * ((n[k] + e) / n[k]);
    var c = U[anchor] + (n[anchor] + e) / 2, t = s[anchor] + n[anchor] / 2.0;
    var du = u - c;
    if (du >= perimeter / 2) du -= perimeter; else if (du < -perimeter / 2) du += perimeter;
    var dt = du / p;
    if (Math.abs(dt) > total / 2.0) return -1;
    var w = t + dt;
    return w - Math.floor(w / total) * total;
  };
  ```

- [ ] **Step 4: Run — both new tests must pass**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 5`, `# fail 0`.

- [ ] **Step 5: Commit**

  ```sh
  git add mods/ovvar/tools/blockbench/src/10-compose.js mods/ovvar/tools/blockbench/test.js
  git commit -m "ovvar: the plugin's image ops and the anchored squeeze, held to the manifest's samples"
  ```

---

## Task 4: `placed`, the seat halves and `artFor` — the placement golden

268 committed PNGs (33 non-seat cells × 8 non-seat patches, plus 2 seat patches × 2 halves) are the whole of `placed`, `artFor` and the seat cut. This task reproduces every one of them.

**Files:**
- Modify: `mods/ovvar/tools/blockbench/src/10-compose.js` (append)
- Modify: `mods/ovvar/tools/blockbench/test.js` (append one test + a shared `ctx` helper)

**Interfaces:**
- Consumes: `OVVAR.tex.*`, `OVVAR.compose.anchored`, the manifest.
- Produces:
  - `OVVAR.compose.ctx(io, m) -> ctx` with `ctx.m`, `ctx.art(file) -> image` (memoised), `ctx.base(chapterId, piece, nercabbad) -> image`, `ctx.generated(relPath) -> image` (any PNG under `src/main/generated/assets/ovvar/textures/`)
  - `OVVAR.compose.artFor(m, patch, cell) -> art` — `m.artByFile[patch.fits[cell.fit]]`
  - `OVVAR.compose.offsetX(m, cell, art) -> number` — `Patches.Art.offsetX`: `Math.trunc((cell.w * m.detail - art.w) / 2)`. Java's integer division truncates toward zero, and the numerator is negative whenever the art hangs over its cell (a 12 px art on a 4-texel cell gives -2), so `Math.trunc` and not `Math.floor`.
  - `OVVAR.compose.seatHalf(m, side) -> number` — `(side === 'left' ? 0 : 1) * m.px`
  - `OVVAR.compose.placed(m, cell, art, x) -> image` (128×64)
  - `OVVAR.compose.placementTexture(ctx, cell, patch, side) -> image` — the whole of one committed placement PNG **without** the marker texels

- [ ] **Step 1: Write the failing test**

  Append to `mods/ovvar/tools/blockbench/test.js`:
  ```js
  function context() {
    const m = OVVAR.loadManifest(io, CHECKOUT);
    return OVVAR.compose.ctx(io, m);
  }

  test('every committed placement texture is placed() + artFor()', () => {
    const ctx = context();
    const m = ctx.m;
    const ignore = m.markerTexels;
    const bad = [];
    let checked = 0;
    for (const cell of m.cells) {
      for (const patch of m.patches) {
        if (patch.seat !== (cell.id === 'seat')) continue;
        const dir = 'entity/equipment/' + cell.layerFolder + '/';
        const targets = cell.id === 'seat'
          ? [['right', dir + 'patch/seat/' + patch.id + '_r.png'],
             ['left', dir + 'patch/seat/' + patch.id + '_l.png']]
          : [[cell.side, dir + 'patch/' + cell.id + '/' + patch.id + '.png']];
        for (const [side, file] of targets) {
          const want = ctx.generated(file);
          const got = OVVAR.compose.placementTexture(ctx, cell, patch, side);
          const d = OVVAR.tex.diff(want, got, ignore);
          if (d.length) bad.push(file + ': ' + d.join('; '));
          checked++;
        }
      }
    }
    assert.strictEqual(checked, 268, 'expected 268 placement textures, walked ' + checked);
    assert.deepStrictEqual(bad, []);
  });
  ```

- [ ] **Step 2: Run and watch it fail**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected failure: `TypeError: OVVAR.compose.ctx is not a function`.

- [ ] **Step 3: Append `ctx`, `artFor`, `placed` and `placementTexture`**

  Append to `mods/ovvar/tools/blockbench/src/10-compose.js`:
  ```js
  /**
   * Everything compose() needs from the world: the manifest, and two ways of getting a PNG out of
   * the checkout -- a patch art by its catalogue file name, and any texture the generator already
   * wrote. Both memoised, because compose() runs on every brush stroke.
   */
  OVVAR.compose.ctx = function (io, m) {
    var arts = {}, gen = {};
    var GENROOT = 'mods/ovvar/src/main/generated/assets/ovvar/textures/';
    var ctx = {
      m: m,
      io: io,
      warnings: [],
      art: function (file) {
        if (!arts[file]) {
          var entry = m.artByFile[file];
          if (!entry) throw new Error('no art named ' + file + ' in the manifest');
          var path = OVVAR.artPath(m, m.checkout, entry);
          if (!io.exists(path)) {
            ctx.warnings.push('missing art file: ' + path);
            arts[file] = OVVAR.compose.placeholder(entry.w, entry.h);
          } else {
            arts[file] = io.decode(io.read(path));
          }
        }
        return arts[file];
      },
      generated: function (rel) {
        if (!gen[rel]) gen[rel] = io.decode(io.read([m.checkout, GENROOT + rel].join('/')));
        return gen[rel];
      },
      base: function (chapterId, piece, nercabbad) {
        var chapter = m.chapterById[chapterId];
        if (!chapter) throw new Error('no chapter ' + chapterId + ' in the manifest');
        var key = nercabbad ? 'bottomNercabbad' : piece;
        var rel = chapter.layers[key];
        if (!rel) throw new Error(chapter.name + ' has no ' + key + ' layer (it does not roll down)');
        return ctx.generated('entity/equipment/' + rel);
      },
      forget: function () { arts = {}; gen = {}; ctx.warnings = []; }
    };
    return ctx;
  };

  /** A missing art file: solid magenta, so it is obvious on the model and in the catalogue list. */
  OVVAR.compose.placeholder = function (w, h) {
    var out = OVVAR.tex.blank(w, h);
    for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) OVVAR.tex.set(out, x, y, 0xFFFF00FF);
    return out;
  };

  /** Patches.artFor(patch, spot): the fit is the cell's, and the manifest has already worked it out. */
  OVVAR.compose.artFor = function (m, patch, cell) {
    var file = patch.fits[cell.fit];
    var art = m.artByFile[file];
    if (!art) throw new Error('no art named ' + file + ' in the manifest');
    return art;
  };

  /** Patches.Art.offsetX: the art centred in the cell, which is not always one cell wide. */
  OVVAR.compose.offsetX = function (m, cell, art) {
    return Math.trunc((cell.w * m.detail - art.w) / 2);
  };

  /** Spot.seatHalf: where a seat patch's art is cut for one leg, in art pixels. */
  OVVAR.compose.seatHalf = function (m, side) {
    return (side === 'left' ? 0 : 1) * m.px;
  };

  /**
   * GeneratedAssets.placed: the art on a garment texture at texel column x (its top-left; the
   * cell's row, centred vertically), clipped to the part's side rows and wrapped round the part's
   * strip -- past the outer face of a limb lies its back face. A cell on a box's top face (the
   * shoulders) is clipped to the face both ways instead, since the top face has no neighbour in
   * the layout to continue onto.
   *
   * Returns null instead of throwing when the art lands entirely off the cell -- datagen's
   * `require` is a build failure there, but a plugin has a panel to warn in and a model to keep
   * drawing.
   */
  OVVAR.compose.placed = function (m, cell, art, x) {
    var D = m.detail, W = m.texture[0], H = m.texture[1];
    var y = cell.v * D + Math.trunc((cell.h * D - art.h) / 2);
    var out = OVVAR.tex.blank(W, H);
    var any = false, ax, row, p;
    if (cell.top) {
      var x0 = cell.u * D, x1 = x0 + cell.w * D, y0 = m.topRow * D, y1 = m.faceRow * D;
      for (ax = 0; ax < art.w; ax++) {
        var column = x + ax;
        if (column < x0 || column >= x1) continue;
        for (row = Math.max(y, y0); row < Math.min(y + art.h, y1); row++) {
          p = OVVAR.tex.get(art, ax, row - y);
          if (p !== 0) { OVVAR.tex.set(out, column, row, p); any = true; }
        }
      }
      return any ? out : null;
    }
    var stripStart = cell.stripStart * D, stripWidth = cell.stripWidth * D;
    if (art.w > stripWidth) throw new Error('patch art is wider than the ' + cell.id + " cell's part");
    for (ax = 0; ax < art.w; ax++) {
      var col = stripStart + (((x + ax - stripStart) % stripWidth) + stripWidth) % stripWidth;
      for (row = Math.max(y, m.faceRow * D); row < Math.min(y + art.h, (m.faceRow + m.faceRows) * D); row++) {
        p = OVVAR.tex.get(art, ax, row - y);
        if (p !== 0) { OVVAR.tex.set(out, col, row, p); any = true; }
      }
    }
    return any ? out : null;
  };

  /**
   * One committed placement texture, minus the marker texels: the art this cell shows, mirrored
   * for a left limb (the model mirrors it back), cut in half for the seat. `side` is 'body',
   * 'right' or 'left' -- for the seat it is the leg being drawn, for anything else the cell's own.
   */
  OVVAR.compose.placementTexture = function (ctx, cell, patch, side) {
    var m = ctx.m;
    var entry = OVVAR.compose.artFor(m, patch, cell);
    var art = ctx.art(entry.file);
    if (cell.side === 'seat') {
      // The art is drawn as seen from behind, so its left half belongs on the wearer's LEFT leg;
      // that half is then flipped in x, because the model flips the left leg's texture back.
      var half = OVVAR.tex.crop(art, OVVAR.compose.seatHalf(m, side), 0, m.px, art.h);
      if (side === 'left') half = OVVAR.tex.flipX(half);
      return OVVAR.compose.placed(m, cell, half, cell.u * m.detail);
    }
    var drawn = cell.side === 'left' ? OVVAR.tex.flipX(art) : art;
    return OVVAR.compose.placed(m, cell, drawn, cell.u * m.detail + OVVAR.compose.offsetX(m, cell, entry));
  };
  ```

- [ ] **Step 4: Run — the placement golden must pass**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 6`, `# fail 0`, and the placement test reports no differences across all 268 files.

  If it fails on the seat only, check the half/flip convention against `Spot.seatColumn`'s javadoc: `_r` is the art's **right** half as drawn, `_l` the art's **left** half flipped in x.

- [ ] **Step 5: Commit**

  ```sh
  git add mods/ovvar/tools/blockbench/src/10-compose.js mods/ovvar/tools/blockbench/test.js
  git commit -m "ovvar: the plugin places a patch on a cell exactly as the pack's textures do"
  ```

---

## Task 5: `placedWrapped` — the trim golden

56 committed trim textures (7 body cells of the top × 8 non-seat patches) are `placedWrapped` outputs, written raw: no `marked`, no `sided`, so the comparison is every texel.

**Files:**
- Modify: `mods/ovvar/tools/blockbench/src/10-compose.js` (append)
- Modify: `mods/ovvar/tools/blockbench/test.js` (append one test)

**Interfaces:**
- Produces: `OVVAR.compose.placedWrapped(m, cell, art, x) -> image` (128×64, or null when `placed` returned null)

- [ ] **Step 1: Write the failing test**

  Append to `mods/ovvar/tools/blockbench/test.js`:
  ```js
  test('every committed trim texture is placedWrapped()', () => {
    const ctx = context();
    const m = ctx.m;
    const bad = [];
    let checked = 0;
    for (const cell of m.cells) {
      // Trims.fits: the top's own body box only -- vanilla draws a trim on both limbs and our
      // shader cannot hide one, so a limb cell can never be a trim.
      if (cell.piece !== 'top' || cell.side !== 'body') continue;
      for (const patch of m.patches) {
        if (patch.seat) continue;
        const file = 'trims/entity/' + cell.layerFolder + '/' + cell.id + '_' + patch.id + '.png';
        const want = ctx.generated(file);
        const entry = OVVAR.compose.artFor(m, patch, cell);
        const got = OVVAR.compose.placedWrapped(m, cell, ctx.art(entry.file),
          cell.u * m.detail + OVVAR.compose.offsetX(m, cell, entry));
        const d = OVVAR.tex.diff(want, got, []);
        if (d.length) bad.push(file + ': ' + d.join('; '));
        checked++;
      }
    }
    assert.strictEqual(checked, 56, 'expected 56 trim textures, walked ' + checked);
    assert.deepStrictEqual(bad, []);
  });
  ```

- [ ] **Step 2: Run and watch it fail**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected failure: `TypeError: OVVAR.compose.placedWrapped is not a function`.

- [ ] **Step 3: Append `placedWrapped`**

  Append to `mods/ovvar/tools/blockbench/src/10-compose.js`:
  ```js
  /**
   * GeneratedAssets.placedWrapped: `placed`, then the strip squeezed round the box the way the
   * shader does (`anchored`), baked column by column -- each column of the part's side rows shows
   * the art column the shader would sample there. Vanilla draws the trim channel, so this is the
   * only place the squeeze is written into pixels; it is also what the plugin draws a body cell's
   * art with, which is why art bends round the chest's corners in the preview.
   */
  OVVAR.compose.placedWrapped = function (m, cell, art, x) {
    var flat = OVVAR.compose.placed(m, cell, art, x);
    if (flat === null) return null;
    var D = m.detail, W = m.texture[0], H = m.texture[1];
    var stripStart = cell.stripStart * D, stripEnd = stripStart + cell.stripWidth * D;
    var inflate = m.inflate[cell.piece];
    var anchor = cell.face;
    var out = OVVAR.tex.blank(W, H);
    for (var column = stripStart; column < stripEnd; column++) {
      var w = OVVAR.compose.anchored((column + 0.5) / D, inflate, anchor);
      if (w < 0) continue;
      var texel = stripStart + Math.floor(w * D);
      for (var row = m.faceRow * D; row < (m.faceRow + m.faceRows) * D; row++) {
        var p = OVVAR.tex.get(flat, texel, row);
        if (p !== 0) OVVAR.tex.set(out, column, row, p);
      }
    }
    return out;
  };
  ```

- [ ] **Step 4: Run — the trim golden must pass**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 7`, `# fail 0`.

- [ ] **Step 5: Commit**

  ```sh
  git add mods/ovvar/tools/blockbench/src/10-compose.js mods/ovvar/tools/blockbench/test.js
  git commit -m "ovvar: the plugin bends a body patch round the box the way the trim textures do"
  ```

---

## Task 6: `downscale` — the scaler golden

`Tex.downscaled` is an area-weighted majority vote with two tie-breaks, and it must be ported exactly: a patch with a 16×16 drawing and no 8×8 one is scaled to fill a shoulder, and the plugin has to show the same pixels the pack does. Today's one generated art, `art/it_8x8.png` (written by Task 2 from `patches/it_16x16.png`), is the golden.

**Files:**
- Modify: `mods/ovvar/tools/blockbench/src/10-compose.js` (append)
- Modify: `mods/ovvar/tools/blockbench/test.js` (append two tests)

**Interfaces:**
- Produces: `OVVAR.compose.downscaled(im, w, h) -> image`

- [ ] **Step 1: Write the failing tests**

  Append to `mods/ovvar/tools/blockbench/test.js`:
  ```js
  test('downscale reproduces every generated art PNG', () => {
    const ctx = context();
    const m = ctx.m;
    const generated = [];
    for (const patch of m.patches) {
      for (const art of patch.arts) {
        if (!art.generated) continue;
        generated.push(art.file);
        const src = ctx.art(art.source);
        const got = OVVAR.compose.downscaled(src, art.w, art.h);
        const want = io.decode(io.read(OVVAR.artPath(m, CHECKOUT, art)));
        assert.deepStrictEqual(OVVAR.tex.diff(want, got, []), [], art.file + ' (from ' + art.source + ')');
      }
    }
    // One today: `it` ships a 12x12 default and a 16x16 drawing, so only its 8x8 is scaled.
    assert.deepStrictEqual(generated, ['patches/it_8x8.png']);
  });

  test('downscale votes by area, breaks ties to the rarer colour, and keeps the source palette', () => {
    const t = OVVAR.tex;
    const A = 0xFF112233, B = 0xFF445566;
    const src = t.blank(4, 4);
    for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) t.set(src, x, y, A);
    t.set(src, 0, 1, B);
    t.set(src, 1, 1, B);
    // Block (0,0) covers A,A,B,B -- an exact 2-2 tie, and B is the rarer colour overall (2 vs 14),
    // which is the rule that keeps an outline or a letter stroke alive through a shrink.
    const out = OVVAR.compose.downscaled(src, 2, 2);
    assert.strictEqual(t.get(out, 0, 0), B);
    assert.strictEqual(t.get(out, 1, 0), A);
    assert.strictEqual(t.get(out, 0, 1), A);
    assert.strictEqual(t.get(out, 1, 1), A);
    // A pixel under alpha 128 votes as nothing at all, and a block that votes nothing comes out
    // fully clear -- not the half-transparent colour it was written in.
    const faint = t.blank(2, 2);
    for (let y = 0; y < 2; y++) for (let x = 0; x < 2; x++) t.set(faint, x, y, 0x7F00FF00);
    assert.strictEqual(t.get(OVVAR.compose.downscaled(faint, 1, 1), 0, 0), 0);
    // No colour the source did not have: the trim channel's key palette depends on it.
    const ctx = context();
    const big = ctx.art('patches/itk_16x16.png');
    const small = OVVAR.compose.downscaled(big, 12, 12);
    const seen = new Set();
    for (let y = 0; y < big.h; y++) for (let x = 0; x < big.w; x++) {
      const p = t.get(big, x, y);
      seen.add(t.a(p) < 128 ? 0 : p);
    }
    for (let y = 0; y < small.h; y++) for (let x = 0; x < small.w; x++) {
      assert.ok(seen.has(t.get(small, x, y)), 'downscale invented ' + t.get(small, x, y).toString(16));
    }
  });
  ```

- [ ] **Step 2: Run and watch it fail**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected failure: `TypeError: OVVAR.compose.downscaled is not a function`.

- [ ] **Step 3: Append `downscaled`**

  Append to `mods/ovvar/tools/blockbench/src/10-compose.js`:
  ```js
  /** Below this alpha a pixel votes as nothing at all, and a pixel that wins as nothing comes out clear. */
  OVVAR.compose.VOTING_ALPHA = 128;

  /**
   * Tex.downscaled, exactly: the source shrunk to w x h by an area-weighted majority vote, each
   * output pixel taking whichever colour covers most of the rectangle it stands for.
   *
   * A vote and not an average, because the result must use no colour the source did not -- the
   * trim channel permutes a key palette built from every opaque colour of every patch art, so a
   * blended edge pixel would have no slot, and it is pixel art besides. Ties go to the colour that
   * is RARER in the whole source: the background always has the votes, so an even split has to
   * fall the other way or every thin thing in the art dissolves. A remaining tie goes to whichever
   * colour appears first reading rows, so the answer never depends on iteration order.
   *
   * Integer arithmetic throughout (the overlap of output pixel i with source pixel j in units of
   * 1/(W*W')), so exact ties are exactly ties -- Number is good to 2^53 and the products here are
   * at most 16*16*16*16, so nothing rounds.
   */
  OVVAR.compose.downscaled = function (im, w, h) {
    if (w <= 0 || h <= 0 || w > im.w || h > im.h) {
      throw new Error('cannot scale ' + im.w + 'x' + im.h + ' down to ' + w + 'x' + h);
    }
    // Keyed once: an invisible pixel votes as the same "nothing" whatever colour it is written in,
    // an opaque one as its exact ARGB, which is never 0 -- so key 0 is transparency alone.
    var n = im.w * im.h;
    var key = new Array(n);
    var frequency = new Map(), firstSeen = new Map();
    for (var i = 0; i < n; i++) {
      var p = OVVAR.tex.get(im, i % im.w, (i / im.w) | 0);
      var k = OVVAR.tex.a(p) < OVVAR.compose.VOTING_ALPHA ? 0 : p;
      key[i] = k;
      frequency.set(k, (frequency.get(k) || 0) + 1);
      if (!firstSeen.has(k)) firstSeen.set(k, i);
    }
    var out = OVVAR.tex.blank(w, h);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var votes = new Map();
        for (var sy = 0; sy < im.h; sy++) {
          var oy = Math.min((y + 1) * im.h, (sy + 1) * h) - Math.max(y * im.h, sy * h);
          if (oy <= 0) continue;
          for (var sx = 0; sx < im.w; sx++) {
            var ox = Math.min((x + 1) * im.w, (sx + 1) * w) - Math.max(x * im.w, sx * w);
            if (ox > 0) {
              var kk = key[sy * im.w + sx];
              votes.set(kk, (votes.get(kk) || 0) + ox * oy);
            }
          }
        }
        var won = 0, winning = -1;
        votes.forEach(function (count, colour) {
          var better = count > winning || (count === winning && (frequency.get(colour) < frequency.get(won)
            || (frequency.get(colour) === frequency.get(won) && firstSeen.get(colour) < firstSeen.get(won))));
          if (winning < 0 || better) { won = colour; winning = count; }
        });
        OVVAR.tex.set(out, x, y, won);   // key 0 is transparency, which is the clear pixel it came from
      }
    }
    return out;
  };
  ```

- [ ] **Step 4: Run — both new tests must pass**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 9`, `# fail 0`.

  Note the one Java-vs-JS trap this test guards: `Map` iteration order in JS is insertion order and `HashMap`'s in Java is not, which is exactly why `Tex.downscaled` has the `firstSeen` tie-break — with it, neither order can change the answer.

- [ ] **Step 5: Commit**

  ```sh
  git add mods/ovvar/tools/blockbench/src/10-compose.js mods/ovvar/tools/blockbench/test.js
  git commit -m "ovvar: the plugin scales a patch down the way the generated sizes were scaled"
  ```

---

## Task 7: `withLeft`, `flattened`, the base garment, stacking and `compose`

**Files:**
- Modify: `mods/ovvar/tools/blockbench/src/10-compose.js` (append)
- Modify: `mods/ovvar/tools/blockbench/test.js` (append four tests)

**Interfaces:**
- Produces:
  - `OVVAR.tex.copyRect(dst, src, sx, sy, w, h, dx, dy) -> image` *(a hard copy, alpha-0 texels included — unlike `blit`)*
  - `OVVAR.compose.withoutGreenKey(im) -> image`
  - `OVVAR.compose.flattened(m, skin64) -> image`
  - `OVVAR.compose.withLeft(m, tex64x32, box, skin64, leftBox) -> image`
  - `OVVAR.compose.buildBase(ctx, chapterId, piece, nercabbad) -> image` *(128×64, no marker texels — the ported build; the plugin itself uses `ctx.base`, and this is what the golden holds it to)*
  - `OVVAR.compose.mirrorStrip(m, piece, im) -> image`
  - `OVVAR.compose.stacked(m, placements) -> placement[]`
  - `OVVAR.compose.composePiece(ctx, piece, design) -> {A: image, B: image}`
  - `OVVAR.compose.compose(ctx, design) -> {top: {A, B}, bottom: {A, B}}`

**Design decision, stated once.** `ctx.base` **loads** the committed layer texture (`humanoid/<chapter>/top.png`) rather than cutting the chapter overlay again. It is the same pixels by construction, it costs nothing, and it means a chapter whose cloth is tinted (`it_kisel`) or hand-drawn (PolymITer's leggings) needs no HSB port at all. `buildBase` is the ported cut, kept **only** so the golden below can prove `withLeft`/`flattened` were ported right — the untinted chapters are enough for that.

- [ ] **Step 1: Write the failing tests**

  Append to `mods/ovvar/tools/blockbench/test.js`:
  ```js
  test('the ported base build equals the committed chapter layer textures', () => {
    const ctx = context();
    const m = ctx.m;
    let checked = 0;
    for (const chapter of m.chapters) {
      // A tinted chapter's cloth goes through Tex.tinted (HSB), which the plugin does not port --
      // it loads the committed layer instead. The untinted ones prove withLeft and flattened.
      if (chapter.tint !== null) continue;
      for (const piece of ['top', 'bottom']) {
        const want = ctx.base(chapter.id, piece, false);
        const got = OVVAR.compose.buildBase(ctx, chapter.id, piece, false);
        assert.deepStrictEqual(OVVAR.tex.diff(want, got, m.markerTexels), [], chapter.id + '/' + piece);
        checked++;
      }
      if (chapter.rollable) {
        const want = ctx.base(chapter.id, 'bottom', true);
        const got = OVVAR.compose.buildBase(ctx, chapter.id, 'bottom', true);
        assert.deepStrictEqual(OVVAR.tex.diff(want, got, m.markerTexels), [], chapter.id + '/bottom_nercabbad');
        checked++;
      }
    }
    // data (3) + it (3) + media (2); it_kisel is tinted and skipped.
    assert.strictEqual(checked, 8, 'walked ' + checked + ' base textures');
  });

  test("texture B shows the left limb's own art, not the right limb's mirrored", () => {
    const ctx = context();
    const m = ctx.m;
    const t = OVVAR.tex;
    const D = m.detail;
    const {A, B} = OVVAR.compose.composePiece(ctx, 'top', {chapter: 'data', nercabbad: false, placements: []});
    const box = m.skinBoxes.rightArm;             // [40, 16, 16, 16]
    const my = box[1] - m.mirrorShift;            // 0
    let same = 0, differ = 0;
    for (let y = 0; y < box[3] * D; y++) {
      for (let x = 0; x < box[2] * D; x++) {
        const strip = t.get(A, box[0] * D + x, my * D + y);   // the mirror strip on A
        const limb = t.get(B, box[0] * D + x, box[1] * D + y);
        assert.strictEqual(limb, strip, 'B arm texel (' + x + ',' + y + ') is not the mirror strip');
        if (t.get(A, box[0] * D + x, box[1] * D + y) === limb) same++; else differ++;
      }
    }
    // The Data ovve's sleeves are not symmetric, so B must differ from A somewhere on the arm.
    assert.ok(differ > 0, 'B is identical to A on the arm; the mirror strip did nothing');
    // The body box is the same on both -- it is one box, drawn unmirrored.
    const body = m.skinBoxes.body;
    assert.deepStrictEqual(t.diff(
      t.crop(A, body[0] * D, body[1] * D, body[2] * D, body[3] * D),
      t.crop(B, body[0] * D, body[1] * D, body[2] * D, body[3] * D), []), []);
  });

  test('a LEFT placement changes B only and a RIGHT one A only', () => {
    const ctx = context();
    const bare = {chapter: 'data', nercabbad: false, placements: []};
    const base = OVVAR.compose.composePiece(ctx, 'top', bare);
    const right = OVVAR.compose.composePiece(ctx, 'top',
      {chapter: 'data', nercabbad: false, placements: [{cell: 'sleeve_out_top_r', patch: 'itk'}]});
    const left = OVVAR.compose.composePiece(ctx, 'top',
      {chapter: 'data', nercabbad: false, placements: [{cell: 'sleeve_out_top_l', patch: 'itk'}]});
    assert.deepStrictEqual(OVVAR.tex.diff(base.B, right.B, []), [], 'a RIGHT sleeve patch touched B');
    assert.ok(OVVAR.tex.diff(base.A, right.A, []).length > 0, 'a RIGHT sleeve patch did not touch A');
    assert.deepStrictEqual(OVVAR.tex.diff(base.A, left.A, []), [], 'a LEFT sleeve patch touched A');
    assert.ok(OVVAR.tex.diff(base.B, left.B, []).length > 0, 'a LEFT sleeve patch did not touch B');
  });

  test('placements stack in layer order whatever order they were sewn in', () => {
    const ctx = context();
    const m = ctx.m;
    assert.strictEqual(m.cellById.back_big.layer, 0);
    assert.strictEqual(m.cellById.back_top_left.layer, 1);
    const big = {cell: 'back_big', patch: 'itk'};
    const small = {cell: 'back_top_left', patch: 'data'};
    const order = OVVAR.compose.stacked(m, [small, big]).map(p => p.cell);
    assert.deepStrictEqual(order, ['back_big', 'back_top_left']);
    const one = OVVAR.compose.composePiece(ctx, 'top', {chapter: 'data', nercabbad: false, placements: [big, small]});
    const two = OVVAR.compose.composePiece(ctx, 'top', {chapter: 'data', nercabbad: false, placements: [small, big]});
    assert.deepStrictEqual(OVVAR.tex.diff(one.A, two.A, []), [], 'sewing order changed the picture');
    const alone = OVVAR.compose.composePiece(ctx, 'top', {chapter: 'data', nercabbad: false, placements: [big]});
    assert.ok(OVVAR.tex.diff(alone.A, one.A, []).length > 0, 'the small patch did not draw over the big one');
    // compose() gives both halves of both pieces at once.
    const all = OVVAR.compose.compose(ctx, {chapter: 'data', nercabbad: false,
      placements: [big, small, {cell: 'seat', patch: 'rivals'}]});
    assert.ok(all.top.A && all.top.B && all.bottom.A && all.bottom.B);
    assert.ok(OVVAR.tex.diff(all.bottom.A, OVVAR.compose.composePiece(ctx, 'bottom', {chapter: 'data', nercabbad: false, placements: []}).A, []).length > 0,
      "the seat's right half did not reach the bottom's A");
  });
  ```

- [ ] **Step 2: Run and watch it fail**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected failure: `TypeError: OVVAR.compose.buildBase is not a function`.

- [ ] **Step 3: Append `copyRect` to the image ops**

  Append to the `OVVAR.tex` object in `mods/ovvar/tools/blockbench/src/10-compose.js` — add it as a new key beside `blit` (before the closing `};` of `OVVAR.tex`):
  ```js
      /**
       * A hard copy of a rectangle, alpha-0 texels included -- unlike `blit`, which leaves them
       * alone the way Tex.blit does. The mirror strip needs this: where the left sleeve is
       * transparent, the right sleeve's art must not show through from underneath.
       */
      copyRect: function (dst, src, sx, sy, w, h, dx, dy) {
        var out = OVVAR.tex.copy(dst);
        for (var y = 0; y < h; y++) {
          for (var x = 0; x < w; x++) OVVAR.tex.set(out, dx + x, dy + y, OVVAR.tex.get(src, sx + x, sy + y));
        }
        return out;
      },
  ```

- [ ] **Step 4: Append the garment build, the stacking and `compose`**

  Append to `mods/ovvar/tools/blockbench/src/10-compose.js`:
  ```js
  /**
   * Tex.withoutGreenKey: the website's overlays mark "erase the skin here" with pure green, and
   * armour has nothing to erase, so those pixels become transparent. Matched loosely, because a
   * colour-managed PNG can decode 00FF00 as 01FE00.
   */
  OVVAR.compose.withoutGreenKey = function (im) {
    var out = OVVAR.tex.copy(im);
    for (var y = 0; y < im.h; y++) {
      for (var x = 0; x < im.w; x++) {
        var p = OVVAR.tex.get(im, x, y);
        if (OVVAR.tex.r(p) < 32 && OVVAR.tex.g(p) > 223 && OVVAR.tex.b(p) < 32 && OVVAR.tex.a(p) > 127) {
          OVVAR.tex.set(out, x, y, 0);
        }
      }
    }
    return out;
  };

  /**
   * GeneratedAssets.flattened: the website renders the skin's second layer as a raised 3D layer
   * (belt folds, pockets, the hanging top of a rolled-down ovve). The armour model has one box per
   * part, so that layer is painted onto the base boxes -- and onto the left limbs' own boxes, for
   * withLeft.
   */
  OVVAR.compose.flattened = function (m, skin) {
    var boxes = m.skinBoxes;
    var pairs = [
      [boxes.body, boxes.bodyOuter], [boxes.rightArm, boxes.rightArmOuter], [boxes.rightLeg, boxes.rightLegOuter],
      [boxes.leftArm, boxes.leftArmOuter], [boxes.leftLeg, boxes.leftLegOuter]
    ];
    var out = skin;
    for (var i = 0; i < pairs.length; i++) {
      var base = pairs[i][0], outer = pairs[i][1];
      var over = OVVAR.tex.blit(OVVAR.tex.blank(skin.w, skin.h), skin, outer[0], outer[1], outer[2], outer[3], base[0], base[1]);
      out = OVVAR.tex.composite(out, over);
    }
    return out;
  };

  /**
   * GeneratedAssets.withLeft: the left limb's art one strip up from the right limb's box
   * (mirrorShift rows), with every face mirrored in place. The armour model draws the left limb as
   * a mirror image off the RIGHT limb's strips, so the art has to be pre-mirrored to come out
   * straight; the plugin's texture B then copies this strip down onto the limb rows, which is what
   * a mirror_uv cube reads.
   *
   * Box layout inside a 16x16 strip: top and bottom faces (4x4) at +4 and +8 on the first four
   * rows, then four 4x12 side faces.
   */
  OVVAR.compose.withLeft = function (m, tex, box, skin, leftBox) {
    var x = box[0], y = box[1], my = y - m.mirrorShift;
    var left = OVVAR.tex.blit(OVVAR.tex.blank(64, 64), skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], 0, 0);
    var out;
    if (OVVAR.tex.isEmpty(left)) {
      out = OVVAR.tex.blit(tex, tex, x, y, box[2], box[3], x, my);
    } else {
      // The skin lays the left limb out for an unmirrored cube: its first side strip is the inner
      // face and its third the outer, the other way round from the right limb's strips the model
      // reads. Swap them so the outer art lands on the outer face.
      out = OVVAR.tex.blit(tex, skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], x, my);
      out = OVVAR.tex.blit(out, skin, leftBox[0] + 8, leftBox[1] + 4, 4, 12, x, my + 4);
      out = OVVAR.tex.blit(out, skin, leftBox[0], leftBox[1] + 4, 4, 12, x + 8, my + 4);
    }
    out = OVVAR.tex.flipXRect(out, x + 4, my, 4, 4);
    out = OVVAR.tex.flipXRect(out, x + 8, my, 4, 4);
    for (var face = 0; face < 4; face++) out = OVVAR.tex.flipXRect(out, x + face * 4, my + 4, 4, 12);
    return out;
  };

  /**
   * The chapter's cloth, cut out of its website overlay the way GeneratedAssets does it. The
   * plugin itself loads the committed layer texture instead (ctx.base) -- it is the same pixels,
   * and it costs nothing for a chapter whose cloth is tinted or hand-drawn. This is here so the
   * tests can prove withLeft and flattened were ported right.
   */
  OVVAR.compose.buildBase = function (ctx, chapterId, piece, nercabbad) {
    var m = ctx.m, boxes = m.skinBoxes, D = m.detail;
    var chapter = m.chapterById[chapterId];
    if (chapter.tint !== null) throw new Error(chapter.name + ' is tinted; load its committed layer instead');
    function overlay(file) {
      var path = [m.checkout, 'mods/ovvar/src/main/resources/art/ovvar', file].join('/');
      return OVVAR.compose.flattened(m, OVVAR.compose.withoutGreenKey(ctx.io.decode(ctx.io.read(path))));
    }
    function cut(src, list) {
      var out = OVVAR.tex.blank(64, 32);
      for (var i = 0; i < list.length; i++) {
        var b = list[i];
        out = OVVAR.tex.blit(out, src, b[0], b[1], b[2], b[3], b[0], b[1]);
      }
      return out;
    }
    if (piece === 'top') {
      var over = overlay(chapter.art);
      return OVVAR.tex.scale(OVVAR.compose.withLeft(m, cut(over, [boxes.body, boxes.rightArm]), boxes.rightArm, over, boxes.leftArm), D);
    }
    if (!nercabbad) {
      var o2 = overlay(chapter.art);
      return OVVAR.tex.scale(OVVAR.compose.withLeft(m, cut(o2, [boxes.rightLeg, boxes.waist]), boxes.rightLeg, o2, boxes.leftLeg), D);
    }
    // Rolled down: the legs plus the top hanging at the waist, all on the legs slot's layer.
    var rolled = overlay(chapter.nercabbad);
    return OVVAR.tex.scale(OVVAR.compose.withLeft(m, cut(rolled, [boxes.rightLeg, boxes.body]), boxes.rightLeg, rolled, boxes.leftLeg), D);
  };

  /**
   * Texture B: the mirror strip copied down onto the limb rows, so a mirror_uv cube reading the
   * standard strip shows the left limb's own art. The body box has no mirror strip -- it is one
   * box, drawn unmirrored -- so only the limb this piece owns is copied.
   */
  OVVAR.compose.mirrorStrip = function (m, piece, im) {
    var D = m.detail;
    var box = piece === 'top' ? m.skinBoxes.rightArm : m.skinBoxes.rightLeg;
    var my = box[1] - m.mirrorShift;
    return OVVAR.tex.copyRect(im, im, box[0] * D, my * D, box[2] * D, box[3] * D, box[0] * D, box[1] * D);
  };

  /** Spot.stacked: bottom first -- layer order, and the order they came in within a layer. */
  OVVAR.compose.stacked = function (m, placements) {
    return placements.slice().sort(function (a, b) {
      return m.cellById[a.cell].layer - m.cellById[b.cell].layer;
    });
  };

  /**
   * One half of the garment, as the two textures the cubes wear.
   *
   * A: the cloth, plus every BODY and RIGHT placement. B: the cloth with the mirror strip copied
   * down, plus every LEFT placement. The seat is one patch cut in half, `_r` on A and `_l` on B.
   *
   * A side cell's art is baked through the squeeze (`placedWrapped`) rather than laid on flat:
   * the game does the squeeze in the shader, Blockbench has no shader, and this is what makes art
   * bend round the chest's and the sleeve's corners the way it does in game. A top-face cell (the
   * shoulders) is never squeezed -- the top face is not on the strip's perimeter -- so it stays
   * `placed`. Mirroring a limb's art and then wrapping is the same as wrapping and then mirroring,
   * because the squeeze is symmetric about the anchor face's centre and a limb cell is that whole
   * face.
   */
  OVVAR.compose.composePiece = function (ctx, piece, design) {
    var m = ctx.m;
    var base = ctx.base(design.chapter, piece, piece === 'bottom' && !!design.nercabbad);
    var A = OVVAR.tex.copy(base);
    var B = OVVAR.compose.mirrorStrip(m, piece, base);
    var placements = OVVAR.compose.stacked(m, design.placements || []);
    for (var i = 0; i < placements.length; i++) {
      var p = placements[i];
      var cell = m.cellById[p.cell], patch = m.patchById[p.patch];
      if (!cell || !patch) { ctx.warnings.push('unknown placement ' + p.cell + '/' + p.patch); continue; }
      if (cell.piece !== piece) continue;
      if (!patch.seat !== !(cell.id === 'seat')) { ctx.warnings.push(patch.name + ' does not fit ' + cell.label); continue; }
      var sides = cell.side === 'seat' ? ['right', 'left'] : [cell.side];
      for (var s = 0; s < sides.length; s++) {
        var side = sides[s];
        var entry = OVVAR.compose.artFor(m, patch, cell);
        var art = ctx.art(entry.file);
        var x, drawn;
        if (cell.side === 'seat') {
          drawn = OVVAR.tex.crop(art, OVVAR.compose.seatHalf(m, side), 0, m.px, art.h);
          if (side === 'left') drawn = OVVAR.tex.flipX(drawn);
          x = cell.u * m.detail;
        } else {
          drawn = cell.side === 'left' ? OVVAR.tex.flipX(art) : art;
          x = cell.u * m.detail + OVVAR.compose.offsetX(m, cell, entry);
        }
        var tex = cell.top ? OVVAR.compose.placed(m, cell, drawn, x) : OVVAR.compose.placedWrapped(m, cell, drawn, x);
        if (tex === null) {
          ctx.warnings.push(patch.name + ' lands entirely off ' + cell.label + '; not drawn');
          continue;
        }
        var onto = (side === 'left') ? 'B' : 'A';
        if (onto === 'A') A = OVVAR.tex.blit(A, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
        else B = OVVAR.tex.blit(B, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
        if (cell.side === 'body') B = OVVAR.tex.blit(B, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
      }
    }
    return {A: A, B: B};
  };

  /** Both halves, both sides: what the four textures of the project are set to. */
  OVVAR.compose.compose = function (ctx, design) {
    return {top: OVVAR.compose.composePiece(ctx, 'top', design), bottom: OVVAR.compose.composePiece(ctx, 'bottom', design)};
  };
  ```

- [ ] **Step 5: Run — all four new tests must pass**

  ```sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 13`, `# fail 0`.

  If the base golden fails only on the `media` chapter's `bottom`, check `cut(...)` for `bottom`: `MEDIA` has no `nercabbadOverlay`, but its plain bottom is still the legs box plus the waistband from its own overlay.

- [ ] **Step 6: Build and commit**

  ```sh
  mods/ovvar/tools/blockbench/build.sh
  git add mods/ovvar/tools/blockbench/src/10-compose.js mods/ovvar/tools/blockbench/test.js mods/ovvar/tools/blockbench/ovvar.js
  git commit -m "ovvar: the plugin composes a whole ovve, both halves and both hands"
  ```

---

## Task 8: The plugin glue — format, cubes, textures, panel, dialogs, export

Blockbench cannot be unit-tested, so every step here ends in a console expression or a screenshot, and every API fact is verified against Blockbench 5.1.6 before it is relied on. Keep `/tmp/bbsrc` from Task 0 open: it is the authority, not the wiki.

**Files:**
- Create: `mods/ovvar/tools/blockbench/src/20-model.js`
- Create: `mods/ovvar/tools/blockbench/src/30-panel.js`
- Create: `mods/ovvar/tools/blockbench/src/90-plugin.js`
- Create: `mods/ovvar/tools/blockbench/README.md`
- Modify: `mods/ovvar/tools/blockbench/ovvar.js` (rebuilt)

**Interfaces:**
- Consumes: everything in `OVVAR.compose` and `OVVAR.makeIo`.
- Produces:
  - `OVVAR.state = {io, ctx, design, textures: {topA, topB, bottomA, bottomB}, artTextures: {<file>: Texture}, cubes: [], group: null, format: null, panel: null, dirty: {}}`
  - `OVVAR.model.registerFormat() -> ModelFormat`
  - `OVVAR.model.CUBES` — the six-row table from Task 0 step 3
  - `OVVAR.model.build(checkout)` — opens a checkout into the current project
  - `OVVAR.model.refresh()` — recompose and push to the four garment textures
  - `OVVAR.model.artTextureFor(file) -> Texture`
  - `OVVAR.panel.register() -> Panel`
  - `OVVAR.panel.sew()`, `OVVAR.panel.newPatch()`, `OVVAR.panel.addSize()`, `OVVAR.panel.exportToRepo()`

### 8a — the format and the cubes

- [ ] **Step 1: Verify the `ModelFormat` and `Cube` options this build accepts**

  In Blockbench's console:
  ```js
  [typeof ModelFormat, typeof Cube, typeof Group, typeof Texture, typeof Panel, typeof Dialog,
   typeof Undo.initEdit, typeof Undo.finishEdit, typeof Canvas.updateAll,
   typeof Blockbench.on, typeof Blockbench.showMessageBox, typeof Blockbench.showQuickMessage,
   typeof newProject]
  ```
  **Verify in Blockbench 5 console:** every entry is `'function'` except the first six, which are `'function'` too (they are classes). Anything reported `'undefined'` must be looked up in `/tmp/bbsrc` before the code below is written.

  ```js
  Object.keys(new ModelFormat('ovvar_probe', {name: 'probe'})).sort().join(' ')
  ```
  **Verify in Blockbench 5 console:** the printed list contains `box_uv`, `single_texture`, `bone_rig`, `centered_grid`, `per_texture_uv_size`, `model_identifier`, `category`. Then clean up: `delete Formats.ovvar_probe`.

  ```js
  const c = new Cube({name:'probe', from:[0,0,0], to:[1,1,1], origin:[0,0,0], box_uv:true, uv_offset:[4,4], inflate:0.5, mirror_uv:true}).init();
  [c.box_uv, c.uv_offset, c.inflate, c.mirror_uv, typeof c.applyTexture];
  ```
  **Verify in Blockbench 5 console:** `[true, [4, 4], 0.5, true, 'function']`. Then `c.remove()`.

- [ ] **Step 2: Write `src/20-model.js`**

  Create `mods/ovvar/tools/blockbench/src/20-model.js`:
  ```js
  // The Blockbench half: the format, the six armour cubes, and the textures they wear. Everything
  // that knows what a pixel should be lives in 10-compose.js; this file only hangs the result on a
  // model. Nothing here runs under Node.

  OVVAR.state = {
    io: null,          // the scoped file system, once a checkout is open
    ctx: null,         // OVVAR.compose.ctx
    checkout: null,
    design: {chapter: null, nercabbad: false, placements: []},
    textures: {},      // topA, topB, bottomA, bottomB
    artTextures: {},   // catalogue file name -> Texture Vlad paints on
    added: {},         // patch id -> the catalogue entry this project invented
    dirty: {},         // art file name -> true, for Export
    cubes: [],
    group: null,
    format: null,
    panel: null,
    listeners: []
  };

  OVVAR.model = {};

  /**
   * The armour model, in Blockbench units. Read out of Blockbench's own `armor` skin template
   * (js/formats/minecraft/skin.ts) and converted from bedrock origin/size the way its bedrock
   * codec does -- from[0] = -(origin[0] + size[0]), to = from + size -- with three corrections:
   * the template fudges the chest to inflate 1.01 and the belt to 0.51 to avoid z-fighting with
   * the skin, and nudges the legs 0.1 apart. The game inflates exactly Spot.inflate (1 and 0.5)
   * and the legs meet at x 0, so the plugin uses those.
   *
   * Blockbench's +x is the wearer's RIGHT. The armour model's arms are 4 wide on Alex as on Steve
   * -- there is no slim armour model -- so this table never varies.
   */
  OVVAR.model.CUBES = [
    {name: 'ovve_body',  piece: 'top',    side: 'A', from: [-4, 12, -2], to: [4, 24, 2],   uv: [16, 16], inflate: 1.0, mirror: false},
    {name: 'ovve_arm_r', piece: 'top',    side: 'A', from: [4, 12, -2],  to: [8, 24, 2],   uv: [40, 16], inflate: 1.0, mirror: false},
    {name: 'ovve_arm_l', piece: 'top',    side: 'B', from: [-8, 12, -2], to: [-4, 24, 2],  uv: [40, 16], inflate: 1.0, mirror: true},
    {name: 'ovve_belt',  piece: 'bottom', side: 'A', from: [-4, 12, -2], to: [4, 24, 2],   uv: [16, 16], inflate: 0.5, mirror: false},
    {name: 'ovve_leg_r', piece: 'bottom', side: 'A', from: [0, 0, -2],   to: [4, 12, 2],   uv: [0, 16],  inflate: 0.5, mirror: false},
    {name: 'ovve_leg_l', piece: 'bottom', side: 'B', from: [-4, 0, -2],  to: [0, 12, 2],   uv: [0, 16],  inflate: 0.5, mirror: true}
  ];

  OVVAR.model.registerFormat = function () {
    OVVAR.state.format = new ModelFormat('ovvar', {
      name: 'Ovvar',
      description: 'A METAcraft ovve: patches on the armour model, drawn the way the game draws them.',
      icon: 'checkroom',
      category: 'minecraft',
      target: ['Minecraft: Java Edition'],
      box_uv: true,
      single_texture: false,
      per_texture_uv_size: true,
      bone_rig: false,
      centered_grid: true,
      model_identifier: false,
      rotate_cubes: false,
      integer_size: false,
      onSetup: function (project, converting) {
        if (converting) return;
        Project.texture_width = 128;
        Project.texture_height = 64;
        OVVAR.panel.askForCheckout();
      }
    });
    return OVVAR.state.format;
  };

  /** Open a checkout: load its manifest, build the cubes, make the textures, compose once. */
  OVVAR.model.build = function (checkout) {
    var s = OVVAR.state;
    s.checkout = checkout;
    s.io = OVVAR.makeIo(OVVAR.require, checkout);
    var m = OVVAR.loadManifest(s.io, checkout);
    s.ctx = OVVAR.compose.ctx(s.io, m);
    if (!s.design.chapter) s.design.chapter = m.chapters[0].id;

    Project.texture_width = m.texture[0];
    Project.texture_height = m.texture[1];

    Undo.initEdit({outliner: true, elements: [], textures: []});
    s.group = new Group({name: 'ovve', origin: [0, 0, 0]}).init();
    s.cubes = OVVAR.model.CUBES.map(function (c) {
      var cube = new Cube({
        name: c.name, from: c.from.slice(), to: c.to.slice(), origin: [0, 0, 0],
        box_uv: true, uv_offset: c.uv.slice(), inflate: c.inflate, mirror_uv: c.mirror
      });
      cube.addTo(s.group);
      return cube.init();
    });

    ['topA', 'topB', 'bottomA', 'bottomB'].forEach(function (key) {
      var tex = new Texture({name: 'ovve_' + key + '.png'})
        .fromDataURL(s.io.dataUrl(OVVAR.tex.blank(m.texture[0], m.texture[1]))).add(false);
      tex.uv_width = m.texture[0];
      tex.uv_height = m.texture[1];
      s.textures[key] = tex;
    });
    OVVAR.model.CUBES.forEach(function (c, i) {
      s.cubes[i].applyTexture(s.textures[c.piece + c.side], true);
    });
    Undo.finishEdit('Build the ovve', {outliner: true, elements: s.cubes, textures: Texture.all});

    OVVAR.model.loadCatalogueTextures();
    OVVAR.model.refresh();
    Canvas.updateAll();
  };

  /**
   * One texture per catalogue art, named by its file, so Vlad paints on the same PNG the mod
   * loads. None of them is on a cube: they are edited in Paint mode and the garment textures are
   * recomposed from them on every finished stroke.
   */
  OVVAR.model.loadCatalogueTextures = function () {
    var s = OVVAR.state, m = s.ctx.m;
    m.patches.forEach(function (patch) {
      patch.arts.forEach(function (art) {
        if (s.artTextures[art.file]) return;
        var image = s.ctx.art(art.file);
        var tex = new Texture({name: art.file.replace('patches/', '')})
          .fromDataURL(s.io.dataUrl(image)).add(false);
        tex.uv_width = image.w;
        tex.uv_height = image.h;
        tex.ovvar_art = art.file;
        s.artTextures[art.file] = tex;
      });
    });
  };

  OVVAR.model.artTextureFor = function (file) {
    return OVVAR.state.artTextures[file];
  };

  /**
   * Recompose and push. Called on every panel change and on every finished paint stroke whose
   * texture is a patch art -- a few 128x64 blits, milliseconds. The art a texture now holds is
   * read back out of it first, so what Vlad just painted is what the model shows.
   */
  OVVAR.model.refresh = function () {
    var s = OVVAR.state;
    if (!s.ctx) return;
    s.ctx.warnings = [];
    var result = OVVAR.compose.compose(s.ctx, s.design);
    s.textures.topA.updateSource(s.io.dataUrl(result.top.A));
    s.textures.topB.updateSource(s.io.dataUrl(result.top.B));
    s.textures.bottomA.updateSource(s.io.dataUrl(result.bottom.A));
    s.textures.bottomB.updateSource(s.io.dataUrl(result.bottom.B));
    if (s.panel && s.panel.inside_vue) {
      s.panel.inside_vue.warnings = s.ctx.warnings.slice();
      s.panel.inside_vue.$forceUpdate();
    }
    Canvas.updateAll();
  };

  /**
   * A patch art texture Vlad has just painted, read back into an image. Canvas pixels are
   * premultiplied, so a texel painted at alpha 0 loses its colour here -- which is right: the
   * brush never produces one, and the PNG the export writes is encoded from this same image.
   */
  OVVAR.model.readTexture = function (tex) {
    var w = tex.width, h = tex.height;
    var data = tex.ctx.getImageData(0, 0, w, h).data;
    return {w: w, h: h, data: new Uint8Array(data)};
  };
  ```

- [ ] **Step 3: Verify the format and cubes for real**

  Build and load:
  ```sh
  mods/ovvar/tools/blockbench/build.sh
  ```
  In Blockbench: *File › Plugins › Load Plugin from File…* → `mods/ovvar/tools/blockbench/ovvar.js`. (It will not register yet — 90-plugin.js does not exist. That is expected; this step only checks the file parses.) In the console:
  ```js
  typeof OVVAR
  ```
  **Verify in Blockbench 5 console:** at this point `OVVAR` is scoped to the plugin's own function and is *not* visible from the console. That is deliberate. To probe it during development, temporarily add `window.OVVAR = OVVAR;` as the last line of `src/90-plugin.js` when it exists — and **remove it before Task 9's commit** (an unload has to leave nothing behind).

### 8b — the sidebar panel and the dialogs

- [ ] **Step 4: Verify `Panel`, `Dialog` and the events**

  In Blockbench's console:
  ```js
  const p = new Panel('ovvar_probe', {
    name: 'probe', icon: 'checkroom', growable: true,
    default_position: {slot: 'left_bar', float_position: [0,0], float_size: [300,400], height: 300},
    component: {data() { return {n: 1}; }, template: '<div>probe {{ n }}</div>'}
  });
  [!!p.inside_vue, p.inside_vue.n];
  ```
  **Verify in Blockbench 5 console:** `[true, 1]`, and a panel named "probe" appears in the left bar. Then `p.delete()`.

  ```js
  const d = new Dialog('ovvar_probe_dialog', {
    title: 'probe',
    form: {
      id: {label: 'id', type: 'text', value: 'x'},
      seat: {label: 'seat', type: 'checkbox', value: false},
      w: {label: 'w', type: 'number', value: 12, min: 6, max: 16, step: 2},
      cell: {label: 'cell', type: 'select', options: {a: 'A', b: 'B'}, value: 'a'}
    },
    onConfirm(result) { console.log('probe result', result); this.hide(); }
  });
  d.show();
  ```
  **Verify in Blockbench 5 console:** the dialog opens with a text field, a checkbox, a number field and a dropdown; confirming logs `probe result {id: 'x', seat: false, w: 12, cell: 'a'}`. Then `d.delete()`. If `type: 'select'` wants an array rather than an object here, `grep -n "case 'select'" /tmp/bbsrc/.._js_interface_dialog.ts` and follow that.

  ```js
  const off = Blockbench.on('finished_edit', e => console.log('finished_edit', Object.keys(e.aspects || {}), e.message));
  ```
  **Verify in Blockbench 5 console:** paint one stroke on any texture; the console logs `finished_edit [...] ...` with `textures` among the aspect keys. Then `Blockbench.removeListener('finished_edit', ...)` — check the real remover name with `Object.getOwnPropertyNames(Blockbench).filter(n => /remove|off/i.test(n))` and use whichever it reports; 90-plugin.js below assumes `Blockbench.removeListener`.

- [ ] **Step 5: Write `src/30-panel.js`**

  Create `mods/ovvar/tools/blockbench/src/30-panel.js`:
  ```js
  // The sidebar: what ovve this is, what is sewn on it, and the four things Vlad does -- sew,
  // unpick, draw a new patch, put it in the repo.

  OVVAR.panel = {};

  OVVAR.panel.askForCheckout = function () {
    // Blockbench.import cannot pick a directory, so the checkout is typed in instead: one field,
    // remembered between sessions in localStorage.
    new Dialog('ovvar_checkout', {
      title: 'Ovvar: open a METAmods checkout',
      form: {
        path: {
          label: 'Checkout',
          type: 'text',
          value: OVVAR.state.checkout || localStorage.getItem('ovvar_checkout') || '',
          description: 'The folder holding mods/ovvar. The manifest is read from mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json.'
        }
      },
      onConfirm: function (result) {
        this.hide();
        try {
          OVVAR.model.build(result.path.replace(/\/+$/, ''));
          localStorage.setItem('ovvar_checkout', OVVAR.state.checkout);
        } catch (e) {
          Blockbench.showMessageBox({
            title: 'Ovvar', icon: 'error',
            message: String(e && e.message ? e.message : e)
          });
        }
      }
    }).show();
  };

  OVVAR.panel.register = function () {
    OVVAR.state.panel = new Panel('ovvar', {
      name: 'Ovve',
      icon: 'checkroom',
      growable: true,
      condition: {formats: ['ovvar']},
      default_position: {slot: 'left_bar', float_position: [0, 0], float_size: [320, 600], height: 600},
      component: {
        data: function () {
          return {state: OVVAR.state, warnings: []};
        },
        computed: {
          manifest: function () { return this.state.ctx ? this.state.ctx.m : null; },
          chapter: function () { return this.manifest ? this.manifest.chapterById[this.state.design.chapter] : null; }
        },
        methods: {
          onChapter: function (id) { this.state.design.chapter = id; this.fix(); OVVAR.model.refresh(); },
          onNercabbad: function (v) { this.state.design.nercabbad = v; OVVAR.model.refresh(); },
          // A chapter that cannot roll down is always worn up.
          fix: function () { if (this.chapter && !this.chapter.rollable) this.state.design.nercabbad = false; },
          label: function (p) {
            var m = this.manifest;
            return m.patchById[p.patch].name + ' on the ' + m.cellById[p.cell].label;
          },
          unpick: function (i) { this.state.design.placements.splice(i, 1); OVVAR.model.refresh(); },
          sew: function () { OVVAR.panel.sew(); },
          newPatch: function () { OVVAR.panel.newPatch(); },
          addSize: function () { OVVAR.panel.addSize(); },
          exportToRepo: function () { OVVAR.panel.exportToRepo(); },
          openCheckout: function () { OVVAR.panel.askForCheckout(); }
        },
        template: [
          '<div class="ovvar_panel">',
          '  <p v-if="!manifest"><button @click="openCheckout()">Open checkout…</button></p>',
          '  <template v-else>',
          '    <p>',
          '      <select :value="state.design.chapter" @change="onChapter($event.target.value)">',
          '        <option v-for="c in manifest.chapters" :key="c.id" :value="c.id">{{ c.name }}</option>',
          '      </select>',
          '    </p>',
          '    <p>',
          '      <label><input type="checkbox" :disabled="!chapter.rollable" :checked="state.design.nercabbad"',
          '        @change="onNercabbad($event.target.checked)"> Zipped down</label>',
          '      <span v-if="!chapter.rollable" class="ovvar_hint">({{ chapter.name }} has nothing to roll down)</span>',
          '    </p>',
          '    <ul class="ovvar_placements">',
          '      <li v-for="(p, i) in state.design.placements" :key="i">',
          '        {{ label(p) }} <button @click="unpick(i)" title="Unpick">×</button>',
          '      </li>',
          '      <li v-if="!state.design.placements.length" class="ovvar_hint">Nothing sewn on yet.</li>',
          '    </ul>',
          '    <p>',
          '      <button @click="sew()">Sew…</button>',
          '      <button @click="newPatch()">New patch…</button>',
          '      <button @click="addSize()">Add size…</button>',
          '    </p>',
          '    <p><button @click="exportToRepo()">Export to repo</button></p>',
          '    <ul class="ovvar_warnings"><li v-for="w in warnings" :key="w">⚠ {{ w }}</li></ul>',
          '    <p class="ovvar_hint">{{ manifest.patches.length }} patches, {{ manifest.cells.length }} cells,',
          '      from {{ state.checkout }}</p>',
          '  </template>',
          '</div>'
        ].join('\n')
      }
    });
    return OVVAR.state.panel;
  };

  /** Which cells a patch may go on, grouped by part, in the enum's own order. */
  OVVAR.panel.cellOptions = function (m, patch) {
    var out = {};
    m.cells.forEach(function (cell) {
      if (patch.seat !== (cell.id === 'seat')) return;
      out[cell.id] = (cell.piece === 'top' ? 'Top — ' : 'Bottom — ') + cell.label;
    });
    return out;
  };

  OVVAR.panel.sew = function () {
    var s = OVVAR.state, m = s.ctx.m;
    var patches = {};
    m.patches.forEach(function (p) { patches[p.id] = p.name + (p.seat ? ' (seat)' : ''); });
    var first = m.patches[0];
    new Dialog('ovvar_sew', {
      title: 'Sew a patch on',
      form: {
        patch: {label: 'Patch', type: 'select', options: patches, value: first.id},
        cell: {label: 'Spot', type: 'select', options: OVVAR.panel.cellOptions(m, first), value: 'front_top_left'}
      },
      onFormChange: function (result) {
        // The spot list follows the patch: a seat patch has exactly one place to go, and no
        // ordinary patch may go there.
        this.setFormValues({}, false);
        var patch = m.patchById[result.patch];
        this.form.form_config.cell.options = OVVAR.panel.cellOptions(m, patch);
        this.form.updateValues();
      },
      onConfirm: function (result) {
        this.hide();
        var cell = m.cellById[result.cell];
        // Spot.overlapping: only the seat's. Everything else may overlap -- that is the point of
        // an ovve -- but the seat is one patch across two cells of two boxes, so it and the legs'
        // back cells cannot both be on.
        var clash = OVVAR.panel.overlapping(m, cell.id);
        s.design.placements = s.design.placements.filter(function (p) { return clash.indexOf(p.cell) < 0 && p.cell !== cell.id; });
        s.design.placements.push({cell: cell.id, patch: result.patch});
        OVVAR.model.refresh();
      }
    }).show();
  };

  /** Spot.overlapping, from the manifest's rectangles: the seat against the two leg-back cells. */
  OVVAR.panel.overlapping = function (m, cellId) {
    var cell = m.cellById[cellId];
    return m.cells.filter(function (o) {
      if (o.id === cell.id || o.piece !== cell.piece) return false;
      if (o.side !== 'seat' && cell.side !== 'seat') return false;
      if (o.side === 'body' || cell.side === 'body') return false;
      return o.u < cell.u + cell.w && cell.u < o.u + o.w && o.v < cell.v + cell.h && cell.v < o.v + o.h;
    }).map(function (o) { return o.id; });
  };

  OVVAR.panel.newPatch = function () {
    var s = OVVAR.state, m = s.ctx.m;
    new Dialog('ovvar_new_patch', {
      title: 'New patch',
      form: {
        id: {label: 'Id', type: 'text', value: '', description: 'Lower case letters, digits and underscores; also the art file name.'},
        name: {label: 'Name', type: 'text', value: ''},
        artist: {label: 'Artist', type: 'text', value: 'Vlad'},
        seat: {label: 'Across the seat', type: 'checkbox', value: false},
        w: {label: 'Width', type: 'number', value: 12, min: 6, max: 16, step: 2},
        h: {label: 'Height', type: 'number', value: 12, min: 6, max: 16, step: 2}
      },
      onConfirm: function (result) {
        var problems = OVVAR.panel.checkNewPatch(m, result);
        if (problems.length) {
          Blockbench.showMessageBox({title: 'Ovvar', icon: 'error', message: problems.join('\n')});
          return;
        }
        this.hide();
        var w = result.seat ? 2 * m.px : result.w;
        var h = result.h;
        var file = 'patches/' + result.id + '.png';
        var entry = {
          id: result.id, name: result.name, seat: !!result.seat, w: w, h: h,
          artist: result.artist || null,
          arts: [{file: file, w: w, h: h, 'default': true, generated: false, source: null}],
          fits: {over: file, clipped: file, filled: file}
        };
        m.patches.push(entry);
        m.patchById[entry.id] = entry;
        m.artByFile[file] = entry.arts[0];
        s.added[entry.id] = entry;
        s.dirty[file] = true;
        var tex = new Texture({name: result.id + '.png'})
          .fromDataURL(s.io.dataUrl(OVVAR.tex.blank(w, h))).add(false);
        tex.uv_width = w;
        tex.uv_height = h;
        tex.ovvar_art = file;
        s.artTextures[file] = tex;
        Texture.selected = tex;
        Blockbench.showQuickMessage('Draw ' + result.name + ' in Paint mode, then Sew…', 3000);
        OVVAR.model.refresh();
      }
    }).show();
  };

  /** The rules Patches.Patch's constructor enforces, said before the art is made rather than after. */
  OVVAR.panel.checkNewPatch = function (m, result) {
    var out = [];
    if (!/^[a-z0-9_]+$/.test(result.id)) out.push('An id is lower case letters, digits and underscores: "' + result.id + '" is not.');
    if (m.patchById[result.id]) out.push('There is already a patch called ' + result.id + '.');
    if (!result.name) out.push('Give it a name.');
    var h = result.h;
    if (result.seat) {
      if (h < m.px || h > m.seatHeightMax) out.push('A seat patch is ' + m.px + '–' + m.seatHeightMax + ' px tall (and always ' + 2 * m.px + ' wide).');
    } else {
      if (result.w % 2 || h % 2) out.push('Patch art is an even size both ways.');
      if (result.w < 6 || result.w > m.maxArt || h < 6 || h > m.maxArt) out.push('Patch art is 6–' + m.maxArt + ' px each way.');
    }
    return out;
  };

  OVVAR.panel.addSize = function () {
    var s = OVVAR.state, m = s.ctx.m;
    var patches = {};
    m.patches.forEach(function (p) { if (!p.seat) patches[p.id] = p.name; });
    new Dialog('ovvar_add_size', {
      title: 'Draw a patch again at another size',
      form: {
        patch: {label: 'Patch', type: 'select', options: patches, value: m.patches[0].id},
        size: {label: 'Size', type: 'select', options: {'8': '8 × 8 (a shoulder)', '12': '12 × 12 (an ordinary cell)', '16': '16 × 16 (the big back cell)'}, value: '12'}
      },
      onConfirm: function (result) {
        this.hide();
        var patch = m.patchById[result.patch];
        var size = parseInt(result.size, 10);
        var file = 'patches/' + patch.id + '_' + size + 'x' + size + '.png';
        if (m.artByFile[file] && !m.artByFile[file].generated) {
          Blockbench.showMessageBox({title: 'Ovvar', icon: 'warning', message: patch.name + ' already has a drawing at ' + size + '×' + size + '.'});
          return;
        }
        // Start from the scaler's answer rather than from nothing: that is the picture the pack
        // shows today, and the point of drawing it again is to fix what the scaler got wrong.
        var from = s.ctx.art(patch.fits.filled);
        var start = (from.w >= size && from.h >= size) ? OVVAR.compose.downscaled(from, size, size) : OVVAR.tex.blank(size, size);
        var art = {file: file, w: size, h: size, 'default': false, generated: false, source: null};
        patch.arts.push(art);
        patch.arts.sort(function (a, b) { return a.w * a.h - b.w * b.h; });
        m.artByFile[file] = art;
        OVVAR.panel.refit(m, patch);
        s.dirty[file] = true;
        var tex = new Texture({name: file.replace('patches/', '')}).fromDataURL(s.io.dataUrl(start)).add(false);
        tex.uv_width = size;
        tex.uv_height = size;
        tex.ovvar_art = file;
        s.artTextures[file] = tex;
        Texture.selected = tex;
        OVVAR.model.refresh();
      }
    }).show();
  };

  /** Patches.artFor, redone for a patch the project has changed: the largest art each fit allows. */
  OVVAR.panel.refit = function (m, patch) {
    function largestIn(w, h) {
      var best = null;
      patch.arts.forEach(function (a) {
        if (a.w > w || a.h > h) return;
        if (!best || a.w * a.h > best.w * best.h) best = a;
      });
      return (best || patch.arts.filter(function (a) { return a['default']; })[0]).file;
    }
    var own = patch.arts.filter(function (a) { return a['default']; })[0];
    patch.fits = {
      over: (patch.seat || (own.w <= m.overMax && own.h <= m.overMax)) ? own.file : largestIn(m.overMax, m.overMax),
      clipped: largestIn(m.px, m.px),
      filled: largestIn(m.maxArt, m.maxArt)
    };
  };

  /**
   * Write every art this project drew or changed into the checkout, then show the catalogue
   * line(s) to paste into Patches.java. Never overwrites a file that is already there without
   * saying so.
   */
  OVVAR.panel.exportToRepo = function () {
    var s = OVVAR.state, m = s.ctx.m;
    var files = Object.keys(s.dirty);
    Object.keys(s.artTextures).forEach(function (file) {
      var tex = s.artTextures[file];
      if (tex.saved === false && files.indexOf(file) < 0) files.push(file);
    });
    if (!files.length) {
      Blockbench.showMessageBox({title: 'Ovvar', icon: 'info', message: 'Nothing has changed since the checkout was opened.'});
      return;
    }
    var dir = [s.checkout, 'mods/ovvar/src/main/resources/art/ovvar/patches'].join('/');
    var existing = files.filter(function (f) { return s.io.exists(dir + '/' + f.replace('patches/', '')); });
    var write = function () {
      s.io.mkdirp(dir);
      files.forEach(function (file) {
        var image = OVVAR.model.readTexture(s.artTextures[file]);
        s.io.write(dir + '/' + file.replace('patches/', ''), s.io.encode(image));
      });
      s.dirty = {};
      var lines = Object.keys(s.added).map(function (id) {
        var p = s.added[id];
        var by = p.artist ? '.by("' + p.artist + '")' : '';
        if (p.seat) return 'Patch.seat("' + p.id + '", "' + p.name + '", ' + p.w + ', ' + p.h + ')' + by + ',';
        if (p.w === m.px && p.h === m.px) return 'new Patch("' + p.id + '", "' + p.name + '")' + by + ',';
        return 'new Patch("' + p.id + '", "' + p.name + '", ' + p.w + ', ' + p.h + ')' + by + ',';
      });
      new Dialog('ovvar_exported', {
        title: 'Exported ' + files.length + ' file(s)',
        form: {
          written: {type: 'info', text: files.join('\n')},
          paste: {type: 'info', text: lines.length
            ? 'Add to the ALL list in Patches.java, last (a design’s instant code is its position):\n\n' + lines.join('\n')
            : 'No new catalogue entries — only art was redrawn.'},
          next: {type: 'info', text: 'Then run ./gradlew :mods:ovvar:runDatagen and commit src/main/generated.'}
        }
      }).show();
    };
    if (existing.length) {
      Blockbench.showMessageBox({
        title: 'Ovvar', icon: 'warning',
        message: 'These files are already in the checkout and will be overwritten:\n\n' + existing.join('\n'),
        buttons: ['Overwrite', 'Cancel'], confirm: 0, cancel: 1
      }, function (button) { if (button === 0) write(); });
    } else {
      write();
    }
  };
  ```

### 8c — registration, events, unload

- [ ] **Step 6: Write `src/90-plugin.js`**

  Create `mods/ovvar/tools/blockbench/src/90-plugin.js`:
  ```js
  // Registration and cleanup. Blockbench runs a plugin file as
  // new Function('requireNativeModule', 'require', code), so `require` here is the scoped one --
  // 'path', 'zlib' and 'buffer' come free, and 'fs' asks once per folder and then only lets us
  // touch that folder. Nothing of ours reaches window, so unloading is a matter of taking back
  // what we added to Blockbench.

  OVVAR.require = typeof require === 'function' ? require : null;

  OVVAR.onPaint = function (event) {
    var s = OVVAR.state;
    if (!s.ctx) return;
    var touched = (event && event.aspects && event.aspects.textures) || [];
    for (var i = 0; i < touched.length; i++) {
      if (touched[i] && touched[i].ovvar_art) {
        s.dirty[touched[i].ovvar_art] = true;
        OVVAR.model.refresh();
        return;
      }
    }
  };

  if (typeof Plugin !== 'undefined') {
    Plugin.register('ovvar', {
      title: 'Ovvar',
      author: 'METAcraft',
      icon: 'checkroom',
      description: 'Draw a patch, sew it onto an ovve, and see it exactly as the game draws it.',
      about: 'Point it at a METAmods checkout (File > New > Ovvar). It reads '
        + 'mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json, so run '
        + './gradlew :mods:ovvar:runDatagen there first.',
      version: '1.0.0',
      min_version: '5.0.0',
      variant: 'desktop',
      tags: ['Minecraft: Java Edition'],

      onload: function () {
        OVVAR.model.registerFormat();
        OVVAR.panel.register();
        Blockbench.on('finished_edit', OVVAR.onPaint);
        OVVAR.state.listeners.push(['finished_edit', OVVAR.onPaint]);
      },

      onunload: function () {
        var s = OVVAR.state;
        s.listeners.forEach(function (l) { Blockbench.removeListener(l[0], l[1]); });
        s.listeners = [];
        if (s.panel) { s.panel.delete(); s.panel = null; }
        if (s.format) { s.format.delete(); s.format = null; }
        s.ctx = null;
        s.io = null;
        s.textures = {};
        s.artTextures = {};
        s.cubes = [];
        s.group = null;
      }
    });
  }
  ```

- [ ] **Step 7: Build and load the plugin for real**

  ```sh
  mods/ovvar/tools/blockbench/build.sh
  ```
  In Blockbench: *File › Plugins › Load Plugin from File…* → `ovvar.js`.

  **Verify in Blockbench 5 console** (a plugin loaded from a file is not in `Plugins.installed`, so check what it registered instead):
  ```js
  [!!Formats.ovvar, Formats.ovvar && Formats.ovvar.name, !!Interface.Panels.ovvar]
  ```
  Expected `[true, 'Ovvar', true]`.

- [ ] **Step 8: Open a checkout and check the permission prompt**

  *File › New › Ovvar*. The checkout dialog appears; type the worktree path and confirm.

  **Verify:** Blockbench asks *"Permission to access a folder requested"* naming the worktree path, with *Allow once* / *Always allow for this plugin*. Choose **Always allow for this plugin**. (This is per-directory: `require('fs', {scope})` returns a file system that throws on anything outside it — see `h4()` in the extracted bundle. The README must say so, because a second checkout prompts again.)

  If the manifest is missing the plugin must say so by name: rename it away and reopen, and check the message reads `No Ovvar manifest at …/manifest.json -- run ./gradlew :mods:ovvar:runDatagen in that checkout first.` Then rename it back.

  **Verify in Blockbench 5 console:**
  ```js
  [Texture.all.map(t => t.name).slice(0, 6), Cube.all.length, Project.texture_width, Project.texture_height]
  ```
  Expected: the four `ovve_*.png` textures first, `Cube.all.length` 6 (plus any inner-player cubes if one was added), and `[128, 64]`.

- [ ] **Step 9: Sew, unpick, and compare with the game**

  Sew ITK on `front_top_left`, Data on `back_big`, ITK on `back_top_left`, Rivals on the seat, ITK on `sleeve_out_top_l`. Screenshot (*View › Screenshot Model*) from the front and the back into `/tmp/ovvar-plugin-front.png` and `/tmp/ovvar-plugin-back.png`.

  Compare against `/tmp/ovvar-promo/shots-run2/05_closeup_chest.png`, `08_closeup_back_and_seat.png`, `09_closeup_back_big_cell.png` and `04_closeup_torso_sleeve.png`. Check, one at a time:
  1. the chest patch sits on the chest's top-left cell, bending round the corner where it laps over
  2. the big back patch fills the back, with the small one drawn **over** it
  3. the seat is one patch across both legs, unbroken at the seam, and reads the right way round from behind
  4. the left sleeve's patch is the right way round (not mirrored)
  5. unpicking the seat removes both halves

  Any mismatch is a bug in `composePiece`, not in the cubes — the cubes were proved in Task 0.

- [ ] **Step 10: New patch, add size, export**

  *New patch…* → id `spike_test`, name `Spike test`, 12×12, artist `Vlad`. Paint a few pixels on the new texture in Paint mode.

  **Verify:** the model updates on mouse-up (that is `finished_edit`), without a panel click.

  Sew it on `front_low_right`. *Add size…* → `spike_test` is not offered (it has one art and the scaler needs a 16×16); pick `it` at 8×8 instead and check the starting picture is the scaler's, not blank.

  *Export to repo* → confirm the overwrite warning names `it_8x8.png` if it is already drawn, and that after confirming:
  ```sh
  ls -la mods/ovvar/src/main/resources/art/ovvar/patches/spike_test.png
  git status --short mods/ovvar/src/main/resources/art/ovvar/patches/
  ```
  Expected: the file exists and `git status` shows it untracked. The dialog must show the line `new Patch("spike_test", "Spike test", 12, 12).by("Vlad"),`.

  Then clean up the spike:
  ```sh
  git checkout -- mods/ovvar/src/main/resources/art/ovvar/patches/
  rm -f mods/ovvar/src/main/resources/art/ovvar/patches/spike_test.png
  ```

- [ ] **Step 11: Unload leaves nothing behind**

  *File › Plugins*, uninstall Ovvar. **Verify in Blockbench 5 console:**
  ```js
  [Formats.ovvar, Interface.Panels.ovvar, window.OVVAR]
  ```
  Expected `[undefined, undefined, undefined]`. If `window.OVVAR` is defined, the development line from step 3 is still in `90-plugin.js` — remove it.

- [ ] **Step 12: Write the README**

  Create `mods/ovvar/tools/blockbench/README.md`:
  ```markdown
  # The Ovvar Blockbench plugin

  Draw a patch, sew it onto an ovve, and see it on a player exactly as the game draws it — no
  Minecraft running. Then export it into the checkout, ready to commit.

  ## Install

  1. Blockbench **5.0 or newer**, the desktop app (the web version cannot read your checkout).
  2. *File › Plugins › Load Plugin from File…* → `mods/ovvar/tools/blockbench/ovvar.js`.
  3. In the checkout, once: `./gradlew :mods:ovvar:runDatagen`. The plugin reads
     `mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json`, which that writes.

  ## Point it at a checkout

  *File › New › Ovvar*, then type the path of the folder holding `mods/ovvar`.

  Blockbench will ask for permission to **access a folder**, naming that path. Choose *Always allow
  for this plugin*. The permission is per folder: a second checkout asks again, and the plugin can
  touch nothing outside the folder you named — that is Blockbench's scoped file system, not a
  promise of ours.

  ## Workflow

  - **Chapter** and **Zipped down** pick the garment. A chapter with nothing to roll down has the
    checkbox greyed.
  - **Sew…** picks a patch and a spot. Spots are grouped by half. A seat patch has one place to go
    and nothing else may go there; everything else may overlap, which is the point of an ovve —
    the big back cell is meant to be worn under the two small ones.
  - **×** beside a placement unpicks it.
  - **New patch…** makes a blank art and a catalogue entry in this project. Ids are
    `[a-z0-9_]+` and unique; ordinary art is an even 6–16 px each way (12×12 is the usual), a seat
    patch is 16 wide and 8–10 tall.
  - **Add size…** draws a patch again at 8×8, 12×12 or 16×16 — for when the automatic shrink gets
    an outline or a letter wrong. It starts you from the shrink's own answer.
  - Paint in Blockbench's Paint mode. The ovve redraws on every finished stroke.
  - **Export to repo** writes every art you drew or changed to
    `mods/ovvar/src/main/resources/art/ovvar/patches/`, warns before overwriting anything, and then
    shows the `Patches.java` line(s) to paste. New entries go **last** in the `ALL` list: a design's
    instant code is its position, so inserting one in the middle repaints everything already sewn.
  - Then, in the checkout: `./gradlew :mods:ovvar:runDatagen` and commit `src/main/generated`.

  ## What the plugin does not do

  Dye colours and the instant channel, the wardrobe paper doll, armour worn over the ovve, posing,
  and editing the chapter garments themselves (they are only displayed).

  ## Tests

  ```sh
  node mods/ovvar/tools/blockbench/test.js <checkout>
  ```

  Node 20 or newer; no dependencies. It builds the plugin the way `build.sh` does and runs the
  composition half against the committed textures: every placement texture, every trim, every
  generated art size, the chapter layers, 512 samples of the squeeze. CI does not run it — run it
  by hand, and always before committing a rebuilt `ovvar.js`.

  ## Building

  ```sh
  mods/ovvar/tools/blockbench/build.sh
  ```

  `ovvar.js` is `cat src/*.js`, nothing else: no npm, no bundler, so a diff of the built file is a
  diff of the sources. Commit both.

  ## How it stays honest

  The plugin has no copy of `Spot`, `Patches` or `Chapter`: datagen writes them out as
  `manifest.json` and a game test (`BlockbenchManifestTests`) holds that file to the enums field for
  field, `Spot.anchored` included. A cell moved in Java is a changed manifest, and a changed
  manifest is a changed plugin — or a failing test.
  ```

- [ ] **Step 13: Rebuild, run the Node tests, commit**

  ```sh
  mods/ovvar/tools/blockbench/build.sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 13`, `# fail 0` (the glue adds no Node tests — it cannot be unit-tested).

  ```sh
  git add mods/ovvar/tools/blockbench/src/20-model.js mods/ovvar/tools/blockbench/src/30-panel.js mods/ovvar/tools/blockbench/src/90-plugin.js
  git add mods/ovvar/tools/blockbench/README.md mods/ovvar/tools/blockbench/ovvar.js
  git commit -m "ovvar: the Blockbench plugin itself — the ovve model, the sidebar and the export"
  ```

---

## Task 9: Green everywhere, and a pointer from the mod's README

**Files:**
- Delete: `mods/ovvar/tools/blockbench/NOTES-spike.md`
- Modify: `mods/ovvar/README.md` (a new `## Blockbench plugin` section immediately before `## How the look works`, which today begins at line 640)
- Modify: `mods/ovvar/tools/blockbench/ovvar.js` (final rebuild)

**Interfaces:** none new.

- [ ] **Step 1: Drop the spike notes**

  Task 0's answers are now written into `20-model.js`'s `CUBES` comment and into `composePiece`'s, which is where a reader will look for them.
  ```sh
  git rm mods/ovvar/tools/blockbench/NOTES-spike.md
  ```

- [ ] **Step 2: Rebuild and run everything**

  ```sh
  mods/ovvar/tools/blockbench/build.sh
  node "mods/ovvar/tools/blockbench/test.js" "$PWD"
  ```
  Expected: `# pass 13`, `# fail 0`.

  ```sh
  ./gradlew :mods:ovvar:checkstyleMain :mods:ovvar:compileJava
  ./gradlew :mods:ovvar:runDatagen
  git status --short mods/ovvar/src/main/generated
  ```
  Expected: `BUILD SUCCESSFUL` twice, and `git status` on `src/main/generated` reports **nothing** — datagen is idempotent, so a second run after Task 2's commit must change no file.

  ```sh
  JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:ovvar:runServer -PrunDir=/tmp/ovvar-gametest-bb
  ```
  Expected: all game tests pass, the three `BlockbenchManifestTests` among them.

- [ ] **Step 3: Add the README section**

  In `mods/ovvar/README.md`, insert immediately **before** the line `## How the look works`:
  ```markdown
  ## Blockbench plugin

  `tools/blockbench/ovvar.js` is a Blockbench 5 desktop plugin: draw a patch, sew it onto an ovve,
  and see it on a player model exactly as the game draws it, without starting the game — then
  export the art back into this checkout. It has no copy of `Spot`, `Patches` or `Chapter`; datagen
  writes those out as `src/main/generated/ovvar/blockbench/manifest.json`, `BlockbenchManifestTests`
  holds that file to the enums field for field (`Spot.anchored` included), and the plugin's own
  Node tests hold its composition to the very placement, trim and garment textures the pack ships.
  So a cell moved in `Spot.java` is a failing test, not a plugin that quietly draws last week's
  model.

  Install, permissions, the workflow and how to run its tests: `tools/blockbench/README.md`.
  ```

- [ ] **Step 4: Commit**

  ```sh
  git add mods/ovvar/README.md mods/ovvar/tools/blockbench/ovvar.js
  git commit -m "ovvar: the README points at the Blockbench plugin"
  ```

- [ ] **Step 5: Final check of the whole branch**

  ```sh
  git status --short
  git log --oneline -8
  ```
  Expected: a clean tree, and eight commits (`ovvar: spike notes…` through `ovvar: the README points…`), none of them carrying a `Co-Authored-By` trailer:
  ```sh
  git log -8 --format=%B | grep -c 'Co-Authored-By' || echo "no trailers: good"
  ```
  Expected: `no trailers: good`.

---

## Done means

- `node mods/ovvar/tools/blockbench/test.js <checkout>` → 13 passed, 0 failed.
- `./gradlew :mods:ovvar:runDatagen` twice in a row leaves `src/main/generated` unchanged.
- `./gradlew :mods:ovvar:checkstyleMain` passes.
- The game tests pass, `BlockbenchManifestTests`'s three included.
- Blockbench 5 loads `ovvar.js`, *File › New › Ovvar* builds the six cubes, sewing redraws the model, painting redraws it on mouse-up, *Export to repo* writes a PNG and prints a `Patches.java` line, and uninstalling leaves `Formats.ovvar`, `Interface.Panels.ovvar` and `window.OVVAR` all `undefined`.
- Screenshots of the plugin's chest, back, seat and left sleeve match `/tmp/ovvar-promo/shots-run2/{05,08,09,04}_*.png` by eye.

## Things a later version owes

- **`placedWrapped` on a limb.** The plugin bakes the squeeze for every side cell, limbs included; datagen only ever bakes it for body cells, because vanilla draws the trim channel and a limb trim would leak onto the other limb (`Trims.fits`). The limb case is therefore held only by eye (Task 8 step 9), not by a golden. Mirroring and wrapping commute here — the squeeze is symmetric about the anchor face's centre and a limb cell is that whole face — but nothing tests it.
- **Tinted and hand-drawn chapters.** `buildBase` refuses a chapter with a `tint` (`it_kisel`); the plugin loads its committed layer instead, which is the same pixels. Porting `Tex.tinted`/`brightened` (HSB) would only be needed if the plugin ever edited the garments, which is out of scope.
- **The inner player.** The spec asks for a Steve/Alex body under the cloth. Task 8 does not build one — the six armour cubes are the model — because `ModelFormat.onSetup` cannot pull in the Skin format's own template. Adding it is a `Blockbench.import` of `assets/skin_model_templates` and is cosmetic.
