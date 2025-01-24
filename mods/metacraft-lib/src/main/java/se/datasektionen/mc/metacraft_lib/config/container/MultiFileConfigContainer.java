package se.datasektionen.mc.metacraft_lib.config.container;

import com.mojang.serialization.Codec;
import se.datasektionen.mc.metacraft_lib.config.container.impl.BasicMultiFileConfigContainer;
import se.datasektionen.mc.metacraft_lib.config.container.impl.MergableMultiFileConfigContainer;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface MultiFileConfigContainer<T> extends ConfigContainerBase<ConfigContainer<T>> {

	T get(String key);

	Stream<T> getAll();


	class Builder<T> {

		protected final Codec<T> codec;
		protected ReloadFunction<T> reloader = ReloadFunction.getDefault();
		protected final Map<String, Supplier<T>> defaultSettings = new HashMap<>();

		public static <T> Builder<T> create(
				Codec<T> codec
		) {
			return new Builder<>(codec);
		}

		protected Builder(
				Codec<T> codec
		) {
			this.codec = codec;
		}

		public Builder<T> setReloader(ReloadFunction<T> reloader) {
			this.reloader = reloader;
			return this;
		}

		public Builder<T> addDefaultSetting(String key, Supplier<T> value) {
			this.defaultSettings.put(key, value);
			return this;
		}

		public MultiFileConfigContainer<T> build(Path directory) {
			return new BasicMultiFileConfigContainer<>(
					codec, directory, reloader, defaultSettings
			);
		}

		public <M> Mergable<M, T> build(
				Path directory, Function<Stream<T>, M> merger
		) {
			return new MergableMultiFileConfigContainer<>(
					codec, directory, reloader, defaultSettings, merger
			);
		}
	}

	interface Mergable<M, I> extends MultiFileConfigContainer<I>, ConfigContainerWithSingleton<M> {}
}
