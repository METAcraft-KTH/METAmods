package nu.metacraft.lib.config.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.impl.ServerAwareWrapper;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ServerAware<C extends ConfigContainerBase<?>, S> {

	S get(MinecraftServer server);

	C getContainer();

	static <C extends ConfigContainerBase<?>, S> ServerAware<C, S> wrap(
		C container, Parser<C, S> serverParse,
		ReloadFunction<S> cacheReloader, Supplier<ObjectStorage<S>> defaultInitializer
	) {
		return ServerAware.wrap(container, serverParse, cacheReloader, server -> defaultInitializer.get(), false);
	}

	static <C extends ConfigContainerBase<?>, S> ServerAware<C, S> wrap(
			C container, Parser<C, S> serverParse,
			ReloadFunction<S> cacheReloader, Function<MinecraftServer, ObjectStorage<S>> defaultInitializer,
			boolean forceInitializeOnStartup
	) {
		return new ServerAwareWrapper<>(container, serverParse, cacheReloader, defaultInitializer, forceInitializeOnStartup);
	}

	class ConfigPair<T, S> {

		private final T staticValues;
		private ObjectStorage<S> serverAwareValues;
		private Function<MinecraftServer, ObjectStorage<S>> serverAwareValuesInitializer;

		public ConfigPair(T staticValues, Function<MinecraftServer, ObjectStorage<S>> serverAwareValues) {
			this.staticValues = staticValues;
			this.serverAwareValuesInitializer = serverAwareValues;
		}

		public ConfigPair(T staticValues, ObjectStorage<S> serverAwareValues) {
			this.staticValues = staticValues;
			this.serverAwareValues = serverAwareValues;
		}

		public T staticValues() {
			return staticValues;
		}

		public ObjectStorage<S> serverAwareValues(MinecraftServer server, ConfigContainerBase<?> container) {
			if (serverAwareValues == null) {
				serverAwareValues = serverAwareValuesInitializer.apply(server);
				container.save();
				serverAwareValuesInitializer = null;
			}
			return serverAwareValues;
		}

		public ObjectStorage<S> serverAwareValues() {
			if (serverAwareValues == null) {
				throw new IllegalStateException("Server aware values not initialized yet!");
			}
			return serverAwareValues;
		}

		public static <T, S> Codec<ConfigPair<T, S>> createCodec(MapCodec<T> codec, MapCodec<S> valueCodec, boolean refreshOnReload) {
			return RecordCodecBuilder.create(instance -> instance.group(
					codec.forGetter(ConfigPair::staticValues),
					ObjectStorage.createCodec(valueCodec, refreshOnReload).forGetter(
						pair -> pair.serverAwareValues != null ?
							pair.serverAwareValues :
							ObjectStorage.fromData(valueCodec.codec(), Map.of(), refreshOnReload)
					)
			).apply(instance, ConfigPair::new));
		}
	}

	@FunctionalInterface
	interface Parser<C extends ConfigContainerBase<?>, S> {
		DataResult<S> parse(C config, MinecraftServer server);
	}

	static <T, S> ReloadFunction<ConfigPair<T, S>> wrapReload(ReloadFunction<T> reloader) {
		return (old, updated, reason) -> {
			var reloaded = reloader.reload(old != null ? old.staticValues() : null, () -> updated.get().map(ConfigPair::staticValues), reason);
			var cacheReloaded = updated.get().map(ConfigPair::serverAwareValues).orElse(old != null ? old.serverAwareValues() : null);
			if (reloaded == null || cacheReloaded == null) return null;
			return new ConfigPair<>(reloaded, cacheReloaded);
		};
	}
}
