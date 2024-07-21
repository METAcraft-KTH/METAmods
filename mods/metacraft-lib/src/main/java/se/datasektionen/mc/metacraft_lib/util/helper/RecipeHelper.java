package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.extensions.RecipeComponentCarryoverExtension;

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

}
