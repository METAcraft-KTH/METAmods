package nu.metacraft.lib.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import nu.metacraft.lib.config.comments.CodecExtension;
import nu.metacraft.lib.config.comments.MapCodecExtension;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommentCodec {

	public static <T> Codec<T> comment(Codec<T> codec, String comment) {
		//noinspection unchecked
		return ((CodecExtension<T>) codec).metacraft$comment(comment);
	}

	public static <T> MapCodec<T> comment(MapCodec<T> codec, String comment) {
		//noinspection unchecked
		return ((MapCodecExtension<T>) codec).metacraft$comment(comment);
	}

	private static String combine(String first, String... rest) {
		return Stream.concat(Stream.of(first), Stream.of(rest)).collect(Collectors.joining("\n"));
	}

	public static <T> Codec<T> comment(Codec<T> codec, String comment, String... extra) {
		return comment(codec, combine(comment, extra));
	}

	public static <T> MapCodec<T> comment(MapCodec<T> codec, String comment, String... extra) {
		return comment(codec, combine(comment, extra));
	}

}
