package metacraft.kultur.drinks;

/**
 * The meter's arithmetic, ported from Brewery 0.17's {@code AlcoholManager} and its
 * {@code brewery_effects.json} with quality fixed at 10 (every Kultur drink's). Brewery's stages sit at
 * 50 to 140, past where Kultur's drinks take anyone, so here they come one step of ten after another,
 * all below the blackout. No Minecraft types, so it is unit-tested on its own.
 */
public final class Alcohol {
	/** Brewery drains {@code 0.0012 × quality} a tick; quality is 10. About 14.4 a minute. */
	public static final double DRAIN_PER_TICK = 0.012;
	/** A drink that leaves the meter here or above blacks the drinker out. */
	public static final double BLACKOUT_AT = 100;
	/** Where the meter stands after a blackout. */
	public static final double AFTER_BLACKOUT = 30;
	/** The step each threshold starts: 30 is step 2 (Inebriated II), 80 is step 7. */
	private static final double[] STEPS = {30, 40, 50, 60, 70, 80};

	private Alcohol() {}

	/** Brewery's {@code max(level + value, value)}: a drink never leaves the meter below its own strength. */
	public static double afterDrink(double alcohol, double value) {
		return Math.max(alcohol + value, value);
	}

	/** The meter after {@code ticks} of draining; never below zero. */
	public static double afterTicks(double alcohol, int ticks) {
		return Math.max(alcohol - DRAIN_PER_TICK * ticks, 0);
	}

	/** 0 when sober, 1 when tipsy, then one more for every threshold reached, up to 7 at 80. */
	public static int step(double alcohol) {
		if (alcohol <= 0) return 0;
		int step = 1;
		for (double threshold : STEPS) {
			if (alcohol >= threshold) step++;
		}
		return step;
	}

	public static boolean blacksOut(double alcoholAfterDrink) {
		return alcoholAfterDrink >= BLACKOUT_AT;
	}

	/** Ticks until the meter drains to zero: Inebriated's duration. */
	public static int ticksUntilSober(double alcohol) {
		if (alcohol <= 0) return 0;
		return (int) Math.ceil(alcohol / DRAIN_PER_TICK);
	}

	/**
	 * How hard one stagger pushes: Brewery's {@code min((0.3 + rand / 10) × ((level − 70) / 70) × 0.7, 0.2)},
	 * moved down to start at 50 (the step it belongs to) and reach Brewery's cap of 0.2 near 90.
	 */
	public static double staggerLength(double alcohol, double rand) {
		return Math.min((0.3 + rand / 10) * ((alcohol - 40) / 50) * 0.7, 0.2);
	}

	/** The amplifier of a drink's own speed or slowness: 0 below 70, 1 from there. */
	public static int drinkAmplifier(double alcohol) {
		return (int) Math.max(0, Math.min(1, Math.floor((alcohol - 30) / 40)));
	}

	/** Bread and milk lower the meter only while there is something to lower, and never below zero. */
	public static double sobered(double alcohol, double by) {
		if (alcohol <= 0) return alcohol;
		return Math.max(alcohol - by, 0);
	}
}
