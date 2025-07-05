package nu.metacraft.lib.config.container;

import com.mojang.serialization.Codec;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.impl.BasicConfigContainer;

import java.nio.file.Path;
import java.util.function.*;

/**
 * A config container contains the config instance and takes care of saving/loading the config file.
 * @param <T> The type of the config instance.
 */
public interface ConfigContainer<T> extends ConfigContainerBase<T>, ConfigContainerWithSingleton<T> {

	/**
	 * Modifies the config
	 * @param modifier A function that modifies the config. If it returns true, the change will be saved, otherwise it will not.
	 * @throws IllegalStateException If config is not modifiable.
	 */
	void modify(Predicate<T> modifier);

	class Builder<T> {

		protected final Codec<T> codec;
		protected final Supplier<T> defaultConfigInitializer;
		protected Function<RegistryWrapper.WrapperLookup, T> defaultConfigInitializerWithLookup;
		protected boolean reloadsBeforeServer = false;
		protected boolean reloadsAfterServer = false;
		protected ReloadFunction<T> reloader = ReloadFunction.getDefault();

		public static <T> Builder<T> create(Codec<T> codec, Supplier<T> defaultConfigInitializer) {
			return new Builder<>(codec, defaultConfigInitializer);
		}

		protected Builder(Codec<T> codec, Supplier<T> defaultConfigInitializer) {
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

		public Builder<T> registryAvailableConfigInitializer(Function<RegistryWrapper.WrapperLookup, T> defaultConfigInitializerWithLookup) {
			this.defaultConfigInitializerWithLookup = defaultConfigInitializerWithLookup;
			return this;
		}

		/**
		 * Builds a normal config container.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(Path configPath) {
			return new BasicConfigContainer<>(codec, configPath, defaultConfigInitializer, reloadsBeforeServer, reloadsAfterServer, reloader);
		}

		/**
		 * Builds a normal config container with registry access.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(Path configPath, Supplier<RegistryWrapper.WrapperLookup> lookupSupplier) {
			return new BasicConfigContainer.WithLookup<>(
					codec, configPath,
					defaultConfigInitializerWithLookup != null ? defaultConfigInitializerWithLookup : l -> defaultConfigInitializer.get(),
					reloadsBeforeServer, reloadsAfterServer, reloader, lookupSupplier
			);
		}

		/**
		 * Builds a normal config container with registry access.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(Path configPath, RegistryWrapper.WrapperLookup lookup) {
			return build(configPath, () -> lookup);
		}

		/**
		 * Builds a config container with an additional registry aware cache creator.
		 * Sometimes you may want to use codecs that require a valid {@link net.minecraft.registry.RegistryWrapper.WrapperLookup}
		 * which is obviously not available at load time with the rest of the config.
		 * Therefore, you can use {@link ObjectStorage} or similar to hold
		 * the raw objects in the config. Then, you can use {@link ServerAware#get(MinecraftServer)}
		 * to fetch the object you create with parser. The config container will cache it for you.
		 * Note that this object will always be destroyed when the /reload command is executed
		 * (even if reloadAfterServer is disabled!) since the objects stored in the cache might no
		 * longer be registered after the reload.
		 * @param parser A function that takes the config and the current Minecraft server and returns an object storing any parameters that could normally not be decoded.
		 * @return The config container.
		 * @param <S> The type of the object storing the cached values.
		 */
		public <S> ServerAware<ConfigContainer<T>, S> buildRegistryAware(
				Path configPath,
				BiFunction<T, MinecraftServer, S> parser
		) {
			return buildRegistryAware(configPath, parser, ReloadFunction.getDefault());
		}

		/**
		 * Builds a config container with an additional registry aware cache creator.
		 * Sometimes you may want to use codecs that require a valid {@link net.minecraft.registry.RegistryWrapper.WrapperLookup}
		 * which is obviously not available at load time with the rest of the config.
		 * Therefore, you can use {@link ObjectStorage} or similar to hold
		 * the raw objects in the config. Then, you can use {@link ServerAware#get(MinecraftServer)}
		 * to fetch the object you create with parser. The config container will cache it for you.
		 * Note that this object will always be destroyed when the /reload command is executed
		 * (even if reloadAfterServer is disabled!) since the objects stored in the cache might no
		 * longer be registered after the reload.
		 * @param parser A function that takes the config and the current Minecraft server and returns an object storing any parameters that could normally not be decoded.
		 * @param cacheReloader Function used to reload the cache.
		 * @return The config container.
		 * @param <S> The type of the object storing the cached values.
		 */
		public <S> ServerAware<ConfigContainer<T>, S> buildRegistryAware(
				Path configPath,
				BiFunction<T, MinecraftServer, S> parser,
				ReloadFunction<S> cacheReloader
		) {
			return ServerAware.<ConfigContainer<T>, S>wrap(
					build(configPath),
					(config, server) -> parser.apply(config.get(), server),
					cacheReloader
			);
		}
	}
}
