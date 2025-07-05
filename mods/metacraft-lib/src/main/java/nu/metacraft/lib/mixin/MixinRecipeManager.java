package nu.metacraft.lib.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.recipe.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.Recipes;
import nu.metacraft.lib.event.RecipeLoad;

@Mixin(ServerRecipeManager.class)
public class MixinRecipeManager {

	@WrapOperation(
		method = "deserialize",
		at = @At(
			value = "NEW",
			target = "(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/recipe/Recipe;)Lnet/minecraft/recipe/RecipeEntry;"
		)
	)
	private static RecipeEntry<?> deserialize(
			RegistryKey<Recipe<?>> registryKey, Recipe<?> recipe, Operation<RecipeEntry<?>> original,
			@Local(argsOnly = true) JsonObject json, @Local(argsOnly = true) RegistryWrapper.WrapperLookup registryLookup
	) {
		return original.call(registryKey, fixRecipe(registryKey, json, recipe, registryLookup));
	}

	@Unique
	private static Recipe<?> fixRecipe(RegistryKey<Recipe<?>> key, JsonObject json, Recipe<?> recipe, RegistryWrapper.WrapperLookup registryLookup) {
		var result = RecipeLoad.EVENT.invoker().modify(key, json, recipe, registryLookup);
		if (result != null) {
			return result;
		} else {
			return Recipes.DUMMY;
		}
	}

}
