package nu.metacraft.nether_rifts.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Vanilla's ghast float (a point up to 16 blocks off each way, picked anew when it gets there or strays far), for a
 * ghast with a deck: only points where the ghast and its deck fit (the deck never in the ground), and no higher than
 * {@code mobs.bombardier.max_float_height} above the ground under them. Over the void (no ground within reach) there's
 * no cap, but it doesn't climb.
 */
public class DeckFloatGoal extends Goal {

	private static final int TRIES = 64;
	private static final double REACH = 16;

	private final WarGhast ghast;

	public DeckFloatGoal(WarGhast ghast) {
		this.ghast = ghast;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		var move = ghast.getMoveControl();
		if (!move.hasWanted()) {
			return true;
		}
		double dx = move.getWantedX() - ghast.getX();
		double dy = move.getWantedY() - ghast.getY();
		double dz = move.getWantedZ() - ghast.getZ();
		double d = dx * dx + dy * dy + dz * dz;
		return d < 1.0 || d > 3600.0;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		var to = pick();
		if (to != null) {
			ghast.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
		}
	}

	@Nullable
	private Vec3 pick() {
		var random = ghast.getRandom();
		double cap = NetherRiftsConfig.getInstance().mobs().bombardier().maxFloatHeight();
		for (int i = 0; i < TRIES; i++) {
			var to = ghast.position().add((random.nextDouble() * 2 - 1) * REACH, (random.nextDouble() * 2 - 1) * REACH,
					(random.nextDouble() * 2 - 1) * REACH);
			if (ghast.hasHome() && !ghast.isWithinHome(to)) {
				continue;
			}
			var ground = groundUnder(ghast.level(), to);
			if (ground == null ? to.y > ghast.getY() : to.y - ground > cap) {
				continue;
			}
			if (ghast.fitsAt(to)) {
				return to;
			}
		}
		return null;
	}

	/** The top of the first solid block under a point (its collision box's), or null over the void. */
	@Nullable
	public static Double groundUnder(Level level, Vec3 at) {
		var pos = BlockPos.containing(at).mutable();
		for (; pos.getY() >= level.getMinY(); pos.move(0, -1, 0)) {
			var shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double top = pos.getY() + shape.max(Direction.Axis.Y);
				if (top <= at.y) {
					return top;
				}
			}
		}
		return null;
	}
}
