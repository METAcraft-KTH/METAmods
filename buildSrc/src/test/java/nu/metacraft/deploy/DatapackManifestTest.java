package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static nu.metacraft.deploy.ManifestTest.A;
import static nu.metacraft.deploy.ManifestTest.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackManifestTest {
    private static DatapackManifest.Pack pack(Map<String, String> files) {
        return new DatapackManifest.Pack(A, "aaaaaaaaaaaa", "vanillatweaks:26.3/a/b", "datapack-heads-aaaaaaaaaaaa.zip",
                new TreeMap<>(files), new DatapackManifest.ResourcePack("resourcepack-heads-aaaaaaaaaaaa.zip", B),
                new TreeMap<>(Map.of("pack.mcmeta", B)));
    }

    @Test
    void roundTripsThroughJson() {
        DatapackManifest m = new DatapackManifest("world", new TreeMap<>(Map.of("heads",
                pack(Map.of("pack.mcmeta", A, "data/heads/function/hi.mcfunction", B)))));
        assertEquals(m, DatapackManifest.parse(m.toJson()));
        DatapackManifest local = new DatapackManifest(null, m.packs());
        assertEquals(local, DatapackManifest.parse(local.toJson()));
    }

    @Test
    void refusesPathsOutsideTheFolder() {
        for (String path : new String[]{"../x", "/etc/x", "a//b", "a/./b", "a\\b", "a/"}) {
            DeployException e = assertThrows(DeployException.class, () -> pack(Map.of("pack.mcmeta", A, path, B)), path);
            assertTrue(e.getMessage().contains("is not a safe relative path"), e.getMessage());
        }
        assertThrows(DeployException.class, () -> new DatapackManifest("../world", new TreeMap<>()));
    }

    @Test
    void refusesAFolderNameThatIsNoPlainName() {
        DeployException e = assertThrows(DeployException.class, () -> new DatapackManifest("world",
                new TreeMap<>(Map.of(".heads", pack(Map.of("pack.mcmeta", A))))));
        assertTrue(e.getMessage().contains("is not a datapack folder name"), e.getMessage());
    }

    @Test
    void aPackHasAPackMcmeta() {
        assertThrows(DeployException.class, () -> pack(Map.of("data/x.json", A)));
    }
}
