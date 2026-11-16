// Reads the five GLSL files the patcher splices in and computes the stamp the way PackPatch.stamp() does.
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../../../', import.meta.url));
const FILES = [
	['ovvar', 'mods/ovvar/src/main/resources/assets/ovvar/shaders/include/ovvar.glsl'],
	['riftCommon', 'mods/metacraft-shaders/src/main/resources/assets/metacraft/shaders/include/rift_common.glsl'],
	['voidRift', 'mods/metacraft-shaders/src/main/resources/assets/metacraft/shaders/include/void_rift.glsl'],
	['netherRift', 'mods/metacraft-shaders/src/main/resources/assets/metacraft/shaders/include/nether_rift.glsl'],
	['textColors', 'mods/ovvar/tools/shaderpatcher/chapter_text_colors.glsl'],
];

export function loadResources() {
	const sha = createHash('sha1');
	const res = {};
	for (const [key, path] of FILES) {
		const bytes = readFileSync(root + path);
		sha.update(bytes);
		res[key] = new TextDecoder('utf-8', { ignoreBOM: true }).decode(bytes);
	}
	res.stamp = sha.digest('hex').slice(0, 8);
	return res;
}
