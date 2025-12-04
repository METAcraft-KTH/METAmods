package nu.metacraft.lib.config.container.impl;

import com.mojang.serialization.Codec;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import nu.metacraft.lib.config.JsonHelper;
import nu.metacraft.lib.config.container.MultiFileConfigContainer;
import nu.metacraft.lib.config.container.ReloadCause;
import nu.metacraft.lib.config.container.ReloadFunction;
import nu.metacraft.lib.config.extensions.LoadAware;
import nu.metacraft.lib.config.extensions.ReloadAware;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class BasicMultiFileConfigContainer<T> implements MultiFileConfigContainer<T> {

	protected PMap<String, T> files = HashTreePMap.empty();
	protected final Codec<T> codec;
	protected final Path directory;
	protected final Map<String, Supplier<T>> defaultSettings;

	protected boolean loadedFirstTime = false;

	protected final ReloadFunction<T> reloader;
	protected Consumer<ReloadCause> onReload = cause -> {};

	public BasicMultiFileConfigContainer(
			Codec<T> codec,
			Path directory,
			ReloadFunction<T> reloader,
			Map<String, Supplier<T>> defaultSettings
	) {
		this.codec = codec;
		this.directory = directory;
		this.reloader = reloader;
		this.defaultSettings = defaultSettings;
	}

	protected Optional<T> loadFromFile(Path path) {
		return JsonHelper.load(path, codec);
	}

	protected String getFileExtension() {
		return "json";
	}

	@Override
	@Nullable
	public T get(String key) {
		initialLoad();
		return files.get(key);
	}

	private Path getPath(String key) {
		return directory.resolve(key + "." + getFileExtension());
	}

	private void checkFiles() {
		var f = directory.toFile();
		if (!f.exists()) {
			f.mkdirs();
		}
	}

	@Override
	public Stream<T> getAll() {
		return files.values().stream();
	}

	private void load(BiConsumer<String, Path> loader) {
		var configs = FileUtils.listFiles(directory.toFile(), new String[]{getFileExtension()}, true);
		if (configs != null) {
			for (var config : configs) {
				var key = config.getName().substring(0, config.getName().length() - getFileExtension().length() - 1);
				loader.accept(key, config.toPath());
			}
		}
	}

	protected void initialLoad() {
		if (!loadedFirstTime) {
			loadedFirstTime = true;
			checkFiles();
			load((key, path) -> {
				loadFromFile(path).ifPresent(result -> {
					files = files.plus(key, result);
					if (result instanceof LoadAware aware) {
						aware.afterLoad(Optional.empty());
					}
				});
			});
			if (files.isEmpty()) {
				for (var e : defaultSettings.entrySet()) {
					files = files.plus(e.getKey(), e.getValue().get());
				}
				save();
			}
		}
	}

	@Override
	public void reload(ReloadCause reloadCause) {
		checkFiles();
		files = HashTreePMap.empty();
		load((key, path) -> {
			var prev = get(key);
			if (prev instanceof ReloadAware aware) {
				aware.beforeReload(reloadCause);
			}
			var result = reloader.reload(prev, () -> loadFromFile(path), reloadCause);
			files = files.plus(key, result);
			if (result instanceof LoadAware aware) {
				aware.afterLoad(Optional.of(reloadCause));
			}
		});
		onReload.accept(reloadCause);
	}

	@Override
	public void save() {
		checkFiles();
		files.forEach((key, config) -> {
			JsonHelper.save(getPath(key), codec, config);
		});
	}

	@Override
	public void addReloadHandler(Consumer<ReloadCause> handler) {
		onReload = onReload.andThen(handler);
	}
}
