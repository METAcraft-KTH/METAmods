package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.RecipeComponentCarryoverExtension;
import nu.metacraft.lib.extensions.RecipeRemainderExtension;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;

@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeMixin implements RecipeComponentCarryoverExtension, RecipeRemainderExtension {

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
			method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("RETURN")
	)
	public ItemStack onCraft(ItemStack original, CraftingInput recipeInputInventory) {
		metacraft_lib$onCraft(original, recipeInputInventory != null ? recipeInputInventory.items() : List.of());
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
