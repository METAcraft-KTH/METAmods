#version 330
#extension GL_ARB_separate_shader_objects : require

// metacraft-shaders: vanilla's entity fragment shader (26.3), plus every effect our mods draw on
// entities. A pack holds one entity shader, so they all live here, each in an include of its own
// namespace, and no other module ships a vanilla shader (the build fails if one does). An effect
// only changes textures of its own — told apart by their size before any texel is read — and
// everything else is drawn exactly as vanilla draws it:
//   pirkko  a 64×65 player-head skin with its key texels is a ray-traced figure (pirkko.glsl);
//   ovvar   patches on student overalls, and their left limbs un-mirrored (ovvar.glsl).
// Clients whose core shaders are replaced (Iris, OptiFine) get vanilla behaviour.
//
// Adding one: its include, one call in main() (an effect that replaces the whole fragment goes
// before those that change what is sampled), and if it needs more than entityColor, entityPos and
// entityNormal, a new input at location 11 or above in both stages.

#include <minecraft:globals.glsl>   // vanilla's only #ifdef GLINT; the rainbow Pirkko reads GameTime
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>   // a Pirkko is lit by its own normal
#endif

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
#else
layout(location = 2) in vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) in vec4 overlayColor;
#endif

layout(location = 6) in vec2 texCoord0;
#ifdef GLINT
layout(location = 7) in vec2 texCoordGlint;
#endif

// From entity.vsh: the raw (unlit) vertex colour, and the vertex position and normal.
layout(location = 8) in vec4 entityColor;
layout(location = 9) in vec3 entityPos;
layout(location = 10) in vec3 entityNormal;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#include <pirkko:pirkko.glsl>

#define OVVAR_SAMPLE(uv) texture(Sampler0, uv)
#define ovvar_color entityColor
#define ovvar_pos entityPos
#define ovvar_normal entityNormal
#include <ovvar:ovvar.glsl>

// Every ovvar texture is OVVAR_TEX in size; nothing else pays for its marker reads.
bool ovvar_texture() {
    return textureSize(Sampler0, 0) == ivec2(OVVAR_TEX);
}

vec4 calculateFinalColor(vec4 color) {
    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif

    #ifndef EMISSIVE
    color *= lightMapColor;
    #endif

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);
    // Matches BlendFuntion.GLINT
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    // Pirkko: a whole fragment of its own (or none, where the ray misses the figure).
    vec4 pirkkoLit;
    if (pirkko_shade(pirkkoLit)) {
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        fragColor = calculateFinalColor(pirkkoLit * ColorModulator);
        #endif
        return;
    }

    // Ovvar: the coordinate to sample instead (derivatives inside: the size test is uniform).
    vec2 uv = ovvar_texture() ? ovvar_uv(texCoord0) : texCoord0;
    vec4 color = texture(Sampler0, uv);

    #ifdef OIT_ADDITIVE
    color.a = min(0.99, color.a);
    #endif

    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
    #else
    vec4 faceVertexColor = vertexColor;
    #endif

    #ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    // The dissolve effect entirely replaces translucency
    faceVertexColor.a = 1.0;
    #endif

    color *= faceVertexColor * ColorModulator;

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
