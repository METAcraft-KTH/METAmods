#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 viewPos;
layout(location = 6) in vec3 chunkPos;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

/** Texels to a block: the grid the paint's border, wobble and highlights are all snapped to. */
const float TEXELS = 16.0;
/** Wobble amplitude, in blocks. Above 1/TEXELS, or it cannot move the quantised edge at all. */
const float WOBBLE = 0.07;

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 tex = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize));
    vec4 color = tex * vertexColor;
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    // RIVALS_GLOSS: paint texels carry alpha 235/255 = 0.9216 as a marker; the window admits 233..237
    // (five steps, filtering tolerance) and no vanilla block texture has an alpha anywhere in it — the
    // nearest values that exist are 232 (nether_portal) and 238 (frosted_ice). The old window around 229
    // admitted 228..230, which nether_portal (226..232) and seventeen *_stained_glass_pane_top (230) hit:
    // those fragments came in here, decoded connection bits out of their red channel and could discard.
    // Derivatives are only defined in uniform control flow: take them before the paint branch.
    vec3 chunkDx = dFdx(chunkPos), chunkDy = dFdy(chunkPos);
    vec3 viewDx = dFdx(viewPos), viewDy = dFdy(viewPos);
    if (abs(tex.a - 0.9216) < 0.008) {
        // Connected paint (spec §5). The texel is one flat colour whose red low nibble says which of the
        // cell's four in-plane neighbours are painted; the face normal (from chunkPos) picks the two
        // in-plane axes, and a rounded box — full on connected sides, inset and rounded on the others —
        // decides whether this fragment is paint at all. Inside it: a meniscus rim from the same distance,
        // a three-wave crawling normal, and glint / sheen / fresnel that mix toward light instead of adding.
        int bits = int(mod(floor(tex.r * 255.0 + 0.5), 16.0));
        vec3 nc = abs(normalize(cross(chunkDx, chunkDy)));
        vec2 p;
        vec2 along;
        if (nc.y >= nc.x && nc.y >= nc.z) { p = chunkPos.xz; }
        else if (nc.x >= nc.z) { p = chunkPos.zy; }
        else { p = chunkPos.xy; }
        along = p;
        p = fract(p);
        // Pixel art, not vector art: everything the border and the look are computed from is snapped to
        // the centre of its 1/16-block texel, so the discard decision is taken once per texel (corners
        // come out stepped rather than smooth), the wobble moves in whole-texel steps, and the
        // highlights are blocky too. Time stays continuous — texels flip, they never slide. The radius
        // (0.28) and the inset (0.06) stay in block units: about 4.5 and 1 texels.
        p = (floor(p * TEXELS) + 0.5) / TEXELS;
        along = (floor(along * TEXELS) + 0.5) / TEXELS;
        vec3 gridPos = (floor(chunkPos * TEXELS) + 0.5) / TEXELS;
        float t = GameTime * 1200.0;
        bool negU = (bits & 1) != 0, posU = (bits & 2) != 0, negV = (bits & 4) != 0, posV = (bits & 8) != 0;
        // Inset each unconnected side by 0.06 plus a slow wobble; connected sides run out past the cell.
        // The amplitude has to clear a whole texel (1/TEXELS = 0.0625) or the quantised edge below never
        // moves: 0.02 kept the threshold inside the texel it started in, so a straight edge sat perfectly
        // still. At 0.07 the inset swings between none and two texels and the border crawls.
        float w0 = WOBBLE * sin(along.y * 12.0 + t * 1.1), w1 = WOBBLE * sin(along.y * 12.0 + 2.0 + t * 0.9);
        float w2 = WOBBLE * sin(along.x * 12.0 + 4.0 + t * 1.3), w3 = WOBBLE * sin(along.x * 12.0 + 1.0 + t * 0.8);
        float lo_u = negU ? -1.0 : 0.06 + w0, hi_u = posU ? 2.0 : 0.94 + w1;
        float lo_v = negV ? -1.0 : 0.06 + w2, hi_v = posV ? 2.0 : 0.94 + w3;
        vec2 centre = vec2(lo_u + hi_u, lo_v + hi_v) * 0.5;
        vec2 halfSize = vec2(hi_u - lo_u, hi_v - lo_v) * 0.5;
        // Corner radius only where both sides meeting at that corner are unconnected.
        bool cu = p.x < centre.x ? !negU : !posU;
        bool cv = p.y < centre.y ? !negV : !posV;
        float r = (cu && cv) ? 0.28 : 0.0;
        vec2 q = abs(p - centre) - halfSize + r;
        float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        if (d > 0.0) discard;
        vec3 n = normalize(cross(viewDx, viewDy));
        vec3 v = normalize(-viewPos);
        vec3 l = normalize(vec3(0.3 + 0.15 * sin(t), 0.8, 0.5 + 0.15 * cos(t)));
        vec3 wave = vec3(
            sin(gridPos.x * 6.0 + t * 0.9) * 0.4 + sin((gridPos.x + gridPos.z) * 11.0 + t * 1.4) * 0.35 + sin(gridPos.z * 17.0 - t * 2.3) * 0.25,
            0.0,
            cos(gridPos.z * 6.0 + t * 0.9) * 0.4 + cos((gridPos.z - gridPos.x) * 11.0 + t * 1.4) * 0.35 + cos(gridPos.x * 17.0 - t * 2.3) * 0.25);
        n = normalize(n + 0.05 * wave);
        vec3 lightened = mix(color.rgb, vec3(1.0), 0.35);
        // The meniscus: the outer 0.08 of the shape is a bevel, lit on the side facing the light.
        float rim = smoothstep(-0.08, 0.0, d);
        vec2 edgeDir = normalize(p - centre + vec2(0.0001));
        float facing = dot(edgeDir, normalize(vec2(l.x, l.z)));
        color.rgb = mix(color.rgb, lightened, 0.30 * rim * max(facing, 0.0));
        color.rgb *= 1.0 - 0.12 * rim * max(-facing, 0.0);
        float glint = pow(max(dot(reflect(-l, n), v), 0.0), 60.0) * 0.55;
        float sheen = pow(max(dot(reflect(-l, n), v), 0.0), 5.0) * 0.14;
        float fresnel = pow(1.0 - max(dot(n, v), 0.0), 4.0) * 0.18;
        color.rgb = mix(color.rgb, vec3(1.0), glint);
        color.rgb = mix(color.rgb, lightened, sheen);
        color.rgb = mix(color.rgb, lightened, fresnel);
        color.a = 1.0;
    }

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
