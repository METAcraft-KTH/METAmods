package nu.metacraft.core.entity.ai.tasks;

import nu.metacraft.core.util.helper.TridentHelper;

import java.util.OptionalInt;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * Copy of {@link net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach},
 * with support for tridents.
 */
public class ImprovedRangedApproachTask {

	public static BehaviorControl<Mob> create(float speed) {
		return create(speed, s -> OptionalInt.empty());
	}

	public static BehaviorControl<Mob> create(float speed, Function<ItemStack, OptionalInt> rangeOverride) {
		return create(entity -> speed, rangeOverride);
	}

	public static boolean isInAttackingDistance(
			Mob entity, LivingEntity target, int reduction,
			Function<ItemStack, OptionalInt> rangeOverride
	) {
		var tridentResult = TridentHelper.shouldThrowTrident(entity, target);
		if (tridentResult.isPresent()) {
			if (tridentResult.get()) {
				return entity.closerThan(target, TridentHelper.getTridentRange());
			}
		}
		return isTargetWithinAttackRange(entity, target, reduction, rangeOverride);
	}

	public static boolean isTargetWithinAttackRange(
			Mob mob, LivingEntity target, int rangedWeaponReachReduction,
			Function<ItemStack, OptionalInt> rangeOverride
	) {
		var range = rangeOverride.apply(mob.getMainHandItem());
		if (range.isPresent()) {
			return mob.closerThan(target, range.getAsInt() - rangedWeaponReachReduction);
		}
		if (mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem rangedWeaponItem && mob.canFireProjectileWeapon(rangedWeaponItem)) {
			int i = rangedWeaponItem.getDefaultProjectileRange() - rangedWeaponReachReduction;
			return mob.closerThan(target, i);
		}

		return mob.isWithinMeleeAttackRange(target);
	}

	public static BehaviorControl<Mob> create(Function<LivingEntity, Float> speed, Function<ItemStack, OptionalInt> rangeOverride) {
		return BehaviorBuilder.create(
			context -> context.group(
				context.registered(MemoryModuleType.WALK_TARGET),
				context.registered(MemoryModuleType.LOOK_TARGET),
				context.present(MemoryModuleType.ATTACK_TARGET),
				context.registered(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
			).apply(
				context, (walkTarget, lookTarget, attackTarget, visibleMobs) -> (world, entity, time) -> {
					LivingEntity livingEntity = context.get(attackTarget);
					var optional = context.tryGet(visibleMobs);
					if (optional.isPresent() && optional.get().contains(livingEntity) && isInAttackingDistance(entity, livingEntity, 1, rangeOverride)) {
						walkTarget.erase();
					} else {
						lookTarget.set(new EntityTracker(livingEntity, true));
						walkTarget.set(new WalkTarget(
								new EntityTracker(livingEntity, false),
								speed.apply(entity), 0)
						);
					}
					return true;
				}
			)
		);
	}
}
