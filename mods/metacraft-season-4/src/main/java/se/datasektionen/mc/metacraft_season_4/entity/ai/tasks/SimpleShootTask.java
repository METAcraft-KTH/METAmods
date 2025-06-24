package se.datasektionen.mc.metacraft_season_4.entity.ai.tasks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.brain.task.TargetUtil;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftMemoryModules;

import java.util.function.IntConsumer;

public class SimpleShootTask<T extends MobEntity & RangedAttackMob> extends MultiTickTask<T> {

	private final int attackInterval;
	private int cooldown = -1;
	private final int maxRange;
	private final IntConsumer onTick;

	public SimpleShootTask(
			int attackInterval, int maxRange, IntConsumer onTick
	) {
		super(ImmutableMap.of(
				MemoryModuleType.LOOK_TARGET, MemoryModuleState.REGISTERED,
				MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_PRESENT
		), 1200);
		this.maxRange = maxRange;
		this.attackInterval = attackInterval;
		this.onTick = onTick;
	}

	@Override
	protected boolean shouldRun(ServerWorld serverWorld, T mobEntity) {
		LivingEntity livingEntity = getAttackTarget(mobEntity);
		return TargetUtil.isVisibleInMemory(mobEntity, livingEntity) && mobEntity.isInRange(livingEntity, maxRange);
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
		if (canSeeTarget && this.cooldown <= 0) {
			mobEntity.shootAt(livingEntity, 1);
			this.cooldown = this.attackInterval;
		}
		if (this.cooldown > 0) {
			this.cooldown--;
			if (onTick != null) {
				onTick.accept(cooldown);
			}
		}
	}

	@Override
	protected void finishRunning(ServerWorld serverWorld, T mobEntity, long l) {
		super.finishRunning(serverWorld, mobEntity, l);
		this.cooldown = -1;
		mobEntity.getBrain().forget(METAcraftMemoryModules.IS_SMART_SHOOTING);
	}

	private static LivingEntity getAttackTarget(LivingEntity entity) {
		return entity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).get();
	}

}
