package nu.metacraft.core.entity.ai.tasks;

import com.google.common.collect.ImmutableMap;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.RangedAttackMob;

/**
 * Copy of {@link net.minecraft.world.entity.ai.goal.RangedBowAttackGoal},
 * rewritten as a {@link net.minecraft.world.entity.ai.behavior.BehaviorControl}
 * with support for weapons other than bows.
 * @param <T> The entity type.
 */
public class SmartStrafeAttackTask<T extends Mob & RangedAttackMob> extends Behavior<T> {

	private final Predicate<T> shouldRun;
	private final double speed;
	private final float squaredRange;
	private int targetSeeingTicker;
	private boolean movingToLeft;
	private boolean backward;
	private int combatTicks = -1;

	public SmartStrafeAttackTask(
			double speed, float range
	) {
		this(speed, range, e -> e.getBrain().hasMemoryValue(METAcraftMemoryModules.IS_SMART_SHOOTING));
	}

	public SmartStrafeAttackTask(
			double speed, float range, Predicate<T> shouldRun
	) {
		super(ImmutableMap.of(
				MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
				MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT
		), 1200);
		this.speed = speed;
		this.squaredRange = range * range;
		this.shouldRun = shouldRun;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel serverWorld, T mobEntity) {
		return shouldRun.test(mobEntity);
	}

	@Override
	protected boolean canStillUse(ServerLevel serverWorld, T mobEntity, long l) {
		return mobEntity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && this.checkExtraStartConditions(serverWorld, mobEntity);
	}

	@Override
	protected void tick(ServerLevel serverWorld, T mobEntity, long l) {
		LivingEntity livingEntity = getAttackTarget(mobEntity);
		double distance = mobEntity.distanceToSqr(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
		boolean canSeeTarget = mobEntity.getSensing().hasLineOfSight(livingEntity);
		if (canSeeTarget) {
			targetSeeingTicker++;
		} else {
			targetSeeingTicker = 0;
		}
		if (distance > this.squaredRange || this.targetSeeingTicker < 20) {
			mobEntity.getNavigation().moveTo(livingEntity, this.speed);
			this.combatTicks = -1;
		} else {
			mobEntity.getNavigation().stop();
			++this.combatTicks;
		}
		if (this.combatTicks >= 20) {
			if (mobEntity.getRandom().nextFloat() < 0.3) {
				this.movingToLeft = !this.movingToLeft;
			}
			if (mobEntity.getRandom().nextFloat() < 0.3) {
				this.backward = !this.backward;
			}
			this.combatTicks = 0;
		}
		if (this.combatTicks > -1) {
			if (distance > this.squaredRange * 0.75f) {
				this.backward = false;
			} else if (distance < this.squaredRange * 0.25f) {
				this.backward = true;
			}
			mobEntity.getMoveControl().strafe(this.backward ? -0.5f : 0.5f, this.movingToLeft ? 0.5f : -0.5f);
			if (mobEntity.getControlledVehicle() instanceof Mob vehicle) {
				vehicle.lookAt(livingEntity, 30.0f, 30.0f);
			}
			mobEntity.lookAt(livingEntity, 30.0f, 30.0f);
		} else {
			mobEntity.getLookControl().setLookAt(livingEntity, 30.0f, 30.0f);
		}
	}

	@Override
	protected void stop(ServerLevel serverWorld, T mobEntity, long l) {
		super.stop(serverWorld, mobEntity, l);
		this.targetSeeingTicker = 0;
	}

	private static LivingEntity getAttackTarget(LivingEntity entity) {
		return entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
	}
}
