package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestTest {
    static final String A = "a".repeat(64);
    static final String B = "b".repeat(64);

    static Manifest manifest(Map<String, Manifest.Entry> entries) {
        return new Manifest(new TreeMap<>(entries));
    }

    @Test
    void roundTripsThroughJsonAndDisk(@TempDir Path tmp) throws IOException {
        Manifest m = manifest(Map.of(
                "faster-minecarts", new Manifest.Entry("faster-minecarts-1.0.0.jar", A, "1.0.0", "faster-minecarts"),
                "ovvar", new Manifest.Entry("ovvar-1.4.0.jar", B, "1.4.0", "https://example.org/ovvar-1.4.0.jar")));
        assertEquals(m, Manifest.parse(m.toJson()));
        m.write(tmp.resolve("manifest.json"));
        assertEquals(m, Manifest.read(tmp.resolve("manifest.json")));
        assertTrue(m.toJson().indexOf("\"faster-minecarts\"") < m.toJson().indexOf("\"ovvar\""), "ids are sorted");
    }

    @Test
    void writesTheSpecShape() {
        Manifest m = manifest(Map.of("a", new Manifest.Entry("a-1.jar", A, "1", "a")));
        String compact = m.toJson().replaceAll("\\s", "");
        assertEquals("{\"a\":{\"file\":\"a-1.jar\",\"sha256\":\"" + A + "\",\"version\":\"1\",\"source\":\"a\"}}", compact);
    }

    @Test
    void rejectsFileNamesThatAreNotPlainJarNames() {
        for (String bad : new String[]{"../server.jar", "sub/a.jar", "a.zip", ".hidden.jar", ""}) {
            String json = "{\"a\":{\"file\":\"" + bad + "\",\"sha256\":\"" + A + "\",\"version\":\"1\",\"source\":\"a\"}}";
            DeployException e = assertThrows(DeployException.class, () -> Manifest.parse(json), bad);
            assertTrue(e.getMessage().contains("is not a plain jar file name"), e.getMessage());
        }
    }

    @Test
    void rejectsTwoEntriesWithTheSameFile() {
        DeployException e = assertThrows(DeployException.class, () -> manifest(Map.of(
                "a", new Manifest.Entry("x.jar", A, "1", "a"),
                "b", new Manifest.Entry("x.jar", B, "1", "b"))));
        assertTrue(e.getMessage().contains("two mods share the file x.jar"), e.getMessage());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(DeployException.class, () -> Manifest.parse(""));
        assertThrows(DeployException.class, () -> Manifest.parse("[]"));
        assertThrows(DeployException.class, () -> Manifest.parse("{\"a\":{\"file\":\"a.jar\"}}"));
        assertThrows(DeployException.class, () -> Manifest.parse("{\"a\":{\"file\":\"a.jar\",\"sha256\":\"xyz\",\"version\":\"1\",\"source\":\"a\"}}"));
    }

    @Test
    void emptyManifestHasNoEntries() {
        assertEquals(0, Manifest.empty().entries().size());
        assertEquals(Manifest.empty(), Manifest.parse(Manifest.empty().toJson()));
    }
}
