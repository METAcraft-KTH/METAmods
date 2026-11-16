# Ovvar Shader Patch

A tiny client-only Fabric mod for the METAcraft launcher/installer. At `preLaunch` it looks in
`<game dir>/shaderpacks` and, for every shaderpack (`.zip` or folder) without a current `+ovvar` copy, writes
`<name>+ovvar(.zip)` with the ovvar and rift shader code spliced in (the same patch as `OvvarShaderPatcher.jar`,
`mods/ovvar/tools/shaderpatcher`, compiled into this mod). A copy whose `ovvar-patch.properties` stamp differs from
the current one is rewritten; originals are never touched. If `config/iris.properties` has `shaderPack=<original>`
and that original now has a current copy, that one line is changed to the copy.

Choosing the original pack in Iris is switched back to the `+ovvar` copy at every launch; the copy starts with the original's Iris settings (`shaderpacks/<pack>.txt`) when it has none of its own.

Nothing here can fail startup: every error is logged (`[ovvar-shaderpatch]`) and swallowed.

No Polymer, no Fabric API dependency. It is client-only and on no server deploy list; the `dist` bundle skips it.

Tests: `./gradlew :mods:ovvar-shaderpatch:test`. Cloud check: see "Client test under a shaderpack" in `mods/ovvar/README.md`
(`-PshaderpatchMod`).
