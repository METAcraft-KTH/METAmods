package metacraft.ovvar.shaderpatcher;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Patches Iris/OptiFine shaderpacks so Ovvar's left and right limb patches render differently.
 *
 * Ovvar (metacraft.se) ships a vanilla core-shader override that a shaderpack replaces wholesale,
 * so shaderpack users see mirrored limbs. This tool adds the same remap to the pack's own entity
 * program: on Ovvar's armour textures (marked by a magenta alpha-2 texel at 63,15 of a 64×32
 * texture) fragments of a mirrored limb sample the arm/leg boxes one strip up, where the
 * left-side art lives.
 *
 * How: the entity fragment program's texture-coordinate varying is renamed on both the vertex
 * and fragment side, and a plain global with the original name is declared in the fragment
 * program and assigned the remapped coordinate at the top of main(). Every use of the
 * coordinate — in the file, in its includes, in macros — then sees the remapped value, whatever
 * way the pack samples its textures. Nothing else in the pack is touched; originals are kept.
 *
 * Double-click: patches every pack in the Minecraft shaderpacks folder and shows a summary.
 * Command line: {@code java -jar OvvarShaderPatcher.jar [pack.zip|folder ...] [--dry-run]}.
 */
public final class Patcher {
    private static final String SUFFIX = "+ovvar";
    private static final String TAG = "ovvar_remap";

    private static final String HELPER =
            "\n// --- Ovvar: asymmetric limbs (metacraft.se). On Ovvar's armour textures (marker texel at 63,15)\n"
            + "// the left arm and leg, the model's mirror images of the right ones, sample their boxes one\n"
            + "// strip up, where the left-side art is drawn.\n"
            + "vec2 ovvar_remap(vec2 uv) {\n"
            + "    vec2 ovvar_du = dFdx(uv), ovvar_dv = dFdy(uv);\n"
            + "    float ovvar_det = ovvar_du.x * ovvar_dv.y - ovvar_dv.x * ovvar_du.y;\n"
            + "    vec4 ovvar_marker = %SAMPLE%(%SAMPLER%, vec2(63.5 / 64.0, 15.5 / 32.0));\n"
            + "    if (any(greaterThan(abs(ovvar_marker - vec4(1.0, 0.0, 1.0, 2.0 / 255.0)), vec4(0.5 / 255.0)))) return uv;\n"
            + "    bool ovvar_limb = uv.y >= 0.5 && (uv.x < 16.0 / 64.0 || (uv.x >= 40.0 / 64.0 && uv.x < 56.0 / 64.0));\n"
            + "    if (!ovvar_limb) return uv;\n"
            + "    bool ovvar_mirrored = (ovvar_det > 0.0) == gl_FrontFacing;\n"
            + "    return ovvar_mirrored ? uv - vec2(0.0, 0.5) : uv;\n"
            + "}\n"
            + "// --- end Ovvar\n";

    /** Texture-coordinate varying names, most likely first. */
    private static final List<String> UV_NAMES = Arrays.asList(
            "texCoord", "texcoord", "uv", "TexCoord", "texCoords", "texcoords", "lmtexcoord", "vTexCoord", "coord");
    private static final String ALBEDO = "gtexture|texture|tex|gcolor";

    private static final Pattern FRAGMENT_MARKER = Pattern.compile("(?m)^\\s*#\\s*if(?:def)?\\s+.*\\b(FSH|FRAGMENT_SHADER|FRAGMENT)\\b");
    private static final Pattern VERTEX_MARKER = Pattern.compile("(?m)^\\s*#\\s*if(?:def)?\\s+.*\\b(VSH|VERTEX_SHADER|VERTEX)\\b");
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#\\s*include\\s+\"([^\"]+)\"");
    private static final Pattern SAMPLER_DECL = Pattern.compile("uniform\\s+sampler2D\\s+([^;]*)\\b(" + ALBEDO + ")\\b");
    private static final Pattern MAIN = Pattern.compile("(?m)^\\s*void\\s+main\\s*\\(\\s*\\)\\s*\\{");
    /** A varying declaration statement: qualifiers, type, comma-separated names. */
    private static final Pattern VARYING = Pattern.compile(
            "(?m)^[ \\t]*((?:(?:flat|smooth|noperspective|centroid|invariant)\\s+)*)(in|out|varying)\\s+(vec[234])\\s+([\\w\\s,]+?)\\s*;");

