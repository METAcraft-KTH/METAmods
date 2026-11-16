// 1:1 port of PackPatch.java (the Java is the truth; the shared fixtures prove they agree).
// Runtime-agnostic: no imports, works on Map<string, Uint8Array> with TextDecoder/TextEncoder.
//
// Regex notes (Java vs JS): Java's \s is only [ \t\n\x0B\f\r] (S below), its '^' in (?m) mode is "after a
// line terminator, but not between \r and \n" (LS below), String.matches is a full match, and
// String.split drops trailing empty strings (harmless here: names are trimmed and compared non-empty).

export type Status = 'PATCHED' | 'NOT_A_SHADERPACK' | 'NOTHING_PATCHED' | 'PATCHED_COPY';
export interface Resources { ovvar: string; riftCommon: string; voidRift: string; netherRift: string; textColors: string; stamp: string }

const STAMP_FILE = 'ovvar-patch.properties';
const TAG = 'ovvar_uv';
const RIFT_TAG = 'metacraft_rift_sample';
const TEXT_TAG = 'metacraft_text_tint';
// The program Iris draws world text (nametags) with, then its fallbacks: see TEXT_PROGRAMS in PackPatch.java.
const TEXT_PROGRAMS = ['gbuffers_entities_translucent', 'gbuffers_entities', 'gbuffers_textured_lit', 'gbuffers_textured', 'gbuffers_basic'];
const COMMENT = /\/\/[^\n]*|\/\*[\s\S]*?\*\//g;

const RIFT_UNIFORMS: string[][] = [
	['metacraftRiftTime', 'uniform.float.metacraftRiftTime = frameTimeCounter'],
	['metacraftRiftHeight', 'uniform.float.metacraftRiftHeight = viewHeight'],
	['metacraftRiftCameraInt', 'uniform.vec3.metacraftRiftCameraInt = vec3(cameraPositionInt.x, cameraPositionInt.y, cameraPositionInt.z)'],
	['metacraftRiftCameraFract', 'uniform.vec3.metacraftRiftCameraFract = cameraPositionFract'],
];

const UV_NAMES = ['texCoord', 'texcoord', 'uv', 'TexCoord', 'texCoords', 'texcoords', 'lmtexcoord', 'vTexCoord', 'coord'];
const COLOR_NAMES = ['color', 'glcolor', 'glColor', 'vColor', 'vertexColor', 'vcolor', 'colour', 'vertColor', 'col', 'tint', 'tintColor'];
const ALBEDO = 'gtexture|texture|tex|gcolor';

const S = '[ \\t\\n\\x0B\\f\\r]';
const LS = '(?:(?<![^])|(?<=[\\n\\u0085\\u2028\\u2029])|(?<=\\r)(?!\\n))';
const DOT = '[^\\n\\r\\u0085\\u2028\\u2029]';

const FRAGMENT_MARKER = new RegExp(`${LS}${S}*#${S}*if(?:def)?${S}+${DOT}*\\b(FSH|FRAGMENT_SHADER|FRAGMENT)\\b`, 'g');
const VERTEX_MARKER = new RegExp(`${LS}${S}*#${S}*if(?:def)?${S}+${DOT}*\\b(VSH|VERTEX_SHADER|VERTEX)\\b`, 'g');
const INCLUDE = new RegExp(`${LS}${S}*#${S}*include${S}+"([^"]+)"`, 'g');
const VERSION = new RegExp(`${LS}${S}*#${S}*version${S}+(\\d+)`, 'g');
const SAMPLER_DECL = new RegExp(`uniform${S}+sampler2D${S}+([^;]*)\\b(${ALBEDO})\\b`, 'g');
const MAIN = new RegExp(`${LS}${S}*void${S}+main${S}*\\(${S}*\\)${S}*\\{`, 'g');
const WORLD_FOLDER = /^shaders\/world-?\d+\/$/;
const DIMENSION_FOLDER = new RegExp(`${LS}${S}*dimension\\.([^ \\t\\n\\x0B\\f\\r=:/]+)${S}*[=:]`, 'g');
const VARYING = new RegExp(
	`${LS}[ \\t]*((?:(?:flat|smooth|noperspective|centroid|invariant)${S}+)*)(in|out|varying)${S}+(vec[234])${S}+([\\w \\t\\n\\x0B\\f\\r,]+?)${S}*;`, 'g');

