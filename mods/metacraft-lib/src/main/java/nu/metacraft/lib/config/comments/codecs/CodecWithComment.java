package nu.metacraft.lib.config.comments.codecs;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

public record CodecWithComment<T>(Codec<T> codec, String comment) implements WrappingCodec<T> {
	@Override
	public <T1> DataResult<Pair<T, T1>> decode(DynamicOps<T1> ops, T1 input) {
		return codec.decode(ops, input);
	}

	@Override
	public <T1> DataResult<T1> encode(T input, DynamicOps<T1> ops, T1 prefix) {
		return codec.encode(input, ops, prefix);
	}

	@Override
	public @NonNull String toString() {
		return "WithComment[" + codec + ", comment=" + comment + "]";
	}

	public <F> MapCodec<F> wrapMap(MapCodec<F> wrapped, String name) {
		return new MapCodecWithComments<>(wrapped, Map.of(name, comment), Optional.empty());
	}

	@Override
	public <S> Codec<S> wrap(Codec<S> codec) {
		if (codec instanceof CodecWithComment<S> c) {
			return new CodecWithComment<>(c.codec, comment);
		}
		return new CodecWithComment<>(codec, comment);
	}

	@Override
	public MapCodec<T> fieldOf(final String name) {
		return wrapMap(WrappingCodec.super.fieldOf(name), name);
	}

	@Override
	public MapCodec<Optional<T>> optionalFieldOf(final String name) {
		return wrapMap(WrappingCodec.super.optionalFieldOf(name), name);
	}

	@Override
	public MapCodec<T> optionalFieldOf(final String name, final T defaultValue) {
		return wrapMap(WrappingCodec.super.optionalFieldOf(name, defaultValue), name);
	}

	@Override
	public MapCodec<Optional<T>> lenientOptionalFieldOf(final String name) {
		return wrapMap(WrappingCodec.super.optionalFieldOf(name), name);
	}

	@Override
	public MapCodec<T> lenientOptionalFieldOf(final String name, final T defaultValue) {
		return wrapMap(WrappingCodec.super.optionalFieldOf(name, defaultValue), name);
	}

	@Override
	public MapCodec<T> lenientOptionalFieldOf(final String name, final T defaultValue, final Lifecycle lifecycleOfDefault) {
		return wrapMap(WrappingCodec.super.lenientOptionalFieldOf(name, defaultValue, lifecycleOfDefault), name);
	}

	@Override
	public MapCodec<T> lenientOptionalFieldOf(final String name, final Lifecycle fieldLifecycle, final T defaultValue, final Lifecycle lifecycleOfDefault) {
		return wrapMap(WrappingCodec.super.lenientOptionalFieldOf(name, fieldLifecycle, defaultValue, lifecycleOfDefault), name);
	}

	public static Map<Object, String> mergeComments(Map<Object, String> lhs, Map<Object, String> rhs) {
		ImmutableMap.Builder<Object, String> newMap = ImmutableMap.builder();
		lhs.forEach(newMap::put);
		rhs.forEach(newMap::put);
		return newMap.build();
	}
}
