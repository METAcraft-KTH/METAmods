package nu.metacraft.faster_minecarts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.faster_minecarts.MinecartExtensions;

@Mixin(VehicleEntity.class)
public class VehicleEntityMixin {

	@ModifyVariable(
			method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/Item;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
			),
			name = "itemStack"
	)
	public ItemStack dropItems(ItemStack stack) {
		if ((Object) this instanceof MinecartExtensions minecart) {
			return minecart.fasterMinecarts$getMinecartItem().orElse(stack);
		}
		return stack;
	}

}
