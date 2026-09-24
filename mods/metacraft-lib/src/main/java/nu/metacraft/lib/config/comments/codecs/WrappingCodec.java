package nu.metacraft.lib.config.comments.codecs;

import com.mojang.serialization.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface WrappingCodec<T> extends Codec<T> {

	<S> Codec<S> wrap(Codec<S> codec);

	@Override
	default Codec<T> withLifecycle(final Lifecycle lifecycle) {
		return wrap(Codec.super.withLifecycle(lifecycle));
	}

	@Override
	default Codec<T> withAlternative(final Codec<? extends T> alternative) {
		return wrap(Codec.super.withAlternative(alternative));
	}

	@Override
	default <U> Codec<T> withAlternative(final Codec<U> alternative, final Function<U, T> converter) {
		return wrap(Codec.super.withAlternative(alternative, converter));
	}

	@Override
	default Codec<List<T>> listOf() {
		return wrap(Codec.super.listOf());
	}

	@Override
	default Codec<List<T>> listOf(final int minSize, final int maxSize) {
		return wrap(Codec.super.listOf(minSize, maxSize));
	}

	@Override
	default Codec<List<T>> sizeLimitedListOf(final int maxSize) {
		return wrap(Codec.super.sizeLimitedListOf(maxSize));
	}

	@Override
	default <S> Codec<S> xmap(final Function<? super T, ? extends S> to, final Function<? super S, ? extends T> from) {
		return wrap(Codec.super.xmap(to, from));
	}

	@Override
	default <S> Codec<S> comapFlatMap(final Function<? super T, ? extends DataResult<? extends S>> to, final Function<? super S, ? extends T> from) {
		return wrap(Codec.super.comapFlatMap(to, from));
	}

	@Override
	default <S> Codec<S> flatComapMap(final Function<? super T, ? extends S> to, final Function<? super S, ? extends DataResult<? extends T>> from) {
		return wrap(Codec.super.flatComapMap(to, from));
	}

	@Override
	default <S> Codec<S> flatXmap(final Function<? super T, ? extends DataResult<? extends S>> to, final Function<? super S, ? extends DataResult<? extends T>> from) {
		return wrap(Codec.super.flatXmap(to, from));
	}

	@Override
	default Codec<T> mapResult(final ResultFunction<T> function) {
		return wrap(Codec.super.mapResult(function));
	}

	@Override
	default Codec<T> promotePartial(final Consumer<String> onError) {
		return wrap(Codec.super.promotePartial(onError));
	}

}
