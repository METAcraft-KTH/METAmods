package se.datasektionen.mc.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(PlayerInventory.class)
public class MixinPlayerInventory {

	@Shadow @Final public PlayerEntity player;

	@ModifyExpressionValue(
		method = "insertStack(ILnet/minecraft/item/ItemStack;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;copyAndEmpty()Lnet/minecraft/item/ItemStack;"
		)
	)
	public ItemStack insertStack(ItemStack original) {
		PortableJukeboxEntity.transferToEntityFromUnknown(original, player);
		return original;
	}

	@Inject(
			method = "addStack(ILnet/minecraft/item/ItemStack;)I",
			at = @At("RETURN")
	)
	public void addStack(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir, @Local(ordinal = 1) ItemStack insertedStack) {
		PortableJukeboxEntity.transferToEntityFromUnknown(insertedStack, player);
	}

}
