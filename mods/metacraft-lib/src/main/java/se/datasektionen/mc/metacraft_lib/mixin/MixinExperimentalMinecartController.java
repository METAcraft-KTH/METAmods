package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.extensions.EntityExtensions;

@Mixin(ExperimentalMinecartController.class)
public class MixinExperimentalMinecartController {

	@ModifyExpressionValue(
			method = {
					"pickUpEntities",
					"pushAwayFromEntities"
			},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;hasVehicle()Z"
			)
	)
	public boolean shouldNotMount(boolean original, @Local Entity entity) {
		return original || ((EntityExtensions) entity).metacraft_lib$preventEnterVehicle();
	}

}
