package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.RecipeComponentCarryoverExtension;
import nu.metacraft.lib.extensions.RecipeRemainderExtension;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@Mixin(ShapelessRecipe.class)
public abstract class MixinShapelessRecipe implements RecipeComponentCarryoverExtension, RecipeRemainderExtension {

	@Unique
	private Predicate<ItemStack> checker;

	@Unique
	private UnaryOperator<ItemStack> remainderFunction;

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


	@Override
	public UnaryOperator<ItemStack> metacraft_lib$getRemainderFunction() {
		return remainderFunction;
	}

	@Override
	public void metacraft_lib$setRemainderFunction(UnaryOperator<ItemStack> remainderFunction) {
		this.remainderFunction = remainderFunction;
	}
}
