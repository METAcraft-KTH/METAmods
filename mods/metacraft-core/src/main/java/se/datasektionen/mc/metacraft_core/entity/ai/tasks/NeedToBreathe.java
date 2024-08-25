package se.datasektionen.mc.metacraft_core.entity.ai.tasks;

import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftActivities;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftMemoryModules;

import java.util.Map;

public class NeedToBreathe extends MultiTickTask<MobEntity> {

	private final float speed;

	public NeedToBreathe(float speed) {
		super(Map.of(METAcraftMemoryModules.NEAREST_OXYGEN, MemoryModuleState.VALUE_PRESENT, MemoryModuleType.WALK_TARGET, MemoryModuleState.REGISTERED));
		this.speed = speed;
	}

	private double getActualAir(MobEntity mob) {
		var attribute = mob.getAttributeInstance(EntityAttributes.GENERIC_OXYGEN_BONUS);
		double value = attribute != null ? attribute.getValue() : 0;
		double airLossProbability = 1.0 / (value + 1.0);
		if (airLossProbability == 0) {
			return Double.POSITIVE_INFINITY;
		} else {
			return mob.getAir() / airLossProbability;
		}
	}

	private boolean isRelevant(MobEntity entity) {
		return !entity.canBreatheInWater() && !StatusEffectUtil.hasWaterBreathing(entity);
	}


	@Override
	protected boolean shouldRun(ServerWorld world, MobEntity entity) {
		if (entity.isSubmergedInWater() && isRelevant(entity)) {
			var nearestOxygen = entity.getBrain().getOptionalRegisteredMemory(METAcraftMemoryModules.NEAREST_OXYGEN);
			if (nearestOxygen.isPresent() && nearestOxygen.get().dimension() == world.getRegistryKey()) {
				double entitySpeed = entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
				if (nearestOxygen.get().pos().getSquaredDistance(entity.getPos()) * speed * entitySpeed >= getActualAir(entity)) {
					return true;
				}
			}
		}
		return false;
	}

	private void updateTarget(ServerWorld world, MobEntity entity) {
		var nearestOxygen = entity.getBrain().getOptionalRegisteredMemory(METAcraftMemoryModules.NEAREST_OXYGEN);
		if (nearestOxygen.isPresent() && nearestOxygen.get().dimension() == world.getRegistryKey()) {
			entity.getBrain().remember(
					MemoryModuleType.WALK_TARGET,
					new WalkTarget(nearestOxygen.get().pos(), speed, 0)
			);
		}
	}

	@Override
	protected void run(ServerWorld world, MobEntity entity, long time) {
		super.run(world, entity, time);
		entity.getBrain().doExclusively(METAcraftActivities.SWIM_TO_SURFACE);
		updateTarget(world, entity);
	}

	@Override
	protected void keepRunning(ServerWorld world, MobEntity entity, long time) {
		updateTarget(world, entity);
		if (entity.isSubmergedInWater() && !world.getBlockState(BlockPos.ofFloored(entity.getEyePos()).up()).getFluidState().isIn(FluidTags.WATER)) {
			entity.getMoveControl().moveTo(entity.getX(), entity.getY()+1, entity.getZ(), speed);
		}
	}

	@Override
	protected boolean shouldKeepRunning(ServerWorld world, MobEntity entity, long time) {
		if (isRelevant(entity) && entity.getAir() < entity.getMaxAir()) {
			return entity.getBrain().getOptionalRegisteredMemory(METAcraftMemoryModules.NEAREST_OXYGEN).isPresent();
		}
		return super.shouldKeepRunning(world, entity, time);
	}

	@Override
	protected void finishRunning(ServerWorld world, MobEntity entity, long time) {
		super.finishRunning(world, entity, time);
		var target = entity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.WALK_TARGET);
		if (target.isPresent() && target.equals(entity.getBrain().getOptionalRegisteredMemory(METAcraftMemoryModules.NEAREST_OXYGEN))) {
			entity.getBrain().forget(MemoryModuleType.WALK_TARGET);
		}
		entity.getBrain().resetPossibleActivities();
	}

	@Override
	protected boolean isTimeLimitExceeded(long time) {
		return false;
	}
}
