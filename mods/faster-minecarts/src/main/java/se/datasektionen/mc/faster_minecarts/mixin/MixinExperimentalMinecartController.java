package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import net.minecraft.entity.vehicle.MinecartController;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import se.datasektionen.mc.faster_minecarts.*;

@Mixin(ExperimentalMinecartController.class)
public abstract class MixinExperimentalMinecartController extends MinecartController {

	protected MixinExperimentalMinecartController(AbstractMinecartEntity minecart) {
		super(minecart);
	}

	@ModifyReturnValue(method = "getMaxSpeed", at = @At("RETURN"))
	protected double modifyMaxSpeed(double speed) {
		return FasterMinecartsHelper.getActualMaxSpeed(minecart, speed);
	}

	@Unique
	private MinecartExtensions getData() {
		return (MinecartExtensions) minecart;
	}

	@ModifyExpressionValue(
		method = "accelerateFromPoweredRail",
			at = @At(
					value = "CONSTANT",
					args = "doubleValue=0.06"
			)
	)
	private double accelerateFromPoweredRail(double acceleration) {
		return getData().fasterMinecarts$getAcceleration().orElse(
				FasterMinecartsHelper.getValue(minecart, acceleration, FasterMinecartsConfig.getPoweredRailAccelerationFactor())
		);
	}

	@ModifyArg(
		method = "moveAlongTrack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"
		),
		index = 1
	)
	public Vec3d moveAlongTrack(
			Vec3d movement
	) {
		FasterMinecarts.damageEntitiesFromCart(minecart, this.getVelocity().length(), movement);
		return movement;
	}

	@ModifyExpressionValue(
			method = "getSpeedRetention",
			at = @At(
					value = "CONSTANT",
					args = "doubleValue=0.975"
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
