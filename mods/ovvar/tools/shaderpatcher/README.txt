Ovvar shader patcher
====================

METAcraft's ovvar (student overalls) can show different patches on the left and right sleeve
and leg. Vanilla clients get that automatically. If you play with a shaderpack in Iris or
OptiFine, the pack replaces the shader that does it, so both sides look the same — unless your
shaderpack is patched.

1. Have Java installed (java.com or adoptium.net).
2. Double-click OvvarShaderPatcher.jar.
   It finds your .minecraft/shaderpacks folder and writes a "+ovvar" copy of every pack
   (the originals are untouched).
3. In Iris / OptiFine, select the "+ovvar" copy.

Re-run it when you install or update a shaderpack.

From a terminal: java -jar OvvarShaderPatcher.jar [pack.zip|folder ...] [--dry-run]

The patch adds ten lines to the pack's entity shader; everything else is copied as-is.
Tested with BSL v10 and Complementary Reimagined r5. If a pack reports "nothing patched",
tell the METAcraft project group which pack and version.
