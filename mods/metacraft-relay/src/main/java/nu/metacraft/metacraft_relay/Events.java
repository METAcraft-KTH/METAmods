package nu.metacraft.metacraft_relay;

import net.minecraft.recipe.ShapelessRecipe;
import nu.metacraft.metacraft_relay.recipe.RelayProgramRecipe;
import se.datasektionen.mc.metacraft_lib.event.RecipeLoad;

public class Events {

	public static void init() {
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (id == RelayDatagen.RELAY_PROGRAM && recipe instanceof ShapelessRecipe crafting) {
				return new RelayProgramRecipe(crafting);
			}
			return recipe;
		});
	}

}
