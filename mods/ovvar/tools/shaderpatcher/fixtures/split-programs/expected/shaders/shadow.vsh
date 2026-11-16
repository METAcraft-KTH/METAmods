#version 120

varying vec2 texcoord;

// --- Metacraft rifts: a rift casts no shadow
void metacraft_rift_shadow_main();
void main() {
	metacraft_rift_shadow_main();
	vec3 tint = floor(gl_Color.rgb * 255.0 + 0.5);
	if (tint.r == 254.0 && ((tint.g == 254.0 && tint.b >= 251.0 && tint.b <= 253.0)
			|| (tint.g >= 251.0 && tint.g <= 253.0 && tint.b >= 249.0 && tint.b <= 250.0))) {
		gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
	}
}

void metacraft_rift_shadow_main() {
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}
