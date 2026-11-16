package se.metacraft.ovvar.content.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import se.metacraft.ovvar.PunkphozConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * When Punkphöz turns up: on top of vanilla's wanderers, every one of which still comes. Vanilla's spawner rolls
 * {@code nextInt(100) <= chance} once per attempt, then {@code spawn} passes only one in ten of those
 * ({@code nextInt(10) == 0}); he only ever gets the attempts whose roll failed, so vanilla's own visits and its
 * ramp are exactly as without him. His own {@code spawn} skips the one-in-ten gate.
 */
public final class PunkphozSpawning {
	// Copied from net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner (two of them are private there).
	private static final int MIN_SPAWN_CHANCE = 25;
	private static final int SPAWN_CHANCE_INCREASE = 25;
	private static final int MAX_SPAWN_CHANCE = 75;
	/** WanderingTraderSpawner.spawn returns false unless {@code random.nextInt(GATE) == 0}, after tick's roll passed. */
	private static final int GATE = 10;

	/** Non-null only while the spawner mixin runs vanilla's {@code spawn} for him, near that player. */
	public static final ThreadLocal<ServerPlayer> TARGET = new ThreadLocal<>();

	/**
	 * What one of vanilla's spawn attempts comes to. {@code VANILLA_ROLL}: vanilla's roll passed, so the attempt is
	 * vanilla's; its one-in-ten gate in {@code spawn} may still fail, and only a visit resets the ramp.
	 */
	public enum Outcome { VANILLA_ROLL, PUNKPHOZ, NONE }

	private PunkphozSpawning() {}

	/**
	 * Vanilla's long-run visits per attempt. The chance is advanced before the roll; an attempt at chance {@code s}
	 * is a visit with {@code p_s = q_s/10}, {@code q_s = (s+1)/100} (roll, then {@code spawn}'s gate), which resets
	 * the next chance to 25; otherwise the next chance is {@code min(s + 25, 75)}. With {@code E_75 = 1/p_75} and
	 * {@code E_s = 1 + (1 − p_s)·E_next(s)} the rate is {@code 1/E_25} (= 1520/21487 ≈ 0.0707405).
	 */
	public static double vanillaRate() {
		double expected = 0;
		for (int s = MAX_SPAWN_CHANCE; s >= MIN_SPAWN_CHANCE; s -= SPAWN_CHANCE_INCREASE) {
			double p = (s + 1) / 100.0 / GATE;
			expected = s == MAX_SPAWN_CHANCE ? 1 / p : 1 + (1 - p) * expected;
		}
		return 1 / expected;
	}

	/**
	 * His chance on each of vanilla's failed rolls, so that he makes {@code share} of all visits:
	 * {@code V/(1 − 10·V) · share/(1−share)} with {@code V} the vanilla rate ({@code 1 − 10·V} is the long-run
	 * chance that vanilla's roll fails; his spawn skips the gate), clamped to [0, 1]; 0 when he is off.
	 */
	public static double chanceOnAFailedRoll(PunkphozConfig config) {
		if (!config.enabled()) return 0;
		double rate = vanillaRate();
		double share = config.share();
		double chance = rate / (1 - GATE * rate) * share / (1 - share);
		return Math.clamp(chance, 0.0, 1.0);
	}

	/** One attempt: vanilla's own roll first, untouched; only a failed roll can be his. */
	public static Outcome decide(int chance, int roll, PunkphozConfig config, RandomSource random) {
		if (roll <= chance) return Outcome.VANILLA_ROLL;
		return random.nextDouble() < chanceOnAFailedRoll(config) ? Outcome.PUNKPHOZ : Outcome.NONE;
	}

	/** How many rolls a failed vanilla attempt gives him: one per {@code playersPerRoll} players, rounded up. */
	public static int rolls(int players, PunkphozConfig config) {
		if (!config.enabled() || players <= 0) return 0;
		return (players + config.playersPerRoll() - 1) / config.playersPerRoll();
	}

	/** The players he turns up near on a failed vanilla roll. */
	public static <P> List<P> targets(List<P> players, int loaded, PunkphozConfig config, RandomSource random) {
		return targets(players, loaded, config, chanceOnAFailedRoll(config), random);
	}

	/**
	 * Pure: shuffles a copy of {@code players}, takes the first {@link #rolls} (distinct by construction), rolls
	 * {@code chance} for each and returns the winners in order, at most {@code max(0, maxLoaded − loaded)}.
	 */
	public static <P> List<P> targets(List<P> players, int loaded, PunkphozConfig config, double chance, RandomSource random) {
		List<P> shuffled = new ArrayList<>(players);
		for (int i = shuffled.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			P swap = shuffled.get(i);
			shuffled.set(i, shuffled.get(j));
			shuffled.set(j, swap);
		}
		int room = Math.max(0, config.maxLoaded() - loaded);
		int rolls = rolls(shuffled.size(), config);
		List<P> winners = new ArrayList<>();
		for (int i = 0; i < rolls; i++) {
			if (random.nextDouble() < chance && winners.size() < room) winners.add(shuffled.get(i));
		}
		return winners;
	}
}
