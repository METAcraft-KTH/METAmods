#version 120

uniform sampler2D texture;

varying vec2 texcoord;
vec2 ovvar_shadow_texcoord; // Ovvar: stands in for texcoord from here on
vec2 ovvar_raw_texcoord() { return texcoord; }
#define texcoord ovvar_shadow_texcoord

varying vec4 glcolor;
vec4 ovvar_shadow_glcolor; // Ovvar: stands in for glcolor from here on
vec4 ovvar_raw_glcolor() { return glcolor; }
#define glcolor ovvar_shadow_glcolor


// --- Ovvar (metacraft.se): patches on student overalls; see README.txt in ovvar-client.jar
varying vec4 ovvar_color;
varying vec3 ovvar_pos;
varying vec3 ovvar_normal;
#define OVVAR_SAMPLE(uv) texture2D(texture, uv)
// Ovvar: patches on student overalls, finished by the shader. Shared by the vanilla entity.fsh
// override and (copied in by the shaderpack patcher) by shaderpacks' entity programs, so it is
// written against GLSL 120: no integer bit operations, no texelFetch — texels are read at their
// centres with OVVAR_SAMPLE, and bit fields are pulled out with floating-point arithmetic.
//
// A texture of ours is the 64×32 armour layout at OVVAR_D texels per texel (256×128), with a
// marker texel at (W−1, H/2−1): magenta, alpha 2/255. The texel left of it says what it is (R):
//   0  a base garment texture. The model draws the left arm and leg as mirror images off the right
//	  limb's strips; on those fragments the shader samples one strip up, where the left-side art
//	  lives.
//   1  a placement texture: one patch drawn on one cell, for one side (G: 0 body, 1 right limb,
//	  2 left limb) on one face of its strip (B: 0..3). Fragments of the other limb read the
//	  blank texel.
//   2  the preview texture: every instant design's art in a library (head rows), a cell table at
//	  x 40·D (index in the half → u, v, side, in texels; column-major, 16 tall;
//	  OVVAR_TABLE_COLUMNS columns further right: the cell's own width and height, since a cell is
//	  not one size — the big back cell is two cells each way and the seat two wide; its B is
//	  Spot.layer, which of two overlapping cells is drawn on top) and a design table
//	  OVVAR_TABLE_COLUMNS further right again (design + fit·OVVAR_FIT_SLOTS → library x, y, cells;
//	  OVVAR_TABLE_COLUMNS further right: art width, height — a row per art the design can be
//	  drawn as, since a patch may ship its art at several sizes); G = cells in the half,
//	  B = designs. The garment's dye
//	  colour carries up to three placements as the rank of their set among all sets of
//	  (cell × designs + design) states, after all smaller sets (0 = none) — packed as three
//	  base-255 digits, each byte one more than its digit, so no byte is ever 0. Everything else
//	  reads the blank texel at (W−1, H/2−2).
//
// Requires before inclusion: OVVAR_SAMPLE(uv) — the albedo sample of this program;
// ovvar_color — the raw (unlit) vertex colour, vec4; ovvar_pos and ovvar_normal — the vertex
// position and normal (any one space for both).

// Texels per skin texel (Spot.DETAIL in the mod) and the texture size, cell size and fixed texels that follow.
const float OVVAR_D = 4.0;
// Art is drawn at Spot.ART_DETAIL and kept that way in the library -- storing it at OVVAR_D would
// spend four times the head rows on the same picture, which is the whole reason the library has room
// at all. So the art's own size (the design table's width and height) is in art pixels while the
// cell, the fragment and everything else here is in texture pixels, and this is the ratio between
// them: Spot.ART_SCALE. Scale art sizes up by it; divide by it to index the library.
const float OVVAR_ART_SCALE = 2.0;
const vec2 OVVAR_TEX = vec2(64.0, 32.0) * OVVAR_D;
const float OVVAR_CELL = 4.0 * OVVAR_D;   // the default cell; a cell's own size comes from the cell table
// The first of the box SIDE rows (Spot.FACE_ROW), where every cell is: above it are the boxes' top
// and bottom faces, which carry no cells and where nothing is squeezed round the box.
const float OVVAR_SIDE_ROW = 20.0;
// The preview texture's four tables (GeneratedAssets: CELL_TABLE_X and the three beside it), each
// OVVAR_TABLE_COLUMNS texel columns wide and 16 rows tall, read at (base + floor(i/16), mod(i,16)):
// the cell table, the cells' own sizes, the design table, the arts' sizes. GeneratedAssets'
// TABLE_COLUMNS: two fits of OVVAR_FIT_SLOTS rows, two columns each.
const float OVVAR_TABLE_X = 40.0 * OVVAR_D;
const float OVVAR_TABLE_COLUMNS = 4.0;
// A design's row in the two design tables is design + fit * OVVAR_FIT_SLOTS: a patch may ship its
// art at several sizes and which of them a cell shows is the cell's to decide (Patches.Fit), so the
// tables hold a row per (design, fit) and this is the fit of the cell being drawn. The numbers are
// Patches.Fit's own ordinals, which a game test holds these to:
//   OVER (0)     the artist's own size, hanging over the cell if it is bigger - every ordinary cell,
//   FILLED (1)   a cell as big as art may get (the big back cell), meant to be filled.
const float OVVAR_FIT_SLOTS = 32.0;
const float OVVAR_FIT_OVER = 0.0;
const float OVVAR_FIT_FILLED = 1.0;
const vec2 OVVAR_MARKER = vec2(OVVAR_TEX.x - 1.0, OVVAR_TEX.y * 0.5 - 1.0);
const vec2 OVVAR_BLANK = (OVVAR_MARKER + vec2(0.5, -0.5)) / OVVAR_TEX;

vec4 ovvar_read(float x, float y) {
	return floor(OVVAR_SAMPLE((vec2(x, y) + 0.5) / OVVAR_TEX) * 255.0 + 0.5);
}

// Which sign of texture-over-geometry handedness the mirrored limbs have. Fixed by how the game
// builds its vertex data (the same on every platform); calibrated once against a known garment.
// It holds for a box's top and bottom faces as well as its four sides: on the top face v runs
// across the box, from its back edge to its front, instead of down it, but u runs the wearer's
// right to left the same way the front face's does (the box's top-front edge is one line in the
// texture, and the two faces run along it the same way), so u ^ v against the outward normal has
// the same sign there as on a side face.
const bool OVVAR_MIRROR_SENSE = true;

// Which half of a seat patch's art the unmirrored (the wearer's right) leg wears: Spot.seatColumn's
// RIGHT value, which a game test holds this to. Seat art is drawn as seen from behind, where the
// wearer's right leg is on the viewer's right, so the right leg takes the art's RIGHT half (column
// 1) and the mirrored left leg the other one.
const float OVVAR_SEAT_COLUMN_RIGHT = 1.0;

bool ovvar_marked() {
	return all(equal(ovvar_read(OVVAR_MARKER.x, OVVAR_MARKER.y), vec4(255.0, 0.0, 255.0, 2.0)));
}

vec4 ovvar_kind() {
	return ovvar_read(OVVAR_MARKER.x - 1.0, OVVAR_MARKER.y);
}

// 1.0 on a patch texture of ours (placement or preview), else 0.0.
float ovvar_patch_layer() {
	return ovvar_marked() && ovvar_kind().r > 0.5 ? 1.0 : 0.0;
}

// The patch bits carried by the raw vertex colour.
float ovvar_bits() {
	vec3 c = floor(ovvar_color.rgb * 255.0 + 0.5) - 1.0;
	return c.r * 65025.0 + c.g * 255.0 + c.b;
}

// Binomials, exact in float up to the channel's range (c ≤ 448: C(c,3) < 2^24). c(c-1)(c-2)
// would not be, so C(c,3) is C(c,2)·(c-2)/3 split into a multiple of 3 and a remainder.
float ovvar_c2(float c) {
	return c < 2.0 ? 0.0 : c * (c - 1.0) * 0.5;
}

