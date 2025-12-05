package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.faster_minecarts.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {

	protected NewMinecartBehaviorMixin(AbstractMinecart minecart) {
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
		method = "calculateBoostTrackSpeed",
			at = @At(
					value = "CONSTANT",
					args = "doubleValue=0.06"
			)
	)
	private double accelerateFromPoweredRail(double acceleration) {
		return getData().fasterMinecarts$getAcceleration().orElse(
				FasterMinecartsHelper.getValue(minecart, acceleration, FasterMinecartsConfig.MinecartModifier::poweredRailAccelerationFactor)
		);
	}

	@ModifyArg(
		method = "stepAlongTrack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
		),
		index = 1
	)
	public Vec3 moveAlongTrack(
			Vec3 movement
	) {
		FasterMinecarts.damageEntitiesFromCart(minecart, this.getDeltaMovement().length(), movement);
		return movement;
	}

	@ModifyExpressionValue(
			method = "getSlowdownFactor",
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
