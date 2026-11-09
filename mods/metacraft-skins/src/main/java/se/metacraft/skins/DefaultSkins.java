package se.metacraft.skins;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What ships in the jar: the skins, in groups ({@code skins/default/}: the starter set, the MCC hats
 * and two test hats; {@code skins/winter/}: Winter Wonderland), and the Skin Boxes ({@code lootboxes/}:
 * the Classic Hats Box, of the MCC hats, and the Winter Skin Box), each copied into its folder the first time a server starts without that
 * folder. The groups only sort the jar: every skin lands right in the skins folder, so no two groups
 * may hold the same skin id. After that the folder is the admin's: a deleted shipped skin or box stays
 * deleted.
 */
public final class DefaultSkins {
	private DefaultSkins() {}

	/** Copies the shipped skins, from every group, into {@code skinsFolder} if it doesn't exist yet; returns whether it did. */
	public static boolean installIfMissing(Path skinsFolder) {
		return copyIfMissing("skins", skinsFolder, "skins", true);
	}

	/** Copies the shipped Skin Boxes into {@code boxesFolder} if it doesn't exist yet; returns whether it did. */
	public static boolean installBoxesIfMissing(Path boxesFolder) {
		return copyIfMissing("lootboxes", boxesFolder, "Skin Boxes", false);
	}

	/** Copies {@code bundledFolder}'s contents, or with {@code grouped} each of its folders' contents, into {@code target}. */
	private static boolean copyIfMissing(String bundledFolder, Path target, String what, boolean grouped) {
		if (Files.exists(target)) return false;
		Optional<Path> bundled = FabricLoader.getInstance().getModContainer(MetacraftSkins.MOD_ID).flatMap(mod -> mod.findPath(bundledFolder));
		if (bundled.isEmpty()) {
			MetacraftSkins.LOGGER.warn("[{}] the jar has no {} folder; starting with no shipped {}", MetacraftSkins.MOD_ID, bundledFolder, what);
			return false;
		}
		try {
			Files.createDirectories(target);
			List<Path> roots;
			if (grouped) {
				try (Stream<Path> groups = Files.list(bundled.get())) {
					roots = groups.filter(Files::isDirectory).sorted().toList();
				}
			} else {
				roots = List.of(bundled.get());
			}
			for (Path root : roots) {
				try (Stream<Path> files = Files.walk(root)) {
					for (Path file : files.toList()) {
						// The jar's paths belong to another file system: resolve them by name. A skin id in two
						// groups fails here (the file is already there), rather than one overwriting the other.
						Path copy = target.resolve(root.relativize(file).toString());
						if (Files.isDirectory(file)) Files.createDirectories(copy);
						else Files.copy(file, copy);
					}
				}
			}
			MetacraftSkins.LOGGER.info("[{}] put the shipped {} in {}; delete them to remove them", MetacraftSkins.MOD_ID, what, target);
			return true;
		} catch (IOException e) {
			MetacraftSkins.LOGGER.error("[{}] could not put the shipped {} in {}", MetacraftSkins.MOD_ID, what, target, e);
			return false;
		}
	}
}
