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
// garment's edge columns stretch into the margin that leaves, patches leave it to the fabric.
// Which layer a texture is for is in the layer texel, two left of the marker: R = 2·inflate.
float ovvar_inflate() {
    return ovvar_read(OVVAR_MARKER.x - 2.0, OVVAR_MARKER.y).r * 0.5;
}

// The face a column of the side rows belongs to, in skin texels: (start, width). Legs and arms
// have four 4-wide faces; the body has right 4 | front 8 | left 4 | back 8.
vec2 ovvar_face(float skinX) {
    if (skinX < 16.0) return vec2(floor(skinX / 4.0) * 4.0, 4.0);
    if (skinX < 20.0) return vec2(16.0, 4.0);
    if (skinX < 28.0) return vec2(20.0, 8.0);
    if (skinX < 32.0) return vec2(28.0, 4.0);
    if (skinX < 40.0) return vec2(32.0, 8.0);
    return vec2(40.0 + floor((skinX - 40.0) / 4.0) * 4.0, 4.0);
}

// The pixel every layer on a part is drawn at, in model units: the legs take the leggings
// layer's (inflate 0.5) so what the boots pass draws on them (inflate 1) lands on the same grid;
// the body and arms take their own layer's.
float ovvar_pixel(float skinX, float inflate) {
    return skinX < 16.0 ? 13.0 / 12.0 : (12.0 + 2.0 * inflate) / 12.0;
}

// Scale in x that makes a face's texels come out ovvar_pixel wide.
float ovvar_squeeze(float skinX, float faceWidth, float inflate) {
    return ovvar_pixel(skinX, inflate) / ((faceWidth + 2.0 * inflate) / faceWidth);
}

// A fragment's texel x on the side rows → the texel x to draw there (its face's art squeezed
// about the face centre). Outside [face start, face end) the fragment is in the margin.
float ovvar_squeezed(float tx, float inflate) {
    vec2 f = ovvar_face(tx / OVVAR_D) * OVVAR_D;
    float c = f.x + f.y * 0.5;
    return (tx - c) / ovvar_squeeze(tx / OVVAR_D, f.y / OVVAR_D, inflate) + c;
}

// The same in y, about the side rows' centre: only the boots pass on the legs needs it (its rows
// are 14/12 tall, the leggings' 13/12).
float ovvar_squeezed_y(float tx, float ty, float inflate) {
    float sy = ovvar_pixel(tx / OVVAR_D, inflate) / ((12.0 + 2.0 * inflate) / 12.0);
    float c = 26.0 * OVVAR_D;
    return (ty - c) / sy + c;
}

bool ovvar_in_face(float a, float tx) {
    vec2 f = ovvar_face(tx / OVVAR_D) * OVVAR_D;
    return a >= f.x && a < f.x + f.y;
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
    float a = sides ? ovvar_squeezed(t.x, inflate) : t.x;
    float ay = sides ? ovvar_squeezed_y(t.x, t.y, inflate) : t.y;
    bool inFace = !sides || (ovvar_in_face(a, t.x) && ay >= 20.0 * OVVAR_D && ay < 32.0 * OVVAR_D);

    if (kind.r < 0.5) {
        // Base garment: squeezed, the margin filled by the face's edge column; the mirrored limb
        // reads the strip above.
        if (sides && !inFace) {
            vec2 f = ovvar_face(t.x / OVVAR_D) * OVVAR_D;
            a = clamp(a, f.x + 0.5, f.x + f.y - 0.5);
            ay = clamp(ay, 20.0 * OVVAR_D + 0.5, 32.0 * OVVAR_D - 0.5);
        }
        return vec2(a, ay) / OVVAR_TEX - vec2(0.0, mirrored ? 0.5 : 0.0);
    }

    if (kind.r < 1.5) {
        // Placement: hide it on the limb it is not for; squeezed, the margin left to the fabric.
        if (limb && ((kind.g > 0.5 && kind.g < 1.5 && mirrored) || (kind.g > 1.5 && !mirrored))) return OVVAR_BLANK;
        if (!inFace) return OVVAR_BLANK;
        return vec2(a, ay) / OVVAR_TEX;
    }
    if (!inFace) return OVVAR_BLANK;
    t = vec2(a, ay);   // the preview is looked up in squeezed texels too

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
    for (int i = 0; i < 3; i++) {
        if (i >= count) break;
        float cell = floor(s[i] / designs), design = s[i] - cell * designs;
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
        return (pe.rg + vec2(column * OVVAR_CELL, 0.0) + local) / OVVAR_TEX;
    }
    return OVVAR_BLANK;
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
