package se.metacraft.fake_players;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Replays {@link Track} with Carpet's fake players, using nothing but commands: {@code /player <bot> spawn},
 * {@code /tp} and {@code /player <bot> kill}. The replay follows the wall clock, like real player clients do: a lagging
 * server does not slow the bots down, they just skip ahead to where they should be. Positions are not interpolated: a
 * bot is teleported to each ~5 second sample, which is good enough to load and unload chunks like real players do.
 */
public final class Playback {
	/** Prefix of the fake player names. Also lets the bots skip Carpet's Mojang name lookup. */
	public static final String NAME_PREFIX = "bot_fake_";
	private static Playback current;

	private final int offsetX;
	private final int offsetZ;
	/** The server's pause-when-empty-seconds before the replay, restored on stop. An idle server stops ticking. */
	private final int previousPause;
	private long startNanos = System.nanoTime();
	private int lastSecond = -1;
	private int loops;
	/** Segment id to the sample index its bot was last moved to. */
	private final Map<Integer, Integer> online = new HashMap<>();

	private Playback(MinecraftServer server, int offsetX, int offsetZ) {
		this.offsetX = offsetX;
		this.offsetZ = offsetZ;
		if (server instanceof DedicatedServer dedicated) {
			// With nobody online the server would pause itself and the replay would never start.
			previousPause = dedicated.pauseWhenEmptySeconds();
			dedicated.setPauseWhenEmptySeconds(-1);
		} else {
			previousPause = -1;
		}
	}

	public static boolean isRunning() {
		return current != null;
	}

	/** Whether Carpet's {@code /player} command is installed. */
	public static boolean hasCarpet(MinecraftServer server) {
		return server.getCommands().getDispatcher().getRoot().getChild("player") != null;
	}

	public static void start(MinecraftServer server, int offsetX, int offsetZ) {
		current = new Playback(server, offsetX, offsetZ);
	}

	public static void stop(MinecraftServer server) {
		if (current != null) {
			current.removeAll(server);
			if (server instanceof DedicatedServer dedicated) {
				dedicated.setPauseWhenEmptySeconds(current.previousPause);
			}
			current = null;
		}
	}

	public static String status() {
		if (current == null) {
			return "No replay is running.";
		}
		var track = Track.get();
		return String.format(
				"Replaying %d fake player sessions: %ds / %ds (loop %d), %d online, offset %d,%d.",
				track.segments.size(), current.elapsedSeconds(), track.length, current.loops + 1,
				current.online.size(), current.offsetX, current.offsetZ
		);
	}

	public static void tick(MinecraftServer server) {
		if (current != null) {
			current.doTick(server);
		}
	}

	private static String name(int id) {
		return NAME_PREFIX + id;
	}

	private static void run(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	private int elapsedSeconds() {
		return (int) ((System.nanoTime() - startNanos) / 1_000_000_000L);
	}

	private void doTick(MinecraftServer server) {
		int second = elapsedSeconds();
		if (second != lastSecond) {
			lastSecond = second;
			step(server, second);
		}
	}

	private void step(MinecraftServer server, int time) {
		var track = Track.get();
		if (time > track.length) {
			removeAll(server);
			loops++;
			startNanos = System.nanoTime();
			lastSecond = 0;
			time = 0;
		}
		for (var segment : track.segments) {
			int index = time > segment.endTime() ? -1 : segment.indexAt(time);
			var last = online.get(segment.id);
			if (index < 0) {
				if (last != null) {
					online.remove(segment.id);
					run(server, "player " + name(segment.id) + " kill");
				}
			} else if (last == null) {
				online.put(segment.id, index);
				run(server, String.format(
						Locale.ROOT, "player %s spawn at %d %d %d facing %.1f 0 in minecraft:overworld in creative",
						name(segment.id), segment.x(index) + offsetX, segment.y(index), segment.z(index) + offsetZ,
						segment.yaw(index)
				));
			} else if (last != index) {
				online.put(segment.id, index);
				run(server, String.format(
						Locale.ROOT, "tp %s %d %d %d %.1f 0",
						name(segment.id), segment.x(index) + offsetX, segment.y(index), segment.z(index) + offsetZ,
						segment.yaw(index)
				));
			}
		}
	}

	private void removeAll(MinecraftServer server) {
		for (var id : online.keySet()) {
			run(server, "player " + name(id) + " kill");
		}
		online.clear();
	}
}
