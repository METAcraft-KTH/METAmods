// The one namespace every file of the plugin shares. Blockbench loads the built ovvar.js as
// new Function('requireNativeModule', 'require', code), so `var` here is function-scoped to the
// plugin: nothing of ours lands on window, and onunload has nothing global to clean up.
var OVVAR = {};

OVVAR.VERSION = 2;

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
  // The other way round. A texture carries its own PNG as a data URL, which is the only copy of it
  // that is there the instant a project is parsed -- and it is byte-exact, where a canvas is not.
  io.fromDataUrl = function (url) {
    var comma = url.indexOf(',');
    if (comma < 0 || url.slice(0, comma).indexOf(';base64') < 0) throw new Error('not a base64 data URL');
    var b = Buf.from(url.slice(comma + 1), 'base64');
    return new Uint8Array(b.buffer, b.byteOffset, b.length);
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
  // Version 1 wrote one resolution: art and texture were the same size, so its art scales by 1.
  if (m.artDetail === undefined) { m.artDetail = m.detail; m.artScale = 1; m.artPx = m.px; }
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

/**
 * Patches.Art.offsetX: the art centred in the cell, which is not always one cell wide. In texture
 * pixels -- the art's own width is in art pixels and lands on the texture `artScale` times as wide.
 */
OVVAR.compose.offsetX = function (m, cell, art) {
  return Math.trunc((cell.w * m.detail - art.w * m.artScale) / 2);
};

/** Spot.seatHalf: where a seat patch's art is cut for one leg, in art pixels. */
OVVAR.compose.seatHalf = function (m, side) {
  return (side === 'left' ? 0 : 1) * m.artPx;
};

/**
 * Tex.scaledUp(Spot.ART_SCALE): art at the texture's resolution. A patch is drawn at `artDetail`
 * px per skin texel and a texture is at `detail`, so every art is scaled up by the ratio on its
 * way onto a texture -- `placed` and `placedWrapped` take art that has already been through this.
 * The identity while the two agree, which is what a version 1 manifest says.
 */
OVVAR.compose.baked = function (m, art) {
  return m.artScale === 1 ? art : OVVAR.tex.scale(art, m.artScale);
};

/**
 * GeneratedAssets.placed: the art (at texture resolution: see `baked`) on a garment texture at
 * texel column x (its top-left; the cell's row, centred vertically), clipped to the part's side rows and wrapped round the part's
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
 * What a placement draws and where: the art this cell shows, mirrored for a left limb (the model
 * mirrors it back), cut in half for the seat, and the texel column its left edge sits at. `side`
 * is 'body', 'right' or 'left' -- for the seat it is the leg being drawn, for anything else the
 * cell's own. The committed placement textures and the plugin's live composite both go through
 * this, so they cannot drift apart; all they choose is what to hand it to (`placed` or
 * `placedWrapped`).
 */
OVVAR.compose.placementArt = function (ctx, cell, patch, side) {
  var m = ctx.m;
  var entry = OVVAR.compose.artFor(m, patch, cell);
  var art = ctx.art(entry.file);
  if (cell.side === 'seat') {
    // The art is drawn as seen from behind, so its left half belongs on the wearer's LEFT leg;
    // that half is then flipped in x, because the model flips the left leg's texture back.
    var half = OVVAR.tex.crop(art, OVVAR.compose.seatHalf(m, side), 0, m.artPx, art.h);
    return {art: OVVAR.compose.baked(m, side === 'left' ? OVVAR.tex.flipX(half) : half), x: cell.u * m.detail};
  }
  return {
    art: OVVAR.compose.baked(m, cell.side === 'left' ? OVVAR.tex.flipX(art) : art),
    x: cell.u * m.detail + OVVAR.compose.offsetX(m, cell, entry)
  };
};

/** One committed placement texture, minus the marker texels: `placementArt`, laid on flat. */
OVVAR.compose.placementTexture = function (ctx, cell, patch, side) {
  var drawn = OVVAR.compose.placementArt(ctx, cell, patch, side);
  return OVVAR.compose.placed(ctx.m, cell, drawn.art, drawn.x);
};

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
  // The manifest drops a null property rather than writing it (absentMeansNull), so a chapter
  // with no tint has no `tint` key at all -- read it for truth, not against null.
  if (chapter.tint) throw new Error(chapter.name + ' is tinted; load its committed layer instead');
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

/**
 * Spot.stacked: bottom first -- layer order, and the order they came in within a layer.
 *
 * A placement naming a cell this manifest has never heard of (an older plugin against a newer
 * design, a hand-edited file) sorts as layer 0 and stays in the list, so composePiece is the one
 * place that warns about it; the sort must not be the thing that throws.
 */
OVVAR.compose.stacked = function (m, placements) {
  function layer(p) {
    var cell = m.cellById[p.cell];
    return cell ? cell.layer : 0;
  }
  return placements.slice().sort(function (a, b) { return layer(a) - layer(b); });
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
    // Nothing painted on it yet is the ordinary state of a patch just invented, and it is a
    // different thing from art that missed its cell. Said once for the whole patch, before the
    // seat's two halves: a half-empty art still has a half to draw.
    if (OVVAR.tex.isEmpty(ctx.art(OVVAR.compose.artFor(m, patch, cell).file))) {
      ctx.warnings.push(patch.name + ' is blank; paint it');
      continue;
    }
    // The seat is one patch cut in half, so it is drawn twice -- a leg at a time.
    var sides = cell.side === 'seat' ? ['right', 'left'] : [cell.side];
    for (var s = 0; s < sides.length; s++) {
      var side = sides[s];
      var drawn = OVVAR.compose.placementArt(ctx, cell, patch, side);
      var tex = cell.top
        ? OVVAR.compose.placed(m, cell, drawn.art, drawn.x)
        : OVVAR.compose.placedWrapped(m, cell, drawn.art, drawn.x);
      if (tex === null) {
        ctx.warnings.push(patch.name + ' lands entirely off ' + cell.label + '; not drawn');
        continue;
      }
      // A left limb's art belongs on B alone; a right limb's on A alone; the body box is one box
      // that both textures carry, so it goes on both.
      if (side !== 'left') A = OVVAR.tex.blit(A, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
      if (side === 'left' || cell.side === 'body') B = OVVAR.tex.blit(B, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
    }
  }
  return {A: A, B: B};
};

/** Both halves, both sides: what the four textures of the project are set to. */
OVVAR.compose.compose = function (ctx, design) {
  return {top: OVVAR.compose.composePiece(ctx, 'top', design), bottom: OVVAR.compose.composePiece(ctx, 'bottom', design)};
};
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
