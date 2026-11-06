package se.metacraft.skins.ui;

import se.metacraft.skins.BoxSummary;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The Roll menu's reel, as numbers: when each of its steps happens, how high each click is, and the
 * tape of skins it slides along. The reel is the menu's middle row, nine slots; each step slides every
 * skin one slot to the right and a new one in at the left.
 */
public final class Reel {
	private Reel() {}

	/** How many steps a spin takes, and over how many ticks (5 s). */
	public static final int STEPS = 40, TICKS = 100;
	/** How many skins the reel shows, and which of them is the centre. */
	public static final int WIDTH = 9, CENTRE = 4;

	/**
	 * The tick after the press at which each step happens, steps 1 to {@link #STEPS}: a quadratic
	 * ease-out, {@code t(k) = max(t(k-1) + 1, round(TICKS × (1 - √(1 - k / STEPS))))}, about one step
	 * a tick at first and a sixth of the spin for the last. The last is at {@link #TICKS}.
	 */
	public static int[] schedule() {
		int[] ticks = new int[STEPS];
		int previous = 0;
		for (int k = 1; k <= STEPS; k++) {
			previous = Math.max(previous + 1, (int) Math.round(TICKS * (1 - Math.sqrt(1 - (double) k / STEPS))));
			ticks[k - 1] = previous;
		}
		return ticks;
	}

	/** The click's pitch after {@code steps} steps: rising in semitones from 0.5 (none) to 2.0 (all of them). */
	public static float pitch(int steps) {
		return (float) Math.pow(2, (Math.round(steps * 24.0 / STEPS) - 12) / 12.0);
	}

	/**
	 * The skins the reel slides along: {@link #STEPS} fresh draws from {@code entries} with the
	 * {@code result} at index {@link #CENTRE}, then the {@code preview} the reel showed before the press.
	 * After {@code s} steps, reel column {@code i} shows {@code tape[i - s + STEPS]}: the preview
	 * unchanged at first, and the result in the centre after the last step.
	 */
	public static List<String> tape(String result, List<String> preview, List<BoxSummary.Entry> entries, RandomSource random) {
		List<String> tape = new ArrayList<>();
		for (int i = 0; i < STEPS; i++) tape.add(BoxSummary.draw(entries, random));
		tape.set(CENTRE, result);
		tape.addAll(preview);
		return tape;
	}
}
