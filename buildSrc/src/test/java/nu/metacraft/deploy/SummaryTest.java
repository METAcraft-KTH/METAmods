package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.List;

import static nu.metacraft.deploy.ManifestTest.A;
import static nu.metacraft.deploy.ManifestTest.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryTest {
    private static final Manifest MANIFEST = new Manifest(new TreeMap<>(Map.of(
            "a", new Manifest.Entry("a-1.jar", A, "1", "a"),
            "b", new Manifest.Entry("b-2.jar", B, "2", "b"))));

    @Test
    void listsUploadedRemovedAndUnchanged() {
        Deployer.Report report = new Deployer.Report("test", true, false, new TreeSet<>(List.of("b")),
                new TreeSet<>(List.of("metacraft")), new TreeSet<>(List.of("a")), MANIFEST);
        assertEquals("""
                ### test

                First deploy with separate jars: the `metacraft` bundle is deleted at the next restart.

                **uploaded** (1)
                - `b` 2

                **removed at next restart** (1)
                - `metacraft`

                **unchanged** (1)
                - `a` 1

                Changes apply at test's next restart.
                """, Summary.markdown(report));
    }

    @Test
    void marksADryRunAndEmptySections() {
        Deployer.Report report = new Deployer.Report("survival", false, true, new TreeSet<>(), new TreeSet<>(),
                new TreeSet<>(List.of("a", "b")), MANIFEST);
        String md = Summary.markdown(report);
        assertTrue(md.startsWith("### survival (dry run: nothing was uploaded)\n"), md);
        assertTrue(md.contains("**uploaded** (0)\n- none\n"), md);
    }
}
