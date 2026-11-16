package metacraft.ovvar.shaderpatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs {@link PackPatch} over the shared fixtures: {@code <dir>/<case>/in/**} is a pack, and
 * {@code expected/} holds the output pack (only for PATCHED), {@code status.txt} (the status
 * name) and {@code log.txt} (the log lines). Inputs are read in sorted path order (a TreeMap), and
 * the log order follows that. The TypeScript port reads the very same files.
 * {@code --update} writes {@code expected/} instead of comparing. Exit 1 on any difference.
 */
public final class Fixtures {
	private static final String STATUS = "status.txt", LOG = "log.txt";

	public static void main(String[] args) throws IOException {
		boolean update = Arrays.asList(args).contains("--update");
		Path root = Paths.get(args[0]);
		List<String> problems = new ArrayList<>();
		List<Path> cases;
		try (Stream<Path> s = Files.list(root)) {
			cases = s.filter(Files::isDirectory).sorted().collect(Collectors.toList());
		}
		for (Path dir : cases) {
			String name = dir.getFileName().toString();
			Map<String, byte[]> in = read(dir.resolve("in"));
			PackPatch.Result result = PackPatch.patch(in);
			Path expected = dir.resolve("expected");
			Map<String, byte[]> out = result.status() == PackPatch.Status.PATCHED ? result.files() : new TreeMap<String, byte[]>();
			String status = result.status().name() + "\n";
			String log = result.log().stream().map(l -> l + "\n").collect(Collectors.joining());
			if (update) {
				if (Files.exists(expected)) delete(expected);
				write(expected, out);
				Files.write(expected.resolve(STATUS), status.getBytes(StandardCharsets.UTF_8));
				Files.write(expected.resolve(LOG), log.getBytes(StandardCharsets.UTF_8));
				System.out.println(name + ": " + result.status());
				continue;
			}
			Map<String, byte[]> want = read(expected);
			String wantStatus = text(want.remove(STATUS));
			String wantLog = text(want.remove(LOG));
			if (!status.equals(wantStatus)) problems.add(name + ": status " + status.trim() + ", expected " + (wantStatus == null ? null : wantStatus.trim()));
			if (!log.equals(wantLog)) problems.add(name + ": log differs\n--- got\n" + log + "--- expected\n" + wantLog);
			if (result.status() != PackPatch.Status.PATCHED && !sameFiles(result.files(), in)) problems.add(name + ": files changed on " + result.status());
			for (String path : out.keySet()) {
				if (!want.containsKey(path)) problems.add(name + ": unexpected file " + path);
				else if (!Arrays.equals(out.get(path), want.get(path))) problems.add(name + ": " + path + " differs");
			}
			for (String path : want.keySet()) {
				if (!out.containsKey(path)) problems.add(name + ": missing file " + path);
			}
		}
		if (!problems.isEmpty()) {
			System.out.println("FAILED:");
			problems.forEach(p -> System.out.println("  " + p));
			System.exit(1);
		}
		if (!update) System.out.println(cases.size() + " fixture case(s) ok");
	}

	private static boolean sameFiles(Map<String, byte[]> a, Map<String, byte[]> b) {
		if (!a.keySet().equals(b.keySet())) return false;
		for (String k : a.keySet()) if (!Arrays.equals(a.get(k), b.get(k))) return false;
		return true;
	}

	private static String text(byte[] bytes) {
		return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
	}

	private static Map<String, byte[]> read(Path dir) throws IOException {
		Map<String, byte[]> files = new TreeMap<>();
		if (!Files.isDirectory(dir)) return files;
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
				files.put(dir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
			}
		}
		return files;
	}

	private static void write(Path dir, Map<String, byte[]> files) throws IOException {
		Files.createDirectories(dir);
		for (Map.Entry<String, byte[]> e : files.entrySet()) {
			Path p = dir.resolve(e.getKey());
			Files.createDirectories(p.getParent());
			Files.write(p, e.getValue());
		}
	}

	private static void delete(Path dir) throws IOException {
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) s.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(p);
		}
	}
}
