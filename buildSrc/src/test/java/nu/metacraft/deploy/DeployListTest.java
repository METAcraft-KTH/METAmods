package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployListTest {
    private static final String HASH = "3f2a" + "0".repeat(60);

    private static String failure(List<String> lines) {
        return assertThrows(DeployException.class, () -> DeployList.parse("test", lines)).getMessage();
    }

    @Test
    void parsesProjectsExternalsAndComments() {
        DeployList list = DeployList.parse("survival", List.of(
                "# survival: what prod shipped in Season 5",
                "",
                "better-pets",
                "   faster-minecarts   # a trailing comment",
                "external ovvar https://example.org/ovvar-1.4.0.jar sha256:" + HASH.toUpperCase()));
        assertEquals("survival", list.server());
        assertEquals(List.of("better-pets", "faster-minecarts"), list.projects());
        assertEquals(List.of(new DeployList.External("ovvar", URI.create("https://example.org/ovvar-1.4.0.jar"), HASH)),
                list.externals());
    }

    @Test
    void keepsAHashInsideAUrl() {
        DeployList list = DeployList.parse("test",
                List.of("external ovvar https://example.org/ovvar.jar#v1 sha256:" + HASH + " # pinned"));
        assertEquals(URI.create("https://example.org/ovvar.jar#v1"), list.externals().get(0).url());
    }

    @Test
    void refusesMetacraft() {
        String message = failure(List.of("better-pets", "metacraft"));
        assertTrue(message.contains("a list may not contain metacraft"), message);
        assertTrue(message.contains("deploy/test.txt line 2"), message);
    }

    @Test
    void refusesAnExternalMetacraft() {
        String message = failure(List.of("external metacraft https://example.org/m.jar sha256:" + HASH));
        assertTrue(message.contains("a list may not contain metacraft"), message);
    }

    @Test
    void refusesDuplicates() {
        assertTrue(failure(List.of("better-pets", "better-pets")).contains("better-pets is listed twice"));
    }

    @Test
    void refusesMalformedExternals() {
        assertTrue(failure(List.of("external ovvar https://example.org/o.jar"))
                .contains("expected 'external <mod-id> <url> sha256:<hex>'"));
        assertTrue(failure(List.of("external ovvar https://example.org/o.jar " + HASH)).contains("sha256:<hex>"));
        assertTrue(failure(List.of("external ovvar https://example.org/o.jar sha256:abc")).contains("64 hex digits"));
        assertTrue(failure(List.of("external ovvar ftp://example.org/o.jar sha256:" + HASH)).contains("is not an https URL"));
    }

    @Test
    void refusesTwoWordsOnAProjectLine() {
        assertTrue(failure(List.of("better-pets faster-minecarts")).contains("one project name per line"));
    }

    @Test
    void readFailsForAMissingList(@TempDir Path dir) {
        DeployException e = assertThrows(DeployException.class, () -> DeployList.read("nope", dir.resolve("nope.txt")));
        assertTrue(e.getMessage().startsWith("no list for server nope"), e.getMessage());
    }
}
