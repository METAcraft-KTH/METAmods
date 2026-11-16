package metacraft.ovvar.shaderpatcher;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Patches Iris/OptiFine shaderpacks so Ovvar's overalls show their patches, and different ones
 * on the left and right limbs.
 *
 * Ovvar (metacraft.se) ships a vanilla core-shader override that a shaderpack replaces wholesale,
 * so shaderpack users see plain overalls with mirrored limbs. This tool puts the same code
 * (ovvar.glsl, bundled in this jar) into the pack's own entity program:
 * <ul>
 *   <li>fragment stage: right after the texture-coordinate varying's declaration, a global of
 *	   the same type is declared and the varying's name is {@code #define}d to it, so every use
 *	   — in the file, in its includes, in macros — reads the global; the same for the vertex
 *	   colour varying. At the top of main() the globals are set to the remapped coordinate and
 *	   the un-dyed colour;</li>
 *   <li>vertex stage: varyings carry the raw vertex colour (the dye colour, which holds the
 *	   patch bits), position and normal to the fragment stage.</li>
 * </ul>
 * It does the same for metacraft-shaders' rifts (rift_common/void_rift/nether_rift.glsl, bundled
 * too): see {@link PackPatch}. Rifts also drop out of the pack's shadow pass, and
 * shaders.properties gains four custom uniforms. The program Iris draws world text with gets
 * the chapter text colours (chapter_text_colors.glsl): dark purple and dark blue nametags draw as the
 * chapter-text-colors resource pack draws them without a shaderpack. Nothing else in the pack is
 * touched; originals are kept. Textures that are not Ovvar's or a rift's are sampled exactly as before.
 *
 * Double-click: a file chooser (opening in the Minecraft shaderpacks folder) asks which packs, then a summary shows.
 * This is the fallback for the web patcher at https://metacraft.se/ovvar/shaders.
 * Command line: {@code java -jar ovvar-client.jar [pack.zip|folder ...] [--dry-run]} (the same jar is the
 * optional ovvar client mod, mods/ovvar-client; this class is its Main-Class).
 */
public final class Patcher {
	private static final String SUFFIX = "+ovvar";
	private final StringBuilder log = new StringBuilder();
	private final boolean dryRun;
	private int patchedPacks, skippedPacks, failedPacks;

	private Patcher(boolean dryRun) {
		this.dryRun = dryRun;
	}

	/** Which packs to patch when the jar is double-clicked: the user's pick, empty when they cancel. */
	interface Chooser {
		List<Path> choose(Path start);
	}

	public static void main(String[] args) throws IOException {
		// Not System.console() == null: on JDK 22+ console() is non-null even with no terminal.
		boolean interactive = args.length == 0 && !GraphicsEnvironment.isHeadless();
		System.exit(run(args, interactive ? Patcher::chooseWithDialog : null, interactive));
	}

