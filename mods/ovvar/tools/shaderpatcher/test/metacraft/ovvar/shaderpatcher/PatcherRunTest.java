package metacraft.ovvar.shaderpatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

/** Patcher.run through its Chooser seam (no GUI): cancel, a pick, and the no-GUI usage text. Plain main. */
public final class PatcherRunTest {
	private static int failures;

	public static void main(String[] args) throws IOException {
		Path fixtures = Path.of(args[0]);
		Path dir = Files.createTempDirectory("patcher-run");
		Path zip = dir.resolve("fix.zip");
		zipUp(fixtures.resolve("split-programs/in"), zip);

		// Cancel: an empty pick exits 0 and writes nothing (summary flag on: it must not even try a dialog).
		String out = capture(() -> check("cancel exit", 0, Patcher.run(new String[0], start -> Collections.emptyList(), true)));
		check("cancel prints nothing", "", out);
		check("cancel writes nothing", 1L, count(dir));

		// A pick of a zip writes its +ovvar copy.
		capture(() -> check("pick exit", 0, Patcher.run(new String[0], start -> List.of(zip), false)));
		check("pick writes the +ovvar copy", true, Files.exists(dir.resolve("fix+ovvar.zip")));

		// Explicit paths bypass the chooser.
		Files.delete(dir.resolve("fix+ovvar.zip"));
		capture(() -> check("cli exit", 0, Patcher.run(new String[] {zip.toString()}, start -> { throw new AssertionError("asked"); }, false)));
		check("cli writes the copy", true, Files.exists(dir.resolve("fix+ovvar.zip")));

		// No chooser (no display): usage and the web URL, exit 0.
		out = capture(() -> check("usage exit", 0, Patcher.run(new String[0], null, false)));
		check("usage names the page", true, out.contains("https://metacraft.se/ovvar/shaders"));
		check("usage shows the command line", true, out.contains("Usage"));

		// A +ovvar name is said out loud, not skipped silently.
		out = capture(() -> check("copy exit", 0, Patcher.run(new String[] {dir.resolve("fix+ovvar.zip").toString()}, null, false)));
		check("copy is explained", true, out.contains("fix+ovvar.zip: a +ovvar copy; pick the original pack"));

		// writeFolder clears an existing destination first, so files of an older copy don't linger.
		Path folder = dir.resolve("folder-out");
		Files.createDirectories(folder.resolve("old"));
		Files.writeString(folder.resolve("old/stale.txt"), "x");
		Patcher.writeFolder(folder, java.util.Map.of("a/b.txt", new byte[] {1}));
		check("writeFolder drops stale files", false, Files.exists(folder.resolve("old/stale.txt")));
		check("writeFolder writes the files", true, Files.exists(folder.resolve("a/b.txt")));

		if (failures > 0) {
			System.err.println(failures + " PatcherRunTest check(s) failed");
			System.exit(1);
		}
		System.out.println("PatcherRunTest: ok");
	}

	private static long count(Path dir) throws IOException {
		try (Stream<Path> s = Files.list(dir)) {
			return s.count();
		}
	}

	private static void check(String what, Object expected, Object actual) {
		if (!expected.equals(actual)) {
			failures++;
			System.err.println("FAIL " + what + ": expected " + expected + ", got " + actual);
		}
	}

	private static String capture(Runnable r) {
		PrintStream real = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		System.setOut(new PrintStream(bytes));
		try {
			r.run();
		} finally {
			System.setOut(real);
		}
		return bytes.toString();
	}

	private static void zipUp(Path root, Path zip) throws IOException {
		try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip)); Stream<Path> walk = Files.walk(root)) {
			for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				z.putNextEntry(new ZipEntry(root.relativize(p).toString().replace('\\', '/')));
				z.write(Files.readAllBytes(p));
				z.closeEntry();
			}
		}
	}
}
