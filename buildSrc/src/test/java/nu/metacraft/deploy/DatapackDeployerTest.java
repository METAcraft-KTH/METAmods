package nu.metacraft.deploy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static nu.metacraft.deploy.DatapackDeployer.REMOTE_MANIFEST;
import static nu.metacraft.deploy.DatapackDeployer.RESOURCE_PACK_DIR;
import static nu.metacraft.deploy.DatapackDeployer.RESOURCE_PACK_UPDATE_DIR;
import static nu.metacraft.deploy.Deployer.MARKER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackDeployerTest {
    private static final String MCMETA = "{\"pack\":{\"pack_format\":81}}";
    private static final String DIR = "world/datapacks";

    @TempDir
    Path tmp;
    InMemoryRemoteFiles remote;

    @BeforeEach
    void server() {
        remote = new InMemoryRemoteFiles();
        remote.put(MARKER, "1.1\n");
    }

    /** Files of a pack besides its pack.mcmeta, as path/content pairs. */
    private static Map<String, String> files(String... pathContent) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pathContent.length; i += 2) {
            m.put(pathContent[i], pathContent[i + 1]);
        }
        return m;
    }

    /** A set with no mods and these datapacks, built as deploySet builds it. */
    private Path set(String name, Object... namesAndFiles) throws IOException {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        new Manifest(new TreeMap<>()).write(dir.resolve("manifest.json"));
        SortedMap<String, DatapackManifest.Pack> packs = new TreeMap<>();
        for (int i = 0; i < namesAndFiles.length; i += 2) {
            String pack = (String) namesAndFiles[i];
            @SuppressWarnings("unchecked")
            Map<String, String> files = (Map<String, String>) namesAndFiles[i + 1];
            List<String> flat = new ArrayList<>();
            files.forEach((k, v) -> {
                flat.add(k);
                flat.add(v);
            });
            Path zip = TestJars.datapackZip(tmp.resolve("downloads/" + name + "/" + pack + ".zip"), MCMETA, flat.toArray(String[]::new));
            packs.put(pack, Datapack.build(zip, pack, Sha256.of(zip), zip.toUri(), "test", true, dir));
        }
        if (!packs.isEmpty()) {
            new DatapackManifest(null, packs).write(dir.resolve(DatapackManifest.FILE_NAME));
        }
        return dir;
    }

    private Deployer.Report deploy(Path set) throws IOException {
        return Deployer.deploy("test", set, remote, false);
    }

    private DatapackManifest serverManifest() {
        return DatapackManifest.parse(remote.text(REMOTE_MANIFEST));
    }

    private SortedMap<String, String> folder(String name) {
        return remote.textsIn(DIR + "/" + name);
    }

    private void assertNoLeftovers() {
        for (String name : remote.entries(DIR).keySet()) {
            assertFalse(name.startsWith("."), "leftover " + name);
        }
    }

    @Test
    void installsAPackAsAFolderInTheWorld() throws IOException {
        Deployer.Report report = deploy(set("v1", "heads", files("data/heads/function/hi.mcfunction", "say hi")));

        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/function/hi.mcfunction", "say hi"), folder("heads"));
        assertNoLeftovers();
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().installed()));
        DatapackManifest onServer = serverManifest();
        assertEquals("world", onServer.world());
        assertNull(onServer.packs().get("heads").pending());
        assertEquals(DatapackManifest.readSet(tmp.resolve("v1")).withWorld("world"), onServer);
    }

    @Test
    void theFolderAppearsByRenameWithItsPackMcmetaLast() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a", "data/heads/b.mcfunction", "b")));

        String staging = DIR + "/.heads.deploying";
        List<String> ops = remote.ops.stream().filter(op -> op.contains(DIR)).toList();
        int mcmeta = ops.indexOf("put " + staging + "/pack.mcmeta");
        assertTrue(ops.indexOf("put " + staging + "/data/heads/a.mcfunction") < mcmeta, ops.toString());
        assertTrue(ops.indexOf("put " + staging + "/data/heads/b.mcfunction") < mcmeta, ops.toString());
        assertEquals("rename " + staging + " " + DIR + "/heads", ops.get(mcmeta + 1));
        assertTrue(remote.ops.indexOf("write " + REMOTE_MANIFEST) < remote.ops.indexOf("mkdirs " + staging),
                "the manifest names the pack before its folder is made");
    }

    @Test
    void theWorldIsLevelNameFromServerProperties() throws IOException {
        remote.put("server.properties", "#Minecraft server properties\nmotd=hi\nlevel-name=season5\n");
        deploy(set("v1", "heads", files()));
        assertEquals(Map.of("pack.mcmeta", MCMETA), remote.textsIn("season5/datapacks/heads"));
        assertEquals("season5", serverManifest().world());
    }

    @Test
    void aBlankLevelNameIsWorld() throws IOException {
        remote.put("server.properties", "level-name=\n");
        assertEquals("world", DatapackDeployer.levelName("test", remote));
        remote.put("server.properties", "level-name=worlds/s\\u00e4song\n");
        assertEquals("worlds/säsong", DatapackDeployer.levelName("test", remote));
    }

    @Test
    void anUnsafeLevelNameRefusesBeforeAnyWrite() throws IOException {
        remote.put("server.properties", "level-name=../elsewhere\n");
        DeployException e = assertThrows(DeployException.class, () -> deploy(set("v1", "heads", files())));
        assertTrue(e.getMessage().startsWith("level-name in test's server.properties"), e.getMessage());
        assertEquals(List.of(), remote.ops);
    }

    @Test
    void serverPropertiesIsNotReadWithoutDatapacks() throws IOException {
        remote.put("server.properties", "level-name=../elsewhere\n");
        deploy(set("none"));
        assertFalse(remote.files.containsKey(REMOTE_MANIFEST));
    }

    @Test
    void anUpdateReplacesTheWholeFolder() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a", "data/heads/gone.mcfunction", "x")));
        remote.ops.clear();
        Deployer.Report report = deploy(set("v2", "heads", files("data/heads/a.mcfunction", "a2", "data/heads/new.mcfunction", "n")));

        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/a.mcfunction", "a2", "data/heads/new.mcfunction", "n"), folder("heads"));
        assertNoLeftovers();
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().updated()));
        assertTrue(remote.ops.contains("rename " + DIR + "/heads " + DIR + "/.heads.replaced"));
        assertTrue(remote.ops.indexOf("delete " + DIR + "/.heads.replaced/pack.mcmeta")
                < remote.ops.indexOf("rename " + DIR + "/.heads.deploying " + DIR + "/heads"), "the old folder stops being a pack first");
    }

    @Test
    void anUnchangedPackIsNotTouched() throws IOException {
        Path v1 = set("v1", "heads", files("data/heads/a.mcfunction", "a"));
        deploy(v1);
        remote.ops.clear();
        Deployer.Report report = deploy(v1);
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().unchanged()));
        assertTrue(remote.ops.stream().noneMatch(op -> op.contains(DIR) || op.contains(REMOTE_MANIFEST)), remote.ops.toString());
        assertFalse(report.datapacks().datapacksChanged());
    }

    @Test
    void anInterruptedUploadLeavesTheOldPackAndTheNextDeployFinishes() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a")));
        Path v2 = set("v2", "heads", files("data/heads/a.mcfunction", "a2", "data/heads/b.mcfunction", "b"));
        remote.failOnOp = "put " + DIR + "/.heads.deploying/data/heads/b";
        assertThrows(IOException.class, () -> deploy(v2));

        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/a.mcfunction", "a"), folder("heads"), "the old pack stays whole");
        assertFalse(remote.files.containsKey(DIR + "/.heads.deploying/pack.mcmeta"), "the half upload is no pack");
        assertEquals(DatapackManifest.readSet(v2).packs().get("heads").files(), serverManifest().packs().get("heads").pending());

        Deployer.Report report = deploy(v2);
        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/a.mcfunction", "a2", "data/heads/b.mcfunction", "b"), folder("heads"));
        assertNoLeftovers();
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().updated()));
        assertNull(serverManifest().packs().get("heads").pending());
    }

    @Test
    void anInterruptionBetweenTheRenamesReinstalls() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a")));
        Path v2 = set("v2", "heads", files("data/heads/a.mcfunction", "a2"));
        remote.failOnOp = "put " + DIR + "/.heads.deploying/pack.mcmeta";
        assertThrows(IOException.class, () -> deploy(v2));
        assertFalse(remote.entries(DIR).containsKey("heads"));
        assertFalse(remote.files.containsKey(DIR + "/.heads.replaced/pack.mcmeta"), "the old folder is no pack");

        Deployer.Report report = deploy(v2);
        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/a.mcfunction", "a2"), folder("heads"));
        assertNoLeftovers();
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().installed()));
    }

    @Test
    void aPackSwappedInBeforeAnInterruptionIsNotAnEdit() throws IOException {
        Path v1 = set("v1", "heads", files("data/heads/a.mcfunction", "a"));
        deploy(v1);
        Path v2 = set("v2", "heads", files("data/heads/a.mcfunction", "a2"));
        remote.failOnOp = "rmdir " + DIR + "/.heads.replaced";
        assertThrows(IOException.class, () -> deploy(v2));
        assertEquals("a2", folder("heads").get("data/heads/a.mcfunction"));

        // Rolling back right away: the folder holds what the interrupted deploy was putting there.
        Deployer.Report report = deploy(v1);
        assertEquals(List.of(), report.datapacks().skipped());
        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/a.mcfunction", "a"), folder("heads"));
        assertNoLeftovers();
    }

    @Test
    void theSameSetAfterAnInterruptedSwapUploadsNothingMore() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a")));
        Path v2 = set("v2", "heads", files("data/heads/a.mcfunction", "a2"));
        remote.failOnOp = "rmdir " + DIR + "/.heads.replaced";
        assertThrows(IOException.class, () -> deploy(v2));
        remote.ops.clear();

        Deployer.Report report = deploy(v2);
        assertTrue(remote.ops.stream().noneMatch(op -> op.startsWith("put ")), remote.ops.toString());
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().updated()), "the server may not have reloaded it yet");
        assertNoLeftovers();
    }

    @Test
    void aPackTakenOffTheListIsDeleted() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a"), "hats", files()));
        Deployer.Report report = deploy(set("v2", "hats", files()));

        assertFalse(remote.entries(DIR).containsKey("heads"));
        assertNoLeftovers();
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().removed()));
        assertEquals(List.of("hats"), List.copyOf(serverManifest().packs().keySet()));
    }

    @Test
    void onlyTheDeploysOwnFoldersAreRemoved() throws IOException {
        remote.put(DIR + "/staff_pack/pack.mcmeta", MCMETA);
        remote.put(DIR + "/.staff.deploying/pack.mcmeta", MCMETA);
        remote.put(DIR + "/old.zip", "a zipped pack");
        deploy(set("v1", "heads", files()));
        deploy(set("none"));

        assertEquals(List.of(".staff.deploying", "old.zip", "staff_pack"), List.copyOf(remote.entries(DIR).keySet()));
        assertEquals(List.of(), List.copyOf(serverManifest().packs().keySet()));
    }

    @Test
    void aFolderOfTheSameNameNotFromTheDeployIsLeftAlone() throws IOException {
        remote.put(DIR + "/heads/pack.mcmeta", "staff's own");
        Path v1 = set("v1", "heads", files("data/heads/a.mcfunction", "a"));
        Deployer.Report report = deploy(v1);

        assertEquals(Map.of("pack.mcmeta", "staff's own"), folder("heads"));
        DatapackDeployer.Skipped skip = report.datapacks().skipped().get(0);
        assertEquals("heads", skip.name());
        assertEquals(List.of(DIR + "/heads"), skip.files());
        assertFalse(serverManifest().packs().containsKey("heads"), "the deploy doesn't claim it");
        assertTrue(Summary.markdown(report).contains("> [!WARNING]\n> Datapack `heads` not installed"), Summary.markdown(report));

        deploy(set("none"));
        assertEquals(Map.of("pack.mcmeta", "staff's own"), folder("heads"), "nor removes it");

        remote.files.remove(DIR + "/heads/pack.mcmeta");
        deploy(v1);
        assertEquals("a", folder("heads").get("data/heads/a.mcfunction"));
    }

    @Test
    void anEditedPackIsNotUpdatedAndTheReportNamesTheFiles() throws IOException {
        Path v1 = set("v1", "heads", files("data/heads/a.mcfunction", "a", "data/heads/b.mcfunction", "b"));
        deploy(v1);
        remote.put(DIR + "/heads/data/heads/a.mcfunction", "staff edit");
        remote.files.remove(DIR + "/heads/data/heads/b.mcfunction");
        remote.put(DIR + "/heads/data/heads/c.mcfunction", "new");
        SortedMap<String, String> edited = folder("heads");

        Deployer.Report report = deploy(set("v2", "heads", files("data/heads/a.mcfunction", "a2")));
        assertEquals(edited, folder("heads"));
        assertEquals(List.of(new DatapackDeployer.Skipped("heads", "not updated: edited on the server", List.of(
                "changed " + DIR + "/heads/data/heads/a.mcfunction",
                "deleted " + DIR + "/heads/data/heads/b.mcfunction",
                "added " + DIR + "/heads/data/heads/c.mcfunction"))), report.datapacks().skipped());
        assertEquals(DatapackManifest.readSet(v1).packs().get("heads"), serverManifest().packs().get("heads"),
                "the manifest still records what the deploy put there");
        assertFalse(report.datapacks().datapacksChanged());
        assertEquals(List.of("test: datapack heads not updated: edited on the server (changed " + DIR + "/heads/data/heads/a.mcfunction, deleted "
                + DIR + "/heads/data/heads/b.mcfunction, added " + DIR + "/heads/data/heads/c.mcfunction)"), Summary.warningLines(report));

        // Resolved by deleting the folder: the next deploy installs the pack again.
        remote.files.keySet().removeIf(p -> p.startsWith(DIR + "/heads/"));
        remote.dirs.removeIf(d -> d.startsWith(DIR + "/heads"));
        report = deploy(set("v2b", "heads", files("data/heads/a.mcfunction", "a2")));
        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/a.mcfunction", "a2"), folder("heads"));
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().installed()));
    }

    @Test
    void anEditedPackIsNotRemovedUntilTheEditIsUndone() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a")));
        remote.put(DIR + "/heads/data/heads/a.mcfunction", "staff edit");
        Path none = set("none");

        Deployer.Report report = deploy(none);
        assertEquals("staff edit", folder("heads").get("data/heads/a.mcfunction"));
        assertEquals("not removed: edited on the server", report.datapacks().skipped().get(0).reason());
        assertTrue(serverManifest().packs().containsKey("heads"));

        remote.put(DIR + "/heads/data/heads/a.mcfunction", "a");
        report = deploy(none);
        assertFalse(remote.entries(DIR).containsKey("heads"));
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().removed()));
    }

    @Test
    void anEditToAnUnchangedPackStays() throws IOException {
        Path v1 = set("v1", "heads", files("data/heads/a.mcfunction", "a"));
        deploy(v1);
        remote.put(DIR + "/heads/data/heads/a.mcfunction", "staff edit");
        Deployer.Report report = deploy(v1);
        assertEquals("staff edit", folder("heads").get("data/heads/a.mcfunction"));
        assertEquals(List.of(), report.datapacks().skipped());
    }

    @Test
    void aDryRunWritesNothingButReportsEdits() throws IOException {
        deploy(set("v1", "heads", files("data/heads/a.mcfunction", "a")));
        remote.put(DIR + "/heads/data/heads/a.mcfunction", "staff edit");
        remote.ops.clear();
        Deployer.Report report = Deployer.deploy("test", set("v2", "heads", files("data/heads/a.mcfunction", "a2"), "hats", files()), remote, true);
        assertEquals(List.of(), remote.ops);
        assertEquals("heads", report.datapacks().skipped().get(0).name());
        assertEquals(List.of("hats"), List.copyOf(report.datapacks().installed()));
    }

    @Test
    void refusesWhenAPacksZipDoesNotMatchDatapacksJson() throws IOException {
        Path v1 = set("v1", "heads", files("data/heads/a.mcfunction", "a"));
        DatapackManifest.Pack pack = DatapackManifest.readSet(v1).packs().get("heads");
        TestJars.datapackZip(v1.resolve(pack.file()), MCMETA, "data/heads/a.mcfunction", "tampered");
        DeployException e = assertThrows(DeployException.class, () -> deploy(v1));
        assertTrue(e.getMessage().endsWith("does not match datapacks.json; nothing was uploaded"), e.getMessage());
        assertEquals(List.of(), remote.ops);
    }

    @Test
    void assetsGoToMetacraftResourcePacksUpdateFolder() throws IOException {
        Path v1 = set("v1", "hats", files("data/hats/recipe/hat.json", "{}", "assets/hats/textures/item/hat.png", "png1"));
        Deployer.Report report = deploy(v1);

        DatapackManifest.Pack pack = DatapackManifest.readSet(v1).packs().get("hats");
        assertArrayEquals(Files.readAllBytes(v1.resolve(pack.resourcePack().file())), remote.files.get(RESOURCE_PACK_UPDATE_DIR + "/hats.zip"));
        assertFalse(folder("hats").keySet().stream().anyMatch(p -> p.startsWith("assets/")), "no assets in the datapack");
        assertEquals(List.of("hats"), List.copyOf(report.datapacks().newResourcePacks()));
        assertEquals(List.of("hats"), List.copyOf(report.datapacks().resourcePacksSent()));
        assertTrue(Summary.markdown(report).contains("set `\"global\": true` on `hats.zip`"), Summary.markdown(report));

        // The mod moves it at its next start; the same set again sends nothing.
        remote.files.put(RESOURCE_PACK_DIR + "/hats.zip", remote.files.remove(RESOURCE_PACK_UPDATE_DIR + "/hats.zip"));
        report = deploy(v1);
        assertEquals(List.of(), List.copyOf(report.datapacks().resourcePacksSent()));
        assertFalse(remote.files.containsKey(RESOURCE_PACK_UPDATE_DIR + "/hats.zip"));

        // New textures only: the folder stays, the zip goes to update/ again, under the same name.
        report = deploy(set("v2", "hats", files("data/hats/recipe/hat.json", "{}", "assets/hats/textures/item/hat.png", "png2")));
        assertTrue(remote.files.containsKey(RESOURCE_PACK_UPDATE_DIR + "/hats.zip"));
        assertEquals(List.of(), List.copyOf(report.datapacks().newResourcePacks()));
        assertEquals(List.of("hats"), List.copyOf(report.datapacks().updated()));
        assertTrue(Summary.applies(report).contains("Resource packs changed: apply with `/metacraft-resource-pack reload` or the next restart."));

        report = deploy(set("none"));
        assertFalse(remote.files.containsKey(RESOURCE_PACK_UPDATE_DIR + "/hats.zip"));
        assertFalse(remote.files.containsKey(RESOURCE_PACK_DIR + "/hats.zip"));
        assertEquals(List.of("hats"), List.copyOf(report.datapacks().resourcePacksRemoved()));
    }

    @Test
    void aResourcePackOfTheSameNameNotFromTheDeployIsLeftAlone() throws IOException {
        remote.put(RESOURCE_PACK_DIR + "/hats.zip", "staff's own");
        Deployer.Report report = deploy(set("v1", "hats", files("assets/hats/textures/item/hat.png", "png")));
        assertEquals("staff's own", remote.text(RESOURCE_PACK_DIR + "/hats.zip"));
        assertFalse(remote.files.containsKey(RESOURCE_PACK_UPDATE_DIR + "/hats.zip"));
        assertFalse(remote.entries(DIR).containsKey("hats"));
        assertEquals(List.of(RESOURCE_PACK_DIR + "/hats.zip"), report.datapacks().skipped().get(0).files());
    }

    @Test
    void aNewLevelNameLeavesTheOldWorldsPacksAlone() throws IOException {
        Path v1 = set("v1", "heads", files());
        deploy(v1);
        remote.put("server.properties", "level-name=season6\n");
        Deployer.Report report = deploy(v1);

        assertEquals(Map.of("pack.mcmeta", MCMETA), folder("heads"));
        assertEquals(Map.of("pack.mcmeta", MCMETA), remote.textsIn("season6/datapacks/heads"));
        assertEquals("season6", serverManifest().world());
        assertTrue(report.datapacks().note().startsWith("level-name is now `season6`"), report.datapacks().note());
    }

    @Test
    void rollingBackRestoresTheFoldersOfTheRelease() throws IOException {
        Path release1 = set("release1", "heads", files("data/heads/a.mcfunction", "a"), "hats", files());
        deploy(release1);
        deploy(set("release2", "heads", files("data/heads/a.mcfunction", "a2"), "boots", files()));

        // A rollback deploys the release's manifest.json, datapacks.json and zips, as downloaded.
        Path download = tmp.resolve("download");
        Files.createDirectories(download);
        try (var files = Files.list(release1)) {
            for (Path f : files.toList()) {
                Files.copy(f, download.resolve(f.getFileName()));
            }
        }
        Deployer.Report report = deploy(download);
        assertEquals(Map.of("pack.mcmeta", MCMETA, "data/heads/a.mcfunction", "a"), folder("heads"));
        assertEquals(List.of("hats", "heads"), List.copyOf(remote.entries(DIR).keySet()));
        assertEquals(List.of("hats"), List.copyOf(report.datapacks().installed()));
        assertEquals(List.of("heads"), List.copyOf(report.datapacks().updated()));
        assertEquals(List.of("boots"), List.copyOf(report.datapacks().removed()));
    }

    @Test
    void aRollbackLeavesAnEditedPackAlone() throws IOException {
        Path release1 = set("release1", "heads", files("data/heads/a.mcfunction", "a"));
        deploy(release1);
        deploy(set("release2", "heads", files("data/heads/a.mcfunction", "a2")));
        remote.put(DIR + "/heads/data/heads/a.mcfunction", "staff edit");

        Deployer.Report report = deploy(release1);
        assertEquals("staff edit", folder("heads").get("data/heads/a.mcfunction"));
        assertEquals("heads", report.datapacks().skipped().get(0).name());
    }

    @Test
    void worldgenChangesNeedARestart() throws IOException {
        deploy(set("v1", "heads", files()));
        Deployer.Report report = deploy(set("v2", "heads", files(), "biomes", files("data/b/worldgen/biome/x.json", "{}")));
        assertEquals(List.of("biomes"), List.copyOf(report.datapacks().worldgen()));
        assertEquals("**Needs a restart**: `biomes` changed worldgen or dimensions, which `/reload` doesn't apply.", Summary.applies(report));

        report = deploy(set("v3", "heads", files("data/h/function/x.mcfunction", "x"), "biomes", files("data/b/worldgen/biome/x.json", "{}")));
        assertEquals("Datapacks changed: apply with `/reload` or the next restart.", Summary.applies(report),
                "an unchanged worldgen pack doesn't count");

        report = deploy(set("v4", "heads", files("data/h/function/x.mcfunction", "x")));
        assertEquals(List.of("biomes"), List.copyOf(report.datapacks().worldgen()), "removing worldgen needs a restart too");
    }

    @Test
    void dimensionsAndOverlaysCountAsWorldgen() throws IOException {
        Deployer.Report report = deploy(set("v1", "a", files("data/a/dimension/x.json", "{}"),
                "b", files("data/b/dimension_type/x.json", "{}"), "c", files("overlay_1/data/c/worldgen/noise/x.json", "{}"),
                "d", files("data/d/function/worldgen/x.mcfunction", "x")));
        assertEquals(List.of("a", "b", "c"), List.copyOf(report.datapacks().worldgen()));
    }
}
