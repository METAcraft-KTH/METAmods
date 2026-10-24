package se.metacraft.nether_rifts.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * A Chained Ghast's attacks, one at a time, so a player can read and answer each. In a fight it goes round:
 * <ol>
 * <li>{@link Phase#CRUISE}: it floats about; only its crossbow piglin's bolts fly.</li>
 * <li>{@link Phase#TELEGRAPH}: a bombing run is coming: the ghast wails, its drummer war-cries, it pitches into the dive.</li>
 * <li>{@link Phase#BOMBING_RUN}: down to the target and across it ({@link BombingRunGoal}), the Grenadier dropping at
 * most {@code max_bombs_per_run} bombs; no fireballs, no bolts. Low over the target is the ghast's weak window.</li>
 * <li>{@link Phase#CLIMB}, then {@link Phase#VOLLEY}: {@code volley_size} fireballs, each charged (the spear piglin
 * stabs, the shooting face shows, the ghast's warning is heard) and shot as vanilla's ghast shoots.</li>
 * <li>{@link Phase#REGROUP}: no attacks for {@code regroup_ticks}; then it cruises, and {@code global_cooldown} later may
 * start the next.</li>
 * </ol>
 * From cruising it starts a run if one is due (its {@code cooldown} since the last ended), the Grenadier is on the deck
 * and there is room over the target for the ghast and its deck at the dive's height (open sky, or a roof high enough);
 * else a volley if one is due ({@code volley_cooldown}), the spear piglin is on the deck and the ghast can see the target;
 * else it cruises on. A run that can't go on (its target gone, the Grenadier killed) or a phase that runs past
 * {@code phase_timeout} goes to regrouping. A ghast without AI, or a showcase one, keeps cruising. Numbers are the
 * config's {@code mobs.bombardier.tactics}.
 */
public final class GhastTactics {

	public enum Phase { CRUISE, TELEGRAPH, BOMBING_RUN, CLIMB, VOLLEY, REGROUP }

	public enum Ability { BOLT, BOMB, FIREBALL }

	/** A fireball's charge, from the warning till it flies: vanilla's ghast's. */
	public static final int CHARGE_TICKS = 10;
	/** It goes after a target this close, for a run (in blocks), and shoots at one this close (vanilla's 64). */
	public static final double RUN_REACH = 48;
	public static final double VOLLEY_REACH = 64;

	/** Hear every change of phase (tests); none in play. */
	public static final java.util.List<PhaseListener> PHASE_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	@FunctionalInterface
	public interface PhaseListener {
		void changed(WarGhast ghast, Phase from, Phase to, long gameTime);
	}

	private final WarGhast ghast;
	private Phase phase = Phase.CRUISE;
	/** Game times: when the phase began, and when each ability may next start. */
	private long since = 0;
	private long nextAbility = 0;
	private long nextRun = 0;
	private long nextVolley = 0;
	private int bombs = 0;

	GhastTactics(WarGhast ghast) {
		this.ghast = ghast;
	}

	public Phase phase() {
		return phase;
	}

	/** Bombs dropped on this run. */
	public int bombs() {
		return bombs;
	}

	/** Whether an attack may be made now: bolts while cruising or regrouping, bombs on a run (up to its most), fireballs in a volley. */
	public boolean allows(Ability ability) {
		return switch (ability) {
			case BOLT -> tuning().boltsInCruiseOnly() ? phase == Phase.CRUISE || phase == Phase.REGROUP
					: phase != Phase.BOMBING_RUN && phase != Phase.VOLLEY;
			case BOMB -> phase == Phase.BOMBING_RUN && bombs < tuning().maxBombsPerRun();
			case FIREBALL -> phase == Phase.VOLLEY;
		};
	}

	/** A bomb went down on this run. */
	void bombDropped() {
		bombs++;
	}

	/** The run is over (its pass done, or given up at the dive): it climbs, and the next run is due a cooldown from now. */
	void climb(ServerLevel level) {
		if (phase == Phase.BOMBING_RUN) {
			nextRun = level.getGameTime() + bombardier().cooldown();
			enter(level, Phase.CLIMB);
		}
	}

	/** Up again after a run: the volley, if it can shoot one now, else it regroups. */
	void climbed(ServerLevel level) {
		if (phase != Phase.CLIMB) {
			return;
		}
		var target = target();
		enter(level, target != null && level.getGameTime() >= nextVolley && canVolley(target) ? Phase.VOLLEY : Phase.REGROUP);
	}

	/** The run stopped short (no room over the target, its target gone, the Grenadier killed): it regroups. */
	void runStopped(ServerLevel level) {
		if (phase == Phase.BOMBING_RUN || phase == Phase.CLIMB) {
			if (phase == Phase.BOMBING_RUN) {
				nextRun = level.getGameTime() + bombardier().cooldown();
			}
			enter(level, Phase.REGROUP);
		}
	}

	public void tick(ServerLevel level) {
		if (ghast.isNoAi() || !ghast.isAlive()) {
			return;
		}
		var tactics = tuning();
		long now = level.getGameTime();
		int in = (int) (now - since);
		var target = target();
		switch (phase) {
			case CRUISE -> {
				if (target == null || now < nextAbility) {
					return;
				}
				if (now >= nextRun && canRun(target)) {
					enter(level, Phase.TELEGRAPH);
				} else if (now >= nextVolley && canVolley(target)) {
					enter(level, Phase.VOLLEY);
				}
			}
			case TELEGRAPH -> {
				if (in >= tactics.telegraphTicks()) {
					enter(level, target != null && canRun(target) ? Phase.BOMBING_RUN : Phase.REGROUP);
				}
			}
			case BOMBING_RUN, CLIMB -> {
				// the run goal moves it through these and says when each ends; this is in case it never does
				if (in > 3 * tactics.phaseTimeout()) {
					runStopped(level);
				}
			}
			case VOLLEY -> {
				// its first shot started as it came in
				if (in > 0) {
					volley(level, target, in);
				}
			}
			case REGROUP -> {
				if (in >= tactics.regroupTicks()) {
					nextAbility = now + tactics.globalCooldown();
					enter(level, Phase.CRUISE);
				}
			}
		}
	}

	/**
	 * Each fireball in turn, {@code volley_spacing} apart: it charges (the shooting face, the warning heard) and flies
	 * {@link #CHARGE_TICKS} later. Without a target it can see, or the spear piglin, the volley ends there.
	 */
	private void volley(ServerLevel level, @Nullable LivingEntity target, int in) {
		var tactics = tuning();
		int shot = in / tactics.volleySpacing();
		int at = in % tactics.volleySpacing();
		if (shot >= tactics.volleySize() || in > tactics.phaseTimeout()) {
			endVolley(level);
			return;
		}
		if (at == 0) {
			if (target == null || !canVolley(target)) {
				endVolley(level);
				return;
			}
			ghast.setCharging(true);
			if (!ghast.isSilent()) {
				level.levelEvent(null, LevelEvent.SOUND_GHAST_WARNING, ghast.blockPosition(), 0);
			}
		} else if (at == CHARGE_TICKS) {
			if (target != null && target.isAlive() && ghast.shoots()) {
				shoot(level, target);
			}
			ghast.setCharging(false);
		}
	}

	private void endVolley(ServerLevel level) {
		ghast.setCharging(false);
		nextVolley = level.getGameTime() + tuning().volleyCooldown();
		enter(level, Phase.REGROUP);
	}

	/** A fireball at the target, as vanilla's ghast shoots one: from 4 blocks ahead of it, its own blast. */
	private void shoot(ServerLevel level, LivingEntity target) {
		var view = ghast.getViewVector(1.0f);
		var aim = new Vec3(target.getX() - (ghast.getX() + view.x * 4), target.getY(0.5) - (0.5 + ghast.getY(0.5)),
				target.getZ() - (ghast.getZ() + view.z * 4));
		if (!ghast.isSilent()) {
			level.levelEvent(null, LevelEvent.SOUND_GHAST_FIREBALL, ghast.blockPosition(), 0);
		}
		var fireball = new LargeFireball(level, ghast, aim.normalize(), ghast.getExplosionPower());
		fireball.setPos(ghast.getX() + view.x * 4, ghast.getY(0.5) + 0.5, ghast.getZ() + view.z * 4);
		level.addFreshEntity(fireball);
	}

	private void enter(ServerLevel level, Phase next) {
		var from = phase;
		phase = next;
		since = level.getGameTime();
		for (var listener : PHASE_LISTENERS) {
			listener.changed(ghast, from, next, since);
		}
		switch (next) {
			case TELEGRAPH -> telegraph(level);
			case BOMBING_RUN -> bombs = 0;
			case VOLLEY -> volley(level, target(), 0);
			default -> {
			}
		}
	}

	/** The run's tell: the ghast's wail, its drummer's war cry (if he's on the deck), and the dive's pitch. */
	private void telegraph(ServerLevel level) {
		if (!ghast.isSilent()) {
			level.playSound(null, ghast.getX(), ghast.getY(), ghast.getZ(), SoundEvents.GHAST_WARN, SoundSource.HOSTILE, 4.0f, 0.8f);
		}
		if (ghast.hasCrew(WarGhast.Crew.DRUMMER)) {
			var drummer = ghast.spot(WarGhast.Crew.DRUMMER).feet();
			WarDrummer.warCrySound(level, drummer.x, drummer.y + 1, drummer.z);
		}
		ghast.play(WarGhast.DIVE);
	}

	/** A run needs the Grenadier, a target in reach, and room over it for the ghast and its deck at the dive's height. */
	public boolean canRun(LivingEntity target) {
		return ghast.hasCrew(WarGhast.Crew.BOMBARDIER) && ghast.distanceTo(target) < RUN_REACH && BombingRunGoal.roomOver(ghast, target);
	}

	/** A volley needs the spear piglin, and a target in reach the ghast can see. */
	public boolean canVolley(LivingEntity target) {
		return ghast.shoots() && ghast.distanceToSqr(target) < VOLLEY_REACH * VOLLEY_REACH && ghast.hasLineOfSight(target);
	}

	@Nullable
	private LivingEntity target() {
		var target = ghast.getTarget();
		return target != null && target.isAlive() ? target : null;
	}

	private static NetherRiftsConfig.BombardierTuning bombardier() {
		return NetherRiftsConfig.getInstance().mobs().bombardier();
	}

	private static NetherRiftsConfig.TacticsTuning tuning() {
		return bombardier().tactics();
	}
}
