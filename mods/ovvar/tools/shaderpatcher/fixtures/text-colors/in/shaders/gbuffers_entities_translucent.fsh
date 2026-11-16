#version 330 core

uniform sampler2D gtexture;

in vec2 texCoord;
in vec4 tint;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 colorOut;

void main() {
	colorOut = texture(gtexture, texCoord) * tint;
}
