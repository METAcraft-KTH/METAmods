#include "/lib/common.glsl"

in vec2 texcoord;
in vec4 glcolor;

void main() {
	vec4 albedo = texture(gtexture, texcoord) * glcolor;
	gl_FragData[0] = albedo;
}
