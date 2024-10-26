package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;
import se.datasektionen.mc.faster_minecarts.MinecartData;

@Mixin(VehicleEntity.class)
public class MixinVehicleEntity {

	@Inject(
			method = "killAndDropItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/vehicle/VehicleEntity;dropStack(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/entity/ItemEntity;"
			)
	)
	public void dropItems(ServerWorld world, Item item, CallbackInfo ci, @Local ItemStack itemStack) {
		if ((Object) this instanceof MinecartData minecart && minecart.fasterMinecarts$isSuperFast()) {
			FasterMinecarts.makeSuperFastMinecartItem(
					itemStack, minecart.fasterMinecarts$getAcceleration(), minecart.fasterMinecarts$getMaxSpeed(),
					minecart.fasterMinecarts$getMaxSpeedUnderwater(), minecart.fasterMinecarts$getCraftingTag(),
					minecart.fasterMinecarts$getItemName()
			);
		}
	}

}
