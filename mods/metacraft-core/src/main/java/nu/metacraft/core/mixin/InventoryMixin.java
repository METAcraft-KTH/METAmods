package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.core.item.ItemModifiers;
import nu.metacraft.core.item.components.METAcraftComponents;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

	@Shadow @Final public Player player;

	@Shadow public abstract void setItem(int slot, ItemStack stack);

	@ModifyExpressionValue(
		method = "dropAll",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"
		)
	)
	public boolean dropAll(boolean original, @Local ItemStack itemStack) {
		if (itemStack.has(METAcraftComponents.SOULBOUND)) return true;
		return original;
	}

	@ModifyExpressionValue(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"
		)
	)
	public ItemStack updateItems(ItemStack original, @Local int i) {
		return ItemModifiers.modifyTick(
				original, player.getRandom()
		).map(result -> {
			setItem(i, result);
			return result;
		}).orElse(original);
	}

}