float ovvar_c3(float c) {
	if (c < 3.0) return 0.0;
	float a = ovvar_c2(c), q = floor(a / 3.0), rem = a - 3.0 * q;
	return q * (c - 2.0) + rem * (c - 2.0) / 3.0;
}

// The largest c with C(c,2) ≤ r, resp. C(c,3) ≤ r: an estimate, then a few steps to be exact.
float ovvar_max_c2(float r) {
	float c = floor(sqrt(2.0 * r)) + 1.0;
	for (int i = 0; i < 4; i++) if (ovvar_c2(c) > r) c -= 1.0;
	for (int i = 0; i < 4; i++) if (ovvar_c2(c + 1.0) <= r) c += 1.0;
	return c;
}

float ovvar_max_c3(float r) {
	float c = floor(pow(max(6.0 * r, 1.0), 1.0 / 3.0)) + 2.0;
	for (int i = 0; i < 6; i++) if (ovvar_c3(c) > r) c -= 1.0;
	for (int i = 0; i < 6; i++) if (ovvar_c3(c + 1.0) <= r) c += 1.0;
	return c;
}

// The armour model draws a texel wider than it is tall: the box is inflated (1 for the chest
// layer, 0.5 for the leggings layer) but its texture is not, so a face n texels wide covers
// n + 2·inflate units and 12 rows cover 12 + 2·inflate. Everything of ours on the box sides is
// drawn at a square pixel instead (ovvar_pixel), which leaves slack — 2·inflate units per face:
//   the garment centres each face's texels on the face, so the cells sit on the fabric's grid,
//   and the slack is a margin at each corner where it stretches its edge column;
//   a patch (a placement texture, or one the preview names) is drawn continuous round the box
//   from the face of its cell — that face's texels centred, the neighbours' continuing past its
//   edges at the same pixel, so a big patch hanging over a corner bends round it unbroken — and
//   all its slack lands in the middle of the opposite face, which the patch never reaches.
// Which layer a texture is for is in the layer texel, two left of the marker: R = 2·inflate.
float ovvar_inflate() {
	return ovvar_read(OVVAR_MARKER.x - 2.0, OVVAR_MARKER.y).r * 0.5;
}

// The pixel every layer on a part is drawn at, in model units: the legs take the leggings
// layer's (inflate 0.5) so what the boots pass draws on them (inflate 1) lands on the same grid;
// the body and arms take their own layer's.
float ovvar_pixel(float skinX, float inflate) {
	return skinX < 16.0 ? 13.0 / 12.0 : (12.0 + 2.0 * inflate) / 12.0;
}

// The strip a skin texel is on: its first texel and whether it is the body's (right 4 | front 8 |
// left 4 | back 8; a limb's is outer 4 | front 4 | inner 4 | back 4).
float ovvar_strip(float skinX, out bool body) {
	body = skinX >= 16.0 && skinX < 40.0;
	return skinX < 16.0 ? 0.0 : body ? 16.0 : 40.0;
}

// Face i (0..3) of a strip: its first texel, texel count and first unit round the box (e = 2·inflate).
void ovvar_face(bool body, float i, float e, out float s, out float n, out float U) {
	float n1 = body ? 8.0 : 4.0;
	if (i < 0.5) { s = 0.0; n = 4.0; U = 0.0; }
	else if (i < 1.5) { s = 4.0; n = n1; U = 4.0 + e; }
	else if (i < 2.5) { s = 4.0 + n1; n = 4.0; U = 4.0 + n1 + 2.0 * e; }
	else { s = 8.0 + n1; n = n1; U = 8.0 + n1 + 3.0 * e; }
}

// The face a strip-local texel x is on.
float ovvar_face_of(bool body, float local) {
	float n1 = body ? 8.0 : 4.0;
	return local < 4.0 ? 0.0 : local < 4.0 + n1 ? 1.0 : local < 8.0 + n1 ? 2.0 : 3.0;
}

// Per-face centring (the garment): which strip-local texel column a side-row
// fragment at skin texel skinX shows. Past the face's own texels (faceStart..faceEnd) is its
// margin, where the column returned continues past the edge — the neighbouring face's edge
// column, what lies round the corner — and is not yet wrapped into the strip (total wide).
float ovvar_centred(float skinX, float inflate, out float stripStart, out float total, out float faceStart, out float faceEnd) {
	float p = ovvar_pixel(skinX, inflate), e = 2.0 * inflate;
	bool body;
	stripStart = ovvar_strip(skinX, body);
	float local = skinX - stripStart;
	total = body ? 24.0 : 16.0;
	float fs, n, U;
	ovvar_face(body, ovvar_face_of(body, local), e, fs, n, U);
	faceStart = fs;
	faceEnd = fs + n;
	float c = fs + n * 0.5;
	float units = (local - c) * ((n + e) / n);   // from the face centre, in model units
	return c + units / p;
}

// Continuous from an anchor face (a placement): which strip-local texel column a side-row
// fragment at skin texel skinX shows, or -1.0 in the slack opposite the anchor.
float ovvar_anchored(float skinX, float inflate, float anchor, out float stripStart) {
	float p = ovvar_pixel(skinX, inflate), e = 2.0 * inflate;
	bool body;
	stripStart = ovvar_strip(skinX, body);
	float local = skinX - stripStart;
	float total = body ? 24.0 : 16.0, P = total + 4.0 * e;   // texels round, units round
	float sk, nk, Uk;
	ovvar_face(body, ovvar_face_of(body, local), e, sk, nk, Uk);
	float u = Uk + (local - sk) * ((nk + e) / nk);   // where on the perimeter, in units
	float sa, na, Ua;
	ovvar_face(body, anchor, e, sa, na, Ua);
	float C = Ua + (na + e) * 0.5, T = sa + na * 0.5;   // the anchor's centre, in units and texels
	float du = u - C;
	if (du >= P * 0.5) du -= P; else if (du < -P * 0.5) du += P;
	float dt = du / p;
	if (abs(dt) > total * 0.5) return -1.0;
	return mod(T + dt, total);
}

// A fragment's texel x on the side rows → the texel x to draw there, per-face centred and
// wrapped round the strip; edge: the nearest texel of the face's own (the fragment's own,
// unless it is in the margin); margin: is it?
float ovvar_squeezed(float tx, float inflate, out float edge, out bool margin) {
	float stripStart, total, faceStart, faceEnd;
	float t = ovvar_centred(tx / OVVAR_D, inflate, stripStart, total, faceStart, faceEnd);
	margin = t < faceStart || t >= faceEnd;
	edge = (stripStart + clamp(t, faceStart + 0.5 / OVVAR_D, faceEnd - 0.5 / OVVAR_D)) * OVVAR_D;
	return (stripStart + mod(t, total)) * OVVAR_D;
}

// The same, continuous from the anchor face; -1.0 in the slack.
float ovvar_squeezed_anchored(float tx, float inflate, float anchor) {
	float stripStart;
	float t = ovvar_anchored(tx / OVVAR_D, inflate, anchor, stripStart);
	return t < 0.0 ? -1.0 : (stripStart + t) * OVVAR_D;
}

// The same in y, about the side rows' centre: only the boots pass on the legs needs it (its rows
// are 14/12 tall, the leggings' 13/12).
float ovvar_squeezed_y(float tx, float ty, float inflate) {
	float sy = ovvar_pixel(tx / OVVAR_D, inflate) / ((12.0 + 2.0 * inflate) / 12.0);
	float c = 26.0 * OVVAR_D;
	return (ty - c) / sy + c;
}

// The texture coordinate to sample instead of uv. Call with the program's original coordinate;
// derivatives must be taken in uniform control flow, hence at the top.
// Set by ovvar_uv: is this fragment on a mirrored (left) limb face?
bool ovvar_handed = false;

