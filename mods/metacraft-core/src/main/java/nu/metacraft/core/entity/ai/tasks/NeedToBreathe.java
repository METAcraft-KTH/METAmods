package nu.metacraft.core.entity.ai.tasks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;

import java.util.Map;

public class NeedToBreathe extends Behavior<Mob> {

	private final float speed;

	public NeedToBreathe(float speed) {
		super(Map.of(METAcraftMemoryModules.NEAREST_OXYGEN, MemoryStatus.VALUE_PRESENT, MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, METAcraftMemoryModules.RECOVERING_BREATH, MemoryStatus.REGISTERED));
		this.speed = speed;
	}

	private double getActualAir(Mob mob) {
		var attribute = mob.getAttribute(Attributes.OXYGEN_BONUS);
		double value = attribute != null ? attribute.getValue() : 0;
		double airLossProbability = 1.0 / (value + 1.0);
		if (airLossProbability == 0) {
			return Double.POSITIVE_INFINITY;
		} else {
			return mob.getAirSupply() / airLossProbability;
		}
	}

	private boolean isRelevant(Mob entity) {
		return !entity.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(entity);
	}


	@Override
	protected boolean checkExtraStartConditions(ServerLevel world, Mob entity) {
		if (entity.isUnderWater() && isRelevant(entity)) {
			var nearestOxygen = entity.getBrain().getMemory(METAcraftMemoryModules.NEAREST_OXYGEN);
			if (nearestOxygen.isPresent() && nearestOxygen.get().dimension() == world.dimension()) {
				double entitySpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
				return nearestOxygen.get().pos().distToCenterSqr(entity.position()) * speed * entitySpeed >= getActualAir(entity);
			}
		}
		return false;
	}

	private void updateTarget(ServerLevel world, Mob entity) {
		if (!entity.isUnderWater()) {
			return;
		}
		var nearestOxygen = entity.getBrain().getMemory(METAcraftMemoryModules.NEAREST_OXYGEN);
		if (nearestOxygen.isPresent() && nearestOxygen.get().dimension() == world.dimension()) {
			if (
					entity.getNavigation().getPath() != null &&
					nearestOxygen.get().pos().equals(entity.getNavigation().getPath().getTarget())
			) {
				return;
			}
			entity.getNavigation().stop();
			entity.getBrain().setMemory(
					MemoryModuleType.WALK_TARGET,
					new WalkTarget(nearestOxygen.get().pos(), speed, 0)
			);
		}
	}

	@Override
	protected void start(ServerLevel world, Mob entity, long time) {
		super.start(world, entity, time);
		updateTarget(world, entity);
		entity.getBrain().setMemory(METAcraftMemoryModules.RECOVERING_BREATH, Unit.INSTANCE);
	}

	@Override
	protected void tick(ServerLevel world, Mob entity, long time) {
		super.tick(world, entity, time);
		updateTarget(world, entity);
	}

	@Override
	protected boolean canStillUse(ServerLevel world, Mob entity, long time) {
		if (isRelevant(entity) && entity.getAirSupply() < entity.getMaxAirSupply()) {
			return entity.getBrain().getMemory(METAcraftMemoryModules.NEAREST_OXYGEN).isPresent();
		}
		return super.canStillUse(world, entity, time);
	}

	@Override
	protected void stop(ServerLevel world, Mob entity, long time) {
		super.stop(world, entity, time);
		entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		entity.getBrain().eraseMemory(METAcraftMemoryModules.RECOVERING_BREATH);
		entity.getNavigation().stop();
	}

	@Override
	protected boolean timedOut(long time) {
		return false;
	}
}
