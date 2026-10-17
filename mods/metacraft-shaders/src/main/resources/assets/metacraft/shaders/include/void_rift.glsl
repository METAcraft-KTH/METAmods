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
