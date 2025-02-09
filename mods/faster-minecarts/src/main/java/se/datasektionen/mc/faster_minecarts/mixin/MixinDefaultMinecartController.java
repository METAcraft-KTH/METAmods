package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.DefaultMinecartController;
import net.minecraft.entity.vehicle.MinecartController;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.faster_minecarts.*;

@Mixin(DefaultMinecartController.class)
public abstract class MixinDefaultMinecartController extends MinecartController {

	protected MixinDefaultMinecartController(AbstractMinecartEntity minecart) {
		super(minecart);
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z"))
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
		method = "moveOnRail",
		at = @At(
			value = "CONSTANT",
			args = "doubleValue=0.06"
		)
	)
	public double changePoweredRailAcceleration(double acceleration) {
		return getData().fasterMinecarts$getAcceleration().orElse(
				FasterMinecartsHelper.getValue(minecart, acceleration, FasterMinecartsConfig.getPoweredRailAccelerationFactor())
		);
	}

	@Inject(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/math/MathHelper;atan2(DD)D"
		)
	)
	public void fixYaw(CallbackInfo ci) {
		((MinecartExtensions) minecart).fasterMinecarts$setYawFixed();
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/DefaultMinecartController;moveOnRail(Lnet/minecraft/server/world/ServerWorld;)V"))
	public void moveMinecartsSeveralTimesPerTick(
			DefaultMinecartController instance, ServerWorld world, Operation<Void> moveOnRail,
			@Share("shouldUseActivatorRail") LocalBooleanRef shouldUseActivatorRail
	) {
		shouldUseActivatorRail.set(true);
		final double maxSpeed = 0.4;
		if (this.getVelocity().horizontalLength() > maxSpeed && FasterMinecartsHelper.hasSuperSpeed(minecart)) {
			shouldUseActivatorRail.set(false);
			double prevVelocity = this.getVelocity().horizontalLength();

			Vec3d currentVelocity = this.getVelocity().normalize().multiply(maxSpeed);

			double distanceMoved = 0;

			while (prevVelocity > distanceMoved) {
				Vec3d prevPos = this.getPos();
				if (currentVelocity.horizontalLength() < 0.01) {
					break;
				}
				this.setVelocity(currentVelocity);
				BlockPos railPos = minecart.getBlockPos();
				BlockState railState = this.getWorld().getBlockState(railPos);
				if (!(railState.getBlock() instanceof AbstractRailBlock)) {
					railPos = railPos.up();
					railState = this.getWorld().getBlockState(railPos);
					if (!(railState.getBlock() instanceof AbstractRailBlock)) {
						railPos = railPos.down(2);
						railState = this.getWorld().getBlockState(railPos);
						if (!(railState.getBlock() instanceof AbstractRailBlock)) {
							this.setVelocity(this.getVelocity().normalize().multiply(prevVelocity));
							break;
						}
					}
				}

				FasterMinecarts.damageEntitiesFromCart(minecart, prevVelocity, currentVelocity);

				((MinecartExtensions) minecart).fasterMinecarts$setCurrentRailPosOverride(railPos);
				moveOnRail.call(instance, world);
				((MinecartExtensions) minecart).fasterMinecarts$setCurrentRailPosOverride(null);

				double newSpeed = this.getVelocity().horizontalLength();
				if (newSpeed == 0) {
					break;
				}

				double distance = this.getPos().distanceTo(prevPos);

				double acceleration = (Math.pow(newSpeed, 2) - Math.pow(currentVelocity.horizontalLength(), 2)) / (2 * distance);
				double time = distance / prevVelocity;
				prevVelocity += acceleration * time;

				Vec3d v = this.getVelocity();
				((MinecartExtensions) minecart).fasterMinecarts$applySlowdown(this.getVelocity());
				prevVelocity *= this.getVelocity().length() / v.length();
				this.setVelocity(v);


				if (prevVelocity > this.getMaxSpeed(world)*20) {
					prevVelocity = this.getMaxSpeed(world)*20;
				}
				distanceMoved += distance;
				if (newSpeed > maxSpeed) {
					currentVelocity = this.getVelocity().normalize().multiply(Math.min(maxSpeed, Math.abs(prevVelocity - distanceMoved)));
				} else {
					currentVelocity = this.getVelocity().normalize().multiply(newSpeed);
				}

				if (railState.isOf(Blocks.ACTIVATOR_RAIL)) {
					minecart.onActivatorRail(railPos.getX(), railPos.getY(), railPos.getZ(), railState.get(PoweredRailBlock.POWERED));
				}
			}

			this.setVelocity(this.getVelocity().normalize().multiply(prevVelocity));

		} else {
			moveOnRail.call(instance, world);
		}
	}

	@ModifyExpressionValue(
		method = "moveOnRail",
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
			return FasterMinecartsHelper.getValue(minecart, value, FasterMinecartsConfig.getTopSpeedFactorNoPassenger());
		} else if (value == 1.0) {
			return FasterMinecartsHelper.getValue(minecart, value, FasterMinecartsConfig.getTopSpeedFactorWithPassenger());
		}
		return value;
	}

	@ModifyReturnValue(method = "getMaxSpeed", at = @At("RETURN"))
	protected double modifyMaxSpeed(double speed) {
		return FasterMinecartsHelper.getActualMaxSpeed(minecart, speed);
	}


	@ModifyExpressionValue(
			method = "getSpeedRetention",
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
		method = "getSpeedRetention",
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
