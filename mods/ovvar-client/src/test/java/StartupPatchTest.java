import metacraft.ovvar.shaderpatcher.Patcher;
import metacraft.ovvar.shaderpatcher.PackPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.metacraft.ovvarclient.StartupPatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the launcher mod does to a game directory at startup. */
public class StartupPatchTest {
	private static final Path FIXTURE = Paths.get("../ovvar/tools/shaderpatcher/fixtures/split-programs/in");

	@TempDir
	Path game;
	Path packs;
	Path original;
	Path copy;

	@BeforeEach
	void setUp() throws IOException {
		packs = Files.createDirectories(game.resolve("shaderpacks"));
		original = packs.resolve("bsl.zip");
		copy = packs.resolve("bsl+ovvar.zip");
		Patcher.writeZip(original, Patcher.readFolder(FIXTURE));
	}

	private Map<String, byte[]> copyFiles() throws IOException {
		return Patcher.readZip(copy);
	}

	@Test
	void patchesNewPack() throws IOException {
		StartupPatch.run(game);
		assertTrue(Files.isRegularFile(copy));
		String stamp = new String(copyFiles().get(PackPatch.STAMP_FILE), StandardCharsets.UTF_8);
		assertEquals("stamp=" + PackPatch.stamp() + "\n", stamp);
	}

	@Test
	void skipsCurrentCopy() throws IOException {
		StartupPatch.run(game);
		FileTime old = FileTime.fromMillis(1_000_000_000_000L);
		Files.setLastModifiedTime(original, FileTime.fromMillis(old.toMillis() - 10_000));
		Files.setLastModifiedTime(copy, old);
		StartupPatch.run(game);
		assertEquals(old, Files.getLastModifiedTime(copy));
	}

	@Test
	void rewritesStaleCopy() throws IOException {
		StartupPatch.run(game);
		Map<String, byte[]> files = new LinkedHashMap<>(copyFiles());
		files.put(PackPatch.STAMP_FILE, "stamp=00000000\n".getBytes(StandardCharsets.UTF_8));
		Patcher.writeZip(copy, files);
		StartupPatch.run(game);
		String stamp = new String(copyFiles().get(PackPatch.STAMP_FILE), StandardCharsets.UTF_8);
		assertEquals("stamp=" + PackPatch.stamp() + "\n", stamp);
	}

	@Test
	void leavesOriginalAlone() throws IOException {
		byte[] before = Files.readAllBytes(original);
		StartupPatch.run(game);
		assertArrayEquals(before, Files.readAllBytes(original));
	}

	@Test
	void patchesFolderPack() throws IOException {
		Path folder = packs.resolve("folderpack");
		Patcher.writeFolder(folder, Patcher.readFolder(FIXTURE));
		StartupPatch.run(game);
		assertTrue(Files.isRegularFile(packs.resolve("folderpack+ovvar").resolve(PackPatch.STAMP_FILE)));
	}

	@Test
	void switchesIris() throws IOException {
		Path config = Files.createDirectories(game.resolve("config"));
		Path props = config.resolve("iris.properties");
		Files.writeString(props, "enableShaders=true\nshaderPack=bsl.zip\n");
		StartupPatch.run(game);
		assertEquals("enableShaders=true\nshaderPack=bsl+ovvar.zip\n", Files.readString(props));
	}

	@Test
	void carriesIrisPackOptionsToTheCopy() throws IOException {
		Files.writeString(packs.resolve("bsl.zip.txt"), "shadowMapResolution=4096\n");
		iris("shaderPack=bsl.zip\n");
		StartupPatch.run(game);
		assertEquals("shadowMapResolution=4096\n", Files.readString(packs.resolve("bsl+ovvar.zip.txt")));
	}

	@Test
	void keepsTheCopysOwnIrisPackOptions() throws IOException {
		Files.writeString(packs.resolve("bsl.zip.txt"), "a=1\n");
		Files.writeString(packs.resolve("bsl+ovvar.zip.txt"), "b=2\n");
		iris("shaderPack=bsl.zip\n");
		StartupPatch.run(game);
		assertEquals("b=2\n", Files.readString(packs.resolve("bsl+ovvar.zip.txt")));
	}

	@Test
	void nothingPatchedLogsTheCoresReason() throws IOException {
		Map<String, byte[]> pack = new LinkedHashMap<>();
		pack.put("shaders/final.fsh", "void main() {}\n".getBytes(StandardCharsets.UTF_8));
		Patcher.writeZip(packs.resolve("odd.zip"), pack);
		List<String> log = StartupPatch.run(game);
		assertTrue(log.stream().anyMatch(l -> l.startsWith("odd.zip: a shaderpack, but nothing")));
	}

