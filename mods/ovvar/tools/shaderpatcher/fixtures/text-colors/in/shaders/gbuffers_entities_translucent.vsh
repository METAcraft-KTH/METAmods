#version 330 core

in vec3 vaPosition;
in vec4 vaColor;
in vec2 vaUV0;

uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

out vec2 texCoord;
out vec4 tint;

void main() {
	gl_Position = projectionMatrix * modelViewMatrix * vec4(vaPosition, 1.0);
	texCoord = vaUV0;
	tint = vaColor;
}
