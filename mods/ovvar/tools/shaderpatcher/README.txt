Ovvar shader patcher
====================

METAcraft's ovvar (student overalls) draw their sewn-on patches with a shader, and so do the
rifts (the void anchor's, and nether rifts). Vanilla clients get that automatically. If you play
with a shaderpack in Iris or OptiFine, the pack replaces those shaders, so you see plain overalls
without patches and flat, still rifts — unless your shaderpack is patched.

1. Have Java installed (java.com or adoptium.net).
2. Double-click OvvarShaderPatcher.jar.
   It finds your .minecraft/shaderpacks folder and writes a "+ovvar" copy of every pack
   (the originals are untouched).
3. In Iris / OptiFine, select the "+ovvar" copy.

Re-run it when you install or update a shaderpack.

From a terminal: java -jar OvvarShaderPatcher.jar [pack.zip|folder ...] [--dry-run]

The patch splices Ovvar's and the rifts' shader code into the pack's entity program, keeps rifts
out of the pack's shadows, and adds four custom uniforms to shaders.properties; everything else is
copied as-is. Tested with BSL, Bliss, Complementary Reimagined/Unbound, MakeUp Ultra Fast,
Solas, Photon and Super Duper Vanilla. If a pack reports "skipped" or "nothing patched",
tell the METAcraft project group which pack and version.
