// Vanilla 26.3 item.vsh with one addition (metacraft-shaders, shared by every rift drawn with item displays):
// a vertex whose tint is a rift marker gets the part as riftFlag, its stage as riftStage and a corner index
// for its place on the quad. Markers: void anchor #FEFEFD crack (1), #FEFEFC glowing crack (2), #FEFEFB
// core (3); nether rift #FE{G}{B} with B FA body (11) / F9 edge (12) and G FD/FC/FB stage 1/2/3. A nether
// rift's vertex also passes on where it is: riftWorld, its world position less CameraBlockPos (item vertices
// arrive relative to the camera, and the camera is at CameraBlockPos - CameraOffset, so the world position is
// Position - CameraOffset + CameraBlockPos; the fragment adds the whole blocks back as integers, exactly),
// and riftView, its position relative to the camera, for the view direction.
// Re-diff against vanilla on every Minecraft update. 26.3 compiles to SPIR-V, hence gl_VertexIndex.
#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif
layout(location = 2) out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
#endif

layout(location = 5) out vec2 texCoord0;
layout(location = 7) out vec2 riftUv;
layout(location = 8) out float riftFlag;
layout(location = 9) out float riftStage;
layout(location = 10) out vec3 riftWorld;
layout(location = 11) out vec3 riftView;
#ifdef GLINT
layout(location = 6) out vec2 texCoordGlint;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    #endif
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    #endif

    texCoord0 = UV0;

    vec3 tint = floor(Color.rgb * 255.0 + 0.5);
    // Quads are drawn as four vertices in order round the face; which one this is gives its corner.
    int corner = gl_VertexIndex % 4;
    vec2 cornerUv = vec2((corner == 1 || corner == 2) ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
    riftFlag = 0.0;
    riftStage = 0.0;
    riftUv = vec2(0.0);
    riftWorld = vec3(0.0);
    riftView = vec3(0.0);
    if (tint.r == 254.0 && tint.g == 254.0 && tint.b >= 251.0 && tint.b <= 253.0) {
        riftFlag = 254.0 - tint.b;
        riftUv = cornerUv;
    } else if (tint.r == 254.0 && tint.g >= 251.0 && tint.g <= 253.0 && tint.b >= 249.0 && tint.b <= 250.0) {
        riftFlag = 11.0 + (250.0 - tint.b);
        riftStage = 254.0 - tint.g;
        riftUv = cornerUv;
        riftWorld = Position - CameraOffset;
        riftView = Position;
    }
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif
}
