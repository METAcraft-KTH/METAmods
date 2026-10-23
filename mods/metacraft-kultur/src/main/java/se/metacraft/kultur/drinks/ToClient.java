package se.metacraft.kultur.drinks;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * The packets the drinks send a player's own client (the stagger's push, the blackout's line), all
 * through here so a game test can see what a client would have been told.
 */
public final class ToClient {
	private static final List<BiConsumer<ServerPlayer, Packet<?>>> WATCHERS = new CopyOnWriteArrayList<>();

	private ToClient() {}

	static void send(ServerPlayer player, Packet<?> packet) {
		player.connection.send(packet);
		for (var watcher : WATCHERS) watcher.accept(player, packet);
	}

	/** Shows the watcher every packet sent through here until the returned handle is closed. For the game tests. */
	public static Watch watch(BiConsumer<ServerPlayer, Packet<?>> watcher) {
		WATCHERS.add(watcher);
		return () -> WATCHERS.remove(watcher);
	}

	public interface Watch extends AutoCloseable {
		@Override
		void close();
	}
}
