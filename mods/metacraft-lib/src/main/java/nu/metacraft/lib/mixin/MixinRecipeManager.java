package nu.metacraft.lib.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.Recipes;
import nu.metacraft.lib.event.RecipeLoad;

@Mixin(RecipeManager.class)
public class MixinRecipeManager {

	@WrapOperation(
		method = "fromJson",
		at = @At(
			value = "NEW",
			target = "(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/item/crafting/Recipe;)Lnet/minecraft/world/item/crafting/RecipeHolder;"
		)
	)
	private static RecipeHolder<?> deserialize(
			ResourceKey<Recipe<?>> registryKey, Recipe<?> recipe, Operation<RecipeHolder<?>> original,
			@Local(argsOnly = true) JsonObject json, @Local(argsOnly = true) HolderLookup.Provider registryLookup
	) {
		return original.call(registryKey, fixRecipe(registryKey, json, recipe, registryLookup));
	}

	@Unique
	private static Recipe<?> fixRecipe(ResourceKey<Recipe<?>> key, JsonObject json, Recipe<?> recipe, HolderLookup.Provider registryLookup) {
		var result = RecipeLoad.EVENT.invoker().modify(key, json, recipe, registryLookup);
		if (result != null) {
			return result;
		} else {
			return Recipes.DUMMY;
		}
	}

}
