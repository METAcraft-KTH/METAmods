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
}
