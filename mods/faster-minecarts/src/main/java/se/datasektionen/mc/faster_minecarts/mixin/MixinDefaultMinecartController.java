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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;
import se.datasektionen.mc.faster_minecarts.FasterMinecartsConfig;
import se.datasektionen.mc.faster_minecarts.FasterMinecartsHelper;
import se.datasektionen.mc.faster_minecarts.MinecartData;

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
	private MinecartData getData() {
		return (MinecartData) minecart;
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

				Vec3d facing = currentVelocity.normalize();
				Vec3d left = facing.rotateY((float) Math.PI / 2);
				double halfWidth = minecart.getWidth()/2;
				Vec3d boxStart = this.getPos().add(facing.multiply(halfWidth));
				Box ahead = new Box(boxStart.add(left.multiply(-halfWidth)), boxStart.add(facing.multiply(currentVelocity.horizontalLength())).add(left.multiply(halfWidth)).add(0, minecart.getHeight(),0));
				FasterMinecarts.damageEntitiesFromCart(minecart, prevVelocity, ahead);


				((MinecartData) minecart).fasterMinecarts$setCurrentRailPosOverride(railPos);
				moveOnRail.call(instance, world);
				((MinecartData) minecart).fasterMinecarts$setCurrentRailPosOverride(null);

				double newSpeed = this.getVelocity().horizontalLength();
				if (newSpeed == 0) {
					break;
				}

				double distance = this.getPos().distanceTo(prevPos);

				double acceleration = (Math.pow(newSpeed, 2) - Math.pow(currentVelocity.horizontalLength(), 2)) / (2 * distance);
				double time = distance / prevVelocity;
				prevVelocity += acceleration * time;

				Vec3d v = this.getVelocity();
				((MinecartData) minecart).fasterMinecarts$applySlowdown(this.getVelocity());
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

}
