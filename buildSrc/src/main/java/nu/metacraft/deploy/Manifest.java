package nu.metacraft.deploy;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * What a deploy contains, by mod id: {@code build/deploy/<server>/manifest.json} locally and
 * {@code mods/metacraft-deploy.json} on a server. It is also read back from releases and servers,
 * so file names are checked to be plain jar names.
 */
public record Manifest(SortedMap<String, Manifest.Entry> entries) {
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*\\.jar");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public record Entry(String file, String sha256, String version, String source) {
        public Entry {
            if (file == null || !FILE.matcher(file).matches()) {
                throw new DeployException("manifest: '" + file + "' is not a plain jar file name");
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new DeployException("manifest: '" + sha256 + "' is not a sha256 for " + file);
            }
            if (version == null || source == null) {
                throw new DeployException("manifest: " + file + " needs a version and a source");
            }
        }
    }

    public Manifest {
        SortedMap<String, Entry> copy = new TreeMap<>(entries);
        Set<String> files = new HashSet<>();
        for (Entry entry : copy.values()) {
            if (!files.add(entry.file())) {
                throw new DeployException("manifest: two mods share the file " + entry.file());
            }
        }
        entries = Collections.unmodifiableSortedMap(copy);
    }

    public static Manifest empty() {
        return new Manifest(new TreeMap<>());
    }

    public static Manifest parse(String json) {
        Object parsed;
        try {
            parsed = new JsonSlurper().parseText(json);
        } catch (RuntimeException e) {
            throw new DeployException("manifest is not valid JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new DeployException("manifest must be a JSON object of mod id to entry");
        }
        SortedMap<String, Entry> entries = new TreeMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String id = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> fields)) {
                throw new DeployException("manifest entry " + id + " must be an object");
            }
            entries.put(id, new Entry(string(fields, "file", id), string(fields, "sha256", id),
                    string(fields, "version", id), string(fields, "source", id)));
        }
        return new Manifest(entries);
    }

    public static Manifest read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new DeployException("no " + file.getFileName() + " in " + file.getParent()
                    + "; run deploySet first, or download a release into it");
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DeployException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public String toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        entries.forEach((id, entry) -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("file", entry.file());
            fields.put("sha256", entry.sha256());
            fields.put("version", entry.version());
            fields.put("source", entry.source());
            out.put(id, fields);
        });
        return JsonOutput.prettyPrint(JsonOutput.toJson(out)) + "\n";
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    }

    private static String string(Map<?, ?> fields, String key, String id) {
        if (!(fields.get(key) instanceof String value)) {
            throw new DeployException("manifest entry " + id + " has no string \"" + key + "\"");
        }
        return value;
    }
}
