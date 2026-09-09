// Ovvar: patches on student overalls, drawn by the shader. Shared by the vanilla entity.fsh
// override and (copied in by the shaderpack patcher) by shaderpacks' entity programs, so it is
// written against GLSL 120: no integer bit operations, no texelFetch — texels are read at their
// centres with OVVAR_SAMPLE, and bit fields are pulled out with floating-point arithmetic.
//
// A texture of ours is 64×32 with a marker texel at (63,15): magenta, alpha 2/255. The texel
// at (62,15) says what the texture is: G = 0 → a base garment texture, whose left arm and leg —
// mirror images the model draws off the right limb's strips — sample their boxes one strip up,
// where the left-side art lives. G > 0 → a patch layer: R = bit offset, G = bit count, B = cell
// count; texels (61,15), (60,15), ... hold its cells (R = u, G = v, B = side: 0 body, 1 right
// limb, 2 left limb). The garment's dye colour carries the patch bits (three base-255 digits,
// each byte one more than its digit, so no byte is ever 0); this layer's field value is read
// out of them and, if non-zero, picks patch art from the library cells in the top strip (six
// columns from x = 16, then two from x = 56, four rows), drawn on the field's cells. Everywhere
// else a patch layer reads the blank texel at (63,14).
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

// 1.0 on a patch layer of ours, else 0.0.
float ovvar_patch_layer() {
    return ovvar_marked() && ovvar_read(62.0, 15.0).g > 0.5 ? 1.0 : 0.0;
}

vec2 ovvar_library(float index) {
    // entry index → library cell origin in texels
    if (index < 24.0) {
        float col = mod(index, 6.0);
        float row = floor(index / 6.0);
        return vec2(16.0 + col * 4.0, row * 4.0);
    }
    float j = index - 24.0;
    return vec2(56.0 + mod(j, 2.0) * 4.0, floor(j / 2.0) * 4.0);
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

    if (kind.g < 0.5) {
        // Base garment: the mirrored limb reads the strip above.
        return mirrored ? uv - vec2(0.0, 0.5) : uv;
    }

    // Patch layer. Which value does this field have?
    float offset = kind.r, bits = kind.g, cells = kind.b;
    float value = floor(mod(ovvar_bits() / exp2(offset), exp2(bits)));
    if (value < 0.5) return OVVAR_BLANK;

    float side = limb ? (mirrored ? 2.0 : 1.0) : 0.0;
    for (int i = 0; i < 4; i++) {
        if (float(i) >= cells) break;
        vec4 cell = ovvar_read(61.0 - float(i), 15.0);
        if (abs(cell.b - side) > 0.5) continue;
        vec2 local = t - cell.rg;
        if (local.x < 0.0 || local.x >= 4.0 || local.y < 0.0 || local.y >= 4.0) continue;
        if (side > 1.5) local.x = 4.0 - local.x;   // the model mirrors the left limb; mirror back
        vec2 origin = ovvar_library((value - 1.0) * cells + float(i));
        return (origin + local) / vec2(64.0, 32.0);
    }
    return OVVAR_BLANK;
}

// The vertex colour to shade a patch layer with: the dye colour is data, not paint, so it is
// divided out of the lit colour (no byte of it is 0). Other layers keep their colour.
vec4 ovvar_shade(vec4 lit) {
    if (ovvar_patch_layer() < 0.5) return lit;
    return vec4(lit.rgb / max(ovvar_color.rgb, vec3(1.0 / 255.0)), lit.a);
}
