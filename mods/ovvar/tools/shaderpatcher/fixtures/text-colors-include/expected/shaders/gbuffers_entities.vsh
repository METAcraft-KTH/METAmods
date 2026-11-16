#version 120

varying vec2 texcoord;
varying vec4 glcolor;

// --- Metacraft chapter text colours (metacraft.se): nametags in the chapters' colours, as the
// chapter-text-colors resource pack draws them without a shaderpack
// Chapter text colours: text in dark purple draws in cerise, text in dark blue in laserviolet.
// These colours mirror the chapter-text-colors resource pack (its assets/minecraft/shaders/core/text.fsh; a copy
// is in tools/shaderpatcher/test-packs/chapter-text-colors). Change both together: PatcherRunTest checks they
// agree with the copy, and the patch stamp follows this file, so a change re-patches everyone's packs.
// Exact matches only, on the raw vertex colour, as the pack does; every other colour passes through.
vec4 metacraft_text_tint(vec4 color) {
	uvec3 raw = uvec3(color.rgb * 255.0 + 0.5);
	if (raw == uvec3(170u, 0u, 170u)) return vec4(0.909803921569, 0.239215686275, 0.517647058824, color.a);
	if (raw == uvec3(0u, 0u, 170u)) return vec4(0.8, 0.6, 1.0, color.a);
	return color;
}
vec4 metacraft_text_gl_Color() { return metacraft_text_tint(gl_Color); }
// --- end chapter text colours

// --- Ovvar: the raw vertex colour (an ovve's dye colour holds its patch bits), position and normal
varying vec4 ovvar_color;
varying vec3 ovvar_pos;
varying vec3 ovvar_normal;

// --- Metacraft rifts: the tint marker, the quad corner, the raw sprite coordinate and, for a nether
// rift, where the vertex is: relative to the camera, and in the world less the camera's whole blocks
varying vec2 metacraft_rift_corner;
varying vec2 metacraft_rift_tex;
varying float metacraft_rift_flag;
varying float metacraft_rift_stage;
varying vec3 metacraft_rift_world;
varying vec3 metacraft_rift_view;
uniform vec3 metacraftRiftCameraFract;




void main() {
	{
		vec3 tint = floor(gl_Color.rgb * 255.0 + 0.5);
		int corner = gl_VertexID - 4 * (gl_VertexID / 4);
		vec2 cornerUv = vec2((corner == 1 || corner == 2) ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
		metacraft_rift_tex = gl_MultiTexCoord0.st;
		metacraft_rift_flag = 0.0;
		metacraft_rift_stage = 0.0;
		metacraft_rift_corner = vec2(0.0);
		metacraft_rift_world = vec3(0.0);
		metacraft_rift_view = vec3(0.0);
		if (tint.r == 254.0 && tint.g == 254.0 && tint.b >= 251.0 && tint.b <= 253.0) {
			metacraft_rift_flag = 254.0 - tint.b;
			metacraft_rift_corner = cornerUv;
		} else if (tint.r == 254.0 && tint.g >= 251.0 && tint.g <= 253.0 && tint.b >= 249.0 && tint.b <= 250.0) {
			metacraft_rift_flag = 11.0 + (250.0 - tint.b);
			metacraft_rift_stage = 254.0 - tint.g;
			metacraft_rift_corner = cornerUv;
			metacraft_rift_view = gl_Vertex.xyz;
			metacraft_rift_world = gl_Vertex.xyz + metacraftRiftCameraFract;
		}
	}

	ovvar_color = gl_Color;
	ovvar_pos = gl_Vertex.xyz;
	ovvar_normal = gl_Normal;

#define gl_Color metacraft_text_gl_Color()
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	#include "/lib/vertex_color.glsl"
}
