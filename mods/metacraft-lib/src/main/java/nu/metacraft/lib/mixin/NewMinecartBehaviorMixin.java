package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.EntityExtensions;

@Mixin(NewMinecartBehavior.class)
public class NewMinecartBehaviorMixin {

	@ModifyExpressionValue(
			method = {
					"pickupEntities",
					"pushEntities"
			},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;isPassenger()Z"
			)
	)
	public boolean shouldNotMount(boolean original, @Local Entity entity) {
		return original || ((EntityExtensions) entity).metacraft_lib$preventEnterVehicle();
	}

}
