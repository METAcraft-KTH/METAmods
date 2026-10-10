package nu.metacraft.deploy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import static nu.metacraft.deploy.Deployer.MARKER;
import static nu.metacraft.deploy.Deployer.REMOTE_MANIFEST;
import static nu.metacraft.deploy.Deployer.REMOVE_FILE;
import static nu.metacraft.deploy.Deployer.UPDATE_DIR;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployerTest {
    @TempDir
    Path tmp;
    InMemoryRemoteFiles remote;

    @BeforeEach
    void server() {
        remote = new InMemoryRemoteFiles();
        remote.put(MARKER, "1.1\n");
        remote.put("mods/fabric-api.jar", "hand-managed");
    }

    /** A deploy dir with one jar per id, named {@code <id>-<version>.jar}; {@code versions} maps mod id to version. */
    private Path set(String name, Map<String, String> versions) throws IOException {
        return set(name, versions, true);
    }

    /** Like {@link #set(String, Map)}, but with jars named {@code <id>.jar} whatever the version, as our builds name them. */
    private Path sameNamesSet(String name, Map<String, String> versions) throws IOException {
        return set(name, versions, false);
    }

    private Path set(String name, Map<String, String> versions, boolean versionInName) throws IOException {
        Path dir = tmp.resolve(name);
        SortedMap<String, Manifest.Entry> entries = new TreeMap<>();
        for (Map.Entry<String, String> e : versions.entrySet()) {
            String file = e.getKey() + (versionInName ? "-" + e.getValue() : "") + ".jar";
            Path jar = TestJars.modJar(dir.resolve(file), e.getKey(), e.getValue());
            entries.put(e.getKey(), new Manifest.Entry(jar.getFileName().toString(), Sha256.of(jar), e.getValue(), e.getKey()));
        }
        new Manifest(entries).write(dir.resolve("manifest.json"));
        return dir;
    }

    private static Map<String, String> mods(String... idVersion) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < idVersion.length; i += 2) {
            m.put(idVersion[i], idVersion[i + 1]);
        }
        return m;
    }

    private void alreadyDeployed(Path dir) throws IOException {
        remote.files.put(REMOTE_MANIFEST, Manifest.read(dir.resolve("manifest.json")).toJson().getBytes());
    }

    /** {@link #alreadyDeployed} plus the set's jars installed in mods/. */
    private void installed(Path dir) throws IOException {
        alreadyDeployed(dir);
        for (Manifest.Entry entry : Manifest.read(dir.resolve("manifest.json")).entries().values()) {
            remote.files.put("mods/" + entry.file(), Files.readAllBytes(dir.resolve(entry.file())));
        }
    }

    /**
     * A server restart, as autodeploy does it: every jar in mods/update replaces the one of the same
     * name in mods/, then the jar of each mod id in remove.txt is deleted, then remove.txt.
     */
    private void restart() throws IOException {
        for (String jar : remote.jarsIn(UPDATE_DIR)) {
            remote.files.put("mods/" + jar, remote.files.remove(UPDATE_DIR + "/" + jar));
        }
        Set<String> ids = new TreeSet<>();
        remote.read(REMOVE_FILE).ifPresent(bytes -> ids.addAll(List.of(new String(bytes).strip().split("\n"))));
        for (String jar : remote.jarsIn("mods")) {
            Path copy = tmp.resolve("restart").resolve(jar);
            Files.createDirectories(copy.getParent());
            Files.write(copy, remote.files.get("mods/" + jar));
            String id;
            try {
                id = ModInfo.read(copy).id();
            } catch (DeployException notAMod) {
                continue;
            }
            if (ids.contains(id)) {
                remote.files.remove("mods/" + jar);
            }
        }
        remote.files.remove(REMOVE_FILE);
    }

    private byte[] bytes(Path set, String file) throws IOException {
        return Files.readAllBytes(set.resolve(file));
    }

    @Test
    void refusesWithoutMarkerAndWritesNothing() throws IOException {
        remote.files.remove(MARKER);
        Path set = set("s", mods("a", "1"));
        DeployException e = assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertTrue(e.getMessage().startsWith("update autodeploy.jar on test first (FabricModsUpdate v1.1); nothing was uploaded"), e.getMessage());
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void refusesOlderAutodeploy() throws IOException {
        remote.put(MARKER, "1.0\n");
        Path set = set("s", mods("a", "1"));
        assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        remote.put(MARKER, "garbage");
        assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void acceptsNewerAutodeploy() throws IOException {
        remote.put(MARKER, "1.10\n");
        Deployer.deploy("test", set("s", mods("a", "1")), remote, false);
        assertTrue(remote.files.containsKey(REMOTE_MANIFEST));
    }

    @Test
    void firstDeployUploadsEverythingAndRemovesTheBundle() throws IOException {
        Path set = set("s", mods("a", "1", "b", "1"));
        Deployer.Report report = Deployer.deploy("test", set, remote, false);
        assertTrue(report.firstDeploy());
        assertEquals(Set.of("a-1.jar", "b-1.jar"), remote.jarsIn(UPDATE_DIR));
        assertEquals("metacraft\n", remote.text(REMOVE_FILE));
        assertEquals(Manifest.read(set.resolve("manifest.json")).toJson(), remote.text(REMOTE_MANIFEST));
        assertEquals(REMOTE_MANIFEST, remote.writes.get(remote.writes.size() - 1), "manifest is written last");
        assertEquals("hand-managed", remote.text("mods/fabric-api.jar"));
        assertEquals(Set.of("a", "b"), report.uploaded());
        assertEquals(Set.of("metacraft"), report.removed());
    }

    @Test
    void anUnchangedSetUploadsNothing() throws IOException {
        Path set = set("s", mods("a", "1", "b", "1"));
        alreadyDeployed(set);
        Deployer.Report report = Deployer.deploy("test", set, remote, false);
        assertEquals(List.of(REMOVE_FILE, REMOTE_MANIFEST), remote.writes);
        assertEquals("metacraft\n", remote.text(REMOVE_FILE));
        assertEquals(Set.of("a", "b"), report.unchanged());
        assertEquals(Set.of(), report.removed());
        assertEquals(Set.of(), report.uploaded());
    }

    @Test
    void aChangedModUploadsOnlyThatJar() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "1")));
        Deployer.Report report = Deployer.deploy("test", set("new", mods("a", "1", "b", "2")), remote, false);
        assertEquals(Set.of("b-2.jar"), remote.jarsIn(UPDATE_DIR));
        assertEquals(Set.of("b"), report.uploaded());
    }

    @Test
    void aModTakenOffTheListGoesInRemoveTxt() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "1")));
        Deployer.Report report = Deployer.deploy("test", set("new", mods("a", "1")), remote, false);
        assertEquals("b\nmetacraft\n", remote.text(REMOVE_FILE));
        assertEquals(Set.of("b"), report.removed());
    }

    @Test
    void secondDeployBeforeRestartKeepsPendingRemovals() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "1")));
        remote.put(REMOVE_FILE, "metacraft\n");
        Deployer.deploy("test", set("new", mods("a", "1")), remote, false);
        assertEquals("b\nmetacraft\n", remote.text(REMOVE_FILE));
    }

    @Test
    void readdedModIsDroppedFromPendingRemovals() throws IOException {
        alreadyDeployed(set("old", mods("a", "1")));
        remote.put(REMOVE_FILE, "b\n");
        Deployer.deploy("test", set("new", mods("a", "1", "b", "1")), remote, false);
        assertEquals("metacraft\n", remote.text(REMOVE_FILE));
        assertEquals(Set.of("b-1.jar"), remote.jarsIn(UPDATE_DIR));
    }

    @Test
    void staleUploadFromAnUnrestartedDeployIsDeleted() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "2", "c", "1")));
        remote.put(UPDATE_DIR + "/b-2.jar", "waiting for a restart");
        remote.put(UPDATE_DIR + "/c-1.jar", "waiting for a restart");
        Deployer.deploy("test", set("new", mods("a", "1", "b", "3")), remote, false);
        assertEquals(Set.of("b-3.jar"), remote.jarsIn(UPDATE_DIR));
        assertEquals("c\nmetacraft\n", remote.text(REMOVE_FILE));
    }

    @Test
    void interruptedDeployThenDifferentSetLeavesNoOrphans() throws IOException {
        // Deploy 2 (b-2, plus a new c-1) uploaded its jars then died before writing the manifest,
        // so the server is still on M1 (b-1) with b-2.jar and c-1.jar sitting in mods/update.
        alreadyDeployed(set("m1", mods("b", "1")));
        remote.put(UPDATE_DIR + "/b-2.jar", "left by an interrupted deploy");
        remote.put(UPDATE_DIR + "/c-1.jar", "left by an interrupted deploy");
        Deployer.deploy("test", set("m3", mods("b", "3")), remote, false);
        assertEquals(Set.of("b-3.jar"), remote.jarsIn(UPDATE_DIR));
    }

    @Test
    void pendingJarOfTheSameSetIsReuploaded() throws IOException {
        Path set = set("s", mods("a", "1", "b", "1"));
        alreadyDeployed(set);
        remote.put(UPDATE_DIR + "/b-1.jar", "leftover from an earlier attempt, server manifest agrees");
        Deployer.Report report = Deployer.deploy("test", set, remote, false);
        assertEquals(Set.of("b"), report.uploaded());
        assertEquals(Set.of("a"), report.unchanged());
    }

    @Test
    void aJarNotFromTheManifestInUpdateIsDeleted() throws IOException {
        Path set = set("s", mods("a", "1"));
        remote.put(UPDATE_DIR + "/orphan-1.jar", "not in any manifest");
        Deployer.deploy("test", set, remote, false);
        assertEquals(Set.of("a-1.jar"), remote.jarsIn(UPDATE_DIR));
    }

    @Test
    void refusesWhenALocalJarDoesNotMatchTheManifest() throws IOException {
        Path set = set("s", mods("a", "1"));
        TestJars.modJar(set.resolve("a-1.jar"), "a", "tampered");
        DeployException e = assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertTrue(e.getMessage().contains("does not match manifest.json"), e.getMessage());
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void anUnreadableServerManifestSaysWhatToDo() throws IOException {
        remote.put(REMOTE_MANIFEST, "{not json");
        Path set = set("s", mods("a", "1"));
        DeployException e = assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertTrue(e.getMessage().contains("delete it to redeploy everything"), e.getMessage());
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void dryRunWritesNothing() throws IOException {
        Deployer.Report report = Deployer.deploy("test", set("s", mods("a", "1")), remote, true);
        assertEquals(List.of(), remote.writes);
        assertTrue(report.dryRun());
        assertEquals(Set.of("a"), report.uploaded());
        assertEquals(Set.of("metacraft"), report.removed());
    }

    @Test
    void interruptedDeployThenRestartThenRollbackRestores() throws IOException {
        Path m1 = sameNamesSet("m1", mods("a", "1", "b", "1"));
        installed(m1);
        // Deploy 2 changes b; b.jar keeps its name. The connection drops before the manifest is written.
        Path m2 = sameNamesSet("m2", mods("a", "1", "b", "2"));
        remote.failOnWrite = remote.writes.size() + 4; // interim manifest, b.jar, remove.txt, then the manifest
        assertThrows(IOException.class, () -> Deployer.deploy("test", m2, remote, false));
        assertEquals(List.of(REMOTE_MANIFEST, UPDATE_DIR + "/b.jar", REMOVE_FILE), remote.writes);
        restart();
        assertArrayEquals(bytes(m2, "b.jar"), remote.files.get("mods/b.jar"), "the restart installed b 2");

        Deployer.Report report = Deployer.deploy("test", m1, remote, false);
        assertEquals(Set.of("b"), report.uploaded());
        assertEquals(Set.of("a"), report.unchanged());
        restart();
        assertArrayEquals(bytes(m1, "b.jar"), remote.files.get("mods/b.jar"), "the rollback put b 1 back");
        assertArrayEquals(bytes(m1, "a.jar"), remote.files.get("mods/a.jar"));
    }

    @Test
    void interruptedRemovalThenRestartThenReaddUploads() throws IOException {
        Path m1 = sameNamesSet("m1", mods("a", "1", "b", "1"));
        installed(m1);
        // Deploy 2 takes b off the list and dies after writing remove.txt, before the manifest.
        Path m2 = sameNamesSet("m2", mods("a", "1"));
        remote.failOnWrite = remote.writes.size() + 3; // interim manifest, remove.txt, then the manifest
        assertThrows(IOException.class, () -> Deployer.deploy("test", m2, remote, false));
        assertEquals(List.of(REMOTE_MANIFEST, REMOVE_FILE), remote.writes);
        restart();
        assertFalse(remote.files.containsKey("mods/b.jar"), "the restart deleted b");

        Deployer.Report report = Deployer.deploy("test", m1, remote, false);
        assertEquals(Set.of("b"), report.uploaded());
        restart();
        assertArrayEquals(bytes(m1, "b.jar"), remote.files.get("mods/b.jar"), "b is back");
    }

    @Test
    void interimManifestNeverMatchesARealJar() throws IOException {
        Path old = set("old", mods("a", "1", "b", "1", "c", "1", "d", "1"));
        alreadyDeployed(old);
        remote.put(REMOVE_FILE, "d\n");
        remote.failOnWrite = 2; // only the interim manifest gets through
        assertThrows(IOException.class, () -> Deployer.deploy("test", set("new", mods("a", "1", "b", "2", "e", "1")), remote, false));
        Map<String, Manifest.Entry> before = Manifest.read(old.resolve("manifest.json")).entries();
        Map<String, Manifest.Entry> interim = Manifest.parse(remote.text(REMOTE_MANIFEST)).entries();
        assertEquals(Set.of("a", "b", "c", "d"), interim.keySet(), "touched mods stay named; the new e needs no entry");
        assertEquals(before.get("a"), interim.get("a"), "an untouched mod keeps its entry");
        for (String touched : List.of("b", "c", "d")) {
            assertEquals("0".repeat(64), interim.get(touched).sha256());
            assertEquals(before.get(touched).file(), interim.get(touched).file());
        }
    }

    @Test
    void interruptedUploadAndRemovalThenRerunStillRemoves() throws IOException {
        Path m1 = sameNamesSet("m1", mods("a", "1", "b", "1"));
        installed(m1);
        Path m2 = sameNamesSet("m2", mods("a", "1", "c", "1"));
        remote.failOnWrite = remote.writes.size() + 2; // the interim manifest, then c.jar
        assertThrows(IOException.class, () -> Deployer.deploy("test", m2, remote, false));
        assertFalse(remote.files.containsKey(REMOVE_FILE), "interrupted before remove.txt");

        Deployer.Report report = Deployer.deploy("test", m2, remote, false);
        assertEquals("b\nmetacraft\n", remote.text(REMOVE_FILE));
        assertEquals(Set.of("b"), report.removed());
        assertEquals(Set.of("c"), report.uploaded());
    }

    @Test
    void everyDeployRemovesTheBundle() throws IOException {
        Path set = set("s", mods("a", "1"));
        alreadyDeployed(set);
        Deployer.Report report = Deployer.deploy("test", set, remote, false);
        assertFalse(report.firstDeploy());
        assertEquals("metacraft\n", remote.text(REMOVE_FILE), "a bundle an old workflow uploaded is deleted too");
        assertEquals(Set.of(), report.removed(), "the summary mentions the bundle on the first deploy only");
    }

    @Test
    void comparesVersionsNumerically() {
        assertTrue(Deployer.compareVersions("1.10", "1.1") > 0);
        assertEquals(0, Deployer.compareVersions("1.1", "1.1.0"));
        assertTrue(Deployer.compareVersions("1.0", "1.1") < 0);
        assertTrue(Deployer.compareVersions("2", "1.9") > 0);
    }
}