const enc = new TextEncoder();
const dec = new TextDecoder('utf-8', { ignoreBOM: true });

type Files = Map<string, Uint8Array>;
interface Hit { start: number; end: number; g: string[] }

/** Matcher.find(from): the first match at or after from (lookbehinds see the text before from). */
function find(re: RegExp, text: string, from = 0): Hit | null {
	re.lastIndex = from;
	const m = re.exec(text);
	if (!m) return null;
	return { start: m.index, end: m.index + m[0].length, g: m as unknown as string[] };
}

function* findAll(re: RegExp, text: string): Generator<Hit> {
	let from = 0;
	for (;;) {
		const h = find(re, text, from);
		if (!h) return;
		from = h.end > h.start ? h.end : h.end + 1;
		yield h;
	}
}

function insert(s: string, at: number, ins: string): string {
	return s.substring(0, at) + ins + s.substring(at);
}

function fullMatch(re: string, s: string): boolean {
	return new RegExp(`^(?:${re})$`).test(s);
}

function text(files: Files, path: string): string {
	return dec.decode(files.get(path)!);
}

function bytes(s: string): Uint8Array {
	return enc.encode(s);
}

interface Decl { start: number; end: number; direction: string; type: string; name: string }

class PackPatch {
	log: string[] = [];
	private GLSL: string;
	private RIFT_GLSL: string;
	private TEXT_COLORS_GLSL: string;
	private stamp: string;

	constructor(res: Resources) {
		this.GLSL = res.ovvar;
		this.RIFT_GLSL = res.riftCommon + res.voidRift + res.netherRift;
		this.TEXT_COLORS_GLSL = res.textColors;
		this.stamp = res.stamp;
	}

	run(input: Files): { status: Status; files: Files } {
		if (![...input.keys()].some((p) => p.startsWith('shaders/'))) {
			this.log.push('no shaders/ folder, not a shaderpack');
			return { status: 'NOT_A_SHADERPACK', files: input };
		}
		const programs = this.entityPrograms(input);
		for (const pair of programs) {
			if (text(input, pair[0]).includes(TAG)) {
				this.log.push('already an ovvar-patched copy; patch the original pack instead');
				return { status: 'PATCHED_COPY', files: input };
			}
		}
		const files: Files = new Map(input);
		// First, so the ovvar and rift code added below still reads the raw colour (see PackPatch.java).
		const textLog = this.patchTextColors(files);
		const done = new Set<string>();
		let patched = 0;
		let rifts = false;
		for (const pair of programs) {
			const frag = pair[0];
			const vert = pair[1];
			if (done.has(frag)) continue;
			done.add(frag);
			let fragText = text(files, frag);
			let vertText: string | null = vert === null ? null : text(files, vert);
			if (vert === null) {
				this.say('  ' + frag + ': no matching vertex program found; skipped');
				continue;
			}
			// Both stages in one file: without both stage markers there is no telling where one ends, and the
			// vertex code would land in the fragment main().
			if (vert === frag && (find(FRAGMENT_MARKER, fragText) === null || find(VERTEX_MARKER, fragText) === null)) {
				this.say('  ' + frag + ': one file for both stages, but no #ifdef FSH/VSH-style stage markers found; skipped');
				continue;
			}
			let changed = false;
			const program = this.patchProgram(files, frag, fragText, vert, vertText!);
			if (program !== null) {
				fragText = program[0];
				vertText = program[1];
				changed = true;
			}
			if (fragText.includes(RIFT_TAG)) {
				this.say('  ' + frag + ': rifts already patched');
			} else {
				const result = this.patchRifts(files, frag, fragText, vert, vertText!);
				if (result !== null) {
					fragText = result[0];
					vertText = result[1];
					changed = true;
					rifts = true;
				}
			}
			if (!changed) continue;
			files.set(frag, bytes(fragText));
			if (vert !== frag) {
				files.set(vert, bytes(vertText!));
				done.add(vert);
			}
			patched++;
		}
		if (rifts) {
			this.patchRiftShadows(files);
			const props = files.has('shaders/shaders.properties') ? text(files, 'shaders/shaders.properties') : '';
			let added = '';
			for (const uniform of RIFT_UNIFORMS) {
				if (!props.includes(uniform[0])) added += uniform[1] + '\n';
			}
			if (added.length > 0) {
				const header = '\n# Metacraft rifts (added by the shader patcher): time, screen height and camera under names of their own\n';
				files.set('shaders/shaders.properties', bytes(props + header + added));
			}
		}
		if (patched === 0) {
			this.say('  nothing patched');
			return { status: 'NOTHING_PATCHED', files: input };
		}
		this.log.push(...textLog);
		files.set(STAMP_FILE, bytes('stamp=' + this.stamp + '\n'));
		return { status: 'PATCHED', files };
	}

