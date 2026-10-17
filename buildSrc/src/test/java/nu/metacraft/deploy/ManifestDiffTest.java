package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static nu.metacraft.deploy.ManifestTest.A;
import static nu.metacraft.deploy.ManifestTest.B;
import static nu.metacraft.deploy.ManifestTest.manifest;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManifestDiffTest {
    @Test
    void splitsIntoUploadRemoveAndUnchanged() {
        Manifest old = manifest(Map.of(
                "same", new Manifest.Entry("same-1.jar", A, "1", "same"),
                "changed", new Manifest.Entry("changed-1.jar", A, "1", "changed"),
                "gone", new Manifest.Entry("gone-1.jar", A, "1", "gone")));
        Manifest now = manifest(Map.of(
                "same", new Manifest.Entry("same-1.jar", A, "1", "same"),
                "changed", new Manifest.Entry("changed-1.jar", B, "1", "changed"),
                "new", new Manifest.Entry("new-1.jar", A, "1", "new")));
        ManifestDiff diff = ManifestDiff.of(old, now);
        assertEquals(Set.of("changed", "new"), diff.upload());
        assertEquals(Set.of("gone"), diff.remove());
        assertEquals(Set.of("same"), diff.unchanged());
    }

    @Test
    void aRenamedFileIsUploadedEvenWithTheSameHash() {
        Manifest old = manifest(Map.of("a", new Manifest.Entry("a-1.jar", A, "1", "a")));
        Manifest now = manifest(Map.of("a", new Manifest.Entry("a-2.jar", A, "2", "a")));
        assertEquals(Set.of("a"), ManifestDiff.of(old, now).upload());
    }

    @Test
    void fromNothingEverythingIsUploaded() {
        Manifest now = manifest(Map.of("a", new Manifest.Entry("a-1.jar", A, "1", "a")));
        ManifestDiff diff = ManifestDiff.of(Manifest.empty(), now);
        assertEquals(Set.of("a"), diff.upload());
        assertEquals(Set.of(), diff.remove());
    }
}
