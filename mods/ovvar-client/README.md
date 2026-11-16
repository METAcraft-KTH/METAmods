# Ovvar Client

The one optional client-side jar for ovvar (`ovvar-client-<version>.jar`, offered as `ovvar-client.jar`).
It replaced both `OvvarShaderPatcher.jar` and the `ovvar-shaderpatch` mod. Players never need it: a vanilla
client gets exactly what it always got.

## In the mods folder (Fabric, client only)

**Shaderpacks.** At `preLaunch` it looks in `<game dir>/shaderpacks` and, for every shaderpack (`.zip` or
folder) without a current `+ovvar` copy, writes `<name>+ovvar(.zip)` with the ovvar and rift shader code
spliced in (the patcher in `mods/ovvar/tools/shaderpatcher`, compiled into this jar). A copy whose
`ovvar-patch.properties` stamp differs from the current one is rewritten; originals are never touched. If
`config/iris.properties` has `shaderPack=<original>` and that original now has a current copy, that one line
is changed to the copy. Choosing the original pack in Iris is switched back to the `+ovvar` copy at every
launch; the copy starts with the original's Iris settings (`shaderpacks/<pack>.txt`) when it has none of its own.

Nothing here can fail startup: every error is logged (`[ovvar-client]`) and swallowed.

**Ovvar's combinations.** On an ovvar server it builds every patch combination's equipment asset itself, from
the asset's name (`ClientCombos`, a mixin on `EquipmentAssetManager.get`, only for `ovvar:` assets no pack holds),
with the code the server builds its combination pack with (`mods/ovvar/src/combo`, compiled into both jars; this
build moves its copy to `se.metacraft.ovvarclient.combo`), and only while the base garment from the server's Polymer
pack is loaded. That includes the top under a chestplate with every patch, `chest/<chapter>/<material>/<combo>`,
for every chestplate material the server wraps (falling back to the plain wrap for a name it cannot read). It declares `breaks: ovvar-shaderpatch`, the mod it replaced. It
tells the server with a handshake: a configuration-phase receiver on `ovvar:draws_combos/v<protocol>`
(`Handshake`, nothing is ever sent on it). The server then skips the combination pack for this player, on join
and mid-play, and sends whole combinations. The Polymer pack still comes from the server. On a server without
ovvar it does nothing; on one with another protocol, or if the hook did not load, there is no handshake and the
server pushes the pack as for vanilla. See "The ovvar-client mod" in `mods/ovvar/README.md`.

No Polymer. Fabric API is recommended, not required: without it the handshake is off (the shaderpacks are still
patched). It is client-only and on no server deploy list;
the `dist` bundle skips it.

## Double-clicked (`java -jar`)

The jar's `Main-Class` is the standalone shader patcher (`metacraft.ovvar.shaderpatcher.Patcher`, built for
Java 11+ in its own source set): a file chooser opens in `.minecraft/shaderpacks` and a `+ovvar` copy of each
pick is written. From a terminal: `java -jar ovvar-client.jar [pack.zip|folder ...] [--dry-run]`. The patcher
loads nothing but itself and the JDK, so it runs with no Minecraft or Fabric on the classpath. `mods/ovvar`
embeds this jar at `assets/ovvar/shaderpatcher/ovvar-client.jar` for autohost to serve.

## Tests

`./gradlew :mods:ovvar-client:test -x :mods:metacraft-lib:test`: `StartupPatchTest` (the startup patch),
`StandaloneJarTest` (builds the jar and runs it with `java -jar` on a fixture pack) and `ComboAssetsTest` (every
combination in `mods/ovvar/src/combo/goldens.json`, which ovvar's game tests compare to the real pack, builds byte
for byte from its name; the mixin under Knot; the protocol pin). Cloud check (a real client):
`./gradlew :mods:ovvar:runClientGameTest -PovvarClientMod`, and "Client test under a shaderpack" in
`mods/ovvar/README.md`.
