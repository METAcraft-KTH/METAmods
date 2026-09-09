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
 * Patches Iris/OptiFine shaderpacks so Ovvar's overalls show their patches, and different ones
 * on the left and right limbs.
 *
 * Ovvar (metacraft.se) ships a vanilla core-shader override that a shaderpack replaces wholesale,
 * so shaderpack users see plain overalls with mirrored limbs. This tool puts the same code
 * (ovvar.glsl, bundled in this jar) into the pack's own entity program:
 * <ul>
 *   <li>fragment stage: right after the texture-coordinate varying's declaration, a global of
 *       the same type is declared and the varying's name is {@code #define}d to it, so every use
 *       — in the file, in its includes, in macros — reads the global; the same for the vertex
 *       colour varying. At the top of main() the globals are set to the remapped coordinate and
 *       the un-dyed colour;</li>
 *   <li>vertex stage: a varying {@code ovvar_color} carries the raw vertex colour (the dye
 *       colour, which holds the patch bits) to the fragment stage.</li>
 * </ul>
 * Nothing else in the pack is touched; originals are kept. Textures that are not Ovvar's are
 * sampled exactly as before.
 *
 * Double-click: patches every pack in the Minecraft shaderpacks folder and shows a summary.
 * Command line: {@code java -jar OvvarShaderPatcher.jar [pack.zip|folder ...] [--dry-run]}.
 */
public final class Patcher {
    private static final String SUFFIX = "+ovvar";
    private static final String TAG = "ovvar_uv";
    private static final String GLSL = readResource("/ovvar.glsl");

    /** Texture-coordinate varying names, most likely first. */
    private static final List<String> UV_NAMES = Arrays.asList(
            "texCoord", "texcoord", "uv", "TexCoord", "texCoords", "texcoords", "lmtexcoord", "vTexCoord", "coord");
    /** Vertex-colour varying names, most likely first. */
    private static final List<String> COLOR_NAMES = Arrays.asList(
            "color", "glcolor", "glColor", "vColor", "vertexColor", "vcolor", "colour", "vertColor", "col", "tint", "tintColor");
    private static final String ALBEDO = "gtexture|texture|tex|gcolor";

    private static final Pattern FRAGMENT_MARKER = Pattern.compile("(?m)^\\s*#\\s*if(?:def)?\\s+.*\\b(FSH|FRAGMENT_SHADER|FRAGMENT)\\b");
    private static final Pattern VERTEX_MARKER = Pattern.compile("(?m)^\\s*#\\s*if(?:def)?\\s+.*\\b(VSH|VERTEX_SHADER|VERTEX)\\b");
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#\\s*include\\s+\"([^\"]+)\"");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#\\s*version\\s+(\\d+)");
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
        String sampleFn = glslVersion(fragText) < 130 ? "texture2D" : "texture";

        Decl uv = findVarying(frag, "in|varying", UV_NAMES, false, "texcoord");
        if (uv == null) {
            say("  " + fragPath + ": no texture-coordinate varying found; skipped");
            return null;
        }
        Decl color = findVarying(frag, "in|varying", COLOR_NAMES, true, "col", "tint");
        if (color == null) {
            say("  " + fragPath + ": no vertex-colour varying (color/glcolor/vColor/tint...) found; skipped");
            return null;
        }
        if (!color.type.equals("vec4") && !color.type.equals("vec3")) {
            say("  " + fragPath + ": vertex-colour varying '" + color.name + "' is a " + color.type + ", not vec3/vec4; skipped");
            return null;
        }
        Matcher main = MAIN.matcher(frag);
        if (!main.find(Math.max(uv.end, color.end))) {
            say("  " + fragPath + ": no void main() after the varyings; skipped");
            return null;
        }

        // Fragment: shadow both varyings, add the raw-colour varying and the shader code, set the
        // shadows at the top of main. Edits are applied back to front so indices stay valid.
        String helper = "\n// --- Ovvar (metacraft.se): patches on student overalls; see README.txt in OvvarShaderPatcher.jar\n"
                + uv.direction + " vec4 ovvar_color;\n"
                + "#define OVVAR_SAMPLE(uv) " + sampleFn + "(" + samplerName + ", uv)\n"
                + GLSL
                + "// --- end Ovvar\n\n";
        String assign = "\n    " + shadow(uv) + " = ovvar_raw_" + uv.name + "();\n"
                + "    " + shadow(uv) + ".xy = ovvar_uv(" + shadow(uv) + ".xy);\n"
                + "    " + shadow(color) + " = " + (color.type.equals("vec4")
                        ? "ovvar_shade(ovvar_raw_" + color.name + "())"
                        : "ovvar_shade(vec4(ovvar_raw_" + color.name + "(), 1.0)).rgb") + ";\n";
        StringBuilder out = new StringBuilder(frag);
        out.insert(main.end(), assign);
        out.insert(main.start(), helper);
        // The later declaration first.
        Decl first = uv.end <= color.end ? uv : color, second = first == uv ? color : uv;
        out.insert(second.end, shadowDecl(second));
        out.insert(first.end, shadowDecl(first));
        if (unified) out.append("\n#undef ").append(uv.name).append("\n#undef ").append(color.name).append("\n");
        String fragOut = fragText.substring(0, fr[0]) + out + fragText.substring(fr[1]);

