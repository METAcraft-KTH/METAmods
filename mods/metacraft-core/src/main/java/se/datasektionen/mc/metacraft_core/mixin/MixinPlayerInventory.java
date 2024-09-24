package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;

@Mixin(PlayerInventory.class)
public class MixinPlayerInventory {

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

}
