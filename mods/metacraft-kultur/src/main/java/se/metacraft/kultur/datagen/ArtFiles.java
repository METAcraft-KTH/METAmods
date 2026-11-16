package se.metacraft.kultur.datagen;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Which file under {@code art/kultur/<chapter>/<kind>/} is the art for a pattern id. A file named
 * {@code _<id>.png} is an AI-drawn draft and counts as the art for {@code <id>}; a plain
 * {@code <id>.png} (a hand-drawn replacement) wins over it. Ids never carry the underscore.
 */
public final class ArtFiles {
	private ArtFiles() {
	}

	/** The file name to load for {@code id}, or null when neither form exists. */
	public static String fileFor(String id, Predicate<String> exists, Consumer<String> log) {
		String plain = id + ".png", draft = "_" + id + ".png";
		if (exists.test(plain)) {
			if (exists.test(draft)) log.accept(draft + " is ignored: " + plain + " replaces it");
			return plain;
		}
		return exists.test(draft) ? draft : null;
	}
}
