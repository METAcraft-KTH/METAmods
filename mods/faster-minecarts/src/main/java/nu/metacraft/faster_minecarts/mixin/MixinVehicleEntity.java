package nu.metacraft.faster_minecarts.mixin;

import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nu.metacraft.faster_minecarts.MinecartExtensions;

@Mixin(VehicleEntity.class)
public class MixinVehicleEntity {

	@ModifyVariable(
			method = "killAndDropItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/ItemStack;set(Lnet/minecraft/component/ComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
			)
	)
	public ItemStack dropItems(ItemStack stack) {
		if ((Object) this instanceof MinecartExtensions minecart) {
			return minecart.fasterMinecarts$getMinecartItem().orElse(stack);
		}
		return stack;
	}

}