vec2 ovvar_uv(vec2 uv) {
	// The model draws the left limbs as mirror images: their texture runs the other way round
	// the face. Compare the handedness of the texture over the screen with the handedness of the
	// geometry over the screen; the screen cancels out, leaving texture-over-geometry, which no
	// framebuffer orientation or facing convention can change.
	vec2 du = dFdx(uv), dv = dFdy(uv);
	float det = du.x * dv.y - dv.x * du.y;
	float geo = dot(cross(dFdx(ovvar_pos), dFdy(ovvar_pos)), ovvar_normal);
	ovvar_handed = ((det > 0.0) == (geo > 0.0)) == OVVAR_MIRROR_SENSE;
	if (!ovvar_marked()) return uv;

	vec4 kind = ovvar_kind();
	vec2 t = uv * OVVAR_TEX;   // texel coordinates
	bool limb = t.y >= 16.0 * OVVAR_D && (t.x < 16.0 * OVVAR_D || (t.x >= 40.0 * OVVAR_D && t.x < 56.0 * OVVAR_D));
	bool sides = t.y >= OVVAR_SIDE_ROW * OVVAR_D;   // the box sides, not the top and bottom faces
	// Is this fragment on a limb the model draws mirrored? The handedness of the texture over the
	// geometry says so (see OVVAR_MIRROR_SENSE). Every path below — the base garment's mirror strip,
	// a placement's "hide it on the other limb", and the preview's cell sides — reads this one bool,
	// so the arms are told apart exactly once.
	bool mirrored = limb && ovvar_handed;
	float inflate = ovvar_inflate();
	float ay = sides ? ovvar_squeezed_y(t.x, t.y, inflate) : t.y;
	// On the side rows a patch is drawn on, after the squeeze in y: every cell is there, so a top or
	// bottom face of a box — and a squeezed row past the ends of the side rows — shows no patch.
	bool inFace = sides && ay >= OVVAR_SIDE_ROW * OVVAR_D && ay < 32.0 * OVVAR_D;

	if (kind.r > 0.5 && kind.r < 1.5) {
		// Placement: hide it on the limb it is not for; continuous round the box from its face
		// (the kind texel's B), nothing in the slack.
		if (limb && ((kind.g > 0.5 && kind.g < 1.5 && mirrored) || (kind.g > 1.5 && !mirrored))) return OVVAR_BLANK;
		if (!inFace) return OVVAR_BLANK;
		float a = ovvar_squeezed_anchored(t.x, inflate, kind.b);
		if (a < 0.0) return OVVAR_BLANK;
		return vec2(a, ay) / OVVAR_TEX;
	}

	if (kind.r < 0.5) {
		// Base garment: per-face centred, the margin filled by the face's edge column (and row);
		// the mirrored limb reads the strip above.
		float aEdge = t.x;
		bool margin = false;
		if (sides) {
			ovvar_squeezed(t.x, inflate, aEdge, margin);
			ay = clamp(ay, OVVAR_SIDE_ROW * OVVAR_D + 0.5, 32.0 * OVVAR_D - 0.5);
		}
		return vec2(aEdge, ay) / OVVAR_TEX - vec2(0.0, mirrored ? 0.5 : 0.0);
	}
	if (!inFace) return OVVAR_BLANK;

	// Preview: unrank the instant set from the dye colour (see Looks.rank).
	float designs = kind.b, m = kind.g * designs;
	float v = ovvar_bits();
	if (v < 0.5) return OVVAR_BLANK;
	float s[3];
	int count;
	float base3 = 1.0 + m + ovvar_c2(m), base2 = 1.0 + m;
	float r;
	if (v >= base3) {
		count = 3; r = v - base3;
		s[2] = ovvar_max_c3(r); r -= ovvar_c3(s[2]);
		s[1] = ovvar_max_c2(r); r -= ovvar_c2(s[1]);
		s[0] = r;
	} else if (v >= base2) {
		count = 2; r = v - base2;
		s[1] = ovvar_max_c2(r); r -= ovvar_c2(s[1]);
		s[0] = r;
	} else {
		count = 1; s[0] = v - 1.0;
	}
	bool body;
	float stripStart = ovvar_strip(t.x / OVVAR_D, body), stripWidth = (body ? 24.0 : 16.0) * OVVAR_D;
	// Cells overlap -- the big back cell and the two back-top cells are meant to be worn all three
	// at once -- and the set the dye colour carries is a ranked SET, with no order of its own. So a
	// fragment may fall inside more than one of the design's cells, and the one drawn is the one
	// highest in the stack: Spot.layer, the cell table's own B, which is a patch smaller than the
	// cell it lies inside. The pack path stacks its layers in the same order, so the two agree.
	vec2 drawn = OVVAR_BLANK;
	float drawnLayer = -1.0;
	for (int i = 0; i < 3; i++) {
		if (i >= count) break;
		float cell = floor(s[i] / designs), design = s[i] - cell * designs;
		vec4 ce = ovvar_read(OVVAR_TABLE_X + floor(cell / 16.0), mod(cell, 16.0));					  // u, v, side (texels)
		vec4 cz = ovvar_read(OVVAR_TABLE_X + OVVAR_TABLE_COLUMNS + floor(cell / 16.0), mod(cell, 16.0));   // the cell's own width, height
		float side = ce.b;
		float column = 0.0;   // which cell of the art
		bool flip = false;
		if (side < 0.5) { if (limb) continue; }
		else if (side < 1.5) { if (!limb || mirrored) continue; }
		else if (side < 2.5) { if (!limb || !mirrored) continue; flip = true; }
		// Seat: one half per leg. The unmirrored (right) leg takes OVVAR_SEAT_COLUMN_RIGHT, the
		// mirrored (left) leg the other half, flipped back the way the art was drawn.
		else { if (!limb) continue; column = mirrored ? 1.0 - OVVAR_SEAT_COLUMN_RIGHT : OVVAR_SEAT_COLUMN_RIGHT; flip = mirrored; }
		// The art, centred on its cell (a seat patch: one cell per leg), looked up on the side
		// rows through the mapping a placement of its face gets — continuous round the box, so
		// a big patch bends round the corners here just as it will once the pack has it.
		// The cell's own size: OVVAR_CELL for nearly all of them, twice that each way for the big
		// back cell, and two cells wide for the seat — half of which is one leg's. The art's height
		// is the art's own either way, so a seat patch taller than the seat's row hangs over it just
		// as a big patch hangs over a plain cell.
		float cw = cz.r, ch = cz.g;
		// Which of the design's arts this cell shows (Patches.Fit, above): a cell bigger than a cell --
		// the big back one, never the seat, whose two cells wear one patch drawn to their own size --
		// is filled; anything else takes the artist's own size and lets it hang over.
		float fit = (side < 2.5 && (cw > OVVAR_CELL || ch > OVVAR_CELL)) ? OVVAR_FIT_FILLED : OVVAR_FIT_OVER;
		float slot = design + fit * OVVAR_FIT_SLOTS;
		vec4 pe = ovvar_read(OVVAR_TABLE_X + 2.0 * OVVAR_TABLE_COLUMNS + floor(slot / 16.0), mod(slot, 16.0));   // library x, y, cells
		vec4 sz = ovvar_read(OVVAR_TABLE_X + 3.0 * OVVAR_TABLE_COLUMNS + floor(slot / 16.0), mod(slot, 16.0));   // art width, height
		// In texture pixels, like the cell and the fragment: a seat half is half the cell whatever the
		// art is, and any other art is its own size scaled up from the art pixels the table holds.
		float w = side > 2.5 ? cw * 0.5 : sz.r * OVVAR_ART_SCALE, h = sz.g * OVVAR_ART_SCALE;
		vec2 origin = ce.rg + vec2(side > 2.5 ? 0.0 : (cw - w) * 0.5, (ch - h) * 0.5);
		float a = ovvar_squeezed_anchored(t.x, inflate, ovvar_face_of(body, ce.r / OVVAR_D - stripStart));
		if (a < 0.0) continue;
		vec2 local = vec2(a, ay) - origin;
		if (local.x < 0.0) local.x += stripWidth; else if (local.x >= stripWidth) local.x -= stripWidth;   // art wrapped round the strip's end
		if (local.x < 0.0 || local.x >= w || local.y < 0.0 || local.y >= h) continue;
		if (flip) local.x = w - local.x;   // the model mirrors the left limb; mirror back
		if (cz.b <= drawnLayer) continue;   // a cell higher in the stack has this fragment already
		// The dye colour is ONE layer, so a fragment can show one texel however many cells it is
		// inside: the top cell's, unless that cell's art has painted nothing there, in which case the
		// cell under it shows through — which is what the pack path's layers do of their own accord.
		// Back into art pixels to index the library, which holds the art at the size it was drawn:
		// pe.rg is the block's corner in texture pixels, and the offset into it is the fragment's
		// position within the art divided by the ratio. Flooring is what samples one art pixel rather
		// than blending between two, so the art stays pixel art however far it is scaled up.
		vec2 at = pe.rg + floor((vec2(column * w, 0.0) + local) / OVVAR_ART_SCALE);
		if (ovvar_read(floor(at.x), floor(at.y)).a < 0.5) continue;
		drawnLayer = cz.b;
		// The texel's centre, not its corner: `at` is a whole art pixel now that the library is
		// indexed by one, and a coordinate exactly on the boundary is the neighbour's to round to.
		drawn = (at + 0.5) / OVVAR_TEX;
	}
	return drawnLayer >= 0.0 ? drawn : OVVAR_BLANK;
}

