package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static nu.metacraft.deploy.ManifestTest.A;
import static nu.metacraft.deploy.ManifestTest.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

                **Needs a restart**: mod jars changed. Changes apply at test's next restart.
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

    private static SortedSet<String> set(String... names) {
        return new TreeSet<>(List.of(names));
    }

    private static Deployer.Report report(SortedSet<String> uploaded, SortedSet<String> datapacksUpdated,
                                          SortedSet<String> worldgen, SortedSet<String> resourcePacks) {
        DatapackManifest.Pack pack = new DatapackManifest.Pack(A, "abc", "https://example.org/p.zip", "datapack-p-abc.zip",
                new TreeMap<>(Map.of("pack.mcmeta", B)), null, null);
        DatapackDeployer.Report packs = new DatapackDeployer.Report("world", null, set(), datapacksUpdated, set(), set(),
                List.of(), resourcePacks, set(), set(), worldgen, new DatapackManifest("world", new TreeMap<>(Map.of("p", pack))));
        return new Deployer.Report("test", false, false, uploaded, set(), set("a", "b"), MANIFEST, packs);
    }

    @Test
    void datapacksOnlyApplyWithReload() {
        assertEquals("Datapacks changed: apply with `/reload` or the next restart.",
                Summary.applies(report(set(), set("p"), set(), set())));
    }

    @Test
    void aChangedModJarNeedsARestartWhateverElseChanged() {
        assertEquals("**Needs a restart**: mod jars changed. Changes apply at test's next restart.",
                Summary.applies(report(set("b"), set("p"), set(), set())));
    }

    @Test
    void aChangedPackWithWorldgenNeedsARestart() {
        assertEquals("**Needs a restart**: `p` changed worldgen or dimensions, which `/reload` doesn't apply.",
                Summary.applies(report(set(), set("p"), set("p"), set())));
    }

    @Test
    void resourcePacksApplyWithTheirOwnReload() {
        assertEquals("Datapacks changed: apply with `/reload` or the next restart.\n"
                        + "Resource packs changed: apply with `/metacraft-resource-pack reload` or the next restart.",
                Summary.applies(report(set(), set("p"), set(), set("p"))));
    }

    @Test
    void saysWhenNothingChanged() {
        assertEquals("Nothing changed.", Summary.applies(report(set(), set(), set(), set())));
    }

    @Test
    void listsTheDatapacksWithTheirFolder() {
        String md = Summary.markdown(report(set(), set("p"), set(), set()));
        assertTrue(md.contains("#### datapacks in `world/datapacks`\n\n"), md);
        assertTrue(md.contains("**updated** (1)\n- `p` abc\n"), md);
    }

    @Test
    void aSetWithoutDatapacksHasNoDatapackSection() {
        Deployer.Report report = new Deployer.Report("test", false, false, set(), set(), set("a"), MANIFEST);
        String md = Summary.markdown(report);
        assertFalse(md.contains("datapacks"), md);
        assertTrue(md.endsWith("Nothing changed.\n"), md);
    }
}
