package nu.metacraft.deploy;

import groovy.json.JsonSlurper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** What a deploy needs from a mod's {@code fabric.mod.json}: its id, version and {@code depends} keys. */
public record ModInfo(String id, String version, Set<String> depends) {

    public static ModInfo read(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                throw new DeployException(jar.getFileName() + " has no fabric.mod.json; it is not a Fabric mod");
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return parse(reader, jar.getFileName().toString());
            }
        } catch (IOException e) {
            throw new DeployException("cannot read " + jar.getFileName() + " as a jar: " + e.getMessage(), e);
        }
    }

    /** The id in a project's {@code src/main/resources/fabric.mod.json} (placeholders like {@code ${version}} are fine). */
    public static String readSourceId(Path fabricModJson) {
        try (Reader reader = Files.newBufferedReader(fabricModJson, StandardCharsets.UTF_8)) {
            return parse(reader, fabricModJson.toString()).id();
        } catch (IOException e) {
            throw new DeployException("cannot read " + fabricModJson + ": " + e.getMessage(), e);
        }
    }

    static ModInfo parse(Reader reader, String what) {
        Object json;
        try {
            json = new JsonSlurper().parse(reader);
        } catch (RuntimeException e) {
            throw new DeployException(what + ": fabric.mod.json is not valid JSON: " + e.getMessage(), e);
        }
        if (!(json instanceof Map<?, ?> map) || !(map.get("id") instanceof String id)) {
            throw new DeployException(what + ": fabric.mod.json has no \"id\"");
        }
        String version = map.get("version") instanceof String v ? v : "";
        Set<String> depends = new TreeSet<>();
        if (map.get("depends") instanceof Map<?, ?> deps) {
            for (Object key : deps.keySet()) {
                depends.add(String.valueOf(key));
            }
        }
        return new ModInfo(id, version, Collections.unmodifiableSet(depends));
    }
}
