package nu.metacraft.core.entity.ai.tasks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;

import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Copy of {@link net.minecraft.world.entity.ai.goal.RangedBowAttackGoal},
 * rewritten as a {@link net.minecraft.world.entity.ai.behavior.BehaviorControl}
 * with support for weapons other than bows.
 * @param <T> The entity type.
 */
public class SmartShootAttackTask<T extends Mob & RangedAttackMob> extends Behavior<T> {

	protected final Predicate<ItemStack> isValidWeapon;
	protected final Function<ItemStack, OptionalInt> rangeOverrides;

	private final int attackInterval;
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
				MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
				MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT
		), 1200);
		this.isValidWeapon = isValidWeapon;
		this.rangeOverrides = rangeOverrides;
		this.attackInterval = attackInterval;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel serverWorld, T mobEntity) {
		LivingEntity livingEntity = getAttackTarget(mobEntity);
		return mobEntity.isHolding(isValidWeapon) && BehaviorUtils.canSee(mobEntity, livingEntity) && ImprovedRangedApproachTask.isInAttackingDistance(mobEntity, livingEntity, 0, rangeOverrides);
	}

	@Override
	protected boolean canStillUse(ServerLevel serverWorld, T mobEntity, long l) {
		return mobEntity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && this.checkExtraStartConditions(serverWorld, mobEntity);
	}

	@Override
	protected void tick(ServerLevel serverWorld, T mobEntity, long l) {
		LivingEntity livingEntity = getAttackTarget(mobEntity);
		boolean canSeeTarget = mobEntity.getSensing().hasLineOfSight(livingEntity);
		if (!mobEntity.getBrain().hasMemoryValue(METAcraftMemoryModules.IS_SMART_SHOOTING)) {
			mobEntity.getBrain().setMemory(METAcraftMemoryModules.IS_SMART_SHOOTING, Unit.INSTANCE);
		}
		if (canSeeTarget) {
			targetSeeingTicker++;
		} else {
			targetSeeingTicker = 0;
		}
		if (mobEntity.isUsingItem()) {
			int i;
			if (!canSeeTarget && this.targetSeeingTicker < -60) {
				mobEntity.stopUsingItem();
			} else if (canSeeTarget && (i = mobEntity.getTicksUsingItem()) >= 20) {
				mobEntity.stopUsingItem();
				mobEntity.performRangedAttack(livingEntity, BowItem.getPowerForTime(i));
				this.cooldown = this.attackInterval;
			}
		} else if (--this.cooldown <= 0 && this.targetSeeingTicker >= -60) {
			if (isValidWeapon.test(mobEntity.getMainHandItem())) {
				mobEntity.startUsingItem(InteractionHand.MAIN_HAND);
			} else if (isValidWeapon.test(mobEntity.getOffhandItem())) {
				mobEntity.startUsingItem(InteractionHand.OFF_HAND);
			}
		}
	}

	@Override
	protected void stop(ServerLevel serverWorld, T mobEntity, long l) {
		super.stop(serverWorld, mobEntity, l);
		this.targetSeeingTicker = 0;
		this.cooldown = -1;
		mobEntity.stopUsingItem();
		mobEntity.getBrain().eraseMemory(METAcraftMemoryModules.IS_SMART_SHOOTING);
	}

	private static LivingEntity getAttackTarget(LivingEntity entity) {
		return entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
	}
}
