package nu.metacraft.lib.util.helper;

import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.extensions.RecipeComponentCarryoverExtension;
import nu.metacraft.lib.mixin.NormalCraftingRecipeAccessor;
import nu.metacraft.lib.mixin.ShapelessRecipeAccessor;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public class RecipeHelper {

	public static void addComponentCarryover(
			Recipe<?> recipe, Predicate<ItemInstance> checker
	) {
		addComponentCarryover(recipe, checker, false);
	}

	public static void addComponentCarryover(
			Recipe<?> recipe, Predicate<ItemInstance> checker, boolean skipError
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
		return ((ShapelessRecipeAccessor) recipe).getIngredients();
	}

	public static ItemStackTemplate getResult(ShapelessRecipe recipe) {
		return ((ShapelessRecipeAccessor) recipe).getResult();
	}

	public static Recipe.CommonInfo getCommonInfo(NormalCraftingRecipe recipe) {
		return ((NormalCraftingRecipeAccessor) recipe).getCommonInfo();
	}

	public static CraftingRecipe.CraftingBookInfo getBookInfo(NormalCraftingRecipe recipe) {
		return ((NormalCraftingRecipeAccessor) recipe).getBookInfo();
	}

}
