package nu.metacraft.resource_packs;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.net.BindException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ResourcePackServerManager {

	private static final Map<MinecraftServer, ResourcePackServer> servers = new ConcurrentHashMap<>();

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (servers.containsKey(server)) return;
			try {
				var s = ResourcePackConfig.getConfig().createResourcePackServer(server);
				servers.put(server, s);
				s.start();
			} catch (UnknownHostException | BindException e) {
				ResourcePacks.LOGGER.error("Unable to start resource pack server: " + e.getMessage(), e);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (!servers.containsKey(server)) return;
			servers.get(server).close();
			servers.remove(server);
		});
	}

	public static Set<MinecraftServer> getServers() {
		return servers.keySet();
	}
}
