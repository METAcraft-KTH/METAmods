package se.datasektionen.mc.metacraft_core;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.advancement.criterion.RecipeUnlockedCriterion;
import net.minecraft.advancement.criterion.TickCriterion;
import net.minecraft.data.recipe.RecipeExporter;
import net.minecraft.data.recipe.RecipeGenerator;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_core.item.METAcraftItems;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class METAcraftCoreDatagen implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(Recipes::new);
	}

	public static class Recipes extends FabricRecipeProvider {

		public Recipes(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
			return new RecipeGenerator(wrapperLookup, recipeExporter) {
				@Override
				public void generate() {
					var wrench = RegistryKey.of(
							RegistryKeys.RECIPE,
							Identifier.of(METAcraftCore.MODID, "wrench")
					);
					Advancement.Builder builder = recipeExporter.getAdvancementBuilder().criterion(
							"has_the_recipe", RecipeUnlockedCriterion.create(wrench)
					).rewards(AdvancementRewards.Builder.recipe(wrench)).criteriaMerger(
							AdvancementRequirements.CriterionMerger.OR
					);
					builder.criterion(
							"trigger_always",
							Criteria.TICK.create(new TickCriterion.Conditions(
									Optional.empty()
							))
					);
					recipeExporter.accept(
							wrench,
							new ShapedRecipe(
									"misc",
									CraftingRecipeCategory.EQUIPMENT,
									RawShapedRecipe.create(
											Map.of(
													'S', Ingredient.ofItem(Items.STICK)
											),
											"S S",
											" S ",
											" S "
									),
									METAcraftItems.WRENCH.getDefaultStack()
							),
							builder.build(wrench.getValue().withPrefixedPath("recipes/" + RecipeCategory.TOOLS.getName() + "/"))
					);
				}
			};
		}

		@Override
		public String getName() {
			return "metacraft-core";
		}
	}
}
