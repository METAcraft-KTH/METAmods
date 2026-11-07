package se.metacraft.fake_players;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Replays {@link Track} with fake players. Only one replay can run at a time. */
public final class Playback {
	private static Playback current;

	private final int offsetX;
	private final int offsetZ;
	private final double speed;
	private long ticks;
	private int loops;
	private final Map<Integer, ServerPlayer> online = new HashMap<>();
	private final double[] sample = new double[4];

	private Playback(int offsetX, int offsetZ, double speed) {
		this.offsetX = offsetX;
		this.offsetZ = offsetZ;
		this.speed = speed;
	}

	public static boolean isRunning() {
		return current != null;
	}

	public static void start(int offsetX, int offsetZ, double speed) {
		current = new Playback(offsetX, offsetZ, speed);
	}

	public static void stop(MinecraftServer server) {
		if (current != null) {
			current.removeAll(server);
			current = null;
		}
	}

	public static String status() {
		if (current == null) {
			return "No replay is running.";
		}
		var track = Track.get();
		return String.format(
				"Replaying %d fake player sessions: %.0fs / %.0fs (loop %d), %d online, offset %d,%d, speed x%.2f.",
				track.segments.size(), current.time(), track.length, current.loops + 1,
				current.online.size(), current.offsetX, current.offsetZ, current.speed
		);
	}

	public static void tick(MinecraftServer server) {
		if (current != null) {
			current.doTick(server);
		}
	}

	private double time() {
		return ticks * speed / 20.0;
	}

	private void doTick(MinecraftServer server) {
		var track = Track.get();
		double time = time();
		if (time > track.length) {
			removeAll(server);
			loops++;
			ticks = 0;
			time = 0;
		}
		ticks++;
		for (var segment : track.segments) {
			var player = online.get(segment.id);
			if (time < segment.startTime() || time > segment.endTime()) {
				if (player != null) {
					online.remove(segment.id);
					server.getPlayerList().remove(player);
				}
				continue;
			}
			segment.sample(time, sample);
			if (player == null) {
				player = spawn(server, segment.id);
				online.put(segment.id, player);
			}
			move(player, segment);
		}
	}

	private void move(ServerPlayer player, Track.Segment segment) {
		float yaw = (float) sample[3];
		if (sample[3] == 0 && sample[0] == player.getX() && sample[2] == player.getZ()) {
			yaw = player.getYRot();
		}
		player.snapTo(sample[0] + offsetX, sample[1], sample[2] + offsetZ, yaw, 0);
		player.setYHeadRot(yaw);
		player.setOnGround(true);
		player.level().getChunkSource().move(player);
		// Fake players have no packet listener ticking them, so run the per-tick player logic here.
		player.doTick();
	}

	private ServerPlayer spawn(MinecraftServer server, int id) {
		String name = "bot_fake_" + id;
		UUID uuid = UUID.nameUUIDFromBytes(("metacraft-fake-players:" + id).getBytes(StandardCharsets.UTF_8));
		var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, name), false);
		var task = new PrepareSpawnTask(server, new NameAndId(uuid, name));
		task.start(p -> {});
		server.managedBlock(task::tick); // Wait for the spawn point to be found.
		var player = task.spawnPlayer(new FakeConnection(), cookie);
		player.setPermanentlyInvulnerable(true);
		return player;
	}

	private void removeAll(MinecraftServer server) {
		for (var player : online.values()) {
			server.getPlayerList().remove(player);
		}
		online.clear();
	}

	static Component describe() {
		return Component.literal(status());
	}
}
