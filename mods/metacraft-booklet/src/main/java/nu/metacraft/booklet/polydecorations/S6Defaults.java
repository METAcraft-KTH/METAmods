package nu.metacraft.booklet.polydecorations;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Season 6's PolyDecorations features on a server's first start. Our PolyDecorations fork reads
 * {@code config/polydecorations.json} from its mixin plugin, before any mod initialises (pre-launch
 * included), and writes every feature on when the file is missing; the booklet's own mixin plugin
 * ({@link nu.metacraft.booklet.mixin.BookletMixinPlugin}) loads first and writes Season 6's choices
 * there. An existing file is left alone: that is {@link PolyDecorationsConfig}'s. No Minecraft
 * classes here: it runs before the game is loaded.
 */
public final class S6Defaults {

	/** Season 6's features, the defaults of {@link PolyDecorationsConfig}. */
	public static final String RESOURCE = "/metacraft-booklet/polydecorations-s6.json";

	private static final Logger LOGGER = LoggerFactory.getLogger("metacraft-booklet");

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("polydecorations.json");
	}

	public static void writeIfMissing() {
		if (!FabricLoader.getInstance().isModLoaded("polydecorations") || Files.exists(path())) {
			return;
		}
		try (var in = S6Defaults.class.getResourceAsStream(RESOURCE)) {
			Files.createDirectories(path().getParent());
			Files.copy(in, path());
			LOGGER.info("[metacraft-booklet] wrote Season 6's PolyDecorations features to {}", path());
		} catch (IOException | NullPointerException e) {
			LOGGER.warn("[metacraft-booklet] couldn't write Season 6's PolyDecorations features", e);
		}
	}

}