	@Test
	void unmarkedSingleFileProgramIsNeitherWrittenNorSelected() throws IOException {
		Path pack = packs.resolve("nomarkers.zip");
		Patcher.writeZip(pack, Patcher.readFolder(Paths.get("../ovvar/tools/shaderpatcher/fixtures/single-file-no-markers/in")));
		Path props = iris("shaderPack=nomarkers.zip\n");
		List<String> log = StartupPatch.run(game);
		assertFalse(Files.exists(packs.resolve("nomarkers+ovvar.zip")));
		assertEquals("shaderPack=nomarkers.zip\n", read(props));
		assertTrue(log.stream().anyMatch(l -> l.startsWith("nomarkers.zip: a shaderpack, but nothing")));
	}

	@Test
	void leavesIrisAloneWithoutCopy() throws IOException {
		Path config = Files.createDirectories(game.resolve("config"));
		Path props = config.resolve("iris.properties");
		Files.writeString(props, "shaderPack=other.zip\n");
		StartupPatch.run(game);
		assertEquals("shaderPack=other.zip\n", Files.readString(props));
	}

	@Test
	void ignoresNonShaderpacks() throws IOException {
		Map<String, byte[]> resourcePack = new LinkedHashMap<>();
		resourcePack.put("pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8));
		Patcher.writeZip(packs.resolve("faithful.zip"), resourcePack);
		StartupPatch.run(game);
		assertFalse(Files.exists(packs.resolve("faithful+ovvar.zip")));
	}

	@Test
	void neverThrows() throws IOException {
		Path odd = Files.createTempDirectory(game, "odd");
		Files.writeString(odd.resolve("shaderpacks"), "not a folder");
		List<String> log = StartupPatch.run(odd);
		assertFalse(log.isEmpty());
		// and a game dir that does not exist
		assertNotEquals(null, StartupPatch.run(game.resolve("missing")));
	}

	@Test
	void rewritesCopyWhenOriginalIsReplaced() throws IOException {
		StartupPatch.run(game);
		FileTime t1 = FileTime.fromMillis(1_000_000_000_000L);
		Files.setLastModifiedTime(copy, t1);
		Files.setLastModifiedTime(original, FileTime.fromMillis(t1.toMillis() + 5_000));
		StartupPatch.run(game);
		assertTrue(Files.getLastModifiedTime(copy).toMillis() > t1.toMillis() + 5_000);
	}

	@Test
	void rewritesCorruptCopy() throws IOException {
		Files.writeString(copy, "garbage");
		StartupPatch.run(game);
		assertEquals("stamp=" + PackPatch.stamp() + "\n", new String(copyFiles().get(PackPatch.STAMP_FILE), StandardCharsets.UTF_8));
	}

	@Test
	void cleansLeftoverTemp() throws IOException {
		Path tmp = packs.resolve("bsl.zip.ovvar-tmp");
		Files.writeString(tmp, "half");
		StartupPatch.run(game);
		assertFalse(Files.exists(tmp));
		assertTrue(Files.isRegularFile(copy));
	}

	@Test
	void logsOneLinePerSkippedPack() throws IOException {
		Map<String, byte[]> resourcePack = new LinkedHashMap<>();
		resourcePack.put("pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8));
		Patcher.writeZip(packs.resolve("faithful.zip"), resourcePack);
		List<String> log = StartupPatch.run(game);
		assertEquals(1, log.stream().filter(l -> l.startsWith("faithful.zip")).count());
	}

	private Path iris(String content) throws IOException {
		Path props = Files.createDirectories(game.resolve("config")).resolve("iris.properties");
		Files.write(props, content.getBytes(StandardCharsets.ISO_8859_1));
		return props;
	}

	private String read(Path p) throws IOException {
		return new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
	}

	@Test
	void irisKeepsCrlf() throws IOException {
		Path props = iris("# c\r\nenableShaders=true\r\nshaderPack=bsl.zip\r\nx=1\r\n");
		StartupPatch.run(game);
		assertEquals("# c\r\nenableShaders=true\r\nshaderPack=bsl+ovvar.zip\r\nx=1\r\n", read(props));
	}

	@Test
	void irisSpacedAndColonForms() throws IOException {
		Path props = iris("shaderPack = bsl.zip\n");
		StartupPatch.run(game);
		assertEquals("shaderPack=bsl+ovvar.zip\n", read(props));
		props = iris("shaderPack:bsl.zip\n");
		StartupPatch.run(game);
		assertEquals("shaderPack=bsl+ovvar.zip\n", read(props));
	}

	@Test
	void irisNonLatin1AndSpace() throws IOException {
		Path odd = packs.resolve("bsl \u30c4.zip");
		Patcher.writeZip(odd, Patcher.readFolder(FIXTURE));
		Path props = iris("shaderPack=bsl \\u30C4.zip\n");
		StartupPatch.run(game);
		assertTrue(Files.isRegularFile(packs.resolve("bsl \u30c4+ovvar.zip")));
		assertEquals("shaderPack=bsl \\u30C4+ovvar.zip\n", read(props));
	}

	@Test
	void irisAlreadyOnCopyIsUnchanged() throws IOException {
		Path props = iris("shaderPack=bsl+ovvar.zip\n");
		StartupPatch.run(game);
		assertEquals("shaderPack=bsl+ovvar.zip\n", read(props));
	}
}
