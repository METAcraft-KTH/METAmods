package nu.metacraft.rivals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The seam between this mod and the MAIN datapack, which owns the minigame the way it owns every other
 * one on that server: MAIN decides when Paint Splat Town is being played, and this mod does the playing.
 *
 * <p><b>The running flag.</b> MAIN keeps a fake player {@code ?running} in the objective
 * {@code splat.state}: 0 while the minigame is not active, 1 while it is (which is MAIN's own
 * {@code ?superstate main.state} being 3). Nothing here writes it — it is read, every
 * {@link #POLL_TICKS} ticks, and only the <em>edges</em> do anything:
 *
 * <ul>
 *   <li>0 → 1 starts a match, {@link #minutes} long, in {@link #arenaLevel the arena level}, forced
 *       (MAIN decides who is playing; a player on neither side is simply left out rather than the whole
 *       round refused);</li>
 *   <li>1 → 0 stops whatever is running, quietly — see below.</li>
 * </ul>
 *
 * <p>Edges, not levels, so that the ten seconds of fireworks after the whistle are not read as "the flag
 * still says 1, start another round". The first poll of a server's life only reads: a restart with the
 * flag already up neither starts nor stops anything, because the mod has no idea how much of that round
 * has been played.
 *
 * <p><b>Ending the game.</b> MAIN never ends this minigame on its own, so the arena says how: a win
 * function per side and a draw function ({@link Arena#getWinFunction}, set with {@code /rivals
 * win-function set <side> <function>} and {@code /rivals draw-function set <function>} — on the event
 * server, {@code main:api/end_game_data} and {@code main:api/end_game_it}), which {@link Match} runs when
 * the celebration is over and the lobby begins. That happens whether the clock ran out or an operator
 * stopped it; the one ending that runs nothing is the flag dropping ({@link Match#stopQuietly}), because
 * then MAIN is already ending it, and a second ending would be MAIN answering itself.
 *
 * <p>The per-player numbers the outro reads are {@link Stats}'s business.
 */
public final class MainPack {
	/** MAIN's objective and the fake player in it that says whether the minigame is on. */
	public static final String STATE_OBJECTIVE = "splat.state";
	public static final String RUNNING_HOLDER = "?running";

	/** How often the flag is read. Half a second: MAIN drives it from its own second-by-second loop. */
	public static final int POLL_TICKS = 10;
	/** How long a round the flag starts runs, unless {@code config/rivals-paint/main.json} says otherwise. */
	public static final int DEFAULT_MINUTES = 3;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String HELP = "How this mod answers the MAIN datapack. \"minutes\" is how long a "
			+ "round started by MAIN's running flag (the fake player ?running in the objective splat.state) "
			+ "lasts; when it ends, the arena's win function for the winning side runs (/rivals win-function "
			+ "set <side> <function>, /rivals draw-function set <function>). The arena is whichever level has "
			+ "a spawn set for both sides — /rivals spawn set <side>. Run /rivals reload after editing this file.";

	private static int minutes = DEFAULT_MINUTES;
	/** The flag as it was last read. -1 before the first read of a server's life; see the class note. */
	private static int lastFlag = -1;

	private MainPack() {}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			reload();
			lastFlag = -1;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % POLL_TICKS == 0) poll(server, server.getTickCount());
		});
	}

	// ---- the flag

	/**
	 * {@code ?running} in {@code splat.state}, or 0 when MAIN has made neither. An objective this mod does
	 * not own is never created here: an absent flag means the minigame is not being run by anybody, which
	 * is exactly 0.
	 */
	public static int flag(MinecraftServer server) {
		Objective objective = server.getScoreboard().getObjective(STATE_OBJECTIVE);
		if (objective == null) return 0;
		ReadOnlyScoreInfo score = server.getScoreboard()
				.getPlayerScoreInfo(ScoreHolder.forNameOnly(RUNNING_HOLDER), objective);
		return score == null ? 0 : score.value();
	}

	/** What a read of the flag meant. */
	public enum Edge { NONE, START, STOP }

	/**
	 * Read the flag and say what it means, remembering it for next time. Anything that is not 0 counts as
	 * 1, because MAIN's contract is "not active / active" and a 2 would be a typo, not a third state.
	 *
	 * <p>Split from {@link #poll} so the deciding can be tested without the doing: starting a round is the
	 * one thing in this module that moves every player in it, and a game test that drove a real round for
	 * the sake of an edge would be fighting the rest of the batch for the {@link Match} singleton.
	 */
	public static Edge edge(MinecraftServer server) {
		int flag = flag(server) == 0 ? 0 : 1;
		int was = lastFlag;
		if (flag == was) return Edge.NONE;
		lastFlag = flag;
		if (was < 0) return Edge.NONE; // the first read only reads
		return flag == 1 ? Edge.START : Edge.STOP;
	}

	/**
	 * Read the flag and act on the edge. The tick is handed in for the same reason {@link Match#tick}
	 * takes one.
	 */
	public static void poll(MinecraftServer server, long now) {
		switch (edge(server)) {
			case START -> start(server, now);
			case STOP -> stop(server, now);
			case NONE -> {}
		}
	}

	/** What the flag last read as: 0, 1, or -1 for "not read yet". For the tests. */
	public static int lastFlag() {
		return lastFlag;
	}

	/** Forget the flag, so the next poll is a first read again. Server start, and the tests. */
	public static void forget() {
		lastFlag = -1;
	}

	private static void start(MinecraftServer server, long now) {
		if (Match.running()) return;
		Optional<ServerLevel> level = arenaLevel(server);
		if (level.isEmpty()) {
			Rivals.LOGGER.warn("[{}] {} says the minigame is on, but no level has a spawn for both sides "
					+ "— set them with /rivals spawn set <{}>", Rivals.MOD_ID, RUNNING_HOLDER, PaintColor.idList());
			return;
		}
		Match.Result result = Match.start(server, level.get(),
				() -> server.getPlayerList().getPlayers(), minutes, true, now);
		Rivals.LOGGER.info("[{}] {} → 1: {}", Rivals.MOD_ID, RUNNING_HOLDER, result.message().getString());
	}

	/** Quietly: MAIN dropped the flag, so MAIN is already ending the game and needs no function to say so. */
	private static void stop(MinecraftServer server, long now) {
		if (Match.stopQuietly(now, server)) {
			Rivals.LOGGER.info("[{}] {} → 0: the match was stopped", Rivals.MOD_ID, RUNNING_HOLDER);
		}
	}

	/**
	 * Where a round the flag starts is played: the first level with a spawn set for both sides. Per level
	 * because an {@link Arena} is, and the overworld comes first in the server's own order, so a server
	 * with one arena never has to say which it means.
	 */
	public static Optional<ServerLevel> arenaLevel(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (Arena.of(level).spawnsReady()) return Optional.of(level);
		}
		return Optional.empty();
	}

	// ---- the config

	public static int minutes() {
		return minutes;
	}

	/** For the tests, which must not write a file the whole server reads. */
	public static void setMinutes(int value) {
		minutes = Math.max(Match.MIN_MINUTES, Math.min(Match.MAX_MINUTES, value));
	}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(Rivals.MOD_ID).resolve("main.json");
	}

	/** Re-read {@code config/rivals-paint/main.json}. Returns the round length it now holds. */
	public static int reload() {
		return load(configPath());
	}

	/** The same from a given file, written with its {@code _help} the first time there is none. */
	public static int load(Path path) {
		minutes = DEFAULT_MINUTES;
		if (!Files.isRegularFile(path)) {
			write(path);
			return minutes;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				Rivals.LOGGER.warn("[{}] {}: not a json object, {} minutes stands", Rivals.MOD_ID, path, minutes);
				return minutes;
			}
			JsonElement value = root.getAsJsonObject().get("minutes");
			if (value == null || !value.isJsonPrimitive()) return minutes;
			int wanted = value.getAsInt();
			if (wanted < Match.MIN_MINUTES || wanted > Match.MAX_MINUTES) {
				Rivals.LOGGER.warn("[{}] {}: a round is {} to {} minutes, not {} — {} stands", Rivals.MOD_ID, path,
						Match.MIN_MINUTES, Match.MAX_MINUTES, wanted, minutes);
				return minutes;
			}
			minutes = wanted;
		} catch (IOException | RuntimeException e) {
			Rivals.LOGGER.warn("[{}] {}: {}; {} minutes stands", Rivals.MOD_ID, path, e, minutes);
			minutes = DEFAULT_MINUTES;
		}
		return minutes;
	}

	private static void write(Path path) {
		JsonObject root = new JsonObject();
		root.addProperty("_help", HELP);
		root.addProperty("minutes", minutes);
		try {
			Path parent = path.getParent();
			if (parent != null) Files.createDirectories(parent);
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			Rivals.LOGGER.warn("[{}] could not write {}: {}", Rivals.MOD_ID, path, e.toString());
		}
	}
}
