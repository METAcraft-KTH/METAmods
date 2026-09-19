// The Blockbench half: the format, the six armour cubes, and the textures they wear. Everything
// that knows what a pixel should be lives in 10-compose.js; this file only hangs the result on a
// model. Nothing here runs under Node.

OVVAR.state = {
  io: null,          // the scoped file system, once a checkout is open
  ctx: null,         // OVVAR.compose.ctx
  checkout: null,
  project: null,     // the ModelProject the cubes below belong to
  design: {chapter: null, nercabbad: false, placements: []},
  textures: {},      // topA, topB, bottomA, bottomB
  artTextures: {},   // catalogue file name -> Texture Vlad paints on
  added: {},         // patch id -> the catalogue entry this project invented
  sizes: [],         // {patch, file, w, h} this project drew a patch again at
  dirty: {},         // art file name -> true, for Export
  cubes: [],
  group: null,
  format: null,
  panel: null,
  listeners: [],
  codecListeners: [],
  properties: [],    // the Blockbench Properties the plugin declares, to take back on unload
  pending: null      // the requestAnimationFrame that coalesces a burst of refreshes
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

/**
 * The UV resolution is the SKIN's, 64 x 32, not the texture's 128 x 64.
 *
 * Blockbench computes a box UV in cube-size units offset by `uv_offset` and only then divides by
 * the UV resolution (Canvas.updateUV in outliner/types/cube.js), so the offsets above -- 16, 40,
 * 0 -- are skin texels and the divisor has to be the skin. The mod's armour layers are that same
 * vanilla layout drawn at 2x; the image is 128 x 64 and every face still lands on whole texels.
 * Setting the resolution to 128 x 64 instead loses the arms and reads the body as garbage.
 */
OVVAR.model.uvSize = function (m) { return [m.skin[0], m.skin[1]]; };

OVVAR.model.registerFormat = function () {
  OVVAR.state.format = new ModelFormat('ovvar', {
    name: 'Ovvar',
    description: 'A METAcraft ovve: patches on the armour model, drawn the way the game draws them.',
    icon: 'checkroom',
    category: 'minecraft',
    target: ['Minecraft: Java Edition'],
    box_uv: true,
    // The built-in `skin` format is single_texture and could not hold top and bottom at once,
    // let alone their two mirror-strip variants. This one holds four.
    single_texture: false,
    per_texture_uv_size: true,
    bone_rig: false,
    centered_grid: true,
    model_identifier: false,
    rotate_cubes: false,
    integer_size: false,
    // Blockbench calls this with (project, new_model): true from File > New, false when a saved
    // project is being set up, and nothing at all when a project is converted into this format.
    // Only a brand new one should go looking for a checkout.
    onSetup: function (project, newModel) {
      if (!newModel) return;
      var skin = [64, 32];
      Project.texture_width = skin[0];
      Project.texture_height = skin[1];
      OVVAR.panel.askForCheckout();
    }
  });
  return OVVAR.state.format;
};

/** True when the six cubes of this state are still in the project they were made in. */
OVVAR.model.hasGeometry = function () {
  var s = OVVAR.state;
  return !!(s.project && typeof Project !== 'undefined' && Project === s.project && s.cubes.length);
};

/**
 * Every trace of the checkout that was open: the art textures (which are that checkout's PNGs,
 * named after its files), what was drawn on them and what the project invented. Pointing the same
 * project at a second checkout without this would draw B's ovve out of A's art and then export A's
 * pixels into B, because loadCatalogueTextures skips any file it already has a texture for.
 *
 * The design is kept: the placements are the artist's work, and composePiece warns by name about
 * any cell or patch the new manifest has never heard of.
 */
OVVAR.model.forgetCheckout = function () {
  var s = OVVAR.state;
  Object.keys(s.artTextures).forEach(function (file) {
    var tex = s.artTextures[file];
    if (tex && Texture.all.indexOf(tex) >= 0) tex.remove(true);
  });
  s.artTextures = {};
  s.dirty = {};
  s.added = {};
  s.sizes = [];
};

/** The project these cubes belonged to has gone: let the next one build its own. */
OVVAR.model.forgetProject = function () {
  var s = OVVAR.state;
  s.project = null;
  s.cubes = [];
  s.group = null;
  s.textures = {};
  s.artTextures = {};
  s.checkout = null;
  s.io = null;
  s.ctx = null;
  s.dirty = {};
  s.added = {};
  s.sizes = [];
  s.design = {chapter: null, nercabbad: false, placements: []};
  if (s.panel && s.panel.inside_vue) {
    s.panel.inside_vue.design = s.design;
    s.panel.inside_vue.warnings = [];
    s.panel.inside_vue.revision++;
  }
};

/**
 * Open a checkout: load its manifest, build the cubes, make the textures, compose once.
 *
 * Two things are settled before anything is touched. One ovve at a time: the state below is one
 * object for the whole app, so a second Ovvar project would take this one's textures away from it
 * -- it is refused by name instead. And the manifest is loaded first, so a mistyped path leaves the
 * open ovve exactly as it was; only then, if this is a different checkout and there is unexported
 * art, is the artist asked before it goes.
 */
OVVAR.model.build = function (checkout) {
  var s = OVVAR.state;
  if (s.project && typeof Project !== 'undefined' && Project !== s.project) {
    throw new Error('Another Ovvar project is already open. Close the other Ovvar tab first: '
      + 'the plugin draws one ovve at a time.');
  }
  var io = OVVAR.makeIo(OVVAR.require, checkout);
  var m = OVVAR.loadManifest(io, checkout);
  var losing = (s.checkout && s.checkout !== checkout) ? Object.keys(s.dirty) : [];
  if (losing.length) {
    Blockbench.showMessageBox({
      title: 'Ovvar', icon: 'warning',
      message: losing.length + (losing.length === 1 ? ' patch has' : ' patches have')
        + ' not been exported and will be discarded:\n\n' + losing.join('\n'),
      buttons: ['Open anyway', 'Cancel'], confirmIndex: 0, cancelIndex: 1
    }, function (button) { if (button === 0) OVVAR.model.open(io, m, checkout); });
    return;
  }
  OVVAR.model.open(io, m, checkout);
};

/** The rest of `build`, once the questions it had to ask have been answered. */
OVVAR.model.open = function (io, m, checkout) {
  var s = OVVAR.state;
  if (s.checkout && s.checkout !== checkout) OVVAR.model.forgetCheckout();
  s.checkout = checkout;
  s.io = io;
  s.ctx = OVVAR.model.paintable(OVVAR.compose.ctx(io, m));
  if (!s.design.chapter || !m.chapterById[s.design.chapter]) s.design.chapter = m.chapters[0].id;
  if (!m.chapterById[s.design.chapter].rollable) s.design.nercabbad = false;

  var uv = OVVAR.model.uvSize(m);
  Project.texture_width = uv[0];
  Project.texture_height = uv[1];

  if (!OVVAR.model.hasGeometry()) {
    Undo.initEdit({outliner: true, elements: [], textures: []});
    s.project = Project;
    s.group = new Group({name: 'ovve', origin: [0, 0, 0]}).init();
    s.cubes = OVVAR.model.CUBES.map(function (c) {
      var cube = new Cube({
        name: c.name, from: c.from.slice(), to: c.to.slice(), origin: [0, 0, 0],
        box_uv: true, uv_offset: c.uv.slice(), inflate: c.inflate, mirror_uv: c.mirror
      });
      cube.addTo(s.group);
      return cube.init();
    });

    s.textures = {};
    ['topA', 'topB', 'bottomA', 'bottomB'].forEach(function (key) {
      var tex = new Texture({name: 'ovve_' + key + '.png'})
        .fromDataURL(io.dataUrl(OVVAR.tex.blank(m.texture[0], m.texture[1]))).add(false);
      tex.uv_width = uv[0];
      tex.uv_height = uv[1];
      s.textures[key] = tex;
    });
    OVVAR.model.CUBES.forEach(function (c, i) {
      s.cubes[i].applyTexture(s.textures[c.piece + c.side], true);
    });
    Undo.finishEdit('Build the ovve', {outliner: true, elements: s.cubes, textures: Texture.all});
  }

  OVVAR.model.loadCatalogueTextures();
  OVVAR.model.refresh();
  Canvas.updateAll();
  try { localStorage.setItem('ovvar_checkout', checkout); } catch (e) { /* a private window; no matter */ }
};

// ---- the project file
//
// Blockbench already saves the cubes, the four garment textures and every patch art as textures in
// the .bbmodel -- what it cannot know is which ovve they are: the chapter, whether it is zipped
// down, what is sewn where, which arts have not been exported yet, and which patches and sizes this
// project invented that no manifest has heard of. That is what goes under `model.ovvar`, written on
// save and read back after the textures have been parsed.

OVVAR.model.SAVE_VERSION = 1;

/** What `save_project` writes into the .bbmodel. */
OVVAR.model.saveState = function () {
  var s = OVVAR.state;
  return {
    version: OVVAR.model.SAVE_VERSION,
    checkout: s.checkout,
    design: {
      chapter: s.design.chapter,
      nercabbad: !!s.design.nercabbad,
      placements: s.design.placements.map(function (p) { return {cell: p.cell, patch: p.patch}; })
    },
    dirty: Object.keys(s.dirty),
    added: JSON.parse(JSON.stringify(s.added)),
    sizes: JSON.parse(JSON.stringify(s.sizes))
  };
};

/**
 * Put back what this project invented before anything asks the manifest for it: a patch drawn here
 * and never exported is in no manifest, and a placement naming it would only warn.
 */
OVVAR.model.replayCatalogue = function (m, saved) {
  Object.keys(saved.added || {}).forEach(function (id) {
    if (m.patchById[id]) return;
    var entry = JSON.parse(JSON.stringify(saved.added[id]));
    m.patches.push(entry);
    m.patchById[id] = entry;
    entry.arts.forEach(function (a) { m.artByFile[a.file] = a; });
  });
  (saved.sizes || []).forEach(function (size) {
    var patch = m.patchById[size.patch];
    if (!patch) return;
    var art = {file: size.file, w: size.w, h: size.h, 'default': false, generated: false, source: null};
    OVVAR.model.putArt(m, patch, art);
    OVVAR.panel.refit(m, patch);
  });
};

/**
 * An art on a patch, replacing the entry of the same name rather than sitting beside it -- drawing
 * a size the generator had already scaled must take that size's place, not leave two arts claiming
 * the same file.
 */
OVVAR.model.putArt = function (m, patch, art) {
  var at = -1;
  for (var i = 0; i < patch.arts.length; i++) if (patch.arts[i].file === art.file) at = i;
  if (at >= 0) patch.arts[at] = art; else patch.arts.push(art);
  patch.arts.sort(function (a, b) { return a.w * a.h - b.w * b.h; });
  m.artByFile[art.file] = art;
};

/** The cubes, the group and the four garment textures a reopened project already has. */
OVVAR.model.adopt = function () {
  var s = OVVAR.state;
  s.project = Project;
  s.group = Group.all.find(function (g) { return g.name === 'ovve'; }) || null;
  s.cubes = OVVAR.model.CUBES.map(function (c) {
    return Cube.all.find(function (cube) { return cube.name === c.name; });
  });
  if (s.cubes.indexOf(undefined) >= 0) { s.cubes = []; s.project = null; return false; }
  s.textures = {};
  var ok = true;
  ['topA', 'topB', 'bottomA', 'bottomB'].forEach(function (key) {
    var tex = Texture.all.find(function (t) { return t.name === 'ovve_' + key + '.png'; });
    if (!tex) ok = false; else s.textures[key] = tex;
  });
  if (!ok) { s.cubes = []; s.textures = {}; s.project = null; return false; }
  s.artTextures = {};
  Texture.all.forEach(function (t) { if (t.ovvar_art) s.artTextures[t.ovvar_art] = t; });
  return true;
};

/**
 * Read `model.ovvar` back after the codec has parsed the textures. The checkout is opened again
 * from its path -- the manifest is the repo's, never the project file's -- and what the project
 * invented is replayed on top of it.
 */
OVVAR.model.restoreState = function (saved) {
  var s = OVVAR.state;
  if (!saved || saved.version > OVVAR.model.SAVE_VERSION) return;
  if (s.project && Project !== s.project) {
    Blockbench.showMessageBox({title: 'Ovvar', icon: 'warning',
      message: 'Another Ovvar project is already open, so this one was opened without its ovve. '
        + 'Close the other Ovvar tab and reopen this file.'});
    return;
  }
  if (!OVVAR.model.adopt()) return;
  s.design = {
    chapter: saved.design && saved.design.chapter || null,
    nercabbad: !!(saved.design && saved.design.nercabbad),
    placements: ((saved.design && saved.design.placements) || []).slice()
  };
  if (s.panel && s.panel.inside_vue) s.panel.inside_vue.design = s.design;
  s.added = JSON.parse(JSON.stringify(saved.added || {}));
  s.sizes = JSON.parse(JSON.stringify(saved.sizes || []));
  s.dirty = {};
  (saved.dirty || []).forEach(function (f) { s.dirty[f] = true; });
  try {
    var io = OVVAR.makeIo(OVVAR.require, saved.checkout);
    var m = OVVAR.loadManifest(io, saved.checkout);
    OVVAR.model.replayCatalogue(m, saved);
    s.checkout = saved.checkout;
    s.io = io;
    s.ctx = OVVAR.model.paintable(OVVAR.compose.ctx(io, m));
    var uv = OVVAR.model.uvSize(m);
    Project.texture_width = uv[0];
    Project.texture_height = uv[1];
    OVVAR.model.loadCatalogueTextures();
    Object.keys(s.dirty).forEach(OVVAR.model.readBack);
    OVVAR.model.refresh();
    Canvas.updateAll();
  } catch (e) {
    Blockbench.showMessageBox({title: 'Ovvar', icon: 'warning',
      message: 'This ovve was drawn against ' + saved.checkout + ', which will not open:\n\n'
        + String(e && e.message ? e.message : e) + '\n\nUse "Open checkout…" to point it somewhere else.'});
  }
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
 * texture is a patch art -- a few 128x64 blits, milliseconds.
 *
 * `ctx.warnings` is append-only by design (composePiece only ever pushes), so it is emptied in
 * place here, before the run that fills it, and what comes out is handed to the panel.
 */
OVVAR.model.refresh = function () {
  var s = OVVAR.state;
  if (!s.ctx) return;
  s.ctx.warnings.length = 0;
  var result = OVVAR.compose.compose(s.ctx, s.design);
  s.textures.topA.updateSource(s.io.dataUrl(result.top.A));
  s.textures.topB.updateSource(s.io.dataUrl(result.top.B));
  s.textures.bottomA.updateSource(s.io.dataUrl(result.bottom.A));
  s.textures.bottomB.updateSource(s.io.dataUrl(result.bottom.B));
  if (s.panel && s.panel.inside_vue) {
    s.panel.inside_vue.warnings = s.ctx.warnings.slice();
    s.panel.inside_vue.revision++;
  }
  Canvas.updateAll();
};

/**
 * A refresh at the end of this frame. A stroke can finish several edits in a row and the model
 * only has to be right once; a recompose is a handful of milliseconds, so one frame of delay is
 * all the coalescing it needs.
 *
 * The timer beside the frame is not a second debounce: requestAnimationFrame does not run at all
 * while the window is hidden, and an edit can still finish there. Without it the first such edit
 * would leave `pending` set for ever and the ovve would stop redrawing even once the window came
 * back. Whichever fires first cancels the other.
 */
OVVAR.model.scheduleRefresh = function () {
  var s = OVVAR.state;
  if (s.pending) return;
  var run = function () {
    if (!s.pending) return;
    cancelAnimationFrame(s.pending.frame);
    clearTimeout(s.pending.timer);
    s.pending = null;
    OVVAR.model.refresh();
  };
  s.pending = {frame: requestAnimationFrame(run), timer: setTimeout(run, 100)};
};

/**
 * What a patch art texture holds now, as an image.
 *
 * Off the texture's own PNG, not its canvas. Blockbench rewrites `source` from the canvas at the
 * end of every edit, so it is always current; it survives a save and is there the instant a
 * project is parsed, while the canvas is still blank; and it is byte-exact, where reading a canvas
 * premultiplies and would lose the colour of any texel drawn at alpha 0.
 *
 * `tex.width` cannot be used to tell whether the canvas is ready, by the way: it is a declared
 * Texture property, so a reopened project restores it from the file long before the image decodes.
 */
OVVAR.model.readArt = function (tex) {
  if (tex.layers_enabled) tex.updateLayerChanges(true);
  if (typeof tex.source === 'string' && tex.source.indexOf('data:image/png') === 0) {
    return OVVAR.state.io.decode(OVVAR.state.io.fromDataUrl(tex.source));
  }
  return OVVAR.model.readTexture(tex);
};

/** The fallback: a texture whose pixels are only on its canvas. */
OVVAR.model.readTexture = function (tex) {
  if (tex.layers_enabled) tex.updateLayerChanges();
  var w = tex.width, h = tex.height;
  var data = tex.ctx.getImageData(0, 0, w, h).data;
  return {w: w, h: h, data: new Uint8Array(data)};
};

/**
 * A ctx whose `art` can be overruled by what is on a Texture right now.
 *
 * 10-compose.js memoises every art it reads off disk, which is what makes a recompose cheap --
 * but a stroke Vlad has just painted is not on disk, and a patch invented in this session has no
 * file at all. So `art` is wrapped rather than the memo picked open: `put` wins, the memo is
 * consulted only for files nothing has painted, and 10-compose.js stays a file that knows
 * nothing about Blockbench.
 */
OVVAR.model.paintable = function (ctx) {
  var painted = {};
  var read = ctx.art, forget = ctx.forget;
  ctx.painted = painted;
  ctx.art = function (file) { return painted[file] || read(file); };
  ctx.put = function (file, image) { painted[file] = image; };
  // forget() drops what was read off disk, so it must drop what was painted over it too -- and it
  // empties `warnings` in place, the way refresh() does, so nothing can end up holding the old
  // array.
  ctx.forget = function () {
    var kept = ctx.warnings;
    forget();
    ctx.warnings = kept;
    kept.length = 0;
    for (var file in painted) delete painted[file];
  };
  return ctx;
};

/** Put what Vlad has painted on a texture in front of whatever the checkout holds. */
OVVAR.model.readBack = function (file) {
  var s = OVVAR.state;
  var tex = s.artTextures[file];
  if (!s.ctx || !tex) return;
  s.ctx.put(file, OVVAR.model.readArt(tex));
};
