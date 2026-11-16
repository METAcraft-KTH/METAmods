out vec2 texcoord;
out vec4 glcolor;

// --- Ovvar: the raw vertex colour (an ovve's dye colour holds its patch bits), position and normal
out vec4 ovvar_color;
out vec3 ovvar_pos;
out vec3 ovvar_normal;

// --- Metacraft rifts: the tint marker, the quad corner, the raw sprite coordinate and, for a nether
// rift, where the vertex is: relative to the camera, and in the world less the camera's whole blocks
out vec2 metacraft_rift_corner;
out vec2 metacraft_rift_tex;
out float metacraft_rift_flag;
out float metacraft_rift_stage;
out vec3 metacraft_rift_world;
out vec3 metacraft_rift_view;
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

	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
}
