package se.metacraft.plots;

import net.minecraft.world.item.crafting.ShapelessRecipe;
import se.metacraft.lib.event.RecipeLoad;
import se.metacraft.plots.recipes.AuthoriseSecondaryKeyRecipe;

public class Events {

	public static void init() {
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (id.identifier().equals(METAcraftPlots.getID("authorise_secondary_key")) && recipe instanceof ShapelessRecipe crafting) {
				return new AuthoriseSecondaryKeyRecipe(crafting);
			}
			return recipe;
		});
	}

}
