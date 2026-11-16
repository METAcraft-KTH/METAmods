uniform sampler2D texture;

#ifdef FSH

varying vec2 texCoord;
varying vec4 color;

void main() {
	vec4 albedo = texture2D(texture, texCoord) * color;
	gl_FragData[0] = albedo;
}

#endif

#ifdef VSH

varying vec2 texCoord;
varying vec4 color;

void main() {
	texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	color = gl_Color;
	gl_Position = ftransform();
}

#endif
