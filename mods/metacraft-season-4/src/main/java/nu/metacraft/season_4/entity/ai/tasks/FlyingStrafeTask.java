package nu.metacraft.season_4.entity.ai.tasks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import nu.metacraft.season_4.entity.ai.FlightWithStrafeMoveControl;

import java.util.Optional;

public class FlyingStrafeTask extends MultiTickTask<MobEntity> {

	private boolean movingToLeft;
	private boolean backward;
	private boolean upward;
	private int targetSeeingTicker;
	private int combatTicks = -1;

	private final double speed;
	private final float squaredRange;
	private final double heightAboveTarget;
	private final boolean pathfindIfFaraway;

	public FlyingStrafeTask(double speed, float range, double heightAboveTarget, boolean pathfindIfFaraway) {
		super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_PRESENT));
		this.speed = speed;
		this.squaredRange = range * range;
		this.heightAboveTarget = heightAboveTarget;
		this.pathfindIfFaraway = pathfindIfFaraway;
	}

	@Override
	protected boolean shouldKeepRunning(ServerWorld serverWorld, MobEntity polly, long l) {
		return polly.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET);
	}

	public static Optional<FlightWithStrafeMoveControl> getControl(MobEntity living) {
		return living.getMoveControl() instanceof FlightWithStrafeMoveControl p ? Optional.of(p) : Optional.empty();
	}

	@Override
	protected void keepRunning(ServerWorld serverWorld, MobEntity mobEntity, long l) {
		mobEntity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
			double distance = mobEntity.squaredDistanceTo(target.getX(), mobEntity.getY(), target.getZ());
			boolean canSeeTarget = mobEntity.getVisibilityCache().canSee(target);
			if (canSeeTarget) {
				targetSeeingTicker++;
			} else {
				targetSeeingTicker = 0;
			}
			if (pathfindIfFaraway && (distance > this.squaredRange || this.targetSeeingTicker < 20)) {
				mobEntity.getNavigation().startMovingTo(target, this.speed);
				this.combatTicks = -1;
			} else {
				mobEntity.getNavigation().stop();
				++this.combatTicks;
			}


			if (mobEntity.getY() - target.getY() < heightAboveTarget) {
				upward = true;
			} else {
				upward = false;
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
				getControl(mobEntity).ifPresent(control -> {
					control.strafeTo(
							this.backward ? -1 : 1,
							this.movingToLeft ? 1 : -1,
							this.upward ? 1 : -1,
							0.9f
					);
				});
				mobEntity.lookAtEntity(target, 30.0f, 30.0f);
			} else {
				mobEntity.getLookControl().lookAt(target, 30.0f, 30.0f);
			}
		});

	}
}
