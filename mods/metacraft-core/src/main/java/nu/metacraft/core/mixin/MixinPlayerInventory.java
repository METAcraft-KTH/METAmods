package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.core.item.ItemModifiers;
import nu.metacraft.core.item.components.METAcraftComponents;

@Mixin(PlayerInventory.class)
public abstract class MixinPlayerInventory {

	@Shadow @Final public PlayerEntity player;

	@Shadow public abstract void setStack(int slot, ItemStack stack);

	@ModifyExpressionValue(
		method = "dropAll",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;isEmpty()Z"
		)
	)
	public boolean dropAll(boolean original, @Local ItemStack itemStack) {
		if (itemStack.contains(METAcraftComponents.SOULBOUND)) return true;
		return original;
	}

	@ModifyExpressionValue(
		method = "updateItems",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/player/PlayerInventory;getStack(I)Lnet/minecraft/item/ItemStack;"
		)
	)
	public ItemStack updateItems(ItemStack original, @Local int i) {
		return ItemModifiers.modifyTick(
				original, player.getRandom()
		).map(result -> {
			setStack(i, result);
			return result;
		}).orElse(original);
	}

}
