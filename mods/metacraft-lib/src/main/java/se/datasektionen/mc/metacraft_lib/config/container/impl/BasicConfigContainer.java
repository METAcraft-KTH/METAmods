package se.datasektionen.mc.metacraft_lib.config.container.impl;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Unit;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.config.container.ConfigContainer;
import se.datasektionen.mc.metacraft_lib.config.JsonHelper;
import se.datasektionen.mc.metacraft_lib.config.extensions.Modifiable;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadCause;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadFunction;
import se.datasektionen.mc.metacraft_lib.config.extensions.LoadAware;
import se.datasektionen.mc.metacraft_lib.config.extensions.ReloadAware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
	}

	protected final Codec<T> codec;
	protected final Path configPath;
	protected final Supplier<T> defaultConfigInitializer;
	protected final boolean reloadsBeforeServer;
	protected final boolean reloadsAfterServer;
	protected final ReloadFunction<T> reloader;
	protected Consumer<ReloadCause> onReload = cause -> {};

	protected T config;

	protected final List<Predicate<T>> modifiers = new ArrayList<>();

	public BasicConfigContainer(
			Codec<T> codec, Path configPath, Supplier<T> defaultConfigInitializer,
			boolean reloadsBeforeServer, boolean reloadsAfterServer,
			ReloadFunction<T> reloader
	) {
		this.codec = codec;
		this.configPath = configPath;
		this.defaultConfigInitializer = defaultConfigInitializer;
		this.reloadsBeforeServer = reloadsBeforeServer;
		this.reloadsAfterServer = reloadsAfterServer;
		this.reloader = reloader;
		containers.put(this, Unit.INSTANCE);
	}

	protected T initDefaultConfig() {
		return defaultConfigInitializer.get();
	}

	protected Optional<T> loadFromFile() {
		return JsonHelper.load(configPath, codec);
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
		} else if (config instanceof Modifiable modifiable) {
			if (!modifiers.isEmpty()) {
				boolean modified = modifiers.stream().map(
						action -> action.test(config)
				).reduce((lhs, rhs) -> lhs || rhs).orElse(false);
				if (modified) {
					modifiable.setModified(true);
				}
				modifiers.clear();
			}
			if (modifiable.isModified()) {
				save();
				modifiable.setModified(false);
			}
		}
		return config;
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
	}

	@Override
	public void modify(Predicate<T> modifier) {
		if (this.config != null) {
			if (this.config instanceof Modifiable modifiable) {
				if (modifier.test(config)) {
					modifiable.setModified(true);
				}
			} else {
				throw new IllegalStateException("Config is not modifiable!");
			}
		} else {
			modifiers.add(modifier);
		}
	}

	@Override
	public void save() {
		if (config == null) return;
		JsonHelper.save(configPath, codec, config);
	}

	@Override
	public void addReloadHandler(Consumer<ReloadCause> handler) {
		onReload = onReload.andThen(handler);
	}

	public static class WithLookup<T> extends BasicConfigContainer<T> {

		protected final Supplier<RegistryWrapper.WrapperLookup> lookupSupplier;
		protected final Function<RegistryWrapper.WrapperLookup, T> defaultConfigInitializer;

		public WithLookup(
				Codec<T> codec, Path configPath, Function<RegistryWrapper.WrapperLookup, T> defaultConfigInitializer,
				boolean reloadsBeforeServer, boolean reloadsAfterServer, ReloadFunction<T> reloader,
				Supplier<RegistryWrapper.WrapperLookup> lookupSupplier
		) {
			super(codec, configPath, null, reloadsBeforeServer, reloadsAfterServer, reloader);
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
