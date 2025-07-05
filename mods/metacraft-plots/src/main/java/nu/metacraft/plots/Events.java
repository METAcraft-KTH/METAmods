package nu.metacraft.plots;

import net.minecraft.recipe.CraftingRecipe;
import nu.metacraft.lib.event.RecipeLoad;
import nu.metacraft.plots.recipes.AuthoriseSecondaryKeyRecipe;

public class Events {

	public static void init() {
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (id.getValue().equals(METAcraftPlots.getID("authorise_secondary_key")) && recipe instanceof CraftingRecipe crafting) {
				return new AuthoriseSecondaryKeyRecipe(crafting);
			}
			return recipe;
		});
	}

}
