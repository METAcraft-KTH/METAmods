package se.metacraft.config.container.impl;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.Unit;
import nu.metacraft.lib.METAcraftLib;
import se.metacraft.config.extensions.ModificationAware;
import se.metacraft.config.util.helper.JanksonHelper;
import se.metacraft.config.container.ConfigContainer;
import nu.metacraft.lib.util.helper.JsonHelper;
import se.metacraft.config.container.ReloadCause;
import se.metacraft.config.container.ReloadFunction;
import se.metacraft.config.extensions.LoadAware;
import se.metacraft.config.extensions.ReloadAware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.*;

public class BasicConfigContainer<T> implements ConfigContainer<T> {

	private static final WeakHashMap<BasicConfigContainer<?>, Unit> containers = new WeakHashMap<>();

	static {
		ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, manager) -> {
			containers.keySet().forEach(container -> {
				if (container.reloadsBeforeServer) {
					container.reload(ReloadCause.BEFORE_SERVER_RELOAD);
				}
			});
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			if (success) {
				containers.keySet().forEach(container -> {
					if (container.reloadsAfterServer) {
						container.reload(ReloadCause.AFTER_SERVER_RELOAD);
					}
				});
			}
		});
		ConfigContainer.CONFIG_FINDER.register(() -> containers.keySet().stream());
	}

	protected final Codec<T> codec;
	protected final Path configPath;
	protected final Supplier<T> defaultConfigInitializer;
	protected final boolean reloadsBeforeServer;
	protected final boolean reloadsAfterServer;
	protected final Optional<String> configScreenName;
	protected final ReloadFunction<T> reloader;
	protected Consumer<ReloadCause> onReload = cause -> {};

	protected T config;

	protected final List<UnaryOperator<T>> modifiers = new ArrayList<>();

	public BasicConfigContainer(
			Codec<T> codec, Path configPath, Supplier<T> defaultConfigInitializer,
			boolean reloadsBeforeServer, boolean reloadsAfterServer, Optional<String> configScreenName,
			ReloadFunction<T> reloader
	) {
		this.codec = codec;
		this.configPath = configPath;
		this.defaultConfigInitializer = defaultConfigInitializer;
		this.reloadsBeforeServer = reloadsBeforeServer;
		this.reloadsAfterServer = reloadsAfterServer;
		this.configScreenName = configScreenName;
		this.reloader = reloader;
		containers.put(this, Unit.INSTANCE);
	}

	protected T initDefaultConfig() {
		return defaultConfigInitializer.get();
	}

	protected Optional<T> loadFromFile() {
		return JanksonHelper.load(configPath, codec);
	}

	@Override
	public T get() {
		if (config == null) {
			try {
				config = loadFromFile().orElse(null);
				if (config != null) {
					triggerLoad(Optional.empty());
				} else {
					var file = configPath.toFile();
					if (file.exists()) {
						METAcraftLib.LOGGER.error("Unable to load existing config, backing up and creating new default config.");
						var name = configPath.getFileName().toString().split("\\.")[0];
						Path target = configPath.getParent().resolve(name +".bak.json");
						int num = 1;
						while (target.toFile().exists()) {
							target = configPath.getParent().resolve(name +".bak" + num++ + ".json");
							if (num > 10) {
								break;
							}
						}
						try {
							Files.copy(configPath, target, StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException err) {
							METAcraftLib.LOGGER.fatal("Unable to backup config file! You may have lost stuff!");
							err.printStackTrace();
						}
					}

					config = initDefaultConfig();
					save();
				}
			} catch (Throwable t) {
				METAcraftLib.LOGGER.error("Unable to parse config: ", t);
			}
		} else {
			if (!modifiers.isEmpty()) {
				var config = this.config;
				for (var modifier : modifiers) {
					config = modifier.apply(config);
				}
				if (config != this.config) {
					this.config = config;
					save();
				}
				modifiers.clear();
			}
		}
		return config;
	}

	@Override
	public Codec<T> codec() {
		return codec;
	}

	private void triggerLoad(Optional<ReloadCause> cause) {
		if (config instanceof LoadAware aware) {
			aware.afterLoad(cause);
		}
	}

	@Override
	public void reload(ReloadCause cause) {
		if (config instanceof ReloadAware r) {
			r.beforeReload(cause);
		}
		config = reloader.reload(config, this::loadFromFile, cause);
		triggerLoad(Optional.of(cause));
		onReload.accept(cause);
	}

	private <C extends ModificationAware<C>> T onModified(ModificationAware<C> oldConfig) {
		if (config instanceof ModificationAware<?>) {
			try {
				//noinspection unchecked
				return (T) ((ModificationAware<C>) config).onModified((C) oldConfig);
			} catch (ClassCastException ignored) {}
		}
		return config;
	}

	@Override
	public void modify(UnaryOperator<T> modifier) {
		if (this.config != null) {
			var prevConfig = config;
			this.config = modifier.apply(config);
			if (prevConfig != config) {
				if (prevConfig instanceof ModificationAware<?> prev) {
					config = onModified(prev);
				}
				save();
			}
		} else {
			modifiers.add(modifier);
		}
	}

	@Override
	public Optional<String> name() {
		return configScreenName;
	}

	@Override
	public void save() {
		if (config == null) return;
		JanksonHelper.save(configPath, codec, config);
	}

	@Override
	public void addReloadHandler(Consumer<ReloadCause> handler) {
		onReload = onReload.andThen(handler);
	}

	public static class WithLookup<T> extends BasicConfigContainer<T> {

		protected final Supplier<HolderLookup.Provider> lookupSupplier;
		protected final Function<HolderLookup.Provider, T> defaultConfigInitializer;

		public WithLookup(
				Codec<T> codec, Path configPath, Function<HolderLookup.Provider, T> defaultConfigInitializer,
				boolean reloadsBeforeServer, boolean reloadsAfterServer, Optional<String> configScreenName, ReloadFunction<T> reloader,
				Supplier<HolderLookup.Provider> lookupSupplier
		) {
			super(codec, configPath, null, reloadsBeforeServer, reloadsAfterServer, configScreenName, reloader);
			this.lookupSupplier = lookupSupplier;
			this.defaultConfigInitializer = defaultConfigInitializer;
		}

		@Override
		protected Optional<T> loadFromFile() {
			return JsonHelper.load(configPath, codec, lookupSupplier.get());
		}

		@Override
		public void save() {
			if (config == null) return;
			JsonHelper.save(configPath, codec, config, lookupSupplier.get());
		}

		@Override
		protected T initDefaultConfig() {
			return defaultConfigInitializer.apply(lookupSupplier.get());
		}

	}

}
