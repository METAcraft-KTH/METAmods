package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.faster_minecarts.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OldMinecartBehavior.class)
public abstract class OldMinecartBehaviorMixin extends MinecartBehavior {

	protected OldMinecartBehaviorMixin(AbstractMinecart minecart) {
		super(minecart);
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
	public boolean activatorRail(
			boolean isOfBlock, @Share("shouldUseActivatorRail") LocalBooleanRef shouldUseActivatorRail
	) {
		if (shouldUseActivatorRail.get()) {
			return isOfBlock;
		} else {
			return false;
		}
	}

	@Unique
	private MinecartExtensions getData() {
		return (MinecartExtensions) minecart;
	}

	@ModifyExpressionValue(
		method = "moveAlongTrack",
		at = @At(
			value = "CONSTANT",
			args = "doubleValue=0.06"
		)
	)
	public double changePoweredRailAcceleration(double acceleration) {
		return getData().fasterMinecarts$getAcceleration().orElse(
				FasterMinecartsHelper.getValue(minecart, acceleration, FasterMinecartsConfig.MinecartModifier::poweredRailAccelerationFactor)
		);
	}

	@Inject(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/Mth;atan2(DD)D"
		)
	)
	public void fixYaw(CallbackInfo ci) {
		((MinecartExtensions) minecart).fasterMinecarts$setYawFixed();
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;moveAlongTrack(Lnet/minecraft/server/level/ServerLevel;)V"))
	public void moveMinecartsSeveralTimesPerTick(
			OldMinecartBehavior instance, ServerLevel world, Operation<Void> moveOnRail,
			@Share("shouldUseActivatorRail") LocalBooleanRef shouldUseActivatorRail
	) {
		shouldUseActivatorRail.set(true);
		final double maxSpeed = 0.4;
		if (this.getDeltaMovement().horizontalDistance() > maxSpeed && FasterMinecartsHelper.hasSuperSpeed(minecart)) {
			shouldUseActivatorRail.set(false);
			double prevVelocity = this.getDeltaMovement().horizontalDistance();

			Vec3 currentVelocity = this.getDeltaMovement().normalize().scale(maxSpeed);

			double distanceMoved = 0;

			while (prevVelocity > distanceMoved) {
				Vec3 prevPos = this.position();
				if (currentVelocity.horizontalDistance() < 0.01) {
					break;
				}
				this.setDeltaMovement(currentVelocity);
				BlockPos railPos = minecart.blockPosition();
				BlockState railState = this.level().getBlockState(railPos);
				if (!(railState.getBlock() instanceof BaseRailBlock)) {
					railPos = railPos.above();
					railState = this.level().getBlockState(railPos);
					if (!(railState.getBlock() instanceof BaseRailBlock)) {
						railPos = railPos.below(2);
						railState = this.level().getBlockState(railPos);
						if (!(railState.getBlock() instanceof BaseRailBlock)) {
							this.setDeltaMovement(this.getDeltaMovement().normalize().scale(prevVelocity));
							break;
						}
					}
				}

				FasterMinecarts.damageEntitiesFromCart(minecart, prevVelocity, currentVelocity);

				((MinecartExtensions) minecart).fasterMinecarts$setCurrentRailPosOverride(railPos);
				moveOnRail.call(instance, world);
				((MinecartExtensions) minecart).fasterMinecarts$setCurrentRailPosOverride(null);

				double newSpeed = this.getDeltaMovement().horizontalDistance();
				if (newSpeed == 0) {
					break;
				}

				double distance = this.position().distanceTo(prevPos);

				double acceleration = (Math.pow(newSpeed, 2) - Math.pow(currentVelocity.horizontalDistance(), 2)) / (2 * distance);
				double time = distance / prevVelocity;
				prevVelocity += acceleration * time;

				Vec3 v = this.getDeltaMovement();
				((MinecartExtensions) minecart).fasterMinecarts$applySlowdown(this.getDeltaMovement());
				prevVelocity *= this.getDeltaMovement().length() / v.length();
				this.setDeltaMovement(v);


				if (prevVelocity > this.getMaxSpeed(world)*20) {
					prevVelocity = this.getMaxSpeed(world)*20;
				}
				distanceMoved += distance;
				if (newSpeed > maxSpeed) {
					currentVelocity = this.getDeltaMovement().normalize().scale(Math.min(maxSpeed, Math.abs(prevVelocity - distanceMoved)));
				} else {
					currentVelocity = this.getDeltaMovement().normalize().scale(newSpeed);
				}

				if (railState.is(Blocks.ACTIVATOR_RAIL)) {
					minecart.activateMinecart(world, railPos.getX(), railPos.getY(), railPos.getZ(), railState.getValue(PoweredRailBlock.POWERED));
				}
			}

			this.setDeltaMovement(this.getDeltaMovement().normalize().scale(prevVelocity));

		} else {
			moveOnRail.call(instance, world);
		}
	}

	@ModifyExpressionValue(
		method = "moveAlongTrack",
		at = {
				@At(
						value = "CONSTANT",
						args = "doubleValue=0.75",
						ordinal = 0
				),
				@At(
						value = "CONSTANT",
						args = "doubleValue=1.0",
						ordinal = 0
				)
		}
	)
	public double increaseTopSpeedFactor(double value) {
		if (value == 0.75) {
			return FasterMinecartsHelper.getValue(minecart, value, FasterMinecartsConfig.MinecartModifier::topSpeedFactor);
		} else if (value == 1.0) {
			return FasterMinecartsHelper.getValue(minecart, value, FasterMinecartsConfig.MinecartModifier::topSpeedFactor);
		}
		return value;
	}

	@ModifyReturnValue(method = "getMaxSpeed", at = @At("RETURN"))
	protected double modifyMaxSpeed(double speed) {
		return FasterMinecartsHelper.getActualMaxSpeed(minecart, speed);
	}


	@ModifyExpressionValue(
			method = "getSlowdownFactor",
			at = @At(
					value = "CONSTANT",
					args = "doubleValue=0.96"
			)
	)
	public double getSpeedRetention(double original) {
		return FasterMinecartsHelper.getMinecartItem(minecart).map(
				minecart -> minecart.get(MinecartComponents.SLOWDOWN)
		).orElse(original);
	}

	@ModifyExpressionValue(
		method = "getSlowdownFactor",
		at = @At(
			value = "CONSTANT",
				args = "doubleValue=0.997"
		)
	)
	public double getSpeedRetentionPassenger(double original) {
		return FasterMinecartsHelper.getMinecartItem(minecart).map(
				minecart -> minecart.get(MinecartComponents.SLOWDOWN_WITH_PASSENGER)
		).orElse(original);
	}

}
