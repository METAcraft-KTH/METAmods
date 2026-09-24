package nu.metacraft.lib.config.comments.ops;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.RecordBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class WrappingRecordBuilderWithComments<T> implements RecordBuilderWithComments<T> {

	private final Map<T, String> comments = new HashMap<>();
	private RecordBuilder<T> builder;

	public WrappingRecordBuilderWithComments(RecordBuilder<T> builder) {
		this.builder = builder;
	}

	private RecordBuilder<T> update(RecordBuilder<T> builder) {
		if (builder != this.builder) {
			this.builder = builder;
		}
		return builder;
	}

	@Override
	public RecordBuilderWithComments<T> metacraft$addComments(Map<T, String> comments) {
		this.comments.putAll(comments);
		return this;
	}

	@Override
	public DynamicOps<T> ops() {
		return builder.ops();
	}

	@Override
	public RecordBuilder<T> add(T key, T value) {
		return update(builder.add(key, value));
	}

	@Override
	public RecordBuilder<T> add(T key, DataResult<T> value) {
		return update(builder.add(key, value));
	}

	@Override
	public RecordBuilder<T> add(DataResult<T> key, DataResult<T> value) {
		return update(builder.add(key, value));
	}

	@Override
	public RecordBuilder<T> withErrorsFrom(DataResult<?> result) {
		return update(builder.withErrorsFrom(result));
	}

	@Override
	public RecordBuilder<T> setLifecycle(Lifecycle lifecycle) {
		return update(builder.setLifecycle(lifecycle));
	}

	@Override
	public RecordBuilder<T> mapError(UnaryOperator<String> onError) {
		return update(builder.mapError(onError));
	}

	@Override
	public DataResult<T> build(T prefix) {
		var result = builder.build(prefix);
		if (ops() instanceof OpsWithComments<T> c) {
			return result.flatMap(v -> c.metacraft$withComments(v, this.comments));
		}
		return result;
	}
}
