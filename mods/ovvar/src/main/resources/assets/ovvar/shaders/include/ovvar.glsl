// Ovvar: patches on student overalls, finished by the shader. Shared by the vanilla entity.fsh
// override and (copied in by the shaderpack patcher) by shaderpacks' entity programs, so it is
// written against GLSL 120: no integer bit operations, no texelFetch — texels are read at their
// centres with OVVAR_SAMPLE, and bit fields are pulled out with floating-point arithmetic.
//
// A texture of ours is 64×32 with a marker texel at (63,15): magenta, alpha 2/255. The texel at
// (62,15) says what it is (R):
//   0  a base garment texture. The model draws the left arm and leg as mirror images off the right
//      limb's strips; on those fragments the shader samples one strip up, where the left-side art
//      lives.
//   1  a placement texture: one patch drawn on one cell, for one side (G: 0 body, 1 right limb,
//      2 left limb). Fragments of the other limb read the blank texel.
//   2  the preview texture: every patch's art in a library (head rows), a cell table at x 40–43
//      (index in the half → u, v, side; column-major, 16 tall) and a design table at x 44–47
//      (design → library x, y, cells); G = cells in the half, B = designs. The garment's dye
//      colour carries up to three placements as the rank of their set among all sets of
//      (cell × designs + design) states, after all smaller sets (0 = none) — packed as three
//      base-255 digits, each byte one more than its digit, so no byte is ever 0. Everything else
//      reads the blank texel at (63,14).
//
// Requires before inclusion: OVVAR_SAMPLE(uv) — the albedo sample of this program;
// ovvar_color — the raw (unlit) vertex colour, vec4; ovvar_pos and ovvar_normal — the vertex
// position and normal (any one space for both).

vec4 ovvar_read(float x, float y) {
    return floor(OVVAR_SAMPLE(vec2((x + 0.5) / 64.0, (y + 0.5) / 32.0)) * 255.0 + 0.5);
}

const vec2 OVVAR_BLANK = vec2(63.5 / 64.0, 14.5 / 32.0);
// Which sign of texture-over-geometry handedness the mirrored limbs have. Fixed by how the game
// builds its vertex data (the same on every platform); calibrated once against a known garment.
const bool OVVAR_MIRROR_SENSE = true;

bool ovvar_marked() {
    return all(equal(ovvar_read(63.0, 15.0), vec4(255.0, 0.0, 255.0, 2.0)));
}

// 1.0 on a patch texture of ours (placement or preview), else 0.0.
float ovvar_patch_layer() {
    return ovvar_marked() && ovvar_read(62.0, 15.0).r > 0.5 ? 1.0 : 0.0;
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

    vec4 kind = ovvar_read(62.0, 15.0);
    vec2 t = uv * vec2(64.0, 32.0);   // texel coordinates
    bool limb = t.y >= 16.0 && (t.x < 16.0 || (t.x >= 40.0 && t.x < 56.0));
    bool mirrored = limb && ovvar_handed;

    if (kind.r < 0.5) {
        // Base garment: the mirrored limb reads the strip above.
        return mirrored ? uv - vec2(0.0, 0.5) : uv;
    }

    if (kind.r < 1.5) {
        // Placement: hide it on the limb it is not for.
        if (limb && ((kind.g > 0.5 && kind.g < 1.5 && mirrored) || (kind.g > 1.5 && !mirrored))) return OVVAR_BLANK;
        return uv;
    }

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
        vec4 ce = ovvar_read(40.0 + floor(cell / 16.0), mod(cell, 16.0));       // u, v, side
        vec4 pe = ovvar_read(44.0 + floor(design / 16.0), mod(design, 16.0));   // library x, y, cells
        vec2 local = t - ce.rg;
        if (local.x < 0.0 || local.x >= 4.0 || local.y < 0.0 || local.y >= 4.0) continue;
        float side = ce.b;
        float column = 0.0;   // which 4×4 of the art
        bool flip = false;
        if (side < 0.5) { if (limb) continue; }
        else if (side < 1.5) { if (!limb || mirrored) continue; }
        else if (side < 2.5) { if (!limb || !mirrored) continue; flip = true; }
        else { if (!limb) continue; if (mirrored) { column = 1.0; flip = true; } }   // seat: one half per leg
        if (flip) local.x = 4.0 - local.x;   // the model mirrors the left limb; mirror back
        return (pe.rg + vec2(column * 4.0, 0.0) + local) / vec2(64.0, 32.0);
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
