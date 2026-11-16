package metacraft.ovvar.shaderpatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

		textColors(fixtures);

		if (failures > 0) {
			System.err.println(failures + " PatcherRunTest check(s) failed");
			System.exit(1);
		}
		System.out.println("PatcherRunTest: ok");
	}

	/**
	 * Chapter text colours: the colours the patcher injects are the chapter-text-colors resource pack's (its test copy
	 * in test-packs/), and they go into the program Iris draws world text with in each program folder, nowhere else.
	 */
	private static void textColors(Path fixtures) throws IOException {
		String glsl = new String(PatcherRunTest.class.getResourceAsStream("/chapter_text_colors.glsl").readAllBytes(), StandardCharsets.UTF_8);
		String pack = Files.readString(fixtures.resolveSibling("test-packs/chapter-text-colors/assets/minecraft/shaders/core/text.fsh"));
		check("text colours: same exact-match colours as the pack", triples(UVEC3, pack), triples(UVEC3, glsl));
		check("text colours: same replacement colours as the pack", triples(FLOAT_VEC, pack), triples(FLOAT_VEC, glsl));
		check("text colours: two of each", 4, triples(UVEC3, glsl).size() + triples(FLOAT_VEC, glsl).size());

		Map<String, byte[]> in = readPack(fixtures.resolve("text-colors/in"));
		PackPatch.Result result = PackPatch.patch(in);
		check("text colours: patched", PackPatch.Status.PATCHED, result.status());
		String translucent = new String(result.files().get("shaders/gbuffers_entities_translucent.vsh"), StandardCharsets.UTF_8);
		check("text colours: entities_translucent wraps vaColor", true, translucent.contains("#define vaColor metacraft_text_vaColor()"));
		check("text colours: ovvar reads the raw colour, before the wrap", true,
				translucent.indexOf("ovvar_color = vaColor;") >= 0 && translucent.indexOf("ovvar_color = vaColor;") < translucent.indexOf("#define vaColor"));
		String entities = new String(result.files().get("shaders/gbuffers_entities.vsh"), StandardCharsets.UTF_8);
		check("text colours: not in gbuffers_entities when entities_translucent exists", false, entities.contains("metacraft_text"));
		for (String fallback : List.of("shaders/world1/gbuffers_textured.vsh", "shaders/nether_custom/gbuffers_basic.vsh")) {
			String text = new String(result.files().get(fallback), StandardCharsets.UTF_8);
			check("text colours: fallback " + fallback, true, text.contains("#define gl_Color metacraft_text_gl_Color()"));
		}
		check("text colours: an include folder is no program folder", true,
				Arrays.equals(in.get("shaders/program/gbuffers_basic.vsh"), result.files().get("shaders/program/gbuffers_basic.vsh")));

		// MakeUp reads the colour in a file it includes inside main(), below the #define.
		PackPatch.Result included = PackPatch.patch(readPack(fixtures.resolve("text-colors-include/in")));
		check("text colours: a colour read in an include counts", true,
				new String(included.files().get("shaders/gbuffers_entities.vsh"), StandardCharsets.UTF_8).contains("#define gl_Color metacraft_text_gl_Color()"));

		// A compatibility pack that only mentions vaColor in a comment: gl_Color is its input, and nothing else is wrapped.
		String commented = new String(PackPatch.patch(readPack(fixtures.resolve("text-colors-comment/in"))).files()
				.get("shaders/gbuffers_entities.vsh"), StandardCharsets.UTF_8);
		check("text colours: gl_Color wrapped in a compatibility pack", true, commented.contains("#define gl_Color metacraft_text_gl_Color()"));
		check("text colours: a vaColor in a comment is no input", false, commented.contains("metacraft_text_vaColor"));

		// A text program with no .vsh: Iris gives it a vertex stage of its own, so the fallback is never used.
		PackPatch.Result unified = PackPatch.patch(readPack(fixtures.resolve("text-colors-unified/in")));
		check("text colours: a .fsh-only text program is skipped, not passed over", true,
				unified.log().stream().anyMatch(l -> l.contains("world-2/gbuffers_entities_translucent") && l.contains("no .vsh")));

		PackPatch.Result only = PackPatch.patch(readPack(fixtures.resolve("text-only/in")));
		check("text colours alone do not make a patched pack", PackPatch.Status.NOTHING_PATCHED, only.status());
	}

	private static final Pattern UVEC3 = Pattern.compile("uvec3\\(\\s*(\\d+)u?\\s*,\\s*(\\d+)u?\\s*,\\s*(\\d+)u?\\s*\\)");
	private static final Pattern FLOAT_VEC = Pattern.compile("(?<!u)vec[34]\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)");

	/** Every match's three numbers, as doubles, in order. */
	private static List<List<Double>> triples(Pattern p, String text) {
		List<List<Double>> out = new ArrayList<>();
		Matcher m = p.matcher(text);
		while (m.find()) out.add(List.of(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3))));
		return out;
	}

	private static Map<String, byte[]> readPack(Path dir) throws IOException {
		Map<String, byte[]> files = new TreeMap<>();
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
				files.put(dir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
			}
		}
		return files;
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