// The vertex colour to shade with: on a patch texture of ours the dye colour is data, not paint,
// so it is divided out of the lit colour (no byte of it is 0). uv: the coordinate being sampled.
vec4 ovvar_shade(vec4 lit, vec2 uv) {
	if (ovvar_patch_layer() < 0.5) return lit;
	return vec4(lit.rgb / max(ovvar_color.rgb, vec3(1.0 / 255.0)), lit.a);
}
// --- end Ovvar

// --- Metacraft rifts (metacraft.se): the void anchor's and nether rifts, drawn as in metacraft-shaders' item shader
varying vec2 metacraft_rift_corner;
varying vec2 metacraft_rift_tex;
varying float metacraft_rift_flag;
varying float metacraft_rift_stage;
varying vec3 metacraft_rift_world;
varying vec3 metacraft_rift_view;
uniform float metacraftRiftTime;
uniform float metacraftRiftHeight;
uniform vec3 metacraftRiftCameraInt;
float metacraft_rift_det(mat2 m) { return m[0][0] * m[1][1] - m[1][0] * m[0][1]; }
mat2 metacraft_rift_inverse(mat2 m) { return mat2(m[1][1], -m[0][1], -m[1][0], m[0][0]) / metacraft_rift_det(m); }
#define texCoord0 metacraft_rift_tex
#define riftUv metacraft_rift_corner
#define GameTime (metacraftRiftTime / 1200.0)
#define ScreenSize vec2(1.0, metacraftRiftHeight)
#define riftWorld metacraft_rift_world
#define riftView metacraft_rift_view
#define CameraBlockPos ivec3(metacraftRiftCameraInt)
#define Sampler0 texture
#define determinant metacraft_rift_det
#define inverse metacraft_rift_inverse
// Shared by every rift the item shader draws (metacraft-shaders' item.fsh): noise, and where on its sprite a fragment is.
// Needs texCoord0 and riftUv declared before it is included.
float riftHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float riftNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(riftHash(i), riftHash(i + vec2(1.0, 0.0)), u.x),
               mix(riftHash(i + vec2(0.0, 1.0)), riftHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float riftFbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * riftNoise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

mat2 riftRot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

// The sprite's box in the atlas, lo and hi in texture coordinates. riftUv numbers the quad's corners
// from wherever its draw starts in a shared buffer, so it can come out turned by a quarter; texCoord0
// cannot. The derivatives of both give the map from one to the other, and from it the box, whichever
// corner came first. False when the quad is seen edge-on and the map can't be had.
bool riftSpriteBox(out vec2 lo, out vec2 hi) {
    mat2 dUv = mat2(dFdx(riftUv), dFdy(riftUv));
    mat2 dTex = mat2(dFdx(texCoord0), dFdy(texCoord0));
    lo = texCoord0;
    hi = texCoord0;
    if (abs(determinant(dUv)) < 1e-12) return false;
    mat2 m = dTex * inverse(dUv);
    vec2 o = texCoord0 - m * riftUv;
    vec2 c1 = o + m[0], c2 = o + m[1], c3 = o + m[0] + m[1];
    lo = min(min(o, c1), min(c2, c3));
    hi = max(max(o, c1), max(c2, c3));
    return true;
}

// Where on the sprite this fragment is, 0..1 along the sprite's own axes.
vec2 riftLocal() {
    vec2 lo, hi;
    if (!riftSpriteBox(lo, hi)) return riftUv;
    return (texCoord0 - lo) / max(hi - lo, vec2(1e-9));
}
// The void anchor (metacraft-qol): a crack in space with the End's void showing through, its glowing cracks and its core.
// Needs rift_common.glsl, Globals (GameTime, ScreenSize) and Sampler0.
// The End portal's colours, one per layer, as vanilla's rendertype_end_portal has them.
const vec3 RIFT_LAYER_COLORS[16] = vec3[](
    vec3(0.022087, 0.098399, 0.110818), vec3(0.011892, 0.095924, 0.089485), vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838), vec3(0.064901, 0.117696, 0.097189), vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380), vec3(0.097489, 0.154120, 0.091064), vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229), vec3(0.133516, 0.138278, 0.148582), vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696), vec3(0.047281, 0.315338, 0.321970), vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
);

// Sparse specks, as the end portal texture's: one in some cells of a grid.
float riftSpecks(vec2 p, float seed) {
    vec2 g = p * 12.0;
    vec2 cell = floor(g);
    float h = riftHash(cell + seed);
    vec2 at = vec2(riftHash(cell + seed + 3.1), riftHash(cell + seed + 7.7)) * 0.7 + 0.15;
    return smoothstep(0.16, 0.02, length(fract(g) - at)) * step(0.80, h);
}

// The void behind the crack, the way the End portal and gateway draw theirs: layers of specks laid
// in screen space, each turned, scaled and drifting its own way, so the void stays put on the screen
// while the crack moves over it and reads as endlessly deep.
vec3 riftVoid() {
    vec2 screen = gl_FragCoord.xy / ScreenSize.y;
    vec3 color = RIFT_LAYER_COLORS[0] * 0.6;
    for (int i = 0; i < 15; i++) {
        float layer = float(i + 1);
        vec2 p = riftRot(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0)) * (screen * (4.5 - layer / 4.0) * 0.5)
                + vec2(17.0 / layer, (2.0 + layer / 1.5) * GameTime * 1.5);
        color += riftSpecks(p, layer * 13.0) * RIFT_LAYER_COLORS[i] * 2.4;
    }
    return color;
}

// The rift: a crack in space (the sprite's alpha). Through the split, the End's void; along its
// edges and hairline branches, white-hot light flickering magenta and violet; round it, a glow.
// GameTime counts days, so * 1200 is seconds.
vec4 riftColor(vec4 sprite) {
    float t = GameTime * 1200.0;
    vec2 local = riftLocal();
    float a = sprite.a;
    float inside = smoothstep(0.90, 0.99, a);
    float edge = exp(-pow((a - 0.76) / 0.16, 2.0));
    float flicker = 0.7 + 0.6 * riftFbm(local * 14.0 + vec2(t * 1.6, -t * 1.1));
    vec3 hot = mix(vec3(1.0, 0.28, 0.95), vec3(0.5, 0.22, 1.0), smoothstep(0.42, 0.68, riftFbm(local * 5.0 - t * 0.5)));
    vec3 line = mix(hot, vec3(1.0, 0.92, 1.0), 0.35 * smoothstep(0.82, 0.92, a));
    float halo = (1.0 - inside) * smoothstep(0.0, 0.45, a) * (1.0 - edge);
    vec3 color = riftVoid() * inside + line * edge * flicker * 1.5 + hot * halo * 1.3;
    float alpha = clamp(max(inside, max(edge * flicker, halo)), 0.0, 1.0);
    return vec4(color, alpha);
}

