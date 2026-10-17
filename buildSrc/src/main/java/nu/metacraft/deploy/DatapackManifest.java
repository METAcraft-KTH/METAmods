package nu.metacraft.deploy;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The datapacks of a set, by folder name: {@code build/deploy/<server>/datapacks.json} locally (no
 * world) and {@code mods/metacraft-deploy-datapacks.json} on a server, where it records which folders
 * in {@code <world>/datapacks} the deploy put there and what each held, file by file. A folder that no
 * longer holds exactly those files was edited on the server, and the deploy leaves it alone.
 */
public record DatapackManifest(String world, SortedMap<String, DatapackManifest.Pack> packs) {
    public static final String FILE_NAME = "datapacks.json";

    /** A datapack's folder name: also its pack id ({@code file/<name>}) and its resource pack's file name. */
    public static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern ZIP = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*\\.zip");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** The zip of the pack's {@code assets/}, for metacraft-resource-packs. */
    public record ResourcePack(String file, String sha256) {
        public ResourcePack {
            checkZip(file);
            checkSha256(sha256, file);
        }
    }

    /**
     * One datapack. {@code sha256}, {@code version} and {@code source} name the pinned zip it came
     * from; {@code file} is the set's zip of the folder; {@code files} maps each path in the folder to
     * its sha256. {@code pending} is only in a server's manifest while a deploy is replacing the
     * folder: the files it is putting there, which the folder may hold if that deploy was interrupted.
     */
    public record Pack(String sha256, String version, String source, String file, SortedMap<String, String> files,
                       ResourcePack resourcePack, SortedMap<String, String> pending) {
        public Pack {
            checkSha256(sha256, file);
            checkZip(file);
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(source, "source");
            files = checkFiles(files, file);
            if (!files.containsKey("pack.mcmeta")) {
                throw new DeployException("datapack manifest: " + file + " has no pack.mcmeta");
            }
            pending = pending == null ? null : checkFiles(pending, file);
        }

        public Pack withPending(SortedMap<String, String> pending) {
            return new Pack(sha256, version, source, file, files, resourcePack, pending);
        }

        public Pack withResourcePack(ResourcePack resourcePack) {
            return new Pack(sha256, version, source, file, files, resourcePack, pending);
        }
    }

    public DatapackManifest {
        if (world != null) {
            checkPath(world, "level-name");
        }
        SortedMap<String, Pack> copy = new TreeMap<>(packs);
        for (String name : copy.keySet()) {
            if (!NAME.matcher(name).matches()) {
                throw new DeployException("datapack manifest: '" + name + "' is not a datapack folder name");
            }
        }
        packs = Collections.unmodifiableSortedMap(copy);
    }

    public static DatapackManifest empty() {
        return new DatapackManifest(null, new TreeMap<>());
    }

    public DatapackManifest withWorld(String world) {
        return new DatapackManifest(world, packs);
    }

    /** A {@code /}-separated relative path with no empty, {@code .} or {@code ..} part, as zip entries and level names must be. */
    public static String checkPath(String path, String what) {
        boolean ok = path != null && !path.isEmpty() && !path.startsWith("/") && !path.endsWith("/")
                && path.indexOf('\\') < 0 && path.chars().noneMatch(c -> c < 0x20 || c == 0x7f);
        if (ok) {
            for (String part : path.split("/", -1)) {
                if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            throw new DeployException("'" + path + "' is not a safe relative path for a " + what);
        }
        return path;
    }

    private static void checkZip(String file) {
        if (file == null || !ZIP.matcher(file).matches()) {
            throw new DeployException("datapack manifest: '" + file + "' is not a plain zip file name");
        }
    }

    private static void checkSha256(String sha256, String file) {
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new DeployException("datapack manifest: '" + sha256 + "' is not a sha256 for " + file);
        }
    }

    private static SortedMap<String, String> checkFiles(Map<String, String> files, String pack) {
        if (files == null) {
            throw new DeployException("datapack manifest: " + pack + " has no files");
        }
        SortedMap<String, String> copy = new TreeMap<>();
        files.forEach((path, sha256) -> {
            checkPath(path, "file in a datapack");
            checkSha256(sha256, pack + ": " + path);
            copy.put(path, sha256);
        });
        return Collections.unmodifiableSortedMap(copy);
    }

    public static DatapackManifest parse(String json) {
        Object parsed;
        try {
            parsed = new JsonSlurper().parseText(json);
        } catch (RuntimeException e) {
            throw new DeployException("datapack manifest is not valid JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("packs") instanceof Map<?, ?> packMap)) {
            throw new DeployException("datapack manifest must be a JSON object with \"packs\"");
        }
        Object world = root.get("world");
        if (world != null && !(world instanceof String)) {
            throw new DeployException("datapack manifest: \"world\" must be a string");
        }
        SortedMap<String, Pack> packs = new TreeMap<>();
        for (Map.Entry<?, ?> e : packMap.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> fields)) {
                throw new DeployException("datapack manifest: " + name + " must be an object");
            }
            ResourcePack resourcePack = null;
            if (fields.get("resource_pack") instanceof Map<?, ?> rp) {
                resourcePack = new ResourcePack(string(rp, "file", name), string(rp, "sha256", name));
            } else if (fields.get("resource_pack") != null) {
                throw new DeployException("datapack manifest: " + name + "'s \"resource_pack\" must be an object");
            }
            packs.put(name, new Pack(string(fields, "sha256", name), string(fields, "version", name),
                    string(fields, "source", name), string(fields, "file", name), files(fields, "files", name),
                    resourcePack, fields.get("pending") == null ? null : files(fields, "pending", name)));
        }
        return new DatapackManifest((String) world, packs);
    }

    /** The set's datapack manifest, or an empty one if the set has none (a set without datapacks). */
    public static DatapackManifest readSet(Path deployDir) {
        Path file = deployDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return empty();
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DeployException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public String toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (world != null) {
            out.put("world", world);
        }
        Map<String, Object> packMap = new LinkedHashMap<>();
        packs.forEach((name, pack) -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("sha256", pack.sha256());
            fields.put("version", pack.version());
            fields.put("source", pack.source());
            fields.put("file", pack.file());
            fields.put("files", pack.files());
            if (pack.resourcePack() != null) {
                Map<String, Object> rp = new LinkedHashMap<>();
                rp.put("file", pack.resourcePack().file());
                rp.put("sha256", pack.resourcePack().sha256());
                fields.put("resource_pack", rp);
            }
            if (pack.pending() != null) {
                fields.put("pending", pack.pending());
            }
            packMap.put(name, fields);
        });
        out.put("packs", packMap);
        return JsonOutput.prettyPrint(JsonOutput.toJson(out)) + "\n";
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    }

    private static String string(Map<?, ?> fields, String key, String name) {
        if (!(fields.get(key) instanceof String value)) {
            throw new DeployException("datapack manifest: " + name + " has no string \"" + key + "\"");
        }
        return value;
    }

    private static SortedMap<String, String> files(Map<?, ?> fields, String key, String name) {
        if (!(fields.get(key) instanceof Map<?, ?> map)) {
            throw new DeployException("datapack manifest: " + name + " has no \"" + key + "\" object");
        }
        SortedMap<String, String> files = new TreeMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getValue() instanceof String sha256)) {
                throw new DeployException("datapack manifest: " + name + "'s " + e.getKey() + " has no sha256");
            }
            files.put(String.valueOf(e.getKey()), sha256);
        }
        return files;
    }
}