	private say(line: string): void {
		this.log.push(line);
	}

	private entityPrograms(files: Files): (string | null)[][] {
		const out: (string | null)[][] = [];
		for (const path of files.keys()) {
			const file = path.substring(path.lastIndexOf('/') + 1);
			if (!path.startsWith('shaders/') || !file.startsWith('gbuffers_entities') || !file.endsWith('.fsh')) continue;
			const frag = this.program(files, path);
			const vshPath = path.substring(0, path.length - 4) + '.vsh';
			let vert: string | null = files.has(vshPath) ? this.program(files, vshPath) : null;
			if (frag === vert || (vert === null && find(VERTEX_MARKER, text(files, frag)) !== null)) vert = frag;
			out.push([frag, vert]);
		}
		return out;
	}

	private program(files: Files, path: string): string {
		const t = text(files, path);
		if (find(MAIN, t)) return path;
		const includes: string[] = [];
		for (const inc of findAll(INCLUDE, t)) {
			const resolved = resolveInclude(path, inc.g[1]);
			if (files.has(resolved) && find(MAIN, text(files, resolved))) includes.push(resolved);
		}
		return includes.length === 0 ? path : includes[0];
	}

	private patchProgram(files: Files, fragPath: string, fragText: string, vertPath: string, vertText: string): string[] | null {
		const unified = vertPath === fragPath;
		const fr = region(fragText, FRAGMENT_MARKER, VERTEX_MARKER);
		const frag = fragText.substring(fr[0], fr[1]);

		const samplerName = albedoSampler(files, fragPath, fragText, new Set(), 0);
		if (samplerName === null) {
			this.say('  ' + fragPath + ': no albedo sampler (uniform sampler2D gtexture/texture/tex), not even in includes; skipped');
			return null;
		}
		const sampleFn = glslVersion(fragText) < 130 ? 'texture2D' : 'texture';

		const uv = findVarying(frag, 'in|varying', UV_NAMES, false, 'texcoord');
		if (uv === null) {
			this.say('  ' + fragPath + ': no texture-coordinate varying found; skipped');
			return null;
		}
		const color = findVarying(frag, 'in|varying', COLOR_NAMES, true, 'col', 'tint');
		if (color === null) {
			this.say('  ' + fragPath + ': no vertex-colour varying (color/glcolor/vColor/tint...) found; skipped');
			return null;
		}
		if (color.type !== 'vec4' && color.type !== 'vec3') {
			this.say('  ' + fragPath + ": vertex-colour varying '" + color.name + "' is a " + color.type + ', not vec3/vec4; skipped');
			return null;
		}
		const main = find(MAIN, frag, Math.max(uv.end, color.end));
		if (!main) {
			this.say('  ' + fragPath + ': no void main() after the varyings; skipped');
			return null;
		}

		const helper = '\n// --- Ovvar (metacraft.se): patches on student overalls; see README.txt in ovvar-client.jar\n'
			+ uv.direction + ' vec4 ovvar_color;\n'
			+ uv.direction + ' vec3 ovvar_pos;\n'
			+ uv.direction + ' vec3 ovvar_normal;\n'
			+ '#define OVVAR_SAMPLE(uv) ' + sampleFn + '(' + samplerName + ', uv)\n'
			+ this.GLSL
			+ '// --- end Ovvar\n\n';
		const assign = '\n\t' + shadow(uv) + ' = ovvar_raw_' + uv.name + '();\n'
			+ '\t' + shadow(uv) + '.xy = ovvar_uv(' + shadow(uv) + '.xy);\n'
			+ '\t' + shadow(color) + ' = ' + (color.type === 'vec4'
				? 'ovvar_shade(ovvar_raw_' + color.name + '(), ' + shadow(uv) + '.xy)'
				: 'ovvar_shade(vec4(ovvar_raw_' + color.name + '(), 1.0), ' + shadow(uv) + '.xy).rgb') + ';\n';
		let out = frag;
		out = insert(out, main.end, assign);
		out = insert(out, main.start, helper);
		const first = uv.end <= color.end ? uv : color;
		const second = first === uv ? color : uv;
		out = insert(out, second.end, shadowDecl(second));
		out = insert(out, first.end, shadowDecl(first));
		if (unified) out += '\n#undef ' + uv.name + '\n#undef ' + color.name + '\n';
		const fragOut = fragText.substring(0, fr[0]) + out + fragText.substring(fr[1]);

		const vertSource = unified ? fragOut : vertText;
		const vr = unified ? region(vertSource, VERTEX_MARKER, FRAGMENT_MARKER) : [0, vertSource.length];
		const vert = vertSource.substring(vr[0], vr[1]);
		const vmain = find(MAIN, vert);
		if (!vmain) {
			this.say('  ' + fragPath + ': vertex side (' + vertPath + ') has no void main(); skipped');
			return null;
		}
		const vuv = findVaryingNamed(vert, 'out|varying', uv.name);
		const direction = vuv !== null ? vuv.direction : uv.direction === 'in' ? 'out' : 'varying';
		const core = vert.includes('vaColor') || vert.includes('vaPosition');
		const colorAttr = core ? 'vaColor' : 'gl_Color';
		const position = core ? 'vaPosition' : 'gl_Vertex.xyz';
		const normal = core ? 'vaNormal' : 'gl_Normal';
		const newVert = vert.substring(0, vmain.start)
			+ '\n// --- Ovvar: the raw vertex colour (an ovve\'s dye colour holds its patch bits), position and normal\n'
			+ direction + ' vec4 ovvar_color;\n'
			+ direction + ' vec3 ovvar_pos;\n'
			+ direction + ' vec3 ovvar_normal;\n\n'
			+ vert.substring(vmain.start, vmain.end)
			+ '\n\tovvar_color = ' + colorAttr + ';\n'
			+ '\tovvar_pos = ' + position + ';\n'
			+ '\tovvar_normal = ' + normal + ';\n'
			+ vert.substring(vmain.end);
		const vertOut = vertSource.substring(0, vr[0]) + newVert + vertSource.substring(vr[1]);

		this.say('  ' + fragPath + (unified ? '' : ' + ' + vertPath) + ": '" + uv.name + "' and '" + color.name
			+ "' shadowed, art via " + sampleFn + '(' + samplerName + '), colour from ' + colorAttr);
		return unified ? [vertOut, vertOut] : [fragOut, vertOut];
	}

