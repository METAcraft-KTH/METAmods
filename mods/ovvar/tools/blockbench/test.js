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

test('png round-trip preserves RGB under alpha 0 (the canvas would not)', () => {
  // it-nercabbad.png carries 182 texels with alpha 0 and non-zero RGB -- datagen's `placed`
  // treats any texel with a non-zero packed ARGB as present, alpha-0 included, so this fixture's
  // hidden colour under transparency is part of the goldens. Assert the fixture still has such
  // pixels (so this test fails loudly, not silently, if the file is ever replaced) and that the
  // codec's round trip is byte-identical, RGB-under-alpha-0 included.
  const p = path.join(CHECKOUT, 'mods/ovvar/src/main/resources/art/ovvar/it-nercabbad.png');
  const im = io.decode(io.read(p));
  assert.strictEqual(im.w, 64);
  assert.strictEqual(im.h, 64);
  assert.strictEqual(im.data.length, 64 * 64 * 4);
  let alphaZeroWithColour = 0;
  for (let i = 0; i < im.data.length; i += 4) {
    if (im.data[i + 3] === 0 && (im.data[i] !== 0 || im.data[i + 1] !== 0 || im.data[i + 2] !== 0)) {
      alphaZeroWithColour++;
    }
  }
  assert.strictEqual(alphaZeroWithColour, 182,
    'fixture no longer has the expected alpha-0-with-colour pixels; this test would not catch a premultiplying codec');
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

// The checkout has no palette or grayscale PNGs, so colour types 0 and 3 (with tRNS) are
// otherwise unexercised. These two tiny fixtures were generated once with Pillow and are
// committed as data (no npm dependency, nothing regenerates them at test time):
//
//   python3 -c "
//   from PIL import Image
//   palette_pixels = [[0,1,2,3],[3,2,1,0],[0,0,3,3],[1,2,3,0]]
//   im = Image.new('P', (4, 4))
//   pal = [255,0,0, 0,255,0, 0,0,255, 255,255,255] + [0] * (768 - 12)
//   im.putpalette(pal)
//   for y, row in enumerate(palette_pixels):
//       for x, idx in enumerate(row):
//           im.putpixel((x, y), idx)
//   im.info['transparency'] = bytes([255, 128, 0, 255])
//   im.save('palette-trns-4x4.png', 'PNG')
//   gray_pixels = [[0,64,128,255],[255,128,64,0],[16,32,48,64],[200,150,100,50]]
//   gim = Image.new('L', (4, 4))
//   for y, row in enumerate(gray_pixels):
//       for x, v in enumerate(row):
//           gim.putpixel((x, y), v)
//   gim.save('gray-4x4.png', 'PNG')
//   "
//
// Both came out 8-bit (this decoder only supports 8-bit depth; Pillow does not have a
// documented way to force sub-byte palette/grayscale depths, so there is no 2-bit or 16-bit
// fixture here -- OVVAR.png.decode throws on any depth other than 8, which the truncated-file
// test below exercises the general failure path for).
const FIXTURES = path.join(HERE, 'test-fixtures');

test('png decodes a palette (colour type 3) image with tRNS', () => {
  const im = io.decode(io.read(path.join(FIXTURES, 'palette-trns-4x4.png')));
  assert.strictEqual(im.w, 4);
  assert.strictEqual(im.h, 4);
  const RED = [255, 0, 0, 255];
  const GREEN_HALF = [0, 255, 0, 128];
  const BLUE_TRANSPARENT = [0, 0, 255, 0];
  const WHITE = [255, 255, 255, 255];
  const rows = [
    [RED, GREEN_HALF, BLUE_TRANSPARENT, WHITE],
    [WHITE, BLUE_TRANSPARENT, GREEN_HALF, RED],
    [RED, RED, WHITE, WHITE],
    [GREEN_HALF, BLUE_TRANSPARENT, WHITE, RED]
  ];
  for (let y = 0; y < 4; y++) {
    for (let x = 0; x < 4; x++) {
      const d = (y * 4 + x) * 4;
      assert.deepStrictEqual(Array.from(im.data.subarray(d, d + 4)), rows[y][x], `pixel (${x},${y})`);
    }
  }
});

test('png decodes a grayscale (colour type 0) image', () => {
  const im = io.decode(io.read(path.join(FIXTURES, 'gray-4x4.png')));
  assert.strictEqual(im.w, 4);
  assert.strictEqual(im.h, 4);
  const rows = [
    [0, 64, 128, 255],
    [255, 128, 64, 0],
    [16, 32, 48, 64],
    [200, 150, 100, 50]
  ];
  for (let y = 0; y < 4; y++) {
    for (let x = 0; x < 4; x++) {
      const d = (y * 4 + x) * 4;
      const v = rows[y][x];
      assert.deepStrictEqual(Array.from(im.data.subarray(d, d + 4)), [v, v, v, 255], `pixel (${x},${y})`);
    }
  }
});

test('png decode throws on a truncated file instead of returning garbage', () => {
  const full = io.read(path.join(CHECKOUT, 'mods/ovvar/src/main/resources/art/ovvar/patches/itk_8x8.png'));
  const truncated = full.subarray(0, 40);
  assert.throws(() => io.decode(truncated));
});

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

test('the ported base build equals the committed chapter layer textures', () => {
  const ctx = context();
  const m = ctx.m;
  let checked = 0;
  for (const chapter of m.chapters) {
    // A tinted chapter's cloth goes through Tex.tinted (HSB), which the plugin does not port --
    // it loads the committed layer instead. The untinted ones prove withLeft and flattened.
    // (The manifest omits a null property, so an untinted chapter has no `tint` key at all.)
    if (chapter.tint) continue;
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
  // Every kind of half, because the Task 0 spike found a generated top's arm strip all but
  // symmetric (36 texels of shading): a mirror strip that did nothing would barely show there.
  // The Media frack's leggings and a rolled-down ovve carry a left leg that is materially its
  // own art, and those are what pin the copy down. The counts today are 36 / 64 / 28 differing
  // texels; the thresholds sit under them so a redrawn ovve does not fail this test, while a
  // deleted mirror strip (which would make B identical to A) still does.
  const halves = [
    ['top', 'data', false, m.skinBoxes.rightArm, 1],
    ['bottom', 'media', false, m.skinBoxes.rightLeg, 32],
    ['bottom', 'data', true, m.skinBoxes.rightLeg, 8]
  ];
  for (const [piece, chapter, nercabbad, box, least] of halves) {
    const {A, B} = OVVAR.compose.composePiece(ctx, piece, {chapter: chapter, nercabbad: nercabbad, placements: []});
    const what = chapter + '/' + piece + (nercabbad ? ' (rolled down)' : '');
    const my = box[1] - m.mirrorShift;              // the mirror strip, one box up
    let differ = 0;
    for (let y = 0; y < box[3] * D; y++) {
      for (let x = 0; x < box[2] * D; x++) {
        const strip = t.get(A, box[0] * D + x, my * D + y);   // the mirror strip on A
        const limb = t.get(B, box[0] * D + x, box[1] * D + y);
        assert.strictEqual(limb, strip, what + ' B limb texel (' + x + ',' + y + ') is not the mirror strip');
        if (t.get(A, box[0] * D + x, box[1] * D + y) !== limb) differ++;
      }
    }
    // The left limb's art is its own, so B must differ from A over the limb's own rows.
    assert.ok(differ >= least,
      what + ': B differs from A on only ' + differ + ' limb texels (wanted at least ' + least + ')');
  }
  // The body box is the same on both -- it is one box, drawn unmirrored.
  const {A, B} = OVVAR.compose.composePiece(ctx, 'top', {chapter: 'data', nercabbad: false, placements: []});
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

test('copyRect is a hard copy where blit leaves the destination alone', () => {
  // The mirror strip is a copy, not a blit: where the left sleeve is transparent, the right
  // sleeve's cloth underneath must be wiped, not left showing. No committed chapter has such a
  // texel today, so the difference is pinned here rather than by a golden.
  const t = OVVAR.tex;
  const src = t.blank(2, 1);
  t.set(src, 0, 0, 0x00123456);           // alpha 0, but a colour: blit skips it, copyRect does not
  t.set(src, 1, 0, 0xFF00FF00);
  const dst = t.blank(2, 1);
  t.set(dst, 0, 0, 0xFFAABBCC);
  t.set(dst, 1, 0, 0xFFAABBCC);
  assert.strictEqual(t.get(t.blit(dst, src, 0, 0, 2, 1, 0, 0), 0, 0), 0xFFAABBCC);
  assert.strictEqual(t.get(t.copyRect(dst, src, 0, 0, 2, 1, 0, 0), 0, 0), 0x00123456);
  assert.strictEqual(t.get(t.copyRect(dst, src, 0, 0, 2, 1, 0, 0), 1, 0), 0xFF00FF00);
  // Neither one touches its argument.
  assert.strictEqual(t.get(dst, 0, 0), 0xFFAABBCC);
  // ... and the mirror strip is the caller that needs it: a transparent texel on the strip wipes
  // the limb row underneath rather than letting the right limb's cloth show through.
  const ctx = context();
  const m = ctx.m;
  const D = m.detail;
  const box = m.skinBoxes.rightArm;
  const my = box[1] - m.mirrorShift;
  const fake = t.blank(m.texture[0], m.texture[1]);
  t.set(fake, box[0] * D, box[1] * D, 0xFFAABBCC);        // the right sleeve's cloth
  t.set(fake, box[0] * D, my * D, 0x00123456);            // the left sleeve, transparent there
  assert.strictEqual(t.get(OVVAR.compose.mirrorStrip(m, 'top', fake), box[0] * D, box[1] * D), 0x00123456);
});

test('a placement naming an unknown cell warns instead of throwing', () => {
  const ctx = context();
  const design = {chapter: 'data', nercabbad: false, placements: [
    {cell: 'no_such_cell', patch: 'itk'},
    {cell: 'another_ghost', patch: 'itk'},          // two, because the sort compares them to each other
    {cell: 'back_big', patch: 'itk'},
    {cell: 'back_top_left', patch: 'no_such_patch'}
  ]};
  // The sort must not be the thing that throws: an unknown cell has no layer to compare.
  assert.deepStrictEqual(OVVAR.compose.stacked(ctx.m, design.placements).map(p => p.cell),
    ['no_such_cell', 'another_ghost', 'back_big', 'back_top_left']);
  const out = OVVAR.compose.compose(ctx, design);
  assert.ok(out.top.A && out.top.B && out.bottom.A && out.bottom.B);
  // Once per half, for each of the three bad placements.
  assert.deepStrictEqual(ctx.warnings.filter(w => w.indexOf('unknown placement') === 0).sort(), [
    'unknown placement another_ghost/itk', 'unknown placement another_ghost/itk',
    'unknown placement back_top_left/no_such_patch', 'unknown placement back_top_left/no_such_patch',
    'unknown placement no_such_cell/itk', 'unknown placement no_such_cell/itk'
  ]);
  // The good placement still drew.
  const bare = OVVAR.compose.composePiece(ctx, 'top', {chapter: 'data', nercabbad: false, placements: []});
  assert.ok(OVVAR.tex.diff(bare.A, out.top.A, []).length > 0, 'the one good placement was lost');
});

test('a patch with nothing painted on it says so, rather than blaming the cell', () => {
  const ctx = context();
  const m = ctx.m;
  // A patch invented in Blockbench and not yet drawn on: the plugin puts a blank image in front of
  // the catalogue, and every cell it could go on would otherwise report "lands entirely off".
  const blank = {file: 'patches/blank_probe.png', w: 12, h: 12, default: true, generated: false};
  const patch = {id: 'blank_probe', name: 'Blank probe', seat: false, w: 12, h: 12,
    arts: [blank], fits: {over: blank.file, clipped: blank.file, filled: blank.file}};
  m.patches.push(patch);
  m.patchById[patch.id] = patch;
  m.artByFile[blank.file] = blank;
  const empty = OVVAR.tex.blank(12, 12);
  const read = ctx.art;
  ctx.art = (file) => (file === blank.file ? empty : read(file));

  const design = {chapter: 'data', nercabbad: false, placements: [{cell: 'front_top_left', patch: 'blank_probe'}]};
  const bare = OVVAR.compose.compose(ctx, {chapter: 'data', nercabbad: false, placements: []});
  ctx.warnings.length = 0;
  const out = OVVAR.compose.compose(ctx, design);
  assert.deepStrictEqual(ctx.warnings, ['Blank probe is blank; paint it'],
    'a blank art warns once, by name, and does not mention the cell');
  assert.deepStrictEqual(OVVAR.tex.diff(bare.top.A, out.top.A, []), [], 'a blank patch drew something');

  // Paint one texel and the warning goes; the cell is innocent, so nothing says "lands off".
  const painted = OVVAR.tex.blank(12, 12);
  OVVAR.tex.set(painted, 6, 6, 0xFF00FF00);
  ctx.art = (file) => (file === blank.file ? painted : read(file));
  ctx.warnings.length = 0;
  const drawn = OVVAR.compose.compose(ctx, design);
  assert.deepStrictEqual(ctx.warnings, []);
  assert.ok(OVVAR.tex.diff(bare.top.A, drawn.top.A, []).length > 0, 'the painted texel never landed');
});

test('a BODY placement reaches both textures and a limb one only its own', () => {
  const ctx = context();
  const t = OVVAR.tex;
  const bare = {chapter: 'data', nercabbad: false, placements: []};
  const base = OVVAR.compose.composePiece(ctx, 'top', bare);
  function changed(a, b) {
    let n = 0;
    for (let y = 0; y < a.h; y++) for (let x = 0; x < a.w; x++) if (t.get(a, x, y) !== t.get(b, x, y)) n++;
    return n;
  }
  // The chest and the back are one box, worn by both cubes: a patch sewn there has to be on B as
  // well, or the left half of the body shows bare cloth where the patch should be.
  const body = OVVAR.compose.composePiece(ctx, 'top',
    {chapter: 'data', nercabbad: false, placements: [{cell: 'back_big', patch: 'itk'}]});
  const onA = changed(base.A, body.A);   // 196 texels today
  assert.ok(onA >= 128, 'the back patch changed only ' + onA + ' texels of A');
  assert.strictEqual(changed(base.B, body.B), onA, 'the back patch did not reach B the same way');
  // A limb box is not shared: the right sleeve is A's alone.
  const sleeve = OVVAR.compose.composePiece(ctx, 'top',
    {chapter: 'data', nercabbad: false, placements: [{cell: 'sleeve_out_top_r', patch: 'itk'}]});
  assert.ok(changed(base.A, sleeve.A) > 0, 'the right sleeve patch did not draw on A');
  assert.strictEqual(changed(base.B, sleeve.B), 0, 'the right sleeve patch reached B');
});

test('a side cell is composed through the squeeze and a top-face cell flat', () => {
  const ctx = context();
  const m = ctx.m;
  const t = OVVAR.tex;
  const W = m.texture[0], H = m.texture[1];
  const patch = m.patchById.itk;
  // A side cell (the back) bends round the box's corners, so its art is baked through
  // placedWrapped; a top-face cell (a shoulder) is not on the strip's perimeter and stays flat.
  const cases = [['back_big', OVVAR.compose.placedWrapped], ['shoulder_r', OVVAR.compose.placed]];
  for (const [id, placer] of cases) {
    const cell = m.cellById[id];
    const drawn = OVVAR.compose.placementArt(ctx, cell, patch, cell.side);
    const want = t.blit(ctx.base('data', 'top', false), placer(m, cell, drawn.art, drawn.x), 0, 0, W, H, 0, 0);
    const got = OVVAR.compose.composePiece(ctx, 'top',
      {chapter: 'data', nercabbad: false, placements: [{cell: id, patch: 'itk'}]}).A;
    assert.deepStrictEqual(t.diff(want, got, []), [], id);
    // ... and the other placer would have given a different picture, so this pins the choice.
    const other = (placer === OVVAR.compose.placed ? OVVAR.compose.placedWrapped : OVVAR.compose.placed)(m, cell, drawn.art, drawn.x);
    assert.ok(other === null || t.diff(t.blit(ctx.base('data', 'top', false), other, 0, 0, W, H, 0, 0), got, []).length > 0,
      id + ': the two placers agree, so this test proves nothing');
  }
});
