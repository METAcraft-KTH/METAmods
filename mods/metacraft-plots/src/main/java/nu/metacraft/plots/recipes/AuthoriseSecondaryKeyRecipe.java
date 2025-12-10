package nu.metacraft.plots.recipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.item.PlotKey;

public class AuthoriseSecondaryKeyRecipe extends ShapelessRecipe {
	public AuthoriseSecondaryKeyRecipe(CraftingRecipe recipe) {
		super(
				recipe.group(), recipe.category(),
				recipe.assemble(null, null),
				recipe.placementInfo().ingredients()
		);
	}

	@Override
	public ItemStack assemble(CraftingInput recipeInputInventory, HolderLookup.Provider lookup) {
		var result = ItemStack.EMPTY;
		String prevPlot = null;
		String prevPlotFriendlyName = null;
		for (var stack : recipeInputInventory.items()) {
			if (stack.is(PlotItems.PLOT_MASTER_KEY)) {
				result = PlotKey.createUninitialisedKeyFromMasterKey(stack);
			}
			if (stack.is(PlotItems.PLOT_KEY)) {
				prevPlot = PlotKey.getPlot(stack);
				prevPlotFriendlyName = PlotKey.getFriendlyName(stack);
			}
		}
		if (result.isEmpty()) {
			result = super.assemble(recipeInputInventory, lookup);
		}
		if (prevPlot != null && prevPlotFriendlyName != null) {
			PlotKey.addKeyToRevoke(result, prevPlotFriendlyName, prevPlot);
		}
		return result;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput inventory) {
		NonNullList<ItemStack> remainders = NonNullList.withSize(inventory.size(), ItemStack.EMPTY);
		for (int i = 0; i < inventory.size(); i++) {
			if (inventory.getItem(i).is(PlotItems.PLOT_MASTER_KEY)) {
				remainders.set(i, inventory.getItem(i).copy());
				break;
			}
		}
		return remainders;
	}
}
