package nu.metacraft.core.entity.ai.tasks;

import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

/**
 * Copy of {@link net.minecraft.entity.ai.brain.task.GoToCloserPointOfInterestTask}
 * but targets a given memory module instead.
 */
public class GoToMoveTarget {

	public static Task<PathAwareEntity> create(MemoryModuleType<GlobalPos> target, float speed, int completionRange) {
		return TaskTriggerer.task(context -> context.group(context.queryMemoryAbsent(MemoryModuleType.WALK_TARGET), context.queryMemoryValue(target)).apply(context, (walkTarget, targetPos) -> (world, entity, time) -> {
			var actualTarget = context.getValue(targetPos);
			if (actualTarget.dimension() != world.getRegistryKey()) {
				return false;
			}
			double distanceToTarget = actualTarget.pos().getSquaredDistance(entity.getBlockPos());
			Vec3d walkTargetPos = null;
			for (int tries = 0; tries < 5; tries++) {
				Vec3d nearbyPosition = FuzzyTargeting.find(entity, 15, 7, pos -> -pos.getSquaredDistance(actualTarget.pos()));
				if (nearbyPosition == null) continue;
				double futureDistanceToTarget = actualTarget.pos().getSquaredDistance(BlockPos.ofFloored(nearbyPosition));
				if (futureDistanceToTarget < distanceToTarget) {
					walkTargetPos = nearbyPosition;
					break;
				}
				if (futureDistanceToTarget != distanceToTarget) continue;
				walkTargetPos = nearbyPosition;
			}
			if (walkTargetPos != null) {
				walkTarget.remember(new WalkTarget(walkTargetPos, speed, completionRange));
			}
			return true;
		}));
	}

}
