package nu.metacraft.season_5;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import nu.metacraft.season_5.items.Season5Items;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class METAcraftS5Datagen implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(Recipes::new);
	}

	public static class Recipes extends FabricRecipeProvider {

		public Recipes(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
			return new RecipeProvider(recipes, advancements) {
				@Override
				public void buildRecipes() {
					var wrench = ResourceKey.create(
							Registries.RECIPE,
							METAcraftSeason5.getID("bedrock_drill")
					);
					Advancement.Builder builder = output.advancement().addCriterion(
							"has_the_recipe", RecipeUnlockedTrigger.unlocked(output.lookup(Registries.RECIPE).getOrThrow(wrench))
					).rewards(AdvancementRewards.Builder.recipe(wrench)).requirements(
							AdvancementRequirements.Strategy.OR
					);
					builder.addCriterion(
							"trigger_above_roof",
							CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(
									Optional.of(
											Holder.direct(
													new LocationCheck(
															Optional.of(
																	LocationPredicate.Builder.inDimension(Level.NETHER).setY(
																			MinMaxBounds.Doubles.atLeast(128)
																	).build()
															),
															BlockPos.ZERO
													)
											)
									)
							))
					);
					output.accept(
							wrench,
							new ShapedRecipe(
									new Recipe.CommonInfo(true),
									new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.EQUIPMENT, "misc"),
									ShapedRecipePattern.of(
											Map.of(
													'E', Ingredient.of(Items.END_CRYSTAL),
													'O', Ingredient.of(Items.OBSIDIAN),
													'P', Ingredient.of(Items.PISTON)
											),
											"E",
											"P",
											"O"
									),
									new ItemStackTemplate(Season5Items.BEDROCK_DRILL)
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
