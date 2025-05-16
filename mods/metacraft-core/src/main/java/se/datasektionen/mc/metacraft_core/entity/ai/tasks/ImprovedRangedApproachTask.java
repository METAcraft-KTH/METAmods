package se.datasektionen.mc.metacraft_core.entity.ai.tasks;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.TargetUtil;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import se.datasektionen.mc.metacraft_core.util.helper.TridentHelper;

import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Copy of {@link net.minecraft.entity.ai.brain.task.RangedApproachTask},
 * with support for tridents.
 */
public class ImprovedRangedApproachTask {

	public static Task<MobEntity> create(float speed) {
		return create(speed, s -> OptionalInt.empty());
	}

	public static Task<MobEntity> create(float speed, Function<ItemStack, OptionalInt> rangeOverride) {
		return create(entity -> speed, rangeOverride);
	}

	public static boolean isInAttackingDistance(
			MobEntity entity, LivingEntity target, int reduction,
			Function<ItemStack, OptionalInt> rangeOverride
	) {
		var tridentResult = TridentHelper.shouldThrowTrident(entity, target);
		if (tridentResult.isPresent()) {
			if (tridentResult.get()) {
				return entity.isInRange(target, TridentHelper.getTridentRange());
			}
		}
		return isTargetWithinAttackRange(entity, target, reduction, rangeOverride);
	}

	public static boolean isTargetWithinAttackRange(
			MobEntity mob, LivingEntity target, int rangedWeaponReachReduction,
			Function<ItemStack, OptionalInt> rangeOverride
	) {
		var range = rangeOverride.apply(mob.getMainHandStack());
		if (range.isPresent()) {
			return mob.isInRange(target, range.getAsInt() - rangedWeaponReachReduction);
		}
		if (mob.getMainHandStack().getItem() instanceof RangedWeaponItem rangedWeaponItem && mob.canUseRangedWeapon(rangedWeaponItem)) {
			int i = rangedWeaponItem.getRange() - rangedWeaponReachReduction;
			return mob.isInRange(target, i);
		}

		return mob.isInAttackRange(target);
	}

	public static Task<MobEntity> create(Function<LivingEntity, Float> speed, Function<ItemStack, OptionalInt> rangeOverride) {
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
					if (optional.isPresent() && optional.get().contains(livingEntity) && isInAttackingDistance(entity, livingEntity, 1, rangeOverride)) {
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
