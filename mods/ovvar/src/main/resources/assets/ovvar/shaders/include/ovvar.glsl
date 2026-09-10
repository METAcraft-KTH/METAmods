// Ovvar: patches on student overalls, finished by the shader. Shared by the vanilla entity.fsh
// override and (copied in by the shaderpack patcher) by shaderpacks' entity programs, so it is
// written against GLSL 120: no integer bit operations, no texelFetch — texels are read at their
// centres with OVVAR_SAMPLE, and bit fields are pulled out with floating-point arithmetic.
//
// A texture of ours is the 64×32 armour layout at OVVAR_D texels per texel (128×64), with a
// marker texel at (W−1, H/2−1): magenta, alpha 2/255. The texel left of it says what it is (R):
//   0  a base garment texture. The model draws the left arm and leg as mirror images off the right
//      limb's strips; on those fragments the shader samples one strip up, where the left-side art
//      lives.
//   1  a placement texture: one patch drawn on one cell, for one side (G: 0 body, 1 right limb,
//      2 left limb). Fragments of the other limb read the blank texel.
//   2  the preview texture: every patch's art in a library (head rows), a cell table at x 40·D
//      (index in the half → u, v, side, in texels; column-major, 16 tall) and a design table at
//      x 44·D (design → library x, y, cells); G = cells in the half, B = designs. The garment's dye
//      colour carries up to three placements as the rank of their set among all sets of
//      (cell × designs + design) states, after all smaller sets (0 = none) — packed as three
//      base-255 digits, each byte one more than its digit, so no byte is ever 0. Everything else
//      reads the blank texel at (W−1, H/2−2).
//
// Requires before inclusion: OVVAR_SAMPLE(uv) — the albedo sample of this program;
// ovvar_color — the raw (unlit) vertex colour, vec4; ovvar_pos and ovvar_normal — the vertex
// position and normal (any one space for both).

// Texels per skin texel (Spot.DETAIL in the mod) and the texture size, cell size and fixed texels that follow.
const float OVVAR_D = 2.0;
const vec2 OVVAR_TEX = vec2(64.0, 32.0) * OVVAR_D;
const float OVVAR_CELL = 4.0 * OVVAR_D;
const vec2 OVVAR_MARKER = vec2(OVVAR_TEX.x - 1.0, OVVAR_TEX.y * 0.5 - 1.0);
const vec2 OVVAR_BLANK = (OVVAR_MARKER + vec2(0.5, -0.5)) / OVVAR_TEX;

vec4 ovvar_read(float x, float y) {
    return floor(OVVAR_SAMPLE((vec2(x, y) + 0.5) / OVVAR_TEX) * 255.0 + 0.5);
}

// Which sign of texture-over-geometry handedness the mirrored limbs have. Fixed by how the game
// builds its vertex data (the same on every platform); calibrated once against a known garment.
const bool OVVAR_MIRROR_SENSE = true;

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
// n + 2·inflate units and 12 rows cover 12 + 2·inflate. Everything of ours on the box sides —
// the garment and the patches alike — is drawn squeezed in x about its face's centre by
// height/width, so pixels come out square and the patch grid stays on the fabric's grid; the
// garment's edge columns stretch into the margin that leaves; patches leave it to the fabric,
// except one that continues round the corner, which stretches its edge column there too.
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

// Which texel column of its strip a side-row fragment shows. Each face's texels are drawn
// centred on the face at ovvar_pixel units each, so a cell's art sits where the face's own
// texels do; the face is wider than that (the box is inflated), and in its margin the column
// returned continues past the face's edge — the neighbouring face's edge column, what lies
// round the corner. faceStart/faceEnd: the face's own texels (strip-local), so the caller
// knows a margin when it sees one. skinX in skin texels; returns the strip-local texel x.
float ovvar_wrap(float skinX, float inflate, out float stripStart, out float faceStart, out float faceEnd) {
    float p = ovvar_pixel(skinX, inflate), e = 2.0 * inflate;
    bool body = skinX >= 16.0 && skinX < 40.0;
    stripStart = skinX < 16.0 ? 0.0 : body ? 16.0 : 40.0;
    float local = skinX - stripStart;
    float total = body ? 24.0 : 16.0;
    // The face: start and texel count (legs and arms: four 4-wide; body: right 4 | front 8 | left 4 | back 8).
    float fs, n;
    if (!body) { fs = floor(local / 4.0) * 4.0; n = 4.0; }
    else if (local < 4.0) { fs = 0.0; n = 4.0; }
    else if (local < 12.0) { fs = 4.0; n = 8.0; }
    else if (local < 16.0) { fs = 12.0; n = 4.0; }
    else { fs = 16.0; n = 8.0; }
    faceStart = fs;
    faceEnd = fs + n;
    float c = fs + n * 0.5;
    float units = (local - c) * ((n + e) / n);   // from the face centre, in model units
    return mod(c + units / p, total);
}

