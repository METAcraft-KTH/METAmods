package metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import se.metacraft.config.util.CommentCodec;

import java.util.Arrays;
import java.util.Optional;

/** How ovvar's config files are written: through metacraft-config, every key with its comment. */
public final class ConfigFields {
	private ConfigFields() {}

	/**
	 * A key that reads as {@code defaultValue} when missing but is always written, so a server's file
	 * shows every setting with its comment. (optionalFieldOf(key, default) would leave out every key
	 * still at its default.)
	 */
	public static <T> MapCodec<T> field(Codec<T> codec, String name, T defaultValue, String comment, String... more) {
		MapCodec<T> field = codec.optionalFieldOf(name).xmap(present -> present.orElse(defaultValue), Optional::of);
		return CommentCodec.comment(field, comment, more);
	}

	/** A required key with its comment. */
	public static <T> MapCodec<T> required(MapCodec<T> field, String comment, String... more) {
		return CommentCodec.comment(field, comment, more);
	}

	static String[] lines(String... parts) {
		return Arrays.copyOf(parts, parts.length);
	}
}
