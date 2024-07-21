package se.datasektionen.mc.metacraft_lib.config.container.impl;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import se.datasektionen.mc.metacraft_lib.config.container.ServerAwareConfigContainer;
import se.datasektionen.mc.metacraft_lib.config.extensions.Modifiable;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadCause;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadFunction;
import se.datasektionen.mc.metacraft_lib.config.extensions.ServerLoadAware;
import se.datasektionen.mc.metacraft_lib.config.extensions.ServerUnloadAware;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BasicServerAwareConfigContainer<T, S> extends BasicConfigContainer<T> implements ServerAwareConfigContainer<T, S> {

	private static final Set<BasicServerAwareConfigContainer<?, ?>> containers = new HashSet<>();

	static {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			containers.forEach(container -> container.unload(server));
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			if (success) {
				containers.forEach(container -> {
					if (!container.reloadsAfterServer) {
						container.reloadServerCache(server, ReloadCause.AFTER_SERVER_RELOAD);
					}
				});
			}
		});
	}

	protected final Map<MinecraftServer, S> serverCache = new HashMap<>();
	protected final ReloadFunction<S> cacheReloader;
	private final BiFunction<T, MinecraftServer, S> serverParse;

	public BasicServerAwareConfigContainer(
			Codec<T> codec, Path configPath, Supplier<T> defaultConfigInitializer, boolean reloadsBeforeServer,
			boolean reloadsAfterServer, ReloadFunction<T> reloader, BiFunction<T, MinecraftServer, S> serverParse,
			ReloadFunction<S> cacheReloader
	) {
		super(codec, configPath, defaultConfigInitializer, reloadsBeforeServer, reloadsAfterServer, reloader);
		this.serverParse = serverParse;
		this.cacheReloader = cacheReloader;
		containers.add(this);
	}
	
	private S loadNewServerConfig(MinecraftServer server) {
		return serverParse.apply(get(), server);
	}

	private void triggerLoad(MinecraftServer server, S cache, Optional<ReloadCause> cause) {
		if (cache instanceof ServerLoadAware aware) {
			aware.afterLoad(server, cause);
		}
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
	public void reload(ReloadCause cause) {
		super.reload(cause);
		reloadServerCache(cause);
	}

	private static void triggerUnload(MinecraftServer server, Object config, Optional<ReloadCause> cause) {
		if (config instanceof ServerUnloadAware aware) {
			aware.beforeUnload(server, cause);
		}
	}

	private void unload(MinecraftServer server) {
		triggerUnload(server, serverCache.get(server), Optional.empty());
		serverCache.remove(server);
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

	@Override
	public void modify(Predicate<T> modifier) {
		super.modify(modifier);
		if (this.config instanceof Modifiable m && m.isModified()) {
			reloadServerCache(ReloadCause.REFRESH_CACHE_AFTER_MODIFY);
		}
	}

}
