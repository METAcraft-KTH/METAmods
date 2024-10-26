package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.extensions.RecipeComponentCarryoverExtension;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ShapedRecipe.class)
public abstract class MixinShapedRecipe implements RecipeComponentCarryoverExtension {

	@Unique
	private Predicate<ItemStack> checker;

	@Override
	public void metacraft_lib$setComponentCarryOver(Predicate<ItemStack> checker) {
		this.checker = checker;
	}

	@Override
	public Predicate<ItemStack> metacraft_lib$getComponentCarryOver() {
		return checker;
	}

	@ModifyReturnValue(
			method = "craft(Lnet/minecraft/recipe/input/CraftingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",
			at = @At("RETURN")
	)
	public ItemStack onCraft(ItemStack original, CraftingRecipeInput recipeInputInventory) {
		metacraft_lib$onCraft(original, recipeInputInventory != null ? recipeInputInventory.getStacks() : List.of());
		return original;
	}
}
