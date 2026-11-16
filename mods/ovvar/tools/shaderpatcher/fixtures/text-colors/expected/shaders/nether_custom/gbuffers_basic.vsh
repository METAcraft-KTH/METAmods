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


void main() {
#define gl_Color metacraft_text_gl_Color()
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
}