	private patchRifts(files: Files, fragPath: string, fragText: string, vertPath: string, vertText: string): string[] | null {
		const unified = vertPath === fragPath;
		const fr = region(fragText, FRAGMENT_MARKER, VERTEX_MARKER);
		const frag = fragText.substring(fr[0], fr[1]);
		const samplerName = albedoSampler(files, fragPath, fragText, new Set(), 0);
		if (samplerName === null) {
			this.say('  ' + fragPath + ': rifts: no albedo sampler; skipped');
			return null;
		}
		const uv = findVarying(frag, 'in|varying', UV_NAMES, false, 'texcoord');
		const main = find(MAIN, frag);
		if (uv === null || !main) {
			this.say('  ' + fragPath + ': rifts: no texture-coordinate varying or no void main(); skipped');
			return null;
		}
		const sample = new RegExp(`\\b(\\w+)${S}*\\(${S}*${escapeRegex(samplerName)}${S}*[,)]`, 'g');
		let callStart = -1;
		let callEnd = -1;
		let sampleFn: string | null = null;
		let from = main.end;
		for (;;) {
			const s = find(sample, frag, from);
			if (!s) break;
			from = s.end;
			if (fullMatch('textureSize|textureQueryLod|textureQueryLevels', s.g[1])) continue;
			const close = closingParen(frag, frag.indexOf('(', s.start));
			if (close < 0) break;
			callStart = s.start;
			callEnd = close + 1;
			sampleFn = s.g[1];
			break;
		}
		if (sampleFn === null) {
			this.say('  ' + fragPath + ': rifts: main() never samples ' + samplerName + '; skipped');
			return null;
		}
		const inn = uv.direction;
		const helper = '\n// --- Metacraft rifts (metacraft.se): the void anchor\'s and nether rifts, drawn as in metacraft-shaders\' item shader\n'
			+ inn + ' vec2 metacraft_rift_corner;\n'
			+ inn + ' vec2 metacraft_rift_tex;\n'
			+ inn + ' float metacraft_rift_flag;\n'
			+ inn + ' float metacraft_rift_stage;\n'
			+ inn + ' vec3 metacraft_rift_world;\n'
			+ inn + ' vec3 metacraft_rift_view;\n'
			+ 'uniform float metacraftRiftTime;\n'
			+ 'uniform float metacraftRiftHeight;\n'
			+ 'uniform vec3 metacraftRiftCameraInt;\n'
			+ 'float metacraft_rift_det(mat2 m) { return m[0][0] * m[1][1] - m[1][0] * m[0][1]; }\n'
			+ 'mat2 metacraft_rift_inverse(mat2 m) { return mat2(m[1][1], -m[0][1], -m[1][0], m[0][0]) / metacraft_rift_det(m); }\n'
			+ '#define texCoord0 metacraft_rift_tex\n'
			+ '#define riftUv metacraft_rift_corner\n'
			+ '#define GameTime (metacraftRiftTime / 1200.0)\n'
			+ '#define ScreenSize vec2(1.0, metacraftRiftHeight)\n'
			+ '#define riftWorld metacraft_rift_world\n'
			+ '#define riftView metacraft_rift_view\n'
			+ '#define CameraBlockPos ivec3(metacraftRiftCameraInt)\n'
			+ '#define Sampler0 ' + samplerName + '\n'
			+ '#define determinant metacraft_rift_det\n'
			+ '#define inverse metacraft_rift_inverse\n'
			+ this.RIFT_GLSL
			+ '\n// the pack\'s own albedo sample goes through here: a rift\'s colour for a rift, else the sample\n'
			+ 'vec4 metacraft_rift_sample(vec4 sprite) {\n'
			+ '\tfloat flag = metacraft_rift_flag;\n'
			+ '\tif (flag < 0.5) return sprite;\n'
			+ '\tvec4 rift;\n'
			+ '\tif (flag > 11.5) rift = netherRiftEdge(metacraft_rift_stage);\n'
			+ '\telse if (flag > 10.5) rift = netherRiftBody(metacraft_rift_stage);\n'
			+ '\telse if (flag > 2.5) rift = riftCore();\n'
			+ '\telse if (flag > 1.5) rift = riftGlow(sprite);\n'
			+ '\telse rift = riftColor(sprite);\n'
			+ '\tif (rift.a < 0.02) discard;\n'
			+ '\treturn rift;\n'
			+ '}\n'
			+ '#undef texCoord0\n#undef riftUv\n#undef GameTime\n#undef ScreenSize\n#undef riftWorld\n#undef riftView\n#undef CameraBlockPos\n'
			+ '#undef Sampler0\n#undef determinant\n#undef inverse\n'
			+ '// --- end Metacraft rifts\n\n';
		let out = frag;
		out = insert(out, callEnd, ')');
		out = insert(out, callStart, RIFT_TAG + '(');
		out = insert(out, main.start, helper);
		const fragOut = fragText.substring(0, fr[0]) + out + fragText.substring(fr[1]);

		const vertSource = unified ? fragOut : vertText;
		const vr = unified ? region(vertSource, VERTEX_MARKER, FRAGMENT_MARKER) : [0, vertSource.length];
		const vert = vertSource.substring(vr[0], vr[1]);
		const vmain = find(MAIN, vert);
		if (!vmain) {
			this.say('  ' + fragPath + ': rifts: vertex side (' + vertPath + ') has no void main(); skipped');
			return null;
		}
		const dir = inn === 'in' ? 'out' : 'varying';
		const core = vert.includes('vaColor') || vert.includes('vaPosition');
		const colorAttr = core ? 'vaColor' : 'gl_Color';
		const uvAttr = core ? 'vaUV0' : 'gl_MultiTexCoord0.st';
		const position = core ? 'vaPosition' : 'gl_Vertex.xyz';
		const newVert = vert.substring(0, vmain.start)
			+ '\n// --- Metacraft rifts: the tint marker, the quad corner, the raw sprite coordinate and, for a nether\n'
			+ '// rift, where the vertex is: relative to the camera, and in the world less the camera\'s whole blocks\n'
			+ dir + ' vec2 metacraft_rift_corner;\n'
			+ dir + ' vec2 metacraft_rift_tex;\n'
			+ dir + ' float metacraft_rift_flag;\n'
			+ dir + ' float metacraft_rift_stage;\n'
			+ dir + ' vec3 metacraft_rift_world;\n'
			+ dir + ' vec3 metacraft_rift_view;\n'
			+ 'uniform vec3 metacraftRiftCameraFract;\n\n'
			+ vert.substring(vmain.start, vmain.end)
			+ '\n\t{\n'
			+ '\t\tvec3 tint = floor(' + colorAttr + '.rgb * 255.0 + 0.5);\n'
			+ '\t\tint corner = gl_VertexID - 4 * (gl_VertexID / 4);\n'
			+ '\t\tvec2 cornerUv = vec2((corner == 1 || corner == 2) ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);\n'
			+ '\t\tmetacraft_rift_tex = ' + uvAttr + ';\n'
			+ '\t\tmetacraft_rift_flag = 0.0;\n'
			+ '\t\tmetacraft_rift_stage = 0.0;\n'
			+ '\t\tmetacraft_rift_corner = vec2(0.0);\n'
			+ '\t\tmetacraft_rift_world = vec3(0.0);\n'
			+ '\t\tmetacraft_rift_view = vec3(0.0);\n'
			+ '\t\tif (tint.r == 254.0 && tint.g == 254.0 && tint.b >= 251.0 && tint.b <= 253.0) {\n'
			+ '\t\t\tmetacraft_rift_flag = 254.0 - tint.b;\n'
			+ '\t\t\tmetacraft_rift_corner = cornerUv;\n'
			+ '\t\t} else if (tint.r == 254.0 && tint.g >= 251.0 && tint.g <= 253.0 && tint.b >= 249.0 && tint.b <= 250.0) {\n'
			+ '\t\t\tmetacraft_rift_flag = 11.0 + (250.0 - tint.b);\n'
			+ '\t\t\tmetacraft_rift_stage = 254.0 - tint.g;\n'
			+ '\t\t\tmetacraft_rift_corner = cornerUv;\n'
			+ '\t\t\tmetacraft_rift_view = ' + position + ';\n'
			+ '\t\t\tmetacraft_rift_world = ' + position + ' + metacraftRiftCameraFract;\n'
			+ '\t\t}\n'
			+ '\t}\n'
			+ vert.substring(vmain.end);
		const vertOut = vertSource.substring(0, vr[0]) + newVert + vertSource.substring(vr[1]);
		this.say('  ' + fragPath + (unified ? '' : ' + ' + vertPath) + ': rifts drawn through ' + sampleFn + '(' + samplerName + '), marker from ' + colorAttr
			+ ', position from ' + position);
		return unified ? [vertOut, vertOut] : [fragOut, vertOut];
	}

