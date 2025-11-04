package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.EntityExtensions;

@Mixin(AbstractBoat.class)
public class MixinAbstractBoatEntity {

	@ModifyExpressionValue(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/vehicle/AbstractBoat;hasEnoughSpaceFor(Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	public boolean shouldMount(boolean original, @Local Entity entity) {
		return original && !((EntityExtensions) entity).metacraft_lib$preventEnterVehicle();
	}

}
