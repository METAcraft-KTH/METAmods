package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.DefaultMinecartController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.EntityExtensions;

@Mixin(DefaultMinecartController.class)
public class MixinDefaultMinecartController {

	@ModifyExpressionValue(
		method = "handleCollision",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;hasVehicle()Z"
		)
	)
	public boolean shouldNotMount(boolean original, @Local Entity entity) {
		return original || ((EntityExtensions) entity).metacraft_lib$preventEnterVehicle();
	}

}
