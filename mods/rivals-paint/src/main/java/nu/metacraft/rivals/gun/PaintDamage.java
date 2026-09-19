package nu.metacraft.rivals.gun;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every hit a paint weapon lands goes through here, and the reason is vanilla's damage cooldown.
 *
 * <p>{@code LivingEntity.hurtServer} keeps a twenty-tick window after a hit, and for the first ten
 * ticks of it a second hit is not a second hit: it applies only the <em>excess</em> over the last one
 * ({@code amount <= lastHurt} is refused outright, and anything above it lands as
 * {@code amount - lastHurt}). That rule is written for a sword. It is wrong for every weapon in this
 * module, and worst for the ones that fire more than one thing at once: the slosher throws two pellets
 * that arrive on the same tick, and the second was worth nothing, so a bucketful did the damage of one
 * pellet — which is exactly what it looked like. A shooter firing every three ticks lost two shots in
 * three the same way.
 *
 * <p>So the window is taken off before the hit and left off after it. {@code damageCooldownTime} is a
 * public field on {@link LivingEntity} in 26.3 (verified with {@code javap}); zeroing it takes the
 * other branch of {@code hurtServer}, which applies the whole amount and overwrites {@code lastHurt} —
 * so {@code lastHurt}, which is protected and not ours to touch, never gates anything. Zeroing it again
 * afterwards is what lets the second pellet of the same tick land its own full damage rather than the
 * difference. Splatcraft solves the same problem the same way, forcing the window to a tick in
 * {@code InkDamageUtils.doDamage}; this goes one further, because a tick is still longer than the gap
 * between two pellets of one slosh.
 *
 * <p>What is <em>not</em> changed: knockback. Vanilla's {@code dealDefaultKnockback} runs per hit, so a
 * two-pellet slosh pushes twice. That is a real difference from a single 14-damage hit, and it is left
 * alone for now — the shove reads as weight rather than as a bug.
 *
 * <p>The cost of leaving the window at zero is that the victim's <em>next</em> damage from anything at
 * all — a fall, a mob — also lands in full rather than being swallowed. In an arena where the only
 * other damage is the enemy-ink drip, which is deliberately never lethal, that is the right trade.
 */
public final class PaintDamage {
	private PaintDamage() {}

	/**
	 * Hurt {@code victim} for the full {@code amount}, whatever it was hit with a moment ago. Returns
	 * what {@code hurtServer} returned — whether the hit landed — so callers can hang the ink-on-screen
	 * splash and the kill credit off it. A non-living victim is never hurt and answers false.
	 */
	public static boolean hurt(ServerLevel level, Entity victim, DamageSource source, float amount) {
		if (!(victim instanceof LivingEntity living)) return false;
		living.damageCooldownTime = 0;
		boolean landed = living.hurtServer(level, source, amount);
		living.damageCooldownTime = 0;
		return landed;
	}
}