	/** Chapter text colours on world nametags: see patchTextColors in PackPatch.java. */
	private patchTextColors(files: Files): string[] {
		const out: string[] = [];
		const dirSet = new Set<string>(['shaders/']);
		for (const path of files.keys()) {
			const dir = path.substring(0, path.lastIndexOf('/') + 1);
			if (WORLD_FOLDER.test(dir)) dirSet.add(dir);
		}
		if (files.has('shaders/dimension.properties')) {
			for (const m of findAll(DIMENSION_FOLDER, text(files, 'shaders/dimension.properties'))) dirSet.add('shaders/' + m.g[1] + '/');
		}
		const dirs = [...dirSet].sort();
		const programs = new Map<string, string[]>();
		const unifiedOf = new Map<string, boolean>();
		for (const dir of dirs) {
			const name = TEXT_PROGRAMS.find((c) => files.has(dir + c + '.fsh'));
			if (name === undefined) continue;
			if (!files.has(dir + name + '.vsh')) {
				out.push('  ' + dir + name + '.fsh: chapter text colours: no .vsh, Iris draws it with its own vertex stage; skipped');
				continue;
			}
			const prog = this.program(files, dir + name + '.vsh');
			if (!programs.has(prog)) programs.set(prog, []);
			programs.get(prog)!.push(dir + name);
			if (!unifiedOf.has(prog)) unifiedOf.set(prog, prog === this.program(files, dir + name + '.fsh'));
		}
		for (const [prog, labels] of programs) {
			const t = text(files, prog);
			const unified = unifiedOf.get(prog)!;
			const where = '  ' + prog + ': chapter text colours (' + labels.join(', ') + ')';
			if (unified && (find(FRAGMENT_MARKER, t) === null || find(VERTEX_MARKER, t) === null)) {
				out.push(where + ': one file for both stages, but no #ifdef FSH/VSH-style stage markers found; skipped');
				continue;
			}
			const vr = unified ? region(t, VERTEX_MARKER, FRAGMENT_MARKER) : [0, t.length];
			const vert = t.substring(vr[0], vr[1]);
			if (vert.includes(TEXT_TAG)) continue;
			const main = find(MAIN, vert);
			if (!main) {
				out.push(where + ': no void main(); skipped');
				continue;
			}
			const code = vert.replace(COMMENT, ' ');
			const input = code.includes('vaColor') || code.includes('vaPosition') ? 'vaColor' : 'gl_Color';
			const codeMain = find(MAIN, code);
			if (!codeMain || !reads(files, prog, code.substring(codeMain.start), new RegExp('\\b' + input + '\\b'), new Set(), 0)) {
				out.push(where + ': main() never reads ' + input + '; skipped');
				continue;
			}
			const helper = "\n// --- Metacraft chapter text colours (metacraft.se): nametags in the chapters' colours, as the\n"
				+ '// chapter-text-colors resource pack draws them without a shaderpack\n' + this.TEXT_COLORS_GLSL
				+ 'vec4 metacraft_text_' + input + '() { return ' + TEXT_TAG + '(' + input + '); }\n'
				+ '// --- end chapter text colours\n\n';
			const newVert = vert.substring(0, main.start) + helper + vert.substring(main.start, main.end)
				+ '\n#define ' + input + ' metacraft_text_' + input + '()' + vert.substring(main.end)
				+ (unified ? '\n#undef ' + input + '\n' : '');
			files.set(prog, bytes(t.substring(0, vr[0]) + newVert + t.substring(vr[1])));
			out.push(where + ': ' + input + ' wrapped');
		}
		return out;
	}

