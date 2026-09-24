package nu.metacraft.lib.config.comments.codecs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;

import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class WrappingMapCodec<T> extends MapCodec<T> {

	public abstract <S> Codec<S> wrap(Codec<S> codec);

	public abstract <S> MapCodec<S> wrap(MapCodec<S> codec);

	@Override
	public Codec<T> codec() {
		return wrap(super.codec());
	}

	@Override
	public MapCodec<T> withLifecycle(final Lifecycle lifecycle) {
		return wrap(super.withLifecycle(lifecycle));
	}

	@Override
	public <S> MapCodec<S> xmap(final Function<? super T, ? extends S> to, final Function<? super S, ? extends T> from) {
		return wrap(super.xmap(to, from));
	}

	@Override
	public <S> MapCodec<S> flatXmap(final Function<? super T, ? extends DataResult<? extends S>> to, final Function<? super S, ? extends DataResult<? extends T>> from) {
		return wrap(super.flatXmap(to, from));
	}

	@Override
	public <E> MapCodec<T> dependent(final MapCodec<E> initialInstance, final Function<T, Pair<E, MapCodec<E>>> splitter, final BiFunction<T, E, T> combiner) {
		return wrap(super.dependent(initialInstance, splitter, combiner));
	}

	@Override
	public <E> Codec<E> partialDispatch(final Function<? super E, ? extends DataResult<? extends T>> type, final Function<? super T, ? extends DataResult<? extends MapCodec<? extends E>>> codec) {
		return wrap(super.partialDispatch(type, codec));
	}

	@Override
	public <E> MapCodec<E> dispatchMap(final Function<? super E, ? extends T> type, final Function<? super T, ? extends MapCodec<? extends E>> codec) {
		return wrap(super.dispatchMap(type, codec));
	}

	@Override
	public MapCodec<T> mapResult(final ResultFunction<T> function) {
		return wrap(super.mapResult(function));
	}

}