// A glowing crack, as the shatter rift crosses its main one with: no void, the split and its edges
// white-hot, the hairlines lit, and a wide soft glow smeared along them that drifts like an aurora.
vec4 riftGlow(vec4 sprite) {
    float t = GameTime * 1200.0;
    vec2 local = riftLocal();
    float a = sprite.a;
    float core = smoothstep(0.9, 0.99, a);
    float edge = smoothstep(0.5, 0.8, a) * (1.0 - core);
    float flicker = 0.75 + 0.5 * riftFbm(local * 12.0 + vec2(t * 2.0, -t * 1.3));
    // the halo's alpha falls off fast; its root spreads the glow wider
    float glow = (1.0 - smoothstep(0.45, 0.6, a)) * sqrt(clamp(a / 0.45, 0.0, 1.0));
    float aurora = 0.45 + 0.9 * riftFbm(vec2(local.x * 3.0 - t * 0.7, local.y * 9.0 + t * 0.3));
    vec3 hot = mix(vec3(1.0, 0.3, 0.95), vec3(0.55, 0.25, 1.0), riftFbm(local * 4.0 + t * 0.4));
    vec3 color = mix(hot * 1.4, vec3(1.0, 0.92, 1.0), core);
    float alpha = clamp(max(core, max(edge * flicker, glow * aurora * 0.6)), 0.0, 1.0);
    return vec4(color, alpha);
}

// The shatter rift's core, facing the camera: drawn like its cracks, on the same pixel grid and in
// the same light, a white-hot disc with a flickering magenta-violet rim and arms of light swirling
// into it.
vec4 riftCore() {
    float t = GameTime * 1200.0;
    vec2 local = (floor(riftLocal() * 32.0) + 0.5) / 32.0;
    vec2 p = local * 2.0 - 1.0;
    float r = length(p);
    float angle = atan(p.y, p.x);
    float pulse = 1.0 + 0.08 * sin(t * 5.0);
    float hot = 1.0 - smoothstep(0.2 * pulse, 0.3 * pulse, r);
    float rim = (1.0 - smoothstep(0.38, 0.52, r)) * (1.0 - hot);
    float swirl = pow(0.5 + 0.5 * sin(angle * 3.0 + r * 12.0 - t * 6.0), 4.0) * (1.0 - smoothstep(0.4, 0.75, r)) * (1.0 - hot);
    float flicker = 0.75 + 0.5 * riftFbm(local * 12.0 + vec2(t * 2.0, -t * 1.3));
    vec3 tint = mix(vec3(1.0, 0.3, 0.95), vec3(0.55, 0.25, 1.0), riftFbm(local * 4.0 + t * 0.4));
    vec3 color = mix(tint * 1.4, vec3(1.0, 0.92, 1.0), hot);
    float alpha = clamp(max(hot, max(rim * flicker, swirl * 0.7)), 0.0, 1.0);
    return vec4(color, alpha);
}
// Nether rifts (nether-rifts): glass cracking over a vanilla nether portal, drawn as pixel art on a grid fixed
// to the world, 16 pixels a block like block textures, so the panels a rift is built from join without a seam
// and nothing swims as the camera moves. Clients see the rift's blocks as a nether portal and draw its swirl
// themselves; each panel is two faces just clear of the portal's slab, one either side. The face on the
// camera's side is the glass's surface: clear but for its cracks, so the portal shows. It cracks like struck
// glass: from an impact or two, fixed to the world, spokes run out and rings join them like a spider web.
// Stage 1 to 3 the web spreads (hairlines, then spokes and rings, then all the way to the frame); at 2 the
// innermost shards fall out and at 3 most of the middle, and through the holes the nether shows: the panel's
// sprite is a 360-degree nether panorama, looked up by view direction like a skybox, so it reads as a far
// place, seen through a haze of the portal's swirl. The face beyond, seen through the portal, is the glass's
// body: it reddens the portal from stage 2, each shard a shade of its own. The edge is a few sparks along the
// outline. Needs rift_common.glsl, Globals, Sampler0, riftWorld (world position less CameraBlockPos) and
// riftView (relative to the camera).

// Exact integer hashing, so the grid is the same anywhere in the world.
uint netherRiftMix(uint h) {
    h ^= h >> 16;
    h *= 0x7feb352du;
    h ^= h >> 15;
    h *= 0x846ca68bu;
    h ^= h >> 16;
    return h;
}

uint netherRiftHashU(ivec3 p) {
    uvec3 u = uvec3(p);
    return netherRiftMix(u.x ^ netherRiftMix(u.y ^ netherRiftMix(u.z + 0x9e3779b9u)));
}

float netherRiftHash(ivec3 p) {
    return float(netherRiftHashU(p) >> 8) * (1.0 / 16777216.0);
}

// 0..1 from a key already hashed once, mixed with a salt.
float netherRiftUnit(uint key, uint salt) {
    return float(netherRiftMix(key ^ salt) >> 8) * (1.0 / 16777216.0);
}

