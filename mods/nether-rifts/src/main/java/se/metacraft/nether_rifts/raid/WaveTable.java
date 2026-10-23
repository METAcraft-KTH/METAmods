package se.metacraft.nether_rifts.raid;

import se.metacraft.nether_rifts.rift.Tier;

import java.util.EnumMap;
import java.util.function.IntUnaryOperator;

/**
 * How many of each slot a wave brings: vanilla 26.3's raid table (Raid$RaiderType) and bonus rolls
 * (Raid.getPotentialBonusSpawns) with the tier standing in for difficulty and no bonus wave, plus our
 * troopers-per-player scaling, one leader from wave 2 and the captain in a large raid's last wave.
 */
public final class WaveTable {

	/** Index = wave number (index 0 unused), as in vanilla. */
	private static final int[] RANGED = {0, 4, 3, 3, 4, 4, 4, 2};
	private static final int[] MELEE = {0, 0, 2, 0, 1, 4, 2, 5};
	private static final int[] HEAVY = {0, 0, 0, 1, 0, 1, 0, 2};
	private static final int[] SUPPORT = {0, 0, 0, 0, 3, 0, 0, 1};
	private static final int[] ELITE = {0, 0, 0, 0, 0, 1, 1, 2};

	private WaveTable() {}

	public static EnumMap<Slot, Integer> counts(Tier tier, int wave, int participants, IntUnaryOperator nextInt, double perPlayer, double cap) {
		var counts = new EnumMap<Slot, Integer>(Slot.class);
		double scale = Math.min(cap, 1.0 + perPlayer * Math.max(0, participants - 1));
		counts.put(Slot.RANGED, scaled(RANGED[wave] + trooperBonus(tier, nextInt), scale));
		counts.put(Slot.MELEE, scaled(MELEE[wave] + trooperBonus(tier, nextInt), scale));
		counts.put(Slot.HEAVY, HEAVY[wave]);
		counts.put(Slot.SUPPORT, SUPPORT[wave] + supportBonus(tier, wave, nextInt));
		counts.put(Slot.ELITE, ELITE[wave]);
		counts.put(Slot.LEADER, wave >= 2 ? 1 : 0);
		counts.put(Slot.CAPTAIN, tier == Tier.LARGE && wave == tier.waves() ? 1 : 0);
		return counts;
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
