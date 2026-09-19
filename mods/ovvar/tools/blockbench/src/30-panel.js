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
    onClose: function () { this.delete(); },
    onConfirm: function (result) {
      var path = String(result.path || '').trim().replace(/\/+$/, '');
      if (!path) {
        Blockbench.showMessageBox({title: 'Ovvar', icon: 'error',
          message: 'Type the folder holding mods/ovvar.'});
        return false;
      }
      this.hide();
      try {
        OVVAR.model.build(path);
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
      // Only the design is handed to Vue. The rest of OVVAR.state is the manifest, the composer's
      // memoised images, six Cubes and four Textures -- nothing that wants deep reactivity, and
      // a lot for Vue to walk on every open. Anything else the template needs is read straight
      // off OVVAR.state through `revision`, which refresh() bumps.
      data: function () {
        return {design: OVVAR.state.design, revision: 0, warnings: []};
      },
      computed: {
        manifest: function () { this.revision; return OVVAR.state.ctx ? OVVAR.state.ctx.m : null; },
        checkout: function () { this.revision; return OVVAR.state.checkout; },
        chapter: function () { return this.manifest ? this.manifest.chapterById[this.design.chapter] : null; }
      },
      methods: {
        onChapter: function (id) { this.design.chapter = id; this.fix(); OVVAR.model.refresh(); },
        onNercabbad: function (v) { this.design.nercabbad = v; OVVAR.model.refresh(); },
        // A chapter that cannot roll down is always worn up.
        fix: function () { if (this.chapter && !this.chapter.rollable) this.design.nercabbad = false; },
        label: function (p) {
          var m = this.manifest;
          var patch = m.patchById[p.patch], cell = m.cellById[p.cell];
          return (patch ? patch.name : p.patch) + ' on the ' + (cell ? cell.label : p.cell);
        },
        unpick: function (i) { this.design.placements.splice(i, 1); OVVAR.model.refresh(); },
        sew: function () { OVVAR.panel.sew(); },
        newPatch: function () { OVVAR.panel.newPatch(); },
        addSize: function () { OVVAR.panel.addSize(); },
        exportToRepo: function () { OVVAR.panel.exportToRepo(); },
        openCheckout: function () { OVVAR.panel.askForCheckout(); }
      },
      template: [
        // The panel shares the left bar, so it is often shorter than its contents: scroll rather
        // than clip, or the buttons go missing on a small screen.
        '<div class="ovvar_panel" style="height: 100%; overflow-y: auto; padding: 0 4px;">',
        '  <p v-if="!manifest"><button @click="openCheckout()">Open checkout…</button></p>',
        '  <template v-else>',
        '    <p>',
        '      <select :value="design.chapter" @change="onChapter($event.target.value)">',
        '        <option v-for="c in manifest.chapters" :key="c.id" :value="c.id">{{ c.name }}</option>',
        '      </select>',
        '    </p>',
        '    <p>',
        '      <label><input type="checkbox" :disabled="!chapter.rollable" :checked="design.nercabbad"',
        '        @change="onNercabbad($event.target.checked)"> Zipped down</label>',
        '      <span v-if="!chapter.rollable" class="ovvar_hint">({{ chapter.name }} has nothing to roll down)</span>',
        '    </p>',
        '    <ul class="ovvar_placements">',
        '      <li v-for="(p, i) in design.placements" :key="i">',
        '        {{ label(p) }} <button @click="unpick(i)" title="Unpick">×</button>',
        '      </li>',
        '      <li v-if="!design.placements.length" class="ovvar_hint">Nothing sewn on yet.</li>',
        '    </ul>',
        '    <p>',
        '      <button @click="sew()">Sew…</button>',
        '      <button @click="newPatch()">New patch…</button>',
        '      <button @click="addSize()">Add size…</button>',
        '    </p>',
        '    <p><button @click="exportToRepo()">Export to repo</button></p>',
        '    <ul class="ovvar_warnings"><li v-for="w in warnings" :key="w">⚠ {{ w }}</li></ul>',
        '    <p class="ovvar_hint">{{ manifest.patches.length }} patches, {{ manifest.cells.length }} cells,',
        '      from {{ checkout }}</p>',
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
    if (!patch.seat !== !(cell.id === 'seat')) return;
    out[cell.id] = (cell.piece === 'top' ? 'Top — ' : 'Bottom — ') + cell.label;
  });
  return out;
};

