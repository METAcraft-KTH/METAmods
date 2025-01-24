package se.datasektionen.mc.metacraft_lib.config.container.impl;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import se.datasektionen.mc.metacraft_lib.config.container.*;
import se.datasektionen.mc.metacraft_lib.config.extensions.ServerLoadAware;
import se.datasektionen.mc.metacraft_lib.config.extensions.ServerUnloadAware;

import java.util.*;
import java.util.function.BiFunction;

public class ServerAwareWrapper<C extends ConfigContainerBase<?>, S> implements ServerAware<C, S> {

	private final C container;

	private static final Set<ServerAwareWrapper<?, ?>> wrappers = new HashSet<>();

	protected final Map<MinecraftServer, S> serverCache = new HashMap<>();
	protected final ReloadFunction<S> cacheReloader;
	private final BiFunction<C, MinecraftServer, S> serverParse;
	
	static {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			wrappers.forEach(container -> container.unload(server));
		});
	}

	public ServerAwareWrapper(
			C container, BiFunction<C, MinecraftServer, S> serverParse,
			ReloadFunction<S> cacheReloader
	) {
		this.container = container;
		this.serverParse = serverParse;
		this.cacheReloader = cacheReloader;
		wrappers.add(this);
		container.addReloadHandler(this::reloadServerCache);
	}

	private void triggerLoad(MinecraftServer server, S cache, Optional<ReloadCause> cause) {
		if (cache instanceof ServerLoadAware aware) {
			aware.afterLoad(server, cause);
		}
	}

	private S loadNewServerConfig(MinecraftServer server) {
		return serverParse.apply(container, server);
	}

	@Override
	public S get(MinecraftServer server) {
		if (!serverCache.containsKey(server)) {
			var instance = loadNewServerConfig(server);
			serverCache.put(server, instance);
			triggerLoad(server, instance, Optional.empty());
		}
		return serverCache.get(server);
	}

	@Override
	public C getContainer() {
		return container;
	}

	private void reloadServerCache(MinecraftServer server, ReloadCause cause) {
		var opt = Optional.of(cause);
		triggerUnload(server, serverCache.get(server), opt);
		serverCache.computeIfPresent(
				server, (s, config) -> cacheReloader.reload(
						config, () -> Optional.of(loadNewServerConfig(server)), cause
				)
		);
		triggerLoad(server, serverCache.get(server), opt);
	}

	private void reloadServerCache(ReloadCause cause) {
		var servers = new ArrayList<>(serverCache.keySet());
		servers.forEach(server -> {
			if (server.isRunning()) {
				reloadServerCache(server, cause);
			} else {
				unload(server);
			}
		});
	}

	private void unload(MinecraftServer server) {
		triggerUnload(server, serverCache.get(server), Optional.empty());
		serverCache.remove(server);
	}

	private static void triggerUnload(MinecraftServer server, Object config, Optional<ReloadCause> cause) {
		if (config instanceof ServerUnloadAware aware) {
			aware.beforeUnload(server, cause);
		}
	}

}
