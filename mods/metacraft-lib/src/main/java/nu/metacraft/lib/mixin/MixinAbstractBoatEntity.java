package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.EntityExtensions;

@Mixin(AbstractBoatEntity.class)
public class MixinAbstractBoatEntity {

	@ModifyExpressionValue(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/vehicle/AbstractBoatEntity;isSmallerThanBoat(Lnet/minecraft/entity/Entity;)Z"
		)
	)
	public boolean shouldMount(boolean original, @Local Entity entity) {
		return original && !((EntityExtensions) entity).metacraft_lib$preventEnterVehicle();
	}

}
