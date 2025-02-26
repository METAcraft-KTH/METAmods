package se.datasektionen.mc.metacraft_core.music;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

public class MusicTimerTracker {

	private static final Map<MinecraftServer, Timer> TIMER_MAP = new ConcurrentHashMap<>();

	public static Timer getTimer(MinecraftServer server) {
		return TIMER_MAP.get(server);
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(
				server -> {
					if (!TIMER_MAP.containsKey(server)) {
						TIMER_MAP.put(server, new Timer());
					}
				}
		);
		ServerLifecycleEvents.SERVER_STOPPED.register(
				server -> {
					var timer = TIMER_MAP.remove(server);
					if (timer != null) {
						timer.cancel();
					}
				}
		);
	}

}
