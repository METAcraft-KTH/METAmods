package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetBuilderTest {
    @TempDir
    Path tmp;

    private static final Set<String> REPO = Set.of("metacraft-lib", "faster-minecarts");

    private Path out() {
        return tmp.resolve("build/deploy/test");
    }

    private Manifest build(DeployList list, Map<String, Path> jars) {
        return SetBuilder.build(list, new TreeMap<>(jars), REPO, out(), SetBuilder.URL_FETCHER);
    }

    @Test
    void copiesJarsFetchesExternalsAndWritesTheManifest() throws IOException {
        Path lib = TestJars.modJar(tmp.resolve("libs/metacraft-lib-1.0.0.jar"), "metacraft-lib", "1.0.0");
        Path carts = TestJars.modJar(tmp.resolve("libs/faster-minecarts-1.0.0.jar"), "faster-minecarts", "1.0.0", "metacraft-lib");
        Path ovvar = TestJars.modJar(tmp.resolve("downloads/ovvar-1.4.0.jar"), "ovvar", "1.4.0", "metacraft-lib");
        Files.createDirectories(out());
        Files.writeString(out().resolve("stale-0.jar"), "left over from an earlier build");

        DeployList list = new DeployList("test", List.of("faster-minecarts"),
                List.of(new DeployList.External("ovvar", ovvar.toUri(), Sha256.of(ovvar))));
        Manifest m = build(list, Map.of("metacraft-lib", lib, "faster-minecarts", carts));

        assertEquals(Set.of("faster-minecarts", "metacraft-lib", "ovvar"), m.entries().keySet());
        assertEquals(new Manifest.Entry("faster-minecarts-1.0.0.jar", Sha256.of(carts), "1.0.0", "faster-minecarts"),
                m.entries().get("faster-minecarts"));
        assertEquals(ovvar.toUri().toString(), m.entries().get("ovvar").source());
        assertEquals(m, Manifest.read(out().resolve("manifest.json")));
        for (Manifest.Entry e : m.entries().values()) {
            assertEquals(e.sha256(), Sha256.of(out().resolve(e.file())));
        }
        assertFalse(Files.exists(out().resolve("stale-0.jar")));
    }

    @Test
    void aWrongExternalHashFailsAndLeavesNoJar() throws IOException {
        Path ovvar = TestJars.modJar(tmp.resolve("downloads/ovvar-1.4.0.jar"), "ovvar", "1.4.0");
        DeployList list = new DeployList("test", List.of(),
                List.of(new DeployList.External("ovvar", ovvar.toUri(), "0".repeat(64))));
        DeployException e = assertThrows(DeployException.class, () -> build(list, Map.of()));
        assertTrue(e.getMessage().startsWith("sha256 mismatch for " + ovvar.toUri()), e.getMessage());
        assertFalse(Files.exists(out().resolve("ovvar-1.4.0.jar")));
    }

    @Test
    void anExternalWithAnotherModIdFails() throws IOException {
        Path jar = TestJars.modJar(tmp.resolve("downloads/ovvar-1.4.0.jar"), "something-else", "1");
        DeployList list = new DeployList("test", List.of(),
                List.of(new DeployList.External("ovvar", jar.toUri(), Sha256.of(jar))));
        DeployException e = assertThrows(DeployException.class, () -> build(list, Map.of()));
        assertTrue(e.getMessage().contains("is mod something-else, but deploy/test.txt calls it ovvar"), e.getMessage());
    }

    @Test
    void refusesAJarWhoseModIdIsMetacraft() throws IOException {
        Path bundle = TestJars.modJar(tmp.resolve("libs/metacraft-1.0.0.jar"), "metacraft", "1.0.0");
        DeployList list = new DeployList("test", List.of("dist"), List.of());
        DeployException e = assertThrows(DeployException.class, () -> build(list, Map.of("dist", bundle)));
        assertTrue(e.getMessage().contains("a list may not contain metacraft"), e.getMessage());
    }

    @Test
    void failsWhenAnInRepoDependencyIsMissing() throws IOException {
        Path carts = TestJars.modJar(tmp.resolve("libs/faster-minecarts-1.0.0.jar"), "faster-minecarts", "1.0.0", "metacraft-lib");
        DeployList list = new DeployList("test", List.of("faster-minecarts"), List.of());
        DeployException e = assertThrows(DeployException.class, () -> build(list, Map.of("faster-minecarts", carts)));
        assertEquals("faster-minecarts needs metacraft-lib, which is not on test's list", e.getMessage());
    }
}
