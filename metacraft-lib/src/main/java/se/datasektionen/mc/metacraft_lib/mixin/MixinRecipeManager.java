package se.datasektionen.mc.metacraft_lib.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.recipe.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.Recipes;
import se.datasektionen.mc.metacraft_lib.event.RecipeLoad;

import java.util.Map;

@Mixin(RecipeManager.class)
public class MixinRecipeManager {

	@Shadow @Final private RegistryWrapper.WrapperLookup registryLookup;

	@WrapOperation(
			method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
			at = @At(
					value = "NEW",
					target = "(Lnet/minecraft/util/Identifier;Lnet/minecraft/recipe/Recipe;)Lnet/minecraft/recipe/RecipeEntry;"
			)
	)
	private RecipeEntry<?> deserialize(
			Identifier id, Recipe<?> recipe, Operation<RecipeEntry<?>> original, @Local Map.Entry<Identifier, JsonElement> entry
	) {
		if (entry.getValue().isJsonObject()) {
			return original.call(id, fixRecipe(id, entry.getValue().getAsJsonObject(), recipe, registryLookup));
		}
		return original.call(id, recipe);
	}

	@WrapWithCondition(
			method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
			at = @At(
					value = "INVOKE",
					target = "Lcom/google/common/collect/ImmutableMultimap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMultimap$Builder;"
			)
	)
	private <K, V> boolean skipDummy(ImmutableMultimap.Builder<RecipeType<?>, RecipeEntry<Recipe<?>>> instance, K key, V value) {
		return value != Recipes.DUMMY;
	}

	@WrapWithCondition(
			method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
			at = @At(
					value = "INVOKE",
					target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;"
			)
	)
	private <K, V> boolean skipDummy(ImmutableMap.Builder<Identifier, RecipeEntry<Recipe<?>>> instance, K key, V value) {
		return value != Recipes.DUMMY;
	}

	@WrapOperation(
		method = "deserialize",
		at = @At(
			value = "NEW",
			target = "(Lnet/minecraft/util/Identifier;Lnet/minecraft/recipe/Recipe;)Lnet/minecraft/recipe/RecipeEntry;"
		)
	)
	private static RecipeEntry<?> deserialize(
			Identifier id, Recipe<?> recipe, Operation<RecipeEntry<?>> original,
			@Local(argsOnly = true) JsonObject json, @Local(argsOnly = true) RegistryWrapper.WrapperLookup registryLookup
	) {
		return original.call(id, fixRecipe(id, json, recipe, registryLookup));
	}

	@Unique
	private static Recipe<?> fixRecipe(Identifier id, JsonObject json, Recipe<?> recipe, RegistryWrapper.WrapperLookup registryLookup) {
		var result = RecipeLoad.EVENT.invoker().modify(id, json, recipe, registryLookup);
		if (result != null) {
			return result;
		} else {
			return Recipes.DUMMY;
		}
	}

}
