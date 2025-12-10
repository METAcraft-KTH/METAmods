package nu.metacraft.core;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import nu.metacraft.core.item.METAcraftItems;

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

		public Recipes(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected RecipeProvider createRecipeProvider(HolderLookup.Provider wrapperLookup, RecipeOutput recipeExporter) {
			return new RecipeProvider(wrapperLookup, recipeExporter) {
				@Override
				public void buildRecipes() {
					var wrench = ResourceKey.create(
							Registries.RECIPE,
							Identifier.fromNamespaceAndPath(METAcraftCore.MODID, "wrench")
					);
					Advancement.Builder builder = recipeExporter.advancement().addCriterion(
							"has_the_recipe", RecipeUnlockedTrigger.unlocked(wrench)
					).rewards(AdvancementRewards.Builder.recipe(wrench)).requirements(
							AdvancementRequirements.Strategy.OR
					);
					builder.addCriterion(
							"trigger_always",
							CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(
									Optional.empty()
							))
					);
					recipeExporter.accept(
							wrench,
							new ShapedRecipe(
									"misc",
									CraftingBookCategory.EQUIPMENT,
									ShapedRecipePattern.of(
											Map.of(
													'S', Ingredient.of(Items.STICK)
											),
											"S S",
											" S ",
											" S "
									),
									METAcraftItems.WRENCH.getDefaultInstance()
							),
							builder.build(wrench.identifier().withPrefix("recipes/" + RecipeCategory.TOOLS.getFolderName() + "/"))
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
