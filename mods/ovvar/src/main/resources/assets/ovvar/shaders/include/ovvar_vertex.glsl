// Ovvar, vertex side (see ovvar.glsl). A patch layer of ours must not be tinted by its dye colour
// — that colour is data, not paint — so the vertex shader replaces the colour it lights with
// white on those layers and passes the raw colour separately for the fragment shader to decode.
// GLSL 120 compatible; requires OVVAR_SAMPLE(uv), the program's albedo sampler.

// Keep in step with ovvar.glsl: texels per skin texel, texture size, marker texel.
const vec2 OVVAR_VTEX = vec2(64.0, 32.0) * 2.0;
const vec2 OVVAR_VMARKER = vec2(OVVAR_VTEX.x - 1.0, OVVAR_VTEX.y * 0.5 - 1.0);

vec4 ovvar_vread(float x, float y) {
    return floor(OVVAR_SAMPLE((vec2(x, y) + 0.5) / OVVAR_VTEX) * 255.0 + 0.5);
}

// 1.0 on a patch layer of ours, else 0.0.
float ovvar_patch_layer() {
    if (!all(equal(ovvar_vread(OVVAR_VMARKER.x, OVVAR_VMARKER.y), vec4(255.0, 0.0, 255.0, 2.0)))) return 0.0;
    return ovvar_vread(OVVAR_VMARKER.x - 1.0, OVVAR_VMARKER.y).r > 0.5 ? 1.0 : 0.0;
}
