uniform sampler2D texture;

#ifdef STAGE_FS

varying vec2 texCoord;
varying vec4 color;

void main() {
	gl_FragData[0] = texture2D(texture, texCoord) * color;
}

#endif

#ifdef STAGE_VS

varying vec2 texCoord;
varying vec4 color;

void main() {
	texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	color = gl_Color;
	gl_Position = ftransform();
}

#endif
