package nu.metacraft.lib.util.helper;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.Registries;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.extensions.RecipeComponentCarryoverExtension;
import nu.metacraft.lib.mixin.AccessorShapelessRecipe;

import java.util.List;
import java.util.function.Predicate;

public class RecipeHelper {

	public static void addComponentCarryover(
			Recipe<?> recipe, Predicate<ItemStack> checker
	) {
		addComponentCarryover(recipe, checker, false);
	}

	public static void addComponentCarryover(
			Recipe<?> recipe, Predicate<ItemStack> checker, boolean skipError
	) {
		if (recipe instanceof RecipeComponentCarryoverExtension extension) {
			extension.metacraft_lib$setComponentCarryOver(checker);
		} else if (!skipError) {
			METAcraftLib.LOGGER.error(
					"Recipe type {} does not support component carryover!",
					Registries.RECIPE_TYPE.getId(recipe.getType())
			);
		}
	}

	public static List<Ingredient> getIngredients(ShapelessRecipe recipe) {
		return ((AccessorShapelessRecipe) recipe).getIngredients();
	}

}
