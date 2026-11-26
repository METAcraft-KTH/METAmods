package se.metacraft.config.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import se.metacraft.config.util.ObjectStorage;
import se.metacraft.config.container.impl.BasicConfigContainer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.*;
import java.util.stream.Stream;

/**
 * A config container contains the config instance and takes care of saving/loading the config file.
 * @param <T> The type of the config instance.
 */
public interface ConfigContainer<T> extends ConfigContainerBase<T>, ConfigContainerWithSingleton<T> {

	Event<ConfigFinder> CONFIG_FINDER = EventFactory.createArrayBacked(
		ConfigFinder.class, events -> () -> Arrays.stream(events).map(
			ConfigFinder::containers
		).reduce(
			Stream.of(), Stream::concat
		)
	);

	interface ConfigFinder {
		Stream<? extends ConfigContainer<?>> containers();
	}

	Codec<T> codec();

	/**
	 * Modifies the config
	 * @param modifier A function that replaces the config.
	 */
	void modify(UnaryOperator<T> modifier);

	Optional<String> name();

	class Builder<T> {

		protected final MapCodec<T> codec;
		protected final Supplier<T> defaultConfigInitializer;
		protected Function<HolderLookup.Provider, T> defaultConfigInitializerWithLookup;
		protected boolean reloadsBeforeServer = false;
		protected boolean reloadsAfterServer = false;
		protected boolean resaveOnServerStart = false;
		protected Optional<Path> configPath = Optional.empty();
		protected boolean supportsConfigScreen = true;
		protected ReloadFunction<T> reloader = ReloadFunction.getDefault();

		public static <T> Builder<T> create(MapCodec<T> codec, Supplier<T> defaultConfigInitializer) {
			return new Builder<>(codec, defaultConfigInitializer);
		}

		protected Builder(MapCodec<T> codec, Supplier<T> defaultConfigInitializer) {
			this.codec = codec;
			this.defaultConfigInitializer = defaultConfigInitializer;
		}

		/**
		 * Always attempt to reload the config whenever the server reload (whenever /reload is executed),
		 * will reload the config even if the datapack reload fails.
		 * @return The builder.
		 */
		public Builder<T> reloadBeforeServer() {
			this.reloadsBeforeServer = true;
			return this;
		}

		/**
		 * Reload the config after the server reload is completed.
		 * Will only reload if the server reload was successful.
		 * @return The builder.
		 */
		public Builder<T> reloadAfterServer() {
			this.reloadsAfterServer = true;
			return this;
		}

		/**
		 * Forces the config to save when the server starts.
		 * Necessary for translation keys defined via server-translations to work properly.
		 * @return The builder.
		 */
		public Builder<T> resaveOnServerStart() {
			this.resaveOnServerStart = true;
			return this;
		}

		/**
		 * Set a custom reloading function.
		 * Allows you to reload the config in multiple steps.
		 * For example, reloading commands before the server reload, then reloading items after.
		 * @param reloader The new reloader function. Takes the old config, a function that might create a new config as well as the reload cause as arguments.
		 * @return The builder.
		 */
		public Builder<T> setReloader(ReloadFunction<T> reloader) {
			this.reloader = reloader;
			return this;
		}

		public Builder<T> registryAvailableConfigInitializer(Function<HolderLookup.Provider, T> defaultConfigInitializerWithLookup) {
			this.defaultConfigInitializerWithLookup = defaultConfigInitializerWithLookup;
			return this;
		}

		public Builder<T> withPath(Path path) {
			this.configPath = Optional.of(path);
			return this;
		}

		public Builder<T> disableConfigScreen() {
			supportsConfigScreen = false;
			return this;
		}

		private static Path defaultPath(String name) {
			return FabricLoader.getInstance().getConfigDir().resolve(name + ".json5");
		}

		/**
		 * Builds a normal config container.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(String name) {
			return new BasicConfigContainer<>(
				codec.codec(), configPath.orElse(defaultPath(name)), defaultConfigInitializer,
				reloadsBeforeServer, reloadsAfterServer,
				resaveOnServerStart,
				supportsConfigScreen ? Optional.of(name) : Optional.empty(),
				reloader
			);
		}

		/**
		 * Builds a normal config container with registry access.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(String name, Supplier<HolderLookup.Provider> lookupSupplier) {
			return new BasicConfigContainer.WithLookup<>(
					codec.codec(), configPath.orElse(defaultPath(name)),
					defaultConfigInitializerWithLookup != null ? defaultConfigInitializerWithLookup : l -> defaultConfigInitializer.get(),
					reloadsBeforeServer, reloadsAfterServer,
					resaveOnServerStart,
					supportsConfigScreen ? Optional.of(name) : Optional.empty(),
					reloader, lookupSupplier
			);
		}

		/**
		 * Builds a normal config container with registry access.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(String name, HolderLookup.Provider lookup) {
			return build(name, () -> lookup);
		}

		/**
		 * Converts this builder into a registry aware builder.
		 * Make sure to run all non-registry-dependent functions first.
		 * @param registryAwareCodec The codec to use for the registry-aware part.
		 * @return The registry aware builder.
		 * @param <S> The type of the registry aware part.
		 */
		public <S> RegistryAwareBuilder<S> makeRegistryAware(MapCodec<S> registryAwareCodec) {
			return new RegistryAwareBuilder<>(registryAwareCodec);
		}

