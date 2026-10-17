package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackTest {
    @TempDir
    Path tmp;

    private static final String SHA = "ab".repeat(32);
    private static final URI SOURCE = URI.create("https://example.org/pack.zip");

    private DatapackManifest.Pack build(Path zip, URI source, Path out) throws IOException {
        Files.createDirectories(out);
        return Datapack.build(zip, "my_pack", SHA, source, "test", true, out);
    }

    private static String text(SortedMap<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void keepsThePackAsItIsOverlaysIncluded() throws IOException {
        String mcmeta = "{\"pack\": {\"min_format\": [100, 0], \"max_format\": [121, 0]},"
                + " \"overlays\": {\"entries\": [{\"directory\": \"o121\", \"min_format\": 121, \"max_format\": 121}]}}";
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), mcmeta, "data/my/function/hi.mcfunction", "say hi",
                "o121/data/my/function/hi.mcfunction", "say hi 121", "fabric.mod.json", "{}");
        DatapackManifest.Pack pack = build(zip, SOURCE, tmp.resolve("out"));

        SortedMap<String, byte[]> files = Datapack.read(tmp.resolve("out").resolve(pack.file()));
        assertEquals(Set.of("pack.mcmeta", "data/my/function/hi.mcfunction", "o121/data/my/function/hi.mcfunction",
                "fabric.mod.json"), files.keySet());
        assertEquals(mcmeta, text(files, "pack.mcmeta"));
        assertEquals("datapack-my_pack-abababababab.zip", pack.file());
        assertNull(pack.resourcePack());
    }

    @Test
    void theSameZipGivesTheSameBytes() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81}}", "data/b.json", "b",
                "data/a.json", "a", "assets/x/y.png", "png");
        DatapackManifest.Pack first = build(zip, SOURCE, tmp.resolve("one"));
        DatapackManifest.Pack second = build(zip, SOURCE, tmp.resolve("two"));
        assertEquals(first, second);
        assertArrayEquals(Files.readAllBytes(tmp.resolve("one").resolve(first.file())),
                Files.readAllBytes(tmp.resolve("two").resolve(second.file())));
        assertArrayEquals(Files.readAllBytes(tmp.resolve("one").resolve(first.resourcePack().file())),
                Files.readAllBytes(tmp.resolve("two").resolve(second.resourcePack().file())));
    }

    @Test
    void addsVanillaTweaksCreditsToBoth() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81}}", "assets/x/y.png", "png");
        DatapackManifest.Pack pack = build(zip, URI.create("vanillatweaks:26.3/items/hats"), tmp.resolve("out"));
        assertEquals(VanillaTweaks.CREDITS, text(Datapack.read(tmp.resolve("out").resolve(pack.file())), "credits.txt"));
        assertEquals(VanillaTweaks.CREDITS, text(Datapack.read(tmp.resolve("out").resolve(pack.resourcePack().file())), "credits.txt"));
    }

    @Test
    void refusesAssetsInAnOverlay() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"),
                "{\"pack\":{\"pack_format\":81},\"overlays\":{\"entries\":[{\"directory\":\"o\",\"formats\":81}]}}",
                "o/assets/x/y.png", "png");
        DeployException e = assertThrows(DeployException.class, () -> build(zip, SOURCE, tmp.resolve("out")));
        assertTrue(e.getMessage().contains("its overlay o has assets/"), e.getMessage());
    }

    @Test
    void refusesAnEntryOutsideTheFolder() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81}}", "../../evil.txt", "x");
        DeployException e = assertThrows(DeployException.class, () -> build(zip, SOURCE, tmp.resolve("out")));
        assertTrue(e.getMessage().contains("'../../evil.txt' is not a safe relative path"), e.getMessage());
    }

    @Test
    void refusesAZipWithoutPackMcmeta() throws IOException {
        Path zip = tmp.resolve("p.zip");
        TestJars.modJar(zip, "a", "1");
        DeployException e = assertThrows(DeployException.class, () -> build(zip, SOURCE, tmp.resolve("out")));
        assertEquals(SOURCE + " has no pack.mcmeta at its root; it is not a datapack zip", e.getMessage());
    }

    @Test
    void theResourcePackKeepsADescriptionComponent() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81,\"description\":{\"text\":\"Hats\",\"color\":\"gold\"}}}",
                "assets/x/y.png", "png");
        DatapackManifest.Pack pack = build(zip, SOURCE, tmp.resolve("out"));
        Object json = new groovy.json.JsonSlurper().parse(Datapack.read(tmp.resolve("out").resolve(pack.resourcePack().file())).get("pack.mcmeta"), "UTF-8");
        assertEquals(Map.of("pack", Map.of("description", Map.of("text", "Hats", "color", "gold"), "min_format", 65,
                "max_format", Integer.MAX_VALUE)), json);
    }
}