OVVAR.panel.sew = function () {
  var s = OVVAR.state, m = s.ctx.m;
  var patches = {};
  m.patches.forEach(function (p) { patches[p.id] = p.name + (p.seat ? ' (seat)' : ''); });
  var chosen = m.patches[0];
  // The spot list follows the patch: a seat patch has exactly one place to go, and no ordinary
  // patch may go there. Blockbench's select input re-reads a function-valued `options` every time
  // the menu is opened, so the list is a function rather than something onFormChange rewrites --
  // a replaced options object would never reach the input that was built with the old one.
  var options = function () { return OVVAR.panel.cellOptions(m, chosen); };
  new Dialog('ovvar_sew', {
    title: 'Sew a patch on',
    form: {
      patch: {label: 'Patch', type: 'select', options: patches, value: chosen.id},
      cell: {label: 'Spot', type: 'select', options: options, value: Object.keys(options())[0]}
    },
    onFormChange: function (result) {
      if (result.patch === chosen.id) return;
      chosen = m.patchById[result.patch];
      var allowed = options();
      if (!allowed[result.cell]) this.setFormValues({cell: Object.keys(allowed)[0]}, false);
    },
    onClose: function () { this.delete(); },
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
      artist: {label: 'Artist', type: 'text', value: ''},
      seat: {label: 'Across the seat', type: 'checkbox', value: false},
      // A seat patch has no width to choose -- it is always both cells wide -- and its height
      // range is its own, so it gets its own field rather than a shared one with two meanings.
      w: {label: 'Width', type: 'number', value: m.overMax, min: 6, max: m.maxArt, step: 2,
        condition: function (form) { return !form.seat; }},
      h: {label: 'Height', type: 'number', value: m.overMax, min: 6, max: m.maxArt, step: 2,
        condition: function (form) { return !form.seat; }},
      seatHeight: {label: 'Height', type: 'number', value: m.artPx, min: m.artPx, max: m.seatHeightMax, step: 2,
        condition: function (form) { return !!form.seat; },
        description: 'A seat patch is ' + 2 * m.artPx + ' px wide, across both cells.'}
    },
    onConfirm: function (result) {
      var w = result.seat ? 2 * m.artPx : result.w;
      var h = result.seat ? result.seatHeight : result.h;
      var asked = {id: result.id, name: result.name, artist: result.artist, seat: !!result.seat, w: w, h: h};
      var problems = OVVAR.panel.checkNewPatch(m, asked);
      if (problems.length) {
        Blockbench.showMessageBox({title: 'Ovvar', icon: 'error', message: problems.join('\n')});
        return false;
      }
      this.hide();
      var file = 'patches/' + result.id + '.png';
      var entry = {
        id: asked.id, name: asked.name, seat: asked.seat, w: w, h: h,
        artist: asked.artist || null,
        arts: [{file: file, w: w, h: h, 'default': true, generated: false, source: null}],
        fits: {over: file, clipped: file, filled: file}
      };
      m.patches.push(entry);
      m.patchById[entry.id] = entry;
      m.artByFile[file] = entry.arts[0];
      s.added[entry.id] = entry;
      s.dirty[file] = true;
      var blank = OVVAR.tex.blank(w, h);
      // Nothing of this patch is on disk, so the composer is handed the blank directly rather
      // than left to look for a file and warn about it.
      s.ctx.put(file, blank);
      var tex = new Texture({name: result.id + '.png'})
        .fromDataURL(s.io.dataUrl(blank)).add(false);
      tex.uv_width = w;
      tex.uv_height = h;
      tex.ovvar_art = file;
      s.artTextures[file] = tex;
      tex.select();
      Blockbench.showQuickMessage('Draw ' + result.name + ' in Paint mode, then Sew…', 3000);
      OVVAR.model.refresh();
    },
    onClose: function () { this.delete(); }
  }).show();
};

