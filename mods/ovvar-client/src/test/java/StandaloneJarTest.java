import metacraft.ovvar.shaderpatcher.PackPatch;
import metacraft.ovvar.shaderpatcher.Patcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built mod jar is also the double-clickable shader patcher: {@code java -jar ovvar-client.jar pack.zip}
 * runs with nothing but the jar on the classpath (no Minecraft, no Fabric) and writes the {@code +ovvar}
 * copy, the way OvvarShaderPatcher.jar did. The build passes the jar's path in {@code ovvarClient.jar}.
 */
public class StandaloneJarTest {
	private static final Path FIXTURE = Paths.get("../ovvar/tools/shaderpatcher/fixtures/split-programs/in");

	@TempDir
	Path dir;

	private static Path jar() {
		String path = System.getProperty("ovvarClient.jar");
		assertTrue(path != null && Files.isRegularFile(Path.of(path)), "the build did not hand the test the mod jar (ovvarClient.jar=" + path + ")");
		return Path.of(path);
	}

	/** Runs {@code java -jar <the mod> args} headless in {@code dir}; returns exit code and output. */
	private Result run(String... args) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		command.add("-Djava.awt.headless=true");
		command.add("-jar");
		command.add(jar().toString());
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(process.waitFor(60, TimeUnit.SECONDS), "java -jar did not finish");
		return new Result(process.exitValue(), out);
	}

	private record Result(int exit, String out) {}

	@Test
	void javaDashJarPatchesAFixturePack() throws IOException, InterruptedException {
		Path pack = dir.resolve("fix.zip");
		Patcher.writeZip(pack, Patcher.readFolder(FIXTURE));
		Result result = run(pack.toString());
		assertEquals(0, result.exit(), result.out());
		Path copy = dir.resolve("fix+ovvar.zip");
		assertTrue(Files.isRegularFile(copy), "no +ovvar copy: " + result.out());
		Map<String, byte[]> files = Patcher.readZip(copy);
		assertEquals("stamp=" + PackPatch.stamp() + "\n", new String(files.get(PackPatch.STAMP_FILE), StandardCharsets.UTF_8));
		assertTrue(result.out().contains("1 pack(s) patched"), result.out());
	}

	@Test
	void javaDashJarWithoutPacksAndNoDisplayPrintsUsage() throws IOException, InterruptedException {
		Result result = run();
		assertEquals(0, result.exit(), result.out());
		assertTrue(result.out().contains("Usage: java -jar ovvar-client.jar"), result.out());
	}

	/** Main-Class is the patcher, and it and everything it loads run on Java 11 (class file 55), like the old jar. */
	@Test
	void theManifestStartsThePatcherAndThePatcherRunsOnJava11() throws IOException {
		try (JarFile jar = new JarFile(jar().toFile())) {
			assertEquals("metacraft.ovvar.shaderpatcher.Patcher", jar.getManifest().getMainAttributes().getValue("Main-Class"));
			assertTrue(jar.getEntry("fabric.mod.json") != null, "not a Fabric mod any more");
			assertTrue(jar.getEntry("README.txt") != null, "the patcher's README.txt is missing");
			int patcherClasses = 0;
			for (Enumeration<? extends ZipEntry> e = jar.entries(); e.hasMoreElements(); ) {
				ZipEntry entry = e.nextElement();
				if (!entry.getName().startsWith("metacraft/ovvar/shaderpatcher/") || !entry.getName().endsWith(".class")) continue;
				patcherClasses++;
				try (InputStream in = jar.getInputStream(entry); DataInputStream data = new DataInputStream(in)) {
					data.readInt();
					data.readUnsignedShort();
					int major = data.readUnsignedShort();
					assertTrue(major <= 55, entry.getName() + " is class file " + major + ", newer than Java 11");
				}
			}
			assertTrue(patcherClasses > 0, "the patcher is not in the jar");
		}
	}
}
