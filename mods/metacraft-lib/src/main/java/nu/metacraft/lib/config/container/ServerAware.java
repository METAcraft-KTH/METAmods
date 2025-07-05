package nu.metacraft.lib.config.container;

import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.config.container.impl.ServerAwareWrapper;

import java.util.function.BiFunction;

public interface ServerAware<C extends ConfigContainerBase<?>, S> {

	S get(MinecraftServer server);

	C getContainer();

	static <C extends ConfigContainerBase<?>, S> ServerAware<C, S> wrap(
			C container, BiFunction<C, MinecraftServer, S> serverParse,
			ReloadFunction<S> cacheReloader
	) {
		return new ServerAwareWrapper<>(container, serverParse, cacheReloader);
	}

}
