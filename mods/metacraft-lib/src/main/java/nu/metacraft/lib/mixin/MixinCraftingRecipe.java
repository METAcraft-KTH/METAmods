package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.RecipeRemainderExtension;

@Mixin(CraftingRecipe.class)
public interface MixinCraftingRecipe {

	@ModifyReturnValue(method = "getRecipeRemainders", at = @At("RETURN"))
	default DefaultedList<ItemStack> getRecipeRemainders(DefaultedList<ItemStack> inv, CraftingRecipeInput input) {
		if (this instanceof RecipeRemainderExtension remainder) {
			var func = remainder.metacraft_lib$getRemainderFunction();
			if (func != null) {
				for (int i = 0; i < inv.size(); ++i) {
					inv.set(i, func.apply(input.getStackInSlot(i)));
				}
			}
		}
		return inv;
	}

}
