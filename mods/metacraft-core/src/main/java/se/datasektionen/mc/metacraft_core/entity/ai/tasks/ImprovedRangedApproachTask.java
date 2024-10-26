package se.datasektionen.mc.metacraft_core.entity.ai.tasks;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.TargetUtil;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.entity.mob.MobEntity;
import se.datasektionen.mc.metacraft_core.util.helper.TridentHelper;

import java.util.function.Function;

/**
 * Copy of {@link net.minecraft.entity.ai.brain.task.RangedApproachTask},
 * with support for tridents.
 */
public class ImprovedRangedApproachTask {

	public static Task<MobEntity> create(float speed) {
		return create(entity -> speed);
	}

	public static boolean isInAttackingDistance(MobEntity entity, LivingEntity target, int reduction) {
		var tridentResult = TridentHelper.shouldThrowTrident(entity, target);
		if (tridentResult.isPresent()) {
			if (tridentResult.get()) {
				return entity.isInRange(target, TridentHelper.getTridentRange());
			}
		}
		return TargetUtil.isTargetWithinAttackRange(entity, target, reduction);
	}

	public static Task<MobEntity> create(Function<LivingEntity, Float> speed) {
		return TaskTriggerer.task(
			context -> context.group(
				context.queryMemoryOptional(MemoryModuleType.WALK_TARGET),
				context.queryMemoryOptional(MemoryModuleType.LOOK_TARGET),
				context.queryMemoryValue(MemoryModuleType.ATTACK_TARGET),
				context.queryMemoryOptional(MemoryModuleType.VISIBLE_MOBS)
			).apply(
				context, (walkTarget, lookTarget, attackTarget, visibleMobs) -> (world, entity, time) -> {
					LivingEntity livingEntity = context.getValue(attackTarget);
					var optional = context.getOptionalValue(visibleMobs);
					if (optional.isPresent() && optional.get().contains(livingEntity) && isInAttackingDistance(entity, livingEntity, 1)) {
						walkTarget.forget();
					} else {
						lookTarget.remember(new EntityLookTarget(livingEntity, true));
						walkTarget.remember(new WalkTarget(
								new EntityLookTarget(livingEntity, false),
								speed.apply(entity), 0)
						);
					}
					return true;
				}
			)
		);
	}
}
