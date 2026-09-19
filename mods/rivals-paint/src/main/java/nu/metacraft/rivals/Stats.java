package nu.metacraft.rivals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import nu.metacraft.rivals.paint.PaintTally;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two per-player numbers MAIN's outro reads off the scoreboard: {@code splat.stats.blocks} and
 * {@code splat.stats.kills}, both plain dummy objectives, created here if MAIN's pack has not made them.
 *
 * <p><b>Blocks</b> is <em>held</em> paint, not paint thrown: how many faces of the final picture this
 * player was the last to paint. It is written once, at the whistle, off the ownership the painter records
 * per face ({@link PaintTally#countByPlayer}) — so a player whose whole strip was rolled over by the
 * other side ends the round with nothing, which is what "who painted this arena" means when the arena is
 * the score. Paint outside the arena bounds is never painted at all, so it never counts.
 *
 * <p><b>Kills</b> is live: a kill goes on the board the tick it happens, so the number is already there
 * however the round ends. Only a player killing another player during a live match counts; the enemy-ink
 * drip is never lethal ({@link PlayerTick}) and friendly fire is refused by the weapons, so what lands
 * here is exactly what it looks like. {@code DamageSource.getEntity()} is the shooter rather than the
 * ball, which is why a paint ball's kill goes on the shooter's name.
 *
 * <p>Both objectives are wiped at the start of a round — every holder, not only the ones playing, so that
 * last round's top five cannot haunt this one's — and every player on a side starts on a zero of their
 * own, so MAIN's sort sees the whole roster rather than only whoever scored.
 */
public final class Stats {
	public static final String BLOCKS_OBJECTIVE = "splat.stats.blocks";
	public static final String KILLS_OBJECTIVE = "splat.stats.kills";

	/** Kills this round, by player. In memory; the board is the copy MAIN reads. */
	private static final Map<UUID, Integer> KILLS = new HashMap<>();
	/**
	 * The scoreboard name of everybody who has been in this round, so a player who painted half the arena
	 * and then logged out still lands on the board at the whistle. A UUID is not a score holder; a name is.
	 */
	private static final Map<UUID, String> NAMES = new HashMap<>();

	private Stats() {}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer victim) || !Match.running()) return;
			if (!(source.getEntity() instanceof ServerPlayer killer) || killer == victim) return;
			credit(killer);
		});
	}

	// ---- kills

	/** One more kill for this player, on the board the same tick. Returns their new total. */
	public static int credit(ServerPlayer killer) {
		remember(killer);
		int total = KILLS.merge(killer.getUUID(), 1, Integer::sum);
		MinecraftServer server = killer.level().getServer();
		if (server != null) write(server, KILLS_OBJECTIVE, killer, total);
		return total;
	}

	public static int kills(UUID player) {
		return KILLS.getOrDefault(player, 0);
	}

	/**
	 * Which side killed more, or null when they are level — the tie-break {@link Match} reaches for when
	 * the paint itself is a draw. Only players still on a side are counted, because a side is a scoreboard
	 * team and somebody who left theirs is on neither.
	 */
	public static @Nullable PaintColor sideWithMostKills(List<ServerPlayer> players) {
		Map<PaintColor, Integer> perSide = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) perSide.put(color, 0);
		for (ServerPlayer player : players) {
			PaintColor.byTeam(player.getTeam())
					.ifPresent(color -> perSide.merge(color, kills(player.getUUID()), Integer::sum));
		}
		PaintColor best = null;
		int most = -1;
		boolean tied = false;
		for (PaintColor color : PaintColor.values()) {
			int kills = perSide.get(color);
			if (kills > most) {
				most = kills;
				best = color;
				tied = false;
			} else if (kills == most) {
				tied = true;
			}
		}
		return tied || most <= 0 ? null : best;
	}

	// ---- the round

	/**
	 * A clean board for a new round: both objectives emptied of every holder, then a zero for each player
	 * on a side. Also forgets this round's kills and remembers the roster's names.
	 */
	public static void startRound(MinecraftServer server, List<ServerPlayer> players) {
		KILLS.clear();
		NAMES.clear();
		clear(server, BLOCKS_OBJECTIVE);
		clear(server, KILLS_OBJECTIVE);
		for (ServerPlayer player : players) {
			if (PaintColor.byTeam(player.getTeam()).isEmpty()) continue;
			remember(player);
			write(server, BLOCKS_OBJECTIVE, player, 0);
			write(server, KILLS_OBJECTIVE, player, 0);
		}
	}

	/**
	 * The whistle: the final held-paint count per player onto the board. Kills are already there, one per
	 * kill. Returns how many players were given a blocks score.
	 */
	public static int publish(MinecraftServer server, ServerLevel arena) {
		Map<UUID, Integer> blocks = PaintTally.of(arena).countByPlayer(arena);
		int written = 0;
		for (Map.Entry<UUID, Integer> entry : blocks.entrySet()) {
			ScoreHolder holder = holder(server, entry.getKey());
			if (holder == null) continue;
			write(server, BLOCKS_OBJECTIVE, holder, entry.getValue());
			written++;
		}
		return written;
	}

	/** Everything this round held. The server stop's, and the tests'. */
	public static void clearAll() {
		KILLS.clear();
		NAMES.clear();
	}

	// ---- the board

	/** The objective by name, made as a dummy if MAIN's pack has not made it. */
	public static Objective objective(MinecraftServer server, String name) {
		Scoreboard scoreboard = server.getScoreboard();
		Objective existing = scoreboard.getObjective(name);
		if (existing != null) return existing;
		return scoreboard.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
				ObjectiveCriteria.RenderType.INTEGER, false, null);
	}

	public static void write(MinecraftServer server, String objective, ScoreHolder holder, int value) {
		server.getScoreboard().getOrCreatePlayerScore(holder, objective(server, objective)).set(value);
	}

	/** This holder's score, or 0 when they have none. What the tests read. */
	public static int read(MinecraftServer server, String objective, ScoreHolder holder) {
		ReadOnlyScoreInfo score = server.getScoreboard().getPlayerScoreInfo(holder, objective(server, objective));
		return score == null ? 0 : score.value();
	}

	/** Every holder's score in this objective removed, so a round starts on an empty board. */
	private static void clear(MinecraftServer server, String name) {
		Scoreboard scoreboard = server.getScoreboard();
		Objective objective = objective(server, name);
		for (PlayerScoreEntry entry : List.copyOf(scoreboard.listPlayerScores(objective))) {
			scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(entry.owner()), objective);
		}
	}

	/**
	 * Note this player's scoreboard name against their UUID. Every way into a match goes through
	 * {@link Match#join}, which calls this, because the painter credits a face to a UUID and a UUID is
	 * not a score holder: without a name here, a player who paints and then logs out before the whistle
	 * has nothing {@link #publish} can write their held paint to.
	 */
	public static void remember(ServerPlayer player) {
		NAMES.put(player.getUUID(), player.getScoreboardName());
	}

	/** Online, they are their own score holder; offline, the name this round remembered, or nothing. */
	private static @Nullable ScoreHolder holder(MinecraftServer server, UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) return online;
		String name = NAMES.get(player);
		return name == null ? null : ScoreHolder.forNameOnly(name);
	}
}
