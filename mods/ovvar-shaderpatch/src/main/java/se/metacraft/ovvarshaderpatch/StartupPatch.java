package se.metacraft.ovvarshaderpatch;

import metacraft.ovvar.shaderpatcher.PackPatch;
import metacraft.ovvar.shaderpatcher.Patcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Writes a {@code +ovvar} copy of every shaderpack in {@code <game dir>/shaderpacks} that lacks a current one,
 * and points Iris at the copy of the pack it has selected. Originals are never touched. Nothing here throws:
 * startup must never fail because of this mod.
 */
public final class StartupPatch {
	private static final String SUFFIX = "+ovvar";
	private static final String TMP_SUFFIX = ".ovvar-tmp";
	private static final String PREFIX = "[ovvar-shaderpatch] ";

	private StartupPatch() {
	}

	/** Runs the patch and returns the lines it also wrote to the game log. */
	public static List<String> run(Path gameDir) {
		List<String> log = new ArrayList<>();
		try {
			Path packs = gameDir.resolve("shaderpacks");
			if (Files.isDirectory(packs)) {
				List<Path> entries;
				try (Stream<Path> s = Files.list(packs)) {
					entries = s.sorted().toList();
				}
				for (Path pack : entries) {
					try {
						if (pack.getFileName().toString().endsWith(TMP_SUFFIX)) {
							Files.deleteIfExists(pack);
							continue;
						}
						patchPack(pack, log);
					} catch (Throwable t) {
						say(log, pack.getFileName() + ": failed, " + t);
					}
				}
			} else if (Files.exists(packs)) {
				say(log, "shaderpacks is not a folder, nothing to patch");
			}
			switchIris(gameDir.resolve("config").resolve("iris.properties"), packs, log);
		} catch (Throwable t) {
			say(log, "failed, " + t);
		}
		return log;
	}

	private static void say(List<String> log, String line) {
		log.add(line);
		System.out.println(PREFIX + line);
	}

	private static boolean isZip(Path p) {
		return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
	}

	/** The name of the patched copy of a pack. */
	private static String copyName(String name, boolean zip) {
		return zip ? name.substring(0, name.length() - 4) + SUFFIX + ".zip" : name + SUFFIX;
	}

	private static void patchPack(Path pack, List<String> log) throws IOException {
		String name = pack.getFileName().toString();
		boolean zip = isZip(pack);
		if (name.contains(SUFFIX) || name.startsWith(".") || (!zip && !Files.isDirectory(pack))) return;
		if (zip && !Files.isRegularFile(pack)) return;
		Path copy = pack.resolveSibling(copyName(name, zip));
		if (isCurrent(pack, copy)) return;
		if (!hasShadersEntry(pack, zip)) {
			say(log, name + ": not a shaderpack (no shaders/ entry), skipped");
			return;
		}
		PackPatch.Result result = PackPatch.patch(zip ? Patcher.readZip(pack) : Patcher.readFolder(pack));
		if (result.status() != PackPatch.Status.PATCHED) {
			switch (result.status()) {
				case NOT_A_SHADERPACK -> say(log, name + ": not a shaderpack, skipped");
				case NOTHING_PATCHED -> {
					say(log, name + ": a shaderpack, but nothing in it could be patched");
					for (String line : result.log()) say(log, "  " + line);
				}
				default -> say(log, name + ": already a patched copy, skipped");
			}
			return;
		}
		if (zip) {
			Path tmp = pack.resolveSibling(name + TMP_SUFFIX);
			try {
				Patcher.writeZip(tmp, result.files());
				moveOver(tmp, copy);
			} finally {
				Files.deleteIfExists(tmp);
			}
		} else {
			deleteTree(copy);
			Patcher.writeFolder(copy, result.files());
		}
		say(log, name + " -> " + copy.getFileName());
	}

	/** Cheap check before reading a pack: zips list entry names only (nothing is inflated). */
	private static boolean hasShadersEntry(Path pack, boolean zip) throws IOException {
		if (!zip) return Files.isDirectory(pack.resolve("shaders"));
		try (ZipFile z = new ZipFile(pack.toFile())) {
			Enumeration<? extends ZipEntry> entries = z.entries();
			while (entries.hasMoreElements()) {
				if (entries.nextElement().getName().startsWith("shaders/")) return true;
			}
		}
		return false;
	}

	/** A copy is current when it carries today's stamp and is not older than the original (which may have been replaced). */
	private static boolean isCurrent(Path original, Path copy) {
		if (!PackPatch.stamp().equals(stampOf(copy))) return false;
		try {
			return Files.getLastModifiedTime(copy).compareTo(Files.getLastModifiedTime(original)) >= 0;
		} catch (IOException e) {
			return false;
		}
	}

