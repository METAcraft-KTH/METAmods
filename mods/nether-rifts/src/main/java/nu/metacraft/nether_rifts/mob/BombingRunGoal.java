package nu.metacraft.nether_rifts.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A war ghast picks its target and goes through four phases: dives till its deck is 4 blocks above the start of
 * a 20-block line across the target; runs along it while the Bombardier drops a bomb from the deck every 6 ticks
 * (when within 8 blocks of the target); climbs back till the deck is 14 above the target; waits 100 ticks. Low
 * over the line is its weak window. Where a wall, roof or the ground is in the way, the line and the climb stop
 * short of it: a ghast's move control won't fly at a point its body doesn't fit, and the deck hanging under it
 * must fit too. With no room for the ghast and its deck over the target at all, the run is given up until the
 * cooldown has passed. Only while the Grenadier is on the deck: killed there, no more runs. The numbers are the
 * defaults of the config's {@code mobs.bombardier} section.
 */
public class BombingRunGoal extends Goal {

	/** Defaults for {@link NetherRiftsConfig.BombardierTuning}. */
	public static final double DIVE_HEIGHT = 4;
	public static final double CLIMB_HEIGHT = 14;
	public static final double RUN_LENGTH = 20;
	public static final double BOMB_RANGE = 8;
	public static final int BOMB_EVERY = 6;
	public static final int COOLDOWN = 100;
	private static final int PHASE_TIMEOUT = 120;

	private enum Phase { DIVE, RUN, CLIMB }

	private final WarGhast ghast;
	private Phase phase;
	private Vec3 runStart;
	private Vec3 runEnd;
	private Vec3 climbTo;
	private LivingEntity target;
	/** Game time, not a countdown: idle goals are only polled every other tick. */
	private long cooldownUntil = 0;
	private int ticks;

	public BombingRunGoal(WarGhast ghast) {
		this.ghast = ghast;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (ghast.level().getGameTime() < cooldownUntil) {
			return false;
		}
		var t = ghast.getTarget();
		return t != null && t.isAlive() && ghast.distanceTo(t) < 48 && ghast.hasCrew(WarGhast.Crew.BOMBARDIER);
	}

	@Override
	public boolean canContinueToUse() {
		return phase != null && target != null && target.isAlive() && ghast.hasCrew(WarGhast.Crew.BOMBARDIER);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		target = ghast.getTarget();
		var tuning = tuning();
		var across = new Vec3(target.getX() - ghast.getX(), 0, target.getZ() - ghast.getZ());
		across = across.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : across.normalize();
		// the heights are the deck's, which hangs DECK_DROP under the ghast's feet
		var above = target.position().add(0, tuning.diveHeight() + WarGhast.DECK_DROP, 0);
		runStart = clear(above, above.subtract(across.scale(tuning.runLength() / 2)));
		runEnd = clear(above, above.add(across.scale(tuning.runLength() / 2)));
		ticks = 0;
		// no room over the target (a low roof, a tunnel): give up, and try again after the cooldown
		phase = runStart == null || runEnd == null ? null : Phase.DIVE;
	}

	@Override
	public void stop() {
		phase = null;
		target = null;
		cooldownUntil = ghast.level().getGameTime() + tuning().cooldown();
	}

	@Override
	public void tick() {
		// a finished run is only stopped at the next full goal update; every-tick updates come in between
		if (phase == null) {
			return;
		}
		ticks++;
		switch (phase) {
			case DIVE -> {
				moveTo(runStart);
				if (ghast.position().distanceTo(runStart) < 2 || ticks > PHASE_TIMEOUT) {
					next(Phase.RUN);
				}
			}
			case RUN -> {
				moveTo(runEnd);
				var tuning = tuning();
				if (ticks % tuning.bombEvery() == 0 && horizontalDistance(ghast.position(), target.position()) < tuning.bombRange()
						&& ghast.level() instanceof ServerLevel level) {
					ghast.dropBomb(level);
				}
				if (ghast.position().distanceTo(runEnd) < 2 || ticks > PHASE_TIMEOUT) {
					climbTo = clear(target.position().add(0, tuning.diveHeight() + WarGhast.DECK_DROP, 0),
							target.position().add(0, tuning.climbHeight() + WarGhast.DECK_DROP, 0));
					next(climbTo == null ? null : Phase.CLIMB);
				}
			}
			case CLIMB -> {
				moveTo(climbTo);
				if (ghast.position().distanceTo(climbTo) < 3 || ticks > PHASE_TIMEOUT) {
					phase = null;
				}
			}
		}
	}

	private void next(@Nullable Phase phase) {
		this.phase = phase;
		this.ticks = 0;
	}

	/** A ghast's move control flies at its own speed (the flying speed attribute), so there's no speed to give. */
	private void moveTo(Vec3 at) {
		ghast.getMoveControl().setWantedPosition(at.x, at.y, at.z, 1.0);
		ghast.getLookControl().setLookAt(at.x, at.y, at.z);
		// what the ghast's own look goal would do (this goal holds LOOK): face where it flies, deck and crew with it
		Ghast.faceMovementDirection(ghast);
	}

	private static NetherRiftsConfig.BombardierTuning tuning() {
		return NetherRiftsConfig.getInstance().mobs().bombardier();
	}

	/**
	 * The farthest point from {@code from} toward {@code to}, in half-block steps, where the ghast and its deck fit;
	 * null if they don't fit at {@code from}.
	 */
	@Nullable
	private Vec3 clear(Vec3 from, Vec3 to) {
		var stack = stack();
		if (!fits(stack, from)) {
			return null;
		}
		var path = to.subtract(from);
		int steps = Math.max(1, (int) Math.ceil(path.length() * 2));
		var fit = from;
		for (int i = 1; i <= steps; i++) {
			var at = from.add(path.scale(i / (double) steps));
			if (!fits(stack, at)) {
				break;
			}
			fit = at;
		}
		return fit;
	}

	/** The ghast's box down to its deck's underside, with the ghast's feet at the origin. */
	private AABB stack() {
		return ghast.stackBox().move(-ghast.getX(), -ghast.getY(), -ghast.getZ());
	}

	private boolean fits(AABB stack, Vec3 at) {
		return ghast.level().noBlockCollision(ghast, stack.move(at));
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
	}
}
