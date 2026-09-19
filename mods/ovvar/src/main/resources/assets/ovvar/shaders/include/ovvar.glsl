// Ovvar: patches on student overalls, finished by the shader. Shared by the vanilla entity.fsh
// override and (copied in by the shaderpack patcher) by shaderpacks' entity programs, so it is
// written against GLSL 120: no integer bit operations, no texelFetch — texels are read at their
// centres with OVVAR_SAMPLE, and bit fields are pulled out with floating-point arithmetic.
//
// A texture of ours is the 64×32 armour layout at OVVAR_D texels per texel (128×64), with a
// marker texel at (W−1, H/2−1): magenta, alpha 2/255. The texel left of it says what it is (R):
//   0  a base garment texture. The model draws the left arm and leg as mirror images off the right
//	  limb's strips; on those fragments the shader samples one strip up, where the left-side art
//	  lives.
//   1  a placement texture: one patch drawn on one cell, for one side (G: 0 body, 1 right limb,
//	  2 left limb) on one face of its strip (B: 0..3, or 4 — Spot.TOP_FACE — for the box's top
//	  face, which is not on the strip: the shoulders). Fragments of the other limb read the
//	  blank texel.
//   2  the preview texture: every instant design's art in a library (head rows), a cell table at
//	  x 40·D (index in the half → u, v, side, in texels; column-major, 16 tall; 3·D columns
//	  further right: the cell's own width and height, since a cell is not one size — the big back
//	  cell is two cells each way and the seat two wide; its B is Spot.layer, which of two
//	  overlapping cells is drawn on top; a v above the side rows means the cell is
//	  on the box's TOP face, the shoulders) and a design
//	  table 3·D columns further right again (design + fit·OVVAR_FIT_SLOTS → library x, y, cells;
//	  3·D columns further right: art width, height — a row per art the design can be drawn as,
//	  since a patch may ship its art at several sizes); G = cells in the half, B = designs. The garment's dye
//	  colour carries up to three placements as the rank of their set among all sets of
//	  (cell × designs + design) states, after all smaller sets (0 = none) — packed as three
//	  base-255 digits, each byte one more than its digit, so no byte is ever 0. Everything else
//	  reads the blank texel at (W−1, H/2−2).
//
// Requires before inclusion: OVVAR_SAMPLE(uv) — the albedo sample of this program;
// ovvar_color — the raw (unlit) vertex colour, vec4; ovvar_pos and ovvar_normal — the vertex
// position and normal (any one space for both).

// Texels per skin texel (Spot.DETAIL in the mod) and the texture size, cell size and fixed texels that follow.
const float OVVAR_D = 2.0;
const vec2 OVVAR_TEX = vec2(64.0, 32.0) * OVVAR_D;
const float OVVAR_CELL = 4.0 * OVVAR_D;   // the default cell; a cell's own size comes from the cell table
// The first of the box SIDE rows (Spot.FACE_ROW): above it are the boxes' top and bottom faces,
// where the shoulder cells are, and where nothing is squeezed round the box.
const float OVVAR_SIDE_ROW = 20.0;
// Spot.TOP_FACE: what a placement's kind texel carries in B when its cell is on the box's top face.
const float OVVAR_TOP_FACE = 4.0;
// The preview texture's four tables (GeneratedAssets: CELL_TABLE_X and the three beside it), each
// OVVAR_TABLE_COLUMNS texel columns wide and 16 rows tall, read at (base + floor(i/16), mod(i,16)):
// the cell table, the cells' own sizes, the design table, the arts' sizes.
const float OVVAR_TABLE_X = 40.0 * OVVAR_D;
const float OVVAR_TABLE_COLUMNS = 3.0 * OVVAR_D;
// A design's row in the two design tables is design + fit * OVVAR_FIT_SLOTS: a patch may ship its
// art at several sizes and which of them a cell shows is the cell's to decide (Patches.Fit), so the
// tables hold a row per (design, fit) and this is the fit of the cell being drawn. The numbers are
// Patches.Fit's own ordinals, which a game test holds these to:
//   OVER (0)     the artist's own size, hanging over the cell if it is bigger - every ordinary cell,
//   CLIPPED (1)  a cell the art is cut to, which is a box's top face (the shoulders),
//   FILLED (2)   a cell as big as art may get (the big back cell), meant to be filled.
const float OVVAR_FIT_SLOTS = 32.0;
const float OVVAR_FIT_OVER = 0.0;
const float OVVAR_FIT_CLIPPED = 1.0;
const float OVVAR_FIT_FILLED = 2.0;
const vec2 OVVAR_MARKER = vec2(OVVAR_TEX.x - 1.0, OVVAR_TEX.y * 0.5 - 1.0);
const vec2 OVVAR_BLANK = (OVVAR_MARKER + vec2(0.5, -0.5)) / OVVAR_TEX;

