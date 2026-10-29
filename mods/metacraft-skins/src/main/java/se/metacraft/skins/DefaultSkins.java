package se.metacraft.skins;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The skins that ship in the jar ({@code default-skins/}: a starter set and two test hats),
 * copied into the skins folder the first time a server starts without one. After that the folder
 * is the admin's: a deleted shipped skin stays deleted.
 */
public final class DefaultSkins {
	private DefaultSkins() {}

	/** Copies the shipped skins into {@code skinsFolder} if it doesn't exist yet; returns whether it did. */
	public static boolean installIfMissing(Path skinsFolder) {
		if (Files.exists(skinsFolder)) return false;
		Optional<Path> bundled = FabricLoader.getInstance().getModContainer(MetacraftSkins.MOD_ID).flatMap(mod -> mod.findPath("default-skins"));
		if (bundled.isEmpty()) {
			MetacraftSkins.LOGGER.warn("[{}] the jar has no default-skins folder; starting with no skins", MetacraftSkins.MOD_ID);
			return false;
		}
		try (Stream<Path> files = Files.walk(bundled.get())) {
			for (Path file : files.toList()) {
				// The jar's paths belong to another file system: resolve them by name.
				Path target = skinsFolder.resolve(bundled.get().relativize(file).toString());
				if (Files.isDirectory(file)) Files.createDirectories(target);
				else Files.copy(file, target);
			}
			MetacraftSkins.LOGGER.info("[{}] put the shipped skins in {}; delete their folders to remove them", MetacraftSkins.MOD_ID, skinsFolder);
			return true;
		} catch (IOException e) {
			MetacraftSkins.LOGGER.error("[{}] could not put the shipped skins in {}", MetacraftSkins.MOD_ID, skinsFolder, e);
			return false;
		}
	}
}
