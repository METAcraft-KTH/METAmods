package se.metacraft.fake_players;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Replays a {@link Scenario} with Carpet's fake players, using nothing but commands: {@code /player <bot> spawn},
 * {@code /tp} and {@code /player <bot> kill}. A replay follows the wall clock, like real player clients do: a lagging
 * server does not slow the bots down, they just skip ahead to where they should be. Positions are not interpolated: a
 * bot is teleported to each ~5 second sample, which is good enough to load and unload chunks like real players do.
 * Several replays (of the same or different scenarios) can run at once; each is a numbered run.
 */
public final class Playback {
	/** Prefix of the fake player names. Also lets the bots skip Carpet's Mojang name lookup. */
	public static final String NAME_PREFIX = "bot_fake_";
	private static final int MAX_RUNS = 99;

	private static final Map<Integer, Playback> RUNS = new TreeMap<>();
	/** The server's pause-when-empty-seconds before the first run. An idle server stops ticking. */
	private static int previousPause = -1;

	private final int id;
	private final Scenario scenario;
	private final int offsetX;
	private final int offsetZ;
	private long startNanos = System.nanoTime();
	private int lastSecond = -1;
	private int loops;
	/** Session id to the sample index its bot was last moved to. */
	private final Map<Integer, Integer> online = new HashMap<>();

	private Playback(int id, Scenario scenario, int offsetX, int offsetZ) {
		this.id = id;
		this.scenario = scenario;
		this.offsetX = offsetX;
		this.offsetZ = offsetZ;
	}

	/** Whether Carpet's {@code /player} command is installed. */
	public static boolean hasCarpet(MinecraftServer server) {
		return server.getCommands().getDispatcher().getRoot().getChild("player") != null;
	}

	/** Starts a run and returns its number. */
	public static int start(MinecraftServer server, Scenario scenario, int offsetX, int offsetZ) {
		int id = 1;
		while (RUNS.containsKey(id)) {
			id++;
		}
		if (id > MAX_RUNS) {
			throw new IllegalStateException("Too many replays running");
		}
		if (RUNS.isEmpty() && server instanceof DedicatedServer dedicated) {
			// With nobody online the server would pause itself and the replay would never start.
			previousPause = dedicated.pauseWhenEmptySeconds();
			dedicated.setPauseWhenEmptySeconds(-1);
		}
		RUNS.put(id, new Playback(id, scenario, offsetX, offsetZ));
		return id;
	}

	public static boolean stop(MinecraftServer server, int id) {
		var run = RUNS.remove(id);
		if (run == null) {
			return false;
		}
		run.removeAll(server);
		restorePause(server);
		return true;
	}

	/** Stops every run and returns how many there were. */
	public static int stopAll(MinecraftServer server) {
		int count = RUNS.size();
		RUNS.values().forEach(run -> run.removeAll(server));
		RUNS.clear();
		restorePause(server);
		return count;
	}

	private static void restorePause(MinecraftServer server) {
		if (RUNS.isEmpty() && server instanceof DedicatedServer dedicated) {
			dedicated.setPauseWhenEmptySeconds(previousPause);
		}
	}

	public static List<String> status() {
		var lines = new ArrayList<String>();
		for (var run : RUNS.values()) {
			lines.add(String.format(
					Locale.ROOT, "#%d %s: %ds / %ds (loop %d), %d of %d sessions online, offset %d,%d",
					run.id, run.scenario.name, run.elapsedSeconds(), run.scenario.length, run.loops + 1,
					run.online.size(), run.scenario.sessions.size(), run.offsetX, run.offsetZ
			));
		}
		return lines;
	}

	public static void tick(MinecraftServer server) {
		for (var run : RUNS.values()) {
			run.doTick(server);
		}
	}

	private String name(int session) {
		return NAME_PREFIX + id + "_" + session;
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
		if (time > scenario.length) {
			removeAll(server);
			loops++;
			startNanos = System.nanoTime();
			lastSecond = 0;
			time = 0;
		}
		for (var session : scenario.sessions) {
			int index = time > session.endTime() ? -1 : session.indexAt(time);
			var last = online.get(session.id());
			if (index < 0) {
				if (last != null) {
					online.remove(session.id());
					run(server, "player " + name(session.id()) + " kill");
				}
			} else if (last == null) {
				online.put(session.id(), index);
				var p = session.points().get(index);
				run(server, String.format(
						Locale.ROOT, "player %s spawn at %d %d %d facing %.1f 0 in %s in creative",
						name(session.id()), p.x() + offsetX, p.y(), p.z() + offsetZ, session.yaw(index), p.dimension()
				));
			} else if (last != index) {
				online.put(session.id(), index);
				var p = session.points().get(index);
				run(server, String.format(
						Locale.ROOT, "execute in %s run tp %s %d %d %d %.1f 0",
						p.dimension(), name(session.id()), p.x() + offsetX, p.y(), p.z() + offsetZ, session.yaw(index)
				));
			}
		}
	}

	private void removeAll(MinecraftServer server) {
		for (var session : online.keySet()) {
			run(server, "player " + name(session) + " kill");
		}
		online.clear();
	}
}
