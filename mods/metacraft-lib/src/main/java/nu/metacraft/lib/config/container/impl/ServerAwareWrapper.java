package nu.metacraft.lib.config.container.impl;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Unit;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.ConfigContainerBase;
import nu.metacraft.lib.config.container.ReloadCause;
import nu.metacraft.lib.config.container.ReloadFunction;
import nu.metacraft.lib.config.container.ServerAware;
import nu.metacraft.lib.config.extensions.ServerLoadAware;
import nu.metacraft.lib.config.extensions.ServerUnloadAware;

import java.util.*;
import java.util.function.Supplier;

public class ServerAwareWrapper<C extends ConfigContainerBase<?>, S> implements ServerAware<C, S> {

	private final C container;

	private static final WeakHashMap<ServerAwareWrapper<?, ?>, Unit> wrappers = new WeakHashMap<>();

	protected final Map<MinecraftServer, S> serverCache = new HashMap<>();
	protected final ReloadFunction<S> cacheReloader;
	private final Parser<C, S> serverParse;
	private final Supplier<ObjectStorage<S>> defaultInitializer;
	
	static {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			wrappers.keySet().forEach(container -> container.unload(server));
		});
	}

	public ServerAwareWrapper(
			C container, Parser<C, S> serverParse,
			ReloadFunction<S> cacheReloader, Supplier<ObjectStorage<S>> defaultInitializer
	) {
		this.container = container;
		this.serverParse = serverParse;
		this.defaultInitializer = defaultInitializer;
		this.cacheReloader = cacheReloader;
		wrappers.put(this, Unit.INSTANCE);
		container.addReloadHandler(this::reloadServerCache);
	}

	private void triggerLoad(MinecraftServer server, S cache, Optional<ReloadCause> cause) {
		if (cache instanceof ServerLoadAware aware) {
			aware.afterLoad(server, cause);
		}
	}

	private S loadNewServerConfig(MinecraftServer server) {
		return serverParse.parse(container, server).resultOrPartial(
				METAcraftLib.LOGGER::error
		).orElseGet(() -> defaultInitializer.get().parse(server.reloadableRegistries().lookup()).getOrThrow());
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