/**
 * The rules Patches.Patch's constructor enforces, said before the art is made rather than after.
 * `asked` is the size already resolved: a seat patch is two cells wide whatever the form holds.
 */
OVVAR.panel.checkNewPatch = function (m, result) {
  var out = [];
  if (!/^[a-z0-9_]+$/.test(result.id)) out.push('An id is lower case letters, digits and underscores: "' + result.id + '" is not.');
  if (m.patchById[result.id]) out.push('There is already a patch called ' + result.id + '.');
  if (!result.name) out.push('Give it a name.');
  var h = result.h;
  // Patch's constructor rejects an odd size whatever the patch is, the seat included -- an odd
  // seat height passes a check that only looks at the range and then fails datagen.
  if (h % 2 || (!result.seat && result.w % 2)) out.push('Patch art is an even size both ways.');
  if (result.seat) {
    if (h < m.artPx || h > m.seatHeightMax) out.push('A seat patch is ' + m.artPx + '–' + m.seatHeightMax + ' px tall (and always ' + 2 * m.artPx + ' wide).');
  } else {
    if (result.w < 6 || result.w > m.maxArt || h < 6 || h > m.maxArt) out.push('Patch art is 6–' + m.maxArt + ' px each way.');
  }
  return out;
};

OVVAR.panel.addSize = function () {
  var s = OVVAR.state, m = s.ctx.m;
  var patches = {};
  m.patches.forEach(function (p) { if (!p.seat) patches[p.id] = p.name; });
  var sizes = {};
  sizes[m.artPx] = m.artPx + ' × ' + m.artPx + ' (a shoulder)';
  sizes[m.overMax] = m.overMax + ' × ' + m.overMax + ' (an ordinary cell)';
  sizes[m.maxArt] = m.maxArt + ' × ' + m.maxArt + ' (the big back cell)';
  new Dialog('ovvar_add_size', {
    title: 'Draw a patch again at another size',
    form: {
      patch: {label: 'Patch', type: 'select', options: patches, value: Object.keys(patches)[0]},
      size: {label: 'Size', type: 'select', options: sizes, value: String(m.overMax)}
    },
    onConfirm: function (result) {
      var patch = m.patchById[result.patch];
      var size = parseInt(result.size, 10);
      var file = 'patches/' + patch.id + '_' + size + 'x' + size + '.png';
      if (m.artByFile[file] && !m.artByFile[file].generated) {
        Blockbench.showMessageBox({title: 'Ovvar', icon: 'warning', message: patch.name + ' already has a drawing at ' + size + '×' + size + '.'});
        return false;
      }
      this.hide();
      // Start from the scaler's answer rather than from nothing: that is the picture the pack
      // shows today, and the point of drawing it again is to fix what the scaler got wrong.
      var from = s.ctx.art(patch.fits.filled);
      var start = (from.w >= size && from.h >= size) ? OVVAR.compose.downscaled(from, size, size) : OVVAR.tex.blank(size, size);
      // The generator had already scaled this size, so the drawing takes that entry's place --
      // pushing beside it would leave two arts claiming one file, and `refit` would pick whichever
      // sorted first.
      var art = {file: file, w: size, h: size, 'default': false, generated: false, source: null};
      OVVAR.model.putArt(m, patch, art);
      OVVAR.panel.refit(m, patch);
      s.sizes.push({patch: patch.id, file: file, w: size, h: size});
      s.dirty[file] = true;
      s.ctx.put(file, start);
      var tex = s.artTextures[file];
      if (tex) {
        tex.updateSource(s.io.dataUrl(start));
      } else {
        tex = new Texture({name: file.replace('patches/', '')}).fromDataURL(s.io.dataUrl(start)).add(false);
        tex.ovvar_art = file;
        s.artTextures[file] = tex;
      }
      tex.uv_width = size;
      tex.uv_height = size;
      tex.select();
      OVVAR.model.refresh();
    },
    onClose: function () { this.delete(); }
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
    clipped: largestIn(m.artPx, m.artPx),
    filled: largestIn(m.maxArt, m.maxArt)
  };
};

/**
 * Write every art this project drew or changed into the checkout, then show the catalogue
 * line(s) to paste into Patches.java. Never overwrites a file that is already there without
 * saying so.
 *
 * What counts as changed is `state.dirty` alone -- a new patch, a new size, a finished stroke.
 * Texture.saved cannot be asked: every texture here was made with fromDataURL, which sets
 * saved = false on all of them from birth, so it would export the whole catalogue.
 */
OVVAR.panel.exportToRepo = function () {
  var s = OVVAR.state, m = s.ctx.m;
  var files = Object.keys(s.dirty);
  if (!files.length) {
    Blockbench.showMessageBox({title: 'Ovvar', icon: 'info', message: 'Nothing has changed since the checkout was opened.'});
    return;
  }
  var dir = [s.checkout, 'mods/ovvar/src/main/resources/art/ovvar/patches'].join('/');
  var existing = files.filter(function (f) { return s.io.exists(dir + '/' + f.replace('patches/', '')); });
  var write = function () {
    s.io.mkdirp(dir);
    files.forEach(function (file) {
      var image = OVVAR.model.readArt(s.artTextures[file]);
      s.io.write(dir + '/' + file.replace('patches/', ''), s.io.encode(image));
    });
    var added = s.added;
    s.dirty = {};
    s.added = {};
    var lines = Object.keys(added).map(function (id) {
      var p = added[id];
      var by = p.artist ? '.by("' + p.artist + '")' : '';
      if (p.seat) return 'Patch.seat("' + p.id + '", "' + p.name + '", ' + p.w + ', ' + p.h + ')' + by + ',';
      // new Patch(id, name) is Spot.PX square -- a cell-sized patch; anything else says its size.
      if (p.w === m.artPx && p.h === m.artPx) return 'new Patch("' + p.id + '", "' + p.name + '")' + by + ',';
      return 'new Patch("' + p.id + '", "' + p.name + '", ' + p.w + ', ' + p.h + ')' + by + ',';
    });
    new Dialog('ovvar_exported', {
      title: 'Exported ' + files.length + ' file(s)',
      buttons: ['Close'],
      form: {
        written: {type: 'info', text: files.join('\n')},
        paste: {type: 'info', text: lines.length
          ? 'Add to the ALL list in Patches.java, last (a design’s instant code is its position):\n\n' + lines.join('\n')
          : 'No new catalogue entries — only art was redrawn.'},
        next: {type: 'info', text: 'Then, in the checkout: re-run ./gradlew :mods:ovvar:runDatagen, '
          + 'and commit the new art under mods/ovvar/src/main/resources/art/ovvar/patches/ together '
          + 'with the Patches.java line. Nothing under src/main/generated/ is ever committed.'}
      },
      onClose: function () { this.delete(); }
    }).show();
  };
  if (existing.length) {
    Blockbench.showMessageBox({
      title: 'Ovvar', icon: 'warning',
      message: 'These files are already in the checkout and will be overwritten:\n\n' + existing.join('\n'),
      buttons: ['Overwrite', 'Cancel'], confirmIndex: 0, cancelIndex: 1
    }, function (button) { if (button === 0) write(); });
  } else {
    write();
  }
};
