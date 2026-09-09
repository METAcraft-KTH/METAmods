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
//      (index → u, v, side; column-major, 16 tall) and a patch table at x 44–47 (code → library
//      x, y, cells). The garment's dye colour carries two 11-bit slots (cell index in 6 bits,
//      patch code in 5), packed as three base-255 digits (each byte one more than its digit, so
//      no byte is ever 0); each slot's patch is drawn on its cell. Everything else reads the blank
//      texel at (63,14).
//
// Requires before inclusion: OVVAR_SAMPLE(uv) — the albedo sample of this program;
// ovvar_color — the raw (unlit) vertex colour, vec4.

vec4 ovvar_read(float x, float y) {
    return floor(OVVAR_SAMPLE(vec2((x + 0.5) / 64.0, (y + 0.5) / 32.0)) * 255.0 + 0.5);
}

const vec2 OVVAR_BLANK = vec2(63.5 / 64.0, 14.5 / 32.0);

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

// The texture coordinate to sample instead of uv. Call with the program's original coordinate;
// derivatives must be taken in uniform control flow, hence at the top.
vec2 ovvar_uv(vec2 uv) {
    vec2 du = dFdx(uv), dv = dFdy(uv);
    float det = du.x * dv.y - dv.x * du.y;
    if (!ovvar_marked()) return uv;

    vec4 kind = ovvar_read(62.0, 15.0);
    vec2 t = uv * vec2(64.0, 32.0);   // texel coordinates
    bool limb = t.y >= 16.0 && (t.x < 16.0 || (t.x >= 40.0 && t.x < 56.0));
    // Unmirrored faces seen from outside map u left-to-right and v top-to-bottom on screen: det < 0.
    bool mirrored = limb && ((det > 0.0) == gl_FrontFacing);

    if (kind.r < 0.5) {
        // Base garment: the mirrored limb reads the strip above.
        return mirrored ? uv - vec2(0.0, 0.5) : uv;
    }

    if (kind.r < 1.5) {
        // Placement: hide it on the limb it is not for.
        if (limb && ((kind.g > 0.5 && kind.g < 1.5 && mirrored) || (kind.g > 1.5 && !mirrored))) return OVVAR_BLANK;
        return uv;
    }

    // Preview: the slots in the dye colour.
    float bits = ovvar_bits();
    for (int i = 0; i < 2; i++) {
        float slot = floor(mod(bits / exp2(11.0 * float(i)), 2048.0));
        float cell = mod(slot, 64.0), patch = floor(slot / 64.0);
        if (cell < 0.5 || patch < 0.5) continue;
        vec4 ce = ovvar_read(40.0 + floor(cell / 16.0), mod(cell, 16.0));   // u, v, side
        vec4 pe = ovvar_read(44.0 + floor(patch / 16.0), mod(patch, 16.0)); // library x, y, cells
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

// The vertex colour to shade a patch texture with: the dye colour is data, not paint, so it is
// divided out of the lit colour (no byte of it is 0). Other textures keep their colour.
vec4 ovvar_shade(vec4 lit) {
    if (ovvar_patch_layer() < 0.5) return lit;
    return vec4(lit.rgb / max(ovvar_color.rgb, vec3(1.0 / 255.0)), lit.a);
}