        // Vertex: pass the raw vertex colour along.
        String vertSource = unified ? fragOut : vertText;
        int[] vr = unified ? region(vertSource, VERTEX_MARKER, FRAGMENT_MARKER) : new int[]{0, vertSource.length()};
        String vert = vertSource.substring(vr[0], vr[1]);
        Matcher vmain = MAIN.matcher(vert);
        if (!vmain.find()) {
            say("  " + fragPath + ": vertex side (" + vertPath + ") has no void main(); skipped");
            return null;
        }
        Decl vuv = findVaryingNamed(vert, "out|varying", uv.name);
        String direction = vuv != null ? vuv.direction : uv.direction.equals("in") ? "out" : "varying";
        String attribute = vert.contains("vaColor") ? "vaColor" : "gl_Color";
        String newVert = vert.substring(0, vmain.start())
                + "\n// --- Ovvar: the raw vertex colour (an ovve's dye colour holds its patch bits)\n"
                + direction + " vec4 ovvar_color;\n\n"
                + vert.substring(vmain.start(), vmain.end())
                + "\n    ovvar_color = " + attribute + ";\n"
                + vert.substring(vmain.end());
        String vertOut = vertSource.substring(0, vr[0]) + newVert + vertSource.substring(vr[1]);

        say("  " + fragPath + (unified ? "" : " + " + vertPath) + ": '" + uv.name + "' and '" + color.name
                + "' shadowed, art via " + sampleFn + "(" + samplerName + "), colour from " + attribute);
        return unified ? new String[]{vertOut, vertOut} : new String[]{fragOut, vertOut};
    }

    private static String shadow(Decl d) {
        return "ovvar_shadow_" + d.name;
    }

    /** A global standing in for the varying, a function still reading the real one, and the name switch. */
    private static String shadowDecl(Decl d) {
        return "\n" + d.type + " " + shadow(d) + "; // Ovvar: stands in for " + d.name + " from here on\n"
                + d.type + " ovvar_raw_" + d.name + "() { return " + d.name + "; }\n"
                + "#define " + d.name + " " + shadow(d) + "\n";
    }

    private static int glslVersion(String text) {
        Matcher m = VERSION.matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));
        return text.contains("texture2D(") ? 120 : 330;
    }

    private static String readResource(String name) {
        try (InputStream in = Patcher.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException(name + " is missing from the patcher jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
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
        final String direction, type, name;

        Decl(int start, int end, String direction, String type, String name) {
            this.start = start;
            this.end = end;
            this.direction = direction;
            this.type = type;
            this.name = name;
        }
    }

    /** The best-ranked named varying, else the first one whose name contains a hint (flat ones only if allowed). */
    private static Decl findVarying(String region, String direction, List<String> names, boolean allowFlat, String... hints) {
        for (String candidate : names) {
            Decl d = findVaryingNamed(region, direction, candidate);
            if (d != null) return d;
        }
        for (String hint : hints) {
            Matcher m = VARYING.matcher(region);
            while (m.find()) {
                if ((!allowFlat && m.group(1).contains("flat")) || !m.group(2).matches(direction)) continue;
                for (String n : m.group(4).split(",")) {
                    String name = n.trim();
                    if (name.toLowerCase(Locale.ROOT).contains(hint)) return new Decl(m.start(), m.end(), m.group(2), m.group(3), name);
                }
            }
        }
        return null;
    }

    private static Decl findVaryingNamed(String region, String direction, String wanted) {
        Matcher m = VARYING.matcher(region);
        while (m.find()) {
            if (!m.group(2).matches(direction)) continue;
            for (String n : m.group(4).split(",")) {
                if (n.trim().equals(wanted)) return new Decl(m.start(), m.end(), m.group(2), m.group(3), wanted);
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
