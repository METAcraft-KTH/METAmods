package se.datasektionen.mc.metacraft_plots.recipes;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import se.datasektionen.mc.metacraft_plots.item.PlotItems;
import se.datasektionen.mc.metacraft_plots.item.PlotKey;

public class AuthoriseSecondaryKeyRecipe extends ShapelessRecipe {
	public AuthoriseSecondaryKeyRecipe(CraftingRecipe recipe) {
		super(
				recipe.getGroup(), recipe.getCategory(),
				recipe.craft(null, null),
				recipe.getIngredientPlacement().getIngredients()
		);
	}

	@Override
	public ItemStack craft(CraftingRecipeInput recipeInputInventory, RegistryWrapper.WrapperLookup lookup) {
		var result = ItemStack.EMPTY;
		String prevPlot = null;
		String prevPlotFriendlyName = null;
		for (var stack : recipeInputInventory.getStacks()) {
			if (stack.isOf(PlotItems.PLOT_MASTER_KEY)) {
				result = PlotKey.createUninitialisedKeyFromMasterKey(stack);
			}
			if (stack.isOf(PlotItems.PLOT_KEY)) {
				prevPlot = PlotKey.getPlot(stack);
				prevPlotFriendlyName = PlotKey.getFriendlyName(stack);
			}
		}
		if (result.isEmpty()) {
			result = super.craft(recipeInputInventory, lookup);
		}
		if (prevPlot != null && prevPlotFriendlyName != null) {
			PlotKey.addKeyToRevoke(result, prevPlotFriendlyName, prevPlot);
		}
		return result;
	}

	@Override
	public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput inventory) {
		DefaultedList<ItemStack> remainders = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
		for (int i = 0; i < inventory.size(); i++) {
			if (inventory.getStackInSlot(i).isOf(PlotItems.PLOT_MASTER_KEY)) {
				remainders.set(i, inventory.getStackInSlot(i).copy());
				break;
			}
		}
		return remainders;
	}
}
