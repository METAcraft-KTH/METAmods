#version 330

// Ovvar: vanilla's entity fragment shader plus asymmetric limbs for our armour layers.
//
// The humanoid model builds the left arm and left leg as mirror images of the right ones, sharing
// their texture strips, so nothing drawn on a sleeve or leg can differ between sides. A mirror
// image has the opposite texture-mapping handedness, though, and that is measurable per fragment:
// the sign of the UV Jacobian in screen space, corrected for which side of the face we see. On our
// textures (marked with a magic texel) fragments of mirrored limbs sample the arm and leg boxes
// from the otherwise unused top strip instead, where datagen puts the left-side art. Textures
// that are not ours, and clients whose core shaders are replaced (Iris, OptiFine), get vanilla
// behaviour: the strip is simply never sampled and both sides look alike.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
in vec4 overlayColor;
#endif

in vec2 texCoord0;

out vec4 fragColor;

// Bottom-right texel of the free strip: magenta at alpha 2/255, which no art uses.
const ivec2 OVVAR_MARKER = ivec2(63, 15);
const vec4 OVVAR_MAGIC = vec4(1.0, 0.0, 1.0, 2.0 / 255.0);

vec2 ovvar_remap(vec2 uv) {
    // Derivatives first, in uniform control flow, where they are defined.
    vec2 du = dFdx(uv), dv = dFdy(uv);
    float det = du.x * dv.y - dv.x * du.y;

    if (textureSize(Sampler0, 0) != ivec2(64, 32)) return uv;
    vec4 marker = texelFetch(Sampler0, OVVAR_MARKER, 0);
    if (any(greaterThan(abs(marker - OVVAR_MAGIC), vec4(0.5 / 255.0)))) return uv;

    // Right arm 40..56 and right leg 0..16, rows 16..32; their mirrors live directly above.
    bool limb = uv.y >= 0.5 && (uv.x < 16.0 / 64.0 || (uv.x >= 40.0 / 64.0 && uv.x < 56.0 / 64.0));
    if (!limb) return uv;

    // Unmirrored faces seen from outside map u left-to-right and v top-to-bottom on screen: det < 0.
    bool mirrored = (det > 0.0) == gl_FrontFacing;
    return mirrored ? uv - vec2(0.0, 0.5) : uv;
}

void main() {
    vec4 color = texture(Sampler0, ovvar_remap(texCoord0));
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
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= lightMapColor;
#endif

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