// A fragment's texel x on the side rows → the texel x to draw there; lo/hi: the face's own texels.
float ovvar_squeezed(float tx, float inflate, out float lo, out float hi) {
    float stripStart, faceStart, faceEnd;
    float t = ovvar_wrap(tx / OVVAR_D, inflate, stripStart, faceStart, faceEnd);
    lo = (stripStart + faceStart) * OVVAR_D;
    hi = (stripStart + faceEnd) * OVVAR_D;
    return (stripStart + t) * OVVAR_D;
}

// The same in y, about the side rows' centre: only the boots pass on the legs needs it (its rows
// are 14/12 tall, the leggings' 13/12).
float ovvar_squeezed_y(float tx, float ty, float inflate) {
    float sy = ovvar_pixel(tx / OVVAR_D, inflate) / ((12.0 + 2.0 * inflate) / 12.0);
    float c = 26.0 * OVVAR_D;
    return (ty - c) / sy + c;
}

// The preview texture's instant set, unranked by ovvar_uv from the dye colour: up to three
// (cell × designs + design) states, in order.
float ovvar_set[3];
int ovvar_set_count = 0;

// Where in the preview texture's library the fragment at squeezed texel t reads, or OVVAR_BLANK;
// which of the set's placements it is comes back in `which` (−1 for none).
vec2 ovvar_preview_at(float designs, vec2 t, bool limb, bool mirrored, out int which) {
    which = -1;
    for (int i = 0; i < 3; i++) {
        if (i >= ovvar_set_count) break;
        float cell = floor(ovvar_set[i] / designs), design = ovvar_set[i] - cell * designs;
        vec4 ce = ovvar_read(40.0 * OVVAR_D + floor(cell / 16.0), mod(cell, 16.0));       // u, v, side (texels)
        vec4 pe = ovvar_read(44.0 * OVVAR_D + floor(design / 16.0), mod(design, 16.0));   // library x, y, cells
        vec2 local = t - ce.rg;
        if (local.x < 0.0 || local.x >= OVVAR_CELL || local.y < 0.0 || local.y >= OVVAR_CELL) continue;
        float side = ce.b;
        float column = 0.0;   // which cell of the art
        bool flip = false;
        if (side < 0.5) { if (limb) continue; }
        else if (side < 1.5) { if (!limb || mirrored) continue; }
        else if (side < 2.5) { if (!limb || !mirrored) continue; flip = true; }
        else { if (!limb) continue; if (mirrored) { column = 1.0; flip = true; } }   // seat: one half per leg
        if (flip) local.x = OVVAR_CELL - local.x;   // the model mirrors the left limb; mirror back
        which = i;
        return (pe.rg + vec2(column * OVVAR_CELL, 0.0) + local) / OVVAR_TEX;
    }
    return OVVAR_BLANK;
}

// Does a patch layer show anything at this coordinate?
bool ovvar_opaque(vec2 uv) {
    return any(notEqual(uv, OVVAR_BLANK)) && OVVAR_SAMPLE(uv).a > 0.0;
}

