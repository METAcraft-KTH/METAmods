#version 330
#extension GL_ARB_separate_shader_objects : require

// metacraft-shaders: vanilla's entity vertex shader (26.3), plus the inputs our mods' entity
// effects need (see entity.fsh): the raw vertex colour, position and normal go to entity.fsh,
// and an ovvar patch layer is lit as white, since its dye colour is data and must never tint the art.

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler1;
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
#else
layout(location = 2) out vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) out vec4 overlayColor;
#endif

layout(location = 6) out vec2 texCoord0;
#ifdef GLINT
layout(location = 7) out vec2 texCoordGlint;
#endif

// Shared by every effect in entity.fsh. A new one that needs more takes location 11 or above.
layout(location = 8) out vec4 entityColor;
layout(location = 9) out vec3 entityPos;
layout(location = 10) out vec3 entityNormal;

uniform sampler2D Sampler0;

// Ovvar: only its constants and ovvar_patch_layer; the rest of ovvar.glsl is the fragment's.
#define OVVAR_VERTEX
#define OVVAR_SAMPLE(uv) texture(Sampler0, uv)
#define ovvar_color Color
#define ovvar_pos Position
#define ovvar_normal Normal
#include <ovvar:ovvar.glsl>

// Every ovvar texture is OVVAR_TEX in size; nothing else pays for its marker reads.
bool ovvar_texture() {
    return textureSize(Sampler0, 0) == ivec2(OVVAR_TEX);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    entityColor = Color;
    entityPos = Position;
    entityNormal = Normal;
    vec4 litColor = ovvar_texture() ? mix(Color, vec4(1.0), ovvar_patch_layer()) : Color;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, Normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, litColor);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, litColor);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = litColor;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, litColor);
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
    lightMapColor = sample_lightmap(Sampler2, UV2);
#endif

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

    texCoord0 = UV0;

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif

#ifdef GLINT
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
