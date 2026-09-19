package nu.metacraft.rivals.gun;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The gun's ink tank, kept in the stack's custom data so it survives Polymer's client mapping and
 * item moves. Absent means full. A refill is a deadline in server ticks; it completes on the first
 * tick at or past it.
 *
 * <p>The deadline is an <em>absolute</em> server tick, and the tick count starts again at 0 every
 * boot, so a gun saved mid-refill comes back with a deadline far in the future and would otherwise
 * never fire again. A deadline more than {@link #REFILL_TICKS} ahead of now cannot have been set
 * this session: it is stale, and counts as due rather than as a refill in progress.
 */
public final class Ink {
	/**
	 * The tank, in units that read as a percentage — which is what Splatoon's own numbers are, so every
	 * ink cost in {@link Weapon} is the game's figure without a scale factor in front of it. It was 40
	 * through round 6; a stack written then holds a number inside this one, and {@link #get} clamps
	 * anyway, so an old weapon comes back merely part-full rather than wrong.
	 */
	public static final int MAX = 100;
	public static final int REFILL_TICKS = 30;
	static final String INK = "rivals_ink";
	static final String REFILL_UNTIL = "rivals_refill_until";
	/** The tick the post-shot wait is over, for {@link #recovering}. */
	static final String RECOVER_AT = "rivals_recover_at";
	/**
	 * The longest post-shot wait any parameter may ask for, which is {@code refill_delay}'s and
	 * {@code special_refill_delay}'s own range. Nothing further ahead than this can have been written
	 * this session, so it is the staleness bound for {@link #RECOVER_AT} the way {@link #REFILL_TICKS}
	 * is for {@link #REFILL_UNTIL}.
	 */
	public static final int MAX_RECOVERY = 200;

	private Ink() {}

	/**
	 * Note that this weapon has just fired, and for how long that stops it drinking. Splatcraft calls the
	 * wait {@code ink_recovery_cooldown}: standing in your own ink does not top the tank up until the
	 * weapon has been quiet that long, so a weapon cannot be fired and refilled in the same breath —
	 * which is most of why holding down a shooter over your own paint is not free.
	 *
	 * <p>The <em>deadline</em> is stored rather than the shot's tick, because the wait is not a property
	 * of the weapon alone: a splat bomb thrown from a shooter costs the bomb's own
	 * {@code special_refill_delay}, not the shooter's. Whoever fires knows which; the tick loop only has
	 * to know whether the wait is over.
	 */
	public static void noteShot(ItemStack stack, long now, int delay) {
		if (delay <= 0) return;
		long until = now + Math.min(delay, MAX_RECOVERY);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(RECOVER_AT, until));
	}

	/**
	 * Is this weapon still inside its post-shot wait? An absolute server tick, and the tick count starts
	 * again at 0 every boot, so the same rule {@link #stale} uses applies: a deadline more than
	 * {@link #MAX_RECOVERY} ahead of now cannot have been written this session and counts as long past.
	 */
	public static boolean recovering(ItemStack stack, long now) {
		long until = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr(RECOVER_AT, Long.MIN_VALUE);
		if (until == Long.MIN_VALUE) return false;
		return now < until && until - now <= MAX_RECOVERY;
	}

	/** What the stack holds, clamped: the tank has changed size once and may again. */
	public static int get(ItemStack stack) {
		int stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(INK, MAX);
		return Math.clamp(stored, 0, MAX);
	}

	public static void set(ItemStack stack, int ink) {
		int clamped = Math.clamp(ink, 0, MAX);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(INK, clamped));
	}

	public static void add(ItemStack stack, int amount) {
		set(stack, get(stack) + amount);
	}

	public static boolean isRefilling(ItemStack stack, long now) {
		long until = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr(REFILL_UNTIL, -1L);
		return until >= 0 && now < until && !stale(until, now);
	}

	public static void startRefill(ItemStack stack, long now) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(REFILL_UNTIL, now + REFILL_TICKS));
	}

	/** A deadline that could not have been set this session: left over from before a restart. */
	private static boolean stale(long until, long now) {
		return until - now > REFILL_TICKS;
	}

	/** Complete a refill whose deadline has passed (or is stale): full tank, deadline cleared. */
	public static void finishIfDue(ItemStack stack, long now) {
		long until = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr(REFILL_UNTIL, -1L);
		if (until < 0) return;
		if (now < until && !stale(until, now)) return;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt(INK, MAX);
			tag.remove(REFILL_UNTIL);
		});
	}
}