// Value noise on a lattice of 2^shift pixels; off moves it, in lattice cells.
float netherRiftNoise(ivec2 px, int shift, vec2 off, int seed) {
    int size = 1 << shift;
    vec2 local = (vec2(px & ivec2(size - 1)) + 0.5) / float(size) + off;
    vec2 fl = floor(local);
    ivec2 i = (px >> shift) + ivec2(fl);
    vec2 f = local - fl;
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = netherRiftHash(ivec3(i, seed)), b = netherRiftHash(ivec3(i + ivec2(1, 0), seed));
    float c = netherRiftHash(ivec3(i + ivec2(0, 1), seed)), d = netherRiftHash(ivec3(i + ivec2(1, 1), seed));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// This fragment's cell on the world's pixel grid, in the panel's plane: x along the panel (world x or z,
// whichever it spans; the plane's normal comes from the derivatives), y up.
ivec2 netherRiftPixel() {
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    vec2 inPlane = vec2(alongX ? riftWorld.x : riftWorld.z, riftWorld.y);
    ivec2 base = ivec2(alongX ? CameraBlockPos.x : CameraBlockPos.z, CameraBlockPos.y);
    return ivec2(floor(inPlane * 16.0)) + base * 16;
}

// The nether portal's purples, dark to pale, and the red they turn to (the cracks and their highlights).
const vec3 NETHER_RIFT_PURPLE[6] = vec3[](
    vec3(20.0, 6.0, 34.0) / 255.0, vec3(48.0, 14.0, 78.0) / 255.0, vec3(92.0, 28.0, 140.0) / 255.0,
    vec3(140.0, 54.0, 196.0) / 255.0, vec3(196.0, 110.0, 236.0) / 255.0, vec3(244.0, 200.0, 255.0) / 255.0
);
const vec3 NETHER_RIFT_RED[6] = vec3[](
    vec3(30.0, 6.0, 14.0) / 255.0, vec3(80.0, 12.0, 30.0) / 255.0, vec3(140.0, 22.0, 44.0) / 255.0,
    vec3(196.0, 46.0, 60.0) / 255.0, vec3(236.0, 110.0, 90.0) / 255.0, vec3(255.0, 210.0, 170.0) / 255.0
);

// The portal's haze over the holes, 0 dark to 1 bright: soft wavy bands, two sets across each other, in a
// plane warped by slow waves so they drift and turn as the portal's own swirl does, on the world's pixel grid.
// Every wave fits a whole number of times in 4 blocks, so it repeats without a seam (and floats stay small), and
// in 8 s, which divides the 1200 s where GameTime wraps. Made here: the portal's sprite isn't in this atlas.
float netherRiftSwirl(ivec2 px, float t) {
    vec2 p = (vec2(px & 63) + 0.5) * (6.2831853 / 64.0);
    float w = 6.2831853 * t / 8.0;
    vec2 q = p + 0.9 * sin(p.yx + vec2(w, -w)) + 0.35 * sin(vec2(2.0 * p.y - p.x - 2.0 * w, 2.0 * p.x + p.y + w));
    float v = 0.5 + 0.325 * sin(3.0 * q.x + 2.0 * q.y + w) + 0.175 * sin(2.0 * q.x - 3.0 * q.y - 2.0 * w);
    return clamp(v + 0.06 * (netherRiftHash(ivec3(px, 29)) - 0.5), 0.0, 1.0);
}

// Impacts. Every panel has one near its middle, which holds still while the panel pulses (it scales about its
// middle), so even the smallest rift is struck where it can be seen. Big rifts get more from the world: one
// candidate per cell of 5 by 5 blocks on the plane, every other row staggered half a cell, jittered within the
// middle of its cell; none crowding the panel's own. Panels of one rift share those; where two panels' own
// impacts disagree, a crack runs along the seam between them.
const int NETHER_RIFT_IMPACT_CELL = 80;

int netherRiftFloorDiv(int a, int b) {
    return a >= 0 ? a / b : -((-a - 1) / b) - 1;
}

// A world candidate's impact, in pixels from o (geometry is done near o, so floats keep their precision
// however far out the rift is).
vec2 netherRiftImpact(ivec2 cell, int plane, ivec2 o) {
    vec2 j = vec2(netherRiftHash(ivec3(cell, plane * 4 + 61)), netherRiftHash(ivec3(cell, plane * 4 + 62)));
    ivec2 corner = cell * NETHER_RIFT_IMPACT_CELL + ivec2((cell.y & 1) * (NETHER_RIFT_IMPACT_CELL / 2), 0) - o;
    return vec2(corner) + (0.2 + 0.6 * j) * float(NETHER_RIFT_IMPACT_CELL);
}

// What is known of the strike before looking at any pixel: which plane the panel lies in (its block along the
// normal, and which way it faces: rifts in other planes crack differently), the panel's middle o in world
// pixels (exact: panels are whole blocks, so it falls on a multiple of 8) and half its size, where this
// fragment is from o, and the panel's own impact (from o). Got from how the world and riftUv (0..1 over the
// panel's quad) move together across the screen; seen edge-on, the panel has no impact of its own.
struct NetherRiftStrike {
    int plane;
    ivec2 o;
    vec2 span;
    vec2 at;
    bool hasMine;
    vec2 mine;
    uint mineKey;
};

NetherRiftStrike netherRiftStrike(ivec2 px) {
    NetherRiftStrike k;
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    int depth = alongX ? int(floor(riftWorld.z)) + CameraBlockPos.z : int(floor(riftWorld.x)) + CameraBlockPos.x;
    k.plane = depth * 2 + (alongX ? 0 : 1);
    vec2 at = vec2(alongX ? riftWorld.x : riftWorld.z, riftWorld.y) * 16.0;
    ivec2 base = ivec2(alongX ? CameraBlockPos.x : CameraBlockPos.z, CameraBlockPos.y) * 16;
    mat2 dUv = mat2(dFdx(riftUv), dFdy(riftUv));
    k.hasMine = abs(determinant(dUv)) > 1e-12;
    k.o = px;
    k.span = vec2(0.0);
    k.at = vec2(0.5);
    k.mine = vec2(0.0);
    k.mineKey = 0u;
    if (k.hasMine) {
        mat2 m = mat2(dFdx(at), dFdy(at)) * inverse(dUv);
        vec2 mid = floor((at + m * (vec2(0.5) - riftUv)) / 8.0 + 0.5) * 8.0;
        k.o = ivec2(mid) + base;
        k.at = at - mid;
        k.span = 0.5 * (abs(m[0]) + abs(m[1]));
        k.mineKey = netherRiftHashU(ivec3(k.o >> 3, k.plane * 4 + 64));
        k.mine = (vec2(netherRiftUnit(k.mineKey, 0x3c6ef372u), netherRiftUnit(k.mineKey, 0xa54ff53au)) - 0.5)
                * vec2(12.0, 20.0);
    }
    return k;
}

// A distance measured across a line of this normal, counted along the nearer grid axis instead, so a crack
// drawn where it is under half a pixel comes out one pixel wide at any angle.
float netherRiftAxis(vec2 n) {
    return max(abs(n.x), abs(n.y));
}

// A crack's sideways wander at distance d along it: straight runs of 5 pixels with a kink between, up to
// about a pixel either way, none at the impact.
float netherRiftZig(uint key, float d) {
    float seg = floor(d / 5.0);
    int i = int(seg);
    float a = netherRiftUnit(key, uint(i) * 0x27d4eb2du), b = netherRiftUnit(key, uint(i + 1) * 0x27d4eb2du);
    return (mix(a, b, d / 5.0 - seg) - 0.5) * 2.0 * clamp(d / 8.0, 0.0, 1.0) * (0.8 + d * 0.006);
}

// Spoke s of n around an impact (s may run one past either end): its key and its angle, jittered within its
// share of the circle.
uint netherRiftSpokeKey(uint imp, int s, int n) {
    int w = s < 0 ? s + n : (s >= n ? s - n : s);
    return netherRiftMix(imp ^ (uint(w) + 1u) * 0x9e3779b9u);
}

float netherRiftSpokeAngle(uint key, int s, int n) {
    return (float(s) + 0.5 + 0.6 * (netherRiftUnit(key, 0x51ed270bu) - 0.5)) * 6.2831853 / float(n);
}

// How far out a spoke runs, in pixels: at stage 1 only some do, a little way; at 2 all, further; at 3 for ever.
float netherRiftReach(uint key, int st) {
    float h = netherRiftUnit(key, 0xc2b2ae35u), h2 = netherRiftUnit(key, 0x165667b1u);
    if (st <= 1) return h < 0.5 ? 6.0 + 20.0 * h2 : 0.0;
    if (st == 2) return 20.0 + 50.0 * h2;
    return 1e4;
}

// The glass at pixel q (at: where its middle is from the panel's middle). edge: how far (in pixels, see
// netherRiftAxis) from the nearest crack round its shard; normal: from that crack towards q; shard: a hash of
// the shard; whole: cracks close it all round (only those catch the light their own way or fall out); fallen:
// it fell out; pin: how far inside the hole the impact punched (> 0 inside).
void netherRiftShard(NetherRiftStrike k, ivec2 q, vec2 at, int st, out float edge, out vec2 normal, out uint shard,
        out bool whole, out bool fallen, out float pin) {
    vec2 p = vec2(q - k.o) + 0.5;
    // the nearest impact (i1, its key imp; own if it is the panel's) and the next (i2), among the world's in
    // the 3 by 3 cells around q (the jitter keeps the nearest among them) and the panel's own
    vec2 i1 = p + vec2(1e4), i2 = p + vec2(2e4);
    uint imp = 0u;
    bool own = false;
    float b1 = 1e12, b2 = 1e12;
    int cy = netherRiftFloorDiv(q.y, NETHER_RIFT_IMPACT_CELL);
    for (int j = -1; j <= 1; j++) {
        int y = cy + j;
        int cx = netherRiftFloorDiv(q.x - (y & 1) * (NETHER_RIFT_IMPACT_CELL / 2), NETHER_RIFT_IMPACT_CELL);
        for (int i = -1; i <= 1; i++) {
            ivec2 c = ivec2(cx + i, y);
            vec2 spot = netherRiftImpact(c, k.plane, k.o);
            if (k.hasMine && distance(spot, k.mine) < 40.0) continue;
            float dd = dot(p - spot, p - spot);
            if (dd < b1) {
                b2 = b1;
                i2 = i1;
                b1 = dd;
                i1 = spot;
                imp = netherRiftHashU(ivec3(c, k.plane * 4 + 63));
            } else if (dd < b2) {
                b2 = dd;
                i2 = spot;
            }
        }
    }
    float dm = dot(p - k.mine, p - k.mine);
    if (k.hasMine && dm < b1) {
        i2 = i1;
        i1 = k.mine;
        imp = k.mineKey;
        own = true;
    } else if (k.hasMine && dm < b2) {
        i2 = k.mine;
    }
    vec2 r = p - i1;
    float d = max(length(r), 0.01);
    int n = 8 + int(imp % 5u);
    float a = atan(r.y, r.x + 1e-6);
    if (a < 0.0) a += 6.2831853;

    // the sector: between the spoke at or below this angle and the next, the spoke's wander counted
    int below = min(int(a / (6.2831853 / float(n))), n - 1);
    uint keyK = netherRiftSpokeKey(imp, below, n);
    float angK = netherRiftSpokeAngle(keyK, below, n);
    vec2 uK = vec2(cos(angK), sin(angK));
    int s = dot(r, vec2(-uK.y, uK.x)) - netherRiftZig(keyK, dot(r, uK)) < 0.0 ? below - 1 : below;
    uint keyA = netherRiftSpokeKey(imp, s, n), keyB = netherRiftSpokeKey(imp, s + 1, n);
    float angA = netherRiftSpokeAngle(keyA, s, n), angB = netherRiftSpokeAngle(keyB, s + 1, n);
    float reachA = netherRiftReach(keyA, st), reachB = netherRiftReach(keyB, st);
    vec2 uA = vec2(cos(angA), sin(angA)), nA = vec2(-uA.y, uA.x);
    vec2 uB = vec2(cos(angB), sin(angB)), nB = vec2(-uB.y, uB.x);
    float alongA = dot(r, uA), alongB = dot(r, uB);
    // signed across each spoke, > 0 on the sector's side
    float sideA = (dot(r, nA) - netherRiftZig(keyA, alongA)) / netherRiftAxis(nA);
    float sideB = (netherRiftZig(keyB, alongB) - dot(r, nB)) / netherRiftAxis(nB);

    edge = 1e4;
    normal = vec2(0.0, 1.0);
    if (alongA < reachA && abs(sideA) < edge) {
        edge = abs(sideA);
        normal = sideA >= 0.0 ? nA : -nA;
    }
    if (alongB < reachB && abs(sideB) < edge) {
        edge = abs(sideB);
        normal = sideB >= 0.0 ? -nB : nB;
    }

    // the rings across this sector, spoke to spoke, further apart going out (they need both spokes to reach)
    uint sector = netherRiftMix(keyA ^ 0x632be5abu);
    int jMax = st <= 1 ? -1 : (st == 2 ? 2 : 9);
    int passed = 0;
    float ring = 0.0, beyond = 1e4;
    bool outward = false;
    for (int j = 0; j <= jMax; j++) {
        float base = 5.0 + 5.5 * float(j) + 1.1 * float(j * j);
        float ra = base * (1.0 + 0.16 * (netherRiftUnit(keyA, uint(j) * 0x2545f491u) - 0.5));
        float rb = base * (1.0 + 0.16 * (netherRiftUnit(keyB, uint(j) * 0x2545f491u) - 0.5));
        if (ra > reachA - 1.0 || rb > reachB - 1.0) break;
        if (netherRiftUnit(sector, uint(j) * 0x9e3779b9u) > 0.6) continue;
        vec2 ca = uA * ra + nA * netherRiftZig(keyA, ra), cb = uB * rb + nB * netherRiftZig(keyB, rb);
        float len = length(cb - ca);
        vec2 dir = (cb - ca) / len, m = vec2(dir.y, -dir.x);
        float t = dot(r - ca, dir);
        float seg = floor(t / 4.0);
        uint key = sector ^ uint(j) * 0x85ebca6bu;
        float wander = mix(netherRiftUnit(key, uint(int(seg)) * 0x27d4eb2du),
                netherRiftUnit(key, uint(int(seg) + 1) * 0x27d4eb2du), t / 4.0 - seg) - 0.5;
        float side = (dot(r - ca, m) - wander * 1.4 * clamp(min(t, len - t) / 2.0, 0.0, 1.0)) / netherRiftAxis(m);
        if (side > 0.0) {
            passed = j + 1;
            ring = base;
            if (side < edge) {
                edge = side;
                normal = m;
            }
        } else if (!outward) {
            outward = true;
            beyond = base;
            if (-side < edge) {
                edge = -side;
                normal = -m;
            }
        }
    }

    // where this impact's web meets the next one's, the nearer impact wins along a crack between them
    vec2 nb = normalize(i2 - i1);
    float border = (dot(p - i2, p - i2) - d * d) / (2.0 * length(i2 - i1)) / netherRiftAxis(nb);
    if ((st >= 3 || (st == 2 && d < 36.0)) && border < edge) {
        edge = border;
        normal = -nb;
    }

    // along the panel's sides, where its own impact owns the glass: on a seam the next panel's web may not
    // match (on the outline the edge strip covers it)
    vec2 inside = k.span - abs(at);
    if (own && (st >= 3 || (st == 2 && d < 36.0)) && min(inside.x, inside.y) < 1.5) {
        float seam = abs(min(inside.x, inside.y) - 0.5);
        if (seam < edge) {
            edge = seam;
            normal = inside.x < inside.y ? vec2(at.x < 0.0 ? 1.0 : -1.0, 0.0) : vec2(0.0, at.y < 0.0 ? 1.0 : -1.0);
        }
    }

    whole = st >= 3 || outward;
    shard = netherRiftMix(sector ^ uint(passed + 1) * 0x85ebca6bu);
    // At stage 3 each sector has broken out from the impact to a way of its own (less in a small panel): the
    // shards whose outer ring lies within it are out, so the hole is one jagged star and what glass is left
    // still hangs on the glass round it. At 2 some of the innermost shards are out.
    float h = netherRiftUnit(sector, 0x1b873593u);
    float broken = min(8.0 + 34.0 * h, k.hasMine ? (0.45 + 0.7 * h) * min(k.span.x, k.span.y) : 1e4);
    fallen = whole && (st >= 3 ? beyond < broken + 8.0
            : (st == 2 && ring == 0.0 && netherRiftUnit(shard, 0x1b873593u) < 0.5));
    pin = (st <= 1 ? 1.6 : (st == 2 ? 2.6 : 0.0)) - d;
}

// The panorama behind the rift, by view direction, nearest texel. The sprite holds six cube faces 3 by 2,
// laid out by tools/gen_panorama.py (nether-rifts): row 0 faces 0 1 2, row 1 faces 3 4 5; face 0 looks south
// (+Z, image right -X, up +Y), 1 west (-X, right -Z), 2 north (-Z, right +X), 3 east (+X, right +Z), 4 up
// (+Y, right -X, up -Z), 5 down (-Y, right -X, up +Z). shift moves the texel column (each hole a texel off the
// next, as if the shards refract).
vec3 netherRiftPanorama(vec3 d, int shift) {
    vec2 lo, hi;
    if (!riftSpriteBox(lo, hi)) return texture(Sampler0, texCoord0).rgb;
    vec2 atlas = vec2(textureSize(Sampler0, 0));
    ivec2 boxMin = ivec2(floor(lo * atlas + 0.5));
    int face = 0;
    vec2 s;
    vec3 a = abs(d);
    if (a.y >= a.x && a.y >= a.z) {
        face = d.y > 0.0 ? 4 : 5;
        s = d.y > 0.0 ? vec2(-d.x, -d.z) / a.y : vec2(-d.x, d.z) / a.y;
    } else if (a.x >= a.z) {
        face = d.x < 0.0 ? 1 : 3;
        s = vec2(d.z, d.y) / a.x * vec2(d.x < 0.0 ? -1.0 : 1.0, 1.0);
    } else {
        face = d.z > 0.0 ? 0 : 2;
        s = vec2(d.z > 0.0 ? -d.x : d.x, d.y) / a.z;
    }
    int size = int(floor((hi.x - lo.x) * atlas.x + 0.5)) / 3;
    ivec2 texel = ivec2(floor(vec2(s.x + 1.0, 1.0 - s.y) * 0.5 * float(size))) + ivec2(shift, 0);
    texel = clamp(texel, ivec2(0), ivec2(size - 1));
    return texelFetch(Sampler0, boxMin + ivec2(face % 3, face / 3) * size + texel, 0).rgb;
}

// Whether this fragment's face is beyond the rift's middle plane from the camera, and so seen through the
// portal: the faces sit about 0.135 of a block either side of it, and riftView runs from the camera.
bool netherRiftBeyond() {
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    float off = fract(alongX ? riftWorld.z : riftWorld.x) - 0.5;
    float eye = off - (alongX ? riftView.z : riftView.x);
    return off * eye < 0.0;
}

vec4 netherRiftBody(float stage) {
    float t = GameTime * 1200.0;
    int st = int(stage + 0.5);
    float red = st >= 3 ? 0.65 : (st == 2 ? 0.2 : 0.0);
    bool beyond = netherRiftBeyond();
    ivec2 px = netherRiftPixel();

    NetherRiftStrike k = netherRiftStrike(px);
    float edge, pin;
    vec2 normal;
    uint shard;
    bool whole, fallen;
    netherRiftShard(k, px, k.at, st, edge, normal, shard, whole, fallen, pin);

    if (beyond) {
        // The glass's body, seen through the portal (which lets about a quarter through): none at stage 1, a
        // faint red at 2, red-orange at 3; each whole shard a shade lighter or darker than the next.
        if (st <= 1 || fallen || pin > 0.0) return vec4(0.0);
        vec3 tint = st >= 3 ? vec3(1.0, 0.34, 0.08) : vec3(0.9, 0.08, 0.14);
        float alpha = st >= 3 ? 1.0 : 0.6;
        if (whole) {
            tint *= 0.6 + 0.7 * netherRiftUnit(shard, 0x68e31da4u);
        }
        return vec4(tint, alpha);
    }

    // A fallen shard leaves a jagged lip of glass, one to three pixels, with a thin glowing rim, along each crack
    // it shared with glass still there; where the shard across fell too, nothing is left between.
    float hole = pin;
    if (fallen) {
        float lip = 1.0 + floor(1.6 * netherRiftNoise(px, 1, vec2(0.0), 71) + 0.9 * netherRiftHash(ivec3(px, 73)));
        hole = 9.0;
        if (edge < lip) {
            ivec2 q = px + ivec2(floor(-normal / netherRiftAxis(normal) * (edge + 1.0) + 0.5));
            float e2, pin2;
            vec2 n2;
            uint shard2;
            bool whole2, fallen2;
            netherRiftShard(k, q, k.at + vec2(q - px), st, e2, n2, shard2, whole2, fallen2, pin2);
            if (pin2 <= 0.0 && !fallen2) hole = edge - lip;
        }
    }

    if (hole > 0.0) {
        // the nether, far away, through the hole (each hole a texel off the next), seen through the portal's
        // haze (a fifth of it on average, thicker in its bright bands and thin between, so the fortress reads
        // even from afar; mixed here, as the hole must stay opaque); darker under its lip
        int slip = int(shard % 3u) - 1;
        vec3 view = netherRiftPanorama(normalize(riftView), slip);
        float swirl = netherRiftSwirl(px, t);
        vec3 haze = mix(vec3(59.0, 10.0, 107.0) / 255.0, vec3(196.0, 108.0, 255.0) / 255.0, swirl);
        view = mix(view, haze, 0.2 * (0.55 + 0.9 * swirl));
        return vec4(view * mix(0.7, 1.0, clamp(hole / 3.0, 0.0, 1.0)), 1.0);
    }
    if (hole > -1.0) {
        // the hole's rim, a pixel of pale violet glow at every stage, each pixel shimmering softly on its own
        // beat (every 2 s, which divides the 1200 s where GameTime wraps)
        float glow = 0.5 + 0.5 * sin(6.2831853 * (t / 2.0 + netherRiftHash(ivec3(px, 23))));
        return vec4(mix(vec3(217.0, 179.0, 255.0) / 255.0, vec3(240.0, 220.0, 255.0) / 255.0, glow), 1.0);
    }

    // The glass's surface: clear, so the portal shows, but for its cracks (a dark pixel line, its far wall
    // catching the light from the upper left: violet, rose from stage 2, a touch brighter at 3) and a glint on
    // some shards. Anything translucent here would hide the portal behind it (items draw before translucent
    // blocks and write depth), so it is opaque or nothing.
    vec3 pale = st >= 3 ? vec3(0.98, 0.44, 0.6) : (st == 2 ? vec3(0.93, 0.36, 0.5) : NETHER_RIFT_PURPLE[4]);
    if (edge < 0.5) {
        return vec4(mix(NETHER_RIFT_PURPLE[0], NETHER_RIFT_RED[0], red) * 0.6, 1.0);
    }
    bool lit = dot(normal, vec2(-0.6, 0.8)) < -0.25;
    if (edge < 1.5 && lit) {
        return vec4(pale, 1.0);
    }
    if (whole && netherRiftUnit(shard, 0x2c1b3c6du) < 0.3 && ((px.x - px.y + int(shard & 31u)) & 31) == 0) {
        return vec4(pale, 1.0);
    }
    return vec4(0.0);
}

// A seam of sparks along the outline, two pixels across the middle of the strip: a few at stage 1, most of
// it at 3, flickering. Opaque or nothing, as everything drawn over the portal must be.
vec4 netherRiftEdge(float stage) {
    if (netherRiftBeyond()) return vec4(0.0);
    float t = GameTime * 1200.0;
    int st = int(stage + 0.5);
    float red = st >= 3 ? 0.65 : (st == 2 ? 0.2 : 0.0);
    int across = clamp(int(floor(riftLocal().y * 4.0)), 0, 3);
    if (across == 0 || across == 3) return vec4(0.0);
    ivec2 px = netherRiftPixel();
    float spark = netherRiftHash(ivec3(px, int(floor(t * 10.0)) * 8 + 21));
    if (spark > 0.12 + 0.18 * (stage - 1.0)) return vec4(0.0);
    vec3 hot = mix(vec3(1.0, 0.47, 0.94), vec3(1.0, 0.35, 0.27), red);
    return vec4(spark < 0.04 ? vec3(1.0, 0.92, 1.0) : hot, 1.0);
}

// the pack's own albedo sample goes through here: a rift's colour for a rift, else the sample
vec4 metacraft_rift_sample(vec4 sprite) {
	float flag = metacraft_rift_flag;
	if (flag < 0.5) return sprite;
	vec4 rift;
	if (flag > 11.5) rift = netherRiftEdge(metacraft_rift_stage);
	else if (flag > 10.5) rift = netherRiftBody(metacraft_rift_stage);
	else if (flag > 2.5) rift = riftCore();
	else if (flag > 1.5) rift = riftGlow(sprite);
	else rift = riftColor(sprite);
	if (rift.a < 0.02) discard;
	return rift;
}
#undef texCoord0
#undef riftUv
#undef GameTime
#undef ScreenSize
#undef riftWorld
#undef riftView
#undef CameraBlockPos
#undef Sampler0
#undef determinant
#undef inverse
// --- end Metacraft rifts



void main() {
	ovvar_shadow_texcoord = ovvar_raw_texcoord();
	ovvar_shadow_texcoord.xy = ovvar_uv(ovvar_shadow_texcoord.xy);
	ovvar_shadow_glcolor = ovvar_shade(ovvar_raw_glcolor(), ovvar_shadow_texcoord.xy);

	vec4 albedo = metacraft_rift_sample(texture2D(texture, texcoord)) * glcolor;
	gl_FragData[0] = albedo;
}
