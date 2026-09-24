package nu.metacraft.lib.config.comments.codecs;

import com.mojang.serialization.*;
import nu.metacraft.lib.config.comments.ops.RecordBuilderWithComments;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapCodecWithComments<T> extends WrappingMapCodec<T> {

	private final MapCodec<T> codec;
	private final Map<Object, String> comments;
	private final Optional<String> selfComment;

	public MapCodecWithComments(MapCodec<T> codec, Map<Object, String> comments, Optional<String> selfComment) {
		this.codec = codec;
		this.comments = comments;
		this.selfComment = selfComment;
	}

	public MapCodec<T> getCodec() {
		return codec;
	}

	public Map<Object, String> comments() {
		return comments;
	}

	public Optional<String> getSelfComment() {
		return selfComment;
	}

	@Override
	public <T1> Stream<T1> keys(DynamicOps<T1> ops) {
		return codec.keys(ops);
	}

	@Override
	public <T1> DataResult<T> decode(DynamicOps<T1> ops, MapLike<T1> input) {
		return codec.decode(ops, input);
	}

	@Override
	public <T1> RecordBuilder<T1> encode(T input, DynamicOps<T1> ops, RecordBuilder<T1> prefix) {
		if (prefix instanceof RecordBuilderWithComments<T1> builder) {
			prefix = builder.metacraft$addComments(
				comments.entrySet().stream().collect(
					Collectors.toMap(
						e -> JavaOps.INSTANCE.convertTo(ops, e.getKey()),
						Map.Entry::getValue
					)
				)
			);
		}
		return codec.encode(input, ops, prefix);
	}

	@Override
	public <S> Codec<S> wrap(Codec<S> codec) {
		return selfComment.map(comment -> (Codec<S>) new CodecWithComment<>(codec, comment)).orElse(codec);
	}

	@Override
	public <S> MapCodec<S> wrap(MapCodec<S> codec) {
		if (codec instanceof MapCodecWithComments<S> c) {
			return new MapCodecWithComments<>(
				c.codec, CodecWithComment.mergeComments(comments, c.comments), selfComment.or(() -> c.selfComment)
			);
		}
		return new MapCodecWithComments<>(codec, comments, selfComment);
	}

	@Override
	public String toString() {
		return "WithComments[" + codec + ", comments=[" + comments + "]]";
	}
}
