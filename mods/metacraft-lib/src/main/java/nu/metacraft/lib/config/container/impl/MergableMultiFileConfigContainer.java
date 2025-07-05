package nu.metacraft.lib.config.container.impl;

import com.mojang.serialization.Codec;
import nu.metacraft.lib.config.container.MultiFileConfigContainer;
import nu.metacraft.lib.config.container.ReloadCause;
import nu.metacraft.lib.config.container.ReloadFunction;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class MergableMultiFileConfigContainer<T, C> extends BasicMultiFileConfigContainer<C> implements MultiFileConfigContainer.Mergable<T, C> {

	private T merged;
	private final Function<Stream<C>, T> merger;

	public MergableMultiFileConfigContainer(
			Codec<C> codec,
			Path directory,
			ReloadFunction<C> reloader,
			Map<String, Supplier<C>> defaultSettings,
			Function<Stream<C>, T> merger
	) {
		super(codec, directory, reloader, defaultSettings);
		this.merger = merger;
	}

	@Override
	public T get() {
		initialLoad();
		if (merged == null) {
			merged = merger.apply(getAll());
		}
		return merged;
	}

	@Override
	public void reload(ReloadCause reloadCause) {
		merged = null;
		super.reload(reloadCause);
	}

}
