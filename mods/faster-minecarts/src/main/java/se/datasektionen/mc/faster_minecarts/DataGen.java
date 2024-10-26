package se.datasektionen.mc.faster_minecarts;

import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.criterion.RecipeUnlockedCriterion;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public class DataGen implements DataGeneratorEntrypoint {

	private void acceptRecipe(
			RecipeExporter exporter, RegistryKey<Recipe<?>> recipeID, Recipe<?> recipe, RecipeCategory category,
			Map<String, AdvancementCriterion<?>> criteria
	) {
		var builder = exporter.getAdvancementBuilder().criterion(
				"has_the_recipe", RecipeUnlockedCriterion.create(recipeID)
		).rewards(AdvancementRewards.Builder.recipe(recipeID)).criteriaMerger(
				AdvancementRequirements.CriterionMerger.OR
		);
		criteria.forEach(builder::criterion);
		exporter.accept(recipeID, recipe, builder.build(recipeID.getValue().withPrefixedPath("recipes/" + category.getName() + "/")));
	}

	private Identifier getIDFromItem(Item item) {
		return item.getRegistryEntry().getKey().orElseThrow().getValue();
	}

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		fabricDataGenerator.createPack().addProvider(
				(output, lookup) -> new FabricRecipeProvider(output, lookup) {
					@Override
					protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup lookup, RecipeExporter exporter) {
						return new RecipeGenerator(lookup, exporter) {
							@Override
							public void generate() {
								List<Item> minecarts = ImmutableList.of(
										Items.MINECART,
										Items.CHEST_MINECART,
										Items.HOPPER_MINECART,
										Items.TNT_MINECART,
										Items.COMMAND_BLOCK_MINECART
								);
							}
						};
					}

					@Override
					public String getName() {
						return "Minecarts";
					}
				}
		);
	}
}