// The texture coordinate to sample instead of uv. Call with the program's original coordinate;
// derivatives must be taken in uniform control flow, hence at the top.
// Set by ovvar_uv: is this fragment on a mirrored (left) limb face? Meaningful on any texture,
// ours or not — the trim atlas draw uses it through ovvar_sided.
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
    bool mirrored = limb && ovvar_handed;
    bool sides = t.y >= 20.0 * OVVAR_D;   // the box sides, not the top and bottom faces
    float inflate = ovvar_inflate();
    float a = t.x, lo = 0.0, hi = OVVAR_TEX.x, ay = t.y;
    if (sides) {
        a = ovvar_squeezed(t.x, inflate, lo, hi);
        ay = ovvar_squeezed_y(t.x, t.y, inflate);
    }
    // Past the face's own texels is its margin (the inflation): the column continued round the
    // corner is a, the face's own edge column aEdge.
    bool margin = a < lo || a >= hi;
    float aEdge = clamp(a, lo + 0.5, hi - 0.5);
    bool inFace = !sides || (ay >= 20.0 * OVVAR_D && ay < 32.0 * OVVAR_D);

    if (kind.r < 0.5) {
        // Base garment: squeezed, the margin filled by the face's edge column (and row); the
        // mirrored limb reads the strip above.
        if (sides) ay = clamp(ay, 20.0 * OVVAR_D + 0.5, 32.0 * OVVAR_D - 0.5);
        return vec2(aEdge, ay) / OVVAR_TEX - vec2(0.0, mirrored ? 0.5 : 0.0);
    }
    if (!inFace) return OVVAR_BLANK;

    // A patch layer: what it shows at the continued column, and — in the margin — at the edge
    // column, from the same placement.
    vec2 here, edge;
    if (kind.r < 1.5) {
        // Placement: hide it on the limb it is not for.
        if (limb && ((kind.g > 0.5 && kind.g < 1.5 && mirrored) || (kind.g > 1.5 && !mirrored))) return OVVAR_BLANK;
        here = vec2(a, ay) / OVVAR_TEX;
        edge = vec2(aEdge, ay) / OVVAR_TEX;
    } else {
        // Preview: unrank the instant set from the dye colour (see Looks.rank), then look it up
        // in squeezed texels too.
        float designs = kind.b, m = kind.g * designs;
        float v = ovvar_bits();
        if (v < 0.5) return OVVAR_BLANK;
        float base3 = 1.0 + m + ovvar_c2(m), base2 = 1.0 + m;
        float r;
        if (v >= base3) {
            ovvar_set_count = 3; r = v - base3;
            ovvar_set[2] = ovvar_max_c3(r); r -= ovvar_c3(ovvar_set[2]);
            ovvar_set[1] = ovvar_max_c2(r); r -= ovvar_c2(ovvar_set[1]);
            ovvar_set[0] = r;
        } else if (v >= base2) {
            ovvar_set_count = 2; r = v - base2;
            ovvar_set[1] = ovvar_max_c2(r); r -= ovvar_c2(ovvar_set[1]);
            ovvar_set[0] = r;
        } else {
            ovvar_set_count = 1; ovvar_set[0] = v - 1.0;
        }
        int hereWhich, edgeWhich = -1;
        here = ovvar_preview_at(designs, vec2(a, ay), limb, mirrored, hereWhich);
        edge = margin ? ovvar_preview_at(designs, vec2(aEdge, ay), limb, mirrored, edgeWhich) : here;
        if (margin && hereWhich != edgeWhich) return OVVAR_BLANK;   // not one placement across the corner
    }
    if (!margin) return here;
    // The margin belongs to no texel, so a patch is left to the fabric there — unless it is one
    // patch continuing round the corner (a big one hanging over), which shows its edge column
    // stretched across the margin rather than a sliver of what lies round the corner, or a cut.
    return ovvar_opaque(edge) && ovvar_opaque(here) ? edge : OVVAR_BLANK;
}

// Trim patches (drawn by the game from its trim atlas, so ovvar_uv cannot place them) carry their
// side in the texture's alpha: 254 = right limb only, 253 = left limb only. Call after ovvar_uv
// with the sampled colour; the wrong limb's fragments come back with alpha 0 (cut out).
vec4 ovvar_sided(vec4 sampled) {
    float a = floor(sampled.a * 255.0 + 0.5);
    if ((a == 254.0 && ovvar_handed) || (a == 253.0 && !ovvar_handed)) return vec4(sampled.rgb, 0.0);
    return sampled;
}

// The vertex colour to shade with: on a patch texture of ours the dye colour is data, not paint,
// so it is divided out of the lit colour (no byte of it is 0); on a trim patch the wrong limb's
// fragments get alpha 0 so the program's alpha test drops them. uv: the coordinate being sampled.
vec4 ovvar_shade(vec4 lit, vec2 uv) {
    if (ovvar_sided(OVVAR_SAMPLE(uv)).a == 0.0 && OVVAR_SAMPLE(uv).a > 0.0) return vec4(lit.rgb, 0.0);
    if (ovvar_patch_layer() < 0.5) return lit;
    return vec4(lit.rgb / max(ovvar_color.rgb, vec3(1.0 / 255.0)), lit.a);
}
