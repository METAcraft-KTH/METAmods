#version 330

// Ovvar: vanilla's entity vertex shader, plus: the raw vertex colour goes to entity.fsh unlit
// (a garment's patch bits live in its dye colour), and patch layers are lit as white so that
// colour never tints the art. See ovvar.glsl.

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#moj_import <minecraft:light.glsl>
#endif
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

#ifndef NO_OVERLAY
uniform sampler2D Sampler1;
#endif

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif

#ifndef EMISSIVE
out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
out vec4 overlayColor;
#endif

out vec2 texCoord0;
out vec4 ovvar_color;

uniform sampler2D Sampler0;
#define OVVAR_SAMPLE(uv) texture(Sampler0, uv)
#moj_import <ovvar:ovvar_vertex.glsl>

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    ovvar_color = Color;
    vec4 ovvar_lit = mix(Color, vec4(1.0), ovvar_patch_layer());

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, Normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, ovvar_lit);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, ovvar_lit);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = ovvar_lit;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, ovvar_lit);
#endif

#ifndef EMISSIVE
    lightMapColor = sample_lightmap(Sampler2, UV2);
#endif

#ifndef NO_OVERLAY
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

    texCoord0 = UV0;

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