vec4 ovvar_read(float x, float y) {
	return floor(OVVAR_SAMPLE((vec2(x, y) + 0.5) / OVVAR_TEX) * 255.0 + 0.5);
}

// Which sign of texture-over-geometry handedness the mirrored limbs have. Fixed by how the game
// builds its vertex data (the same on every platform); calibrated once against a known garment.
// This is the calibration for a box's four SIDE faces, which until the shoulders arrived were the
// only faces anything of ours was drawn on.
const bool OVVAR_MIRROR_SENSE = true;

// Does a box's TOP (and BOTTOM) face unwrap with the same texture-over-geometry handedness as its
// four sides, so that OVVAR_MIRROR_SENSE's answer means the same thing there? Yes — worked out, and
// then settled by the playtest.
//
// The unwrap. On a side face the texture's u runs around the box and its v runs DOWN it (+y in
// model space, the model being drawn y-flipped). On the top face v runs ACROSS the box instead,
// from its back edge to its front (-z): that is what puts the top face's last row against the front
// face's first row, which is how the vanilla box unwrap lays the two out, how the skins are drawn,
// and what the game shows (a shoulder patch reads with the art's top towards the wearer's back).
// So only u's direction is in question, and the handedness follows from it — take u ^ v against the
// outward normal:
//
//   front face  u = +x, v = +y, n = -z:  (+x) ^ (+y) = +z, dot(-z) < 0
//   top face	u = +x, v = -z, n = -y:  (+x) ^ (-z) = +y, dot(-y) < 0   -- same as a side face
//   top face	u = -x, v = -z, n = -y:  (-x) ^ (-z) = -y, dot(-y) > 0   -- opposite
//
// The playtest picked between them: taking the answer as INVERTED on the top rows put each
// shoulder's art on the other shoulder, so it is not inverted — u = +x, the top face's u running
// the wearer's right to left, the same way the front face's does. Which is also what the edge they
// share demands: the box's top-front edge is one line in the texture, and the two faces must run
// along it the same way.
//
// Why the earlier playtest (one shoulder drawing, the other bare) says nothing about this: both
// arms' top faces are the very same texels, so `mirrored` is the only thing that can tell them
// apart, and under EITHER sense exactly one of the two shoulder cells is selected per arm — every
// setting predicts both shoulders drawing, one patch each. A bare shoulder is not a state this
// constant can produce; that session's client had not been pushed a pack holding the new cells
// ("you already have the latest pack"), so its cell table was the one from before they existed.
//
// If a shoulder patch ever comes out on the other shoulder than it was sewn on, this is the one
// line to flip.
const bool OVVAR_TOP_FACE_SAME_SENSE = true;

// Which half of a seat patch's art the unmirrored (the wearer's right) leg wears: Spot.seatColumn's
// RIGHT value, which a game test holds this to. Seat art is drawn as seen from behind, where the
// wearer's right leg is on the viewer's right, so the right leg takes the art's RIGHT half (column
// 1) and the mirrored left leg the other one.
const float OVVAR_SEAT_COLUMN_RIGHT = 1.0;

