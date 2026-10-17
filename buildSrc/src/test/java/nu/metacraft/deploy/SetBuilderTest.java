package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    void splitsADatapackIntoItsFolderAndWritesDatapacksJson() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("downloads/heads.zip"), "{\"pack\":{\"pack_format\":81}}",
                "data/heads/function/load.mcfunction", "say hi");
        String sha256 = Sha256.of(zip);
        DeployList list = new DeployList("test", List.of(),
                List.of(new DeployList.External("more_heads", zip.toUri(), sha256, true)));
        Manifest m = build(list, Map.of());

        assertEquals(Map.of(), m.entries());
        DatapackManifest packs = DatapackManifest.readSet(out());
        DatapackManifest.Pack pack = packs.packs().get("more_heads");
        assertEquals("datapack-more_heads-" + sha256.substring(0, 12) + ".zip", pack.file());
        assertEquals(sha256.substring(0, 12), pack.version());
        assertEquals(sha256, pack.sha256());
        assertEquals(zip.toUri().toString(), pack.source());
        assertEquals(Set.of("pack.mcmeta", "data/heads/function/load.mcfunction"), pack.files().keySet());
        assertEquals(Sha256.of("say hi".getBytes(StandardCharsets.UTF_8)), pack.files().get("data/heads/function/load.mcfunction"));
        assertEquals(null, pack.resourcePack());
        assertEquals(Set.of("manifest.json", "datapacks.json", pack.file()), TestJars.namesIn(out()));
    }

    @Test
    void aSetWithoutDatapacksHasNoDatapacksJson() throws IOException {
        build(new DeployList("test", List.of(), List.of()), Map.of());
        assertEquals(Set.of("manifest.json"), TestJars.namesIn(out()));
    }

    @Test
    void routesADatapacksAssetsToAResourcePackWithMetacraftResourcePacks() throws IOException {
        Path rpMod = TestJars.modJar(tmp.resolve("libs/metacraft-resource-packs-1.jar"), "metacraft-resource-packs", "1");
        Path zip = TestJars.datapackZip(tmp.resolve("downloads/hats.zip"), "{\"pack\":{\"pack_format\":81,\"description\":\"Hats\"}}",
                "data/hats/recipe/hat.json", "{}", "assets/hats/textures/item/hat.png", "png", "pack.png", "icon");
        DeployList list = new DeployList("test", List.of("metacraft-resource-packs"),
                List.of(new DeployList.External("hats", zip.toUri(), Sha256.of(zip), true)));
        build(list, Map.of("metacraft-resource-packs", rpMod));

        DatapackManifest.Pack pack = DatapackManifest.readSet(out()).packs().get("hats");
        assertEquals(Set.of("pack.mcmeta", "pack.png", "data/hats/recipe/hat.json"), pack.files().keySet());
        Path rp = out().resolve(pack.resourcePack().file());
        assertEquals("resourcepack-hats-" + Sha256.of(zip).substring(0, 12) + ".zip", rp.getFileName().toString());
        assertEquals(pack.resourcePack().sha256(), Sha256.of(rp));
        var files = Datapack.read(rp);
        assertEquals(Set.of("pack.mcmeta", "pack.png", "assets/hats/textures/item/hat.png"), files.keySet());
        assertEquals(Map.of("pack", Map.of("description", "Hats", "min_format", 65, "max_format", Integer.MAX_VALUE)),
                new groovy.json.JsonSlurper().parse(files.get("pack.mcmeta"), "UTF-8"));
    }

    @Test
    void aDatapackWithAssetsNeedsMetacraftResourcePacks() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("downloads/hats.zip"), "{\"pack\":{\"pack_format\":81}}",
                "assets/hats/textures/item/hat.png", "png");
        DeployList list = new DeployList("test", List.of(),
                List.of(new DeployList.External("hats", zip.toUri(), Sha256.of(zip), true)));
        DeployException e = assertThrows(DeployException.class, () -> build(list, Map.of()));
        assertEquals(zip.toUri() + " has textures (assets/), which reach players through metacraft-resource-packs:"
                + " add metacraft-resource-packs to deploy/test.txt, or use a pack without assets/", e.getMessage());
    }

    @Test
    void aWrongDatapackHashFailsAndLeavesNothing() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("downloads/heads.zip"), "{\"pack\":{\"pack_format\":81}}");
        DeployList list = new DeployList("test", List.of(),
                List.of(new DeployList.External("more_heads", zip.toUri(), "0".repeat(64), true)));
        DeployException e = assertThrows(DeployException.class, () -> build(list, Map.of()));
        assertTrue(e.getMessage().startsWith("sha256 mismatch for " + zip.toUri()), e.getMessage());
        assertEquals(Set.of(), TestJars.namesIn(out()));
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
