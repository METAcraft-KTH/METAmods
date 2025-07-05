package nu.metacraft.lib;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.apache.commons.lang3.mutable.MutableObject;
import nu.metacraft.lib.event.RecipeLoad;
import nu.metacraft.lib.extensions.RecipeComponentCarryoverExtension;
import nu.metacraft.lib.extensions.RecipeRemainderExtension;
import nu.metacraft.lib.recipe.CustomDisplayIngredient;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Recipes {

	private static final String REMAINDER = METAcraftLib.NAMESPACE + ":remainder";

	private static final String COMPONENT_CARRYOVER = METAcraftLib.NAMESPACE + ":component_carryover";


	private static final String TO = "to";

	private static final String FROM = "from";

	public static void init() {
		CustomIngredientSerializer.register(CustomDisplayIngredient.SERIALIZER);
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (recipe instanceof RecipeRemainderExtension recipeData) {
				if (json.has(REMAINDER)) {
					var remainderMapper = json.get(REMAINDER);
					if (remainderMapper.isJsonPrimitive()) {
						Optional.ofNullable(
								Identifier.tryParse(remainderMapper.getAsString())
						).map(Registries.ITEM::get).ifPresentOrElse(item -> {
							recipeData.metacraft_lib$setRemainderFunction(stack -> new ItemStack(item));
						}, () -> {
							METAcraftLib.LOGGER.error("Item " + remainderMapper + " did not exist.");
						});
					} else if (remainderMapper.isJsonArray()) {
						var array = remainderMapper.getAsJsonArray();
						MutableObject<UnaryOperator<ItemStack>> operator = new MutableObject<>(stack -> ItemStack.EMPTY);
						for (var element : array) {
							if (element.isJsonObject()) {
								parseMapping(element.getAsJsonObject(), registryLookup).ifPresent(mapping -> {
									var prevOp = operator.getValue();
									operator.setValue(stack -> {
										var newStack = mapping.apply(stack);
										if (!newStack.isEmpty()) {
											return newStack;
										} else {
											return prevOp.apply(stack);
										}
									});
								});
							} else {
								METAcraftLib.LOGGER.error("Not a json object: " + element);
							}
						}
						recipeData.metacraft_lib$setRemainderFunction(operator.getValue());
					} else if (remainderMapper.isJsonObject()) {
						var object = remainderMapper.getAsJsonObject();
						if (object.has(TO) && object.has(FROM)) {
							parseMapping(object, registryLookup).ifPresent(recipeData::metacraft_lib$setRemainderFunction);
						} else {
							ItemStack.CODEC.parse(registryLookup.getOps(JsonOps.INSTANCE), remainderMapper).resultOrPartial(
									METAcraftLib.LOGGER::error
							).ifPresent(stack -> {
								recipeData.metacraft_lib$setRemainderFunction(orgStack -> stack.copy());
							});
						}
					}
				}
			}
			if (recipe instanceof RecipeComponentCarryoverExtension nbt) {
				if (json.has(COMPONENT_CARRYOVER)) {
					Codec.either(
							ItemPredicate.CODEC, ItemPredicate.CODEC.listOf()
					).parse(registryLookup.getOps(JsonOps.INSTANCE), json.get(COMPONENT_CARRYOVER)).resultOrPartial(
							METAcraftLib.LOGGER::error
					).map(either -> either.map(predicate -> predicate, predicateList -> {
						Predicate<ItemStack> rootPredicate = stack -> false;
						for (ItemPredicate predicate : predicateList) {
							rootPredicate = rootPredicate.or(predicate);
						}
						return rootPredicate;
					})).ifPresent(nbt::metacraft_lib$setComponentCarryOver);
				}
			}
			return recipe;
		});
	}

	private static Optional<UnaryOperator<ItemStack>> parseMapping(JsonObject object, RegistryWrapper.WrapperLookup lookup) {
		return Ingredient.CODEC.parse(lookup.getOps(JsonOps.INSTANCE), object.get(FROM)).resultOrPartial(
				METAcraftLib.LOGGER::error
		).flatMap(from -> {
			return ItemStack.CODEC.parse(lookup.getOps(JsonOps.INSTANCE), object.get(TO)).resultOrPartial(
					METAcraftLib.LOGGER::error
			).map(to -> {
				return stack -> from.test(stack) ? to.copy() : ItemStack.EMPTY;
			});
		});
	}

	public static final Recipe<?> DUMMY = new Recipe<CraftingRecipeInput>() {
		@Override
		public boolean matches(CraftingRecipeInput input, World world) {
			return false;
		}

		@Override
		public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
			return ItemStack.EMPTY;
		}

		@Override
		public RecipeSerializer<? extends Recipe<CraftingRecipeInput>> getSerializer() {
			return RecipeSerializer.SHAPELESS;
		}

		@Override
		public RecipeType<? extends Recipe<CraftingRecipeInput>> getType() {
			return RecipeType.CRAFTING;
		}

		@Override
		public IngredientPlacement getIngredientPlacement() {
			return IngredientPlacement.NONE;
		}

		@Override
		public RecipeBookCategory getRecipeBookCategory() {
			return RecipeBookCategories.CRAFTING_MISC;
		}
	};

}
