package nu.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(Inventory.class)
public class MixinPlayerInventory {

	@Shadow @Final public Player player;

	@ModifyExpressionValue(
		method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;copyAndClear()Lnet/minecraft/world/item/ItemStack;"
		)
	)
	public ItemStack insertStack(ItemStack original) {
		PortableJukeboxEntity.transferToEntityFromUnknown(original, player);
		return original;
	}

	@Inject(
			method = "addResource(ILnet/minecraft/world/item/ItemStack;)I",
			at = @At("RETURN")
	)
	public void addStack(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir, @Local(ordinal = 1) ItemStack insertedStack) {
		PortableJukeboxEntity.transferToEntityFromUnknown(insertedStack, player);
	}

}
