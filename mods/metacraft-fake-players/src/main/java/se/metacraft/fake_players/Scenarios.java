package se.metacraft.fake_players;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Finds scenario CSV files. Two places are searched: the {@code config/metacraft-fake-players/scenarios} folder on
 * the server (add your own files there, no restart needed) and the ones shipped inside the mod jar. A file in the
 * folder shadows a bundled scenario of the same name.
 */
public final class Scenarios {
	private static final String BUNDLED = "data/metacraft-fake-players/scenarios";
	private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

	private Scenarios() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("metacraft-fake-players").resolve("scenarios");
	}

	private static Optional<Path> bundledFolder() {
		return FabricLoader.getInstance().getModContainer(METAcraftFakePlayers.MOD_ID)
				.flatMap(mod -> mod.findPath(BUNDLED));
	}

	/** Names of every available scenario, sorted. */
	public static TreeSet<String> names() {
		var names = new TreeSet<String>();
		collect(folder(), names);
		bundledFolder().ifPresent(dir -> collect(dir, names));
		return names;
	}

	private static void collect(Path dir, TreeSet<String> names) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var files = Files.list(dir)) {
			files.forEach(file -> {
				var name = file.getFileName().toString();
				if (name.endsWith(".csv") && NAME.matcher(name.substring(0, name.length() - 4)).matches()) {
					names.add(name.substring(0, name.length() - 4));
				}
			});
		} catch (IOException e) {
			METAcraftFakePlayers.LOGGER.warn("Could not list {}", dir, e);
		}
	}

	/** Whether the scenario comes from the server folder rather than the jar. */
	public static boolean isCustom(String name) {
		return Files.isRegularFile(folder().resolve(name + ".csv"));
	}

	public static Scenario load(String name) throws IOException {
		if (!NAME.matcher(name).matches()) {
			throw new IOException("Scenario names only contain a-z, 0-9, _ and -");
		}
		var file = folder().resolve(name + ".csv");
		if (!Files.isRegularFile(file)) {
			file = bundledFolder().map(dir -> dir.resolve(name + ".csv")).orElse(file);
		}
		if (!Files.isRegularFile(file)) {
			throw new IOException("No scenario called '" + name + "'. Available: " + String.join(", ", names()));
		}
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return Scenario.parse(name, reader);
		}
	}
}
