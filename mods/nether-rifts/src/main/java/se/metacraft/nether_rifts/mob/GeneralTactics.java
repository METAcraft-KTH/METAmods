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
 * after his target ({@link PiglinGeneral}'s chase goal); in every other move his walking waits and he stands, but for
 * the spin, which drifts him toward his target by his velocity. From the
 * chase he starts, in this order:
 * <ol>
 * <li>a queued {@link Move#ROAR} ({@link #queueRoar}), even with no target: he stands and roars, and halfway through
 * his guard comes ({@link PiglinGeneral#callGuard});</li>
 * <li>the spin's {@link Move#WINDUP}, if his target is within {@code spin_range} and {@code spin_cooldown} has passed
 * since the last spin ended (or since he came), or only {@code spin_early_cooldown} if a projectile hit him in the last
 * {@code spin_early_window} ticks ({@link #noteProjectileHit}); the windup leads into the {@link Move#SPIN}, and the
 * spin into the {@link Move#DIZZY};</li>
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
	/** One turn of the spin ({@code spin}, 0.52 s): it hurts the players round him once a turn. */
	public static final int SPIN_LOOP_TICKS = 11;
	/** The spin's knockback strength, outward. */
	public static final double SPIN_KNOCKBACK = 1.0;
	/**
	 * What a mob walking at movement speed {@code s} covers a tick on ordinary ground is about this times {@code s²}
	 * (vanilla's walk: a zombie's 0.23 is 2.3 blocks a second); the spin drifts at what {@code spin_speed} would walk
	 * (0.33: 4.8 blocks a second, about 1.2 times his walk at 0.3).
	 */
	private static final double WALK_PACE = 2.2;
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
	/** This spin's length, in ticks (picked as it starts). */
	private int spinTicks;
	/** The game time the last spin ended (or he first ticked), and the last time a projectile hit him. */
	private long spinEnded = Long.MIN_VALUE;
	private long projectileHit = Long.MIN_VALUE;

	public GeneralTactics(PiglinGeneral general) {
		this.general = general;
	}

	public Move move() {
		return move;
	}

	public int moveTicks() {
		return moveTicks;
	}

	/** A projectile hit him at this game time: shooting him brings the spin on early. */
	public void noteProjectileHit(long gameTime) {
		projectileHit = gameTime;
	}

	/** The game time a projectile last hit him; {@link Long#MIN_VALUE} if none has. */
	public long lastProjectileHit() {
		return projectileHit;
	}

	/** A roar, to start when the move playing ends (or at once, chasing). Two queued play back to back. */
	public void queueRoar() {
		roarsQueued++;
	}

	/**
	 * Roars still to bring his guard: those queued, and one playing that hasn't reached {@link #ROAR_SPAWN_TICK}. Saved,
	 * as a loaded General starts over at the chase ({@link #setRoarsQueued}).
	 */
	public int roarsPending() {
		return roarsQueued + (move == Move.ROAR && moveTicks < ROAR_SPAWN_TICK ? 1 : 0);
	}

	/** These many roars queued, in place of any queued now (a loaded General's {@link #roarsPending}). */
	public void setRoarsQueued(int roars) {
		roarsQueued = Math.max(0, roars);
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
		switch (next) {
			case SWING -> general.play("attack");
			case WINDUP -> general.play("spin_windup");
			case SPIN -> {
				var spin = general.tuning().spin();
				spinTicks = spin.minTicks() + general.getRandom().nextInt(spin.maxTicks() - spin.minTicks() + 1);
				general.play("spin");
			}
			case DIZZY -> {
				general.setDeltaMovement(0, general.getDeltaMovement().y, 0);
				general.play("dizzy");
			}
			case ROAR -> {
				general.setDeltaMovement(0, general.getDeltaMovement().y, 0);
				general.play("roar");
				// loud and low: heard across the arena (16 blocks a unit of volume)
				general.playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 4f, 0.6f);
			}
			default -> {}
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
		if (spinEnded == Long.MIN_VALUE) {
			// he comes in with his spin cooling down
			spinEnded = level.getGameTime();
		}
		moveTicks++;
		switch (move) {
			case CHASE -> {}
			case SWING -> swing(level);
			case WINDUP -> windup(level);
			case SPIN -> spin(level);
			case DIZZY -> dizzy(level);
			case ROAR -> roar(level);
		}
		if (move == Move.CHASE) {
			chase(level);
		}
	}

	/** In the chase: a queued roar, the spin, or the swing, if one can start. */
	private void chase(ServerLevel level) {
		if (roarsQueued > 0) {
			roarsQueued--;
			start(Move.ROAR);
			return;
		}
		var target = target();
		if (target == null) {
			return;
		}
		if (spinDue(level, target)) {
			start(Move.WINDUP);
		} else if (swingCooldown <= 0 && canSwing(target)) {
			start(Move.SWING);
		}
	}

	/**
	 * Whether the spin starts now, at this target: within {@code spin_range}, and {@code spin_cooldown} since the last
	 * spin ended, or {@code spin_early_cooldown} with a projectile hit in the last {@code spin_early_window} ticks.
	 */
	private boolean spinDue(ServerLevel level, LivingEntity target) {
		var spin = general.tuning().spin();
		if (general.distanceTo(target) > spin.range()) {
			return false;
		}
		long now = level.getGameTime();
		long since = now - spinEnded;
		return since >= spin.cooldown()
				|| since >= spin.earlyCooldown() && projectileHit != Long.MIN_VALUE && now - projectileHit <= spin.earlyWindow();
	}

	/**
	 * The spin's tell ({@code spin_windup}, held): he stands facing his target while a wind rises, and at
	 * {@link #WINDUP_TICKS} he spins.
	 */
	private void windup(ServerLevel level) {
		var target = target();
		if (target != null) {
			facing = yawTo(target.position());
		}
		face(facing);
		if (moveTicks % 6 == 1) {
			float rising = 0.6f + 0.8f * moveTicks / WINDUP_TICKS;
			level.playSound(null, general.getX(), general.getY(), general.getZ(), SoundEvents.BREEZE_CHARGE, SoundSource.HOSTILE, 1.5f, rising);
		}
		if (moveTicks >= WINDUP_TICKS) {
			start(Move.SPIN);
		}
	}

	/**
	 * The spin ({@code spin}, looped) for its {@link #spinTicks}: he drifts toward his target, re-aiming each tick, and
	 * each turn ({@link #SPIN_LOOP_TICKS}, the first as it starts) hurts the players round him. Then he is dizzy, and
	 * the spin's cooldown begins. Projectiles turn back off him meanwhile ({@link PiglinGeneral#deflection}).
	 */
	private void spin(ServerLevel level) {
		var target = target();
		double pace = WALK_PACE * general.tuning().spin().speed() * general.tuning().spin().speed();
		var drift = Vec3.ZERO;
		if (target != null) {
			facing = yawTo(target.position());
			var across = target.position().subtract(general.position()).horizontal();
			if (across.lengthSqr() > 0.25) {
				drift = across.normalize().scale(pace);
			}
		}
		face(facing);
		// his walking is stopped in a move, so he goes by his velocity, which travel() moves him by after this
		general.setDeltaMovement(drift.x, general.getDeltaMovement().y, drift.z);
		if ((moveTicks - 1) % SPIN_LOOP_TICKS == 0) {
			spinHit(level);
		}
		if (moveTicks >= spinTicks) {
			spinEnded = level.getGameTime();
			start(Move.DIZZY);
		}
	}

	/**
	 * One turn of the spin: every player who can be hurt within {@code spin_radius} of his centre (across) takes
	 * {@code spin_damage}, as his attack, and is knocked outward; a ring of sweeps goes round him with a low whoosh.
	 */
	private void spinHit(ServerLevel level) {
		var spin = general.tuning().spin();
		var source = level.damageSources().mobAttack(general);
		for (var player : level.getEntitiesOfClass(Player.class, general.getBoundingBox().inflate(spin.radius(), 1, spin.radius()),
				p -> !p.isSpectator() && !p.isCreative() && withinAcross(p, spin.radius()))) {
			player.hurtServer(level, source, spin.damage());
			player.knockback(SPIN_KNOCKBACK, general.getX() - player.getX(), general.getZ() - player.getZ(), source, 0f);
			player.syncVelocity = true;
		}
		double y = general.getY() + general.getBbHeight() * 0.55;
		for (int i = 0; i < 8; i++) {
			var along = ahead(facing + i * 45f);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, general.getX() + along.x * 2, y, general.getZ() + along.z * 2, 1, 0, 0, 0, 0);
		}
		level.playSound(null, general.getX(), general.getY(), general.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, 0.6f);
	}

	/**
	 * Dizzy after the spin ({@code dizzy}, once) for {@code dizzy_ticks}: he stands as he is, not turning, with crits
	 * circling over his head, and takes {@code dizzy_damage_taken} times the damage ({@link PiglinGeneral#hurtServer}).
	 */
	private void dizzy(ServerLevel level) {
		face(facing);
		general.setDeltaMovement(0, general.getDeltaMovement().y, 0);
		if (moveTicks % 5 == 1) {
			double y = general.getY() + general.getBbHeight() + 0.3;
			for (int i = 0; i < 4; i++) {
				var along = ahead(moveTicks * 18f + i * 90f);
				level.sendParticles(ParticleTypes.CRIT, general.getX() + along.x * 0.7, y, general.getZ() + along.z * 0.7, 1, 0, 0, 0, 0);
			}
		}
		endAfter(general.tuning().dizzyTicks());
	}

	/**
	 * The roar ({@code roar}, once): he stands as he is, and at {@link #ROAR_SPAWN_TICK} calls his guard (nobody comes
	 * for a General of no rift); at {@link #ROAR_TICKS} he goes back to the chase. Dying, he gets no further
	 * ({@link #tick}), so a roar cut short calls nobody.
	 */
	private void roar(ServerLevel level) {
		face(facing);
		general.setDeltaMovement(0, general.getDeltaMovement().y, 0);
		if (moveTicks == ROAR_SPAWN_TICK) {
			general.callGuard(level);
		}
		endAfter(ROAR_TICKS);
	}

	/** Within this many blocks of his centre, across. */
	private boolean withinAcross(Player player, double radius) {
		double dx = player.getX() - general.getX();
		double dz = player.getZ() - general.getZ();
		return dx * dx + dz * dz <= radius * radius;
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
		return withinAcross(player, swing.reach())
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
