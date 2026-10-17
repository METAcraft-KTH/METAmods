package nu.metacraft.deploy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small jars for tests. The jar's bytes change with its version. */
final class TestJars {
    private TestJars() {
    }

    static Path modJar(Path jar, String id, String version, String... depends) throws IOException {
        StringBuilder deps = new StringBuilder();
        for (String dep : depends) {
            if (deps.length() > 0) {
                deps.append(',');
            }
            deps.append('"').append(dep).append("\":\"*\"");
        }
        String json = "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version
                + "\",\"depends\":{" + deps + "}}";
        return zip(jar, "fabric.mod.json", json);
    }

    static Path plainJar(Path jar) throws IOException {
        return zip(jar, "readme.txt", "not a mod");
    }

    /** A datapack zip: pack.mcmeta, then name/content pairs. */
    static Path datapackZip(Path zip, String mcmeta, String... namesAndContents) throws IOException {
        Files.createDirectories(zip.toAbsolutePath().getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            put(out, "pack.mcmeta", mcmeta);
            for (int i = 0; i < namesAndContents.length; i += 2) {
                put(out, namesAndContents[i], namesAndContents[i + 1]);
            }
        }
        return zip;
    }

    /** The file names directly in {@code dir}. */
    static Set<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private static void put(ZipOutputStream out, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        out.putNextEntry(entry);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static Path zip(Path jar, String entryName, String content) throws IOException {
        Files.createDirectories(jar.toAbsolutePath().getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            ZipEntry entry = new ZipEntry(entryName);
            entry.setTime(0);
            out.putNextEntry(entry);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}
