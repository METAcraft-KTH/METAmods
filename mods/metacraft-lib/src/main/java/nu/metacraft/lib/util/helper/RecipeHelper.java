package nu.metacraft.lib.util.helper;

import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.extensions.RecipeComponentCarryoverExtension;
import nu.metacraft.lib.mixin.AccessorShapelessRecipe;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

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
					BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())
			);
		}
	}

	public static List<Ingredient> getIngredients(ShapelessRecipe recipe) {
		return ((AccessorShapelessRecipe) recipe).getIngredients();
	}

}
