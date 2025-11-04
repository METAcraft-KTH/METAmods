package nu.metacraft.relay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import nu.metacraft.relay.blocks.RelayBlocks;
import nu.metacraft.relay.blocks.block.RelayBlock;
import nu.metacraft.relay.items.RelayItems;
import nu.metacraft.lib.event.RecipeDataGen;
import nu.metacraft.lib.recipe.CustomDisplayIngredient;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RelayDatagen implements DataGeneratorEntrypoint {

	public static final ResourceKey<Recipe<?>> RELAY_PROGRAM = ResourceKey.create(
			Registries.RECIPE,
			ResourceLocation.fromNamespaceAndPath(Relay.MODID, "relay_program")
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

		public RelayBlockTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider wrapperLookup) {
			valueLookupBuilder(BlockTags.MINEABLE_WITH_PICKAXE).add(RelayBlocks.RELAY);
		}
	}

	public static class RelayItemTagProvider extends FabricTagProvider.ItemTagProvider {

		public RelayItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider wrapperLookup) {
			valueLookupBuilder(RelayItems.RELAY_RECHARGE_ITEMS).add(Items.END_CRYSTAL);
		}
	}

	public static class LootTableProvider extends FabricBlockLootTableProvider {

		protected final Set<Item> explosionImmuneItems = Set.of(
				RelayItems.RELAY
		);

		protected LootTableProvider(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
			super(dataOutput, registryLookup);
		}

		@Override
		public <T extends ConditionUserBuilder<T>> T applyExplosionCondition(ItemLike drop, ConditionUserBuilder<T> builder) {
			return !this.explosionResistant.contains(drop.asItem()) ? builder.when(ExplosionCondition.survivesExplosion()) : builder.unwrap();
		}

		public LootTable.Builder endRelayDrop(Block drop) {
			return LootTable.lootTable().withPool(
					this.applyExplosionCondition(
							drop,
							LootPool.lootPool().setRolls(
									ConstantValue.exactly(1.0F)
							).add(
									LootItem.lootTableItem(drop).apply(
											CopyComponentsFunction.copyComponentsFromBlockEntity(
													LootContextParams.BLOCK_ENTITY
											)
									)
							)
					)
			);
		}

		@Override
		public void generate() {
			add(RelayBlocks.RELAY, this::endRelayDrop);
		}
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
					Advancement.Builder builder = recipeExporter.advancement().addCriterion(
							"has_the_recipe", RecipeUnlockedTrigger.unlocked(RELAY_PROGRAM)
					).rewards(AdvancementRewards.Builder.recipe(RELAY_PROGRAM)).requirements(
							AdvancementRequirements.Strategy.OR
					);
					builder.addCriterion(
							"has_relay",
							CriteriaTriggers.INVENTORY_CHANGED.createCriterion(new InventoryChangeTrigger.TriggerInstance(
									Optional.empty(),
									InventoryChangeTrigger.TriggerInstance.Slots.ANY,
									List.of(
											ItemPredicate.Builder.item().of(
													wrapperLookup.lookupOrThrow(Registries.ITEM),
													RelayItems.RELAY
											).build()
									)
							))
					);
					recipeExporter.accept(
							RELAY_PROGRAM,
							new ShapelessRecipe(
									"relay",
									CraftingBookCategory.MISC,
									new ItemStack(
											RelayItems.RELAY.builtInRegistryHolder(), 1,
											DataComponentPatch.builder().set(
													DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(
															DataComponents.LODESTONE_TRACKER, true
													)
											).set(
													DataComponents.LORE, new ItemLore(
															List.of(RelayBlock.getTargetText("?, ?, ?", "?").withStyle(style -> style.withItalic(false)))
													)
											).build()
									),
									List.of(
										new CustomDisplayIngredient(
												DefaultCustomIngredients.difference(
														Ingredient.of(Items.COMPASS),
														DefaultCustomIngredients.components(
																Ingredient.of(Items.COMPASS),
																components -> components.remove(DataComponents.LODESTONE_TRACKER)
														)
												),
												List.of(
														new ItemStack(
																Items.COMPASS.builtInRegistryHolder(),
																1,
																DataComponentPatch.builder().set(
																		DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
																				Optional.empty(), true
																		)
																).build()
														)
												)
										).toVanilla(),
										Ingredient.of(RelayItems.RELAY)
									)
							),
							builder.build(RELAY_PROGRAM.location().withPrefix("recipes/" + RecipeCategory.MISC.getFolderName() + "/"))
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
