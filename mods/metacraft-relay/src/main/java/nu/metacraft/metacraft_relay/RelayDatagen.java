package nu.metacraft.metacraft_relay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.advancement.criterion.RecipeUnlockedCriterion;
import net.minecraft.block.Block;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.data.recipe.RecipeExporter;
import net.minecraft.data.recipe.RecipeGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.LootConditionConsumingBuilder;
import net.minecraft.loot.condition.SurvivesExplosionLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.CopyComponentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import nu.metacraft.metacraft_relay.blocks.RelayBlocks;
import nu.metacraft.metacraft_relay.blocks.block.RelayBlock;
import nu.metacraft.metacraft_relay.items.RelayItems;
import se.datasektionen.mc.metacraft_lib.event.RecipeDataGen;
import se.datasektionen.mc.metacraft_lib.recipe.CustomDisplayIngredient;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RelayDatagen implements DataGeneratorEntrypoint {

	public static final RegistryKey<Recipe<?>> RELAY_PROGRAM = RegistryKey.of(
			RegistryKeys.RECIPE,
			Identifier.of(Relay.MODID, "relay_program")
	);

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		RecipeDataGen.EVENT.register((key, json, recipe, registryLookup) -> {
			if (key == RELAY_PROGRAM) {
				{
					var itemList = new JsonArray();
					itemList.add("minecraft:compass");
					itemList.add("metacraft:relay");
					var map = new JsonObject();
					map.add("items", itemList);
					json.add(
							"metacraft:component_carryover", map
					);
				}
			}
			return json;
		});
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(Recipes::new);
		pack.addProvider(LootTableProvider::new);
		pack.addProvider(RelayBlockTagProvider::new);
		pack.addProvider(RelayItemTagProvider::new);
	}

	public static class RelayBlockTagProvider extends FabricTagProvider.BlockTagProvider {

		public RelayBlockTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
			valueLookupBuilder(BlockTags.PICKAXE_MINEABLE).add(RelayBlocks.RELAY);
		}
	}

	public static class RelayItemTagProvider extends FabricTagProvider.ItemTagProvider {

		public RelayItemTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
			valueLookupBuilder(RelayItems.RELAY_RECHARGE_ITEMS).add(Items.END_CRYSTAL);
		}
	}

	public static class LootTableProvider extends FabricBlockLootTableProvider {

		protected final Set<Item> explosionImmuneItems = Set.of(
				RelayItems.RELAY
		);

		protected LootTableProvider(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
			super(dataOutput, registryLookup);
		}

		@Override
		public <T extends LootConditionConsumingBuilder<T>> T addSurvivesExplosionCondition(ItemConvertible drop, LootConditionConsumingBuilder<T> builder) {
			return !this.explosionImmuneItems.contains(drop.asItem()) ? builder.conditionally(SurvivesExplosionLootCondition.builder()) : builder.getThisConditionConsumingBuilder();
		}

		public LootTable.Builder endRelayDrop(Block drop) {
			return LootTable.builder().pool(
					this.addSurvivesExplosionCondition(
							drop,
							LootPool.builder().rolls(
									ConstantLootNumberProvider.create(1.0F)
							).with(
									ItemEntry.builder(drop).apply(
											CopyComponentsLootFunction.builder(
													CopyComponentsLootFunction.Source.BLOCK_ENTITY
											)
									)
							)
					)
			);
		}

		@Override
		public void generate() {
			addDrop(RelayBlocks.RELAY, this::endRelayDrop);
		}
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
					Advancement.Builder builder = recipeExporter.getAdvancementBuilder().criterion(
							"has_the_recipe", RecipeUnlockedCriterion.create(RELAY_PROGRAM)
					).rewards(AdvancementRewards.Builder.recipe(RELAY_PROGRAM)).criteriaMerger(
							AdvancementRequirements.CriterionMerger.OR
					);
					builder.criterion(
							"has_relay",
							Criteria.INVENTORY_CHANGED.create(new InventoryChangedCriterion.Conditions(
									Optional.empty(),
									InventoryChangedCriterion.Conditions.Slots.ANY,
									List.of(
											ItemPredicate.Builder.create().items(
													wrapperLookup.getOrThrow(RegistryKeys.ITEM),
													RelayItems.RELAY
											).build()
									)
							))
					);
					recipeExporter.accept(
							RELAY_PROGRAM,
							new ShapelessRecipe(
									"relay",
									CraftingRecipeCategory.MISC,
									new ItemStack(
											RelayItems.RELAY.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(
															DataComponentTypes.LODESTONE_TRACKER, true
													)
											).add(
													DataComponentTypes.LORE, new LoreComponent(
															List.of(RelayBlock.getTargetText("?, ?, ?", "?").styled(style -> style.withItalic(false)))
													)
											).build()
									),
									List.of(
										new CustomDisplayIngredient(
												DefaultCustomIngredients.difference(
														Ingredient.ofItem(Items.COMPASS),
														DefaultCustomIngredients.components(
																Ingredient.ofItem(Items.COMPASS),
																components -> components.remove(DataComponentTypes.LODESTONE_TRACKER)
														)
												),
												List.of(
														new ItemStack(
																Items.COMPASS.getRegistryEntry(),
																1,
																ComponentChanges.builder().add(
																		DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(
																				Optional.empty(), true
																		)
																).build()
														)
												)
										).toVanilla(),
										Ingredient.ofItem(RelayItems.RELAY)
									)
							),
							builder.build(RELAY_PROGRAM.getValue().withPrefixedPath("recipes/" + RecipeCategory.MISC.getName() + "/"))
					);
				}
			};
		}

		@Override
		public String getName() {
			return "RelayRecipes";
		}
	}
}