// ---- dev only: see what the shader makes of the boxes' top faces (where the shoulders are)
//
// All off in everything shipped, and a game test holds them off. With one on, a fragment of a limb
// box's top or bottom face is painted instead of drawn. THREE SEPARATE SWITCHES, because the first
// playtest of them could not tell which of the two paintings mattered:
//
//   OVVAR_DEBUG_TOP_FACE_SIDES  the BASE GARMENT layer, which covers every such fragment whatever
//							   is sewn, paints instead of sampling the cloth:
//								 RED   the mirror test calls this fragment mirrored (the left limb),
//								 BLUE  it calls it unmirrored (the right);
//   OVVAR_DEBUG_TOP_FACE_MISS   the PREVIEW layer paints MAGENTA where no cell of the instant
//							   design matched the fragment;
//   OVVAR_DEBUG_TOP_FACE_HIT	the PREVIEW layer paints GREEN where a cell DID match — instead of
//							   the art, so a green face says the shader got all the way to the
//							   library and drew, whatever the result looks like.
//
// OVVAR_DEBUG_TOP_FACES turns on all three (what the first playtest ran).
//
// **The experiment to run now: only OVVAR_DEBUG_TOP_FACE_HIT.** Nothing else about the frame
// changes — the base garment still draws its own cloth — so:
//
//   BOTH arms' top faces GREEN  the preview path reaches the library on both arms in an otherwise
//	   normal build, so the patch was being drawn all along and whatever hides one shoulder is
//	   later than this shader: draw order, the layer stack, or the pack the client is holding;
//   one green arm, one bare	 the preview path really does reach a cell on only one arm, and
//	   since it reaches both when SIDES paints as well, the base garment layer's own top-face
//	   branch is implicated and that is the thing to change;
//   neither green			   no cell matched at all (turn MISS on too: magenta says the same
//	   fragments got as far as the cell table).
//
// The colours are opaque texels datagen writes into every texture of ours, in the marker row left
// of the layer texel (see GeneratedAssets: DEBUG_X), so painting is just a coordinate like any
// other and no program needs a new output.
const bool OVVAR_DEBUG_TOP_FACES = false;
const bool OVVAR_DEBUG_TOP_FACE_SIDES = false;
const bool OVVAR_DEBUG_TOP_FACE_MISS = false;
const bool OVVAR_DEBUG_TOP_FACE_HIT = false;
const vec2 OVVAR_DEBUG_MIRRORED = (vec2(OVVAR_TEX.x - 7.0, OVVAR_TEX.y * 0.5 - 1.0) + 0.5) / OVVAR_TEX;
const vec2 OVVAR_DEBUG_UNMIRRORED = (vec2(OVVAR_TEX.x - 6.0, OVVAR_TEX.y * 0.5 - 1.0) + 0.5) / OVVAR_TEX;
const vec2 OVVAR_DEBUG_NO_CELL = (vec2(OVVAR_TEX.x - 5.0, OVVAR_TEX.y * 0.5 - 1.0) + 0.5) / OVVAR_TEX;
const vec2 OVVAR_DEBUG_CELL_HIT = (vec2(OVVAR_TEX.x - 4.0, OVVAR_TEX.y * 0.5 - 1.0) + 0.5) / OVVAR_TEX;

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
	// geometry says so, read with the sense of the kind of face the fragment is on: a box's top and
	// bottom faces need not unwrap the way its sides do (see OVVAR_TOP_FACE_SAME_SENSE), and if they
	// did not the same answer would mean the opposite thing. Every path below — the base garment's mirror strip, a
	// placement's "hide it on the other limb", and the preview's cell sides — reads this one bool,
	// so the two arms' top faces are told apart exactly once.
	bool mirrored = limb && ((sides || OVVAR_TOP_FACE_SAME_SENSE) ? ovvar_handed : !ovvar_handed);
	float inflate = ovvar_inflate();
	float ay = sides ? ovvar_squeezed_y(t.x, t.y, inflate) : t.y;
	bool inFace = !sides || (ay >= OVVAR_SIDE_ROW * OVVAR_D && ay < 32.0 * OVVAR_D);

	if (kind.r > 0.5 && kind.r < 1.5) {
		// Placement: hide it on the limb it is not for; continuous round the box from its face
		// (the kind texel's B), nothing in the slack.
		if (limb && ((kind.g > 0.5 && kind.g < 1.5 && mirrored) || (kind.g > 1.5 && !mirrored))) return OVVAR_BLANK;
		// A cell on the box's TOP face (the shoulders): datagen has drawn the art on the top rows
		// and clipped it to that face, which is not on the strip's perimeter — so there is no
		// squeeze to do, the texel is taken as it lies, and the side rows carry none of it.
		if (kind.b > OVVAR_TOP_FACE - 0.5) return sides ? OVVAR_BLANK : uv;
		if (!inFace) return OVVAR_BLANK;
		float a = sides ? ovvar_squeezed_anchored(t.x, inflate, kind.b) : t.x;
		if (a < 0.0) return OVVAR_BLANK;
		return vec2(a, ay) / OVVAR_TEX;
	}

	if (kind.r < 0.5) {
		// Dev only: paint a limb box's top (or bottom) face by what the mirror test makes of it.
		if ((OVVAR_DEBUG_TOP_FACES || OVVAR_DEBUG_TOP_FACE_SIDES) && limb && !sides) {
			return mirrored ? OVVAR_DEBUG_MIRRORED : OVVAR_DEBUG_UNMIRRORED;
		}
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
		// a big patch bends round the corners here just as it will once the pack has it. A cell on
		// the box's top face is the exception: no mapping, and clipped rather than bent (below).
		// The cell's own size: OVVAR_CELL for nearly all of them, twice that each way for the big
		// back cell, and two cells wide for the seat — half of which is one leg's. The art's height
		// is the art's own either way, so a seat patch taller than the seat's row hangs over it just
		// as a big patch hangs over a plain cell.
		float cw = cz.r, ch = cz.g;
		// Which kind of face the cell is on, off its own row: above the side rows is the box's TOP
		// face (the shoulders). A top cell is drawn on the top rows and nowhere else, and a side
		// cell on the side rows and nowhere else.
		bool onTop = ce.g < OVVAR_SIDE_ROW * OVVAR_D;
		if (onTop == sides) continue;
		// Which of the design's arts this cell shows (Patches.Fit, above): the art is clipped to a top
		// face, so a cell-sized one goes there whole; a cell bigger than a cell -- the big back one,
		// never the seat, whose two cells wear one patch drawn to their own size -- is filled; anything
		// else takes the artist's own size and lets it hang over.
		float fit = onTop ? OVVAR_FIT_CLIPPED
				: (side < 2.5 && (cw > OVVAR_CELL || ch > OVVAR_CELL) ? OVVAR_FIT_FILLED : OVVAR_FIT_OVER);
		float slot = design + fit * OVVAR_FIT_SLOTS;
		vec4 pe = ovvar_read(OVVAR_TABLE_X + 2.0 * OVVAR_TABLE_COLUMNS + floor(slot / 16.0), mod(slot, 16.0));   // library x, y, cells
		vec4 sz = ovvar_read(OVVAR_TABLE_X + 3.0 * OVVAR_TABLE_COLUMNS + floor(slot / 16.0), mod(slot, 16.0));   // art width, height
		float w = side > 2.5 ? cw * 0.5 : sz.r, h = sz.g;
		vec2 origin = ce.rg + vec2(side > 2.5 ? 0.0 : (cw - w) * 0.5, (ch - h) * 0.5);
		float a = t.x;
		if (sides) {
			a = ovvar_squeezed_anchored(t.x, inflate, ovvar_face_of(body, ce.r / OVVAR_D - stripStart));
			if (a < 0.0) continue;
		}
		vec2 local = vec2(a, ay) - origin;
		if (!onTop) {
			if (local.x < 0.0) local.x += stripWidth; else if (local.x >= stripWidth) local.x -= stripWidth;   // art wrapped round the strip's end
		} else {
			// Clipped to the top face, both ways: a top face's four edges have no neighbouring face
			// in the layout to continue onto, so art bigger than the cell is simply cut off at them
			// rather than bent round — the same as the pack path (GeneratedAssets.placed).
			vec2 inCell = vec2(a, ay) - ce.rg;
			if (inCell.x < 0.0 || inCell.x >= cw || inCell.y < 0.0 || inCell.y >= ch) continue;
		}
		if (local.x < 0.0 || local.x >= w || local.y < 0.0 || local.y >= h) continue;
		if (flip) local.x = w - local.x;   // the model mirrors the left limb; mirror back
		if (cz.b <= drawnLayer) continue;   // a cell higher in the stack has this fragment already
		// The dye colour is ONE layer, so a fragment can show one texel however many cells it is
		// inside: the top cell's, unless that cell's art has painted nothing there, in which case the
		// cell under it shows through — which is what the pack path's layers do of their own accord.
		vec2 at = pe.rg + vec2(column * w, 0.0) + local;
		if (ovvar_read(floor(at.x), floor(at.y)).a < 0.5) continue;
		drawnLayer = cz.b;
		drawn = at / OVVAR_TEX;
	}
	if (drawnLayer >= 0.0) {
		// Dev only: this fragment matched a cell and is about to be drawn from the library — say so
		// in one colour, whatever the art turns out to look like.
		if ((OVVAR_DEBUG_TOP_FACES || OVVAR_DEBUG_TOP_FACE_HIT) && limb && !sides) return OVVAR_DEBUG_CELL_HIT;
		return drawn;
	}
	// Dev only: a limb box's top face that matched no cell of the design. A shoulder that is sewn
	// and comes out magenta is one whose fragments got past the mirror test and then missed the
	// cell's own window.
	if ((OVVAR_DEBUG_TOP_FACES || OVVAR_DEBUG_TOP_FACE_MISS) && limb && !sides) return OVVAR_DEBUG_NO_CELL;
	return OVVAR_BLANK;
}

// The vertex colour to shade with: on a patch texture of ours the dye colour is data, not paint,
// so it is divided out of the lit colour (no byte of it is 0). uv: the coordinate being sampled.
vec4 ovvar_shade(vec4 lit, vec2 uv) {
	if (ovvar_patch_layer() < 0.5) return lit;
	return vec4(lit.rgb / max(ovvar_color.rgb, vec3(1.0 / 255.0)), lit.a);
}
