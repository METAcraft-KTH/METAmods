package nu.metacraft.relay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.references.ItemIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import nu.metacraft.relay.blocks.RelayBlocks;
import nu.metacraft.relay.blocks.block.RelayBlock;
import nu.metacraft.relay.items.RelayItems;
import nu.metacraft.lib.event.RecipeDataGen;
import nu.metacraft.lib.recipe.CustomDisplayIngredient;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RelayDatagen implements DataGeneratorEntrypoint {

	public static final ResourceKey<Recipe<?>> RELAY_PROGRAM = ResourceKey.create(
			Registries.RECIPE,
			Identifier.fromNamespaceAndPath(Relay.MODID, "relay_program")
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
		pack.addProvider(RelayBlockTagsProvider::new);
		pack.addProvider(RelayItemTagsProvider::new);
	}

	public static class RelayBlockTagsProvider extends FabricTagsProvider.BlockTagsProvider {

		public RelayBlockTagsProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider wrapperLookup) {
			builder(BlockTags.MINEABLE_WITH_PICKAXE).add(RelayBlocks.RELAY.key());
		}
	}

	public static class RelayItemTagsProvider extends FabricTagsProvider.ItemTagsProvider {

		public RelayItemTagsProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider wrapperLookup) {
			builder(RelayItems.RELAY_RECHARGE_ITEMS).add(ItemIds.END_CRYSTAL);
		}
	}

	public static class LootTableProvider extends FabricBlockLootSubProvider {

		protected LootTableProvider(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
			super(dataOutput, registryLookup);
		}

		public LootTable.Builder endRelayDrop(Block drop) {
			return LootTable.lootTable().withPool(
					this.applyExplosionCondition(
							drop,
							LootPool.lootPool().setRolls(
									Holder.direct(new ConstantValue(1))
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
			add(RelayBlocks.RELAY.value(), this::endRelayDrop);
		}
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
					Advancement.Builder builder = output.advancement().addCriterion(
							"has_the_recipe", RecipeUnlockedTrigger.unlocked(output.lookup(Registries.RECIPE).getOrThrow(RELAY_PROGRAM))
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
													registries.lookupOrThrow(Registries.ITEM),
													RelayItems.RELAY
											).build()
									)
							))
					);
					output.accept(
							RELAY_PROGRAM,
							new ShapelessRecipe(
									new Recipe.CommonInfo(true),
									new CraftingRecipe.CraftingBookInfo(
											CraftingBookCategory.MISC, "relay"
									),
									new ItemStackTemplate(
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
															new ItemStackTemplate(
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
							builder.build(RELAY_PROGRAM.identifier().withPrefix("recipes/" + RecipeCategory.MISC.getFolderName() + "/"))
					);
				}
			};
		}

		@Override
		public @NonNull String getName() {
			return "RelayRecipes";
		}
	}
}
