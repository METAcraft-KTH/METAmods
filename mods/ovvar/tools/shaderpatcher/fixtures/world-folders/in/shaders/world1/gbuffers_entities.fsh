#version 120

uniform sampler2D texture;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
	vec4 albedo = texture2D(texture, texcoord) * glcolor;
	gl_FragData[0] = albedo;
}
