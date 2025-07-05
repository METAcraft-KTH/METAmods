package nu.metacraft.core.entity.ai.tasks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.brain.task.TargetUtil;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Unit;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;

import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Copy of {@link net.minecraft.entity.ai.goal.BowAttackGoal},
 * rewritten as a {@link net.minecraft.entity.ai.brain.task.Task}
 * with support for weapons other than bows.
 * @param <T> The entity type.
 */
public class SmartShootAttackTask<T extends MobEntity & RangedAttackMob> extends MultiTickTask<T> {

	protected final Predicate<ItemStack> isValidWeapon;
	protected final Function<ItemStack, OptionalInt> rangeOverrides;

	private int attackInterval;
	private int cooldown = -1;
	private int targetSeeingTicker;

	public SmartShootAttackTask(
			Predicate<ItemStack> isValidWeapon, int attackInterval
	) {
		this(isValidWeapon, attackInterval, s -> OptionalInt.empty());
	}

	public SmartShootAttackTask(
			Predicate<ItemStack> isValidWeapon, int attackInterval,
			Function<ItemStack, OptionalInt> rangeOverrides
	) {
		super(ImmutableMap.of(
				MemoryModuleType.LOOK_TARGET, MemoryModuleState.REGISTERED,
				MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_PRESENT
		), 1200);
		this.isValidWeapon = isValidWeapon;
		this.rangeOverrides = rangeOverrides;
		this.attackInterval = attackInterval;
	}

	@Override
	protected boolean shouldRun(ServerWorld serverWorld, T mobEntity) {
		LivingEntity livingEntity = getAttackTarget(mobEntity);
		return mobEntity.isHolding(isValidWeapon) && TargetUtil.isVisibleInMemory(mobEntity, livingEntity) && ImprovedRangedApproachTask.isInAttackingDistance(mobEntity, livingEntity, 0, rangeOverrides);
	}

	@Override
	protected boolean shouldKeepRunning(ServerWorld serverWorld, T mobEntity, long l) {
		return mobEntity.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET) && this.shouldRun(serverWorld, mobEntity);
	}

	@Override
	protected void keepRunning(ServerWorld serverWorld, T mobEntity, long l) {
		LivingEntity livingEntity = getAttackTarget(mobEntity);
		boolean canSeeTarget = mobEntity.getVisibilityCache().canSee(livingEntity);
		if (!mobEntity.getBrain().hasMemoryModule(METAcraftMemoryModules.IS_SMART_SHOOTING)) {
			mobEntity.getBrain().remember(METAcraftMemoryModules.IS_SMART_SHOOTING, Unit.INSTANCE);
		}
		if (canSeeTarget) {
			targetSeeingTicker++;
		} else {
			targetSeeingTicker = 0;
		}
		if (mobEntity.isUsingItem()) {
			int i;
			if (!canSeeTarget && this.targetSeeingTicker < -60) {
				mobEntity.clearActiveItem();
			} else if (canSeeTarget && (i = mobEntity.getItemUseTime()) >= 20) {
				mobEntity.clearActiveItem();
				mobEntity.shootAt(livingEntity, BowItem.getPullProgress(i));
				this.cooldown = this.attackInterval;
			}
		} else if (--this.cooldown <= 0 && this.targetSeeingTicker >= -60) {
			if (isValidWeapon.test(mobEntity.getMainHandStack())) {
				mobEntity.setCurrentHand(Hand.MAIN_HAND);
			} else if (isValidWeapon.test(mobEntity.getOffHandStack())) {
				mobEntity.setCurrentHand(Hand.OFF_HAND);
			}
		}
	}

	@Override
	protected void finishRunning(ServerWorld serverWorld, T mobEntity, long l) {
		super.finishRunning(serverWorld, mobEntity, l);
		this.targetSeeingTicker = 0;
		this.cooldown = -1;
		mobEntity.clearActiveItem();
		mobEntity.getBrain().forget(METAcraftMemoryModules.IS_SMART_SHOOTING);
	}

	private static LivingEntity getAttackTarget(LivingEntity entity) {
		return entity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).get();
	}
}
