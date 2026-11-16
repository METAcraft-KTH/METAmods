#version 330 compatibility

uniform sampler2D gtexture;

#ifdef STAGE_FS

in vec2 texCoord;
in vec4 glColor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 colorOut;

void main() {
	vec4 albedo = texture(gtexture, texCoord) * glColor;
	colorOut = albedo;
}

#endif

#ifdef STAGE_VS

out vec2 texCoord;
out vec4 glColor;

void main() {
	gl_Position = ftransform();
	texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glColor = gl_Color;
}

#endif
