package nu.metacraft.deploy;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SftpRemoteFilesTest {
    @TempDir
    Path tmp;
    private SshServer sshd;
    private Path root;

    @BeforeEach
    void startServer() throws IOException {
        root = Files.createDirectories(tmp.resolve("server"));
        Files.createDirectories(root.resolve("mods/update"));
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tmp.resolve("hostkey.ser")));
        sshd.setPasswordAuthenticator((user, password, session) -> "deploy".equals(user) && "secret".equals(password));
        sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(root));
        sshd.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        sshd.stop(true);
    }

    private SftpRemoteFiles connect() throws IOException {
        return SftpRemoteFiles.connect("127.0.0.1", sshd.getPort(), "deploy", "secret");
    }

    @Test
    void readsWritesOverwritesAndDeletes() throws IOException {
        String path = "mods/metacraft-deploy.json";
        try (SftpRemoteFiles remote = connect()) {
            assertEquals(Optional.empty(), remote.read(path));
            remote.write(path, "{}".getBytes(StandardCharsets.UTF_8));
            remote.write(path, "{\n}".getBytes(StandardCharsets.UTF_8));
            assertEquals("{\n}", new String(remote.read(path).orElseThrow(), StandardCharsets.UTF_8));
            assertFalse(Files.exists(root.resolve(path + ".part")));
            assertTrue(remote.delete(path));
            assertFalse(remote.delete(path));
        }
    }

    @Test
    void aWrongPasswordFailsClearly() {
        IOException e = assertThrows(IOException.class,
                () -> SftpRemoteFiles.connect("127.0.0.1", sshd.getPort(), "deploy", "wrong"));
        assertTrue(e.getMessage().startsWith("cannot open SFTP to deploy@127.0.0.1:"), e.getMessage());
    }

    @Test
    void firstDeployOverSftp() throws IOException {
        Files.writeString(root.resolve("mods/update/.autodeploy-version"), "1.1\n");
        Path jar = TestJars.modJar(tmp.resolve("set/a-1.jar"), "a", "1");
        new Manifest(new TreeMap<>(Map.of("a", new Manifest.Entry("a-1.jar", Sha256.of(jar), "1", "a"))))
                .write(tmp.resolve("set/manifest.json"));
        try (SftpRemoteFiles remote = connect()) {
            Deployer.deploy("test", tmp.resolve("set"), remote, false);
        }
        assertEquals(Sha256.of(jar), Sha256.of(root.resolve("mods/update/a-1.jar")));
        assertEquals("metacraft\n", Files.readString(root.resolve("mods/update/remove.txt")));
        assertEquals(Files.readString(tmp.resolve("set/manifest.json")), Files.readString(root.resolve("mods/metacraft-deploy.json")));
    }

    @Test
    void listsPlainFileNamesInAnExistingDirectory() throws IOException {
        Files.writeString(root.resolve("mods/update/a-1.jar"), "a");
        Files.writeString(root.resolve("mods/update/b-1.jar"), "b");
        Files.createDirectories(root.resolve("mods/update/nested"));
        try (SftpRemoteFiles remote = connect()) {
            List<String> names = remote.list("mods/update");
            names.sort(String::compareTo);
            assertEquals(List.of("a-1.jar", "b-1.jar", "nested"), names);
        }
    }

    @Test
    void listOfAMissingDirectoryIsEmpty() throws IOException {
        try (SftpRemoteFiles remote = connect()) {
            assertEquals(List.of(), remote.list("mods/does-not-exist"));
        }
    }

    @Test
    void makesRenamesListsAndDeletesDirectories() throws IOException {
        try (SftpRemoteFiles remote = connect()) {
            remote.mkdirs("world/datapacks/.p.deploying/data/p");
            remote.mkdirs("world/datapacks/.p.deploying/data/p");
            remote.put("world/datapacks/.p.deploying/data/p/a.json", "{}".getBytes(StandardCharsets.UTF_8));
            remote.put("world/datapacks/.p.deploying/pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8));
            assertEquals(Map.of("data", true, "pack.mcmeta", false), remote.entries("world/datapacks/.p.deploying"));
            remote.rename("world/datapacks/.p.deploying", "world/datapacks/p");
            assertEquals("{}", Files.readString(root.resolve("world/datapacks/p/data/p/a.json")));
            assertEquals(Map.of(), remote.entries("world/nothing"));

            remote.mkdirs("world/datapacks/q");
            IOException e = assertThrows(IOException.class, () -> remote.rename("world/datapacks/p", "world/datapacks/q"));
            assertTrue(e.getMessage().contains("exists"), e.getMessage());
            assertTrue(Files.exists(root.resolve("world/datapacks/p/pack.mcmeta")), "a refused rename leaves both");

            remote.deleteTree("world/datapacks/p");
            assertFalse(Files.exists(root.resolve("world/datapacks/p")));
        }
    }

    @Test
    void deploysAndUpdatesADatapackFolderOverSftp() throws IOException {
        Files.writeString(root.resolve("mods/update/.autodeploy-version"), "1.1\n");
        Files.writeString(root.resolve("server.properties"), "level-name=s5\n");
        Files.createDirectories(root.resolve("s5/datapacks/staff_pack"));
        Files.writeString(root.resolve("s5/datapacks/staff_pack/pack.mcmeta"), "{}");
        Path v1 = datapackSet("v1", "data/p/function/a.mcfunction", "say 1");
        Path v2 = datapackSet("v2", "data/p/function/b.mcfunction", "say 2");
        try (SftpRemoteFiles remote = connect()) {
            Deployer.deploy("test", v1, remote, false);
            assertEquals("say 1", Files.readString(root.resolve("s5/datapacks/p/data/p/function/a.mcfunction")));

            Deployer.Report report = Deployer.deploy("test", v2, remote, false);
            assertEquals(List.of("p"), List.copyOf(report.datapacks().updated()));
            assertFalse(Files.exists(root.resolve("s5/datapacks/p/data/p/function/a.mcfunction")));
            assertEquals("say 2", Files.readString(root.resolve("s5/datapacks/p/data/p/function/b.mcfunction")));

            Files.writeString(root.resolve("s5/datapacks/p/data/p/function/b.mcfunction"), "staff edit");
            report = Deployer.deploy("test", v1, remote, false);
            assertEquals("p", report.datapacks().skipped().get(0).name());
            assertEquals("staff edit", Files.readString(root.resolve("s5/datapacks/p/data/p/function/b.mcfunction")));

            Files.writeString(root.resolve("s5/datapacks/p/data/p/function/b.mcfunction"), "say 2");
            Path none = tmp.resolve("none");
            new Manifest(new TreeMap<>()).write(none.resolve("manifest.json"));
            report = Deployer.deploy("test", none, remote, false);
            assertEquals(List.of("p"), List.copyOf(report.datapacks().removed()));
        }
        try (var names = Files.list(root.resolve("s5/datapacks"))) {
            assertEquals(List.of("staff_pack"), names.map(p -> p.getFileName().toString()).toList());
        }
    }

    private Path datapackSet(String name, String path, String content) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve(name));
        new Manifest(new TreeMap<>()).write(dir.resolve("manifest.json"));
        Path zip = TestJars.datapackZip(tmp.resolve(name + ".zip"), "{\"pack\":{\"pack_format\":81}}", path, content);
        DatapackManifest.Pack pack = Datapack.build(zip, "p", Sha256.of(zip), zip.toUri(), "test", false, dir);
        new DatapackManifest(null, new TreeMap<>(Map.of("p", pack))).write(dir.resolve(DatapackManifest.FILE_NAME));
        return dir;
    }
}