	private patchRiftShadows(files: Files): void {
		const done = new Set<string>();
		for (const path of [...files.keys()]) {
			const file = path.substring(path.lastIndexOf('/') + 1);
			if (!path.startsWith('shaders/') || !file.startsWith('shadow') || !file.endsWith('.vsh')) continue;
			const prog = this.program(files, path);
			if (done.has(prog)) continue;
			done.add(prog);
			const t = text(files, prog);
			if (t.includes('metacraft_rift_shadow_main')) continue;
			const vr = prog === path ? [0, t.length] : region(t, VERTEX_MARKER, FRAGMENT_MARKER);
			const vert = t.substring(vr[0], vr[1]);
			const main = find(MAIN, vert);
			if (!main) {
				this.say('  ' + prog + ': rift shadows: no void main(); left casting shadows');
				continue;
			}
			const colorAttr = vert.includes('vaColor') || vert.includes('vaPosition') ? 'vaColor' : 'gl_Color';
			const wrapper = '\n// --- Metacraft rifts: a rift casts no shadow\n'
				+ 'void metacraft_rift_shadow_main();\n'
				+ 'void main() {\n'
				+ '\tmetacraft_rift_shadow_main();\n'
				+ '\tvec3 tint = floor(' + colorAttr + '.rgb * 255.0 + 0.5);\n'
				+ '\tif (tint.r == 254.0 && ((tint.g == 254.0 && tint.b >= 251.0 && tint.b <= 253.0)\n'
				+ '\t\t\t|| (tint.g >= 251.0 && tint.g <= 253.0 && tint.b >= 249.0 && tint.b <= 250.0))) {\n'
				+ '\t\tgl_Position = vec4(0.0, 0.0, -2.0, 1.0);\n'
				+ '\t}\n'
				+ '}\n\n';
			const newVert = vert.substring(0, main.start) + wrapper + 'void metacraft_rift_shadow_main() {' + vert.substring(main.end);
			files.set(prog, bytes(t.substring(0, vr[0]) + newVert + t.substring(vr[1])));
			this.say('  ' + prog + ': rifts cast no shadow');
		}
	}
}

