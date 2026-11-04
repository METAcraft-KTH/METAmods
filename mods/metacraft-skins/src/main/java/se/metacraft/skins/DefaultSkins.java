package se.metacraft.skins;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What ships in the jar: the skins ({@code default-skins/}: a starter set and two test hats) and the
 * Skin Boxes ({@code default-boxes/}: the Halloween Skin Box), each copied into its folder the first
 * time a server starts without that folder. After that the folder is the admin's: a deleted shipped
 * skin or box stays deleted.
 */
public final class DefaultSkins {
	private DefaultSkins() {}

	/** Copies the shipped skins into {@code skinsFolder} if it doesn't exist yet; returns whether it did. */
	public static boolean installIfMissing(Path skinsFolder) {
		return copyIfMissing("default-skins", skinsFolder, "skins");
	}

	/** Copies the shipped Skin Boxes into {@code boxesFolder} if it doesn't exist yet; returns whether it did. */
	public static boolean installBoxesIfMissing(Path boxesFolder) {
		return copyIfMissing("default-boxes", boxesFolder, "Skin Boxes");
	}

	private static boolean copyIfMissing(String bundledFolder, Path target, String what) {
		if (Files.exists(target)) return false;
		Optional<Path> bundled = FabricLoader.getInstance().getModContainer(MetacraftSkins.MOD_ID).flatMap(mod -> mod.findPath(bundledFolder));
		if (bundled.isEmpty()) {
			MetacraftSkins.LOGGER.warn("[{}] the jar has no {} folder; starting with no shipped {}", MetacraftSkins.MOD_ID, bundledFolder, what);
			return false;
		}
		try (Stream<Path> files = Files.walk(bundled.get())) {
			for (Path file : files.toList()) {
				// The jar's paths belong to another file system: resolve them by name.
				Path copy = target.resolve(bundled.get().relativize(file).toString());
				if (Files.isDirectory(file)) Files.createDirectories(copy);
				else Files.copy(file, copy);
			}
			MetacraftSkins.LOGGER.info("[{}] put the shipped {} in {}; delete them to remove them", MetacraftSkins.MOD_ID, what, target);
			return true;
		} catch (IOException e) {
			MetacraftSkins.LOGGER.error("[{}] could not put the shipped {} in {}", MetacraftSkins.MOD_ID, what, target, e);
			return false;
		}
	}
}
