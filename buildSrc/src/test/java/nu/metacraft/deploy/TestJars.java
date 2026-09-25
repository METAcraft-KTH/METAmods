package nu.metacraft.deploy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
