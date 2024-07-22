package se.datasektionen.mc.faster_minecarts;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.JavaOps;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.criterion.RecipeUnlockedCriterion;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.NbtPredicate;
import net.minecraft.predicate.item.CustomDataPredicate;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.predicate.item.ItemSubPredicateTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public class DataGen implements DataGeneratorEntrypoint {

	private void acceptRecipe(
			RecipeExporter exporter, Identifier recipeID, Recipe<?> recipe, RecipeCategory category,
			Map<String, AdvancementCriterion<?>> criteria
	) {
		var builder = exporter.getAdvancementBuilder().criterion(
				"has_the_recipe", RecipeUnlockedCriterion.create(recipeID)
		).rewards(AdvancementRewards.Builder.recipe(recipeID)).criteriaMerger(
				AdvancementRequirements.CriterionMerger.OR
		);
		criteria.forEach(builder::criterion);
		exporter.accept(recipeID, recipe, builder.build(recipeID.withPrefixedPath("recipes/" + category.getName() + "/")));
	}

	private Identifier getIDFromItem(Item item) {
		return item.getRegistryEntry().getKey().orElseThrow().getValue();
	}

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		fabricDataGenerator.createPack().addProvider(
				(output, lookup) -> new RecipeProvider(output, lookup) {
					@Override
					public void generate(RecipeExporter exporter) {
						List<Item> minecarts = ImmutableList.of(
								Items.MINECART,
								Items.CHEST_MINECART,
								Items.HOPPER_MINECART,
								Items.TNT_MINECART,
								Items.COMMAND_BLOCK_MINECART
						);

						final String tier1Cart = "tier1cart";
						final String tier2Cart = "tier2cart";
						final String tier3Cart = "tier3cart";

						for (var minecart : minecarts) {
							var id = getIDFromItem(minecart);
							var recipe = new ShapedRecipe(
								"", CraftingRecipeCategory.EQUIPMENT,
								RawShapedRecipe.create(
									Map.of(
										'C', Ingredient.ofItems(Items.EXPERIENCE_BOTTLE),
										'R', Ingredient.ofItems(Items.REPEATER),
										'L', Ingredient.ofItems(Items.SLIME_BALL),
										'M', Ingredient.ofItems(Items.GOLD_INGOT),
										'P', Ingredient.ofItems(Items.PISTON),
										'V', Ingredient.DISALLOW_EMPTY_CODEC.parse(
											JavaOps.INSTANCE, Map.of(
												"fabric:type", "fabric:difference",
												"base", Map.of(
													"item", id.toString()
												),
												"subtracted", Map.of(
													"fabric:type", "fabric:custom_data",
													"base", Map.of(
														"item", id.toString()
													),
													"nbt", Map.of(
														"SpeedUpgrade", true
													)
												)
											)
										).resultOrPartial(FasterMinecarts.logger::error).orElseThrow()
									),
									"RCR",
										"LVL",
										"MPM"
								),
								FasterMinecarts.makeSuperFastMinecartItem(
									new ItemStack(minecart, 1), OptionalDouble.of(0.06),
									OptionalDouble.of(20), OptionalDouble.of(20), Optional.of(tier1Cart),
									minecart.getName().copy().append(Text.literal(" Mk. II")).styled(
											style -> style.withColor(Formatting.AQUA)
									)
								)
							);
							acceptRecipe(
									exporter, FasterMinecarts.getID("minecart_upgrades/" + id.getPath() + "/tier1"),
									recipe, RecipeCategory.TRANSPORTATION, Map.of(
											"has_minecart", conditionsFromItemPredicates(
													ItemPredicate.Builder.create().items(
															minecarts.toArray(Item[]::new)
													).build()
											)
									)
							);
						}

						for (var minecart : minecarts) {
							var id = getIDFromItem(minecart);
							var recipe = new ShapedRecipe(
								"", CraftingRecipeCategory.EQUIPMENT,
								RawShapedRecipe.create(
									//FIXME The c:potions tag loads too late for some reason, requiring a /reload after startup to make this recipe work.
									Map.of(
										'C', Ingredient.DISALLOW_EMPTY_CODEC.parse(
											JavaOps.INSTANCE, Map.of(
												"fabric:type", "fabric:any",
												"ingredients", List.of(
													Map.of(
														"fabric:type", "fabric:components",
														"base", Map.of(
															"tag", "c:potions"
														),
														"components", Map.of(
															"potion_contents", Map.of(
																"potion", "minecraft:swiftness"
															)
														)
													),
													Map.of(
														"fabric:type", "fabric:components",
														"base", Map.of(
															"tag", "c:potions"
														),
														"components", Map.of(
															"potion_contents", Map.of(
																"potion", "minecraft:strong_swiftness"
															)
														)
													),
													Map.of(
														"fabric:type", "fabric:components",
														"base", Map.of(
															"tag", "c:potions"
														),
														"components", Map.of(
															"potion_contents", Map.of(
																"potion", "minecraft:long_swiftness"
															)
														)
													)
												)
											)
										).resultOrPartial(FasterMinecarts.logger::error).orElseThrow(),
										'R', Ingredient.ofItems(Items.REPEATER),
										'L', Ingredient.ofItems(Items.MAGMA_CREAM),
										'M', Ingredient.ofItems(Items.DIAMOND),
										'P', Ingredient.ofItems(Items.STICKY_PISTON),
										'V', Ingredient.DISALLOW_EMPTY_CODEC.parse(
											JavaOps.INSTANCE, Map.of(
												"fabric:type", "fabric:custom_data",
												"base", Map.of(
													"item", id.toString()
												),
												"nbt", Map.of(
													"SpeedUpgrade", true,
													"CraftingTag", tier1Cart
												)
											)
										).resultOrPartial(FasterMinecarts.logger::error).orElseThrow()
									),
									"RCR",
									"LVL",
									"MPM"
								),
								FasterMinecarts.makeSuperFastMinecartItem(
									new ItemStack(minecart, 1), OptionalDouble.of(0.06),
									OptionalDouble.of(30), OptionalDouble.of(30), Optional.of(tier2Cart),
									minecart.getName().copy().append(Text.literal(" Mk. III")).styled(
											style -> style.withColor(Formatting.DARK_RED)
									)
								)
							);
							NbtCompound tagCheck = new NbtCompound();
							tagCheck.putString("CraftingTag", tier1Cart);
							acceptRecipe(
								exporter, FasterMinecarts.getID("minecart_upgrades/" + id.getPath() + "/tier2"),
								recipe, RecipeCategory.TRANSPORTATION, Map.of(
									"has_minecart", conditionsFromItemPredicates(
										ItemPredicate.Builder.create().items(
											minecarts.toArray(Item[]::new)
										).subPredicate(
											ItemSubPredicateTypes.CUSTOM_DATA,
											CustomDataPredicate.customData(
												new NbtPredicate(tagCheck)
											)
										).build()
									)
								)
							);
						}


						for (var minecart : minecarts) {
							var id = getIDFromItem(minecart);
							var recipe = new ShapedRecipe(
								"", CraftingRecipeCategory.EQUIPMENT,
								RawShapedRecipe.create(
									Map.of(
										'C', Ingredient.ofItems(Items.DRAGON_BREATH),
										'R', Ingredient.ofItems(Items.REPEATER),
										'L', Ingredient.ofItems(Items.END_CRYSTAL),
										'M', Ingredient.ofItems(Items.NETHERITE_INGOT),
										'P', Ingredient.ofItems(Items.CALIBRATED_SCULK_SENSOR),
										'V', Ingredient.DISALLOW_EMPTY_CODEC.parse(
											JavaOps.INSTANCE, Map.of(
												"fabric:type", "fabric:custom_data",
												"base", Map.of(
													"item", id.toString()
												),
												"nbt", Map.of(
													"SpeedUpgrade", true,
													"CraftingTag", tier2Cart
												)
											)
										).resultOrPartial(FasterMinecarts.logger::error).orElseThrow()
									),
									"RCR",
									"LVL",
									"MPM"
								),
								FasterMinecarts.makeSuperFastMinecartItem(
									new ItemStack(minecart, 1), OptionalDouble.of(0.065),
									OptionalDouble.of(40), OptionalDouble.of(40), Optional.of(tier3Cart),
									minecart.getName().copy().append(Text.literal(" Mk. IV")).styled(
										style -> style.withColor(Formatting.DARK_PURPLE)
									)
								)
							);
							acceptRecipe(
								exporter, FasterMinecarts.getID("minecart_upgrades/" + id.getPath() + "/tier3"),
								recipe, RecipeCategory.TRANSPORTATION, Map.of()
							);
						}
					}
				}
		);
	}
}