function escapeRegex(s: string): string {
	return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function resolveInclude(from: string, include: string): string {
	if (include.startsWith('/')) return 'shaders' + include;
	return from.substring(0, from.lastIndexOf('/') + 1) + include;
}

function closingParen(t: string, open: number): number {
	let depth = 0;
	for (let i = open; i < t.length; i++) {
		const c = t.charAt(i);
		if (c === '(') depth++;
		else if (c === ')' && --depth === 0) return i;
	}
	return -1;
}

function shadow(d: Decl): string {
	return 'ovvar_shadow_' + d.name;
}

function shadowDecl(d: Decl): string {
	return '\n' + d.type + ' ' + shadow(d) + '; // Ovvar: stands in for ' + d.name + ' from here on\n'
		+ d.type + ' ovvar_raw_' + d.name + '() { return ' + d.name + '; }\n'
		+ '#define ' + d.name + ' ' + shadow(d) + '\n';
}

function glslVersion(t: string): number {
	const m = find(VERSION, t);
	if (m) return parseInt(m.g[1], 10);
	return t.includes('texture2D(') ? 120 : 330;
}

function albedoSampler(files: Files, path: string, t: string, seen: Set<string>, depth: number): string | null {
	const m = find(SAMPLER_DECL, t);
	if (m) return m.g[2];
	if (depth > 4 || seen.has(path)) return null;
	seen.add(path);
	for (const inc of findAll(INCLUDE, t)) {
		const resolved = resolveInclude(path, inc.g[1]);
		if (!files.has(resolved)) continue;
		const found = albedoSampler(files, resolved, text(files, resolved), seen, depth + 1);
		if (found !== null) return found;
	}
	return null;
}

/** Whether the text, or a file it includes (to a depth of 4), matches outside comments. */
function reads(files: Files, path: string, t: string, input: RegExp, seen: Set<string>, depth: number): boolean {
	const code = t.replace(COMMENT, ' ');
	if (input.test(code)) return true;
	if (depth > 4 || seen.has(path)) return false;
	seen.add(path);
	for (const inc of findAll(INCLUDE, code)) {
		const resolved = resolveInclude(path, inc.g[1]);
		if (files.has(resolved) && reads(files, resolved, text(files, resolved), input, seen, depth + 1)) return true;
	}
	return false;
}

function region(t: string, own: RegExp, other: RegExp): number[] {
	const m = find(own, t);
	if (!m) return [0, t.length];
	const start = m.end;
	const o = find(other, t, start);
	const end = o ? o.start : t.length;
	return [start, end];
}

function findVarying(regionText: string, direction: string, names: string[], allowFlat: boolean, ...hints: string[]): Decl | null {
	for (const candidate of names) {
		const d = findVaryingNamed(regionText, direction, candidate);
		if (d !== null) return d;
	}
	for (const hint of hints) {
		for (const m of findAll(VARYING, regionText)) {
			if ((!allowFlat && m.g[1].includes('flat')) || !fullMatch(direction, m.g[2])) continue;
			for (const n of m.g[4].split(',')) {
				const name = n.trim();
				if (name.toLowerCase().includes(hint)) return { start: m.start, end: m.end, direction: m.g[2], type: m.g[3], name };
			}
		}
	}
	return null;
}

function findVaryingNamed(regionText: string, direction: string, wanted: string): Decl | null {
	for (const m of findAll(VARYING, regionText)) {
		if (!fullMatch(direction, m.g[2])) continue;
		for (const n of m.g[4].split(',')) {
			if (n.trim() === wanted) return { start: m.start, end: m.end, direction: m.g[2], type: m.g[3], name: wanted };
		}
	}
	return null;
}

/** Patches a pack given as path to bytes. Never throws on bad input. */
export function patchPack(files: Map<string, Uint8Array>, res: Resources): { status: Status; files: Map<string, Uint8Array>; log: string[] } {
	const p = new PackPatch(res);
	try {
		const r = p.run(files);
		return { status: r.status, files: r.files, log: p.log };
	} catch (e) {
		p.log.push('  failed: ' + e);
		return { status: 'NOTHING_PATCHED', files, log: p.log };
	}
}
