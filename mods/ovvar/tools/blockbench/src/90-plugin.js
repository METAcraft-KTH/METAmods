// Registration and cleanup. Blockbench runs a plugin file as
// new Function('requireNativeModule', 'require', code), so `require` here is the scoped one --
// 'path', 'zlib' and 'buffer' come free, and 'fs' asks once per folder and then only lets us
// touch that folder. Nothing of ours reaches window, so unloading is a matter of taking back
// what we added to Blockbench.

OVVAR.require = typeof require === 'function' ? require : null;

/**
 * A finished edit on a patch art: remember it for Export, read it back into the composer, and
 * redraw the ovve at the end of the frame.
 *
 * A painted texture arrives as `aspects.textures` normally and as `aspects.layers` once layers
 * are switched on, so both are looked through.
 */
OVVAR.onPaint = function (event) {
  var s = OVVAR.state;
  if (!s.ctx) return;
  var aspects = (event && event.aspects) || {};
  var touched = (aspects.textures || []).slice();
  (aspects.layers || []).forEach(function (layer) { if (layer && layer.texture) touched.push(layer.texture); });
  var hit = false;
  touched.forEach(function (tex) {
    if (!tex || !tex.ovvar_art) return;
    s.dirty[tex.ovvar_art] = true;
    OVVAR.model.readBack(tex.ovvar_art);
    hit = true;
  });
  if (hit) OVVAR.model.scheduleRefresh();
};

/**
 * Saving and reopening. `save_project` fires inside the .bbmodel codec's compile, with the object
 * about to be written; the codec's own `parsed` fires at the end of parse, once the textures and
 * the cubes are in the project -- which is the earliest the ovve can be put back together, because
 * it is built out of them.
 */
OVVAR.onSaveProject = function (event) {
  var s = OVVAR.state;
  if (!s.ctx || !s.project || s.project !== Project) return;
  event.model.ovvar = OVVAR.model.saveState();
};

OVVAR.onParsedProject = function (event) {
  if (typeof Format === 'undefined' || !Format || Format.id !== 'ovvar') return;
  if (!event.model || !event.model.ovvar) return;
  OVVAR.model.restoreState(event.model.ovvar);
};

/** The tab holding the ovve has gone, so the next Ovvar project may have the plugin. */
OVVAR.onCloseProject = function (event) {
  var s = OVVAR.state;
  if (!s.project) return;
  if (event && event.project && event.project !== s.project) return;
  OVVAR.model.forgetProject();
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
      // `ovvar_art` has to be a declared Texture property or the codec drops it: it is the tag that
      // says which catalogue file a texture is, and without it a reopened project has pixels and no
      // idea what they are.
      OVVAR.state.properties = [new Property(Texture, 'string', 'ovvar_art')];
      OVVAR.model.registerFormat();
      OVVAR.panel.register();
      var on = [
        ['finished_edit', OVVAR.onPaint],
        ['save_project', OVVAR.onSaveProject],
        ['close_project', OVVAR.onCloseProject]
      ];
      on.forEach(function (l) { Blockbench.on(l[0], l[1]); OVVAR.state.listeners.push(l); });
      Codecs.project.on('parsed', OVVAR.onParsedProject);
      OVVAR.state.codecListeners.push(['parsed', OVVAR.onParsedProject]);
    },

    onunload: function () {
      var s = OVVAR.state;
      s.listeners.forEach(function (l) { Blockbench.removeListener(l[0], l[1]); });
      s.listeners = [];
      s.codecListeners.forEach(function (l) { Codecs.project.removeListener(l[0], l[1]); });
      s.codecListeners = [];
      s.properties.forEach(function (prop) { prop.delete(); });
      s.properties = [];
      if (s.pending) { cancelAnimationFrame(s.pending.frame); clearTimeout(s.pending.timer); s.pending = null; }
      if (s.panel) { s.panel.delete(); s.panel = null; }
      if (s.format) { s.format.delete(); s.format = null; }
      s.ctx = null;
      s.io = null;
      s.checkout = null;
      s.project = null;
      s.textures = {};
      s.artTextures = {};
      s.added = {};
      s.sizes = [];
      s.dirty = {};
      s.cubes = [];
      s.group = null;
    }
  });
}