    private final StringBuilder log = new StringBuilder();
    private final boolean dryRun;
    private int patchedPacks, skippedPacks, failedPacks;

    private Patcher(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public static void main(String[] args) throws IOException {
        boolean dryRun = false;
        List<Path> packs = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--dry-run")) dryRun = true;
            else packs.add(Paths.get(a));
        }
        boolean interactive = System.console() == null && !GraphicsEnvironment.isHeadless() && packs.isEmpty();
        Patcher patcher = new Patcher(dryRun);
        if (packs.isEmpty()) {
            Path folder = shaderpacksFolder();
            if (folder == null || !Files.isDirectory(folder)) {
                patcher.say("No shaderpacks folder found (looked for .minecraft/shaderpacks). Pass pack paths as arguments.");
            } else {
                patcher.say("Shaderpacks folder: " + folder);
                try (var list = Files.list(folder)) {
                    list.sorted().forEach(packs::add);
                }
            }
        }
        for (Path pack : packs) {
            try {
                patcher.patchPack(pack);
            } catch (IOException | RuntimeException e) {
                patcher.say(pack.getFileName() + ": failed — " + e);
                patcher.failedPacks++;
            }
        }
        patcher.say("");
        patcher.say(patcher.patchedPacks + " pack(s) patched, " + patcher.skippedPacks + " skipped, " + patcher.failedPacks + " failed."
                + (patcher.patchedPacks > 0 ? " Select the \"" + SUFFIX + "\" copy in Iris." : ""));
        if (interactive) {
            JOptionPane.showMessageDialog(null, patcher.log.toString(), "Ovvar shader patcher",
                    patcher.failedPacks > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        }
        System.exit(patcher.failedPacks > 0 ? 1 : 0);
    }

    private void say(String line) {
        System.out.println(line);
        log.append(line).append('\n');
    }

    static Path shaderpacksFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        Path minecraft;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            minecraft = Paths.get(appData != null ? appData : home + "\\AppData\\Roaming", ".minecraft");
        } else if (os.contains("mac")) {
            minecraft = Paths.get(home, "Library", "Application Support", "minecraft");
        } else {
            minecraft = Paths.get(home, ".minecraft");
        }
        return minecraft.resolve("shaderpacks");
    }

    // ---------------------------------------------------------------- packs

    private void patchPack(Path src) throws IOException {
        String name = src.getFileName().toString();
        boolean zip = name.toLowerCase(Locale.ROOT).endsWith(".zip");
        if (name.contains(SUFFIX) || (!zip && !Files.isDirectory(src))) {
            skippedPacks++;
            return;
        }
        Map<String, byte[]> files = zip ? readZip(src) : readFolder(src);
        if (files.keySet().stream().noneMatch(p -> p.startsWith("shaders/"))) {
            say(name + ": no shaders/ folder, not a shaderpack; skipped");
            skippedPacks++;
            return;
        }
        say(name + ":");
        Set<String> done = new HashSet<>();
        int patched = 0;
        for (String[] pair : entityPrograms(files)) {
            String frag = pair[0], vert = pair[1];
            if (!done.add(frag)) continue;
            String fragText = text(files, frag);
            if (fragText.contains(TAG)) {
                say("  " + frag + ": already patched");
                continue;
            }
            String vertText = vert == null ? null : text(files, vert);
            String[] result = patchProgram(files, frag, fragText, vert, vertText);
            if (result == null) continue;
            files.put(frag, result[0].getBytes(StandardCharsets.UTF_8));
            if (vert != null && !vert.equals(frag)) {
                files.put(vert, result[1].getBytes(StandardCharsets.UTF_8));
                done.add(vert);
            }
            patched++;
        }
        if (patched == 0) {
            say("  nothing patched");
            failedPacks++;
            return;
        }
        if (dryRun) {
            patchedPacks++;
            return;
        }
        String outName = zip ? name.substring(0, name.length() - 4) + SUFFIX + ".zip" : name + SUFFIX;
        Path out = src.resolveSibling(outName);
        if (zip) writeZip(out, files);
        else writeFolder(out, files);
        say("  -> " + outName);
        patchedPacks++;
    }

    private static String text(Map<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    /**
     * For every gbuffers_entities*.fsh: [fragment program file, vertex program file]. A thin
     * wrapper that only includes a shared program resolves to that program; the vertex file is
     * the matching .vsh resolved the same way. Unified files (fragment and vertex parts in one)
     * give the same path twice.
     */
    private static List<String[]> entityPrograms(Map<String, byte[]> files) {
        List<String[]> out = new ArrayList<>();
        for (String path : files.keySet()) {
            String file = path.substring(path.lastIndexOf('/') + 1);
            if (!path.startsWith("shaders/") || !file.startsWith("gbuffers_entities") || !file.endsWith(".fsh")) continue;
            String frag = program(files, path);
            String vshPath = path.substring(0, path.length() - 4) + ".vsh";
            String vert = files.containsKey(vshPath) ? program(files, vshPath) : null;
            if (frag.equals(vert) || (vert == null && VERTEX_MARKER.matcher(text(files, frag)).find())) vert = frag;
            out.add(new String[]{frag, vert});
        }
        return out;
    }

    /** The file holding the program's code: the file itself, or the single program it includes. */
    private static String program(Map<String, byte[]> files, String path) {
        String text = text(files, path);
        if (MAIN.matcher(text).find()) return path;
        List<String> includes = new ArrayList<>();
        Matcher inc = INCLUDE.matcher(text);
        while (inc.find()) {
            String resolved = resolveInclude(path, inc.group(1));
            if (files.containsKey(resolved) && MAIN.matcher(text(files, resolved)).find()) includes.add(resolved);
        }
        return includes.isEmpty() ? path : includes.get(0);
    }

    private static String resolveInclude(String from, String include) {
        if (include.startsWith("/")) return "shaders" + include;
        return from.substring(0, from.lastIndexOf('/') + 1) + include;
    }

    // ---------------------------------------------------------------- programs

    /** Returns [patched fragment text, patched vertex text] or null (with a reason logged). */
    private String[] patchProgram(Map<String, byte[]> files, String fragPath, String fragText, String vertPath, String vertText) {
        if (vertPath == null) {
            say("  " + fragPath + ": no matching vertex program found; skipped");
            return null;
        }
        boolean unified = vertPath.equals(fragPath);
        int[] fr = region(fragText, FRAGMENT_MARKER, VERTEX_MARKER);
        String frag = fragText.substring(fr[0], fr[1]);

        String samplerName = albedoSampler(files, fragPath, fragText, new HashSet<>(), 0);
        if (samplerName == null) {
            say("  " + fragPath + ": no albedo sampler (uniform sampler2D gtexture/texture/tex), not even in includes; skipped");
            return null;
        }
        String sampleFn = frag.contains("texture2D(") ? "texture2D" : "texture";

        Decl uv = findUv(frag, "in|varying");
        if (uv == null) {
            say("  " + fragPath + ": no texture-coordinate varying found; skipped");
            return null;
        }
        Matcher main = MAIN.matcher(frag);
        if (!main.find(uv.end)) {
            say("  " + fragPath + ": no void main() after the varying; skipped");
            return null;
        }
        String shadow = "ovvar_v_" + uv.name;

        // Fragment: rename the varying, declare the global, remap at the top of main.
        String helper = HELPER.replace("%SAMPLE%", sampleFn).replace("%SAMPLER%", samplerName);
        String assign = "\n    " + uv.name + " = " + shadow + ";\n    " + uv.name + ".xy = ovvar_remap(" + shadow + ".xy);\n";
        String newFrag = frag.substring(0, uv.start)
                + frag.substring(uv.start, uv.end).replaceFirst("\\b" + uv.name + "\\b", shadow)
                + "\n" + uv.type + " " + uv.name + "; // Ovvar: remapped copy of " + shadow + "\n"
                + frag.substring(uv.end, main.start())
                + helper
                + frag.substring(main.start(), main.end()) + assign
                + frag.substring(main.end());
        String fragOut = fragText.substring(0, fr[0]) + newFrag + fragText.substring(fr[1]);

        // Vertex: rename the declaration and every use.
        String vertSource = unified ? fragOut : vertText;
        int[] vr = unified ? region(vertSource, VERTEX_MARKER, FRAGMENT_MARKER) : new int[]{0, vertSource.length()};
        String vert = vertSource.substring(vr[0], vr[1]);
        Decl vuv = findUvNamed(vert, "out|varying", uv.name);
        if (vuv == null) {
            say("  " + fragPath + ": vertex side (" + vertPath + ") does not declare '" + uv.name + "'; skipped");
            return null;
        }
        String newVert = vert.replaceAll("\\b" + uv.name + "\\b", shadow);
        String vertOut = vertSource.substring(0, vr[0]) + newVert + vertSource.substring(vr[1]);

        say("  " + fragPath + (unified ? "" : " + " + vertPath) + ": '" + uv.name + "' remapped, marker via "
                + sampleFn + "(" + samplerName + ")");
        return unified ? new String[]{vertOut, vertOut} : new String[]{fragOut, vertOut};
    }

    /** The albedo sampler's name, declared in the program or (Complementary) in something it includes. */
    private static String albedoSampler(Map<String, byte[]> files, String path, String text, Set<String> seen, int depth) {
        Matcher m = SAMPLER_DECL.matcher(text);
        if (m.find()) return m.group(2);
        if (depth > 4 || !seen.add(path)) return null;
        Matcher inc = INCLUDE.matcher(text);
        while (inc.find()) {
            String resolved = resolveInclude(path, inc.group(1));
            if (!files.containsKey(resolved)) continue;
            String found = albedoSampler(files, resolved, text(files, resolved), seen, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    /** Start/end of the part of a unified file belonging to one stage, or the whole file. */
    private static int[] region(String text, Pattern own, Pattern other) {
        Matcher m = own.matcher(text);
        if (!m.find()) return new int[]{0, text.length()};
        int start = m.end();
        Matcher o = other.matcher(text);
        int end = o.find(start) ? o.start() : text.length();
        // The other stage may come first: then our region runs from our marker to the end.
        return new int[]{start, end};
    }

    private static final class Decl {
        final int start, end;
        final String type, name;

        Decl(int start, int end, String type, String name) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.name = name;
        }
    }

    /** The texture-coordinate varying: the best-ranked UV_NAMES entry among declarations, non-flat only. */
    private static Decl findUv(String region, String direction) {
        for (String candidate : UV_NAMES) {
            Decl d = findUvNamed(region, direction, candidate);
            if (d != null) return d;
        }
        Matcher m = VARYING.matcher(region);
        while (m.find()) {
            if (m.group(1).contains("flat") || !m.group(2).matches(direction)) continue;
            for (String n : m.group(4).split(",")) {
                String name = n.trim();
                if (name.toLowerCase(Locale.ROOT).contains("texcoord")) return new Decl(m.start(), m.end(), m.group(3), name);
            }
        }
        return null;
    }

    private static Decl findUvNamed(String region, String direction, String wanted) {
        Matcher m = VARYING.matcher(region);
        while (m.find()) {
            if (!m.group(2).matches(direction)) continue;
            for (String n : m.group(4).split(",")) {
                if (n.trim().equals(wanted)) return new Decl(m.start(), m.end(), m.group(3), wanted);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- files

    private static Map<String, byte[]> readZip(Path zip) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = z.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                try (InputStream in = z.getInputStream(e)) {
                    files.put(e.getName(), in.readAllBytes());
                }
            }
        }
        return files;
    }

    private static Map<String, byte[]> readFolder(Path root) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static void writeZip(Path out, Map<String, byte[]> files) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(out))) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
    }

    private static void writeFolder(Path out, Map<String, byte[]> files) throws IOException {
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path p = out.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            try (OutputStream o = Files.newOutputStream(p)) {
                o.write(e.getValue());
            }
        }
    }
}
