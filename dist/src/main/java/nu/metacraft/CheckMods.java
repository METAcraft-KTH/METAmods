package nu.metacraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.lang3.mutable.MutableBoolean;
import nu.metacraft.lib.util.helper.TextHelper;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CheckMods implements ModInitializer {

	/**
	 * If any mod-id does not match the project name, put a mapping in here.
	 */
	private static final Map<String, String> PROJECT_TO_MOD_ID = Map.of(
			"dist", "metacraft"
	);

	@Override
	public void onInitialize() {
		//Sanity check to make sure all mods are loaded before launching.
		//Sometimes when the game starts, the submodule mods are not loaded,
		//causing any entities, blocks, items, etc. in the world to disappear.
		//This crashes the game instead when that happens, so you don't have to redo your testing setup.
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			List<String> modsThatMustBePresent = new ArrayList<>();
			var settingsPath = Path.of("../settings.gradle");
			if (!settingsPath.toFile().exists()) {
				settingsPath = Path.of("../../settings.gradle");
			}
			try (var reader = new BufferedReader(new FileReader(settingsPath.toFile()))) {
				MutableBoolean inComment = new MutableBoolean(false);
				reader.lines().forEach(line -> {
					if (line.contains("/*")) {
						inComment.setTrue();
					}
					if (line.startsWith("include") && !inComment.booleanValue()) {
						int start = line.indexOf("\"");
						int end = line.lastIndexOf("\"");
						if (start == end) {
							throw new IllegalArgumentException("Unable to find substring!");
						}
						var path = line.substring(start+1, end);
						var name = path.substring(path.lastIndexOf(":")+1);
						modsThatMustBePresent.add(PROJECT_TO_MOD_ID.getOrDefault(name, name));
					}
					if (line.contains("*/")) {
						inComment.setFalse();
					}
				});
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			var unloadedMods = modsThatMustBePresent.stream().filter(
					mod -> !FabricLoader.getInstance().isModLoaded(mod)
			).toList();

			if (!unloadedMods.isEmpty()) {
				String suffix = "not loaded! This sometimes happens, just try to run the project again and it will probably fix itself.";
				if (unloadedMods.size() == 1) {
					throw new IllegalStateException(
							unloadedMods.getFirst() + " was " + suffix
					);
				} else {
					throw new IllegalStateException(
							TextHelper.combine(unloadedMods) + " were " + suffix
					);
				}
			}
		}
	}
}
