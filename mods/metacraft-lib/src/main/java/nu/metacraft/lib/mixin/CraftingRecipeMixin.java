package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.RecipeRemainderExtension;

@Mixin(CraftingRecipe.class)
public interface CraftingRecipeMixin {

	@ModifyReturnValue(method = "getRemainingItems", at = @At("RETURN"))
	default NonNullList<ItemStack> getRecipeRemainders(NonNullList<ItemStack> inv, CraftingInput input) {
		if (this instanceof RecipeRemainderExtension remainder) {
			var func = remainder.metacraft_lib$getRemainderFunction();
			if (func != null) {
				for (int i = 0; i < inv.size(); ++i) {
					inv.set(i, func.apply(input.getItem(i)));
				}
			}
		}
		return inv;
	}

}
