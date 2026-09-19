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
