#version 330

// Ovvar: vanilla's entity fragment shader with the albedo sample routed through ovvar.glsl,
// which draws patches on student overalls and un-mirrors their left limbs. Textures that are
// not ours are sampled exactly as vanilla does; clients whose core shaders are replaced (Iris,
// OptiFine) get vanilla behaviour and see the garments without patches.

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
in vec4 ovvar_color;
in vec3 ovvar_pos;
in vec3 ovvar_normal;

out vec4 fragColor;

#define OVVAR_SAMPLE(uv) texture(Sampler0, uv)
#moj_import <ovvar:ovvar.glsl>

void main() {
	vec4 color = texture(Sampler0, ovvar_uv(texCoord0));
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
