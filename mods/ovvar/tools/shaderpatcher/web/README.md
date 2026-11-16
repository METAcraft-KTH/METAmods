# Shader patcher, TypeScript port

`patcher.ts` is a 1:1 port of `PackPatch.java`, for the website. It has no imports and works on
`Map<string, Uint8Array>` with `TextDecoder`/`TextEncoder`, so it runs in a browser Web Worker.

```ts
import { patchPack } from './patcher.ts';
import { resources } from './resources.generated.ts';
const { status, files, log } = patchPack(packFiles, resources);
```

Log lines are the core's, without a pack name (the Java CLI prefixes `<name>: `).

## Vendoring

1. `node build.mjs` writes `dist/resources.generated.ts` (the four GLSL files inlined, plus `STAMP`). `dist/` is gitignored.
2. Copy `patcher.ts` and `dist/resources.generated.ts` into the website, side by side (the generated file imports
   the `Resources` type from `../patcher.ts`; adjust that path to wherever `patcher.ts` lands).
3. Rebuild and re-copy whenever the GLSL changes: the stamp changes with it.

## Tests

`node --test patcher.test.ts` runs every case in `../fixtures` (same rules as `Fixtures.java`) and checks the stamp
against the Java-written one. `./gradlew :mods:ovvar:patcherFixtures` runs the Java and then this, when node is on the PATH.
Fixtures are the contract; if the two disagree, Java is the truth.
