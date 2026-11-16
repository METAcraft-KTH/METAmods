// Runs every fixture case through patchPack with the rules of Fixtures.java: inputs and expected files read
// in sorted path order, status.txt and log.txt split off the expected pack, the pack compared only on PATCHED.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { patchPack } from './patcher.ts';
import { loadResources } from './resources.mjs';

const fixtures = fileURLToPath(new URL('../fixtures/', import.meta.url));
const res = loadResources();

function read(dir: string): Map<string, Uint8Array> {
	const paths: string[] = [];
	const walk = (rel: string) => {
		for (const name of readdirSync(dir + rel)) {
			const p = rel + name;
			if (statSync(dir + p).isDirectory()) walk(p + '/');
			else paths.push(p);
		}
	};
	try {
		walk('');
	} catch {
		return new Map();
	}
	paths.sort();
	return new Map(paths.map((p): [string, Uint8Array] => [p, new Uint8Array(readFileSync(dir + p))]));
}

const text = (b: Uint8Array | undefined) => (b === undefined ? undefined : new TextDecoder('utf-8', { ignoreBOM: true }).decode(b));
const same = (a: Uint8Array, b: Uint8Array) => Buffer.compare(a, b) === 0;

test('stampMatchesJava', () => {
	const props = readFileSync(fixtures + 'split-programs/expected/ovvar-patch.properties', 'utf8');
	assert.equal(props, 'stamp=' + res.stamp + '\n');
});

for (const name of readdirSync(fixtures).sort()) {
	if (!statSync(fixtures + name).isDirectory()) continue;
	test('fixture ' + name, () => {
		const input = read(fixtures + name + '/in/');
		const result = patchPack(input, res);
		const want = read(fixtures + name + '/expected/');
		const wantStatus = text(want.get('status.txt'));
		const wantLog = text(want.get('log.txt'));
		want.delete('status.txt');
		want.delete('log.txt');
		assert.equal(result.status + '\n', wantStatus);
		assert.equal(result.log.map((l) => l + '\n').join(''), wantLog);
		if (result.status !== 'PATCHED') {
			assert.deepEqual([...result.files.keys()], [...input.keys()], 'files changed on ' + result.status);
			for (const [k, v] of input) assert.ok(same(result.files.get(k)!, v), k + ' changed on ' + result.status);
		}
		const out = result.status === 'PATCHED' ? result.files : new Map<string, Uint8Array>();
		for (const [path, bytes] of out) {
			assert.ok(want.has(path), 'unexpected file ' + path);
			assert.ok(same(bytes, want.get(path)!), path + ' differs');
		}
		for (const path of want.keys()) assert.ok(out.has(path), 'missing file ' + path);
	});
}
