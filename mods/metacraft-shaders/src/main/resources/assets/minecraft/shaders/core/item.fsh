// Vanilla 26.3 item.fsh with one addition (metacraft-shaders): where item.vsh flagged a rift, the painted
// sprite is replaced by that rift's drawing (void anchor: include void_rift; nether rift: include
// nether_rift), and a nether rift skips the alpha cutout. Every other item takes the vanilla path. The Globals include is unconditional (vanilla
// guards it with GLINT) because the rifts need GameTime, ScreenSize and CameraBlockPos. Re-diff against
// vanilla on every Minecraft update.
#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
layout(location = 7) in vec2 riftUv;
layout(location = 8) in float riftFlag;
layout(location = 9) in float riftStage;
layout(location = 10) in vec3 riftWorld;
layout(location = 11) in vec3 riftView;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color) {
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);// Glint color modulator?
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
#endif

#include <metacraft:rift_common.glsl>
#include <metacraft:void_rift.glsl>
#include <metacraft:nether_rift.glsl>

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    #ifdef ALPHA_CUTOUT
    // A nether rift's panel sprite is all but transparent (so shaderpacks that replace this shader draw none of
    // it); the rift draws over it whatever its alpha.
    if (color.a < ALPHA_CUTOUT && riftFlag < 10.5) {
        discard;
    }
    #endif

    color *= vertexColor * ColorModulator;

    if (riftFlag > 11.5) {
        color = netherRiftEdge(riftStage);
    } else if (riftFlag > 10.5) {
        color = netherRiftBody(riftStage);
    } else if (riftFlag > 2.5) {
        color = riftCore();
    } else if (riftFlag > 1.5) {
        color = riftGlow(texture(Sampler0, texCoord0));
    } else if (riftFlag > 0.5) {
        color = riftColor(texture(Sampler0, texCoord0));
    }
    // Nothing to draw: leave no depth behind either, or a rift's empty corners hide its other parts.
    if (riftFlag > 0.5 && color.a < 0.02) {
        discard;
    }

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