	/** Atomic where the file system can, a plain replace where it cannot. */
	private static void moveOver(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** The stamp of a patched copy (zip or folder), or null if there is none. */
	private static String stampOf(Path copy) {
		try {
			byte[] bytes = null;
			if (Files.isDirectory(copy)) {
				Path f = copy.resolve(PackPatch.STAMP_FILE);
				if (Files.isRegularFile(f)) bytes = Files.readAllBytes(f);
			} else if (Files.isRegularFile(copy)) {
				try (ZipFile z = new ZipFile(copy.toFile())) {
					ZipEntry e = z.getEntry(PackPatch.STAMP_FILE);
					if (e != null) {
						try (InputStream in = z.getInputStream(e)) {
							bytes = in.readAllBytes();
						}
					}
				}
			}
			if (bytes == null) return null;
			for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
				if (line.startsWith("stamp=")) return line.substring("stamp=".length()).trim();
			}
		} catch (IOException | RuntimeException e) {
			// An unreadable copy counts as stale and is rewritten.
		}
		return null;
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> s = Files.walk(root)) {
			for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
		}
	}

	/**
	 * Iris keeps its selection in {@code shaderPack=<name>} of a java.util.Properties file. When that names a pack with
	 * a current copy, that one logical line is rewritten to name the copy; every other byte stays as it was. The file
	 * is handled as ISO-8859-1 (what Properties.load(InputStream) does) so unrelated bytes survive untouched.
	 */
	private static void switchIris(Path props, Path packs, List<String> log) throws IOException {
		if (!Files.isRegularFile(props)) return;
		String text = new String(Files.readAllBytes(props), StandardCharsets.ISO_8859_1);
		StringBuilder out = new StringBuilder();
		boolean changed = false;
		for (String logical : logicalLines(text)) {
			String ending = lineEnding(logical);
			String content = logical.substring(0, logical.length() - ending.length());
			String current = shaderPackValue(content);
			if (current != null && !current.isEmpty() && !current.contains(SUFFIX)) {
				Path original = packs.resolve(current);
				String copy = copyName(current, isZip(original));
				if (Files.exists(original) && isCurrent(original, packs.resolve(copy))) {
					carryOptions(packs, current, copy, log);
					out.append(escapedEntry("shaderPack", copy)).append(ending);
					changed = true;
					say(log, "iris.properties: shaderPack " + current + " -> " + copy);
					continue;
				}
			}
			out.append(logical);
		}
		if (changed) {
			Path tmp = props.resolveSibling(props.getFileName() + TMP_SUFFIX);
			try {
				Files.write(tmp, out.toString().getBytes(StandardCharsets.ISO_8859_1));
				moveOver(tmp, props);
			} finally {
				Files.deleteIfExists(tmp);
			}
		}
	}

	/** Iris keeps a pack's settings in {@code shaderpacks/<pack>.txt}; the copy starts with the original's, unless it has its own. */
	private static void carryOptions(Path packs, String original, String copy, List<String> log) {
		try {
			Path from = packs.resolve(original + ".txt");
			Path to = packs.resolve(copy + ".txt");
			if (Files.isRegularFile(from) && !Files.exists(to)) {
				Files.copy(from, to);
				say(log, "copied " + from.getFileName() + " to " + to.getFileName());
			}
		} catch (IOException | RuntimeException e) {
			say(log, "could not carry over the pack's settings: " + e);
		}
	}

	/** Splits into Properties logical lines (a physical line ending in an odd number of backslashes continues), endings kept. */
	private static List<String> logicalLines(String text) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean continuing = false;
		int i = 0;
		while (i < text.length()) {
			int start = i;
			while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
			int end = i;
			if (i < text.length()) {
				if (text.charAt(i) == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i += 2;
				else i++;
			}
			String body = text.substring(start, end);
			current.append(text, start, i);
			boolean comment = !continuing && (body.stripLeading().startsWith("#") || body.stripLeading().startsWith("!"));
			int slashes = 0;
			for (int k = body.length() - 1; k >= 0 && body.charAt(k) == '\\'; k--) slashes++;
			continuing = !comment && slashes % 2 == 1 && i < text.length();
			if (!continuing) {
				result.add(current.toString());
				current.setLength(0);
			}
		}
		if (current.length() > 0) result.add(current.toString());
		return result;
	}

	private static String lineEnding(String line) {
		if (line.endsWith("\r\n")) return "\r\n";
		if (line.endsWith("\n")) return "\n";
		if (line.endsWith("\r")) return "\r";
		return "";
	}

	/** The value of shaderPack if this logical line is that entry, read with Properties' own rules. */
	private static String shaderPackValue(String logicalLine) {
		try {
			Properties p = new Properties();
			p.load(new StringReader(logicalLine));
			return p.getProperty("shaderPack");
		} catch (IOException | IllegalArgumentException e) {
			return null;
		}
	}

	/** One entry escaped the way Properties.store writes it (non-Latin-1 as backslash-u escapes). */
	private static String escapedEntry(String key, String value) throws IOException {
		Properties p = new Properties();
		p.setProperty(key, value);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		p.store(bytes, null);
		for (String line : new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1).split("\\R")) {
			if (!line.startsWith("#") && !line.isEmpty()) return line;
		}
		throw new IOException("no entry written");
	}
}
