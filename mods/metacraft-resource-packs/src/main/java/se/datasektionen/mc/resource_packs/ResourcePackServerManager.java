package se.datasektionen.mc.resource_packs;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.net.UnknownHostException;
import java.util.Map;
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
			} catch (UnknownHostException e) {
				ResourcePacks.LOGGER.error(e);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (!servers.containsKey(server)) return;
			servers.get(server).close();
			servers.remove(server);
		});
	}

}
