package se.metacraft.nether_rifts.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * The Piglin General's fight, one move at a time: he never starts a move during another. In {@link Move#CHASE} he walks
 * after his target ({@link PiglinGeneral}'s chase goal); in every other move he stands, and his walking waits. From the
 * chase he starts, in this order:
 * <ol>
 * <li>a queued {@link Move#ROAR} ({@link #queueRoar}), even with no target;</li>
 * <li>the spin, when it is due ({@link #spinDue});</li>
 * <li>the {@link Move#SWING}, if his target is within {@code swing_start_range} and in front of him (within
 * {@link #SWING_START_ANGLE}°), and {@code swing_cooldown} has passed since the last swing ended.</li>
 * </ol>
 * A move that ends goes back to the chase, which chooses again the same tick, so a queued roar follows straight on. A
 * General dying or dead does nothing more ({@link #stop}). Numbers are the config's {@code mobs.general}.
 */
public final class GeneralTactics {

	public enum Move { CHASE, SWING, WINDUP, SPIN, DIZZY, ROAR }

	/** The swing ({@code attack}, 1.72 s): he turns with his target through the twist back, and the hit lands at the hit tick. */
	public static final int SWING_TICKS = 35;
	public static final int SWING_TURN_TICKS = 13;
	public static final int SWING_HIT_TICK = 18;
	/** He swings at a target this far either side of where he looks, in degrees. */
	public static final float SWING_START_ANGLE = 60;
	/** The swing's knockback strength (a player's sprint hit is 1, a mace's smash about 1.5 to 2). */
	public static final double SWING_KNOCKBACK = 1.5;
	/** The spin's windup ({@code spin_windup}, 1.16 s). */
	public static final int WINDUP_TICKS = 23;
	/** The roar ({@code roar}, 2 s), and the tick his reinforcements come at. */
	public static final int ROAR_TICKS = 40;
	public static final int ROAR_SPAWN_TICK = 20;

	private final PiglinGeneral general;
	private Move move = Move.CHASE;
	/** Ticks into the move (its start is 0, its first tick 1). */
	private int moveTicks = 0;
	/** Ticks till the next swing may start. */
	private int swingCooldown = 0;
	/** Roars to play, one after another, as soon as the chase can start one. */
	private int roarsQueued = 0;
	/** The way he faces through a move (yaw, degrees). */
	private float facing;

	public GeneralTactics(PiglinGeneral general) {
		this.general = general;
	}

	public Move move() {
		return move;
	}

	public int moveTicks() {
		return moveTicks;
	}

	/** A roar, to start when the move playing ends (or at once, chasing). Two queued play back to back. */
	public void queueRoar() {
		roarsQueued++;
	}

	/**
	 * Starts this move now, over the one playing (the tactics' own choice, and tests'). The chase ends any move
	 * animation still playing; every other move stops his walking.
	 */
	public void start(Move next) {
		move = next;
		moveTicks = 0;
		facing = general.getYHeadRot();
		if (next == Move.CHASE) {
			general.stopMove();
			return;
		}
		general.getNavigation().stop();
		if (next == Move.SWING) {
			general.play("attack");
		}
	}

	/** The move stops where it is (he died): back to the chase, with no animation of its own. */
	public void stop() {
		move = Move.CHASE;
		moveTicks = 0;
	}

	public void tick(ServerLevel level) {
		if (general.isDeadOrDying()) {
			stop();
			return;
		}
		if (swingCooldown > 0) {
			swingCooldown--;
		}
		moveTicks++;
		switch (move) {
			case CHASE -> {}
			case SWING -> swing(level);
			case WINDUP -> endAfter(WINDUP_TICKS);
			case SPIN -> endAfter(general.tuning().spin().maxTicks());
			case DIZZY -> endAfter(general.tuning().dizzyTicks());
			case ROAR -> endAfter(ROAR_TICKS);
		}
		if (move == Move.CHASE) {
			chase();
		}
	}

	/** In the chase: a queued roar, the spin, or the swing, if one can start. */
	private void chase() {
		if (roarsQueued > 0) {
			roarsQueued--;
			start(Move.ROAR);
			return;
		}
		var target = target();
		if (target == null) {
			return;
		}
		if (spinDue(target)) {
			start(Move.WINDUP);
		} else if (swingCooldown <= 0 && canSwing(target)) {
			start(Move.SWING);
		}
	}

	/** Whether the spin starts now, at this target. Not yet: the General only swings. */
	private boolean spinDue(LivingEntity target) {
		return false;
	}

	/** Within {@code swing_start_range} and within {@link #SWING_START_ANGLE}° of where he looks. */
	private boolean canSwing(LivingEntity target) {
		return general.distanceTo(target) <= swing().startRange()
				&& Math.abs(Mth.degreesDifference(general.getYHeadRot(), yawTo(target.position()))) <= SWING_START_ANGLE;
	}

	/**
	 * The swing: he faces his target till the twist back is over ({@link #SWING_TURN_TICKS}) and holds that way; the
	 * hit lands at {@link #SWING_HIT_TICK}; at {@link #SWING_TICKS} he goes back to the chase, the cooldown begun.
	 */
	private void swing(ServerLevel level) {
		var target = target();
		if (moveTicks <= SWING_TURN_TICKS && target != null) {
			facing = yawTo(target.position());
		}
		face(facing);
		if (moveTicks == SWING_HIT_TICK) {
			swingHit(level);
		}
		if (moveTicks >= SWING_TICKS) {
			swingCooldown = swing().cooldown();
			start(Move.CHASE);
		}
	}

	/**
	 * Every player who can be hurt (not creative or spectator) in the arc in front of him takes the swing's damage, as
	 * his attack, and is knocked back away from him; a sweep shows along the arc and the mace lands heavily.
	 */
	private void swingHit(ServerLevel level) {
		var swing = swing();
		var source = level.damageSources().mobAttack(general);
		for (var player : level.getEntitiesOfClass(Player.class, general.getBoundingBox().inflate(swing.reach(), 1, swing.reach()),
				p -> !p.isSpectator() && !p.isCreative() && inArc(p, swing))) {
			player.hurtServer(level, source, swing.damage());
			// knockback() pushes away from the (x, z) it is given; a player's own client only hears of it with syncVelocity
			player.knockback(SWING_KNOCKBACK, general.getX() - player.getX(), general.getZ() - player.getZ(), source, 0f);
			player.syncVelocity = true;
		}
		double y = general.getY() + general.getBbHeight() * 0.4;
		double r = swing.reach() * 0.6;
		for (int i = -1; i <= 1; i++) {
			var along = ahead(facing + i * (float) swing.arc() / 3);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, general.getX() + along.x * r, y, general.getZ() + along.z * r, 1, 0, 0, 0, 0);
		}
		level.playSound(null, general.getX(), general.getY(), general.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.5f, 0.8f);
	}

	/** Within {@code swing_reach} of his centre (across), and within half {@code swing_arc} of the way he faces. */
	private boolean inArc(Player player, NetherRiftsConfig.GeneralTuning.Swing swing) {
		double dx = player.getX() - general.getX();
		double dz = player.getZ() - general.getZ();
		return dx * dx + dz * dz <= swing.reach() * swing.reach()
				&& Math.abs(Mth.degreesDifference(facing, yawTo(player.position()))) <= swing.arc() / 2;
	}

	/** Ends this move after so many ticks. */
	private void endAfter(int ticks) {
		if (moveTicks >= ticks) {
			start(Move.CHASE);
		}
	}

	/** He turns this way, head and body, at once. */
	private void face(float yaw) {
		general.setYRot(yaw);
		general.setYBodyRot(yaw);
		general.setYHeadRot(yaw);
	}

	/** The yaw from him to this point (0 is south, as Minecraft's). */
	private float yawTo(Vec3 point) {
		return (float) (Mth.atan2(point.z - general.getZ(), point.x - general.getX()) * Mth.RAD_TO_DEG) - 90f;
	}

	/** The way ahead, across, for this yaw. */
	private static Vec3 ahead(float yaw) {
		float radians = yaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(radians), 0, Mth.cos(radians));
	}

	@Nullable
	private LivingEntity target() {
		var target = general.getTarget();
		return target != null && target.isAlive() ? target : null;
	}

	private NetherRiftsConfig.GeneralTuning.Swing swing() {
		return general.tuning().swing();
	}
}