		public class RegistryAwareBuilder<S> {

			private static final ServerAware.Parser<? extends ConfigContainer<ServerAware.ConfigPair<?, Object>>, Object> NON_RELOADABLE = (config, server) ->
					config.get().serverAwareValues(server, config).parse(server.registryAccess());
			private static final ServerAware.Parser<? extends ConfigContainer<ServerAware.ConfigPair<?, Object>>, Object> RELOADABLE = (config, server) ->
					config.get().serverAwareValues(server, config).parse(server.reloadableRegistries().lookup());

			private final MapCodec<S> serverAwareCodec;
			private ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> parser;
			private boolean refreshOnReload = false;
			private Function<MinecraftServer, ObjectStorage<S>> defaultRegistryAwareInitializer;
			private boolean delayedInitializer = false;
			private ReloadFunction<S> cacheReloader = ReloadFunction.getDefault();

			public RegistryAwareBuilder(MapCodec<S> codec) {
				this.serverAwareCodec = codec;
				//Super hacky hack. I just like having them be constant, we never reference the typeset variables in the default parser anyway.
				//If you feel like rewriting this, beware that I check if parser is the same object as NON_RELOADABLE further down to know if it should switch to RELOADABLE or not,
				//so any alternate implementation will need to cover that as well.
				//noinspection unchecked
				this.parser = (ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S>) (Object) NON_RELOADABLE;
			}

			/**
			 * Sets the default initializer manually.
			 * Note that this will ignore teh value of {@link RegistryAwareBuilder#refreshOnReload}.
			 * @param initializer The initializer.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setInitializerManually(Supplier<ObjectStorage<S>> initializer) {
				this.defaultRegistryAwareInitializer = server -> initializer.get();
				return this;
			}

			/**
			 * Sets the default initializer without registries.
			 * @param initializer The initializer.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setInitializer(Supplier<S> initializer) {
				this.defaultRegistryAwareInitializer = server -> ObjectStorage.fromValue(
						serverAwareCodec.codec(), initializer.get(), refreshOnReload
				);
				return this;
			}

			private HolderLookup.Provider getLookup(MinecraftServer server) {
				if (refreshOnReload) {
					return server.reloadableRegistries().lookup();
				} else {
					return server.registryAccess();
				}
			}

			/**
			 * Sets the default initializer using builtin registries.
			 * @param initializer The initializer.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setInitializer(Function<HolderLookup.Provider, S> initializer) {
				this.defaultRegistryAwareInitializer = server -> {
					if (server != null) {
						var lookup = getLookup(server);
						return ObjectStorage.fromValue(
							serverAwareCodec.codec(), initializer.apply(lookup), lookup, refreshOnReload
						);
					} else {
						return ObjectStorage.fromValueWithDefaultOps(
							serverAwareCodec.codec(), initializer, refreshOnReload
						);
					}
				};
				return this;
			}

			/**
			 * Overrides the default parser, in case you want access to more than just registries for example.
			 * @param parser The parser to use.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setParser(ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> parser) {
				this.parser = parser;
				return this;
			}

			/**
			 * Changes the reload function.
			 * @param cacheReloader The new reload function to use.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setReloader(ReloadFunction<S> cacheReloader) {
				this.cacheReloader = cacheReloader;
				return this;
			}

			/**
			 * Delays the server-aware initializer creation, instead creating it when the server is started.
			 * If you run this, the config may end up overwriting itself when loaded for the first time.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> delayServerAwareInitializer() {
				this.delayedInitializer = true;
				return this;
			}

			/**
			 * Notify that this config should always refresh the ObjectStorage on reload.
			 * If using the default parser, it will also gain access to reloadable registries (such as predicates for example).
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> refreshOnReload() {
				if (defaultRegistryAwareInitializer != null) throw new IllegalStateException("Please run refreshOnReload before setInitializer!");
				refreshOnReload = true;
				if ((Object) parser == NON_RELOADABLE) {
					//Super hacky hack. I just like having them be constant, we never reference the typeset variables in the default parser anyway.
					//noinspection unchecked
					parser = (ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S>) (Object) RELOADABLE;
				}
				return this;
			}

			/**
			 * Builds a normal config container.
			 * @return The config container.
			 */
			public ServerAware<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> build(String name) {
				if (defaultRegistryAwareInitializer == null) throw new IllegalStateException("Please set the initializer first");
				return ServerAware.wrap(
						new BasicConfigContainer<>(
								ServerAware.ConfigPair.createCodec(codec, serverAwareCodec, refreshOnReload),
								configPath.orElse(defaultPath(name)), () -> {
									if (delayedInitializer) {
										return new ServerAware.ConfigPair<>(
											defaultConfigInitializer.get(), defaultRegistryAwareInitializer
										);
									} else {
										return new ServerAware.ConfigPair<>(
											defaultConfigInitializer.get(), defaultRegistryAwareInitializer.apply(null)
										);
									}
								},
								reloadsBeforeServer, reloadsAfterServer,
								resaveOnServerStart,
								supportsConfigScreen ? Optional.of(name) : Optional.empty(),
								ServerAware.wrapReload(reloader)
						),
						parser, cacheReloader, defaultRegistryAwareInitializer, delayedInitializer
				);
			}
		}
	}
}
