package nu.metacraft.lib.config.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.impl.ServerAwareWrapper;

import java.util.function.Supplier;

public interface ServerAware<C extends ConfigContainerBase<?>, S> {

	S get(MinecraftServer server);

	C getContainer();

	static <C extends ConfigContainerBase<?>, S> ServerAware<C, S> wrap(
			C container, Parser<C, S> serverParse,
			ReloadFunction<S> cacheReloader, Supplier<ObjectStorage<S>> defaultInitializer
	) {
		return new ServerAwareWrapper<>(container, serverParse, cacheReloader, defaultInitializer);
	}

	record ConfigPair<T, S>(T staticValues, ObjectStorage<S> serverAwareValues) {
		public static <T, S> Codec<ConfigPair<T, S>> createCodec(MapCodec<T> codec, MapCodec<S> valueCodec, boolean refreshOnReload) {
			return RecordCodecBuilder.create(instance -> instance.group(
					codec.forGetter(ConfigPair::staticValues),
					ObjectStorage.createCodec(valueCodec, refreshOnReload).forGetter(ConfigPair::serverAwareValues)
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
