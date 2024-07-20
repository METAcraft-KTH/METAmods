package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.extensions.EntityExtensions;

@Mixin(AbstractMinecartEntity.class)
public class MixinAbstractMinecartEntity {
	@WrapWithCondition(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;startRiding(Lnet/minecraft/entity/Entity;)Z"
		)
	)
	public boolean ride(Entity entity, Entity boat) {
		return !((EntityExtensions) entity).metacraft_lib$preventEnterVehicle();
	}
}