	/**
	 * Patches the packs named in {@code args}; with none, asks {@code chooser} (null: nobody to ask, so
	 * say how to name packs). An empty pick means the user cancelled: exit 0, no dialog. Returns the exit code.
	 */
	static int run(String[] args, Chooser chooser, boolean showSummary) {
		boolean dryRun = false;
		List<Path> packs = new ArrayList<>();
		for (String a : args) {
			if (a.equals("--dry-run")) dryRun = true;
			else packs.add(Paths.get(a));
		}
		Patcher patcher = new Patcher(dryRun);
		if (packs.isEmpty()) {
			if (chooser == null) {
				System.out.println("Usage: java -jar ovvar-client.jar [pack.zip|folder ...] [--dry-run]");
				System.out.println("Or patch your pack on the web: https://metacraft.se/ovvar/shaders");
				return 0;
			}
			Path folder = shaderpacksFolder();
			packs.addAll(chooser.choose(folder != null && Files.isDirectory(folder) ? folder : null));
			if (packs.isEmpty()) return 0;
		}
		for (Path pack : packs) {
			try {
				patcher.patchPack(pack);
			} catch (IOException | RuntimeException e) {
				patcher.say(pack.getFileName() + ": failed — " + e);
				patcher.failedPacks++;
			}
		}
		patcher.say("");
		patcher.say(patcher.patchedPacks + " pack(s) patched, " + patcher.skippedPacks + " skipped, " + patcher.failedPacks + " failed."
				+ (patcher.patchedPacks > 0 ? " Select the \"" + SUFFIX + "\" copy in Iris." : ""));
		if (showSummary) {
			JOptionPane.showMessageDialog(null, patcher.log.toString(), "Ovvar shader patcher",
					patcher.failedPacks > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
		}
		return patcher.failedPacks > 0 ? 1 : 0;
	}

	/** A multi-select file chooser for zips and folders, opening in the shaderpacks folder when there is one. */
	private static List<Path> chooseWithDialog(Path start) {
		JFileChooser chooser = new JFileChooser(start == null ? null : start.toFile());
		chooser.setDialogTitle("Pick the shaderpack(s) to patch for ovvar");
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setFileFilter(new FileFilter() {
			@Override public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
			}
			@Override public String getDescription() {
				return "Shaderpacks (.zip or folder)";
			}
		});
		List<Path> picked = new ArrayList<>();
		if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			for (File f : chooser.getSelectedFiles()) picked.add(f.toPath());
		}
		return picked;
	}

	private void say(String line) {
		System.out.println(line);
		log.append(line).append('\n');
	}

	static Path shaderpacksFolder() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String home = System.getProperty("user.home");
		Path minecraft;
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			minecraft = Paths.get(appData != null ? appData : home + "\\AppData\\Roaming", ".minecraft");
		} else if (os.contains("mac")) {
			minecraft = Paths.get(home, "Library", "Application Support", "minecraft");
		} else {
			minecraft = Paths.get(home, ".minecraft");
		}
		return minecraft.resolve("shaderpacks");
	}

	// ---------------------------------------------------------------- packs

	private void patchPack(Path src) throws IOException {
		String name = src.getFileName().toString();
		boolean zip = name.toLowerCase(Locale.ROOT).endsWith(".zip");
		if (name.contains(SUFFIX)) {
			say(name + ": a +ovvar copy; pick the original pack");
			skippedPacks++;
			return;
		}
		if (!zip && !Files.isDirectory(src)) {
			skippedPacks++;
			return;
		}
		PackPatch.Result result = PackPatch.patch(zip ? readZip(src) : readFolder(src));
		if (result.status() == PackPatch.Status.NOT_A_SHADERPACK || result.status() == PackPatch.Status.PATCHED_COPY) {
			for (String line : result.log()) say(name + ": " + line);
			skippedPacks++;
			return;
		}
		say(name + ":");
		for (String line : result.log()) say(line);
		if (result.status() == PackPatch.Status.NOTHING_PATCHED) {
			failedPacks++;
			return;
		}
		if (dryRun) {
			patchedPacks++;
			return;
		}
		String outName = zip ? name.substring(0, name.length() - 4) + SUFFIX + ".zip" : name + SUFFIX;
		Path out = src.resolveSibling(outName);
		if (zip) writeZip(out, result.files());
		else writeFolder(out, result.files());
		say("  -> " + outName);
		patchedPacks++;
	}

	// ---------------------------------------------------------------- files

	public static Map<String, byte[]> readZip(Path zip) throws IOException {
		Map<String, byte[]> files = new LinkedHashMap<>();
		try (ZipFile z = new ZipFile(zip.toFile())) {
			Enumeration<? extends ZipEntry> entries = z.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = entries.nextElement();
				if (e.isDirectory()) continue;
				try (InputStream in = z.getInputStream(e)) {
					files.put(e.getName(), in.readAllBytes());
				}
			}
		}
		return files;
	}

	public static Map<String, byte[]> readFolder(Path root) throws IOException {
		Map<String, byte[]> files = new LinkedHashMap<>();
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				files.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
				return FileVisitResult.CONTINUE;
			}
		});
		return files;
	}

	public static void writeZip(Path out, Map<String, byte[]> files) throws IOException {
		try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(out))) {
			for (Map.Entry<String, byte[]> e : files.entrySet()) {
				z.putNextEntry(new ZipEntry(e.getKey()));
				z.write(e.getValue());
				z.closeEntry();
			}
		}
	}

	public static void writeFolder(Path out, Map<String, byte[]> files) throws IOException {
		if (Files.exists(out)) {
			try (java.util.stream.Stream<Path> s = Files.walk(out)) {
				for (Path p : (Iterable<Path>) s.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(p);
			}
		}
		for (Map.Entry<String, byte[]> e : files.entrySet()) {
			Path p = out.resolve(e.getKey());
			Files.createDirectories(p.getParent());
			try (OutputStream o = Files.newOutputStream(p)) {
				o.write(e.getValue());
			}
		}
	}
}
