package nu.metacraft.core.entity.ai.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Copy of {@link net.minecraft.world.entity.ai.behavior.GoToClosestVillage}
 * but targets a given memory module instead.
 */
public class GoToMoveTarget {

	public static BehaviorControl<PathfinderMob> create(MemoryModuleType<GlobalPos> target, float speed, int completionRange) {
		return BehaviorBuilder.create(context -> context.group(context.absent(MemoryModuleType.WALK_TARGET), context.present(target)).apply(context, (walkTarget, targetPos) -> (world, entity, time) -> {
			var actualTarget = context.get(targetPos);
			if (actualTarget.dimension() != world.dimension()) {
				return false;
			}
			double distanceToTarget = actualTarget.pos().distSqr(entity.blockPosition());
			Vec3 walkTargetPos = null;
			for (int tries = 0; tries < 5; tries++) {
				Vec3 nearbyPosition = LandRandomPos.getPos(entity, 15, 7, pos -> -pos.distSqr(actualTarget.pos()));
				if (nearbyPosition == null) continue;
				double futureDistanceToTarget = actualTarget.pos().distSqr(BlockPos.containing(nearbyPosition));
				if (futureDistanceToTarget < distanceToTarget) {
					walkTargetPos = nearbyPosition;
					break;
				}
				if (futureDistanceToTarget != distanceToTarget) continue;
				walkTargetPos = nearbyPosition;
			}
			if (walkTargetPos != null) {
				walkTarget.set(new WalkTarget(walkTargetPos, speed, completionRange));
			}
			return true;
		}));
	}

}
