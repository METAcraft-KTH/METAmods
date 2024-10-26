package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import net.minecraft.entity.vehicle.MinecartController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.faster_minecarts.FasterMinecartsHelper;

@Mixin(ExperimentalMinecartController.class)
public abstract class MixinExperimentalMinecartController extends MinecartController {

	protected MixinExperimentalMinecartController(AbstractMinecartEntity minecart) {
		super(minecart);
	}

	@ModifyReturnValue(method = "getMaxSpeed", at = @At("RETURN"))
	protected double modifyMaxSpeed(double speed) {
		return FasterMinecartsHelper.getActualMaxSpeed(minecart, speed);
	}
}
