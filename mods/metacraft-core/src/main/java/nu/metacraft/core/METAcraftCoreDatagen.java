package nu.metacraft.core;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import nu.metacraft.core.item.METAcraftItems;
import org.jspecify.annotations.NonNull;

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

		public Recipes(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected @NonNull RecipeProvider createRecipeProvider(
				HolderLookup.@NonNull Provider wrapperLookup, @NonNull BootstrapContext<Recipe<?>> recipes,
				@NonNull BootstrapContext<Advancement> advancements
		) {
			return new RecipeProvider(recipes, advancements) {
				@Override
				public void buildRecipes() {
					var wrench = ResourceKey.create(
							Registries.RECIPE,
							Identifier.fromNamespaceAndPath(METAcraftCore.MODID, "wrench")
					);
					Advancement.Builder builder = output.advancement().addCriterion(
							"has_the_recipe", RecipeUnlockedTrigger.unlocked(output.lookup(Registries.RECIPE).getOrThrow(wrench))
					).rewards(AdvancementRewards.Builder.recipe(wrench)).requirements(
							AdvancementRequirements.Strategy.OR
					);
					builder.addCriterion(
							"trigger_always",
							CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(
									Optional.empty()
							))
					);
					output.accept(
							wrench,
							new ShapedRecipe(
									new Recipe.CommonInfo(true),
									new CraftingRecipe.CraftingBookInfo(
											CraftingBookCategory.EQUIPMENT,"misc"
									),
									ShapedRecipePattern.of(
											Map.of(
													'S', Ingredient.of(Items.STICK)
											),
											"S S",
											" S ",
											" S "
									),
									new ItemStackTemplate(METAcraftItems.WRENCH)
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
