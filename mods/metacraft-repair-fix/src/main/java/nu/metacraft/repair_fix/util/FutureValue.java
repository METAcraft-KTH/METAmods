package nu.metacraft.repair_fix.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FutureValue<T> {

	private T value = null;

	private final List<Consumer<T>> toRun = new ArrayList<>();

	public FutureValue() {}

	public void apply(Consumer<T> run) {
		if (isReady()) {
			run.accept(value);
		} else {
			toRun.add(run);
		}
	}

	public boolean isReady() {
		return value != null;
	}

	public void complete(T value) {
		this.value = value;
		for (var run : toRun) {
			run.accept(value);
		}
		toRun.clear();
	}

	public void clear() {
		value = null;
	}

	public T get() throws IllegalAccessError {
		if (value == null) {
			throw new IllegalAccessError("Value is not yet ready!");
		}
		return value;
	}

}
