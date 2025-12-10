package nu.metacraft.core.music;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.core.extensions.ServerPlayerExtensions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MusicTimerTracker {

	private static final Map<MinecraftServer, ScheduledExecutorService> TIMER_MAP = new ConcurrentHashMap<>();

	public static ScheduledExecutorService getTimer(MinecraftServer server) {
		return TIMER_MAP.get(server);
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(
				server -> {
					if (!TIMER_MAP.containsKey(server)) {
						TIMER_MAP.put(server, Executors.newSingleThreadScheduledExecutor());
					}
				}
		);
		ServerLifecycleEvents.SERVER_STOPPED.register(
				server -> {
					var timer = TIMER_MAP.remove(server);
					if (timer != null) {
						timer.close();
					}
				}
		);
	}

	public static class SendPacketTask implements Runnable {

		private final ServerPlayer player;
		private final MusicEntry toPlay;
		private final Packet<?> packet;

		public SendPacketTask(ServerPlayer player, MusicEntry toPlay, Packet<?> packet) {
			this.player = player;
			this.toPlay = toPlay;
			this.packet = packet;
		}

		@Override
		public void run() {
			if (((ServerPlayerExtensions) player).metacraft_core$hasMusicEntry(toPlay)) {
				player.connection.send(packet);
			}
		}
	}

}
