package se.datasektionen.mc.metacraft_core.entity.ai.tasks;

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
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftMemoryModules;

import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Copy of {@link net.minecraft.entity.ai.goal.BowAttackGoal},
 * rewritten as a {@link net.minecraft.entity.ai.brain.task.Task}
 * with support for weapons other than bows.
 * @param <T> The entity type.
 */
public class SmartStrafeAttackTask<T extends MobEntity & RangedAttackMob> extends MultiTickTask<T> {

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
		this(speed, range, e -> e.getBrain().hasMemoryModule(METAcraftMemoryModules.IS_SMART_SHOOTING));
	}

	public SmartStrafeAttackTask(
			double speed, float range, Predicate<T> shouldRun
	) {
		super(ImmutableMap.of(
				MemoryModuleType.LOOK_TARGET, MemoryModuleState.REGISTERED,
				MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_PRESENT
		), 1200);
		this.speed = speed;
		this.squaredRange = range * range;
		this.shouldRun = shouldRun;
	}

	@Override
	protected boolean shouldRun(ServerWorld serverWorld, T mobEntity) {
		return shouldRun.test(mobEntity);
	}

	@Override
	protected boolean shouldKeepRunning(ServerWorld serverWorld, T mobEntity, long l) {
		return mobEntity.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET) && this.shouldRun(serverWorld, mobEntity);
	}

	@Override
	protected void keepRunning(ServerWorld serverWorld, T mobEntity, long l) {
		LivingEntity livingEntity = getAttackTarget(mobEntity);
		double distance = mobEntity.squaredDistanceTo(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
		boolean canSeeTarget = mobEntity.getVisibilityCache().canSee(livingEntity);
		if (canSeeTarget) {
			targetSeeingTicker++;
		} else {
			targetSeeingTicker = 0;
		}
		if (distance > this.squaredRange || this.targetSeeingTicker < 20) {
			mobEntity.getNavigation().startMovingTo(livingEntity, this.speed);
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
			mobEntity.getMoveControl().strafeTo(this.backward ? -0.5f : 0.5f, this.movingToLeft ? 0.5f : -0.5f);
			if (mobEntity.getControllingVehicle() instanceof MobEntity vehicle) {
				vehicle.lookAtEntity(livingEntity, 30.0f, 30.0f);
			}
			mobEntity.lookAtEntity(livingEntity, 30.0f, 30.0f);
		} else {
			mobEntity.getLookControl().lookAt(livingEntity, 30.0f, 30.0f);
		}
	}

	@Override
	protected void finishRunning(ServerWorld serverWorld, T mobEntity, long l) {
		super.finishRunning(serverWorld, mobEntity, l);
		this.targetSeeingTicker = 0;
	}

	private static LivingEntity getAttackTarget(LivingEntity entity) {
		return entity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).get();
	}
}
