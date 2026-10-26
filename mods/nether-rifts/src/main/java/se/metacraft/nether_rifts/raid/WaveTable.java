package se.metacraft.nether_rifts.raid;

import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.rift.Tier;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * How many of each slot a wave brings: the config's base counts ({@code waves}; the defaults are vanilla 26.3's raid
 * table, Raid$RaiderType) plus vanilla's bonus rolls (Raid.getPotentialBonusSpawns) with the tier standing in for
 * difficulty and no bonus wave, and our scaling with the players taking part: troopers from the second player on,
 * heavy, support and elite mobs past {@code elite_scaling_from}.
 */
public final class WaveTable {

	private WaveTable() {}

	/** A wave of the tier's list, or nothing for one past its end (a reload may have shortened it under a running raid). */
	public static EnumMap<Slot, Integer> counts(NetherRiftsConfig config, Tier tier, int wave, int participants, IntUnaryOperator nextInt, double perPlayer, double cap) {
		var waves = config.waves().of(tier);
		if (wave < 1 || wave > waves.size()) {
			return none();
		}
		double elite = scale(participants, config.eliteScalingFrom(), config.eliteScalingPerPlayer(), config.eliteScalingCap());
		return counts(waves.get(wave - 1), tier, wave, nextInt, scale(participants, 1, perPlayer, cap), elite);
	}

	/** The base counts with the bonus rolls, troopers times {@code trooperScale} and heavy, support and elite times {@code eliteScale}. */
	public static EnumMap<Slot, Integer> counts(Map<Slot, Integer> base, Tier tier, int wave, IntUnaryOperator nextInt, double trooperScale, double eliteScale) {
		var counts = none();
		base.forEach(counts::put);
		counts.merge(Slot.RANGED, trooperBonus(tier, nextInt), Integer::sum);
		counts.merge(Slot.MELEE, trooperBonus(tier, nextInt), Integer::sum);
		counts.merge(Slot.SUPPORT, supportBonus(tier, wave, nextInt), Integer::sum);
		// after the bonus rolls, so a support bonus grows with the rest
		for (var slot : Slot.values()) {
			if (slot.trooper()) {
				counts.compute(slot, (k, count) -> scaled(count, trooperScale));
			} else if (slot.scalesPastThreshold()) {
				counts.compute(slot, (k, count) -> scaled(count, eliteScale));
			}
		}
		return counts;
	}

	private static EnumMap<Slot, Integer> none() {
		var counts = new EnumMap<Slot, Integer>(Slot.class);
		for (var slot : Slot.values()) {
			counts.put(slot, 0);
		}
		return counts;
	}

	/** One, plus {@code perPlayer} for each player past {@code from}, at most {@code cap}. */
	private static double scale(int participants, int from, double perPlayer, double cap) {
		return Math.min(cap, 1.0 + perPlayer * Math.max(0, participants - from));
	}

	private static int scaled(int count, double scale) {
		return (int) Math.floor(count * scale + 0.5);
	}

	/** Pillagers and vindicators: easy k = nextInt(2), normal 1, hard 2; then nextInt(k + 1). */
	private static int trooperBonus(Tier tier, IntUnaryOperator nextInt) {
		int k = switch (tier) {
			case SMALL -> nextInt.applyAsInt(2);
			case MEDIUM -> 1;
			case LARGE -> 2;
		};
		return k > 0 ? nextInt.applyAsInt(k + 1) : 0;
	}

	/** Witches: none on easy, in waves 1-2 or in wave 4; otherwise nextInt(2). */
	private static int supportBonus(Tier tier, int wave, IntUnaryOperator nextInt) {
		if (tier == Tier.SMALL || wave <= 2 || wave == 4) {
			return 0;
		}
		return nextInt.applyAsInt(2);
	}
}
